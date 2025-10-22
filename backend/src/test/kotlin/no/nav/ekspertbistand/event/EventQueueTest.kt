package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class EventQueueTest {
    private lateinit var testDb: TestDatabase
    private lateinit var queue: EventQueue

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
        queue = EventQueue
    }

    @Test
    fun `publish and poll returns event`() {
        val event = Event.Foo(fooName = "bar")
        queue.publish(event)
        val polled = queue.poll()
        assertEquals(event, polled!!.event)
    }

    @Test
    fun `finalize moves event to log and removes from queue`() {
        val event = Event.Foo(fooName = "baz")
        val published = queue.publish(event)
        val polled = queue.poll()
        assertNotNull(polled)
        assertEquals(event, polled.event)
        queue.finalize(polled.id)

        transaction(testDb.config.jdbcDatabase) {
            assertNull(QueuedEvents.selectAll().where { QueuedEvents.id eq published.id }.firstOrNull())
            assertNotNull(EventLog.selectAll().where { EventLog.id eq published.id }.firstOrNull())
        }
    }

    @Test
    fun `concurrent poll only returns event to one process`() = runTest {
        val event = Event.Foo(fooName = "concurrent")
        val published = queue.publish(event)
        val results = mutableListOf<QueuedEvent>()
        coroutineScope {
            repeat(5) {
                launch(Dispatchers.IO) {
                    queue.poll()?.let  {
                        results.add(it)
                    }
                }
            }
        }
        assertEquals(1, results.size)
        assertEquals(event, results.first().event)
        assertEquals(published.event, results.first().event)
    }

    @Test
    fun `abandoned event is made available after timeout`() {
        val event = Event.Foo(fooName = "timeout")
        val published = queue.publish(event)

        // Poll and leave in PROCESSING
        val polled = queue.poll()
        assertEquals(event, polled!!.event)

        // Poll again returns null before timeout
        queue.poll().also {
            assertNull(it)
        }

        // Poll again after timeout returns the abandoned event
        queue.poll(object : Clock {
            // Simulate time passing beyond abandonedTimeout
            override fun now(): Instant = Clock.System.now().plus(3.minutes)
        }).also {
            assertEquals(event, it!!.event)
            assertEquals(event, published.event)
        }
    }

    @Test
    fun `abandoned event is made available after timeout even while other events are published`() {
        val event = Event.Foo(fooName = "timeout")
        val published = queue.publish(event)

        // Poll and leave in PROCESSING
        val polled = queue.poll()
        assertEquals(event, polled!!.event)

        // publish other events
        queue.publish(Event.Foo(fooName = "another event"))
        queue.publish(Event.Foo(fooName = "and another event"))

        queue.poll(object : Clock {
            // Simulate time passing beyond abandonedTimeout
            override fun now(): Instant = Clock.System.now().plus(3.minutes)
        }).also {
            assertEquals(event, it!!.event)
            assertEquals(event, published.event)
        }
    }

    @Test
    fun `finalize is idempotent`() {
        val event = Event.Foo(fooName = "idempotent")
        val published = queue.publish(event)
        val polled = queue.poll()
        assertEquals(event, polled!!.event)
        queue.finalize(published.id)
        // Second finalize should not throw or duplicate
        queue.finalize(published.id)
        transaction(testDb.config.jdbcDatabase) {
            val logs = EventLog.selectAll().where { EventLog.id eq published.id }.toList()
            assertEquals(1, logs.size)
        }
    }
}

