package no.nav.ekspertbistand.event

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.*
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.extract
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


class EventMetrics(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    meterRegistry: MeterRegistry = Metrics.meterRegistry,
) {
    val eventQueueSizeGauge: MultiGauge = MultiGauge.builder("eventqueue.size")
        .description("The number of items in eventqueue by status")
        .register(meterRegistry)

    val eventQueueRetriesPerEventType: MultiGauge = MultiGauge.builder("eventqueue.retries")
        .description("The number of retries in eventqueue by event type")
        .register(meterRegistry)

    val processingEventsAgeGauge: MultiGauge = MultiGauge.builder("eventqueue.age")
        .description("The number of items in processing state bucketed by age")
        .register(meterRegistry)

    val eventLogSizeGauge: MultiGauge = MultiGauge.builder("eventlog.size")
        .description("The number of finalized items in event log by status")
        .register(meterRegistry)

    val eventHandlerStateGauge: MultiGauge = MultiGauge.builder("eventhandlerstate.size")
        .description("The number of events being handled by status")
        .register(meterRegistry)

    fun queueSizeByStatus(): Map<ProcessingStatus, Double> = transaction {
        QueuedEvents
            .select(QueuedEvents.id.count(), QueuedEvents.status)
            .groupBy(QueuedEvents.status).associate {
                it[QueuedEvents.status] to it[QueuedEvents.id.count()].toDouble()
            }
    }

    fun logSizeByStatus(): Map<ProcessingStatus, Double> = transaction {
        EventLog
            .select(EventLog.id.count(), EventLog.status)
            .groupBy(EventLog.status).associate {
                it[EventLog.status] to it[EventLog.id.count()].toDouble()
            }
    }

    @OptIn(ExperimentalTime::class)
    fun processingEventsByAgeBucket(clock: Clock = Clock.System): Map<String, Int> = transaction {
        val result = mutableMapOf(
            "<1m" to 0,
            "<5m" to 0,
            "<15m" to 0,
            "<30m" to 0,
            ">30m" to 0
        )
        val now = clock.now()
        QueuedEvents
            .selectAll()
            .where { QueuedEvents.status eq ProcessingStatus.PROCESSING }
            .forEach { row ->
                val updatedAt = row[QueuedEvents.updatedAt]
                val age = (now - updatedAt).inWholeMinutes
                when {
                    age < 1 -> result["<1m"] = result["<1m"]!! + 1
                    age < 5 -> result["<5m"] = result["<5m"]!! + 1
                    age < 15 -> result["<15m"] = result["<15m"]!! + 1
                    age < 30 -> result["<30m"] = result["<30m"]!! + 1
                    else -> result[">30m"] = result[">30m"]!! + 1
                }
            }
        result
    }

    fun eventHandlerStates(): Map<Pair<String, String>, Double> = transaction {
        val resultType = EventHandlerStates.result.extract<String>("type").alias("resultType")
        EventHandlerStates
            .select(EventHandlerStates.handlerId, resultType)
            .groupBy { it[EventHandlerStates.handlerId] to it[resultType] }
            .mapValues { (_, rows) -> rows.size.toDouble() }
    }

    fun queueRetriesByEventType(): Map<String, Double> = transaction {
        val eventType = QueuedEvents.eventData.extract<String>("type")
        QueuedEvents
            .select(eventType, QueuedEvents.attempts)
            .groupBy { it[eventType] }
            .mapValues { (_, rows) -> rows.sumOf { it[QueuedEvents.attempts].toDouble() } }
            .filterValues { it > 0 }
    }
    

    @OptIn(ExperimentalTime::class)
    suspend fun updateGaugesProcessingLoop(clock: Clock = Clock.System) = withContext(dispatcher) {
        while (isActiveAndNotTerminating) {
            eventQueueSizeGauge.register(
                queueSizeByStatus()
                    .map { (status, count) ->
                        MultiGauge.Row.of(
                            Tags.of("status", status.name),
                            count
                        )
                    },
                true
            )
            eventLogSizeGauge.register(
                logSizeByStatus()
                    .map { (status, count) ->
                        MultiGauge.Row.of(
                            Tags.of("status", status.name),
                            count
                        )
                    },
                true
            )

            processingEventsAgeGauge.register(
                processingEventsByAgeBucket(clock)
                    .map { (ageBucket, count) ->
                        MultiGauge.Row.of(
                            Tags.of("age", ageBucket),
                            count
                        )
                    },
                true
            )

            eventHandlerStateGauge.register(
                eventHandlerStates()
                    .map { (key, count) ->
                        val (handlerId, result) = key
                        MultiGauge.Row.of(
                            Tags.of(
                                "handlerId", handlerId,
                                "result", result.substringAfterLast(".")
                            ),
                            count
                        )
                    },
                true
            )

            eventQueueRetriesPerEventType.register(
                queueRetriesByEventType()
                    .map { (eventType, retries) ->
                        MultiGauge.Row.of(
                            Tags.of("event", eventType),
                            retries
                        )
                    },
                true
            )

            delay(60.seconds)
        }
    }
}