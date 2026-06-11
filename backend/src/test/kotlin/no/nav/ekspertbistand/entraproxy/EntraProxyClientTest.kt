package no.nav.ekspertbistand.entraproxy

import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockEntraProxy
import no.nav.ekspertbistand.mocks.mockEntraProxyAnsatt
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

    @Test
    fun `henter ansattdetaljer for navIdent`() = testApplication {
        val navIdent = "A123456"
        mockEntraProxyAnsatt { ident ->
            assertEquals(navIdent, ident)
            // language=JSON
            """
            {
                "navIdent": "A123456",
                "visningNavn": "Tore Tang",
                "fornavn": "Tore",
                "etternavn": "Tang",
                "epost": "tore.tang@nav.no",
                "enhet": { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
                "tident": "T123456"
            }
            """
        }

        val client = EntraProxyClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )

        val ansatt = client.hentAnsatt(navIdent)
        assertEquals("A123456", ansatt.navIdent)
        assertEquals("Tore Tang", ansatt.visningNavn)
        assertEquals("Tore", ansatt.fornavn)
        assertEquals("Tang", ansatt.etternavn)
        assertEquals("tore.tang@nav.no", ansatt.epost)
        assertEquals("1234", ansatt.enhet.enhetnummer)
        assertEquals("Nav Avdeling Sydpolen", ansatt.enhet.navn)
        assertEquals("T123456", ansatt.tident)
    }

    @Test
    fun `haandterer null-felter i ansatt-respons`() = testApplication {
        mockEntraProxyAnsatt {
            // language=JSON
            """
            {
                "navIdent": "A999999",
                "visningNavn": null,
                "fornavn": null,
                "etternavn": null,
                "epost": null,
                "enhet": { "enhetnummer": "5678", "navn": "Nav Kontor" },
                "tident": "T999999"
            }
            """
        }

        val client = EntraProxyClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )

        val ansatt = client.hentAnsatt("A999999")
        assertEquals("A999999", ansatt.navIdent)
        assertEquals(null, ansatt.visningNavn)
        assertEquals(null, ansatt.fornavn)
        assertEquals(null, ansatt.etternavn)
        assertEquals(null, ansatt.epost)
        assertEquals("5678", ansatt.enhet.enhetnummer)
        assertEquals("T999999", ansatt.tident)
    }

    @Test
    fun `haandterer ukjente felter i ansatt-respons`() = testApplication {
        mockEntraProxyAnsatt {
            // language=JSON
            """
            {
                "navIdent": "A123456",
                "visningNavn": "Tore Tang",
                "fornavn": "Tore",
                "etternavn": "Tang",
                "epost": "tore.tang@nav.no",
                "enhet": { "enhetnummer": "1234", "navn": "Nav Kontor" },
                "tident": "T123456",
                "ukjentFelt": "ignoreres"
            }
            """
        }

        val client = EntraProxyClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )

        val ansatt = client.hentAnsatt("A123456")
        assertEquals("A123456", ansatt.navIdent)
        assertEquals("Tore Tang", ansatt.visningNavn)
    }

    @Test
    fun `feiler ved ugyldig token for hentAnsatt`() = testApplication {
        mockEntraProxyAnsatt {
            // language=JSON
            """{}"""
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
            client.hentAnsatt("A123456")
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

