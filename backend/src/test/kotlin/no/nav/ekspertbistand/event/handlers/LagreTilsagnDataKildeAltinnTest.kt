package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.tilsagndata.TilsagndataTable
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LagreTilsagnDataKildeAltinnTest {

    @Test
    fun `Lagrer tilsagndata og returnerer Success`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = LagreTilsagnsDataKildeAltinn(database = database)

        val event = Event(
            id = 1,
            data = EventData.TilskuddsbrevJournalfoertKildeAltinn(
                journaldpostId = 1,
                dokumentId = 1,
                tilsagnData = sampleTilskuddsbrev()
            )
        )
        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            assertEquals(1, TilsagndataTable.selectAll().count())
        }
    }

    private fun sampleTilskuddsbrev() = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(
            1337,
            42,
            43,
        ),
        tilsagnDato = "01.01.2021",
        periode = TilsagnData.Periode(
            fraDato = "01.01.2021",
            tilDato = "01.02.2021"
        ),
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
        beslutter = TilsagnData.Person(
            fornavn = "Ole",
            etternavn = "Brum",
        ),
        saksbehandler = TilsagnData.Person(
            fornavn = "Nasse",
            etternavn = "Nøff",
        ),
        kommentar = "Dette var unødvendig mye testdata å skrive"
    )
}