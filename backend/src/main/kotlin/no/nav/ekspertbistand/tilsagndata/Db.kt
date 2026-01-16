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
    val skjemaId = uuid("skjema_id")
    val tilsagnData = json<TilsagnData>(
        "tilsagnData",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )
}

fun insertTilsagndata(skjemaId: UUID, tilsagnData: TilsagnData) {
    TilsagndataTable.insert {
        it[id] = UUID.randomUUID()
        it[tilsagnNummer] = tilsagnData.tilsagnNummer.concat()
        it[this.skjemaId] = skjemaId
        it[this.tilsagnData] = tilsagnData
    }
}

fun findTilsagnDataByTilsagnNummer(tilsagnNummer: TilsagnData.TilsagnNummer) =
    TilsagndataTable.select(
        TilsagndataTable.tilsagnData
    ).where {
        TilsagndataTable.tilsagnNummer eq tilsagnNummer.concat()
    }.map {
        it[TilsagndataTable.tilsagnData]
    }.firstOrNull()


fun findTilsagnDataBySkjemaId(skjemaId: UUID) =
    TilsagndataTable.select(
        TilsagndataTable.tilsagnData
    ).where {
        TilsagndataTable.skjemaId eq skjemaId
    }.map {
        it[TilsagndataTable.tilsagnData]
    }

fun TilsagnData.TilsagnNummer.concat() = "$aar:$loepenrSak:$loepenrTilsagn"