package no.nav.ekspertbistand.skjerming

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.infrastruktur.NaisEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SkjermedePersonerPipKlientTest {

    private val fnr = "01010112345"

    @Test
    fun `skjermet person gir true og sender personident til POST skjermet`() = runTest {
        var request: HttpRequestData? = null
        var tokenTarget: String? = null
        val klient = klient(svar = "true", tokenTarget = { tokenTarget = it }) { request = it }

        assertTrue(klient.erSkjermet(fnr))

        val sendt = assertNotNull(request)
        assertEquals(HttpMethod.Post, sendt.method)
        assertEquals("/skjermet", sendt.url.encodedPath)
        assertEquals("""{"personident":"$fnr"}""", (sendt.body as TextContent).text)
        assertEquals("${NaisEnvironment.clusterName}:nom:skjermede-personer-pip", tokenTarget)
    }

    @Test
    fun `person som ikke er skjermet gir false`() = runTest {
        assertFalse(klient(svar = "false").erSkjermet(fnr))
    }

    @Test
    fun `feilstatus og uventet svar kaster SkjermingException`() = runTest {
        assertFailsWith<SkjermingException> { klient(svar = "", status = HttpStatusCode.Forbidden).erSkjermet(fnr) }
        assertFailsWith<SkjermingException> { klient(svar = "kanskje").erSkjermet(fnr) }
    }

    private fun klient(
        svar: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        tokenTarget: (String) -> Unit = {},
        capture: (HttpRequestData) -> Unit = {},
    ): SkjermedePersonerPipKlient {
        val engine = MockEngine { request ->
            capture(request)
            respond(
                content = svar,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val tokenProvider = object : AzureAdTokenProvider {
            override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse {
                tokenTarget(target)
                return TokenResponse.Success(accessToken = "token", expiresInSeconds = 3600)
            }
        }
        return SkjermedePersonerPipKlient(tokenProvider, HttpClient(engine) {})
    }
}
