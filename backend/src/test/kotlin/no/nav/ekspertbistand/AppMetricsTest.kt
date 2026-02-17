package no.nav.ekspertbistand

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandlerStates
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.ProcessingStatus
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelse.Companion.tilSoknadBehandletForsinkelse
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState
import no.nav.ekspertbistand.infrastruktur.MetricsStatementInterceptor
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
class AppMetricsTest {

    @Test
    fun `eventQueueSizeGauge reflects database rows`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                QueuedEvents.insert {
                    it[QueuedEvents.status] = ProcessingStatus.PENDING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy1")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
                QueuedEvents.insert {
                    it[QueuedEvents.status] = ProcessingStatus.PENDING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy2")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
                QueuedEvents.insert {
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy3")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry
                    .get("eventqueue.size")
                    .tag("status", ProcessingStatus.PENDING.name)
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }

                meterRegistry
                    .get("eventqueue.size")
                    .tag("status", ProcessingStatus.PROCESSING.name)
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
                    it[EventLog.status] = ProcessingStatus.COMPLETED_WITH_ERRORS
                    it[EventLog.eventData] = EventData.Foo("dummy1")
                    it[EventLog.createdAt] = CurrentTimestamp
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
                EventLog.insert {
                    it[EventLog.id] = 2L
                    it[EventLog.status] = ProcessingStatus.COMPLETED
                    it[EventLog.eventData] = EventData.Foo("dummy2")
                    it[EventLog.createdAt] = CurrentTimestamp
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
                EventLog.insert {
                    it[EventLog.id] = 3L
                    it[EventLog.status] = ProcessingStatus.COMPLETED
                    it[EventLog.eventData] = EventData.Foo("dummy3")
                    it[EventLog.createdAt] = CurrentTimestamp
                    it[EventLog.updatedAt] = CurrentTimestamp
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry
                    .get("eventlog.size")
                    .tag("status", ProcessingStatus.COMPLETED.name)
                    .gauge().let {
                        assertEquals(2.0, it.value())
                    }

                meterRegistry
                    .get("eventlog.size")
                    .tag("status", ProcessingStatus.COMPLETED_WITH_ERRORS.name)
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
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(30.seconds) // <1m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(4.minutes) // <5m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(10.minutes) // <15m
                }
                QueuedEvents.insert {
                    it[QueuedEvents.eventData] = EventData.Foo("dummy")
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.createdAt] = now.minus(35.minutes) // >30m
                }
            }

            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
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
            with(AppMetrics(dispatcher, meterRegistry)) {
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
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy1")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 2
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 2L
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.eventData] = EventData.Foo("dummy2")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 0
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 3L
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.eventData] = EventData.Bar("dummy3")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 5
                }
                QueuedEvents.insert {
                    it[QueuedEvents.id] = 4L
                    it[QueuedEvents.status] = ProcessingStatus.PROCESSING
                    it[QueuedEvents.eventData] = EventData.Bar("dummy4")
                    it[QueuedEvents.updatedAt] = CurrentTimestamp
                    it[QueuedEvents.attempts] = 1
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
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

    @Test
    fun `soknadBehandletForsinkelseGauge shows buckets for age per status`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val now = Clock.System.now()
            val mockClock = object : Clock {
                override fun now() = now
            }
            transaction {
                val durations = listOf(
                    now.minus(30.minutes), // <=1h
                    now.minus(2.hours), // <=1d
                    now.minus(25.hours), // <=2d
                    now.minus(3.days), // <=1w
                    now.minus(8.days) // >1w
                )
                durations.forEach { duration ->
                    SoknadBehandletForsinkelseState.insert {
                        it[soknadId] = UUID.randomUUID().toString()
                        it[innsendtTidspunkt] = duration
                        it[godkjentTidspunkt] = null
                        it[avlystTidspunkt] = null
                    }
                    SoknadBehandletForsinkelseState.insert {
                        it[soknadId] = UUID.randomUUID().toString()
                        it[innsendtTidspunkt] = duration
                        it[godkjentTidspunkt] = now
                        it[avlystTidspunkt] = null
                    }
                    SoknadBehandletForsinkelseState.insert {
                        it[soknadId] = UUID.randomUUID().toString()
                        it[innsendtTidspunkt] = duration
                        it[godkjentTidspunkt] = null
                        it[avlystTidspunkt] = now
                    }
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(mockClock) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                // 5 buckets * 3 status = 15 rows
                listOf("innsendt", "godkjent", "avlyst").forEach { status ->
                    listOf("<=1h", "<=1d", "<=2d", "<=1w", ">1w").forEach { ageBucket ->
                        meterRegistry.get("soknad.behandlet.age")
                            .tag("status", status)
                            .tag("age", ageBucket)
                            .gauge().let {
                                assertEquals(1.0, it.value())
                            }
                    }
                }
            }
        }
    }

    @Test
    fun `tilskuddsbrevVistProsentGauge shows percentage of brev vist`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            transaction {
                TilskuddsbrevVistState.insert {
                    it[tilskuddsbrevOpprettetAt] = Clock.System.now()
                    it[tilsagnNummer] = "1"
                    it[tilskuddsbrevFoersVistAt] = null
                }
                TilskuddsbrevVistState.insert {
                    it[tilskuddsbrevOpprettetAt] = Clock.System.now()
                    it[tilsagnNummer] = "2"
                    it[tilskuddsbrevFoersVistAt] = Clock.System.now()
                }
                TilskuddsbrevVistState.insert {
                    it[tilskuddsbrevOpprettetAt] = Clock.System.now()
                    it[tilsagnNummer] = "3"
                    it[tilskuddsbrevFoersVistAt] = null
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                meterRegistry.get("tilskuddsbrev.vist.prosent")
                    .gauge().let {
                        assertEquals(33.33333333333333, it.value())
                    }
            }
        }
    }

    @Test
    fun `tilskuddsbrevVistAgeGauge shows age per status bucketed`() = runTest {
        TestDatabase().cleanMigrate().use {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val now = Clock.System.now()
            transaction {
                val ages = listOf(
                    now.minus(30.minutes), // <=1h
                    now.minus(2.hours), // <=1d
                    now.minus(25.hours), // <=2d
                    now.minus(3.days), // <=1w
                    now.minus(8.days) // >1w
                )
                ages.forEach { age ->
                    TilskuddsbrevVistState.insert {
                        it[tilskuddsbrevOpprettetAt] = age
                        it[tilsagnNummer] = UUID.randomUUID().toString()
                        it[tilskuddsbrevFoersVistAt] = now
                    }
                }
            }
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
            with(AppMetrics(dispatcher, meterRegistry)) {
                val updateGaugeJob = launch { updateGaugesProcessingLoop(Clock.System) }
                delay(60.seconds)
                updateGaugeJob.cancel()

                listOf("<=1h", "<=1d", "<=2d", "<=1w", ">1w").forEach { ageBucket ->
                    meterRegistry.get("tilskuddsbrev.vist.age")
                        .tag("age", ageBucket)
                        .gauge().let {
                            assertEquals(1.0, it.value())
                        }
                }
            }
        }
    }

    @Test
    fun `MetricsStatementInterceptor records database query execution times`() = runTest {
        TestDatabase().cleanMigrate().use {
            val now = Clock.System.now()

            // Create some test data
            transaction {
                SoknadBehandletForsinkelseState.insert {
                    it[soknadId] = UUID.randomUUID().toString()
                    it[innsendtTidspunkt] = now.minus(2.hours)
                    it[godkjentTidspunkt] = null
                    it[avlystTidspunkt] = now
                }
            }

            val simpleMeterRegistry = SimpleMeterRegistry()
            // Ensure no timers are recorded before the interceptor is registered
            assertEquals(0, simpleMeterRegistry.find(MetricsStatementInterceptor.TIMER_ID).timers().count())
            transaction {
                registerInterceptor(MetricsStatementInterceptor(
                    meterRegistry = simpleMeterRegistry
                ))

                SoknadBehandletForsinkelseState.selectAll()
                    .where { SoknadBehandletForsinkelseState.innsendtTidspunkt lessEq now }
                    .count()
            }


            // Verify that the metric is recorded
            val timers = simpleMeterRegistry.find(MetricsStatementInterceptor.TIMER_ID).timers()
            assertEquals(1, timers.count())
            timers.first().let { timer ->
                assertEquals(1, timer.count())
                assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS) > 0)

                // ensure percentiles are enabled
                assertTrue(timer.takeSnapshot().percentileValues().isNotEmpty())
                timer.takeSnapshot().percentileValues().forEach { percentile ->
                    assertTrue(percentile.value() > 0)
                }

                // ensure the SQL tag contains the parameterized query, not the raw query with parameters
                assertEquals(
                    "SELECT COUNT(*) FROM soknad_behandlet_forsinkelse_state WHERE soknad_behandlet_forsinkelse_state.innsendt_at <= ?",
                    timer.id.getTag("sql")
                )

            }

        }
    }



}