package no.nav.ekspertbistand.services

import kotlinx.coroutines.runBlocking
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdempotencyGuardTest {

    @Test
    fun `Event med en subtask som ikke er guarded`() {
        val testDb = TestDatabase().cleanMigrate()
        val idempotencyGuard = IdempotencyGuard(testDb.config.jdbcDatabase)

        val event = Event(1L, EventData.Foo("fooEvent"))
        val subTask = "subtask"

        assertFalse(idempotencyGuard.isGuarded(event.id, subTask))
    }

    @Test
    fun `Event med to subtasks der den første er guarded`() {
        runBlocking {
            val testDb = TestDatabase().cleanMigrate()
            val idempotencyGuard = IdempotencyGuard(testDb.config.jdbcDatabase)

            val event1 = Event(1L, EventData.Foo("fooEvent"))
            val subTask1 = "subtask1"
            val subtask2 = "subtask2"

            idempotencyGuard.guard(event1, subTask1)

            assertTrue(idempotencyGuard.isGuarded(event1.id, subTask1))
            assertFalse(idempotencyGuard.isGuarded(event1.id, subtask2))
        }
    }
}