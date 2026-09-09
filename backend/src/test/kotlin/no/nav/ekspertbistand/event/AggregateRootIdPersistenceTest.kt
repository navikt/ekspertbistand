package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.soknad.aggregateRootId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.ExperimentalTime

/**
 * P3-integrasjonstestene: `aggregate_root_id` skrives ved publisering og kopieres til `event_log`
 * ved `finalize`. Etter P6 steg 2 er kolonnen `NOT NULL` på begge tabellene, så innsetting uten
 * aggregatrot avvises av databasen.
 */
@OptIn(ExperimentalTime::class)
class AggregateRootIdPersistenceTest {
    private lateinit var testDb: TestDatabase
    private val db get() = testDb.config.jdbcDatabase
    private val soknadRoot get() = TestEventData.sampleSoknad.aggregateRootId

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `publisering setter aggregate_root_id og finalize kopierer den til event_log`() {
        val published = transaction(db) { publishEventQueue(TestEventData.soknadInnsendt) }
        assertEquals(soknadRoot, published.aggregateRootId)

        transaction(db) {
            val kolonne = QueuedEvents.selectAll()
                .where { QueuedEvents.id eq published.id }
                .first()[QueuedEvents.aggregateRootId]
            assertEquals(soknadRoot, kolonne, "aggregate_root_id skal være satt i event_queue")
        }

        EventQueue.finalize(published.id)

        transaction(db) {
            val logget = EventLog.selectAll()
                .where { EventLog.id eq published.id }
                .first()[EventLog.aggregateRootId]
            assertEquals(soknadRoot, logget, "aggregate_root_id skal være kopiert til event_log")
        }
    }

    @Test
    fun `insert i event_log uten aggregate_root_id feiler paa NOT NULL`() {
        assertFailsWith<ExposedSQLException> {
            transaction(db) {
                EventLog.insert {
                    it[id] = 9001L
                    it[eventData] = TestEventData.soknadInnsendt
                    it[status] = ProcessingStatus.COMPLETED
                    it[createdAt] = CurrentTimestamp
                    it[updatedAt] = CurrentTimestamp
                    // aggregateRootId settes ikke — skal avvises av NOT NULL (P6 steg 2)
                }
            }
        }
    }
}
