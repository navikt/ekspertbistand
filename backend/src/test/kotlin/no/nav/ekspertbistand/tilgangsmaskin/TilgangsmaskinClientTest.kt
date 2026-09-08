package no.nav.ekspertbistand.tilgangsmaskin

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenExchanger
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TilgangsmaskinClientTest {

    private val userToken = "saksbehandler-token"
    private val brukerIdent = "22420094160"

    private val fakeExchanger = object : AzureAdTokenExchanger {
        override suspend fun exchange(target: String, userToken: String): TokenResponse =
            TokenResponse.Success(accessToken = "fake-token", expiresInSeconds = 3600)
    }

    @Test
    fun `evaluer returnerer Innvilget ved 204`() = runTest {
        var request: HttpRequestData? = null
        val client = client { req ->
            request = req
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        val resultat = client.evaluer(userToken, brukerIdent, Regelsett.KOMPLETT)

        assertEquals(Tilgangsresultat.Innvilget, resultat)
        val captured = request
        assertNotNull(captured)
        assertEquals("/api/v1/komplett", captured.url.encodedPath)
        assertEquals("Bearer fake-token", captured.headers[HttpHeaders.Authorization])
        // Brukers ident sendes som JSON-streng.
        assertEquals("\"$brukerIdent\"", (captured.body as TextContent).text)
    }

    @Test
    fun `evaluer bruker kjerne-sti for kjerneregelsett`() = runTest {
        var request: HttpRequestData? = null
        val client = client { req ->
            request = req
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        client.evaluer(userToken, brukerIdent, Regelsett.KJERNE)

        assertEquals("/api/v1/kjerne", request?.url?.encodedPath)
    }

    @Test
    fun `evaluer returnerer Avvist ved 403 problem-json`() = runTest {
        val problem = """
            {
              "type": "https://confluence.adeo.no/display/TM/Tilgangsmaskin",
              "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE",
              "status": 403,
              "instance": "Z999999/22420094160",
              "brukerIdent": "22420094160",
              "navIdent": "Z999999",
              "traceId": "abc123",
              "begrunnelse": "Du har ikke tilgang til brukere med strengt fortrolig adresse",
              "kanOverstyres": false
            }
        """.trimIndent()

        val client = client {
            respond(
                content = problem,
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        val resultat = client.evaluer(userToken, brukerIdent)

        val avvist = assertIs<Tilgangsresultat.Avvist>(resultat)
        assertEquals("AVVIST_STRENGT_FORTROLIG_ADRESSE", avvist.kode)
        assertEquals(false, avvist.kanOverstyres)
        assertTrue(avvist.begrunnelse!!.contains("strengt fortrolig"))
    }

    @Test
    fun `evaluer kaster ved uventet status`() = runTest {
        val client = client {
            respond(
                content = """{"title":"Uventet respons fra Entra","status":404}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertFailsWith<TilgangsmaskinException> {
            client.evaluer(userToken, brukerIdent)
        }
    }

    @Test
    fun `evaluerBulk parser aggregert respons og grupperer status`() = runTest {
        val body = """
            {
              "ansattId": "Z999999",
              "resultater": [
                { "brukerId": "111", "status": 204 },
                { "brukerId": "222", "status": 403, "detaljer": { "title": "AVVIST_SKJERMING", "kanOverstyres": true } },
                { "brukerId": "333", "status": 404 }
              ]
            }
        """.trimIndent()

        var request: HttpRequestData? = null
        val client = client { req ->
            request = req
            respond(
                content = body,
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val respons = client.evaluerBulk(userToken, listOf("111", "222", "333"))

        assertEquals("/api/v1/bulk/obo", request?.url?.encodedPath)
        assertEquals(1, respons.godkjente.size)
        assertEquals("111", respons.godkjente.first().brukerId)
        assertEquals(1, respons.avviste.size)
        assertEquals("AVVIST_SKJERMING", respons.avviste.first().detaljer?.title)
        assertEquals(1, respons.ukjente.size)
        assertTrue(respons.godkjente.first().innvilget)
    }

    private fun client(handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): TilgangsmaskinClient {
        val engine = MockEngine { request -> handler(request) }
        return TilgangsmaskinClient(
            tokenExchanger = fakeExchanger,
            defaultHttpClient = HttpClient(engine) {},
        )
    }
}
