package no.nav.ekspertbistand.event

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


/**
 * EventQueue is a simple event queue and log implementation using Exposed.
 *
 * Responsibilities:
 * - publish: Add new events to the queue.
 * - poll: Fetch the next pending event for processing (non-blocking, returns null if none).
 * - finalize: Mark an event as completed or failed, move it to the event log, and remove from the queue.
 *
 * Event lifecycle:
 * 1. publish(ev) / publishInTx(ev, tx) -> Event is stored in the queue (Events table).
 * 2. poll() -> Fetches the next PENDING (or abandoned) event and marks it PROCESSING.
 * 3. finalize(id, success) -> Moves event to EventLog table as COMPLETED or FAILED, deletes from queue.
 *
 * Concurrency:
 * - poll uses SKIP LOCKED to avoid race conditions when multiple workers fetch events.
 * - finalize is idempotent: if event is already moved to log, does nothing.
 *
 * Usage:
 *   // Uten egen transaksjon (typisk fra route-handler):
 *   EventQueue.publish(event)
 *   // Atomisk med kallerens arbeid:
 *   transaction { EventQueue.publishInTx(event, this) }
 *   val event = EventQueue.poll()
 *   EventQueue.finalize(event.id, success)
 *
 * Publisering skjer via [EventQueue.publish] eller [EventQueue.publishInTx] — dette er de eneste
 * veiene inn i køen. Skriv aldri til [QueuedEvents] direkte.
 */
object EventQueue {
    val abandonedTimeout = 1.minutes

    /**
     * Publiserer eventet i en NY transaksjon.
     *
     * Bruk denne når kalleren ikke selv har en transaksjon — typisk fra route-handlere som ikke
     * skriver noe annet. Skal publiseringen være atomisk med annet arbeid, er det en feil å bruke
     * denne: bruk [publishInTx].
     */
    fun publish(ev: EventData): QueuedEvent {
        require(TransactionManager.currentOrNull() == null) {
            "EventQueue.publish() åpner egen transaksjon, men ble kalt inne i en pågående transaksjon. " +
                "Bruk EventQueue.publishInTx(ev) hvis publiseringen skal være atomisk med kallerens arbeid."
        }
        return transaction { insertEvent(ev) }
    }

    /**
     * Publiserer eventet i kallerens pågående transaksjon — commiter og rulles tilbake med den.
     */
    fun publishInTx(ev: EventData): QueuedEvent {
        require(TransactionManager.currentOrNull() != null) {
            "publishInTx() må kalles inne i en pågående transaksjon."
        }
        return insertEvent(ev)
    }

    private fun insertEvent(ev: EventData): QueuedEvent =
        QueuedEvents.insertReturning {
            it[eventData] = ev
        }.first().tilQueuedEvent()


    /**
     * Polls the next pending event for processing.
     * Marks the event as PROCESSING and increments attempt count.
     * Uses SKIP LOCKED to avoid contention with other pollers.
     */
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
                        clock.now().minus(abandonedTimeout)
                    )
                }
                .orderBy(QueuedEvents.id, SortOrder.ASC)
                .limit(1)
                .forUpdate(ForUpdate(SKIP_LOCKED))
                .firstOrNull()


            row?.let { r ->
                QueuedEvents.update({ QueuedEvents.id eq r[QueuedEvents.id] }) { up ->
                    up[status] = ProcessingStatus.PROCESSING
                    up[updatedAt] = CurrentTimestamp
                    up[attempts] = r[attempts] + 1
                }
                r.tilQueuedEvent()
            }
        }
    }

    /**
     * Finalizes processing of an event.
     * Moves the event from the queue to the event log with the given status.
     * If the event is not found in the queue, it checks if it's already in the log (idempotent).
     */
    @OptIn(ExperimentalTime::class)
    fun finalize(
        id: Long,
        errorResults: List<EventHandledResult.UnrecoverableError> = emptyList()
    ) = transaction {
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
            it[eventData] = event[QueuedEvents.eventData]
            if (errorResults.isEmpty()) {
                it[status] = ProcessingStatus.COMPLETED
            } else {
                it[status] = ProcessingStatus.COMPLETED_WITH_ERRORS
                it[errors] = errorResults
            }
            it[attempts] = event[QueuedEvents.attempts]
            it[createdAt] = event[QueuedEvents.createdAt]
            it[updatedAt] = CurrentTimestamp
        }

        QueuedEvents.deleteWhere { QueuedEvents.id eq id }
    }
}



/**
 * Underliggende tabell for [EventQueue].
 *
 * Skriv aldri til denne tabellen direkte — bruk [EventQueue.publish] eller [EventQueue.publishInTx].
 * Tabellen er offentlig fordi lesere som `AppMetrics` og `EventManager.cleanupFinalizedEvents` trenger
 * tilgang, men enhver innsetting skal gå gjennom publiseringsmetodene slik at køens invarianter
 * håndheves ett sted.
 */
@OptIn(ExperimentalTime::class)
object QueuedEvents : Table("event_queue") {
    val id = long("id").autoIncrement()
    val eventData = json<EventData>("event_json", Json)
    val status = enumeration<ProcessingStatus>("status").default(ProcessingStatus.PENDING)
    val attempts = integer("attempts").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("statusOppdatert", isUnique = false, status, updatedAt, id)
    }
}

@OptIn(ExperimentalTime::class)
data class QueuedEvent(
    val id: Long,
    val eventData: EventData,
    val status: ProcessingStatus,
    val attempts: Int,
    val createdAt: kotlin.time.Instant,
    val updatedAt: kotlin.time.Instant
) {
    val event
        get() = Event(
            id = id,
            data = eventData
        )

    companion object {
        fun ResultRow.tilQueuedEvent() = QueuedEvent(
            id = this[QueuedEvents.id],
            eventData = this[QueuedEvents.eventData],
            status = this[QueuedEvents.status],
            attempts = this[QueuedEvents.attempts],
            createdAt = this[QueuedEvents.createdAt],
            updatedAt = this[QueuedEvents.updatedAt]
        )
    }
}

enum class ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    COMPLETED_WITH_ERRORS
}