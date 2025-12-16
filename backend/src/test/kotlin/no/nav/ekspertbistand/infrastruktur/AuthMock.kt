package no.nav.ekspertbistand.infrastruktur


class MockTokenIntrospector(
    val mocks: (String) -> TokenIntrospectionResponse?,
) : TokenIntrospector {
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