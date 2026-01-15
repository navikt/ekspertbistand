package no.nav.ekspertbistand.event

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandlerState.Companion.tilEventHandlerState
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
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
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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
@OptIn(ExperimentalTime::class)
class EventManager internal constructor(
    val config: EventManagerConfig = EventManagerConfig(),
    internal val eventHandlers: List<EventHandler<out EventData>>
) {
    private val log = logger()
    private val q = EventQueue

    /**
     * Starts the main event processing loop.
     * Continuously polls for new events, routes them to handlers, and finalizes them based on handler results.
     * This function is designed to be run within a coroutine.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun runProcessLoop() = withContext(config.dispatcher) {
        while (isActiveAndNotTerminating) {

            val queuedEvent = q.poll(config.clock) ?: run {
                delay(config.pollDelayMs)
                continue
            }

            log.info("Processing event ${queuedEvent.id} of type ${queuedEvent.eventData::class.simpleName}")

            val results = routeToHandlers(
                queuedEvent.id,
                queuedEvent.event,
                handledEvents(queuedEvent.id)
            )
            val succeeded = results.filterIsInstance<EventHandledResult.Success>()
            val unrecoverableErrors = results.filterIsInstance<EventHandledResult.UnrecoverableError>()
            val transientErrors = results.filterIsInstance<EventHandledResult.TransientError>()

            if (results == succeeded) { // all succeeded
                log.info("Event ${queuedEvent.id} handled successfully by all handlers.")
                q.finalize(queuedEvent.id)

            } else if (unrecoverableErrors.isNotEmpty()) {
                unrecoverableErrors.forEach {
                    log.error("Event ${queuedEvent.id} handling failed with urecoverable error: $it")
                }
                q.finalize(queuedEvent.id, unrecoverableErrors)

            } else if (transientErrors.isNotEmpty()) {
                transientErrors.forEach {
                    log.error("Event ${queuedEvent.id} will be retried due to transient error: $it")
                }
                // skip finalize to allow retry via abandoned timeout
            }

            delay(config.pollDelayMs)
        }
    }


    /**
     * Handles the given event with all applicable handlers, using the provided previous handler states to determine
     * whether to process or skip each handler.
     * Persists the result of each handler after processing.
     */
    private suspend inline fun <T : EventData> routeToHandlers(
        queuedEventId: Long,
        event: Event<T>,
        statePerHandler: Map<String, EventHandlerState>
    ): List<EventHandledResult> {

        val eventType = event.data::class

        /**
         * we can not use filterIsInstance here due to type erasure
         * therefore we must check the eventType explicitly at runtime, and cast afterward
         */
        @Suppress("UNCHECKED_CAST")
        val handlers = eventHandlers.filter {
            it.eventType == eventType
        } as List<EventHandler<T>>

        return if (handlers.isEmpty()) {
            listOf(transientError("No handlers registered for event type ${eventType.simpleName}"))
        } else {
            handlers
                .map { handler ->
                    val previousState = statePerHandler[handler.id]
                    when (previousState?.result) {
                        // skip if previously handled resulting in Success or UnrecoverableError
                        is EventHandledResult.Success,
                        is EventHandledResult.UnrecoverableError,
                            -> previousState.result


                        null, // process now if previously unhandled,
                        is EventHandledResult.TransientError, // process now if previously handled resulting in TransientError
                            -> handler.handle(event).also { result ->
                            upsertHandlerResult(queuedEventId, result, handler.id)
                        }
                    }
                }
        }
    }

    /**
     * Cleans up finalized event handler states periodically.
     * Finalized states are those for events that have been removed from the queue (and moved to the log).
     */
    suspend fun cleanupFinalizedEvents() = withContext(config.dispatcher) {
        while (isActiveAndNotTerminating) {
            log.info("Cleaning up finalized events...")

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
suspend fun EventManager(
    config: EventManagerConfig = EventManagerConfig(),
    build: suspend EventManagerBuilder.() -> Unit = {}
): EventManager {
    return EventManager(
        config,
        EventManagerBuilder().apply {
            build()
        }.handlers
    )
}

@OptIn(ExperimentalTime::class)
data class EventManagerConfig(
    val pollDelayMs: Long = 100,
    val cleanupDelayMs: Long = 60_000,
    val clock: Clock = Clock.System,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
)


interface EventHandler<T : EventData> {
    val id: String
    val eventType: KClass<T>
    suspend fun handle(event: Event<T>): EventHandledResult
}

@Serializable
sealed class EventHandledResult {
    @Serializable
    class Success : EventHandledResult()

    /**
     * Indicates a temporary failure; the event is eligible for retry.
     */
    @Serializable
    data class TransientError(val source: String, val message: String, val details: ErrorDetails? = null) :
        EventHandledResult()

    /**
     * Indicates a permanent failure; the event will not be retried.
     * Use this to skip further processing of an event.
     */
    @Serializable
    data class UnrecoverableError(val source: String, val message: String, val details: ErrorDetails? = null) :
        EventHandledResult()

    @Serializable
    data class ErrorDetails(
        val message: String?,
        val stackTrace: String?,
    )

    companion object {
        inline fun <reified T> T.transientError(message: String, throwable: Throwable? = null) =
            TransientError(
                source = T::class.qualifiedName ?: "Unknown",
                message = message,
                details = throwable?.toErrorDetails()
            )

        inline fun <reified T> T.unrecoverableError(message: String, throwable: Throwable? = null) =
            UnrecoverableError(
                source = T::class.qualifiedName ?: "Unknown",
                message = message,
                details = throwable?.toErrorDetails()
            )

        fun success() = Success()

        fun Throwable.toErrorDetails() = ErrorDetails(
            message = message,
            stackTrace = stackTrace.toString()
        )
    }
}

/**
 * Builder for registering event handlers with the EventManager.
 * Provides a DSL for defining handlers inline or registering existing instances.
 * Usage:
 *  EventManager {
 *    register<Event.Foo>("FooHandler") { event -> ... }
 *    register(ExistingFooHandlerInstance)
 *  }
 */
class EventManagerBuilder {
    val handlers = mutableListOf<EventHandler<out EventData>>()

    inline fun <reified T : EventData> register(id: String, noinline block: (Event<T>) -> EventHandledResult) {
        val handler = createBlockHandler(id, block)
        addHandler(handler)
    }

    fun <T : EventData> register(instance: EventHandler<T>) {
        addHandler(instance)
    }

    fun addHandler(handler: EventHandler<out EventData>) {
        require(handlers.none { it.id == handler.id }) {
            "Handler with id '${handler.id}' is already registered"
        }

        handlers += handler
    }
}

inline fun <reified T : EventData> createBlockHandler(
    id: String,
    noinline block: (Event<T>) -> EventHandledResult
): EventHandler<T> = object : EventHandler<T> {
    override val id: String = id
    override val eventType: KClass<T> = T::class
    override suspend fun handle(event: Event<T>) = block(event)
}

object EventHandlerStates : Table("event_handler_states") {
    val eventId = long("id")
    val handlerId = text("handler_id")
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