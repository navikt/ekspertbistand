package no.nav.ekspertbistand.infrastruktur

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.mocks.testAnsattJson
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backend skal bare godta tokens fra frontend (TokenX) og saksbehandling (Entra ID).
 * Manglende eller feiltypet klient-claim gir 401. Gyldig token fra en annen app gir 403.
 */
class KlientvalideringTest {

    private val tokenXToken = mockIntrospectionResponse
    private val azureToken = mockAzureAdIntrospectionResponse

    // TokenX

    @Test
    fun `TokenX fra frontend slipper inn`() = klientvalidering(tokenX = tokenXToken) {
        assertEquals(HttpStatusCode.OK, get("/tokenx"))
    }

    @Test
    fun `TokenX fra ukjent klient gir 403 og telles`() {
        val før = avvist(TOKENX_PROVIDER, "ukjent")
        klientvalidering(tokenX = tokenXToken.withClientId("dev-gcp:annet-team:annen-app")) {
            assertEquals(HttpStatusCode.Forbidden, get("/tokenx"))
        }
        assertTrue(avvist(TOKENX_PROVIDER, "ukjent") > før)
    }

    @Test
    fun `TokenX der klientnavnet bare starter med frontend gir 403`() =
        klientvalidering(tokenX = tokenXToken.withClientId("$TEST_TOKENX_CLIENT_ID-e2e-mock")) {
            assertEquals(HttpStatusCode.Forbidden, get("/tokenx"))
        }

    @Test
    fun `TokenX med saksbehandlings Entra-klient som client_id gir 403`() =
        klientvalidering(tokenX = tokenXToken.withClientId(TEST_AZP)) {
            assertEquals(HttpStatusCode.Forbidden, get("/tokenx"))
        }

    @Test
    fun `TokenX uten client_id gir 401`() =
        klientvalidering(tokenX = tokenXToken.withoutClaim("client_id")) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    @Test
    fun `TokenX med client_id som liste gir 401`() =
        klientvalidering(tokenX = tokenXToken.withClaim("client_id", listOf(TEST_TOKENX_CLIENT_ID))) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    @Test
    fun `TokenX uten pid gir 401, ikke 500`() =
        klientvalidering(tokenX = tokenXToken.withoutClaim("pid")) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    @Test
    fun `TokenX uten acr gir 401, ikke 500`() =
        klientvalidering(tokenX = tokenXToken.withoutClaim("acr")) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    @Test
    fun `TokenX med for lavt sikkerhetsnivå gir 401`() =
        klientvalidering(tokenX = tokenXToken.withAcr("idporten-loa-substantial")) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    // Entra ID

    @Test
    fun `Entra fra saksbehandling slipper inn`() = klientvalidering(azure = azureToken) { entraProxyKall ->
        assertEquals(HttpStatusCode.OK, get("/azure"))
        assertTrue(entraProxyKall.get() > 0)
    }

    @Test
    fun `Entra fra ukjent klient gir 403 uten kall mot entra-proxy`() {
        val før = avvist(AZURE_AD_PROVIDER, "ukjent")
        klientvalidering(azure = azureToken.withAzp("11111111-1111-1111-1111-111111111111")) { entraProxyKall ->
            assertEquals(HttpStatusCode.Forbidden, get("/azure"))
            assertEquals(0, entraProxyKall.get())
        }
        assertTrue(avvist(AZURE_AD_PROVIDER, "ukjent") > før)
    }

    @Test
    fun `Entra med frontendens TokenX-klient som azp gir 403`() =
        klientvalidering(azure = azureToken.withAzp(TEST_TOKENX_CLIENT_ID)) { entraProxyKall ->
            assertEquals(HttpStatusCode.Forbidden, get("/azure"))
            assertEquals(0, entraProxyKall.get())
        }

    @Test
    fun `Entra uten azp gir 401 uten kall mot entra-proxy`() =
        klientvalidering(azure = azureToken.withoutClaim("azp")) { entraProxyKall ->
            assertEquals(HttpStatusCode.Unauthorized, get("/azure"))
            assertEquals(0, entraProxyKall.get())
        }

    @Test
    fun `Entra maskin-til-maskin gir 401 selv fra tillatt klient`() =
        klientvalidering(azure = azureToken.withClaim("idtyp", "app")) { entraProxyKall ->
            assertEquals(HttpStatusCode.Unauthorized, get("/azure"))
            assertEquals(0, entraProxyKall.get())
        }

    // Token-typene kan ikke brukes på hverandres ruter

    @Test
    fun `gyldig TokenX-token slipper ikke inn på Entra-ruter`() =
        klientvalidering(tokenX = tokenXToken, azure = null) {
            assertEquals(HttpStatusCode.Unauthorized, get("/azure"))
        }

    @Test
    fun `gyldig Entra-token slipper ikke inn på TokenX-ruter`() =
        klientvalidering(tokenX = null, azure = azureToken) {
            assertEquals(HttpStatusCode.Unauthorized, get("/tokenx"))
        }

    private fun avvist(provider: String, arsak: String) =
        Metrics.meterRegistry.counter("auth_klient_avvist_total", "provider", provider, "arsak", arsak).count()

    private class Kontekst(private val client: HttpClient) {
        suspend fun get(path: String): HttpStatusCode =
            client.get(path) { bearerAuth("token") }.status
    }

    private fun klientvalidering(
        tokenX: TokenIntrospectionResponse? = null,
        azure: TokenIntrospectionResponse? = null,
        block: suspend Kontekst.(entraProxyKall: AtomicInteger) -> Unit,
    ) = testApplication {
        val entraProxyKall = AtomicInteger()
        mockEntraProxyFull(ansattProvider = { entraProxyKall.incrementAndGet(); testAnsattJson(it) })
        val client = createClient { install(ContentNegotiation) { json() } }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<TokenXTokenIntrospector> { MockTokenIntrospector { tokenX } }
                provide<AzureAdTokenIntrospector> { MockAzureAdIntrospector { azure } }
            }
            configureTestAuthentication()
            configureServer()
            routing {
                authenticate(TOKENX_PROVIDER) { get("/tokenx") { call.respondText("ok") } }
                authenticate(AZURE_AD_PROVIDER) { get("/azure") { call.respondText("ok") } }
            }
        }

        Kontekst(client).block(entraProxyKall)
    }
}
