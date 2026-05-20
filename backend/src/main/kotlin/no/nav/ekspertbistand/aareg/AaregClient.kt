package no.nav.ekspertbistand.aareg

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

class AaregClient(
    val tokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient,
) {
    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-gcp.arbeidsforhold.aareg-services-nais/.default",
            dev = "api://dev-gcp.arbeidsforhold.aareg-services-nais/.default",
            other = "api://mock.aareg-services/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://aareg-services.intern.nav.no",
            dev = "https://aareg-services.intern.dev.nav.no",
            other = "http://aareg-services.mock.svc.cluster.local",
        )

        const val API_PATH = "/api/v2/arbeidstaker/arbeidsforhold"
    }

    val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "aareg.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    suspend fun hentArbeidsforhold(fnr: String, orgnr: String): List<Arbeidsforhold> =
        httpClient.get {
            url {
                takeFrom(ingress)
                path(API_PATH)
                parameters.append("arbeidsforholdstatus", "AKTIV")
            }
            accept(ContentType.Application.Json)
            header("Nav-Personident", fnr)
            header("Nav-Arbeidsstedident", orgnr)
            bearerAuth(
                tokenProvider.token(targetAudience).fold(
                    { it.accessToken },
                    { throw Exception("Failed to get token: ${it.error}") }
                )
            )
        }.body()
}

@Serializable
data class Arbeidsforhold(
    val arbeidssted: Arbeidssted? = null,
    val ansettelsesperiode: Ansettelsesperiode? = null,
)

@Serializable
data class Arbeidssted(
    val type: String? = null,
    val identer: List<Ident>? = null,
)

@Serializable
data class Ansettelsesperiode(
    val startdato: String? = null,
    val sluttdato: String? = null,
)

@Serializable
data class Ident(
    val type: String? = null,
    val ident: String? = null,
    val gjeldende: Boolean? = null,
)

