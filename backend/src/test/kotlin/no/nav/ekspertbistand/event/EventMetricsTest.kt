package no.nav.ekspertbistand.event

import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.event.ProcessingStatus.PENDING
import no.nav.ekspertbistand.event.ProcessingStatus.PROCESSING
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class EventMetricsTest {
    private lateinit var testDb: TestDatabase

    @BeforeEach
    fun setup() {
        testDb = TestDatabase().cleanMigrate()
    }

    @Test
    fun `eventQueueSizeGauge reflects database rows`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        transaction {
            QueuedEvents.insert {
                it[QueuedEvents.status] = PENDING
                it[QueuedEvents.event] = Event.Foo("dummy1")
                it[QueuedEvents.updatedAt] = CurrentTimestamp
            }
            QueuedEvents.insert {
                it[QueuedEvents.status] = PENDING
                it[QueuedEvents.event] = Event.Foo("dummy2")
                it[QueuedEvents.updatedAt] = CurrentTimestamp
            }
            QueuedEvents.insert {
                it[QueuedEvents.status] = PROCESSING
                it[QueuedEvents.event] = Event.Foo("dummy3")
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

    @Test
    fun `processingEventsAgeGauge reflects correct age buckets`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val now = Clock.System.now()
        transaction {
            QueuedEvents.insert {
                it[QueuedEvents.event] = Event.Foo("dummy")
                it[QueuedEvents.status] = PROCESSING
                it[QueuedEvents.updatedAt] = now.minus(30.seconds) // <1m
            }
            QueuedEvents.insert {
                it[QueuedEvents.event] = Event.Foo("dummy")
                it[QueuedEvents.status] = PROCESSING
                it[QueuedEvents.updatedAt] = now.minus(4.minutes) // <5m
            }
            QueuedEvents.insert {
                it[QueuedEvents.event] = Event.Foo("dummy")
                it[QueuedEvents.status] = PROCESSING
                it[QueuedEvents.updatedAt] = now.minus(10.minutes) // <15m
            }
            QueuedEvents.insert {
                it[QueuedEvents.event] = Event.Foo("dummy")
                it[QueuedEvents.status] = PROCESSING
                it[QueuedEvents.updatedAt] = now.minus(35.minutes) // >30m
            }
        }

        val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        with(EventMetrics(dispatcher, meterRegistry)) {
            val updateGaugeJob = launch { updateGaugesProcessingLoop(object : Clock {
                override fun now() = now
            }) }
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