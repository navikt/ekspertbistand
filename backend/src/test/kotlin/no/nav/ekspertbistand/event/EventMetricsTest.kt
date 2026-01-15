package no.nav.ekspertbistand.event

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.event.ProcessingStatus.*
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class EventMetricsTest {

    @Test
    fun `eventQueueSizeGauge reflects database rows`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                QueuedEvents.insert {
                    it[QueuedEvents.status] = PENDING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy1")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
                QueuedEvents.insert {
                    it[QueuedEvents.status] = PENDING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy2")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
                QueuedEvents.insert {
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy3")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(EventMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry
                    .get("eventqueue.size")
                    .tag("status", PENDING.name)
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }

                meterRegistry
                    .get("eventqueue.size")
                    .tag("status", PROCESSING.name)
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
            }


        }
    }

    @Test
    fun `eventLogSizeGauge reflects database rows`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                EventLog.insert {
                    it[EventLog.id] = 1L
                    it[EventLog.status] = COMPLETED_WITH_ERRORS
                    it[EventLog.eventData] = EventData.Foo("dummy1")
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
                EventLog.insert {
                    it[EventLog.id] = 2L
                    it[EventLog.status] = COMPLETED
                    it[EventLog.eventData] = EventData.Foo("dummy2")
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
                EventLog.insert {
                    it[EventLog.id] = 3L
                    it[EventLog.status] = COMPLETED
                    it[EventLog.eventData] = EventData.Foo("dummy3")
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(EventMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry
                    .get("eventlog.size")
                    .tag("status", COMPLETED.name)
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }

                meterRegistry
                    .get("eventlog.size")
                    .tag("status", COMPLETED_WITH_ERRORS.name)
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
            }
        }
    }

    @Test
    fun `processingEventsAgeGauge reflects correct age buckets`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val now = Clock.System.now()
            transaction {
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(30.seconds) // <1m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(4.minutes) // <5m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(10.minutes) // <15m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(35.minutes) // >30m
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(EventMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch {
                    updateGaugesProcessingLoop(object : Clock {
                        override fun now() = now
                    })
                }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry.get("eventqueue.age")
                    .tag("age", "<1m")
                    .gauge().let {
                        assertEquals(1.0, it.value())

                    }
                meterRegistry.get("eventqueue.age")
                    .tag("age", "<5m")
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
                meterRegistry.get("eventqueue.age")
                    .tag("age", "<15m")
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
                meterRegistry.get("eventqueue.age")
                    .tag("age", "<30m")
                    .gauge().let {
                        assertEquals(0.0, it.value())
                    }
                meterRegistry.get("eventqueue.age")
                    .tag("age", ">30m")
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
            }
        }
    }

    @Test
    fun `eventHandlerStateGauge reflects EventHandlerStates rows grouped by handlerId and result`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                EventHandlerStates.insert {
                    it[eventId] = 1L
                    it[handlerId] = "handlerA"
                    it[result] = EventHandledResult.Success()
                    it[errorMessage] = null
                }
                EventHandlerStates.insert {
                    it[eventId] = 1L
                    it[handlerId] = "handlerB"
                    it[result] = EventHandledResult.Success()
                    it[errorMessage] = null
                }
                EventHandlerStates.insert {
                    it[eventId] = 2L
                    it[handlerId] = "handlerA"
                    it[result] = EventHandledResult.TransientError("Unknown", "fail")
                    it[errorMessage] = "fail"
                }
                EventHandlerStates.insert {
                    it[eventId] = 3L
                    it[handlerId] = "handlerB"
                    it[result] = EventHandledResult.Success()
                    it[errorMessage] = null
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(EventMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()
                meterRegistry.get("eventhandlerstate.size")
                    .tag("handlerId", "handlerA")
                    .tag("result", "Success")
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
                meterRegistry.get("eventhandlerstate.size")
                    .tag("handlerId", "handlerA")
                    .tag("result", "TransientError")
                    .gauge().let {
                        assertEquals(1.0, it.value())
                    }
                meterRegistry.get("eventhandlerstate.size")
                    .tag("handlerId", "handlerB")
                    .tag("result", "Success")
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }
            }
        }
    }

    @Test
    fun `eventQueueRetriesPerEventId reflects retries for each event id`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 1L
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy1")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 2
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 2L
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy2")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 0
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 3L
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.eventData] = EventData.Bar("dummy3")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 5
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 4L
                    it[QueuedEvents.status] = PROCESSING
                    it[QueuedEvents.eventData] = EventData.Bar("dummy4")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 1
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(EventMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()
                meterRegistry.get("eventqueue.retries")
                    .tag("event", "foo") // see SerialName in EventData
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }
                meterRegistry.get("eventqueue.retries")
                    .tag("event", "bar") // see SerialName in EventData
                    .gauge().let {
                        assertEquals(6.0, it.value())
                    }
            }
        }
    }
}