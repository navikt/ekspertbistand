package no.nav.ekspertbistand.event

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.skjema.DummyBarHandler
import no.nav.ekspertbistand.skjema.DummyFooHandler
import kotlin.time.ExperimentalTime
import no.nav.ekspertbistand.skjema.DTO


data class Event<T: EventData>(
    val id: Long,
    val data: T
)

@Serializable
sealed interface EventData {

    @Serializable
    @SerialName("foo")
    data class Foo(
        val fooName: String
    ) : EventData

    @Serializable
    @SerialName("bar")
    data class Bar(
        val barName: String
    ) : EventData

    @Serializable
    @SerialName("skjemaInnsendt")
    data class SkjemaInnsendt(
        val skjema: DTO.Skjema
    ) : EventData
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here

        register(dependencies.resolve<DummyFooHandler>())
        register(dependencies.resolve<DummyBarHandler>())

        register<EventData>("InlineAllEventsHandler") { event ->
            // Inline handler example
            log.debug("event handled: {}", event)
            EventHandledResult.Success()
        }

    }



    // Start event processing loop
    launch {
        eventManager.runProcessLoop()
    }

    val metrics = EventMetrics(
        dispatcher = eventManager.config.dispatcher
    )
    // Start metrics processing loop
    launch {
        metrics.updateGaugesProcessingLoop()
    }
}