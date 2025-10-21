package no.nav.ekspertbistand.event

import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import no.nav.ekspertbistand.infrastruktur.logger

class EventManager(
    val config: EventManagerConfig = EventManagerConfig(),
    val q: EventQueue = EventQueue(),
    val eventHandlers: List<EventHandler<Event>> = emptyList()
) {
    private val log = logger()

    suspend fun consume() = withContext(Dispatchers.IO) {
        while (isActive) {
            val ev = q.poll() ?: run {
                kotlinx.coroutines.delay(config.pollDelay)
                continue
            }

            try {
                val results = eventHandlers.filter {
                    it.canHandle(ev)
                }.map {
                    it.handle(ev)
                    // TODO: make manager stateful so we can have retries per handler
                }

                val fail = results.filterIsInstance<EventHandeledResult.Error>()
                val retryableError = results.filterIsInstance<EventHandeledResult.RetryableError>()

                if ((fail + retryableError).isEmpty()) {
                    // all successful
                    q.finalize(ev.id)

                } else if (fail.isNotEmpty()) {
                    // at least one terminal error

                    log.error(
                        "Event ${ev.id} handling failed ${fail.joinToString(", ") { it.exception.message ?: "" }}",
                        fail.first().exception
                    )
                    q.finalize(ev.id, fail)

                } else if (retryableError.isNotEmpty()) {
                    // at least one retry but no terminal errors. will be retried after abandoned timeout

                    log.warn("Event ${ev.id} will be retried due to exception. ${retryableError.map { it.exception.message }}")

                }
            } catch (e: CancellationException) {
                throw e
            }
        }
    }
}

data class EventManagerConfig(
    val pollDelay: Long = 100
)

interface EventHandler<T : Event> {
    fun handle(event: T) : EventHandeledResult
}

sealed class EventHandeledResult {
    class Success : EventHandeledResult()
    class Error(val exception: Exception) : EventHandeledResult()
    class RetryableError(val exception: Exception) : EventHandeledResult()
}

inline fun <reified T : Event> EventHandler<T>.canHandle(event: Event) = event is T
