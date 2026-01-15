package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.OpprettEkspertbistand
import no.nav.ekspertbistand.arena.insertArenaSak
import no.nav.ekspertbistand.event.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class OpprettSakArena(
    private val arenaClient: ArenaClient,
    private val database: Database,
) : EventHandler<EventData.JournalpostOpprettet> {
    override val id = "OpprettSakArena"
    override val eventType = EventData.JournalpostOpprettet::class

    override suspend fun handle(event: Event<EventData.JournalpostOpprettet>): EventHandledResult {
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
            return EventHandledResult.TransientError("Feil ved oppretting av sak i Arena: ${e.message}")
        }

        transaction(database) {
            val tiltakgjennomforingId = opprettetResponse.tiltakgjennomforingId
            val saksnummer = opprettetResponse.saksnummer
            insertArenaSak(
                saksnummer,
                tiltakgjennomforingId,
                skjema
            )
            QueuedEvents.insert {
                it[eventData] = EventData.TiltaksgjennomføringOpprettet(
                    skjema,
                    saksnummer,
                    tiltakgjennomforingId,
                )
            }
        }
        return EventHandledResult.Success()
    }
}
