package no.nav.ekspertbistand.event.projections

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.EKSPERTBISTAND_TILTAKSKODE
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelse.Companion.tilSoknadBehandletForsinkelse
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
class SoknadBehandletForsinkelseProjectionTest {

    private lateinit var testDb: TestDatabase
    private lateinit var projection: SoknadBehandletForsinkelseProjection

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
        projection = SoknadBehandletForsinkelseProjection(testDb.config.jdbcDatabase)
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `can query forsinkelse og status`() = transaction<Unit>(testDb.config.jdbcDatabase) {
        publishAndFinalize(
            EventData.SkjemaInnsendt(
                skjema = sampleSkjema,
            )
        )
        projection.poll()
        SoknadBehandletForsinkelseState.selectAll().map { it.tilSoknadBehandletForsinkelse() }.also {
            assertEquals(1, it.size)
            assertEquals(sampleSkjema.id, it.first().skjemaId)
            assertNotNull(it.first().innsendtTidspunkt)
            assertNull(it.first().godkjentTidspunkt)
            assertNull(it.first().avlystTidspunkt)
        }

        // simuler flere tilskuddsbrev mottatt for samme skjema
        for (i in 1..5) {
            publishAndFinalize(
                EventData.TilskuddsbrevMottatt(
                    skjema = sampleSkjema,
                    // brevid og tilsagnnummer ikke relevant for testen, men justert her for ordens skyld
                    tilsagnbrevId = 42 + i,
                    tilsagnData = sampleTilsagnData.copy(
                        tilsagnNummer = TilsagnData.TilsagnNummer(
                            aar = sampleTilsagnData.tilsagnNummer.aar,
                            loepenrSak = sampleTilsagnData.tilsagnNummer.loepenrSak,
                            loepenrTilsagn = sampleTilsagnData.tilsagnNummer.loepenrSak + i,
                    ))
                )
            )
            projection.poll()
        }

        SoknadBehandletForsinkelseState.selectAll().map { it.tilSoknadBehandletForsinkelse() }.also {
            assertEquals(1, it.size)
            assertEquals(sampleSkjema.id, it.first().skjemaId)
            assertNotNull(it.first().innsendtTidspunkt)
            assertNotNull(it.first().godkjentTidspunkt)
            assertNull(it.first().avlystTidspunkt)
        }

        // simuler at søknaden blir avlyst i Arena
        val avlystSkjema = sampleSkjema.copy(id = UUID.randomUUID().toString())
        publishAndFinalize(
            EventData.SkjemaInnsendt(
                skjema = avlystSkjema,
            )
        )
        projection.poll()
        SoknadBehandletForsinkelseState.selectAll().map { it.tilSoknadBehandletForsinkelse() }.first {
            it.skjemaId == avlystSkjema.id
        }.also {
            assertNotNull(it.innsendtTidspunkt)
            assertNull(it.godkjentTidspunkt)
            assertNull(it.avlystTidspunkt)
        }

        publishAndFinalize(
            EventData.SoknadAvlystIArena(
                skjema = avlystSkjema,
                tiltaksgjennomforingEndret = TiltaksgjennomforingEndret(
                    tiltaksgjennomfoeringId = 1,
                    tiltakKode = EKSPERTBISTAND_TILTAKSKODE,
                    tiltakStatusKode = TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST,
                )
            )
        )
        projection.poll()
        SoknadBehandletForsinkelseState.selectAll().map { it.tilSoknadBehandletForsinkelse() }.first {
            it.skjemaId == avlystSkjema.id
        }.also {
            assertNotNull(it.innsendtTidspunkt)
            assertNull(it.godkjentTidspunkt)
            assertNotNull(it.avlystTidspunkt)
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
