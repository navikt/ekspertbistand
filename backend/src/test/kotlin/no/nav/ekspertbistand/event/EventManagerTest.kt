package no.nav.ekspertbistand.event

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class EventManagerTest {
    val log = logger()
    private lateinit var testDb: TestDatabase

    @BeforeTest
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `event manager retries`() = runTest {
        var now = Clock.System.now()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val config = EventManagerConfig(
            pollDelayMs = 1,
            dispatcher = dispatcher,
            clock = object : Clock {
                override fun now() = now
            },
        )
        val manager = EventManager(config) {
            register<EventData.Foo>("InlineSucceeds") {
                // inline handler
                EventHandledResult.Success()
            }
            register<EventData.Foo>("DelegatedSucceeds") {
                // delegated to object
                DummyFooHandler.handle(it)
            }

            // handler via class instance
            register(FooRetryThenSucceedsHandler())
        }
        val queuedEvent = EventQueue.publish(EventData.Foo("test1"))
        val pollJob = launch { manager.runProcessLoop() }


        // move time forward but not enough to exceed abandoned timeout
        now += (EventQueue.abandonedTimeout - 1.seconds)

        delay(config.pollDelayMs) // give pollJob some time for processing

        // first attempt: two succeed, one transient error
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "FooRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["FooRetryThenSucceedsHandler"]?.result)
        }

        delay(1) // give pollJob some time for processing

        // no change yet, still within abandoned timeout
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "FooRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["FooRetryThenSucceedsHandler"]?.result)
        }

        // move time forward to exceed abandoned timeout
        now += 2.seconds

        delay(1) // give pollJob some time for processing

        // second attempt: all succeed
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "FooRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["FooRetryThenSucceedsHandler"]?.result)
        }

        val cleanupJob = launch { manager.cleanupFinalizedEvents() }

        delay(1) // give cleanupJob some time for processing

        // after cleanup, no handled events should remain due to finalization
        assertEquals(emptyMap(), manager.handledEvents(queuedEvent.id))

        pollJob.cancel()
        cleanupJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `event manager stops on fatal error`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var now = Clock.System.now()
        val config = EventManagerConfig(
            pollDelayMs = 1,
            dispatcher = dispatcher,
            clock = object : Clock {
                override fun now() = now
            },
        )
        val answers = mutableListOf(
            EventHandledResult.TransientError("temporary failure"),
            EventHandledResult.Success()
        )
        val manager = EventManager(config) {
            register<EventData.Bar>("FailsFatally") {
                EventHandledResult.UnrecoverableError("fatal failure")
            }
            register<EventData.Bar>("ShouldNotBeRetried") {
                // because of fatal error in other handler, this should not be retried
                answers.removeFirst()
            }
        }
        val queuedEvent = EventQueue.publish(EventData.Bar("testFatal"))
        val pollJob = launch { manager.runProcessLoop() }

        delay(1) // give pollJob some time for processing

        // first attempt: one succeed, one transient error
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "FailsFatally",
                    "ShouldNotBeRetried",
                ),
                handled.keys
            )
            assertIs<EventHandledResult.UnrecoverableError>(handled["FailsFatally"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["ShouldNotBeRetried"]?.result)
        }

        // move time forward to exceed abandoned timeout
        now += (EventQueue.abandonedTimeout + 1.seconds)

        delay(1) // give pollJob some time for processing

        // second attempt: no change, because processing should have stopped after fatal error
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "FailsFatally",
                    "ShouldNotBeRetried",
                ),
                handled.keys
            )
            assertIs<EventHandledResult.UnrecoverableError>(handled["FailsFatally"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["ShouldNotBeRetried"]?.result)
        }

        val cleanupJob = launch { manager.cleanupFinalizedEvents() }
        delay(1)
        assertEquals(emptyMap(), manager.handledEvents(queuedEvent.id))

        pollJob.cancel()
        cleanupJob.cancel()
    }

    @Test
    fun `validates that all handlers have unique id`() = runTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            EventManager(EventManagerConfig()) {
                register<EventData.Foo>("DuplicateHandler") {
                    EventHandledResult.Success()
                }
                register<EventData.Bar>("DuplicateHandler") {
                    EventHandledResult.Success()
                }
            }
        }
        assertEquals("Handler with id 'DuplicateHandler' is already registered", exception.message)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `unhandled events are retried indefinitely`() = runTest {
        var now = Clock.System.now()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val config = EventManagerConfig(
            pollDelayMs = 1,
            dispatcher = dispatcher,
            clock = object : Clock {
                override fun now() = now
            },
        )
        val manager = EventManager(config) {
            // no handlers for foo registered
        }
        val queuedEvent = EventQueue.publish(EventData.Foo("test1"))
        val pollJob = launch { manager.runProcessLoop() }
        delay(config.pollDelayMs)

        for (attempt in 1..10) {
            transaction {
                QueuedEvents
                    .selectAll()
                    .map { it.tilQueuedEvent() }
            }.let { queuedEvents ->
                assertEquals(1, queuedEvents.size)
                assertEquals(queuedEvent.id, queuedEvents.first().id)
                assertEquals(ProcessingStatus.PROCESSING, queuedEvents.first().status)
                assertEquals(attempt, queuedEvents.first().attempts)
            }

            now += (EventQueue.abandonedTimeout + 1.seconds)
            delay(config.pollDelayMs) // give pollJob some time for processing
        }

        pollJob.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `event manager routes events correctly`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val config = EventManagerConfig(
            pollDelayMs = 1,
            dispatcher = dispatcher,
        )
        val manager = EventManager(config) {
            register<EventData.Foo>("FooDoesNotGetBar") {
                assertIs<EventData.Foo>(it.data)
                EventHandledResult.Success()
            }
            register<EventData.Bar>("BarHandlerDoesNotGetFoo") {
                // assert routing does not give us any foo
                assertIs<EventData.Bar>(it.data)
                EventHandledResult.Success()
            }
        }
        val queuedEvent1 = EventQueue.publish(EventData.Foo("test1"))
        val queuedEvent2 = EventQueue.publish(EventData.Bar("test2"))

        val pollJob = launch { manager.runProcessLoop() }

        delay(config.pollDelayMs) // give pollJob some time for processing

        manager.handledEvents(queuedEvent1.id).let { handled ->
            assertEquals(setOf("FooDoesNotGetBar"), handled.keys)
            assertIs<EventHandledResult.Success>(handled["FooDoesNotGetBar"]?.result)
        }

        delay(config.pollDelayMs) // give pollJob some time for processing

        manager.handledEvents(queuedEvent2.id).let { handled ->
            assertEquals(setOf("BarHandlerDoesNotGetFoo"), handled.keys)
            assertIs<EventHandledResult.Success>(handled["BarHandlerDoesNotGetFoo"]?.result)
        }

        pollJob.cancel()
    }
}


object DummyFooHandler {
    fun handle(event: Event<EventData.Foo>) = EventHandledResult.Success()
}

class FooRetryThenSucceedsHandler : EventHandler<EventData.Foo> {
    private var attempt = 0
    override val id: String = "FooRetryThenSucceedsHandler"
    override val eventType = EventData.Foo::class
    override suspend fun handle(event: Event<EventData.Foo>): EventHandledResult {
        logger().info("Handling Foo event with retry, attempt $attempt")
        return if (attempt < 1) {
            attempt++
            EventHandledResult.TransientError("Temporary failure, attempt $attempt")
        } else {
            EventHandledResult.Success()
        }
    }
}