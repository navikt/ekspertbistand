package no.nav.ekspertbistand.arena

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
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
        val saksnummer = try {
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
            insertSaksnummer(saksnummer, skjema)
            QueuedEvents.insert {
                it[eventData] = EventData.TiltaksgjennomføringOpprettet(
                    skjema,
                    saksnummer,
                )
            }
        }
        return EventHandledResult.Success()
    }
}


object ArenaSakTable : Table("arena_sak") {
    val saksnummer = text("saksnummer")
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val skjema = text("skjema")

}

fun JdbcTransaction.insertSaksnummer(saksnummer: Saksnummer, skjema: DTO.Skjema) {
    ArenaSakTable.insert {
        it[ArenaSakTable.saksnummer] = saksnummer.saksnummer
        it[ArenaSakTable.loepenummer] = saksnummer.loepenrSak
        it[ArenaSakTable.aar] = saksnummer.aar
        it[ArenaSakTable.skjema] = Json.encodeToString(skjema)
    }
}