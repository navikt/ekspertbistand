package no.nav.ekspertbistand.dokdist

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson

/**
 * Klient mot dokdistfordeling for å distribuere en ferdigstilt journalpost til mottaker.
 */
class DokDistClient(
    val azureAdTokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient,
) {
    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "dokdist.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }

    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-fss.teamdokumenthandtering.dokdistfordeling/.default",
            dev = "api://dev-fss.teamdokumenthandtering.dokdistfordeling/.default",
            other = "api://mock.dokdistfordeling/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://dokdistfordeling.prod-fss-pub.nais.io",
            dev = "https://dokdistfordeling.dev-fss-pub.nais.io",
            other = "http://dokdistfordeling.mock.svc.cluster.local",
        )

        const val API_PATH = "/rest/v1/distribuerjournalpost"
        const val BESTILLENDE_FAGSYSTEM = "EKSPERTBISTAND"
        const val DOKUMENT_PROD_APP = "EKSPERTBISTAND"
    }

    /**
     * Distribuerer en ferdigstilt journalpost og returnerer bestillingsId.
     * Dokdist svarer 409 Conflict (med bestillingsId) dersom journalposten allerede er distribuert.
     * Det behandles som suksess slik at kallet er idempotent.
     */
    suspend fun distribuerJournalpost(
        journalpostId: String,
        distribusjonstype: Distribusjonstype,
        distribusjonstidspunkt: Distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID,
        batchId: String? = null,
    ): DistribuerJournalpostResponse {
        val response = httpClient.post {
            url {
                takeFrom(ingress)
                encodedPath = API_PATH
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            expectSuccess = false
            bearerAuth(
                azureAdTokenProvider.token(targetAudience).fold(
                    { it.accessToken },
                    { throw Exception("Failed to get token: ${it.error}") }
                )
            )
            setBody(
                DistribuerJournalpostRequest(
                    journalpostId = journalpostId,
                    batchId = batchId,
                    bestillendeFagsystem = BESTILLENDE_FAGSYSTEM,
                    dokumentProdApp = DOKUMENT_PROD_APP,
                    distribusjonstype = distribusjonstype,
                    distribusjonstidspunkt = distribusjonstidspunkt,
                )
            )
        }

        return when (response.status) {
            HttpStatusCode.OK, HttpStatusCode.Conflict -> response.body()
            else -> throw IllegalStateException(
                "Dokdist responded with ${response.status.value}: ${response.bodyAsText()}"
            )
        }
    }
}

enum class Distribusjonstype {
    VEDTAK,
    VIKTIG,
    ANNET,
}

enum class Distribusjonstidspunkt {
    UMIDDELBART,

    /**
     * Digital distribusjon holdes tilbake til kjernetid (07-23) for å unngå varsler om natten.
     */
    KJERNETID,
}

@Serializable
data class DistribuerJournalpostRequest(
    val journalpostId: String,
    val batchId: String?,
    val bestillendeFagsystem: String,
    val dokumentProdApp: String,
    val distribusjonstype: Distribusjonstype,
    val distribusjonstidspunkt: Distribusjonstidspunkt,
)

@Serializable
data class DistribuerJournalpostResponse(
    val bestillingsId: String,
)
