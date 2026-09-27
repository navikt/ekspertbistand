package no.nav.ekspertbistand.dokument.pdf

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sikkerhetstester for [DokumentRenderer]. Alle fritekstfelt skal HTML-escapes og aldri tolkes som
 * Markdown eller rå HTML. Se §7.2 i `specifications/pdfgenerator_gotenberg.md`.
 */
class DokumentRendererSikkerhetTest {

    private val renderer = DokumentRenderer()

    /** Rendrer arenaNotat med [saksnummer] som fritekst i `saksnummer`-feltet. */
    private fun renderMedSaksnummer(saksnummer: String): String =
        renderer.renderHtml(
            "arenaNotat",
            buildJsonObject {
                put("saksnummer", saksnummer)
                put("tiltaksgjennomfoeringId", "123")
            },
            Format.HTML,
        )

    @Test
    fun `markdown-bilde blir bokstavelig tekst uten img`() {
        val html = renderMedSaksnummer("![x](http://127.0.0.1/x.png)")
        assertFalse(html.contains("<img"), "skal ikke lage <img>")
        assertTrue(html.contains("![x]("), "skal vise markdown-teksten bokstavelig")
    }

    @Test
    fun `markdown-lenke blir bokstavelig tekst uten a-tag`() {
        val html = renderMedSaksnummer("[klikk](javascript:alert(1))")
        assertFalse(html.contains("<a "), "skal ikke lage <a>")
        assertTrue(html.contains("[klikk]("))
    }

    @Test
    fun `markdown-overskrift og punktliste blir bokstavelig tekst`() {
        val html = renderMedSaksnummer("# Overskrift * punkt")
        assertTrue(html.contains("# Overskrift * punkt"))
    }

    @Test
    fun `script-tag escapes og vises som tekst`() {
        val html = renderMedSaksnummer("<script>alert(1)</script>")
        assertFalse(html.contains("<script>alert(1)</script>"), "skal ikke inneholde rå script-tag")
        assertTrue(html.contains("&lt;script&gt;"), "skal være escapet")
    }

    @Test
    fun `iframe escapes og vises som tekst`() {
        val html = renderMedSaksnummer("<iframe src=\"file:///etc/passwd\">")
        assertFalse(html.contains("<iframe"), "skal ikke inneholde rå iframe-tag")
        assertTrue(html.contains("&lt;iframe"), "skal være escapet")
    }

    @Test
    fun `ampersand og spesialtegn escapes uten dobbel-escaping`() {
        val html = renderMedSaksnummer("Navn & Sønn AS a_b*c «sitat»")
        assertTrue(html.contains("Navn &amp; Sønn AS"), "& skal escapes til &amp;")
        assertFalse(html.contains("&amp;amp;"), "skal ikke dobbel-escapes")
        assertTrue(html.contains("a_b*c"), "understrek og stjerne skal ikke tolkes som markdown")
        assertTrue(html.contains("«sitat»"))
    }

    @Test
    fun `linjeskift i soknad-tabellcelle blir br og tabellen er intakt`() {
        val html = renderer.renderHtml(
            "soknad",
            buildJsonObject {
                putJsonObject("behovForBistand") {
                    put("begrunnelse", "linje1\n\nlinje2")
                }
            },
            Format.HTML,
        )
        assertTrue(html.contains("linje1<br/>"), "linjeskift skal bli <br/>")
        assertTrue(html.contains("linje2"))
        assertTrue(html.contains("<table"), "tabellen skal være intakt")
    }
}
