package no.nav.ekspertbistand.tilsagndata

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.soknad.arbeidsgiverTilgangVarighet
import no.nav.ekspertbistand.soknad.findSoknadById
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.json.json
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object TilsagndataTable : Table("tilsagndata") {
    val id = javaUUID("id")
    val tilsagnNummer = text("tilsagnnummer")
    val soknadId = javaUUID("soknad_id").nullable()
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

/**
 * Henter tilsagnet. Med [filterForArbeidsgiver] skjules tilsagnet når arbeidsgivers tilgang er
 * utløpt. Er tilsagnet knyttet til en søknad, gjelder søknadens frist. Tilsagn uten søknad
 * (legacy Altinn-sporet, se [no.nav.ekspertbistand.event.EventData.TilskuddsbrevMottattKildeAltinn])
 * er tilgjengelige i [arbeidsgiverTilgangVarighet] etter siste event på tilsagnsnummeret.
 */
@OptIn(ExperimentalTime::class)
fun findTilsagnDataByTilsagnNummer(
    tilsagnNummer: String,
    filterForArbeidsgiver: Boolean = true,
    now: Instant = Clock.System.now(),
): TilsagnData? {
    val rad = TilsagndataTable.select(
        TilsagndataTable.tilsagnData,
        TilsagndataTable.soknadId,
    ).where {
        TilsagndataTable.tilsagnNummer eq tilsagnNummer
    }.firstOrNull() ?: return null

    val harTilgang = !filterForArbeidsgiver || when (val soknadId = rad[TilsagndataTable.soknadId]) {
        null -> sisteEventTidspunkt(tilsagnNummer)?.let { it + arbeidsgiverTilgangVarighet > now } ?: false
        else -> findSoknadById(soknadId, now = now) != null
    }
    return if (harTilgang) rad[TilsagndataTable.tilsagnData] else null
}

@OptIn(ExperimentalTime::class)
private fun sisteEventTidspunkt(aggregateRootId: String): Instant? {
    val sisteEvent = EventLog.createdAt.max()
    return EventLog.select(sisteEvent)
        .where { EventLog.aggregateRootId eq aggregateRootId }
        .firstOrNull()
        ?.get(sisteEvent)
}

fun findTilsagnDataBySoknadId(soknadId: UUID) =
    TilsagndataTable.select(
        TilsagndataTable.tilsagnData
    ).where {
        TilsagndataTable.soknadId eq soknadId
    }.map {
        it[TilsagndataTable.tilsagnData]
    }

fun TilsagnData.TilsagnNummer.concat() = "$aar:$loepenrSak:$loepenrTilsagn"

/**
 * Aggregatroten for et tilsagns-event (Altinn-sporet) er tilsagnsnummeret på formen "2026:123:1".
 */
val TilsagnData.aggregateRootId: String
    get() = tilsagnNummer.concat()
