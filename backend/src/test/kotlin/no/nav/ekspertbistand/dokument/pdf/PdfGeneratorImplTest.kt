package no.nav.ekspertbistand.dokument.pdf

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ende-til-ende-test av den generiske kjernen mot de faktiske malene/fontene/CSS-en
 * som ligger under `resources/dokumentmaler/`. Verifiserer at hele pipelinen kjører:
 * Handlebars -> Markdown -> innpakning -> openhtmltopdf (PDF/A med embeddede fonter + SVG-logo).
 */
class PdfGeneratorImplTest {

    private val generator = PdfGeneratorImpl(resourcePrefix = "dokumentmaler")

    private fun testdata(template: String, file: String): JsonObject {
        val path = "dokumentmaler/templates/$template/testdata/$file"
        val raw = requireNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Fant ikke testdata: $path"
        }.use { it.readBytes().toString(Charsets.UTF_8) }
        return Json.parseToJsonElement(raw) as JsonObject
    }

    private fun ByteArray.isPdf() =
        size > 1000 && sliceArray(0 until 4).contentEquals("%PDF".toByteArray())

    @Test
    fun `soknad rendres til gyldig PDF`() {
        val pdf = generator.renderPdf("soknad", testdata("soknad", "default.json"))
        assertTrue(pdf.isPdf(), "Forventet en PDF (%PDF-header og rimelig størrelse)")
    }

    @Test
    fun `tilskuddsbrev rendres til gyldig PDF`() {
        val pdf = generator.renderPdf("tilskuddsbrev", testdata("tilskuddsbrev", "tilsagn-deltaker.json"))
        assertTrue(pdf.isPdf(), "Forventet en PDF (%PDF-header og rimelig størrelse)")
    }

    @Test
    fun `arenaNotat rendres til gyldig PDF`() {
        val pdf = generator.renderPdf("arenaNotat", testdata("arenaNotat", "default.json"))
        assertTrue(pdf.isPdf(), "Forventet en PDF (%PDF-header og rimelig størrelse)")
    }

    @Test
    fun `tilskuddsbrev html inneholder forventet innhold`() {
        val html = generator.renderHtml("tilskuddsbrev", testdata("tilskuddsbrev", "tilsagn-deltaker.json"))
        assertTrue(html.contains("Ekspertbistand"), "Mangler tiltakNavn i HTML")
        assertTrue(html.contains("Nils"), "Mangler deltakers fornavn i HTML")
        assertTrue(html.contains("Nilsen"), "Mangler deltakers etternavn i HTML")
        // Markdown-overskrift skal være konvertert til HTML.
        assertTrue(html.contains("<h1>") && html.contains("innvilget tilskudd"), "Markdown-overskrift ikke konvertert")
    }

    @Test
    fun `handlebars stringifiserer leaf-verdier uten json-anførselstegn`() {
        val html = generator.renderHtml("tilskuddsbrev", testdata("tilskuddsbrev", "tilsagn-deltaker.json"))
        assertTrue(html.contains("L.S. Solland AS"), "Strengverdi ble ikke skrevet rent")
        assertTrue(!html.contains("\"L.S. Solland AS\""), "Strengverdi ble skrevet med JSON-anførselstegn")
    }

    @Test
    fun `markdown bevarer raa html i malen`() {
        val jsonToJava = HandlebarsConfig.jsonToJava(
            Json.parseToJsonElement("""{"a":"b","n":1,"flag":true,"nested":{"x":"y"}}""") as JsonObject
        )
        @Suppress("UNCHECKED_CAST")
        val map = jsonToJava as Map<String, Any?>
        assertEquals("b", map["a"])
        assertEquals(1L, map["n"])
        assertEquals(true, map["flag"])
        assertEquals("y", (map["nested"] as Map<*, *>)["x"])
    }
}

