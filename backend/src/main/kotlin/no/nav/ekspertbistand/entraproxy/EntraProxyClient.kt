package no.nav.ekspertbistand.entraproxy

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson

class EntraProxyClient(
    val tokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient
) {
    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-gcp.fager.entraproxy/.default",
            dev = "api://dev-gcp.fager.entraproxy/.default",
            other = "api://mock.entraproxy/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://entraproxy.intern.nav.no",
            dev = "https://entraproxy.intern.dev.nav.no",
            other = "http://entraproxy.mock.svc.cluster.local",
        )

        const val API_PATH = "/api/v1/enhet/ansatt"
        const val ANSATT_API_PATH = "/api/v1/ansatt"
    }

    val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "entra-proxy.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    suspend fun hentEnheter(navIdent: String): List<Enhet> =
        httpClient.get {
            url {
                takeFrom(ingress)
                path("$API_PATH/$navIdent")
            }
            accept(ContentType.Application.Json)
            bearerAuth(
                tokenProvider.token(targetAudience).fold(
                    { it.accessToken },
                    { throw Exception("Failed to get token: ${it.error}") }
                )
            )
        }.body()

    suspend fun hentAnsatt(navIdent: String): UtvidetAnsatt =
        httpClient.get {
            url {
                takeFrom(ingress)
                path("$ANSATT_API_PATH/$navIdent")
            }
            accept(ContentType.Application.Json)
            bearerAuth(
                tokenProvider.token(targetAudience).fold(
                    { it.accessToken },
                    { throw Exception("Failed to get token: ${it.error}") }
                )
            )
        }.body()
}

@Serializable
data class Enhet(
    val enhetnummer: String,
    val navn: String,
)

@Serializable
data class UtvidetAnsatt(
    val navIdent: String,
    val visningNavn: String? = null,
    val fornavn: String? = null,
    val etternavn: String? = null,
    val epost: String? = null,
    val enhet: Enhet,
    val tident: String,
)

