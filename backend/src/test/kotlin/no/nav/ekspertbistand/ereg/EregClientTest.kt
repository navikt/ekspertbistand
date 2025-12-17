package no.nav.ekspertbistand.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EregClientTest {
    @Test
    fun `hentPostAdresse henter og parser adresser`() = runTest {
        val orgnr = "910825226"
        val responseJson = """
            {
              "organisasjonsnummer": "$orgnr",
              "navn": { "sammensattnavn": "Test Org" },
              "organisasjonDetaljer": {
                "postadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "adresselinje2": "C/O NAV",
                    "postnummer": "0557",
                    "poststed": "Oslo",
                    "landkode": "NO"
                  }
                ],
                "forretningsadresser": [
                  { "adresselinje1": "Forretning 1", "postnummer": "0456" }
                ]
              }
            }
        """.trimIndent()

        var capturedRequest: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = responseJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val eregClient = EregClient(defaultHttpClient = client)

        val postadresser = eregClient.hentPostAdresse(orgnr)
        assertEquals(1, postadresser.size)
        val adresse = postadresser.first()
        assertEquals("Testveien 1", adresse.adresselinje1)
        assertEquals("0557", adresse.postnummer)
        assertEquals("Oslo", adresse.poststed)

        val forretningsadresser = eregClient.hentForretningsadresse(orgnr)
        assertEquals(1, forretningsadresser.size)
        assertEquals("Forretning 1", forretningsadresser.first().adresselinje1)

        val organisasjon = eregClient.hentOrganisasjon(orgnr)
        assertEquals(orgnr, organisasjon.organisasjonsnummer)
        assertEquals("Forretning 1", organisasjon.organisasjonDetaljer?.forretningsadresser?.first()?.adresselinje1)

        val request = capturedRequest
        assertNotNull(request, "request should be captured")
        assertEquals("/v2/organisasjon/$orgnr", request.url.fullPath)
        assertEquals(ContentType.Application.Json, request.headers[HttpHeaders.Accept]?.let { ContentType.parse(it) })
        assertEquals(ContentType.Application.Json, request.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) })
    }
}
