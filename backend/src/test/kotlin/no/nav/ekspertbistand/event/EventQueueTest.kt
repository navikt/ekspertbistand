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

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `publish and poll returns event`() {
        val event = EventData.Foo(fooName = "bar")
        queue.publish(event)
        val polled = queue.poll()
        assertEquals(event, polled!!.eventData)
    }

    @Test
    fun `finalize moves event to log and removes from queue`() {
        val event = EventData.Foo(fooName = "baz")
        val published = queue.publish(event)
        val polled = queue.poll()
        assertNotNull(polled)
        assertEquals(event, polled.eventData)
        queue.finalize(polled.id)

        transaction(testDb.config.jdbcDatabase) {
            assertNull(QueuedEvents.selectAll().where { QueuedEvents.id eq published.id }.firstOrNull())
            assertNotNull(EventLog.selectAll().where { EventLog.id eq published.id }.firstOrNull())
        }
    }

    @Test
    fun `concurrent poll only returns event to one process`() = runTest {
        val event = EventData.Foo(fooName = "concurrent")
        val published = queue.publish(event)
        val results = mutableListOf<QueuedEvent>()
        coroutineScope {
            repeat(5) {
                launch(Dispatchers.IO) {
                    queue.poll()?.let {
                        results.add(it)
                    }
                }
            }
        }
        assertEquals(1, results.size)
        assertEquals(event, results.first().eventData)
        assertEquals(published.eventData, results.first().eventData)
    }

    @Test
    fun `abandoned event is made available after timeout`() {
        val event = EventData.Foo(fooName = "timeout")
        val published = queue.publish(event)

        // Poll and leave in PROCESSING
        val polled = queue.poll()
        assertEquals(event, polled!!.eventData)

        // Poll again returns null before timeout
        queue.poll().also {
            assertNull(it)
        }

        // Poll again after timeout returns the abandoned event
        queue.poll(object : Clock {
            // Simulate time passing beyond abandonedTimeout
            override fun now(): Instant = Clock.System.now().plus(3.minutes)
        }).also {
            assertEquals(event, it!!.eventData)
            assertEquals(event, published.eventData)
        }
    }

    @Test
    fun `abandoned event is made available after timeout even while other events are published`() {
        val event = EventData.Foo(fooName = "timeout")
        val published = queue.publish(event)

        // Poll and leave in PROCESSING
        val polled = queue.poll()
        assertEquals(event, polled!!.eventData)

        // publish other events
        queue.publish(EventData.Foo(fooName = "another event"))
        queue.publish(EventData.Foo(fooName = "and another event"))

        queue.poll(object : Clock {
            // Simulate time passing beyond abandonedTimeout
            override fun now(): Instant = Clock.System.now().plus(3.minutes)
        }).also {
            assertEquals(event, it!!.eventData)
            assertEquals(event, published.eventData)
        }
    }

    @Test
    fun `finalize is idempotent`() {
        val event = EventData.Foo(fooName = "idempotent")
        val published = queue.publish(event)
        val polled = queue.poll()
        assertEquals(event, polled!!.eventData)
        queue.finalize(published.id)
        // Second finalize should not throw or duplicate
        queue.finalize(published.id)
        transaction(testDb.config.jdbcDatabase) {
            val logs = EventLog.selectAll().where { EventLog.id eq published.id }.toList()
            assertEquals(1, logs.size)
        }
    }
}

