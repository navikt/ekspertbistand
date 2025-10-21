package no.nav.ekspertbistand.event

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone.Companion.UTC
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Duration.Companion.minutes


object Events : Table("events") {
    val id = long("id").autoIncrement()
    val event = json<Event>("event", Json)
    val status = enumeration<ProcessingStatus>("status").default(ProcessingStatus.PENDING)
    val attempts = integer("attempts").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)

    // TODO: composite index on (status, updated_at, id)
}

object EventLog : Table("event_log") {
    val id = long("id")
    val event = json<Event>("event", Json)
    val status = enumeration<ProcessingStatus>("status").default(ProcessingStatus.PENDING)
    val errors = json<List<EventHandeledResult.Error>>("errors", Json).default(emptyList())
    val attempts = integer("attempts").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)
}

enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS
}


/**
 * EventBus is a simple event queue and log implementation using Exposed.
 *
 * Responsibilities:
 * - publish: Add new events to the queue.
 * - poll: Fetch the next pending event for processing (non-blocking, returns null if none).
 * - finalize: Mark an event as completed or failed, move it to the event log, and remove from the queue.
 *
 * Event lifecycle:
 * 1. publish(Event) -> Event is stored in the queue (Events table).
 * 2. poll() -> Fetches the next PENDING event and marks it PROCESSING.
 * 3. finalize(id, success) -> Moves event to EventLog table as COMPLETED or FAILED, deletes from queue.
 *
 * Concurrency:
 * - poll uses SKIP LOCKED to avoid race conditions when multiple workers fetch events.
 * - finalize is idempotent: if event is already moved to log, does nothing.
 *
 * Usage:
 *   EventBus.publish(event)
 *   val event = EventBus.poll()
 *   EventBus.finalize(event.id, success)
 *
 * [EventQueue.publish] is used to add new events to the queue.
 */
class EventQueue {
    val abandonedTimeout = 1.minutes

    fun publish(ev: Event) = transaction {
        Events.insert {
            it[event] = ev
        }
    }

    fun poll(clock: Clock = Clock.System): Event? {
        return transaction {
            val row = Events
                .selectAll()
                .where {
                    Events.status eq ProcessingStatus.PENDING
                }
                .orWhere {
                    Events.status eq ProcessingStatus.PROCESSING and Events.updatedAt.less(
                        clock.now().minus(abandonedTimeout).toLocalDateTime(UTC)
                    )
                }
                .orderBy(Events.id, SortOrder.ASC)
                .limit(1)
                .forUpdate(ForUpdate(SKIP_LOCKED))
                .firstOrNull()


            row?.let { r ->
                /* TODO: consider if we want to fail events after too many attempts
                if (row[Events.attempts] >= 42) {
                    Events.update({ Events.id eq r[Events.id] }) {
                        it[status] = ProcessingStatus.FAILED
                    }
                    return@transaction null
                }
                 */
                Events.update({ Events.id eq r[Events.id] }) { up ->
                    up[status] = ProcessingStatus.PROCESSING
                    up[updatedAt] = CurrentDateTime
                    up[attempts] = r[attempts] + 1
                }
                r[Events.event]
            }
        }
    }

    // TODO: add failure reason to EventLog instead of just success/failure
    fun finalize(id: Long, errorResults: List<EventHandeledResult.Error> = emptyList()) = transaction {
        val event = Events
            .selectAll()
            .where {
                Events.id eq id
            }.firstOrNull()

        if (event == null) {
            // Already processed? Do nothing (idempotent)
            val alreadyMoved = EventLog
                .selectAll()
                .where {
                    EventLog.id eq id
                }.firstOrNull() != null
            if (alreadyMoved) {
                return@transaction
            } else {
                error("Event with id $id not found")
            }
        }

        EventLog.insert {
            it[EventLog.id] = event[Events.id]
            it[this.event] = event[Events.event]
            if (errorResults.isEmpty()) {
                it[status] = ProcessingStatus.COMPLETED
            } else {
                it[status] = ProcessingStatus.COMPLETED_WITH_ERRORS
                it[errors] = errorResults
            }
            it[attempts] = event[Events.attempts]
            it[createdAt] = event[Events.createdAt]
            it[updatedAt] = CurrentDateTime
        }

        Events.deleteWhere { Events.id eq id }
    }
}
