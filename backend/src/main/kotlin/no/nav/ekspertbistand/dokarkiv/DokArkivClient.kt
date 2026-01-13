package no.nav.ekspertbistand.dokarkiv

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
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import no.nav.ekspertbistand.infrastruktur.defaultJson
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * See documentation at https://dokarkiv-q1.dev.intern.nav.no/swagger-ui/index.html#/journalpostapi/opprettJournalpost
 * or https://confluence.adeo.no/spaces/BOA/pages/313346837/opprettJournalpost
 */
class DokArkivClient(
    val azureAdTokenProvider: AzureAdTokenProvider,
    defaultHttpClient: HttpClient,
) {
    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "dokarkiv.client"
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }
    companion object {
        val targetAudience = basedOnEnv(
            prod = "api://prod-fss.teamdokumenthandtering.dokarkiv/.default",
            dev = "api://dev-fss.teamdokumenthandtering.dokarkiv-q2/.default",
            other = "api://mock.dokarkiv/.default",
        )

        val ingress = basedOnEnv(
            prod = "https://dokarkiv.prod-fss-pub.nais.io",
            dev = "https://dokarkiv-q2.dev-fss-pub.nais.io",
            other = "http://dokarkiv.mock.svc.cluster.local",
        )

        const val API_PATH = "/rest/journalpostapi/v1/journalpost"
    }

    suspend fun opprettOgFerdigstillJournalpost(
        tittel: String,
        virksomhetsnummer: String,
        eksternReferanseId: String,
        dokumentPdfAsBytes: ByteArray,
    ): OpprettJournalpostResponse {
        val journalpost = Journalpost(
            bruker = Bruker(id = virksomhetsnummer, idType = "ORGNR"),
            avsenderMottaker = AvsenderMottaker(id = virksomhetsnummer, idType = "ORGNR"),
            eksternReferanseId = "${eksternReferanseId}-innsendt-skjema",
            journalfoerendeEnhet = "9999",
            tittel = tittel,
            dokumenter = listOf(
                Dokument(
                    tittel = tittel,
                    // TODO: sjekke at dette er riktig brevkode
                    brevkode = "5384",
                    dokumentvarianter = listOf(
                        DokumentVariant(
                            fysiskDokument = encodeToBase64(dokumentPdfAsBytes),
                            filtype = "PDFA",
                            variantformat = "ARKIV",
                        )
                    ),
                )
            ),
            journalposttype = "INNGAAENDE",
            kanal = "NAV_NO",
            tema = "TIL",
            behandlingstema = "ab0423",
            sak = Sak(sakstype = "GENERELL_SAK"),
        )
        val response = httpClient.post {
            url {
                takeFrom(ingress)
                encodedPath = API_PATH
                parameters.append("forsoekFerdigstill", "true")
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
            setBody(journalpost)
        }

        return when (response.status) {
            HttpStatusCode.Created, HttpStatusCode.Conflict -> response.body()
            else -> throw IllegalStateException(
                "DokArkiv responded with ${response.status.value}: ${response.bodyAsText()}"
            )
        }
    }
}

@Serializable
private data class Journalpost(
    val bruker: Bruker,
    val avsenderMottaker: AvsenderMottaker,
    val eksternReferanseId: String,
    val journalfoerendeEnhet: String,
    val tittel: String,
    val dokumenter: List<Dokument>,
    val journalposttype: String,
    val kanal: String,
    val tema: String,
    val behandlingstema: String,
    val sak: Sak,
)

@Serializable
private data class AvsenderMottaker(
    val id: String,
    val idType: String,
)

@Serializable
private data class Sak(
    val sakstype: String,
)

@Serializable
private data class Bruker(
    val id: String,
    val idType: String,
)

@Serializable
private data class Dokument(
    val tittel: String,
    val brevkode: String,
    val dokumentvarianter: List<DokumentVariant>,
)

@Serializable
private data class DokumentVariant(
    val fysiskDokument: String,
    val filtype: String,
    val variantformat: String,
)

@Serializable
data class OpprettJournalpostResponse(
    val dokumenter: List<OpprettJournalpostDokument>,
    val journalpostId: String,
    val journalpostferdigstilt: Boolean,
)

@Serializable
data class OpprettJournalpostDokument(
    val dokumentInfoId: String,
)

@OptIn(ExperimentalEncodingApi::class)
fun encodeToBase64(bytes: ByteArray): String {
    return Base64.encode(bytes)
}
