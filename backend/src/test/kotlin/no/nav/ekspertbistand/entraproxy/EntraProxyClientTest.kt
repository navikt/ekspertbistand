package no.nav.ekspertbistand.entraproxy

import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockEntraProxy
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EntraProxyClientTest {

    @Test
    fun `henter enheter for saksbehandler`() = testApplication {
        val navIdent = "A123456"
        mockEntraProxy { ident ->
            assertEquals(navIdent, ident)
            // language=JSON
            """
            [
                { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
                { "enhetnummer": "5678", "navn": "Nav Avdeling Nordpolen" }
            ]
            """
        }

        val client = EntraProxyClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )

        val enheter = client.hentEnheter(navIdent)
        assertEquals(2, enheter.size)
        assertEquals("1234", enheter[0].enhetnummer)
        assertEquals("Nav Avdeling Sydpolen", enheter[0].navn)
        assertEquals("5678", enheter[1].enhetnummer)
        assertEquals("Nav Avdeling Nordpolen", enheter[1].navn)
    }

    @Test
    fun `haandterer ukjente felter i respons`() = testApplication {
        mockEntraProxy {
            // language=JSON
            """
            [
                { "enhetnummer": "1234", "navn": "Nav Kontor", "ekstraFelt": "ignoreres" }
            ]
            """
        }

        val client = EntraProxyClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )

        val enheter = client.hentEnheter("A999999")
        assertEquals(1, enheter.size)
        assertEquals("1234", enheter[0].enhetnummer)
        assertEquals("Nav Kontor", enheter[0].navn)
    }

    @Test
    fun `feiler ved ugyldig token`() = testApplication {
        mockEntraProxy {
            // language=JSON
            """[]"""
        }

        val failingTokenProvider = object : AzureAdTokenProvider {
            override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse {
                return TokenResponse.Error(
                    TokenErrorResponse("unauthorized", "invalid token"),
                    HttpStatusCode.Unauthorized
                )
            }
        }

        val client = EntraProxyClient(
            tokenProvider = failingTokenProvider,
            defaultHttpClient = client
        )

        val exception = org.junit.jupiter.api.assertThrows<Exception> {
            client.hentEnheter("A123456")
        }
        assert(exception.message!!.contains("Failed to get token"))
    }
}

private val mockTokenProvider = object : AzureAdTokenProvider {
    override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse {
        return if (target == EntraProxyClient.targetAudience) TokenResponse.Success(
            "dummytolkien", 3600
        ) else TokenResponse.Error(
            TokenErrorResponse("error", "you shall not pass"),
            HttpStatusCode.BadRequest
        )
    }
}

