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

    /**
     * Ereg som svarer [hierarki] på oppslag med `inkluderHierarki=true`, og ellers svaret i [enheter] for orgnr.
     * Null eller manglende orgnr gir 404.
     */
    private fun ereg(hierarki: String?, enheter: Map<String, String?> = emptyMap()) = eregService { request ->
        val body = if (request.url.parameters["inkluderHierarki"] == "true") {
            hierarki
        } else {
            enheter[request.url.encodedPath.substringAfterLast("/")]
        }
        if (body == null) {
            respond("", HttpStatusCode.NotFound)
        } else {
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
    }

    private fun juridiskEnhet(sektorkode: String?) =
        """{ "type": "JuridiskEnhet", "juridiskEnhetDetaljer": { "sektorkode": ${sektorkode?.let { "\"$it\"" }} } }"""

    private fun organisasjonsledd(sektorkode: String?) =
        """{ "type": "Organisasjonsledd", "organisasjonsleddDetaljer": { "sektorkode": ${sektorkode?.let { "\"$it\"" }} } }"""

    @Test
    fun `hentSektorkode gir sektorkoden til juridisk enhet rett over virksomheten`() = runTest {
        val service = ereg(
            hierarki = """{ "inngaarIJuridiskEnheter": [{ "organisasjonsnummer": "912345678" }] }""",
            enheter = mapOf("912345678" to juridiskEnhet("2100")),
        )

        assertEquals("2100", service.hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode går gjennom organisasjonsledd til juridisk enhet på toppen`() = runTest {
        // Formen fra Ereg i dev for NAV IKT (990983666): BEDR → ORGL → ORGL → juridisk enhet.
        val service = ereg(
            hierarki = """
                {
                  "organisasjonsnummer": "990983666",
                  "type": "Virksomhet",
                  "virksomhetDetaljer": { "enhetstype": "BEDR" },
                  "bestaarAvOrganisasjonsledd": [{
                    "organisasjonsledd": {
                      "organisasjonsnummer": "990983291",
                      "type": "Organisasjonsledd",
                      "organisasjonsleddOver": [{
                        "organisasjonsledd": {
                          "organisasjonsnummer": "889640782",
                          "type": "Organisasjonsledd",
                          "inngaarIJuridiskEnheter": [{ "organisasjonsnummer": "983887457" }]
                        }
                      }]
                    }
                  }]
                }
            """.trimIndent(),
            enheter = mapOf(
                "990983291" to organisasjonsledd("4900"),
                "889640782" to organisasjonsledd("4900"),
                "983887457" to juridiskEnhet("6100"),
            ),
        )

        assertEquals("6100", service.hentSektorkode("990983666"))
    }

    @Test
    fun `hentSektorkode gir null når virksomheten ikke har enhet over`() = runTest {
        val service = ereg(hierarki = """{ "organisasjonsnummer": "$orgnr" }""")

        assertNull(service.hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode bruker organisasjonsleddet på toppen av en brutt kjede`() = runTest {
        val hierarki = """
            { "bestaarAvOrganisasjonsledd": [{ "organisasjonsledd": { "organisasjonsnummer": "996250318" } }] }
        """.trimIndent()

        assertEquals("6100", ereg(hierarki, mapOf("996250318" to organisasjonsledd("6100"))).hentSektorkode(orgnr))
        assertNull(ereg(hierarki, mapOf("996250318" to organisasjonsledd(null))).hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode gir null når kjeden er dypere enn maks antall nivåer`() = runTest {
        val toppen = """{ "organisasjonsnummer": "900000000", "inngaarIJuridiskEnheter": [{ "organisasjonsnummer": "912345678" }] }"""
        val hierarki = (1..EregService.MAKS_NIVAER).fold(toppen) { over, _ ->
            """{ "organisasjonsleddOver": [{ "organisasjonsledd": $over }] }"""
        }

        assertNull(ereg(hierarki, mapOf("912345678" to juridiskEnhet("6100"))).hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode gir null når virksomheten eller toppen ikke finnes`() = runTest {
        val hierarki = """{ "inngaarIJuridiskEnheter": [{ "organisasjonsnummer": "912345678" }] }"""

        assertNull(ereg(hierarki = null).hentSektorkode(orgnr))
        assertNull(ereg(hierarki, enheter = emptyMap()).hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode gir null når toppen mangler sektorkode`() = runTest {
        val service = ereg(
            hierarki = """{ "inngaarIJuridiskEnheter": [{ "organisasjonsnummer": "912345678" }] }""",
            enheter = mapOf("912345678" to juridiskEnhet(null)),
        )

        assertNull(service.hentSektorkode(orgnr))
    }

    @Test
    fun `hentSektorkode kaster når Ereg svarer med serverfeil`() = runTest {
        for (expectSuccess in listOf(true, false)) {
            val service = eregService(expectSuccess) { respond("", HttpStatusCode.InternalServerError) }

            assertFails("expectSuccess=$expectSuccess") { service.hentSektorkode(orgnr) }
        }
    }
}
