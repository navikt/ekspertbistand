package no.nav.ekspertbistand.sokos

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.defaultJson
import no.nav.ekspertbistand.sokos.KontoregisterClient.Companion.apiPath
import no.nav.ekspertbistand.sokos.KontoregisterClient.Companion.baseUrl
import no.nav.ekspertbistand.sokos.KontoregisterClient.Companion.targetScope


interface KontoregisterClient {
    suspend fun hentKontonummer(virksomhetsnummer: String): Kontooppslag?

    companion object {
        val baseUrl = basedOnEnv(
            prod = { "https://sokos-kontoregister.prod-fss-pub.nais.io" },
            dev = { "https://sokos-kontoregister-q2.dev-fss-pub.nais.io" },
            other = { "http://localhost:8081" })
        val apiPath = "/kontoregister/api/v1/hent-kontonummer-for-organisasjon"
        val targetScope = basedOnEnv(
            prod = { "api://prod-fss.okonomi.sokos-kontoregister/.default" },
            dev = { "api://dev-fss.okonomi.sokos-kontoregister-q2/.default" },
            other = { "" }
        )
    }
}

class KontoregisterClientImpl(
    defaultHttpClient: HttpClient,
    private val tokenProvider: AzureAdTokenProvider,
) : KontoregisterClient {

    private val httpClient = defaultHttpClient.config {
        expectSuccess = true

        install(ContentNegotiation) {
            json(defaultJson)
        }
    }

    override suspend fun hentKontonummer(virksomhetsnummer: String): Kontooppslag? {
        return try {
            httpClient.get {
                url {
                    takeFrom(baseUrl)
                    path(apiPath, virksomhetsnummer)
                }
                bearerAuth(
                    tokenProvider.token(targetScope).fold(
                        onSuccess = { it.accessToken },
                        onError = { throw RuntimeException("Failed to fetch token: ${it.status} ${it.error}") }
                    )
                )
            }.body<Kontooppslag>()
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                null
            }
            throw e
        }
    }
}

/**
 * ref: https://github.com/navikt/sokos-kontoregister/blob/1ccc9c205a9b4e8f66dbe9c865383b256bcac26b/spec/kontoregister-v1-swagger2.json
 */
@Serializable
data class Kontooppslag(
    /**
     * Organisasjonsnummer
     */
    val mottaker: String,
    /**
     * Kontonummer
     */
    val kontonr: String,
)