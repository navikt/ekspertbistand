package no.nav.ekspertbistand

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
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.skjema.configureSkjemaApiV1
import org.slf4j.event.Level
import java.util.*


fun main() {
    val dbConfig = DbConfig.nais()
    val authConfig = TexasAuthConfig.nais()
    val tokenxClient = authConfig.authClient(IdentityProvider.TOKEN_X)

    ktorServer {
        dependencies {
            provide {
                dbConfig
            }
            provide<TokenIntrospector> {
                tokenxClient
            }
            provide {
                AltinnTilgangerClient(
                    tokenxClient
                )
            }
        }
    }
}

fun ktorServer(
    initialConfig: Application.() -> Unit,
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
    module()
}.start(wait = true)

suspend fun Application.module() {
    configureServer()
    configureTokenXAuth()
    configureDatabase()

    configureSkjemaApiV1()
    configureInternal()
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


