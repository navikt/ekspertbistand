package no.nav.ekspertbistand.dokument

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.soknad.DTO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifiserer at [DokumentService] velger riktig mal og at payloaden fra domenetypene ender opp i
 * den rendrede HTML-en. Fordi HTML-en rendres in-process (ikke i [StubPdfKonverterer]) asserter vi
 * på den faktiske malutdata i stedet for et stubbet svar.
 */
class DokumentServiceTest {

    @Test
    fun `lager soknad-pdf med korrekt mal og payload`() = runBlocking {
        var html: String? = null
        val service = DokumentService(StubPdfKonverterer(onConvert = { h -> html = h }))

        val pdf = service.genererSoknadPdf(sampleSoknad())

        assertContentEquals("%PDF-mock".toByteArray(), pdf)
        val rendered = requireNotNull(html)
        assertTrue(rendered.contains("987654321"), "virksomhetsnummer skal rendres")
        assertTrue(rendered.contains("Psykolog"), "godkjentUtdanningEllerAutorisasjon skal rendres")
        assertTrue(rendered.contains("Tilrettelegging på arbeidsplassen"), "relevantKompetanse skal rendres")
        assertTrue(rendered.contains("9000"), "estimertKostnad skal rendres")
        assertTrue(rendered.contains("DejaVu Sans"), "PDF-format skal sette DejaVu Sans som font")
    }

    @Test
    fun `soknad-pdf faller tilbake til kompetanse naar godkjentUtdanning er tom`() = runBlocking {
        var html: String? = null
        val service = DokumentService(StubPdfKonverterer(onConvert = { h -> html = h }))

        service.genererSoknadPdf(
            sampleSoknad().copy(
                ekspert = DTO.Ekspert(
                    navn = "Ekspert Navn",
                    virksomhet = "Ekspertselskap",
                    kompetanse = "Fysioterapeut",
                    godkjentUtdanningEllerAutorisasjon = emptyList(),
                    relevantKompetanse = emptyList(),
                )
            )
        )

        assertTrue(requireNotNull(html).contains("Fysioterapeut"))
    }

    @Test
    fun `lager tilskuddsbrev-pdf med korrekt mal og payload`() = runBlocking {
        var html: String? = null
        val service = DokumentService(StubPdfKonverterer(onConvert = { h -> html = h }))

        val pdf = service.genererTilskuddsbrevPdf(sampleTilskuddsbrev())

        assertContentEquals("%PDF-mock".toByteArray(), pdf)
        val rendered = requireNotNull(html)
        assertTrue(rendered.contains("Dere har fått innvilget tilskudd til ekspertbistand"))
    }

    @Test
    fun `lager arenaNotat-pdf med korrekt mal og payload`() = runBlocking {
        var html: String? = null
        val service = DokumentService(StubPdfKonverterer(onConvert = { h -> html = h }))

        service.genererArenaNotatPdf(saksnummer = "SAK-42", tiltaksgjennomfoeringId = "GJENNOM-314")

        val rendered = requireNotNull(html)
        assertTrue(rendered.contains("SAK-42"))
        assertTrue(rendered.contains("GJENNOM-314"))
    }

    @Test
    fun `tilskuddsbrev-html bruker html-rendring uten pdf-konvertering`() = runBlocking {
        var konverterKalt = false
        val service = DokumentService(StubPdfKonverterer(onConvert = { _ -> konverterKalt = true }))

        val html = service.genererTilskuddsbrevHtml(sampleTilskuddsbrev())

        assertTrue(html.contains("Dere har fått innvilget tilskudd til ekspertbistand"))
        assertFalse(html.contains("id=\"header\""), "HTML-format skal ikke ha PDF-header")
        assertFalse(konverterKalt, "HTML-rendring skal ikke gå via PDF-konvertereren")
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
    )
}
