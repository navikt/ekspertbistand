package no.nav.ekspertbistand

import io.ktor.client.HttpClient
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
import no.nav.ekspertbistand.arena.ArenaTilsagnsbrevProcessor
import no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessor
import no.nav.ekspertbistand.arena.startKafkaConsumers
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.projections.configureProjectionBuilders
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.soknad.configureSoknadApiV1
import no.nav.ekspertbistand.soknad.subjectToken
import no.nav.ekspertbistand.tilsagndata.configureTilsagnDataApiV1
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.event.Level
import java.time.Instant
import java.util.*


const val altinn3Ressursid = "nav_tiltak_ekspertbistand"
val startKafkaProsesseringAt = basedOnEnv(
    other = { Instant.EPOCH },
    prod = { Instant.parse("2026-02-23T10:00:00.00Z") }
)


fun main() {
    val dbConfig = DbConfig.nais()

    ktorServer {
        dependencies {
            provide<Database> {
                dbConfig.destroyExistingDatabase()
                dbConfig.flywayAction {
                    migrate()
                }
                dbConfig.jdbcDatabase
            }
            provide<AuthConfig> { AuthConfig.nais }
            provide<HttpClient> { defaultHttpClient() }

            provide<TokenXTokenIntrospector>(TokenXAuthClient::class)
            provide<TokenXTokenExchanger>(TokenXAuthClient::class)
            provide<AzureAdTokenProvider>(AzureAdAuthClient::class)

            provide(AltinnTilgangerClient::class)
            provide(DokgenClient::class)
            provide(DokArkivClient::class)
            provide(EregClient::class)
            provide(EregService::class)
            provide(NorgKlient::class)
            provide(BehandlendeEnhetService::class)
            provide(PdlApiKlient::class)
            provide(ProdusentApiKlient::class)
            provide(ArenaClient::class)
            provide { ArenaTilsagnsbrevProcessor(resolve(), startKafkaProsesseringAt) }
            provide { ArenaTiltaksgjennomforingEndretProcessor(resolve(), startKafkaProsesseringAt) }
        }

        // configure standard server stuff
        configureServer()

        // configure authentication of clients
        configureTokenXAuth()

        // configure application modules and endpoints
        configureSoknadApiV1()
        configureOrganisasjonerApiV1()
        configureTilsagnDataApiV1()
        configureEregApiV1()

        // event manager and event handlers
        configureEventHandlers()

        configureProjectionBuilders()

        configureAppMetrics()

        startKafkaConsumers(coroutineContext)

        // internal endpoints and lifecycle hooks
        configureInternal()
        registerShutdownListener()
    }
}
// ... existing code ...


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
