package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.event.KvitteringPublisert
import no.nav.ekspertbistand.event.KvitteringPublisert.Type
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class KvitteringPublisertProjectionTest {

    private lateinit var testDb: TestDatabase
    private lateinit var projection: KvitteringPublisertProjection

    @BeforeTest
    fun setup() {
        testDb = TestDatabase.isolert()
        projection = KvitteringPublisertProjection(testDb.config.jdbcDatabase)
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `backfiller kvitteringer for alle utloesende hendelser med samme noekkel som handlerne`() = medDb {
        val soknad = nySoknad()
        val soknadId = soknad.id!!
        publishAndFinalize(EventData.SakOpprettet(sakId = UUID.randomUUID().toString(), soknad = soknad))
        publishAndFinalize(
            EventData.TiltaksgjennomforingOpprettet(soknad = soknad, saksnummer = "2026202", tiltaksgjennomfoeringId = 7)
        )
        publishAndFinalize(EventData.TilsagnsdataLagret(soknad = soknad, tilsagnData = TestEventData.sampleTilsagnData))
        publishAndFinalize(
            EventData.SoknadAvlystIArena(
                soknad = soknad,
                tiltaksgjennomforingEndret = TestEventData.sampleTiltaksgjennomforingEndret,
            )
        )
        pollAlt()

        assertEquals(
            mapOf(
                KvitteringPublisert.soknadMottattNokkel(soknadId) to Type.ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT,
                KvitteringPublisert.arenaNotatNokkel("2026202", 7) to Type.ARENA_NOTAT_JOURNALFOERT,
                KvitteringPublisert.soknadGodkjentNokkel(soknadId, TestEventData.sampleTilsagnData.tilsagnNummer)
                        to Type.ARBEIDSGIVER_VARSLET_SOKNAD_GODKJENT,
                KvitteringPublisert.soknadAvlystNokkel(soknadId) to Type.ARBEIDSGIVER_VARSLET_SOKNAD_AVLYST,
            ),
            kvitteringer(soknadId),
        )
    }

    @Test
    fun `godkjent gir en kvittering per tilsagn`() = medDb {
        val soknad = nySoknad()
        val forste = TestEventData.sampleTilsagnData
        val andre = forste.copy(tilsagnNummer = TilsagnData.TilsagnNummer(2026, 1, 2))
        publishAndFinalize(EventData.TilsagnsdataLagret(soknad = soknad, tilsagnData = forste))
        publishAndFinalize(EventData.TilsagnsdataLagret(soknad = soknad, tilsagnData = forste))
        publishAndFinalize(EventData.TilsagnsdataLagret(soknad = soknad, tilsagnData = andre))
        pollAlt()

        assertEquals(2, kvitteringer(soknad.id!!).size)
    }

    @Test
    fun `eksisterende rad fra handleren beholdes`() = medDb {
        val soknad = nySoknad()
        val nokkel = KvitteringPublisert.soknadAvlystNokkel(soknad.id!!)
        val registrertAvHandler = Instant.parse("2026-10-01T08:00:00Z")
        KvitteringPublisert.registrer(nokkel, soknad.id, Type.ARBEIDSGIVER_VARSLET_SOKNAD_AVLYST, registrertAvHandler)

        publishAndFinalize(
            EventData.SoknadAvlystIArena(
                soknad = soknad,
                tiltaksgjennomforingEndret = TestEventData.sampleTiltaksgjennomforingEndret,
            )
        )
        pollAlt()

        val registrert = KvitteringPublisert.selectAll()
            .where { KvitteringPublisert.nokkel eq nokkel }
            .single()[KvitteringPublisert.registrert]
        assertEquals(registrertAvHandler, registrert)
    }

    @Test
    fun `hendelser som ble fullfoert med feil gir ingen kvittering`() = medDb {
        val soknad = nySoknad()
        val event = publishEventQueue(
            EventData.SoknadAvlystIArena(
                soknad = soknad,
                tiltaksgjennomforingEndret = TestEventData.sampleTiltaksgjennomforingEndret,
            )
        )
        EventQueue.finalize(
            event.id,
            listOf(EventHandledResult.UnrecoverableError(source = "test", message = "varsling feilet"))
        )
        pollAlt()

        assertTrue(kvitteringer(soknad.id!!).isEmpty())
    }

    private fun medDb(block: JdbcTransaction.() -> Unit) {
        transaction(testDb.config.jdbcDatabase) { block() }
    }

    private fun pollAlt() {
        while (projection.poll()) Unit
    }

    private fun nySoknad(): DTO.Soknad = TestEventData.sampleSoknad.copy(id = UUID.randomUUID().toString())

    private fun kvitteringer(soknadId: String): Map<String, Type> =
        KvitteringPublisert.selectAll()
            .where { KvitteringPublisert.soknadId eq soknadId }
            .associate { it[KvitteringPublisert.nokkel] to it[KvitteringPublisert.type] }
}

private fun JdbcTransaction.publishAndFinalize(event: EventData) =
    publishEventQueue(event).also {
        EventQueue.finalize(it.id)
    }
