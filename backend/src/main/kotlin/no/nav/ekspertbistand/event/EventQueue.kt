package no.nav.ekspertbistand.event

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


object QueuedEvents : Table("event_queue") {
    val id = long("id").autoIncrement()
    val event = json<Event>("event_json", Json)
    val status = enumeration<ProcessingStatus>("status").default(ProcessingStatus.PENDING)
    val attempts = integer("attempts").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    override val primaryKey = PrimaryKey(id)

    // TODO: composite index on (status, updated_at, id)
}

data class QueuedEvent(
    val id: Long,
    val event: Event,
    val status: ProcessingStatus,
    val attempts: Int,
    val createdAt: kotlinx.datetime.LocalDateTime,
    val updatedAt: kotlinx.datetime.LocalDateTime
) {
    companion object {
        fun ResultRow.tilQueuedEvent() = QueuedEvent(
            id = this[QueuedEvents.id],
            event = this[QueuedEvents.event],
            status = this[QueuedEvents.status],
            attempts = this[QueuedEvents.attempts],
            createdAt = this[QueuedEvents.createdAt],
            updatedAt = this[QueuedEvents.updatedAt]
        )
    }
}

object EventLog : Table("event_log") {
    val id = long("id")
    val event = json<Event>("event_json", Json)
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
object EventQueue {
    val abandonedTimeout = 1.minutes

    fun publish(ev: Event) = transaction {
        QueuedEvents.insertReturning {
            it[event] = ev
        }.first().tilQueuedEvent()
    }


    @OptIn(ExperimentalTime::class)
    fun poll(clock: Clock = Clock.System): QueuedEvent? {
        return transaction {
            val row = QueuedEvents
                .selectAll()
                .where {
                    QueuedEvents.status eq ProcessingStatus.PENDING
                }
                .orWhere {
                    QueuedEvents.status eq ProcessingStatus.PROCESSING and QueuedEvents.updatedAt.less(
                        clock.now().minus(abandonedTimeout).toLocalDateTime(TimeZone.currentSystemDefault())
                    )
                }
                .orderBy(QueuedEvents.id, SortOrder.ASC)
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
                QueuedEvents.update({ QueuedEvents.id eq r[QueuedEvents.id] }) { up ->
                    up[status] = ProcessingStatus.PROCESSING
                    up[updatedAt] = CurrentDateTime
                    up[attempts] = r[attempts] + 1
                }
                r.tilQueuedEvent()
            }
        }
    }

    // TODO: add failure reason to EventLog instead of just success/failure
    fun finalize(id: Long, errorResults: List<EventHandeledResult.Error> = emptyList()) = transaction {
        val event = QueuedEvents
            .selectAll()
            .where {
                QueuedEvents.id eq id
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
            it[EventLog.id] = event[QueuedEvents.id]
            it[this.event] = event[QueuedEvents.event]
            if (errorResults.isEmpty()) {
                it[status] = ProcessingStatus.COMPLETED
            } else {
                it[status] = ProcessingStatus.COMPLETED_WITH_ERRORS
                it[errors] = errorResults
            }
            it[attempts] = event[QueuedEvents.attempts]
            it[createdAt] = event[QueuedEvents.createdAt]
            it[updatedAt] = CurrentDateTime
        }

        QueuedEvents.deleteWhere { QueuedEvents.id eq id }
    }
}
