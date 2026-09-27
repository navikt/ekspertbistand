package no.nav.ekspertbistand.dokument.gotenberg

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.dokument.pdf.DokumentRenderer
import no.nav.ekspertbistand.dokument.pdf.Format
import no.nav.ekspertbistand.dokument.pdf.PdfGenerationException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider
import org.verapdf.pdfa.Foundries
import org.verapdf.pdfa.flavours.PDFAFlavour
import java.io.File
import kotlin.test.assertTrue

/**
 * Integrasjonstest mot ekte Gotenberg (§7.2). Krever at Gotenberg kjører på `http://localhost:3003`,
 * enten fra `docker compose -f backend/docker-compose.yml up -d` lokalt eller som service-container i
 * CI. Testcontainers brukes ikke, på samme måte som Postgres-testene.
 */
class GotenbergIntegrationTest {

    private val httpClient = HttpClient(CIO)
    private val client = GotenbergClient(httpClient)
    private val renderer = DokumentRenderer()

    private val templatesDir = File("src/main/resources/dokumentmaler/templates")

    init {
        VeraGreenfieldFoundryProvider.initialise()
    }

    @TestFactory
    fun `alle maler og testdata gir gyldig PDF-A-2b`(): List<DynamicTest> =
        templatesDir.listFiles { f -> f.isDirectory && File(f, "template.hbs").exists() }
            ?.sortedBy { it.name }
            ?.flatMap { mal ->
                val testdata = File(mal, "testdata").listFiles { f -> f.extension == "json" }?.sortedBy { it.name }
                    ?: emptyList()
                testdata.map { fil ->
                    DynamicTest.dynamicTest("${mal.name} / ${fil.name}") {
                        val data = Json.parseToJsonElement(fil.readText()) as JsonObject
                        val html = renderer.renderHtml(mal.name, data, Format.PDF)
                        val pdf = runBlocking { client.tilPdfA(html) }

                        assertTrue(erGyldigPdfA2b(pdf), "${mal.name}/${fil.name} skal være gyldig PDF/A-2b")
                        assertTrue(tekstFra(pdf).isNotBlank(), "${mal.name}/${fil.name} skal ha tekst")
                        forventedeVerdier[mal.name]?.forEach { verdi ->
                            assertTrue(tekstFra(pdf).contains(verdi), "PDF for ${mal.name} skal inneholde '$verdi'")
                        }
                    }
                }
            } ?: emptyList()

    @Test
    fun `eksterne ressurser lastes ikke inn i PDF-en`() {
        val html = """
            <!DOCTYPE html>
            <html lang="no" xmlns="http://www.w3.org/1999/xhtml">
            <head><meta charset="UTF-8"/></head>
            <body>
            <p>ufarlig tekst</p>
            <img src="http://example.com/x.png" alt="x"/>
            <iframe src="file:///etc/passwd"></iframe>
            </body>
            </html>
        """.trimIndent()

        try {
            val pdf = runBlocking { client.tilPdfA(html) }
            assertTrue(!tekstFra(pdf).contains("root:"), "PDF-en skal ikke inneholde innhold fra /etc/passwd")
        } catch (_: PdfGenerationException) {
            // Forventet: failOnResourceLoadingFailed gjør at Gotenberg feiler på den eksterne ressursen.
        }
    }

    private fun erGyldigPdfA2b(pdf: ByteArray): Boolean {
        pdf.inputStream().use { input ->
            Foundries.defaultInstance().createParser(input, PDFAFlavour.PDFA_2_B).use { parser ->
                val validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFA_2_B, false)
                return validator.validate(parser).isCompliant
            }
        }
    }

    private fun tekstFra(pdf: ByteArray): String =
        Loader.loadPDF(pdf).use { PDFTextStripper().getText(it) }

    private val forventedeVerdier = mapOf(
        "soknad" to listOf("LOLPATROL"),
        "tilskuddsbrev" to listOf("ekspertbistand"),
    )
}
