package no.nav.ekspertbistand.event

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.skjema.DummyFooHandler
import kotlin.time.ExperimentalTime


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
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here

        dependencies.resolve<DummyFooHandler>()

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