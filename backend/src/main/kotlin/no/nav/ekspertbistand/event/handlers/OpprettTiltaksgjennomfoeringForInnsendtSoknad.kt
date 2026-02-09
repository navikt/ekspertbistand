package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.OpprettEkspertbistand
import no.nav.ekspertbistand.arena.insertArenaSak
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class OpprettTiltaksgjennomfoeringForInnsendtSoknad(
    private val arenaClient: ArenaClient,
    private val database: Database,
) : EventHandler<EventData.InnsendtSoknadJournalfoert> {
    override val id = "Opprett Tiltaksgjennomfoering For Innsendt Soknad"
    override val eventType = EventData.InnsendtSoknadJournalfoert::class

    override suspend fun handle(event: Event<EventData.InnsendtSoknadJournalfoert>): EventHandledResult {
        val soknad = event.data.soknad
        val opprettetResponse = try {
            arenaClient.opprettTiltaksgjennomfoering(
                OpprettEkspertbistand(
                    behandlendeEnhetId = event.data.behandlendeEnhetId,
                    virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                    ansattFnr = soknad.ansatt.fnr,
                    periodeFom = soknad.behovForBistand.startdato,
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
                soknad
            )
            QueuedEvents.insert {
                it[eventData] = EventData.TiltaksgjennomforingOpprettet(
                    soknad,
                    saksnummer,
                    tiltaksgjennomfoeringId,
                )
            }
        }
        return EventHandledResult.Success()
    }
}
