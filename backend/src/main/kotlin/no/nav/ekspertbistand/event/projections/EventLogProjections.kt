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
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.milliseconds
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
) {
    val log = logger()
    abstract val name: String

    abstract fun handle(event: Event<out EventData>, eventTimestamp: Instant)

    fun poll() = transaction(database) {
        val currentPosition = ProjectionBuilderState
            .select(ProjectionBuilderState.position)
            .where { ProjectionBuilderState.builderName eq id }
            .firstOrNull().let {
                if (it == null) {
                    // not registered yet, TODO: consider moving this to a setup step
                    ProjectionBuilderState.insertReturning(
                        returning = listOf(ProjectionBuilderState.position)
                    ) { stmnt ->
                        stmnt[builderName] = id
                        stmnt[this.position] = 0
                    }.first()[ProjectionBuilderState.position]
                } else {
                    it[ProjectionBuilderState.position]
                }
            }

        // fetch events from event log with id > position
        val loggedEvents = EventLog
            .selectAll()
            .where { EventLog.id greater currentPosition }
            .andWhere { EventLog.status eq COMPLETED }
            .orderBy(EventLog.id)
            .limit(1) // TODO: configurable batch size
            .map { it.tilLoggedEvent() }

        loggedEvents.forEach { loggedEvent ->
            try {
                handle(loggedEvent.event, loggedEvent.createdAt)

                ProjectionBuilderState.upsert(
                    where = { ProjectionBuilderState.builderName eq id },
                ) { stmnt ->
                    stmnt[builderName] = id
                    stmnt[position] = loggedEvent.id
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                throw Exception("error handling event ${loggedEvent.id} in projection builder $name", e)
            }
        }

        loggedEvents.isNotEmpty() // return true if we processed any events
    }

}

object ProjectionBuilderState : Table("projection_builder_state") {
    val builderName = text("builder_name")
    val position = long("position").default(0)

    override val primaryKey = PrimaryKey(builderName)
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
                    if (!processed) delay(100.milliseconds) // prevent busy loop
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    logger().error("error polling projection builder ${builder.name}", e)
                    delay(5.seconds) // wait before retrying on error
                }
            }
        }
    }

}