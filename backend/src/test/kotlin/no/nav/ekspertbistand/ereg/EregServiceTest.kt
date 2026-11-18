package no.nav.ekspertbistand.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class EregServiceTest {
    private val orgnr = "910825226"

    private fun eregService(expectSuccess: Boolean = true, handler: MockRequestHandler) =
        EregService(EregClient(HttpClient(MockEngine(handler)) { this.expectSuccess = expectSuccess }))

    private fun json(body: String): MockRequestHandler = {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }

    @Test
    fun `hentVirksomhetsnavn gir sammensattnavn`() = runTest {
        val service = eregService(handler = json("""{ "navn": { "sammensattnavn": "Test Org", "navnelinje1": "Linje 1" } }"""))

        assertEquals("Test Org", service.hentVirksomhetsnavn(orgnr))
    }

    @Test
    fun `hentVirksomhetsnavn faller tilbake til navnelinje1`() = runTest {
        val service = eregService(handler = json("""{ "navn": { "navnelinje1": "Linje 1" } }"""))

        assertEquals("Linje 1", service.hentVirksomhetsnavn(orgnr))
    }

    @Test
    fun `hentVirksomhetsnavn gir null når organisasjonen mangler navn`() = runTest {
        val service = eregService(handler = json("""{ "organisasjonsnummer": "$orgnr" }"""))

        assertNull(service.hentVirksomhetsnavn(orgnr))
    }

    @Test
    fun `hentVirksomhetsnavn gir null når Ereg svarer 404`() = runTest {
        for (expectSuccess in listOf(true, false)) {
            val service = eregService(expectSuccess) { respond("", HttpStatusCode.NotFound) }

            assertNull(service.hentVirksomhetsnavn(orgnr), "expectSuccess=$expectSuccess")
        }
    }

    @Test
    fun `hentVirksomhetsnavn kaster når Ereg svarer med serverfeil`() = runTest {
        for (expectSuccess in listOf(true, false)) {
            val service = eregService(expectSuccess) { respond("", HttpStatusCode.InternalServerError) }

            assertFails("expectSuccess=$expectSuccess") { service.hentVirksomhetsnavn(orgnr) }
        }
    }
}
