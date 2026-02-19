package no.nav.ekspertbistand.event

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.handlers.*
import no.nav.ekspertbistand.soknad.DTO
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
    @SerialName("soknadInnsendt")
    data class SoknadInnsendt(
        val soknad: DTO.Soknad
    ) : EventData

    @Serializable
    @SerialName("innsendtSoknadJournalfoert")
    data class InnsendtSoknadJournalfoert(
        val soknad: DTO.Soknad,
        val dokumentId: Int,
        val journaldpostId: Int,
        val behandlendeEnhetId: String,
    ) : EventData

    @Serializable
    @SerialName("tiltaksgjennomforingOpprettet")
    data class TiltaksgjennomforingOpprettet(
        val soknad: DTO.Soknad,
        val saksnummer: Saksnummer,
        val tiltaksgjennomfoeringId: Int
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevMottatt")
    data class TilskuddsbrevMottatt(
        val soknad: DTO.Soknad,
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
        val soknad: DTO.Soknad,
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
        val soknad: DTO.Soknad,
        val tiltaksgjennomforingEndret: TiltaksgjennomforingEndret
    ) : EventData

    @Serializable
    @SerialName("TilsagnsdataLagret")
    data class TilsagnsdataLagret(
        val soknad: DTO.Soknad,
        val tilsagnData: TilsagnData,
    ) : EventData

    @Serializable
    @SerialName("tilskuddsbrevVist")
    data class TilskuddsbrevVist(
        val tilsagnNummer: String,
        val soknad: DTO.Soknad?
    ) : EventData
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here
        register(dependencies.create(JournalfoerInnsendtSoknad::class))
        register(dependencies.create(OpprettTiltaksgjennomfoeringForInnsendtSoknad::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadMottatt::class))
        register(dependencies.create(JournalfoerTilskuddsbrev::class))
        register(dependencies.create(JournalfoerTilskuddsbrevKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjent::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjentKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadAvlyst::class))
        register(dependencies.create(SettGodkjentSoknadStatus::class))
        register(dependencies.create(SettAvlystSoknadStatus::class))
        register(dependencies.create(LagreTilsagnsData::class))
        register(dependencies.create(LagreTilsagnsDataKildeAltinn::class))
        register<EventData.TilskuddsbrevVist>("TilskuddsbrevVistNoop") { event ->
            // TilskuddsbrevVist brukes kun i projection builder for bruksmetrikk per nå
            EventHandledResult.Success()
        }

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
}