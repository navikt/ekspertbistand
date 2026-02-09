package no.nav.ekspertbistand.arena

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

object ArenaSakTable : Table("arena_sak") {
    val saksnummer = text("saksnummer")
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val tiltaksgjennomfoeringId = integer("tiltakgjennomforing_id")
    val soknad = text("soknad")

}

fun insertArenaSak(
    saksnummer: Saksnummer,
    tiltaksgjennomfoeringId: Int,
    soknad: DTO.Soknad
) {
    ArenaSakTable.insert {
        it[ArenaSakTable.tiltaksgjennomfoeringId] = tiltaksgjennomfoeringId
        it[ArenaSakTable.saksnummer] = saksnummer
        it[ArenaSakTable.loepenummer] = saksnummer.loepenrSak
        it[ArenaSakTable.aar] = saksnummer.aar
        it[ArenaSakTable.soknad] = Json.encodeToString(soknad)
    }
}

fun <T> hentArenaSakBySaksnummer(saksnummer: Saksnummer, mapper: ResultRow.() -> T?) =
    ArenaSakTable.selectAll()
        .where {
            ArenaSakTable.saksnummer eq saksnummer
        }
        .map { mapper(it) }
        .firstOrNull()

fun <T> hentArenaSakBytiltaksgjennomfoeringId(tiltaksgjennomfoeringId: Int, mapper: ResultRow.() -> T?) =
    ArenaSakTable.selectAll()
        .where {
            ArenaSakTable.tiltaksgjennomfoeringId eq tiltaksgjennomfoeringId
        }
        .map { mapper(it) }
        .firstOrNull()