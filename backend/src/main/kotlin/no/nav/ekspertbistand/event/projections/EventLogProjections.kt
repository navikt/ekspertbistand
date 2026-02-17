package no.nav.ekspertbistand.event.projections

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.create
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.LoggedEvent.Companion.tilLoggedEvent
import no.nav.ekspertbistand.event.ProcessingStatus.COMPLETED
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Base class for building projections from the event log.
 * Implementations should override [handle] to process each event.
 *
 * The [poll] method fetches new events and applies the handler,
 * updating the projection state accordingly.
 *
 * Usage:
 * - Create a subclass implementing [handle].
 * - Register the projection builder in [configureProjectionBuilders].
 */
@OptIn(ExperimentalTime::class)
abstract class EventLogProjectionBuilder(
    val database: Database,
    val batchSize: Int = 100,
) {
    val log = logger()
    abstract val name: String

    /**
     * Handle a single event from the event log. This method is called for each event that has not yet been processed by this projection builder.
     * Projection builders are at-least-once, so the same event may be delivered multiple times in case of errors.
     * Implementations should ensure that handling is idempotent.
     */
    abstract fun handle(event: Event<out EventData>, eventTimestamp: Instant)

    fun poll() = transaction(database) {
        initializePositionIfNeeded()

        // Claim the projection row using FOR UPDATE SKIP LOCKED to ensure serial processing
        val currentPosition = getCurrentPositionWithLock() ?: return@transaction false

        val loggedEvents = EventLog
            .selectAll()
            .where { EventLog.id greater currentPosition }
            .andWhere { EventLog.status eq COMPLETED }
            .orderBy(EventLog.id)
            .limit(batchSize)
            .map { it.tilLoggedEvent() }

        loggedEvents.forEach { loggedEvent ->
            try {
                handle(loggedEvent.event, loggedEvent.createdAt)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                throw Exception("error handling event ${loggedEvent.id} in projection builder $name", e)
            }
        }

        loggedEvents.lastOrNull()?.let { lastEvent ->
            ProjectionBuilderState.update(
                where = { ProjectionBuilderState.builderName eq name },
            ) { stmnt ->
                stmnt[position] = lastEvent.id
            }
        }

        loggedEvents.isNotEmpty() // return true if we processed any events
    }

    /**
     * Attempts to acquire a lock on the projection state row for this builder.
     * If the row is locked by another transaction, returns null.
     * If return value is null it means another instance of this projection builder is currently processing
     */
    private fun getCurrentPositionWithLock(): Long? =
        ProjectionBuilderState
            .select(ProjectionBuilderState.position)
            .where { ProjectionBuilderState.builderName eq name }
            .forUpdate(ForUpdate(SKIP_LOCKED))
            .firstOrNull()?.get(ProjectionBuilderState.position)

    private fun initializePositionIfNeeded() {
        ProjectionBuilderState.insertIgnore {
            it[builderName] = name
            it[this.position] = 0
        }
    }
}

object ProjectionBuilderState : Table("projection_builder_state") {
    val builderName = text("builder_name")
    val position = long("position").default(0)

    override val primaryKey = PrimaryKey(builderName)

    fun lagPerBuilder(): Map<String, Long> = transaction {
        val maxPosition = EventLog.select(EventLog.id)
            .orderBy(EventLog.id, SortOrder.DESC)
            .limit(1)
            .firstOrNull()?.get(EventLog.id) ?: 0L

        ProjectionBuilderState
            .selectAll()
            .associate { it[builderName] to (maxPosition - it[position]) }
    }
}

suspend fun Application.configureProjectionBuilders() {
    val projectionBuilders = listOf(
        // register all projection builders here
        dependencies.create(TilskuddsbrevVistProjection::class),
        dependencies.create(SoknadBehandletForsinkelseProjection::class),
    )

    projectionBuilders.forEach { builder ->
        launch {
            while (true) {
                try {
                    val processed = builder.poll()
                    if (!processed) delay(10.seconds) // prevent busy loop
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    logger().error("error polling projection builder ${builder.name}", e)
                    delay(60.seconds) // wait before retrying on error
                }
            }
        }
    }

}