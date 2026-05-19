package no.nav.ekspertbistand.aareg

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AaregClientTest {

    private val fakeTokenProvider = object : AzureAdTokenProvider {
        override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse =
            TokenResponse.Success(accessToken = "fake-token", expiresInSeconds = 3600)
    }

    @Test
    fun `hentArbeidsforhold returnerer liste med arbeidsforhold`() = runTest {
        val fnr = "01010112345"
        val orgnr = "987654321"
        val responseJson = """
            [
              {
                "arbeidssted": {
                  "type": "Underenhet",
                  "identer": [
                    { "type": "ORGANISASJONSNUMMER", "ident": "$orgnr", "gjeldende": true }
                  ]
                },
                "ansettelsesperiode": {
                  "startdato": "2020-01-01",
                  "sluttdato": null
                }
              }
            ]
        """.trimIndent()

        var capturedRequest: HttpRequestData? = null
        val client = aaregClient(responseJson) { capturedRequest = it }

        val result = client.hentArbeidsforhold(fnr, orgnr)

        assertEquals(1, result.size)
        val arbeidsforhold = result.first()
        assertNotNull(arbeidsforhold.arbeidssted)
        assertEquals("Underenhet", arbeidsforhold.arbeidssted?.type)
        assertEquals(1, arbeidsforhold.arbeidssted?.identer?.size)
        assertEquals("ORGANISASJONSNUMMER", arbeidsforhold.arbeidssted?.identer?.first()?.type)
        assertEquals(orgnr, arbeidsforhold.arbeidssted?.identer?.first()?.ident)
        assertTrue(arbeidsforhold.arbeidssted?.identer?.first()?.gjeldende == true)
        assertEquals("2020-01-01", arbeidsforhold.ansettelsesperiode?.startdato)

        val request = capturedRequest
        assertNotNull(request)
        assertEquals(fnr, request.headers["Nav-Personident"])
        assertEquals(orgnr, request.headers["Nav-Arbeidsstedident"])
        assertTrue(request.url.fullPath.contains("arbeidsforholdstatus=AKTIV"))
        assertEquals("Bearer fake-token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `hentArbeidsforhold returnerer tom liste`() = runTest {
        val client = aaregClient("[]")

        val result = client.hentArbeidsforhold("01010112345", "987654321")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `hentArbeidsforhold kaster ved HTTP-feil`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"meldinger": ["Ikke tilgang"]}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = AaregClient(
            tokenProvider = fakeTokenProvider,
            defaultHttpClient = HttpClient(engine) {},
        )

        assertFailsWith<Exception> {
            client.hentArbeidsforhold("01010112345", "987654321")
        }
    }

    @Test
    fun `hentArbeidsforhold kaster ved 401 Unauthorized`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"meldinger": ["Ugyldig token"]}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = AaregClient(
            tokenProvider = fakeTokenProvider,
            defaultHttpClient = HttpClient(engine) {},
        )

        assertFailsWith<Exception> {
            client.hentArbeidsforhold("01010112345", "987654321")
        }
    }

    @Test
    fun `hentArbeidsforhold kaster ved 404 Not Found`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"meldinger": ["Ikke funnet"]}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = AaregClient(
            tokenProvider = fakeTokenProvider,
            defaultHttpClient = HttpClient(engine) {},
        )

        assertFailsWith<Exception> {
            client.hentArbeidsforhold("01010112345", "987654321")
        }
    }

    private fun aaregClient(
        responseJson: String,
        capture: (HttpRequestData) -> Unit = {}
    ): AaregClient {
        val engine = MockEngine { request ->
            capture(request)
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return AaregClient(
            tokenProvider = fakeTokenProvider,
            defaultHttpClient = HttpClient(engine) {},
        )
    }
}

