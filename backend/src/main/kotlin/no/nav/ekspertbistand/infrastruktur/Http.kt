package no.nav.ekspertbistand.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import java.io.EOFException
import javax.net.ssl.SSLHandshakeException

val defaultNormalizer: (String) -> String = { path ->
    path
        // match standard UUIDs (36 chars, including dashes)
        .replace(Regex("[0-9a-fA-F-]{36}"), "{uuid}")
        // match 11-digit FNR
        .replace(Regex("\\b\\d{11}\\b"), "{fnr}")
        // match 9-digit ORGNR
        .replace(Regex("\\b\\d{9}\\b"), "{orgnr}")
        // match other numeric IDs
        .replace(Regex("\\b\\d+\\b"), "{numeric}")
}

val defaultJson = Json(DefaultJson) {
    ignoreUnknownKeys = true
}

fun defaultHttpClient(
    customizeMetrics: HttpClientMetricsFeature.Config.() -> Unit = {},
    configure: HttpClientConfig<CIOEngineConfig>.() -> Unit = {},
) = HttpClient(CIO) {

    expectSuccess = true

    install(HttpRequestRetry) {
        retryOnServerErrors(3)
        retryOnExceptionIf(3) { _, cause ->
            when (cause) {
                is SocketTimeoutException,
                is ConnectTimeoutException,
                is EOFException,
                is SSLHandshakeException,
                is ClosedReceiveChannelException,
                is HttpRequestTimeoutException -> true

                else -> false
            }
        }

        delayMillis { 250L }
    }

    install(HttpClientMetricsFeature) {
        registry = Metrics.meterRegistry
        customizeMetrics()
    }

    install(Logging) {
        sanitizeHeader {
            true
        }
    }

    configure()
}


/**
 * inspired by [io.ktor.server.metrics.micrometer.MicrometerMetrics], but for clients.
 * this feature/plugin generates the following metrics:
 * (x = ktor.http.client, but can be overridden)
 *
 * x.requests: a timer for measuring the time of each request. This metric provides a set of tags for monitoring request data, including http method, path, status
 *
 */
class HttpClientMetricsFeature internal constructor(
    private val registry: MeterRegistry,
    private val clientName: String,
    private val staticPath: String?,
    private val normalizer: ((String) -> String)? = null,
) {
    /**
     * [HttpClientMetricsFeature] configuration that is used during installation
     */
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry
        var staticPath: String? = null
        var pathNormalizer: ((String) -> String)? = defaultNormalizer

        internal fun isRegistryInitialized() = this::registry.isInitialized
    }

    private fun before(context: HttpRequestBuilder) {
        val rawPath = staticPath ?: context.url.encodedPath
        val normalizedPath = normalizer?.invoke(rawPath) ?: rawPath
        context.attributes.put(
            measureKey,
            ClientCallMeasure(Timer.start(registry), normalizedPath)
        )
    }

    private fun after(call: HttpClientCall, context: HttpRequestBuilder) {
        val measure = call.attributes.getOrNull(measureKey) ?: return

        measure.timer.stop(
            Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("method", call.request.method.value),
                    Tag.of("url", measure.normalizedUrl(context.url)),
                    Tag.of("status", call.response.status.value.toString()),
                )
            ).register(registry)
        )
    }

    /**
     * Companion object for feature installation
     */
    companion object Feature : HttpClientPlugin<Config, HttpClientMetricsFeature> {
        private var clientName: String = "ktor.http.client"

        val requestTimeTimerName: String
            get() = "$clientName.requests"

        private val measureKey = AttributeKey<ClientCallMeasure>("HttpClientMetricsFeature")
        override val key: AttributeKey<HttpClientMetricsFeature> = AttributeKey("HttpClientMetricsFeature")

        override fun prepare(block: Config.() -> Unit): HttpClientMetricsFeature =
            Config().apply(block).let {
                if (!it.isRegistryInitialized()) {
                    throw IllegalArgumentException(
                        "Meter registry is missing. Please initialize the field 'registry'"
                    )
                }
                HttpClientMetricsFeature(it.registry, it.clientName, it.staticPath, it.pathNormalizer)
            }

        override fun install(plugin: HttpClientMetricsFeature, scope: HttpClient) {
            clientName = plugin.clientName

            scope.plugin(HttpSend).intercept { context ->
                plugin.before(context)

                val origin = execute(context)
                plugin.after(origin, context)

                origin
            }
        }
    }

}

private data class ClientCallMeasure(
    val timer: Timer.Sample,
    val path: String,
) {
    fun normalizedUrl(url: URLBuilder) = "${url.protocol.name}://${url.host}:${url.port}${path}"
}