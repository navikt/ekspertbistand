package no.nav.ekspertbistand

import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.*
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.skjema.configureSkjemaApiV1
import no.nav.ekspertbistand.skjema.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.event.Level
import java.util.*


fun main() {
    val dbConfig = DbConfig.nais()
    val authConfig = TexasAuthConfig.nais()
    val tokenxClient = authConfig.authClient(IdentityProvider.TOKEN_X)
    val azureClient = authConfig.authClient(IdentityProvider.AZURE_AD)

    ktorServer {
        dependencies {
            provide<Database> {
                dbConfig.flywayAction {
                    migrate()
                }
                // mixing r2dbc and jdbc does not work well together, so we use only jdbc for now
                //    dbConfig.r2dbcDatabase
                dbConfig.jdbcDatabase
            }
            provide<TokenIntrospector>(IdentityProvider.TOKEN_X.alias) { tokenxClient }
            provide<TokenProvider>(IdentityProvider.AZURE_AD.alias) { azureClient }
            provide { AltinnTilgangerClient(tokenxClient) }
            provide { DokgenClient() }
            provide { EregClient() }
            provide<IdempotencyGuard> { IdempotencyGuard(resolve<Database>()) }
            provide<ProdusentApiKlient> {
                ProdusentApiKlient(
                    tokenProvider = resolve<TokenProvider>(IdentityProvider.AZURE_AD.alias),
                    httpClient = defaultHttpClient(customizeMetrics = {
                        clientName = "notifikasjon.produsent.api.klient"
                    }) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 5_000
                        }
                    }
                )
            }
            provide<ArenaClient> {
                ArenaClient(
                    tokenProvider = resolve<TokenProvider>(IdentityProvider.AZURE_AD.alias),
                    httpClient = defaultHttpClient({
                        clientName = "arena-api.client"
                    }) {
                        install(HttpTimeout) {
                            requestTimeoutMillis = 15_000
                        }
                    }
                )
            }
        }

        // configure standard server stuff
        configureServer()

        // configure authentication of clients
        configureTokenXAuth()

        // configure application modules and endpoints
        configureSkjemaApiV1()
        configureOrganisasjonerApiV1()
        configureEregApiV1()

        // event manager and event handlers
        configureEventHandlers()

        // internal endpoints and lifecycle hooks
        configureInternal()
        registerShutdownListener()
    }
}

fun ktorServer(
    initialConfig: suspend Application.() -> Unit,
) = embeddedServer(
    CIO,
    configure = {
        connector {
            port = 8080
            host = "0.0.0.0"
        }
        shutdownGracePeriod = 20_000
        shutdownTimeout = 30_000
    }
) {
    initialConfig()
}.start(wait = true)

suspend fun Application.configureOrganisasjonerApiV1() {
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    routing {
        authenticate(TOKENX_PROVIDER) {
            with(altinnTilgangerClient) {
                get("api/organisasjoner/v1") {
                    call.respond(hentTilganger(subjectToken))
                }
            }
        }
    }
}

fun Application.configureServer() {
    val log = logger()

    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024) // condition
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            when (cause) {
                is ClosedWriteChannelException -> {
                    log.warn("Client closed connection before response was sent", cause)
                    // no response, channel already closed
                }

                else -> {
                    log.error("Unexpected exception at ktor-toplevel: {}", cause.javaClass.canonicalName, cause)
                    call.response.status(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().startsWith("/internal/") }
        disableDefaultColors()

        mdc("method") { call ->
            call.request.httpMethod.value
        }
        mdc("host") { call ->
            call.request.header("host")
        }
        mdc("path") { call ->
            call.request.path()
        }
        mdc("clientId") { call ->
            call.principal<TokenXPrincipal>()?.clientId
        }

        callIdMdc("x_correlation_id")
    }

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XCorrelationId)

        generate {
            UUID.randomUUID().toString()
        }

        replyToHeader(HttpHeaders.XCorrelationId)
    }

    install(MicrometerMetrics) {
        registry = Metrics.meterRegistry
        distributionStatisticConfig = DistributionStatisticConfig.Builder()
            .percentilesHistogram(true)
            .build()
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics(),
            LogbackMetrics()
        )
    }

    install(ContentNegotiation) {
        json()
    }
}
