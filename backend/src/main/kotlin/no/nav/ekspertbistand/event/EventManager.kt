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
import kotlin.reflect.KClass
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
            val id = queuedEvent.id
            val ev = queuedEvent.event
            try {
                val statePerHandler = handledEvents(id)

                // TODO: use explicit when instead of filter
                val handlers = eventHandlers.filter {
                    it.canHandle(ev)
                }
                log.info("Found ${handlers.size} handlers for event ${ev::class.simpleName}: ${handlers.map { it.name }}")
                val results: List<EventHandledResult> = handlers.map { handler ->
                    val previousState = statePerHandler[handler.name]
                    when (previousState?.result) {
                        // if previously handled resulting in FatalError or Success, skip
                        is EventHandledResult.Success,
                        is EventHandledResult.FatalError -> {
                            previousState.result // null == skip this handler
                        }

                        // if previously handled resulting in RetryableError, process now
                        is EventHandledResult.TransientError,
                            // if previously unhandled, process now
                        null -> {
                            handler.handleAny(ev).also { result ->
                                // TODO: store attempts, the whole queuedEvent?
                                upsertHandlerResult(id, result, handler.name)
                            }
                        }
                    }


                }
                val succeeded = results.filterIsInstance<EventHandledResult.Success>()
                val fatalErrors = results.filterIsInstance<EventHandledResult.FatalError>()
                val transientError = results.filterIsInstance<EventHandledResult.TransientError>()

                if (results == succeeded) { // all succeeded
                    log.info("Event $id handled successfully by all handlers.")
                    q.finalize(id)

                    // TODO: should we clean the EventHandlerStates when finalizing, if so we need a tx
                } else if (fatalErrors.isNotEmpty()) {
                    log.error(
                        "Event $id handling failed ${fatalErrors.joinToString(", ") { it.message }}",
                    )
                    q.finalize(id, fatalErrors)
                    // TODO: should we clean the EventHandlerStates when finalizing, if so we need a tx
                    // TODO: perhaps we clean EventHandlerStates in another job, e.g. delete from EventHandlerStates where in eventlog..

                } else if (transientError.isNotEmpty()) {
                    log.warn("Event $id will be retried due to exception. ${transientError.map { it.message }}")
                    // skip finalize to allow retry via abandoned timeout
                }

                delay(config.pollDelayMs)
            } catch (e: CancellationException) {
                throw e
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
        handlerName: String
    ) {
        transaction {
            EventHandlerStates.upsert {
                it[EventHandlerStates.eventId] = eventId
                it[EventHandlerStates.handlerName] = handlerName
                it[EventHandlerStates.result] = result
            }
        }
    }

    internal fun handledEvents(id: Long) = transaction {
        EventHandlerStates
            .selectAll()
            .where { EventHandlerStates.eventId eq id }
            .map { it.tilEventHandlerState() }
            .associateBy { it.handlerName }
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
    val eventType: KClass<T>

    // TODO: static id instead of name
    val name: String
        get() = this::class.simpleName ?: error("EventHandler is missing name")


    fun handle(event: T): EventHandledResult

    fun canHandle(event: Event): Boolean = eventType.isInstance(event)

    @Suppress("UNCHECKED_CAST")
    fun handleAny(event: Event): EventHandledResult {
        require(canHandle(event)) { "Handler $name cannot handle ${event::class.simpleName}" }
        return handle(event as T)
    }
}

abstract class BaseEventHandler<T : Event> : EventHandler<T> {
    // Automatically infer event type T from the subclass
    override val eventType: KClass<T> by lazy {
        @Suppress("UNCHECKED_CAST")
        this::class.supertypes
            .firstNotNullOfOrNull { type ->
                val arg = type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                if (Event::class.java.isAssignableFrom(arg!!.java)) arg as? KClass<T> else null
            } ?: error("Cannot infer event type for ${this::class.simpleName}")
    }

    final override fun canHandle(event: Event): Boolean = eventType.isInstance(event)

    final override fun handleAny(event: Event): EventHandledResult {
        require(canHandle(event)) { "Handler $name cannot handle event of type ${event::class.simpleName}" }

        @Suppress("UNCHECKED_CAST")
        return handle(event as T)
    }
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

    inline fun <reified T : Event> handle(name: String, noinline block: (T) -> EventHandledResult) {
        handlers += on(name, block)
    }

    fun <T : Event> handler(instance: EventHandler<T>) {
        handlers += instance
    }
}


inline fun <reified T : Event> on(
    name: String? = null,
    noinline block: (T) -> EventHandledResult
): EventHandler<T> = object : BaseEventHandler<T>() {
    // TODO: name must be required, otherwise refactor could break semantics. rename name to id
    override val name: String = name ?: this::class.simpleName ?: error("EventHandler is missing name")
    override val eventType: KClass<T> = T::class
    override fun handle(event: T) = block(event)
}

object EventHandlerStates : Table("event_handler_states") {
    val eventId = long("id")
    val handlerName = text("handler_name")
    val result = json<EventHandledResult>("result", Json)
    val errorMessage = text("error_message").nullable()

    override val primaryKey = PrimaryKey(eventId, handlerName)
}

data class EventHandlerState(
    val eventId: Long,
    val handlerName: String,
    val result: EventHandledResult,
    val errorMessage: String?
) {
    companion object {
        fun ResultRow.tilEventHandlerState(): EventHandlerState = EventHandlerState(
            eventId = this[EventHandlerStates.eventId],
            handlerName = this[EventHandlerStates.handlerName],
            result = this[EventHandlerStates.result],
            errorMessage = this[EventHandlerStates.errorMessage]
        )
    }
}