package no.nav.ekspertbistand.arena

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

object ArenaSakTable : Table("arena_sak") {
    val saksnummer = text("saksnummer")
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val tiltakgjennomforingId = integer("tiltakgjennomforing_id")
    val skjema = text("skjema")

}

fun insertArenaSak(
    saksnummer: Saksnummer,
    tiltakgjennomforingId: Int,
    skjema: DTO.Skjema
) {
    ArenaSakTable.insert {
        it[ArenaSakTable.tiltakgjennomforingId] = tiltakgjennomforingId
        it[ArenaSakTable.saksnummer] = saksnummer
        it[ArenaSakTable.loepenummer] = saksnummer.loepenrSak
        it[ArenaSakTable.aar] = saksnummer.aar
        it[ArenaSakTable.skjema] = Json.encodeToString(skjema)
    }
}

fun <T> hentArenaSakBySaksnummer(saksnummer: Saksnummer, mapper: ResultRow.() -> T?) =
    ArenaSakTable.selectAll()
        .where {
            ArenaSakTable.saksnummer eq saksnummer
        }
        .map { mapper(it) }
        .firstOrNull()

fun <T> hentArenaSakByTiltakgjennomforingId(tiltakgjennomforingId: Int, mapper: ResultRow.() -> T?) =
    ArenaSakTable.selectAll()
        .where {
            ArenaSakTable.tiltakgjennomforingId eq tiltakgjennomforingId
        }
        .map { mapper(it) }
        .firstOrNull()