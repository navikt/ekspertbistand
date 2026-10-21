package no.nav.ekspertbistand.tilsagndata

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.soknad.arbeidsgiverTilgangVarighet
import no.nav.ekspertbistand.soknad.findSoknadById
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.json.json
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
object TilsagndataTable : Table("tilsagndata") {
    val id = javaUUID("id")
    val tilsagnNummer = text("tilsagnnummer")
    val soknadId = javaUUID("soknad_id").nullable()
    val tilsagnData = json<TilsagnData>(
        "tilsagnData",
        serialize = { Json.encodeToString(it) },
        deserialize = { Json.decodeFromString(it) }
    )

    /** Gjelder bare tilsagn uten søknad (Altinn-sporet). Ellers styrer søknadens frist. */
    val arbeidsgiverTilgangTil = timestamp("arbeidsgiver_tilgang_til")
        .clientDefault { Clock.System.now().plus(arbeidsgiverTilgangVarighet) }
}

@OptIn(ExperimentalTime::class)
fun insertTilsagndata(soknadId: UUID?, tilsagnData: TilsagnData, clock: Clock = Clock.System) {
    TilsagndataTable.insert {
        it[TilsagndataTable.id] = UUID.randomUUID()
        it[TilsagndataTable.tilsagnNummer] = tilsagnData.tilsagnNummer.concat()
        it[TilsagndataTable.soknadId] = soknadId
        it[TilsagndataTable.tilsagnData] = tilsagnData
        it[TilsagndataTable.arbeidsgiverTilgangTil] = clock.now().plus(arbeidsgiverTilgangVarighet)
    }
}

/**
 * Henter tilsagnet. Med [filterForArbeidsgiver] skjules tilsagnet når arbeidsgivers tilgang er
 * utløpt. Er tilsagnet knyttet til en søknad, gjelder søknadens frist.
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
        TilsagndataTable.arbeidsgiverTilgangTil,
    ).where {
        TilsagndataTable.tilsagnNummer eq tilsagnNummer
    }.firstOrNull() ?: return null

    val harTilgang = !filterForArbeidsgiver || when (val soknadId = rad[TilsagndataTable.soknadId]) {
        null -> rad[TilsagndataTable.arbeidsgiverTilgangTil] > now
        else -> findSoknadById(soknadId, now = now) != null
    }
    return if (harTilgang) rad[TilsagndataTable.tilsagnData] else null
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
