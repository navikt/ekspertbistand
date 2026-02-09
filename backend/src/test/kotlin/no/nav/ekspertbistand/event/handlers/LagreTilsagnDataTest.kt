package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.tilsagndata.TilsagndataTable
import no.nav.ekspertbistand.tilsagndata.findTilsagnDataBySoknadId
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LagreTilsagnDataTest {

    @Test
    fun `Lagrer tilsagndata og returnerer Success`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = LagreTilsagnsData(database = database)

        val event = Event(
            id = 1,
            data = EventData.TilskuddsbrevJournalfoert(
                journaldpostId = 1,
                dokumentId = 1,
                soknad = sampleSoknad(),
                tilsagnData = sampleTilskuddsbrev()
            )
        )
        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            assertEquals(1, TilsagndataTable.selectAll().count())
            val queuedEvents = QueuedEvents.selectAll()
            assertEquals(1, queuedEvents.count())
            assertIs<EventData.TilsagnsdataLagret>(queuedEvents.first()[QueuedEvents.eventData])
        }
    }

    @Test
    fun `Lagrer ny tilsagndata og returnerer Success`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = LagreTilsagnsData(database = database)

        val event = Event(
            id = 1,
            data = EventData.TilskuddsbrevJournalfoert(
                journaldpostId = 1,
                dokumentId = 1,
                soknad = sampleSoknad(),
                tilsagnData = sampleTilskuddsbrev()
            )
        )

        transaction(database) {
            insertTilsagndata(UUID.fromString(event.data.soknad.id), sampleTilskuddsbrev())
        }

        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            assertEquals(2, findTilsagnDataBySoknadId(UUID.fromString(event.data.soknad.id)).count())
            val queuedEvents = QueuedEvents.selectAll()
            assertEquals(1, queuedEvents.count())
            assertIs<EventData.TilsagnsdataLagret>(queuedEvents.first()[QueuedEvents.eventData])
        }
    }

    @Test
    fun `soknad id er null returnerer unrecoverable error`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = LagreTilsagnsData(database = database)

        val event = Event(
            id = 1,
            data = EventData.TilskuddsbrevJournalfoert(
                journaldpostId = 1,
                dokumentId = 1,
                soknad = sampleSoknad().copy(id = null),
                tilsagnData = sampleTilskuddsbrev()
            )
        )
        assertIs<EventHandledResult.UnrecoverableError>(handler.handle(event))

        transaction(database) {
            assertEquals(0, TilsagndataTable.selectAll().count())
        }
    }

    private fun sampleSoknad() = DTO.Soknad(
        id = UUID.randomUUID().toString(),
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "1337",
            virksomhetsnavn = "foo bar AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Donald Duck",
                epost = "Donald@duck.co",
                telefonnummer = "12345678"
            )
        ),
        ansatt = DTO.Ansatt(
            fnr = "12345678910",
            navn = "Ole Olsen"
        ),
        ekspert = DTO.Ekspert(
            navn = "Egon Olsen",
            virksomhet = "Olsenbanden AS",
            kompetanse = "Bankran",
        ),
        behovForBistand = DTO.BehovForBistand(
            behov = "Tilrettelegging",
            begrunnelse = "Tilrettelegging på arbeidsplassen",
            estimertKostnad = "4200",
            timer = "16",
            tilrettelegging = "Spesialtilpasset kontor",
            startdato = LocalDate.parse("2024-11-15")
        ),
        nav = DTO.Nav(
            kontaktperson = "Navn Navnesen"
        ),
        status = SoknadStatus.innsendt,
    )

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