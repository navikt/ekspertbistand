package no.nav.ekspertbistand.infrastruktur

import kotlin.plus

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
    .withClientId("test")

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

fun TokenIntrospectionResponse.withNavIdent(navIdent: String) =
    this.copy(other = this.other + ("NAVident" to navIdent))

fun TokenIntrospectionResponse.withGroups(groups: List<String>) =
    this.copy(other = this.other + ("groups" to groups))
