package no.nav.ekspertbistand.infrastruktur

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer.builder
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

object Metrics {
    val clock: Clock = Clock.SYSTEM

    val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

/**
 * Interceptor for measuring execution time of SQL statements.
 * It records the start time before execution and calculates the duration after execution, recording it in a Micrometer Timer metric.
 * Is registered as a global interceptor via SPI, so it will measure all SQL statements executed through Exposed.
 */
@OptIn(ExperimentalAtomicApi::class)
class MetricsStatementInterceptor(
    /**
     * MeterRegistry to record metrics to. By default, it uses the global MeterRegistry, but can be overridden for testing.
     */
    val meterRegistry: MeterRegistry = Metrics.meterRegistry,
) : GlobalStatementInterceptor {
    companion object {
        const val TIMER_ID = "exposed.database.execution"
    }

    private val log = logger()
    private val startTimes = ConcurrentHashMap<Pair<String, String>, Long>()
    private val lastCleanupCheck = AtomicLong(Metrics.clock.monotonicTime())

    override fun beforeExecution(
        transaction: Transaction,
        context: StatementContext
    ) {
        val parameterizedSQL = context.statement.prepareSQL(transaction, true)
        val coord = transaction.id to parameterizedSQL
        startTimes[coord] = Metrics.clock.monotonicTime()

        checkForStaleEntries()
    }

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi
    ) {
        val endTime = Metrics.clock.monotonicTime()
        contexts.forEach { ctx ->
            val parameterizedSQL = ctx.statement.prepareSQL(transaction, true)
            val coord = transaction.id to parameterizedSQL
            startTimes.remove(coord)?.let { startTime ->
                val duration = endTime - startTime
                builder(TIMER_ID)
                    .publishPercentiles(0.5, 0.8, 0.9, 0.99)
                    .tag("sql", parameterizedSQL)
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS)
            }
        }
    }

    override fun afterRollback(transaction: Transaction) {
        cleanupTransaction(transaction.id)
    }

    override fun afterCommit(transaction: Transaction) {
        cleanupTransaction(transaction.id)
    }

    private fun cleanupTransaction(transactionId: String) {
        startTimes.keys.removeIf { (txId, _) -> txId == transactionId }
    }

    /**
     * defensive check to detect potential memory leaks if entries are not cleaned up properly after commit/rollback.
     * If there are entries older than 10 minutes, log an error. This should not happen under normal circumstances, but can help identify issues during development or in production if something goes wrong.
     * If we see this log, we should investigate why entries are not being cleaned up and fix the underlying issue to prevent memory leaks.
     */
    private fun checkForStaleEntries() {
        val now = Metrics.clock.monotonicTime()
        val lastCheck = lastCleanupCheck.load()

        // Only check every 5 minutes to avoid excessive overhead
        if (now - lastCheck < TimeUnit.MINUTES.toNanos(5)) {
            return
        }


        lastCleanupCheck.store(now)

        val threshold = now - TimeUnit.MINUTES.toNanos(10)
        val staleEntries = startTimes.filterValues { it < threshold }

        if (staleEntries.isNotEmpty()) {
            log.error(
                "Potential memory leak detected in MetricsStatementInterceptor: ${staleEntries.size} statement(s) older than 10 minutes."
            )
        }
    }
}