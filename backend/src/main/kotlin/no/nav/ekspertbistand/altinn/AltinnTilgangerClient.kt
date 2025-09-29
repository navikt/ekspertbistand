package no.nav.ekspertbistand.altinn

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.*



/**
 * ex: AltinnTilgangerClient(AuthClient(TexasAuthConfig.nais(), IdentityProvider.TOKEN_X))
 */
class AltinnTilgangerClient(
    private val authClient: TokenExchanger,
    private val httpClient: HttpClient = defaultHttpClient(customizeMetrics = {
        clientName = "altinn.tilganger.client"
    }) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
        }
    }
) {
    companion object {
        const val altinn2Tjenestekode = "5384:1"
        const val altinn3Ressursid = "nav_ekspertbistand_soknad" // TODO: navn på denne er ikke bestemt enda
        const val ingress = "http://arbeidsgiver-altinn-tilganger.fager" // service discovery
    }

    private val targetAudience = "${NaisEnvironment.clusterName}:fager:arbeidsgiver-altinn-tilganger"

    suspend fun hentTilganger(subjectToken: String): AltinnTilgangerClientResponse {
        val token = authClient.exchange(
            target = targetAudience,
            userToken = subjectToken,
        )

        return httpClient.post {
            url {
                takeFrom(ingress)
                path("/altinn-tilganger")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            bearerAuth(token.fold({ it.accessToken }, { throw Exception("Failed to exchange token: ${it.error}") }))

            setBody(mapOf(
                "filter" to mapOf(
                    "altinn2Tilganger" to listOf(altinn2Tjenestekode),
                    "altinn3Tilganger" to listOf(altinn3Ressursid),
                )
            ))

        }.body<AltinnTilgangerClientResponse>()
    }
}


@Serializable
data class AltinnTilgangerClientResponse(
    val isError: Boolean,
    val hierarki: List<AltinnTilgang>,
    val orgNrTilTilganger: Map<String, Set<String>>,
    val tilgangTilOrgNr: Map<String, Set<String>>,
) {

    @Serializable
    data class AltinnTilgang(
        val orgnr: String,
        val navn: String,
        val underenheter: List<AltinnTilgang>,

        val altinn3Tilganger: Set<String>,
        val altinn2Tilganger: Set<String>,
    )
}