package no.nav.ekspertbistand.dokument

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.dokument.pdf.DokumentRenderer
import no.nav.ekspertbistand.dokument.pdf.Format
import no.nav.ekspertbistand.dokument.pdf.HandlebarsConfig
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.soknad.DTO
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.TextNode
import org.xml.sax.SAXParseException
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.fail

/**
 * Enhetstest av alle maler (§7.1). Kaller ikke Gotenberg; validerer HTML-en fra
 * [DokumentRenderer.renderHtml] i strict mode: velformet XHTML, ingen uløste referanser,
 * kun tillatte ressurser og ingen rester av Handlebars-syntaks.
 */
class DokumentmalTest {

    private val templatesDir = File("src/main/resources/dokumentmaler/templates")
    private val strictRenderer = DokumentRenderer(handlebars = HandlebarsConfig("dokumentmaler", strict = true))
    private val fontFilnavn = strictRenderer.assetsForPdf().map { it.fileName }.toSet()

    private fun maler(): List<File> =
        templatesDir.listFiles { f -> f.isDirectory && File(f, "template.hbs").exists() }
            ?.sortedBy { it.name }
            ?: emptyList()

    @Test
    fun `finner maler under templates`() {
        val navn = maler().map { it.name }
        assertTrue(navn.isNotEmpty(), "skal finne minst én mal")
        assertTrue("README" !in navn, "README skal ikke tolkes som en mal")
    }

    @Test
    fun `hver mal har minst en testdata-fil`() {
        maler().forEach { mal ->
            val testdata = File(mal, "testdata").listFiles { f -> f.extension == "json" } ?: emptyArray()
            assertTrue(testdata.isNotEmpty(), "malen ${mal.name} mangler testdata/*.json")
        }
    }

    @TestFactory
    fun `alle maler rendrer gyldig HTML fra testdata`(): List<DynamicTest> =
        maler().flatMap { mal ->
            val testdata = File(mal, "testdata").listFiles { f -> f.extension == "json" }?.sortedBy { it.name }
                ?: emptyList()
            testdata.map { fil ->
                DynamicTest.dynamicTest("${mal.name} / ${fil.name}") {
                    val data = Json.parseToJsonElement(fil.readText()) as JsonObject
                    val html = strictRenderer.renderHtml(mal.name, data, Format.PDF)
                    validerHtml(html, medHeaderFooter = true)
                    if (mal.name == "tilskuddsbrev") {
                        validerHtml(strictRenderer.renderHtml(mal.name, data, Format.HTML), medHeaderFooter = false)
                    }
                }
            }
        }

    @Test
    fun `rendrer gyldig HTML fra ekte payload via DokumentService`() {
        var soknadHtml: String? = null
        var tilskuddPdfHtml: String? = null
        var arenaHtml: String? = null

        val soknadService = DokumentService(StubPdfKonverterer(onConvert = { h, _ -> soknadHtml = h }), strictRenderer)
        val tilskuddService = DokumentService(StubPdfKonverterer(onConvert = { h, _ -> tilskuddPdfHtml = h }), strictRenderer)
        val arenaService = DokumentService(StubPdfKonverterer(onConvert = { h, _ -> arenaHtml = h }), strictRenderer)

        kotlinx.coroutines.runBlocking {
            soknadService.genererSoknadPdf(sampleSoknad())
            tilskuddService.genererTilskuddsbrevPdf(sampleTilskuddsbrev())
            arenaService.genererArenaNotatPdf("SAK-1", "GJENNOM-2")
            val tilskuddHtml = tilskuddService.genererTilskuddsbrevHtml(sampleTilskuddsbrev())
            validerHtml(tilskuddHtml, medHeaderFooter = false)
        }

        validerHtml(requireNotNull(soknadHtml), medHeaderFooter = true)
        validerHtml(requireNotNull(tilskuddPdfHtml), medHeaderFooter = true)
        validerHtml(requireNotNull(arenaHtml), medHeaderFooter = true)
    }

    @Test
    fun `ingen hbs bruker markdown eller triple-stash`() {
        val hbsFiler = templatesDir.walkTopDown().filter { it.isFile && it.extension == "hbs" }.toList()
        assertTrue(hbsFiler.isNotEmpty())
        val markdownOverskrift = Regex("(?m)^\\s*#{1,6}\\s")
        val markdownLenke = Regex("]\\(")
        hbsFiler.forEach { fil ->
            val innhold = fil.readText()
            assertTrue(!innhold.contains("{{{"), "${fil.path} bruker triple-stash {{{")
            assertTrue(!markdownOverskrift.containsMatchIn(innhold), "${fil.path} bruker markdown-overskrift")
            assertTrue(!markdownLenke.containsMatchIn(innhold), "${fil.path} bruker markdown-lenke")
        }
    }

    private fun validerHtml(html: String, medHeaderFooter: Boolean) {
        assertTrue(!html.contains("{{") && !html.contains("}}"), "HTML har rester av Handlebars-syntaks")

        parseXhtml(html)

        val doc = Jsoup.parse(html)
        forbudteTagsFinnesIkke(doc)
        lenkerErAbsolutteHttps(doc)
        ressurserErTillatt(doc)
        cssRefererKunTillatteRessurser(doc)
        ingenTekstErNull(doc)

        if (medHeaderFooter) {
            assertTrue(doc.selectFirst("svg#nav_logo") != null, "PDF-varianten skal ha NAV-logoen")
            fontFaceRefererAssets(doc)
        }
    }

    private fun parseXhtml(html: String) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        try {
            factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
            }.parse(html.byteInputStream())
        } catch (e: SAXParseException) {
            fail("HTML er ikke velformet XHTML (linje ${e.lineNumber}, kolonne ${e.columnNumber}): ${e.message}")
        }
    }

    private fun forbudteTagsFinnesIkke(doc: Document) {
        listOf("script", "iframe", "object", "embed", "frame").forEach { tag ->
            assertTrue(doc.select(tag).isEmpty(), "<$tag> skal ikke finnes")
        }
        assertTrue(doc.select("link[rel=stylesheet]").isEmpty(), "<link rel=stylesheet> skal ikke finnes")
    }

    private fun lenkerErAbsolutteHttps(doc: Document) {
        doc.select("a[href]").forEach { a ->
            val href = a.attr("href")
            assertTrue(href.startsWith("https://"), "lenke '$href' må være absolutt https")
        }
    }

    private fun ressurserErTillatt(doc: Document) {
        val tags = listOf("img", "source", "video", "audio", "input", "use", "embed", "object", "frame")
        tags.forEach { tag ->
            doc.select(tag).forEach { el ->
                listOf("src", "href", "xlink:href").forEach { attr ->
                    val v = el.attr(attr)
                    if (v.isNotBlank() && !v.startsWith("#")) {
                        assertTrue(v.startsWith("data:"), "<$tag $attr='$v'> er ikke tillatt")
                    }
                }
            }
        }
    }

    private fun cssRefererKunTillatteRessurser(doc: Document) {
        val css = buildString {
            doc.select("style").forEach { append(it.data()).append('\n') }
            doc.select("[style]").forEach { append(it.attr("style")).append('\n') }
        }
        assertTrue(!css.contains("@import"), "CSS skal ikke bruke @import")
        Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)").findAll(css).forEach { m ->
            val ref = m.groupValues[1].trim()
            val ok = ref.startsWith("data:") || ref in fontFilnavn
            assertTrue(ok, "CSS url('$ref') må være data: eller et font-filnavn")
        }
    }

    private fun fontFaceRefererAssets(doc: Document) {
        val css = doc.select("style").joinToString("\n") { it.data() }
        val refererte = Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)").findAll(css)
            .map { it.groupValues[1].trim() }
            .filter { it in fontFilnavn }
            .toSet()
        assertTrue(refererte.containsAll(fontFilnavn), "alle fonter i assetsForPdf skal være referert i @font-face")
    }

    private fun ingenTekstErNull(doc: Document) {
        val harNull = doc.body().select("*").any { el ->
            el.textNodes().any { (it as TextNode).text().trim() == "null" }
        }
        assertTrue(!harNull, "ingen tekstnode skal være bokstavelig 'null'")
    }

    private fun sampleSoknad() = DTO.Soknad(
        id = "42",
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson("Kontakt Person", "kontakt@testbedrift.no", "12345678"),
        ),
        ansatt = DTO.Ansatt(fnr = "01010112345", navn = "Ansatt Navn"),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            godkjentUtdanningEllerAutorisasjon = listOf("Psykolog"),
            relevantKompetanse = listOf("Tilrettelegging"),
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2024, 12, 1),
        ),
        nav = DTO.Nav(kontaktperson = "Veileder Navn"),
        opprettetTidspunkt = "2025-11-01",
    )

    private fun sampleTilskuddsbrev() = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(1337, 42, 43),
        tilsagnDato = "01.01.2021",
        periode = TilsagnData.Periode(fraDato = "01.01.2021", tilDato = "01.02.2021"),
        tiltakKode = "42",
        tiltakNavn = "Ekspertbistand",
        administrasjonKode = "etellerannet",
        refusjonfristDato = "10.01.2021",
        tiltakArrangor = TilsagnData.TiltakArrangor(
            arbgiverNavn = "Arrangøren",
            landKode = "1337",
            postAdresse = "et sted",
            postNummer = "1337",
            postSted = "hos naboen",
            orgNummerMorselskap = 43,
            orgNummer = 42,
            kontoNummer = "1234.12.12345",
            maalform = "norsk"
        ),
        totaltTilskuddbelop = 24000,
        valutaKode = "NOK",
        tilskuddListe = listOf(
            TilsagnData.Tilskudd("ekspertbistand", 24000, false, null)
        ),
        deltaker = TilsagnData.Deltaker(
            fodselsnr = "42",
            fornavn = "navn",
            etternavn = "navnesen",
            landKode = "NO",
            postAdresse = "et sted",
            postNummer = "1234",
            postSted = "hos den andre naboen",
        ),
        antallDeltakere = 1,
        antallTimeverk = 100,
        navEnhet = TilsagnData.NavEnhet(
            navKontorNavn = "kontor1",
            navKontor = "Kontor1",
            postAdresse = "hos den tredje",
            postNummer = "1234",
            postSted = "hos den tredje",
            telefon = "12341234",
            faks = null
        ),
        beslutter = TilsagnData.Person(fornavn = "Ole", etternavn = "Brum"),
        saksbehandler = TilsagnData.Person(fornavn = "Nasse", etternavn = "Nøff"),
        kommentar = "Dette var unødvendig mye testdata å skrive"
    )
}
