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
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
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

    @AfterTest
    fun teardown() {
        testDb.close()
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
            register<EventData.SoknadInnsendt>("InlineSucceeds") {
                // inline handler
                EventHandledResult.Success()
            }
            register<EventData.SoknadInnsendt>("DelegatedSucceeds") {
                // delegated to object
                DummyHandler.handle(it)
            }

            // handler via class instance
            register(RetryThenSucceedsHandler())
        }
        val queuedEvent = EventQueue.publish(TestEventData.soknadInnsendt)
        val pollJob = launch { manager.runProcessLoop() }


        // move time forward but not enough to exceed abandoned timeout
        now += (EventQueue.abandonedTimeout - 1.seconds)

        delay(config.pollDelayMs.milliseconds) // give pollJob some time for processing

        // first attempt: two succeed, one transient error
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "SoknadInnsendtRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["SoknadInnsendtRetryThenSucceedsHandler"]?.result)
        }

        delay(1.milliseconds) // give pollJob some time for processing

        // no change yet, still within abandoned timeout
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "SoknadInnsendtRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.TransientError>(handled["SoknadInnsendtRetryThenSucceedsHandler"]?.result)
        }

        // move time forward to exceed abandoned timeout
        now += 2.seconds

        delay(1.milliseconds) // give pollJob some time for processing

        // second attempt: all succeed
        manager.handledEvents(queuedEvent.id).let { handled ->
            assertEquals(
                setOf(
                    "InlineSucceeds",
                    "DelegatedSucceeds",
                    "SoknadInnsendtRetryThenSucceedsHandler"
                ),
                handled.keys
            )
            assertIs<EventHandledResult.Success>(handled["InlineSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["DelegatedSucceeds"]?.result)
            assertIs<EventHandledResult.Success>(handled["SoknadInnsendtRetryThenSucceedsHandler"]?.result)
        }

        val cleanupJob = launch { manager.cleanupFinalizedEvents() }

        delay(1.milliseconds) // give cleanupJob some time for processing

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
            EventHandledResult.TransientError("Unknown", "temporary failure"),
            EventHandledResult.Success()
        )
        val manager = EventManager(config) {
            register<EventData.SoknadInnsendt>("FailsFatally") {
                EventHandledResult.UnrecoverableError("Unknown", "fatal failure")
            }
            register<EventData.SoknadInnsendt>("ShouldNotBeRetried") {
                // because of fatal error in other handler, this should not be retried
                answers.removeFirst()
            }
        }
        val queuedEvent = EventQueue.publish(TestEventData.soknadInnsendt)
        val pollJob = launch { manager.runProcessLoop() }

        delay(1.milliseconds) // give pollJob some time for processing

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

        delay(1.milliseconds) // give pollJob some time for processing

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
        delay(1.milliseconds)
        assertEquals(emptyMap(), manager.handledEvents(queuedEvent.id))

        pollJob.cancel()
        cleanupJob.cancel()
    }

    @Test
    fun `validates that all handlers have unique id`() = runTest {
        val exception = assertFailsWith<IllegalArgumentException> {
            EventManager(EventManagerConfig()) {
                register<EventData.SoknadInnsendt>("DuplicateHandler") {
                    EventHandledResult.Success()
                }
                register<EventData.InnsendtSoknadJournalfoert>("DuplicateHandler") {
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
        val queuedEvent = EventQueue.publish(TestEventData.soknadInnsendt)
        val pollJob = launch { manager.runProcessLoop() }
        delay(config.pollDelayMs.milliseconds)

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
            delay(config.pollDelayMs.milliseconds) // give pollJob some time for processing
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
            register<EventData.SoknadInnsendt>("SoknadInnsendtDoesNotGetInnsendtSoknadJournalfoert") {
                assertIs<EventData.SoknadInnsendt>(it.data)
                EventHandledResult.Success()
            }
            register<EventData.InnsendtSoknadJournalfoert>("InnsendtSoknadJournalfoertHandlerDoesNotGetSoknadInnsendt") {
                // assert routing does not give us any foo
                assertIs<EventData.InnsendtSoknadJournalfoert>(it.data)
                EventHandledResult.Success()
            }
        }
        val queuedEvent1 = EventQueue.publish(TestEventData.soknadInnsendt)
        val queuedEvent2 = EventQueue.publish(TestEventData.innsendtSoknadJournalfoert)

        val pollJob = launch { manager.runProcessLoop() }

        delay(config.pollDelayMs.milliseconds) // give pollJob some time for processing

        manager.handledEvents(queuedEvent1.id).let { handled ->
            assertEquals(setOf("SoknadInnsendtDoesNotGetInnsendtSoknadJournalfoert"), handled.keys)
            assertIs<EventHandledResult.Success>(handled["SoknadInnsendtDoesNotGetInnsendtSoknadJournalfoert"]?.result)
        }

        delay(config.pollDelayMs.milliseconds) // give pollJob some time for processing

        manager.handledEvents(queuedEvent2.id).let { handled ->
            assertEquals(setOf("InnsendtSoknadJournalfoertHandlerDoesNotGetSoknadInnsendt"), handled.keys)
            assertIs<EventHandledResult.Success>(handled["InnsendtSoknadJournalfoertHandlerDoesNotGetSoknadInnsendt"]?.result)
        }

        pollJob.cancel()
    }
}


object DummyHandler {
    @Suppress("unused")
    fun handle(event: Event<EventData.SoknadInnsendt>) = EventHandledResult.Success()
}

class RetryThenSucceedsHandler : EventHandler<EventData.SoknadInnsendt> {
    private var attempt = 0
    override val id: String = "SoknadInnsendtRetryThenSucceedsHandler"
    override val eventType = EventData.SoknadInnsendt::class
    override suspend fun handle(event: Event<EventData.SoknadInnsendt>): EventHandledResult {
        logger().info("Handling SoknadInnsendt event with retry, attempt $attempt")
        return if (attempt < 1) {
            attempt++
            EventHandledResult.TransientError("Unknown", "Temporary failure, attempt $attempt")
        } else {
            EventHandledResult.Success()
        }
    }
}