package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.sak.KildeTilBehandling
import no.nav.ekspertbistand.sak.SakTable
import no.nav.ekspertbistand.sak.Saksstatus
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SakProjectionTest {

    private lateinit var testDb: TestDatabase
    private lateinit var projection: SakProjection

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
        projection = SakProjection(testDb.config.jdbcDatabase)
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `SoknadInnsendt oppretter sak med status OPPRETTET og kilde ARENA`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        pollAlt()

        val sak = hentSak(soknad)
        assertEquals(Saksstatus.OPPRETTET.name, sak[SakTable.status])
        assertEquals(KildeTilBehandling.ARENA.name, sak[SakTable.kildeTilBehandling])
        assertNull(sak[SakTable.behandlendeEnhet])
        assertNull(sak[SakTable.arenaSakId])
    }

    @Test
    fun `journalfoering setter behandlende enhet og tiltaksgjennomforing setter arena_sak_id`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(journalfoert(soknad, enhet = "0315"))
        publishAndFinalize(tiltaksgjennomforingOpprettet(soknad, saksnummer = "2026202"))
        pollAlt()

        val sak = hentSak(soknad)
        assertEquals("0315", sak[SakTable.behandlendeEnhet])
        assertEquals("2026202", sak[SakTable.arenaSakId])
        assertEquals(Saksstatus.OPPRETTET.name, sak[SakTable.status])
    }

    @Test
    fun `godkjent lop gir UNDER_BEHANDLING og deretter INNVILGET`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(saksbehandlingStartet(soknad))
        pollAlt()
        assertEquals(Saksstatus.UNDER_BEHANDLING.name, hentSak(soknad)[SakTable.status])

        publishAndFinalize(tilskuddsbrevMottatt(soknad))
        pollAlt()
        assertEquals(Saksstatus.INNVILGET.name, hentSak(soknad)[SakTable.status])
    }

    @Test
    fun `SoknadAvlystIArena gir AVSLATT`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(avlyst(soknad))
        pollAlt()

        assertEquals(Saksstatus.AVSLATT.name, hentSak(soknad)[SakTable.status])
    }

    @Test
    fun `terminalstatus overskrives ikke av senere events`() = medDb {
        val innvilget = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(innvilget))
        publishAndFinalize(tilskuddsbrevMottatt(innvilget))
        publishAndFinalize(saksbehandlingStartet(innvilget))
        publishAndFinalize(avlyst(innvilget))

        val avslatt = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(avslatt))
        publishAndFinalize(avlyst(avslatt))
        publishAndFinalize(tilskuddsbrevMottatt(avslatt))
        pollAlt()

        assertEquals(Saksstatus.INNVILGET.name, hentSak(innvilget)[SakTable.status])
        assertEquals(Saksstatus.AVSLATT.name, hentSak(avslatt)[SakTable.status])
    }

    @Test
    fun `sak opprettes ikke naar soknaden ikke finnes, og projeksjonen gaar videre`() = medDb {
        val slettet = TestEventData.sampleSoknad.copy(id = UUID.randomUUID().toString())
        publishAndFinalize(EventData.SoknadInnsendt(slettet))
        publishAndFinalize(journalfoert(slettet, enhet = "0315"))
        publishAndFinalize(tilskuddsbrevMottatt(slettet))

        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        pollAlt()

        assertTrue(SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(slettet.id) }.empty())
        assertEquals(Saksstatus.OPPRETTET.name, hentSak(soknad)[SakTable.status])
    }

    @Test
    fun `samme SoknadInnsendt to ganger gir en sak`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        pollAlt()

        assertEquals(1, SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(soknad.id) }.count())
    }

    @Test
    fun `eksisterende sak opprettet utenfor projeksjonen beholdes`() = medDb {
        val soknad = lagreSoknad()
        SakTable.insert {
            it[soknadId] = UUID.fromString(soknad.id)
            it[status] = Saksstatus.UNDER_BEHANDLING.name
            it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
        }

        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        pollAlt()

        val sak = hentSak(soknad)
        assertEquals(Saksstatus.UNDER_BEHANDLING.name, sak[SakTable.status])
        assertEquals(KildeTilBehandling.EKSPERTBISTAND.name, sak[SakTable.kildeTilBehandling])
    }

    @Test
    fun `sletting av soknad sletter saken`() = medDb {
        val soknad = lagreSoknad()
        publishAndFinalize(EventData.SoknadInnsendt(soknad))
        pollAlt()
        hentSak(soknad)

        SoknadTable.deleteWhere { SoknadTable.id eq UUID.fromString(soknad.id) }

        assertTrue(SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(soknad.id) }.empty())
    }

    private fun medDb(block: JdbcTransaction.() -> Unit) {
        transaction(testDb.config.jdbcDatabase) { block() }
    }

    private fun pollAlt() {
        while (projection.poll()) Unit
    }

    private fun hentSak(soknad: DTO.Soknad): ResultRow =
        SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(soknad.id) }.single()

    private fun lagreSoknad(): DTO.Soknad {
        val soknad = TestEventData.sampleSoknad.copy(id = UUID.randomUUID().toString())
        SoknadTable.insert {
            it[id] = UUID.fromString(soknad.id)
            it[virksomhetsnummer] = soknad.virksomhet.virksomhetsnummer
            it[virksomhetsnavn] = soknad.virksomhet.virksomhetsnavn
            it[opprettetAv] = "42"
            it[behovForBistand] = ""
            it[behovForBistandTilrettelegging] = ""
            it[behovForBistandBegrunnelse] = ""
            it[behovForBistandEstimertKostnad] = "42"
            it[behovForBistandTimer] = "9"
            it[behovForBistandStartdato] = CurrentDate
            it[kontaktpersonNavn] = ""
            it[kontaktpersonEpost] = ""
            it[kontaktpersonTelefon] = ""
            it[ansattFnr] = ""
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[navKontaktPerson] = ""
            it[status] = "innsendt"
        }
        return soknad
    }

    private fun journalfoert(soknad: DTO.Soknad, enhet: String) = EventData.InnsendtSoknadJournalfoert(
        soknad = soknad,
        dokumentId = 1,
        journaldpostId = 2,
        behandlendeEnhetId = enhet,
    )

    private fun tiltaksgjennomforingOpprettet(soknad: DTO.Soknad, saksnummer: String) =
        EventData.TiltaksgjennomforingOpprettet(
            soknad = soknad,
            saksnummer = saksnummer,
            tiltaksgjennomfoeringId = 1,
        )

    private fun saksbehandlingStartet(soknad: DTO.Soknad) = EventData.SaksbehandlingStartetIArena(
        soknad = soknad,
        tiltakssakEndret = TestEventData.sampleTiltakssakEndret,
    )

    private fun tilskuddsbrevMottatt(soknad: DTO.Soknad) = EventData.TilskuddsbrevMottatt(
        soknad = soknad,
        tilsagnbrevId = 1,
        tilsagnData = TestEventData.sampleTilsagnData,
    )

    private fun avlyst(soknad: DTO.Soknad) = EventData.SoknadAvlystIArena(
        soknad = soknad,
        tiltaksgjennomforingEndret = TestEventData.sampleTiltaksgjennomforingEndret,
    )
}

private fun JdbcTransaction.publishAndFinalize(event: EventData) =
    publishEventQueue(event).also {
        EventQueue.finalize(it.id)
    }
