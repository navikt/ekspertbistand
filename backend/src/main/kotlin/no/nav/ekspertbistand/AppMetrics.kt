package no.nav.ekspertbistand

import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.ekspertbistand.event.EventHandlerStates
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.ProcessingStatus
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.projections.ProjectionBuilderState
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelse
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVist
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.extract
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class AppMetrics(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    meterRegistry: MeterRegistry = Metrics.meterRegistry,
) {
    val eventQueueSizeGauge = OppdaterbarMultiGauge(
        "eventqueue.size", "The number of items in eventqueue by status", meterRegistry
    )

    val eventQueueRetriesPerEventType = OppdaterbarMultiGauge(
        "eventqueue.retries", "The number of retries in eventqueue by event type", meterRegistry
    )

    val processingEventsAgeGauge = OppdaterbarMultiGauge(
        "eventqueue.age", "The number of items in processing state bucketed by age", meterRegistry
    )

    val eventLogSizeGauge = OppdaterbarMultiGauge(
        "eventlog.size", "The number of finalized items in event log by status", meterRegistry
    )

    val eventHandlerStateGauge = OppdaterbarMultiGauge(
        "eventhandlerstate.size", "The number of events being handled by status", meterRegistry
    )

    val soknadBehandletForsinkelseGauge = OppdaterbarMultiGauge(
        "soknad.behandlet.age", "The number of processed soknad bucketed by processing time", meterRegistry
    )

    val tilskuddsbrevVistProsentGauge = OppdaterbarMultiGauge(
        "tilskuddsbrev.vist.prosent", "Prosentandel tilskuddsbrev vist", meterRegistry
    )

    val tilskuddsbrevVistAgeGauge = OppdaterbarMultiGauge(
        "tilskuddsbrev.vist.age", "Aldersfordeling tilskuddsbrev vist", meterRegistry
    )

    val projectionBuilderLagGauge = OppdaterbarMultiGauge(
        "projectionbuilder.lag", "Lag per projection builder", meterRegistry
    )

    val aggregateRootIdMissingGauge = OppdaterbarMultiGauge(
        "event.aggregaterootid.missing", "Antall rader uten aggregate_root_id, per tabell", meterRegistry
    )

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
                val createdAt = row[QueuedEvents.createdAt]
                val age = (now - createdAt).inWholeMinutes
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

    /**
     * Antall rader uten `aggregate_root_id` per tabell. Skal ligge flatt på 0 etter backfillen (P4);
     * et hopp over 0 betyr at noen har innført en skrivevei som omgår [publishEventQueue].
     */
    fun aggregateRootIdMissingByTable(): Map<String, Double> = transaction {
        mapOf(
            "event_queue" to QueuedEvents.selectAll()
                .where { QueuedEvents.aggregateRootId.isNull() }.count().toDouble(),
            "event_log" to EventLog.selectAll()
                .where { EventLog.aggregateRootId.isNull() }.count().toDouble(),
        )
    }


    @OptIn(ExperimentalTime::class)
    suspend fun updateGaugesProcessingLoop(clock: Clock = Clock.System) = withContext(dispatcher) {
        while (isActiveAndNotTerminating) {
            eventQueueSizeGauge.oppdater(
                queueSizeByStatus().mapKeys { (status, _) -> Tags.of("status", status.name) }
            )

            eventLogSizeGauge.oppdater(
                logSizeByStatus().mapKeys { (status, _) -> Tags.of("status", status.name) }
            )

            processingEventsAgeGauge.oppdater(
                processingEventsByAgeBucket(clock).mapKeys { (ageBucket, _) -> Tags.of("age", ageBucket) }
            )

            eventHandlerStateGauge.oppdater(
                eventHandlerStates().mapKeys { (key, _) ->
                    val (handlerId, result) = key
                    Tags.of(
                        "handlerId", handlerId,
                        "result", result.substringAfterLast(".")
                    )
                }
            )

            eventQueueRetriesPerEventType.oppdater(
                queueRetriesByEventType().mapKeys { (eventType, _) -> Tags.of("event", eventType) }
            )

            soknadBehandletForsinkelseGauge.oppdater(
                SoknadBehandletForsinkelse.soknadBehandletForsinkelseByAgeBucket(clock)
                    .mapKeys { (key, _) ->
                        val (status, ageBucket) = key
                        Tags.of(
                            "status", status,
                            "age", ageBucket
                        )
                    }
            )

            tilskuddsbrevVistProsentGauge.oppdater(
                mapOf(Tags.empty() to TilskuddsbrevVist.tilskuddsbrevVistProsent())
            )

            tilskuddsbrevVistAgeGauge.oppdater(
                TilskuddsbrevVist.tilskuddsbrevFoerstVistAlderFordelt()
                    .mapKeys { (ageBucket, _) -> Tags.of("age", ageBucket) }
            )

            projectionBuilderLagGauge.oppdater(
                ProjectionBuilderState.lagPerBuilder().mapKeys { (builderName, _) -> Tags.of("builder", builderName) }
            )

            aggregateRootIdMissingGauge.oppdater(
                aggregateRootIdMissingByTable().mapKeys { (table, _) -> Tags.of("table", table) }
            )

            delay(60.seconds)
        }
    }
}

@OptIn(ExperimentalTime::class)
fun Application.configureAppMetrics() {
    val appMetrics = AppMetrics()

    launch {
        appMetrics.updateGaugesProcessingLoop()
    }
}

/**
 * [MultiGauge] der hver rad registreres én gang og leser siste verdi fra et map.
 *
 * `MultiGauge.register(rows, overwrite = true)` fjerner og registrerer hver rad på nytt. Er raden den eneste
 * med det metrikknavnet, avregistreres Prometheus-collectoren og en ny registreres. Et scrape som pågår samtidig
 * kan da få med begge og feiler med `DuplicateLabelsException`.
 *
 * Micrometer fikset en beslektet variant i 1.16.8/1.17.2, men bare for duplikater inne i én collector,
 * ikke på tvers av en avregistrert og en ny collector.
 *
 * @see <a href="https://github.com/micrometer-metrics/micrometer/issues/6851">micrometer#6851</a>
 * @see <a href="https://docs.micrometer.io/micrometer/reference/concepts/gauges.html#_multi_gauge">Micrometer: Multi-gauge</a>
 */
class OppdaterbarMultiGauge(name: String, description: String, meterRegistry: MeterRegistry) {
    private val multiGauge = MultiGauge.builder(name).description(description).register(meterRegistry)
    private val verdier = ConcurrentHashMap<Tags, Double>()

    fun oppdater(nyeVerdier: Map<Tags, Number>) {
        nyeVerdier.forEach { (tags, verdi) -> verdier[tags] = verdi.toDouble() }
        multiGauge.register(
            nyeVerdier.keys.map { tags -> MultiGauge.Row.of(tags) { verdier[tags] ?: Double.NaN } },
            false
        )
        verdier.keys.retainAll(nyeVerdier.keys)
    }
}
