package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.OpprettEkspertbistand
import no.nav.ekspertbistand.arena.insertArenaSak
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class OpprettTiltaksgjennomfoeringForInnsendtSkjema(
    private val arenaClient: ArenaClient,
    private val database: Database,
) : EventHandler<EventData.InnsendtSkjemaJournalfoert> {
    override val id = "Opprett Tiltaksgjennomfoering For Innsendt Skjema"
    override val eventType = EventData.InnsendtSkjemaJournalfoert::class

    override suspend fun handle(event: Event<EventData.InnsendtSkjemaJournalfoert>): EventHandledResult {
        val skjema = event.data.skjema
        val opprettetResponse = try {
            arenaClient.opprettTiltaksgjennomfoering(
                OpprettEkspertbistand(
                    behandlendeEnhetId = event.data.behandlendeEnhetId,
                    virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                    ansattFnr = skjema.ansatt.fnr,
                    periodeFom = skjema.behovForBistand.startdato,
                    journalpostId = event.data.journaldpostId,
                    dokumentId = event.data.dokumentId
                )
            )
        } catch (e: Exception) {
            return transientError("Feil ved oppretting av sak i Arena", e)
        }

        transaction(database) {
            val tiltaksgjennomfoeringId = opprettetResponse.tiltaksgjennomfoeringId
            val saksnummer = opprettetResponse.saksnummer
            insertArenaSak(
                saksnummer,
                tiltaksgjennomfoeringId,
                skjema
            )
            QueuedEvents.insert {
                it[eventData] = EventData.TiltaksgjennomføringOpprettet(
                    skjema,
                    saksnummer,
                    tiltaksgjennomfoeringId,
                )
            }
        }
        return EventHandledResult.Success()
    }
}
