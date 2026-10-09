package no.nav.ekspertbistand.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.*
import no.nav.ekspertbistand.entraproxy.Enhet
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyUtilgjengeligException
import no.nav.ekspertbistand.saksbehandling.Role
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Lånt med modifikasjoner fra https://github.com/nais/wonderwalled
 */

private object AuthLogging
private val authLog = AuthLogging.logger()
private val authTeamLog = AuthLogging.teamLogger()

@Serializable
enum class IdentityProvider(val alias: String) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
    IDPORTEN("idporten"),
    TOKEN_X("tokenx"),
}

@Serializable
sealed class TokenResponse {
    @Serializable
    data class Success(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("expires_in")
        val expiresInSeconds: Int,
    ) : TokenResponse() {
        override fun toString() = "TokenResponse.Success(accessToken: SECRET, expiresInSeconds: $expiresInSeconds)"
    }

    @Serializable
    data class Error(
        val error: TokenErrorResponse,
        @Contextual
        val status: HttpStatusCode,
    ) : TokenResponse()

    fun <R> fold(onSuccess: (Success) -> R, onError: (Error) -> R): R =
        when (this) {
            is Success -> onSuccess(this)
            is Error -> onError(this)
        }
}

@Serializable
data class TokenErrorResponse(
    val error: String,
    @SerialName("error_description")
    val errorDescription: String,
)

@Serializable(with = TokenIntrospectionResponseSerializer::class)
data class TokenIntrospectionResponse(
    val active: Boolean,
    val error: String?,
    @Transient val other: Map<String, Any?> = mutableMapOf(),
)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(forClass = TokenIntrospectionResponse::class)
object TokenIntrospectionResponseSerializer : KSerializer<TokenIntrospectionResponse> {
    override fun deserialize(decoder: Decoder): TokenIntrospectionResponse {
        val jsonDecoder = decoder as JsonDecoder
        jsonDecoder.decodeJsonElement().jsonObject.let { json ->
            return TokenIntrospectionResponse(
                active = json["active"]?.jsonPrimitive?.boolean ?: false,
                error = json["error"]?.jsonPrimitive?.contentOrNull,
                other = json.filter { it.key != "active" && it.key != "error" }
                    .mapValues {
                        when (val value = it.value) {
                            is JsonPrimitive -> value.contentOrNull
                            is JsonArray -> value.map { el -> el.jsonPrimitive.contentOrNull }
                            // skip nested objects for now
                            //is JsonObject -> value.jsonObject.mapValues { el -> el.value.jsonPrimitive.contentOrNull }
                            else -> null
                        }
                    }
            )
        }
    }
}

class AuthConfig(
    val tokenEndpoint: String,
    val tokenExchangeEndpoint: String,
    val tokenIntrospectionEndpoint: String,
) {
    companion object {
        val nais: AuthConfig by lazy {
            AuthConfig(
                tokenEndpoint = System.getenv("NAIS_TOKEN_ENDPOINT"),
                tokenExchangeEndpoint = System.getenv("NAIS_TOKEN_EXCHANGE_ENDPOINT"),
                tokenIntrospectionEndpoint = System.getenv("NAIS_TOKEN_INTROSPECTION_ENDPOINT"),
            )
        }
    }
}

interface TokenXTokenExchanger {
    suspend fun exchange(target: String, userToken: String): TokenResponse
}

interface AzureAdTokenProvider {
    suspend fun token(target: String, additionalParameters: Map<String, String> = mapOf()): TokenResponse
}

interface TokenXTokenIntrospector {
    suspend fun introspect(accessToken: String): TokenIntrospectionResponse
}

interface AzureAdTokenIntrospector {
    suspend fun introspect(accessToken: String): TokenIntrospectionResponse
}

interface AzureAdTokenExchanger {
    suspend fun exchange(target: String, userToken: String): TokenResponse
}

class TokenXAuthClient(
    config: AuthConfig,
    httpClient: HttpClient,
) : AuthClient(config, IdentityProvider.TOKEN_X, httpClient), TokenXTokenExchanger, TokenXTokenIntrospector {
    override suspend fun exchange(target: String, userToken: String): TokenResponse = exchangeToken(target, userToken)
    override suspend fun introspect(accessToken: String): TokenIntrospectionResponse = introspectToken(accessToken)
}

class AzureAdAuthClient(
    config: AuthConfig,
    httpClient: HttpClient,
) : AuthClient(config, IdentityProvider.AZURE_AD, httpClient), AzureAdTokenProvider, AzureAdTokenIntrospector, AzureAdTokenExchanger {
    override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse =
        fetchToken(target, additionalParameters)

    override suspend fun exchange(target: String, userToken: String): TokenResponse = exchangeToken(target, userToken)
    override suspend fun introspect(accessToken: String): TokenIntrospectionResponse = introspectToken(accessToken)
}

abstract class AuthClient(
    private val config: AuthConfig,
    private val provider: IdentityProvider,
    defaultHttpClient: HttpClient,
) {
    protected val httpClient = defaultHttpClient.config {
        install(ContentNegotiation) {
            json(defaultJson)
        }
    }

    protected suspend fun fetchToken(target: String, additionalParameters: Map<String, String>) = try {
        httpClient.submitForm(config.tokenEndpoint, parameters {
            set("target", target)
            set("identity_provider", provider.alias)
            additionalParameters.forEach { (key, value) -> set(key, value) }
        }).body<TokenResponse.Success>()
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

    protected suspend fun exchangeToken(target: String, userToken: String) = try {
        httpClient.submitForm(config.tokenExchangeEndpoint, parameters {
            set("target", target)
            set("user_token", userToken)
            set("identity_provider", provider.alias)
        }).body<TokenResponse.Success>()
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

    protected suspend fun introspectToken(accessToken: String) =
        httpClient.submitForm(config.tokenIntrospectionEndpoint, parameters {
            set("token", accessToken)
            set("identity_provider", provider.alias)
        }).body<TokenIntrospectionResponse>()
}


data class TokenXPrincipal(
    val clientId: String,
    val pid: String,
    val subjectToken: String,
)

const val TOKENX_PROVIDER = "TOKEN_X"

@OptIn(ExperimentalTime::class)
data class AzureAdPrincipal(
    val navIdent: String,
    val navn: String,
    val epost: String?,
    val gjeldendeEnhet: Enhet,
    val groups: List<String>,
    val enheter: List<Enhet>,
    /** Når grupper, ansattdata og enheter ble hentet fra entra-proxy. Til feilsøking av cachen. */
    val berikelseUpdatedAt: Instant,
    val subjectToken: String,
) {
    /** True dersom principal er medlem av gruppen til [role]. */
    fun harRolle(role: Role): Boolean = role in Role.fromGroups(groups)

    /** True dersom saksbehandler har tilgang til [enhet] (enhetsnummer) ifølge entra-proxy. */
    fun harTilgangTilEnhet(enhet: String): Boolean = enheter.any { it.enhetnummer == enhet }
}

const val AZURE_AD_PROVIDER = "AZURE_AD"

@OptIn(ExperimentalTime::class)
fun Application.configureAuthentication() {
    install(Authentication) {
        bearer(TOKENX_PROVIDER) {
            authenticate { credentials ->
                with(application.dependencies.resolve<TokenXTokenIntrospector>().introspect(credentials.token)) {
                    if (!active) return@authenticate null

                    /**
                     * Dersom man trenger varierende claims validering per endepunkt kan man flytte validering
                     * herfra til autentiseringsblokken i routing på modulen det gjelder
                     */

                    val pid = other["pid"]!!
                    val clientId = other["client_id"]!!
                    val acr = other["acr"]!!

                    val acrValid = acr in listOf(
                        "idporten-loa-high",
                        "Level4",
                    )

                    if (acrValid && pid is String && clientId is String) {
                        TokenXPrincipal(
                            clientId = clientId,
                            pid = pid,
                            subjectToken = credentials.token
                        )
                    } else {
                        null
                    }


                }
            }
        }

        bearer(AZURE_AD_PROVIDER) {
            authenticate { credentials ->
                with(application.dependencies.resolve<AzureAdTokenIntrospector>().introspect(credentials.token)) {
                    if (!active) return@authenticate null

                    val navIdent = other["NAVident"] as? String
                        ?: return@authenticate null

                    /**
                     * Grupper, navn og enheter hentes fra entra-proxy, ikke fra token-claims, og caches
                     * per token. Feil hos entra-proxy gir 503 (via StatusPages), ikke 401, slik at
                     * brukeren ikke sendes inn i en innloggingsløkke.
                     */
                    val berikelse = try {
                        application.dependencies.resolve<EntraBerikelseCache>().hent(credentials.token, navIdent)
                    } catch (e: EntraProxyUtilgjengeligException) {
                        authLog.error("Feil ved oppslag mot entra-proxy ({}), avviser request", e.cause?.javaClass?.simpleName)
                        authTeamLog.error("Feil ved oppslag mot entra-proxy for navIdent=$navIdent", e)
                        throw e
                    }

                    AzureAdPrincipal(
                        navIdent = navIdent,
                        navn = berikelse.navn,
                        epost = berikelse.epost,
                        gjeldendeEnhet = berikelse.gjeldendeEnhet,
                        groups = berikelse.groups,
                        enheter = berikelse.enheter,
                        berikelseUpdatedAt = berikelse.updatedAt,
                        subjectToken = credentials.token,
                    )
                }
            }
        }
    }
}
