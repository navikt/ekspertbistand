package no.nav.ekspertbistand.event

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.handlers.OpprettTiltaksgjennomfoeringForInnsendtSkjema
import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrev
import no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrevKildeAltinn
import no.nav.ekspertbistand.event.handlers.LagreTilsagnsData
import no.nav.ekspertbistand.event.handlers.JournalfoerInnsendtSkjema
import no.nav.ekspertbistand.event.handlers.LagreTilsagnsDataKildeAltinn
import no.nav.ekspertbistand.event.handlers.SettAvlystSkjemaStatus
import no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadGodkjent
import no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadMottatt
import no.nav.ekspertbistand.event.handlers.SettGodkjentSkjemaStatus
import no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadAvlyst
import no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadGodkjentKildeAltinn
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.tilsagndata.TilskuddsbrevHtml
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
    @SerialName("innsendtSkjemaJournalfoert")
    data class InnsendtSkjemaJournalfoert(
        val skjema: DTO.Skjema,
        val dokumentId: Int,
        val journaldpostId: Int,
        val behandlendeEnhetId: String,
    ) : EventData

    @Serializable
    @SerialName("tiltaksgjennomføringOpprettet")
    data class TiltaksgjennomføringOpprettet(
        val skjema: DTO.Skjema,
        val saksnummer: Saksnummer,
        val tiltaksgjennomfoeringId: Int
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevMottatt")
    data class TilskuddsbrevMottatt(
        val skjema: DTO.Skjema,
        val tilsagnbrevId: Int,
        val tilsagnData: TilsagnData
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevMottattKildeAltinn")
    data class TilskuddsbrevMottattKildeAltinn(
        val tilsagnbrevId: Int,
        val tilsagnData: TilsagnData
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevJournalfoert")
    data class TilskuddsbrevJournalfoert(
        val skjema: DTO.Skjema,
        val dokumentId: Int,
        val journaldpostId: Int,
        val tilsagnData: TilsagnData
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevJournalfoertKildeAltinn")
    data class TilskuddsbrevJournalfoertKildeAltinn(
        val dokumentId: Int,
        val journaldpostId: Int,
        val tilsagnData: TilsagnData,
    ) : EventData

    @Serializable
    @SerialName("soknadAvlystIArena")
    data class SoknadAvlystIArena(
        val skjema: DTO.Skjema,
        val tiltaksgjennomforingEndret: TiltaksgjennomforingEndret
    ) : EventData

    @Serializable
    @SerialName("TilsagnsdataLagret")
    data class TilsagnsdataLagret(
        val skjema: DTO.Skjema,
        val tilsagnData: TilsagnData,
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevVist")
    data class TilskuddsbrevVist(
        val tilsagnNummer: String,
        val skjema: DTO.Skjema?
    ) : EventData
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here
        register(dependencies.create(JournalfoerInnsendtSkjema::class))
        register(dependencies.create(OpprettTiltaksgjennomfoeringForInnsendtSkjema::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadMottatt::class))
        register(dependencies.create(JournalfoerTilskuddsbrev::class))
        register(dependencies.create(JournalfoerTilskuddsbrevKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjent::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjentKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadAvlyst::class))
        register(dependencies.create(SettGodkjentSkjemaStatus::class))
        register(dependencies.create(SettAvlystSkjemaStatus::class))
        register(dependencies.create(LagreTilsagnsData::class))
        register(dependencies.create(LagreTilsagnsDataKildeAltinn::class))

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