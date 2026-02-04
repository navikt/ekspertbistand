package no.nav.ekspertbistand.tilsagndata

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.arena.TilsagnData
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.json.json
import java.util.UUID

object TilsagndataTable : Table("tilsagndata") {
    val id = uuid("id")
    val tilsagnNummer = text("tilsagnnummer")
    val soknadId = uuid("soknad_id").nullable()
    val tilsagnData = json<TilsagnData>(
        "tilsagnData",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )
}

fun insertTilsagndata(soknadId: UUID?, tilsagnData: TilsagnData) {
    TilsagndataTable.insert {
        it[TilsagndataTable.id] = UUID.randomUUID()
        it[TilsagndataTable.tilsagnNummer] = tilsagnData.tilsagnNummer.concat()
        it[TilsagndataTable.soknadId] = soknadId
        it[TilsagndataTable.tilsagnData] = tilsagnData
    }
}

fun findTilsagnDataByTilsagnNummer(tilsagnNummer: String) =
    TilsagndataTable.select(
        TilsagndataTable.tilsagnData
    ).where {
        TilsagndataTable.tilsagnNummer eq tilsagnNummer
    }.map {
        it[TilsagndataTable.tilsagnData]
    }.firstOrNull()


fun findTilsagnDataBySoknadId(soknadId: UUID) =
    TilsagndataTable.select(
        TilsagndataTable.tilsagnData
    ).where {
        TilsagndataTable.soknadId eq soknadId
    }.map {
        it[TilsagndataTable.tilsagnData]
    }

fun TilsagnData.TilsagnNummer.concat() = "$aar:$loepenrSak:$loepenrTilsagn"