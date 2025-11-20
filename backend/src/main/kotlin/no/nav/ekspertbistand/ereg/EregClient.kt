package no.nav.ekspertbistand.ereg

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.path
import io.ktor.http.takeFrom
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient

class EregClient(
    val httpClient: HttpClient = defaultHttpClient({
        clientName = "ereg.client"
    }) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
        }
    }
) {
    companion object {
        val ingress = basedOnEnv(
            prod = "https://ereg-services.prod-fss-pub.nais.io",
            dev = "https://ereg-services.dev-fss-pub.nais.io",
            other = "http://ereg-services.mock.svc.cluster.local",
        )
        const val API_PATH = "/v2/organisasjon/"
    }

    suspend fun hentPostAdresse(orgnr: String): List<Postadresse> {
        val organisasjon = httpClient.get {
            url {
                takeFrom(ingress)
                path(API_PATH + orgnr)
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<EregOrganisasjon>()

        return organisasjon.organisasjonDetaljer?.postadresser ?: emptyList()
    }
}

@Serializable
private data class EregOrganisasjon(
    val organisasjonDetaljer: OrganisasjonDetaljer? = null,
)

@Serializable
data class OrganisasjonDetaljer(
    val postadresser: List<Postadresse> = emptyList(),
)

@Serializable
data class Postadresse(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val landkode: String? = null,
    val kommunenummer: String? = null,
    val type: String? = null,
)