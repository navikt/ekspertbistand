package no.nav.ekspertbistand.services.entraId

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class EntraIdKlient(
    private val entraIdConfig: EntraIdConfig,
) {

    val client = defaultHttpClient()
    private val tokens = ConcurrentHashMap<String, AccessTokenHolder>()

    suspend fun getToken(scope: String) = tokens.getOrCompute(scope) {
        AccessTokenHolder(fetchToken(scope))
    }.tokenResponse.access_token


    suspend fun evictionLoop() {
        tokens.filter {
            it.value.hasExpired(Instant.now())
        }.forEach {
            tokens.remove(it.key)
        }
    }


    private suspend fun fetchToken(scope: String): TokenResponse {
        return client.request(entraIdConfig.tokenUrl) {
            method = HttpMethod.Post
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "client_credentials")
                        append("client_id", entraIdConfig.clientId)
                        append("client_secret", entraIdConfig.clientSecret)
                        append("scope", scope)
                    }
                )
            )
        }.body<TokenResponse>()
    }

    private suspend fun ConcurrentHashMap<String, AccessTokenHolder>.getOrCompute(
        scope: String,
        block: suspend () -> AccessTokenHolder
    ): AccessTokenHolder {
        if (!containsKey(scope)) {
            this[scope] = block()
        }
        return get(scope)!!
    }
}

class EntraIdConfig(
    var tokenUrl: String = "",
    var clientId: String = "",
    var clientSecret: String = "",
)

internal data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
)

internal const val token_expiry_buffer_seconds = 60 /*sec*/

internal data class AccessTokenHolder(
    val tokenResponse: TokenResponse,
    val createdAt: Instant = Instant.now()
) {
    fun hasExpired(
        now: Instant = Instant.now(),
    ): Boolean {
        return now > createdAt.plusSeconds((tokenResponse.expires_in - token_expiry_buffer_seconds).toLong())
    }
}

