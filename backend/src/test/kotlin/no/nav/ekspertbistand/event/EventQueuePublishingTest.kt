package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.arena.ArenaMeldingIdempotencyTable
import no.nav.ekspertbistand.arena.markerTilsagnsbrevMeldingSomBehandlet
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Atomisitetsgarantiene for de to publiseringsmetodene (P1 i spesifikasjonen).
 *
 * publishInTx skal commite/rulles tilbake med kallerens transaksjon; publish skal alltid ha sin
 * egen. Begge har en require-vakt: publish nekter å kjøre inne i en transaksjon, publishInTx nekter
 * å kjøre med en annen transaksjon enn den pågående på tråden.
 */
class EventQueuePublishingTest {
    private lateinit var testDb: TestDatabase
    private val db get() = testDb.config.jdbcDatabase

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `publishInTx rulles tilbake med kallerens transaksjon`() {
        assertFailsWith<IllegalStateException> {
            transaction(db) {
                EventQueue.publishInTx(TestEventData.soknadInnsendt)
                error("rull tilbake")
            }
        }

        transaction(db) {
            assertEquals(0, QueuedEvents.selectAll().count(), "eventet skal ha blitt rullet tilbake")
        }
    }

    @Test
    fun `publish commiter i sin egen transaksjon`() {
        val published = EventQueue.publish(TestEventData.soknadInnsendt)

        transaction(db) {
            assertNotNull(
                QueuedEvents.selectAll().where { QueuedEvents.id eq published.id }.firstOrNull(),
                "eventet skal være commitet uavhengig av kalleren"
            )
        }
    }

    @Test
    fun `publish kalt inne i en transaksjon feiler paa require`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            transaction(db) {
                EventQueue.publish(TestEventData.soknadInnsendt)
            }
        }
        assertTrue(
            ex.message?.contains("publishInTx") == true,
            "feilmeldingen skal peke på riktig metode, var: ${ex.message}"
        )

        transaction(db) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `publishInTx med en annen transaksjon enn den paagaaende feiler paa require`() {
        // Fang en transaksjon som ikke lenger er den pågående på tråden.
        val fremmedTx = transaction(db) { this }

        transaction(db) {
            val ex = assertFailsWith<IllegalArgumentException> {
                EventQueue.publishInTx(TestEventData.soknadInnsendt)
            }
            assertTrue(
                ex.message?.contains("pågående") == true,
                "feilmeldingen skal forklare at feil transaksjon ble sendt inn, var: ${ex.message}"
            )
        }

        transaction(db) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `marker og publish er atomiske - rollback av markering ruller ogsaa tilbake publiseringen`() {
        assertFailsWith<IllegalStateException> {
            transaction(db) {
                val ikkeTidligereBehandlet = markerTilsagnsbrevMeldingSomBehandlet(42)
                assertTrue(ikkeTidligereBehandlet)
                EventQueue.publishInTx(TestEventData.soknadInnsendt)
                error("simuler feil etter marker + publish")
            }
        }

        transaction(db) {
            assertEquals(0, QueuedEvents.selectAll().count(), "publiseringen skal være rullet tilbake")
            assertEquals(
                0,
                ArenaMeldingIdempotencyTable.selectAll().count(),
                "markeringen skal være rullet tilbake sammen med publiseringen"
            )
        }
    }
}
