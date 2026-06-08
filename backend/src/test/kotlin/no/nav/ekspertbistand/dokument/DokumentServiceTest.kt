package no.nav.ekspertbistand.dokument

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.mocks.StubPdfGenerator
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DokumentServiceTest {

    @Test
    fun `lager soknad-pdf med korrekt mal og payload`() = runBlocking {
        val pdf = "%PDF-mock".toByteArray()
        var template: String? = null
        var data: JsonObject? = null

        val client = DokumentService(StubPdfGenerator(pdf = pdf, onRenderPdf = { t, d -> template = t; data = d }))

        val response = client.genererSoknadPdf(sampleSoknad())

        assertContentEquals(pdf, response)
        assertEquals("soknad", template)

        val body = requireNotNull(data)
        assertEquals("987654321", body["virksomhet"]!!.jsonObject["virksomhetsnummer"]!!.jsonPrimitive.content)
        val behov = body["behovForBistand"]!!.jsonObject
        assertTrue(behov["timer"]!!.jsonPrimitive.isString)
        assertEquals("12", behov["timer"]!!.jsonPrimitive.content)
        assertEquals("9000", behov["estimertKostnad"]!!.jsonPrimitive.content)
        val ekspert = body["ekspert"]!!.jsonObject
        assertEquals("Psykolog", ekspert["godkjentUtdanningEllerAutorisasjon"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("Tilrettelegging på arbeidsplassen", ekspert["relevantKompetanse"]!!.jsonArray[0].jsonPrimitive.content)
    }

    @Test
    fun `lager tilskuddbrev-pdf med korrekt mal og payload`() = runBlocking {
        val pdf = "%PDF-mock".toByteArray()
        var template: String? = null
        var data: JsonObject? = null

        val client = DokumentService(StubPdfGenerator(pdf = pdf, onRenderPdf = { t, d -> template = t; data = d }))

        val response = client.genererTilskuddsbrevPdf(sampleTilskuddsbrev())

        assertContentEquals(pdf, response)
        assertEquals("tilskuddsbrev", template)

        val body = requireNotNull(data)
        assertEquals("1337", body["tilsagnNummer"]!!.jsonObject["aar"]!!.jsonPrimitive.content)
        assertEquals("Ekspertbistand", body["tiltakNavn"]!!.jsonPrimitive.content)
    }

    @Test
    fun `lager arenaNotat-pdf med korrekt mal og payload`() = runBlocking {
        var template: String? = null
        var data: JsonObject? = null

        val client = DokumentService(StubPdfGenerator(onRenderPdf = { t, d -> template = t; data = d }))

        client.genererArenaNotatPdf(saksnummer = "42", tiltaksgjennomfoeringId = "314")

        assertEquals("arenaNotat", template)
        val body = requireNotNull(data)
        assertEquals("42", body["saksnummer"]!!.jsonPrimitive.content)
        assertEquals("314", body["tiltaksgjennomfoeringId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tilskuddsbrev-html bruker html-rendring`() = runBlocking {
        val client = DokumentService(StubPdfGenerator(html = "<html>brev</html>"))
        assertEquals("<html>brev</html>", client.genererTilskuddsbrevHtml(sampleTilskuddsbrev()))
    }

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
            TilsagnData.Tilskudd(
                tilskuddType = "ekspertbistand",
                tilskuddBelop = 24000,
                visTilskuddProsent = false,
                tilskuddProsent = null
            )
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

    private fun sampleSoknad() = DTO.Soknad(
        id = "42",
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Kontakt Person",
                epost = "kontakt@testbedrift.no",
                telefonnummer = "12345678",
            )
        ),
        ansatt = DTO.Ansatt(
            fnr = "01010112345",
            navn = "Ansatt Navn",
        ),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            godkjentUtdanningEllerAutorisasjon = listOf("Psykolog", "Ergoterapeut"),
            relevantKompetanse = listOf("Tilrettelegging på arbeidsplassen"),
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2024, 12, 1),
        ),
        nav = DTO.Nav(
            kontaktperson = "Veileder Navn"
        ),
        status = SoknadStatus.innsendt,
    )
}

