package no.nav.ekspertbistand.infrastruktur

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlin.plus

const val TEST_TOKENX_CLIENT_ID = "test:fager:ekspertbistand-frontend"
const val TEST_AZP = "00000000-0000-0000-0000-00000000a2d0"

/** Matcher standardverdiene for `client_id` og `azp` i fixturene under. */
val testTillatteKlienter = TillatteKlienter(
    tokenX = setOf(TEST_TOKENX_CLIENT_ID),
    azureAd = setOf(TEST_AZP),
)

/** Autentisering som i prod, med [testTillatteKlienter] i stedet for oppslag i Nais-miljøet. */
fun Application.configureTestAuthentication() {
    dependencies { provide<TillatteKlienter> { testTillatteKlienter } }
    configureAuthentication()
}

class MockTokenIntrospector(
    val mocks: (String) -> TokenIntrospectionResponse?,
) : TokenXTokenIntrospector {
    override suspend fun introspect(accessToken: String) =
        mocks(accessToken) ?: TokenIntrospectionResponse(
            active = false,
            error = "no introspect response mocked for $accessToken"
        )
}

val mockIntrospectionResponse = TokenIntrospectionResponse(
    active = true,
    error = null,
    other = mutableMapOf(),
)
    .withPid("0")
    .withAcr("idporten-loa-high")
    .withClientId(TEST_TOKENX_CLIENT_ID)

fun TokenIntrospectionResponse.withPid(pid: String) = this.copy(other = this.other + ("pid" to pid))
fun TokenIntrospectionResponse.withClientId(clientId: String) =
    this.copy(other = this.other + ("client_id" to clientId))

fun TokenIntrospectionResponse.withAcr(acr: String) = this.copy(other = this.other + ("acr" to acr))

val successAzureAdTokenProvider = object : AzureAdTokenProvider {
    override suspend fun token(
        target: String, additionalParameters: Map<String, String>
    ) = TokenResponse.Success("access_token", 3600)
}

val successTokenXTokenExchanger = object : TokenXTokenExchanger {
    override suspend fun exchange(
        target: String, userToken: String
    ) = TokenResponse.Success("access_token", 3600)
}

class MockAzureAdIntrospector(
    val mocks: (String) -> TokenIntrospectionResponse?,
) : AzureAdTokenIntrospector {
    override suspend fun introspect(accessToken: String) =
        mocks(accessToken) ?: TokenIntrospectionResponse(
            active = false,
            error = "no introspect response mocked for $accessToken"
        )
}

val mockAzureAdIntrospectionResponse = TokenIntrospectionResponse(
    active = true,
    error = null,
    other = mutableMapOf(),
)
    .withNavIdent("A123456")
    .withGroups(emptyList())
    .withAzp(TEST_AZP)

fun TokenIntrospectionResponse.withAzp(azp: String) =
    this.copy(other = this.other + ("azp" to azp))

fun TokenIntrospectionResponse.withoutClaim(claim: String) =
    this.copy(other = this.other - claim)

fun TokenIntrospectionResponse.withClaim(claim: String, value: Any?) =
    this.copy(other = this.other + (claim to value))

fun TokenIntrospectionResponse.withNavIdent(navIdent: String) =
    this.copy(other = this.other + ("NAVident" to navIdent))

fun TokenIntrospectionResponse.withGroups(groups: List<String>) =
    this.copy(other = this.other + ("groups" to groups))
