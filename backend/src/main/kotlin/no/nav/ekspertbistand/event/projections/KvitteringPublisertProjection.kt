package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.KvitteringPublisert
import no.nav.ekspertbistand.event.KvitteringPublisert.Type
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Midlertidig backfill av [KvitteringPublisert] for hendelser fra før tabellen fantes.
 *
 * Handlerne som publiserer [EventData.ArbeidsgiverVarslet] og [EventData.ArenaNotatJournalfoert], registrerer selv
 * kvitteringen i samme transaksjon som publiseringen. Uten backfill ville en gammel søknad få en ny kvittering hvis
 * samme utløsende hendelse kom på nytt. Projeksjonen setter inn de samme nøklene for hendelser som allerede er
 * `COMPLETED` i `event_log`, med hendelsens tidspunkt som `registrert`. Den publiserer ingen hendelser.
 *
 * | Hendelse | Kvittering |
 * |---|---|
 * | [EventData.SakOpprettet] | `ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT` |
 * | [EventData.TiltaksgjennomforingOpprettet] | `ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT` og `ARENA_NOTAT_JOURNALFOERT` |
 * | [EventData.TilsagnsdataLagret] | `ARBEIDSGIVER_VARSLET_SOKNAD_GODKJENT` (per tilsagn) |
 * | [EventData.SoknadAvlystIArena] | `ARBEIDSGIVER_VARSLET_SOKNAD_AVLYST` |
 *
 * Idempotent: eksisterende rader beholdes. Slettes når lag er 0 og kontrollspørringen i
 * `event/projections/README.md` gir 0 rader.
 */
@OptIn(ExperimentalTime::class)
class KvitteringPublisertProjection(
    database: Database,
) : EventLogProjectionBuilder(database) {
    override val name = "KvitteringPublisert-v1"

    override fun handle(event: Event<out EventData>, eventTimestamp: Instant) {
        when (val data = event.data) {
            is EventData.SakOpprettet -> data.soknad.id?.let { soknadId ->
                registrer(KvitteringPublisert.soknadMottattNokkel(soknadId), soknadId, MOTTATT, eventTimestamp)
            }

            is EventData.TiltaksgjennomforingOpprettet -> data.soknad.id?.let { soknadId ->
                registrer(KvitteringPublisert.soknadMottattNokkel(soknadId), soknadId, MOTTATT, eventTimestamp)
                registrer(
                    KvitteringPublisert.arenaNotatNokkel(data.saksnummer, data.tiltaksgjennomfoeringId),
                    soknadId,
                    Type.ARENA_NOTAT_JOURNALFOERT,
                    eventTimestamp,
                )
            }

            is EventData.TilsagnsdataLagret -> data.soknad.id?.let { soknadId ->
                registrer(
                    KvitteringPublisert.soknadGodkjentNokkel(soknadId, data.tilsagnData.tilsagnNummer),
                    soknadId,
                    Type.ARBEIDSGIVER_VARSLET_SOKNAD_GODKJENT,
                    eventTimestamp,
                )
            }

            is EventData.SoknadAvlystIArena -> data.soknad.id?.let { soknadId ->
                registrer(
                    KvitteringPublisert.soknadAvlystNokkel(soknadId),
                    soknadId,
                    Type.ARBEIDSGIVER_VARSLET_SOKNAD_AVLYST,
                    eventTimestamp,
                )
            }

            else -> Unit
        }
    }

    private fun registrer(nokkel: String, soknadId: String, type: Type, tidspunkt: Instant) {
        KvitteringPublisert.registrer(nokkel = nokkel, soknadId = soknadId, type = type, registrert = tidspunkt)
    }

    private companion object {
        val MOTTATT = Type.ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT
    }
}
