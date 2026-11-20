package no.nav.ekspertbistand.infrastruktur

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KlientvalideringTest {

    private fun ApplicationTestBuilder.oppsett(
        tokenX: TokenIntrospectionResponse = mockIntrospectionResponse,
        azureAd: TokenIntrospectionResponse = mockAzureAdIntrospectionResponse,
    ) = application {
        dependencies {
            provide<TokenXTokenIntrospector> { MockTokenIntrospector { tokenX } }
            provide<AzureAdTokenIntrospector> { MockAzureAdIntrospector { azureAd } }
        }
        configureAuthentication()
        routing {
            authenticate(TOKENX_PROVIDER) { get("/tokenx") { call.respond(HttpStatusCode.OK) } }
            authenticate(AZURE_AD_PROVIDER) { get("/azure") { call.respond(HttpStatusCode.OK) } }
        }
    }

    @Test
    fun `TokenX-token fra søknadsløsningen slippes inn`() = testApplication {
        oppsett()

        assertEquals(HttpStatusCode.OK, client.get("/tokenx") { bearerAuth("token") }.status)
    }

    @Test
    fun `TokenX-token fra annen klient gir 401`() = testApplication {
        oppsett(tokenX = mockIntrospectionResponse.withClientId(NaisEnvironment.saksbehandlingFrontend))

        assertEquals(HttpStatusCode.Unauthorized, client.get("/tokenx") { bearerAuth("token") }.status)
    }

    @Test
    fun `Entra-token fra annen klient gir 401`() = testApplication {
        oppsett(azureAd = mockAzureAdIntrospectionResponse.withAzpName(NaisEnvironment.soknadFrontend))

        assertEquals(HttpStatusCode.Unauthorized, client.get("/azure") { bearerAuth("token") }.status)
    }

    @Test
    fun `Entra-token uten azp_name gir 401`() = testApplication {
        oppsett(azureAd = mockAzureAdIntrospectionResponse.copy(other = mockAzureAdIntrospectionResponse.other - "azp_name"))

        assertEquals(HttpStatusCode.Unauthorized, client.get("/azure") { bearerAuth("token") }.status)
    }
}
