package no.nav.ekspertbistand.event

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.arena.TiltakssakEndret
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret
import no.nav.ekspertbistand.event.handlers.*
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.aggregateRootId
import no.nav.ekspertbistand.tilsagndata.aggregateRootId
import kotlin.time.ExperimentalTime


data class Event<T : EventData>(
    val id: Long,
    val data: T
) {
    val aggregateRootId: String get() = data.aggregateRootId
}

@Serializable
sealed interface EventData {

    /**
     * Id til aggregatroten hendelsen tilhører.
     *
     * Deriveres fra payload — den lagres bevisst IKKE i event_json, kun i kolonnen
     * aggregate_root_id, slik at vi ikke får to sannheter i samme rad.
     */
    val aggregateRootId: String

    /**
     * Arbeidsgiver har sendt inn en søknad om ekspertbistand.
     *
     * Første hendelse i livsløpet til en søknad. Publiseres av søknads-API-et i samme
     * transaksjon som søknaden lagres og utkastet slettes.
     *
     * Payload er hele søknaden slik den ble sendt inn, og er dermed sannheten om hva
     * arbeidsgiver faktisk søkte om — senere endringer i søknadstabellen påvirker ikke eventen.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.JournalfoerInnsendtSoknad] og
     * [no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseProjection]
     * (starter måling av behandlingstid).
     */
    @Serializable
    @SerialName("soknadInnsendt")
    data class SoknadInnsendt(
        val soknad: DTO.Soknad
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Den innsendte søknaden er journalført i Dokarkiv.
     *
     * Publiseres av [no.nav.ekspertbistand.event.handlers.JournalfoerInnsendtSoknad] etter at
     * PDF er generert og journalpost opprettet. [behandlendeEnhetId] er enheten som ble utledet
     * fra søkers adressebeskyttelse og geografiske tilknytning, og bæres videre fordi Arena
     * trenger den ved opprettelse av sak.
     *
     * Konsument: [no.nav.ekspertbistand.event.handlers.OpprettTiltaksgjennomfoeringForInnsendtSoknad].
     */
    @Serializable
    @SerialName("innsendtSoknadJournalfoert")
    data class InnsendtSoknadJournalfoert(
        val soknad: DTO.Soknad,
        val dokumentId: Int,
        val journaldpostId: Int,
        val behandlendeEnhetId: String,
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Det er opprettet sak og tiltaksgjennomføring for søknaden i Arena.
     *
     * Publiseres av [no.nav.ekspertbistand.event.handlers.OpprettTiltaksgjennomfoeringForInnsendtSoknad]
     * i samme transaksjon som arena_sak lagres. [saksnummer] og [tiltaksgjennomfoeringId] er
     * nøklene vi senere kobler Arena-meldinger tilbake til søknaden med, og er derfor beviset
     * på at vi er kilde til saken.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.JournalfoerNotatArenaSakOpprettet] og
     * [no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadMottatt].
     */
    @Serializable
    @SerialName("tiltaksgjennomforingOpprettet")
    data class TiltaksgjennomforingOpprettet(
        val soknad: DTO.Soknad,
        val saksnummer: Saksnummer,
        val tiltaksgjennomfoeringId: Int
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Arena har sendt tilsagnsbrev for en sak vi er kilde til, altså er søknaden godkjent.
     *
     * Publiseres av [no.nav.ekspertbistand.arena.ArenaTilsagnsbrevProcessor] når
     * tilsagnsnummeret i kafka-meldingen treffer en rad i arena_sak. Publiseringen er
     * idempotent per tilsagnbrevId.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrev],
     * [no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseProjection]
     * (godkjenttidspunkt) og
     * [no.nav.ekspertbistand.event.projections.TilskuddsbrevVistProjection]
     * (oppretter rad for bruksmetrikk).
     *
     * Se [TilskuddsbrevMottattKildeAltinn] for samme melding når søknaden ikke er vår.
     */
    @Serializable
    @SerialName("tilskuddsbrevMottatt")
    data class TilskuddsbrevMottatt(
        val soknad: DTO.Soknad,
        val tilsagnbrevId: Int,
        val tilsagnData: TilsagnData
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Arena har sendt tilsagnsbrev for en sak vi *ikke* er kilde til.
     *
     * Samme kafka-melding som [TilskuddsbrevMottatt], men tilsagnsnummeret finnes ikke i
     * arena_sak. Da er søknaden sendt inn via den gamle Altinn 2-løsningen, noe som kan skje
     * i overgangsperioden. Vi har derfor ingen [DTO.Soknad] å henge hendelsen på, og
     * aggregatroten utledes fra tilsagnsdataene i stedet.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrevKildeAltinn] og
     * [no.nav.ekspertbistand.event.projections.TilskuddsbrevVistProjection].
     */
    @Serializable
    @SerialName("tilskuddsbrevMottattKildeAltinn")
    data class TilskuddsbrevMottattKildeAltinn(
        val tilsagnbrevId: Int,
        val tilsagnData: TilsagnData
    ) : EventData {
        override val aggregateRootId: String get() = tilsagnData.aggregateRootId
    }

    /**
     * Tilskuddsbrevet er journalført i Dokarkiv, for en søknad vi er kilde til.
     *
     * Publiseres av [no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrev].
     * [tilsagnData] bæres med videre slik at etterfølgende handlere kan lagre og vise
     * tilskuddsbrevet uten å gå tilbake til Arena.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.LagreTilsagnsData] og
     * [no.nav.ekspertbistand.event.handlers.SettGodkjentSoknadStatus].
     */
    @Serializable
    @SerialName("tilskuddsbrevJournalfoert")
    data class TilskuddsbrevJournalfoert(
        val soknad: DTO.Soknad,
        val dokumentId: Int,
        val journaldpostId: Int,
        val tilsagnData: TilsagnData
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Tilskuddsbrevet er journalført i Dokarkiv, for en søknad sendt inn via Altinn 2.
     *
     * Altinn 2-varianten av [TilskuddsbrevJournalfoert]. Publiseres av
     * [no.nav.ekspertbistand.event.handlers.JournalfoerTilskuddsbrevKildeAltinn]. Uten søknad
     * i vårt system er tilsagnsdataene alt vi har, og aggregatroten utledes derfra.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.LagreTilsagnsDataKildeAltinn] og
     * [no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadGodkjentKildeAltinn].
     */
    @Serializable
    @SerialName("tilskuddsbrevJournalfoertKildeAltinn")
    data class TilskuddsbrevJournalfoertKildeAltinn(
        val dokumentId: Int,
        val journaldpostId: Int,
        val tilsagnData: TilsagnData,
    ) : EventData {
        override val aggregateRootId: String get() = tilsagnData.aggregateRootId
    }

    /**
     * Tiltaksgjennomføringen er satt til AVLYST i Arena, altså er søknaden avslått eller trukket.
     *
     * Publiseres av [no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessor] når
     * en endringsmelding med status AVLYST treffer en tiltaksgjennomføring vi er kilde til.
     * Publiseringen er idempotent per tiltaksgjennomfoeringId. Avlysning av søknader sendt inn
     * via Altinn 2 håndteres foreløpig ikke.
     *
     * Konsumenter: [no.nav.ekspertbistand.event.handlers.SettAvlystSoknadStatus],
     * [no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadAvlyst] og
     * [no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseProjection]
     * (avlysttidspunkt).
     */
    @Serializable
    @SerialName("soknadAvlystIArena")
    data class SoknadAvlystIArena(
        val soknad: DTO.Soknad,
        val tiltaksgjennomforingEndret: TiltaksgjennomforingEndret
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * En saksbehandler har tatt tiltakssaken vår til behandling i Arena.
     *
     * Publiseres av [no.nav.ekspertbistand.arena.ArenaTiltakssakEndretProcessor] når en
     * oppdatering på tiltakssak-topicet er tatt av saksbehandler og saksnummeret finnes i
     * arena_sak. Publiseringen er idempotent per sakId. Merk at [tiltakssakEndret] inneholder
     * saksbehandlerident og derfor ikke skal logges utenfor teamLog.
     *
     * Konsument: [no.nav.ekspertbistand.event.handlers.MarkerSakUnderBehandlingIArena].
     */
    @Serializable
    @SerialName("saksbehandlingStartetIArena")
    data class SaksbehandlingStartetIArena(
        val soknad: DTO.Soknad,
        val tiltakssakEndret: TiltakssakEndret,
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Tilsagnsdataene er lagret i vår egen database, slik at tilskuddsbrevet kan vises til
     * arbeidsgiver.
     *
     * Publiseres av [no.nav.ekspertbistand.event.handlers.LagreTilsagnsData] i samme
     * transaksjon som lagringen. Dette er signalet om at tilskuddsbrevet faktisk er
     * tilgjengelig for arbeidsgiver, og er derfor det varselet om godkjent søknad henger på.
     *
     * Konsument: [no.nav.ekspertbistand.event.handlers.VarsleArbeidsgiverSoknadGodkjent].
     *
     * NB: serienavnet har stor forbokstav, i motsetning til de andre. Det er lagret slik i
     * event_json og kan ikke endres uten migrering.
     */
    @Serializable
    @SerialName("TilsagnsdataLagret")
    data class TilsagnsdataLagret(
        val soknad: DTO.Soknad,
        val tilsagnData: TilsagnData,
    ) : EventData {
        override val aggregateRootId: String get() = soknad.aggregateRootId
    }

    /**
     * Arbeidsgiver har hentet tilskuddsbrevet, som HTML eller PDF.
     *
     * Publiseres av [no.nav.ekspertbistand.tilsagndata.TilsagnDataApi] ved hvert oppslag, og
     * kan derfor forekomme mange ganger for samme tilsagn. Brukes per nå kun til bruksmetrikk:
     * [no.nav.ekspertbistand.event.projections.TilskuddsbrevVistProjection] registrerer første
     * visning, og eventen har ellers bare en no-op-handler.
     *
     * [soknad] er null når oppslaget er gjort på tilsagnsnummer alene, altså for søknader vi
     * ikke er kilde til. Aggregatroten faller da tilbake til [tilsagnNummer].
     */
    @Serializable
    @SerialName("tilskuddsbrevVist")
    data class TilskuddsbrevVist(
        val tilsagnNummer: String,
        val soknad: DTO.Soknad?
    ) : EventData {
        override val aggregateRootId: String get() = soknad?.aggregateRootId ?: tilsagnNummer
    }
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureEventHandlers() {
    val eventManager = EventManager {
        // Registrer all event handlers here
        register(dependencies.create(JournalfoerInnsendtSoknad::class))
        register(dependencies.create(OpprettTiltaksgjennomfoeringForInnsendtSoknad::class))
        register(dependencies.create(JournalfoerNotatArenaSakOpprettet::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadMottatt::class))
        register(dependencies.create(JournalfoerTilskuddsbrev::class))
        register(dependencies.create(JournalfoerTilskuddsbrevKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjent::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadGodkjentKildeAltinn::class))
        register(dependencies.create(VarsleArbeidsgiverSoknadAvlyst::class))
        register(dependencies.create(SettGodkjentSoknadStatus::class))
        register(dependencies.create(SettAvlystSoknadStatus::class))
        register(dependencies.create(MarkerSakUnderBehandlingIArena::class))
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