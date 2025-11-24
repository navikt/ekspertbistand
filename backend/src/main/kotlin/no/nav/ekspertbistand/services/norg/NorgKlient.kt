package no.nav.ekspertbistand.services.norg

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.ekspertbistand.infrastruktur.basedOnEnv

class NorgKlient(
    private val httpClient: HttpClient
) {
    suspend fun hentBehandlendeEnhet(kommuneNummer: String): Norg2Enhet? {
        val norg2ResponseListe = try {
            httpClient.post("$baseUrl/norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
            {
                contentType(ContentType.Application.Json)
                setBody(Norg2Request(kommuneNummer))
            }.body<List<Norg2Enhet>>()
        } catch (e: Exception) {
            throw RuntimeException("Hente behandlendeEnhet for $kommuneNummer feilet", e)
        }

        return norg2ResponseListe.firstOrNull { it.status == "Aktiv" }
    }


    companion object {
        const val baseUrl = "http://norg2.org"

        // TODO: Skal denne brukes dersom vi får tom liste fra Norg?
        private const val OSLO_ARBEIDSLIVSENTER_KODE = "0391"
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Norg2Enhet(
    val enhetNr: String,
    val status: String,
)

@Serializable
private data class Norg2Request(
    val geografiskOmraade: String,
) {
    val tema: String = "" //TODO: Hva er tema fro ekspertbistand?
}
