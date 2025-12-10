package no.nav.ekspertbistand.event

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.notifikasjon.OpprettNySakEventHandler
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.DummyBarHandler
import no.nav.ekspertbistand.skjema.DummyFooHandler
import kotlin.time.ExperimentalTime


data class Event<T : EventData>(
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

    @Serializable
    @SerialName("journalpostOpprettet")
    data class JournalpostOpprettet(
        val skjema: DTO.Skjema,
        val dokumentId: Int,
        val journaldpostId: Int,
        val behandlendeEnhetId: String,
    ) : EventData

    @Serializable
    @SerialName("tiltaksgjennomføringOpprettet")
    data class TiltaksgjennomføringOpprettet(
        val skjema: DTO.Skjema,
        val saksnummer: Saksnummer
    ) : EventData
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here
        register(DummyFooHandler())
        register(DummyBarHandler())
        register(
            OpprettNySakEventHandler(
                dependencies.resolve<ProdusentApiKlient>(),
                dependencies.resolve<IdempotencyGuard>()
            )
        )

        register<EventData>("InlineAlEventsHandler") { event ->
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