package no.nav.ekspertbistand.norg

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.ekspertbistand.infrastruktur.HttpClientMetricsFeature
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.defaultJson

class NorgKlient(
    defaultHttpClient: HttpClient
) {
    private val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
        install(HttpClientMetricsFeature) {
            registry = Metrics.meterRegistry
            clientName = "norg.client"
        }
    }
    suspend fun hentBehandlendeEnhet(geografiskTilknytning: String): Norg2Enhet? {
        val norg2ResponseListe = try {
            httpClient.post("$baseUrl/norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
            {
                contentType(ContentType.Application.Json)
                setBody(
                    Norg2Request(
                        geografiskOmraade = geografiskTilknytning,
                        diskresjonskode = DISKRESJONSKODE_ANY
                    )
                )
            }.body<List<Norg2Enhet>>()
        } catch (e: Exception) {
            throw RuntimeException("Hente behandlendeEnhet feilet", e)
        }

        return norg2ResponseListe.firstOrNull { it.status == "Aktiv" }
    }

    suspend fun hentBehandlendeEnhetAdresseBeskyttet(geografiskTilknytning: String): Norg2Enhet? {
        val norg2ResponseListe = try {
            httpClient.post("$baseUrl/norg2/api/v1/arbeidsfordeling/enheter/bestmatch")
            {
                contentType(ContentType.Application.Json)
                setBody(
                    Norg2Request(
                        geografiskTilknytning,
                        diskresjonskode = DISKRESJONSKODE_ADRESSEBESKYTTET
                    )
                )
            }.body<List<Norg2Enhet>>()
        } catch (e: Exception) {
            throw RuntimeException("Hente behandlendeEnhet feilet", e)
        }

        return norg2ResponseListe.firstOrNull { it.status == "Aktiv" }
    }


    companion object {
        const val baseUrl = "http://norg2.org"

        const val DISKRESJONSKODE_ANY = "ANY"
        const val DISKRESJONSKODE_ADRESSEBESKYTTET = "SPSF"
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
data class Norg2Request(
    val geografiskOmraade: String,
    val diskresjonskode: String,
) {
    val behandlingstema = "ab0423"
    val tema: String = "TIL"
}
