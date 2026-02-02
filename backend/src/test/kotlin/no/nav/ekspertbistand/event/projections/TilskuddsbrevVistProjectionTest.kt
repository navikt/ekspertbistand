package no.nav.ekspertbistand.event.projections

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVist.Companion.tilTilskuddsbrevVist
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.SkjemaStatus
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class TilskuddsbrevVistProjectionTest {

    private lateinit var testDb: TestDatabase
    private lateinit var projection: TilskuddsbrevVistProjection

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
        projection = TilskuddsbrevVistProjection(testDb.config.jdbcDatabase)
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `can query vist vs ikke vist tilsagnsbrev`() = transaction<Unit>(testDb.config.jdbcDatabase) {
        publishAndFinalize(
            EventData.TilskuddsbrevMottatt(
                skjema = sampleSkjema,
                tilsagnbrevId = 42,
                tilsagnData = sampleTilsagnData,
            )
        )
        projection.poll()

        TilskuddsbrevVistState.selectAll().map { it.tilTilskuddsbrevVist() }.also {
            assertEquals(1, it.size)
            assertEquals(sampleTilsagnData.tilsagnNummer.concat(), it.first().tilsagnNummer)
            assertNotNull(it.first().opprettetTidspunkt)
            assertNull(it.first().foerstVistTidspunkt)
        }

        publishAndFinalize(
            EventData.TilskuddsbrevVist(
                tilsagnNummer = sampleTilsagnData.tilsagnNummer.concat(),
                skjema = sampleSkjema,
            )
        )
        projection.poll()

        TilskuddsbrevVistState.selectAll().map { it.tilTilskuddsbrevVist() }.also {
            assertEquals(1, it.size)
            assertEquals(sampleTilsagnData.tilsagnNummer.concat(), it.first().tilsagnNummer)
            assertNotNull(it.first().opprettetTidspunkt)
            assertNotNull(it.first().foerstVistTidspunkt)
        }
    }

}

private fun publishAndFinalize(mottatt: EventData) =
    EventQueue.publish(mottatt).also {
        EventQueue.finalize(it.id)
    }

private val sampleSkjema = DTO.Skjema(
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
    opprettetAv = "Noen Noensen",
    status = SkjemaStatus.innsendt,
)

private val sampleTilsagnData = TilsagnData(
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
