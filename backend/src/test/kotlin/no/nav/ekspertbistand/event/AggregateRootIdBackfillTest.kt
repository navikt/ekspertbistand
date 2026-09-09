package no.nav.ekspertbistand.event

import kotlinx.coroutines.runBlocking
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

/**
 * P4-integrasjonstestene for [AggregateRootIdBackfill]: at jobben fyller `aggregate_root_id` for
 * legacy-rader (NULL) i begge tabellene med verdier som speiler [EventData.aggregateRootId], at den
 * er selv-avsluttende og idempotent, at den hopper over rader som allerede har verdi, og at
 * `enabled = false` gjør den til et no-op.
 */
@OptIn(ExperimentalTime::class)
class AggregateRootIdBackfillTest {
    private lateinit var testDb: TestDatabase
    private val db get() = testDb.config.jdbcDatabase

    // Rask config for test: liten batch (tvinger flere batcher over 12 rader) og ingen pause.
    private val config = AggregateRootIdBackfillConfig(batchSize = 3, pause = Duration.ZERO)

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `fyller alle event_queue-rader med aggregate_root_id som speiler modellen`() {
        // Legg inn én legacy-rad (NULL) per EventData-subklasse. event_queue har ingen NOT NULL-check
        // ennå (den kommer i P6), så NULL-innsetting er lov.
        val idTilRot: Map<Long, String> = transaction(db) {
            TestEventData.allEventSamples.associate { sample ->
                val id = QueuedEvents.insertReturning {
                    it[eventData] = sample
                }.first()[QueuedEvents.id]
                id to sample.aggregateRootId
            }
        }

        runBlocking { AggregateRootIdBackfill(db, config).run() }

        transaction(db) {
            idTilRot.forEach { (id, forventetRot) ->
                val faktisk = QueuedEvents.selectAll()
                    .where { QueuedEvents.id eq id }
                    .first()[QueuedEvents.aggregateRootId]
                assertEquals(forventetRot, faktisk, "rad $id skal ha derivert aggregate_root_id")
            }
        }

        val state = backfillState(BackfillTable.EVENT_QUEUE)
        assertNotNull(state.completedAt, "event_queue-jobben skal være markert fullført")
        assertEquals(idTilRot.size.toLong(), state.updated, "alle radene skulle bli oppdatert")

        // event_log er tom → sveipet er 0 med en gang, og jobben fullfører.
        assertNotNull(backfillState(BackfillTable.EVENT_LOG).completedAt)
    }

    @Test
    fun `fyller legacy-rader i event_log`() {
        // event_log har V10-checken (NOT VALID). Den håndheves ved INSERT, så for å simulere rader
        // fra før constrainten fantes dropper vi den, legger inn NULL-rader, og kjører backfillen.
        transaction(db) {
            exec("ALTER TABLE event_log DROP CONSTRAINT event_log_aggregate_root_id_nn")
            TestEventData.allEventSamples.forEachIndexed { i, sample ->
                EventLog.insert {
                    it[id] = (i + 1).toLong()
                    it[eventData] = sample
                    it[status] = ProcessingStatus.COMPLETED
                    it[createdAt] = CurrentTimestamp
                    it[updatedAt] = CurrentTimestamp
                }
            }
        }

        runBlocking { AggregateRootIdBackfill(db, config).run() }

        transaction(db) {
            TestEventData.allEventSamples.forEachIndexed { i, sample ->
                val faktisk = EventLog.selectAll()
                    .where { EventLog.id eq (i + 1).toLong() }
                    .first()[EventLog.aggregateRootId]
                assertEquals(sample.aggregateRootId, faktisk, "logg-rad ${i + 1} skal være backfillet")
            }
        }
        assertNotNull(backfillState(BackfillTable.EVENT_LOG).completedAt)
    }

    @Test
    fun `er selv-avsluttende - en ny NULL-rad etter fullfoering roeres ikke`() {
        runBlocking { AggregateRootIdBackfill(db, config).run() }
        assertNotNull(backfillState(BackfillTable.EVENT_QUEUE).completedAt)

        // En rad som dukker opp etter at jobben er markert fullført skal ikke bli backfillet ved
        // neste oppstart — completed_at gjør run() til et billig no-op.
        val nyId = transaction(db) {
            QueuedEvents.insertReturning { it[eventData] = TestEventData.soknadInnsendt }
                .first()[QueuedEvents.id]
        }

        runBlocking { AggregateRootIdBackfill(db, config).run() }

        transaction(db) {
            val faktisk = QueuedEvents.selectAll()
                .where { QueuedEvents.id eq nyId }
                .first()[QueuedEvents.aggregateRootId]
            assertNull(faktisk, "en fullført jobb skal ikke røre nye NULL-rader")
        }
    }

    @Test
    fun `hopper over rader som allerede har aggregate_root_id`() {
        // publishEventQueue setter aggregate_root_id (P3), så disse har allerede verdi.
        val forhaandsutfylt = transaction(db) {
            List(2) { publishEventQueue(TestEventData.soknadInnsendt).id }
        }
        // Én legacy-rad uten verdi.
        val legacyId = transaction(db) {
            QueuedEvents.insertReturning { it[eventData] = TestEventData.innsendtSoknadJournalfoert }
                .first()[QueuedEvents.id]
        }

        runBlocking { AggregateRootIdBackfill(db, config).run() }

        val state = backfillState(BackfillTable.EVENT_QUEUE)
        assertEquals(1L, state.updated, "kun legacy-raden skulle oppdateres")
        assertEquals(3L, state.scanned, "alle tre radene skulle skannes")

        transaction(db) {
            (forhaandsutfylt + legacyId).forEach { id ->
                val faktisk = QueuedEvents.selectAll()
                    .where { QueuedEvents.id eq id }
                    .first()[QueuedEvents.aggregateRootId]
                assertNotNull(faktisk, "rad $id skal ha aggregate_root_id")
            }
        }
    }

    @Test
    fun `deaktivert backfill er et no-op`() {
        val legacyId = transaction(db) {
            QueuedEvents.insertReturning { it[eventData] = TestEventData.soknadInnsendt }
                .first()[QueuedEvents.id]
        }

        runBlocking { AggregateRootIdBackfill(db, config.copy(enabled = false)).run() }

        transaction(db) {
            val faktisk = QueuedEvents.selectAll()
                .where { QueuedEvents.id eq legacyId }
                .first()[QueuedEvents.aggregateRootId]
            assertNull(faktisk, "deaktivert jobb skal ikke skrive noe")

            val harState = BackfillState.selectAll()
                .where { BackfillState.jobName eq BackfillTable.EVENT_QUEUE.jobName }
                .empty()
            assertEquals(true, harState, "deaktivert jobb skal ikke opprette state-rader")
        }
    }

    private data class StateSnapshot(
        val cursorPos: Long,
        val scanned: Long,
        val updated: Long,
        val skipped: Long,
        val completedAt: kotlin.time.Instant?,
    )

    private fun backfillState(table: BackfillTable): StateSnapshot = transaction(db) {
        BackfillState.selectAll()
            .where { BackfillState.jobName eq table.jobName }
            .first()
            .let {
                StateSnapshot(
                    cursorPos = it[BackfillState.cursorPos],
                    scanned = it[BackfillState.scanned],
                    updated = it[BackfillState.updated],
                    skipped = it[BackfillState.skipped],
                    completedAt = it[BackfillState.completedAt],
                )
            }
    }
}
