/**
 * EventManager is responsible for polling, processing, and finalizing events using registered event handlers.
 *
 * Main responsibilities:
 * - Polls for new events from the EventQueue at a configurable interval.
 * - Dispatches events to all applicable EventHandlers.
 * - Tracks handler results (Success, TransientError, FatalError) and persists them.
 * - Finalizes events when all handlers succeed or any handler returns a fatal error.
 * - Supports retrying events with transient errors until abandoned timeout is reached.
 * - Cleans up finalized event handler states periodically.
 *
 * Usage:
 * Instantiate with a configuration and register handlers via the builder lambda.
 * Call runProcessLoop() to start event processing, and cleanupFinalizedEvents() to periodically clean up state.
 */

package no.nav.ekspertbistand.event

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventHandlerState.Companion.tilEventHandlerState
import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class EventManager(
    val config: EventManagerConfig = EventManagerConfig(),
    builder: EventManagerBuilder.() -> Unit = {},
) {
    private val log = logger()
    private val q = EventQueue
    private val eventHandlers: List<EventHandler<out Event>>

    init {
        EventManagerBuilder().also {
            builder(it)
            eventHandlers = it.handlers
        }
    }

    @OptIn(ExperimentalTime::class)
    suspend fun runProcessLoop() = withContext(config.dispatcher) {
        while (isActive) {
            log.info("Polling for events...")

            val queuedEvent = q.poll(config.clock) ?: run {
                log.info("No events found, delaying for ${config.pollDelayMs}ms")
                delay(config.pollDelayMs)
                continue
            }

            log.info("Processing event ${queuedEvent.id} of type ${queuedEvent.event::class.simpleName}")
            try {
                val results = routeToHandlers(queuedEvent)
                val succeeded = results.filterIsInstance<EventHandledResult.Success>()
                val fatalErrors = results.filterIsInstance<EventHandledResult.FatalError>()
                val transientError = results.filterIsInstance<EventHandledResult.TransientError>()

                if (results == succeeded) { // all succeeded
                    log.info("Event ${queuedEvent.id} handled successfully by all handlers.")
                    q.finalize(queuedEvent.id)

                } else if (fatalErrors.isNotEmpty()) {
                    log.error(
                        "Event ${queuedEvent.id} handling failed ${fatalErrors.joinToString(", ") { it.message }}",
                    )
                    q.finalize(queuedEvent.id, fatalErrors)

                } else if (transientError.isNotEmpty()) {
                    log.warn("Event ${queuedEvent.id} will be retried due to exception. ${transientError.map { it.message }}")
                    // skip finalize to allow retry via abandoned timeout
                }

                delay(config.pollDelayMs)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun routeToHandlers(queued: QueuedEvent) =
        handledEvents(queued.id).let { statePerHandler ->

            // ROUTE EVENT
            when (val event = queued.event) {
                is Event.Foo -> eventHandlers.filterIsInstance<EventHandler<Event.Foo>>().map { handler ->
                    handleWithState(queued.id, statePerHandler[handler.id], event, handler)
                }

                is Event.Bar -> eventHandlers.filterIsInstance<EventHandler<Event.Bar>>().map { handler ->
                    handleWithState(queued.id, statePerHandler[handler.id], event, handler)
                }
            }
        }

    private fun <T : Event> handleWithState(
        eventId: Long,
        previousState: EventHandlerState?,
        event: T,
        handler: EventHandler<T>
    ): EventHandledResult {
        return when (previousState?.result) {
            // if previously handled resulting in FatalError or Success, skip
            is EventHandledResult.Success,
            is EventHandledResult.FatalError -> previousState.result


            null, // if previously unhandled, process now
            is EventHandledResult.TransientError -> { // if previously handled resulting in TransientError, process now
                handler.handle(event).also { result ->
                    upsertHandlerResult(eventId, result, handler.id)
                }
            }
        }
    }

    suspend fun cleanupFinalizedEvents() = withContext(config.dispatcher) {
        while (isActive) {
            log.info("Cleaning up finalized events...")
            try {
                val deletedRows = transaction {
                    EventHandlerStates.deleteWhere {
                        notExists(
                            QueuedEvents
                                .select(QueuedEvents.id)
                                .where { QueuedEvents.id eq EventHandlerStates.eventId }
                        )
                    }
                }
                log.info("Deleted $deletedRows finalized states. Delaying for ${config.cleanupDelayMs}ms")
                delay(config.cleanupDelayMs)
            } catch (e: CancellationException) {
                throw e
            }
        }
    }

    private fun upsertHandlerResult(
        eventId: Long,
        result: EventHandledResult,
        handlerId: String
    ) {
        transaction {
            EventHandlerStates.upsert {
                it[EventHandlerStates.eventId] = eventId
                it[EventHandlerStates.handlerId] = handlerId
                it[EventHandlerStates.result] = result
            }
        }
    }

    internal fun handledEvents(eventId: Long) = transaction {
        EventHandlerStates
            .selectAll()
            .where { EventHandlerStates.eventId eq eventId }
            .map { it.tilEventHandlerState() }
            .associateBy { it.handlerId }
    }
}

@OptIn(ExperimentalTime::class)
data class EventManagerConfig(
    val pollDelayMs: Long = 100,
    val cleanupDelayMs: Long = 60_000,
    val clock: Clock = Clock.System,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
)

interface EventHandler<T : Event> {
    val id: String

    fun handle(event: T): EventHandledResult

}

@Serializable
sealed class EventHandledResult {
    @Serializable
    class Success : EventHandledResult()

    @Serializable
    class TransientError(val message: String) : EventHandledResult()

    @Serializable
    class FatalError(val message: String) : EventHandledResult()
}

class EventManagerBuilder {
    val handlers = mutableListOf<EventHandler<out Event>>()

    inline fun <reified T : Event> handle(id: String, noinline block: (T) -> EventHandledResult) {
        handlers += on(id, block)
    }

    fun <T : Event> handler(instance: EventHandler<T>) {
        handlers += instance
    }
}


inline fun <reified T : Event> on(
    id: String,
    noinline block: (T) -> EventHandledResult
): EventHandler<T> = object : EventHandler<T> {
    override val id: String = id
    override fun handle(event: T) = block(event)
}

object EventHandlerStates : Table("event_handler_states") {
    val eventId = long("id")
    val handlerId = text("handler_name")
    val result = json<EventHandledResult>("result", Json)
    val errorMessage = text("error_message").nullable()

    override val primaryKey = PrimaryKey(eventId, handlerId)
}

data class EventHandlerState(
    val eventId: Long,
    val handlerId: String,
    val result: EventHandledResult,
    val errorMessage: String?
) {
    companion object {
        fun ResultRow.tilEventHandlerState(): EventHandlerState = EventHandlerState(
            eventId = this[EventHandlerStates.eventId],
            handlerId = this[EventHandlerStates.handlerId],
            result = this[EventHandlerStates.result],
            errorMessage = this[EventHandlerStates.errorMessage]
        )
    }
}