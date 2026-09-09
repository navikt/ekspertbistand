package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.arena.ArenaMeldingIdempotencyTable
import no.nav.ekspertbistand.arena.markerTilsagnsbrevMeldingSomBehandlet
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Atomisitetsgarantiene for [publishEventQueue] (P1 i spesifikasjonen).
 *
 * Publisering skjer alltid i kallerens transaksjon — receiveren er en `JdbcTransaction`, så
 * publisering uten transaksjon finnes ikke som gyldig kode. Det som gjenstår å teste er at
 * publiseringen commiter og rulles tilbake sammen med kallerens transaksjon, spesielt at
 * Arena-prosessorenes marker+publish er atomisk.
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
    fun `publishEventQueue rulles tilbake med kallerens transaksjon`() {
        assertFailsWith<IllegalStateException> {
            transaction(db) {
                publishEventQueue(TestEventData.soknadInnsendt)
                error("rull tilbake")
            }
        }

        transaction(db) {
            assertEquals(0, QueuedEvents.selectAll().count(), "eventet skal ha blitt rullet tilbake")
        }
    }

    @Test
    fun `marker og publish er atomiske - rollback av markering ruller ogsaa tilbake publiseringen`() {
        assertFailsWith<IllegalStateException> {
            transaction(db) {
                val ikkeTidligereBehandlet = markerTilsagnsbrevMeldingSomBehandlet(42)
                assertTrue(ikkeTidligereBehandlet)
                publishEventQueue(TestEventData.soknadInnsendt)
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
