package no.nav.ekspertbistand.event

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.logger
import org.junit.jupiter.api.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
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
            handle<Event.Foo>("InlineSucceeds") {
                // inline handler
                EventHandledResult.Success()
            }
            handle<Event.Foo>("DelegatedSucceeds") {
                // delegated to object
                DummyFooHandler.handle(it)
            }

            // handler via class instance
            handler(FooRetryThenSucceedsHandler())
        }
        val queuedEvent = EventQueue.publish(Event.Foo("test1"))
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

        // move time forward to exceed abandoned timeout
        now += 2.seconds

        delay(1) // give pollJob some time for processing

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
            handle<Event.Bar>("FailsFatally") {
                EventHandledResult.FatalError("fatal failure")
            }
            handle<Event.Bar>("ShouldNotBeRetried") {
                // because of fatal error in other handler, this should not be retried
                answers.removeFirst()
            }
        }
        val queuedEvent = EventQueue.publish(Event.Bar("testFatal"))
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
            assertIs<EventHandledResult.FatalError>(handled["FailsFatally"]?.result)
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
            assertIs<EventHandledResult.FatalError>(handled["FailsFatally"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["ShouldNotBeRetried"]?.result)
        }

        val cleanupJob = launch { manager.cleanupFinalizedEvents() }
        delay(1)
        assertEquals(emptyMap(), manager.handledEvents(queuedEvent.id))

        pollJob.cancel()
        cleanupJob.cancel()
    }
}


object DummyFooHandler {
    fun handle(event: Event.Foo) = EventHandledResult.Success()
}

class FooRetryThenSucceedsHandler : EventHandler<Event.Foo> {
    private var attempt = 0
    override val id: String = "FooRetryThenSucceedsHandler"
    override fun handle(event: Event.Foo): EventHandledResult {
        logger().info("Handling Foo event with retry, attempt $attempt")
        return if (attempt < 1) {
            attempt++
            EventHandledResult.TransientError("Temporary failure, attempt $attempt")
        } else {
            EventHandledResult.Success()
        }
    }
}