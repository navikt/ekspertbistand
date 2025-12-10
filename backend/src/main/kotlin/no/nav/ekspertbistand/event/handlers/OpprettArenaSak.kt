package no.nav.ekspertbistand.event.handlers

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.OpprettEkspertbistand
import no.nav.ekspertbistand.arena.Saksnummer
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

class OpprettArenaSak(
    private val arenaClient: ArenaClient,
    private val database: Database,
) : EventHandler<EventData.JournalpostOpprettet> {
    override val id = "Opprett sak i Arena"

    override suspend fun handle(event: Event<EventData.JournalpostOpprettet>): EventHandledResult {
        val skjema = event.data.skjema
        val saksnummer = arenaClient.opprettTiltaksgjennomfoering(
            OpprettEkspertbistand(
                behandlendeEnhetId = event.data.behandlendeEnhetId,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                ansattFnr = skjema.ansatt.fnr,
                periodeFom = skjema.behovForBistand.startdato,
                journalpostId = event.data.journaldpostId,
                dokumentId = event.data.dokumentId
            )
        )
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
    val id = uuid("id").entityId()
    val saksnummer = text("saksnummer")
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val skjema = text("skjema")

}

data class ArenaSak(
    val saksnummer: Saksnummer,
    val skjema: DTO.Skjema,
)

fun JdbcTransaction.getBySaksnummer(saksnummer: Saksnummer) {
    ArenaSakTable.selectAll().where {
        ArenaSakTable.saksnummer eq saksnummer.saksnummer
        ArenaSakTable.loepenummer eq saksnummer.loepenrSak
        ArenaSakTable.aar eq saksnummer.aar
    }.map {

        val skjema = Json.decodeFromString<DTO.Skjema>(it[ArenaSakTable.skjema])
        ArenaSak(
            saksnummer = saksnummer, skjema = skjema
        )
    }
}

fun JdbcTransaction.insertSaksnummer(saksnummer: Saksnummer, skjema: DTO.Skjema) {
    ArenaSakTable.insert {
        it[ArenaSakTable.id] = UUID.randomUUID()
        it[ArenaSakTable.saksnummer] = saksnummer.saksnummer
        it[ArenaSakTable.loepenummer] = saksnummer.loepenrSak
        it[ArenaSakTable.aar] = saksnummer.aar
        it[ArenaSakTable.skjema] = Json.encodeToString(skjema)
    }
}