package no.nav.ekspertbistand.arena

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll

object ArenaSakTable : Table("arena_sak") {
    val saksnummer = text("saksnummer")
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val tiltaksgjennomfoeringId = integer("tiltakgjennomforing_id")
    val soknad = text("soknad")

}

object ArenaMeldingIdempotencyTable : Table("arena_melding_idempotency") {
    val meldingstype = enumerationByName("meldingstype", 50, ArenaMeldingType::class)
    val eksternId = integer("ekstern_id")

    override val primaryKey = PrimaryKey(meldingstype, eksternId)
}

private fun markerArenaMeldingSomBehandlet(meldingstype: ArenaMeldingType, eksternId: Int): Boolean {
    val insertStatement = ArenaMeldingIdempotencyTable.insertIgnore {
        it[ArenaMeldingIdempotencyTable.meldingstype] = meldingstype
        it[ArenaMeldingIdempotencyTable.eksternId] = eksternId
    }
    return insertStatement.insertedCount > 0
}

fun markerTiltaksgjennomfoeringEndretMeldingSomBehandlet(tiltaksgjennomfoeringId: Int) =
    markerArenaMeldingSomBehandlet(ArenaMeldingType.TILTAKSGJENNOMFORING, tiltaksgjennomfoeringId)

fun markerTilsagnsbrevMeldingSomBehandlet(tilsagnBrevId: Int) =
    markerArenaMeldingSomBehandlet(ArenaMeldingType.TILSKUDDSBREV_GODKJENT, tilsagnBrevId)

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

enum class ArenaMeldingType {
    TILSKUDDSBREV_GODKJENT,
    TILTAKSGJENNOMFORING
}