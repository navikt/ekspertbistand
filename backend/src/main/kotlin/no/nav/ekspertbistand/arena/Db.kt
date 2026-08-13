package no.nav.ekspertbistand.arena

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.soknad.DTO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.ExperimentalTime

object ArenaSakTable : Table("arena_sak") {
    /**
     * Indeks er påkrevd: [ArenaTiltakssakEndretProcessor] slår opp på saksnummer for hver melding
     * som passerer forfiltrene, og topicet bærer alle tiltakssaker i Arena. Uten indeks blir hvert
     * oppslag en full table scan.
     *
     * Indeksen er *unik* fordi saksnummer er unikt per sak i Arena. Merk konsekvensene:
     *  - DB Migrering feiler hardt hvis dev/prod allerede inneholder duplikate saksnummer.
     *    Kjør `SELECT saksnummer, count(*) FROM arena_sak GROUP BY saksnummer HAVING count(*) > 1`
     *    mot begge miljøer før deploy. Ved duplikater: bytt til `.index()` (ikke-unik) og fjern
     *    ALTER TABLE-setningen i V4.
     *  - [insertArenaSak] bruker et rent `insert` uten konflikthåndtering og kalles i samme
     *    transaksjon som `EventQueue.publish` i OpprettTiltaksgjennomfoeringForInnsendtSoknad,
     *    utenfor try/catch. Skulle Arena gjenbruke et saksnummer på tvers av to
     *    tiltaksgjennomføringer, vil innsettingen kaste og rulle tilbake publiseringen *etter* at
     *    tiltaksgjennomføringen allerede er opprettet i Arena. Forutsetningen om at Arena minter en
     *    ny sak per opprettelse må bekreftes med Team Arena.
     */
    val saksnummer = text("saksnummer").uniqueIndex()
    val loepenummer = integer("løpenummer")
    val aar = integer("år")
    val tiltaksgjennomfoeringId = integer("tiltakgjennomforing_id")
    val soknad = text("soknad")

}

/**
 * Saker som er observert tatt til behandling av en saksbehandler i Arena.
 *
 * Primærnøkkelen er `sak_id`, som betyr at vi kun registrerer den *første* observerte
 * ansvarlig-endringen per sak. Senere omfordelinger mellom saksbehandlere i Arena ignoreres.
 * Det er tilstrekkelig for formålet: vi trenger kun å vite *at* saken behandles i Arena,
 * ikke av hvem til enhver tid.
 */
@OptIn(ExperimentalTime::class)
object ArenaSakUnderBehandlingTable : Table("arena_sak_under_behandling") {
    val sakId = integer("sak_id")
    val saksnummer = text("saksnummer")
    val soknadId = uuid("soknad_id")
    val brukeridAnsvarlig = text("brukerid_ansvarlig")
    val aetatenhetAnsvarlig = text("aetatenhet_ansvarlig").nullable()
    val sakstatuskode = text("sakstatuskode").nullable()
    val observertAt = timestamp("observert_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sakId)
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

/**
 * Idempotensnøkkelen er Arena sin `SAK_ID`. Vi reagerer derfor kun på den første observerte
 * ansvarlig-endringen per sak, se [ArenaSakUnderBehandlingTable].
 */
fun markerTiltakssakEndretMeldingSomBehandlet(sakId: Int) =
    markerArenaMeldingSomBehandlet(ArenaMeldingType.TILTAKSSAK_ENDRET, sakId)

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

/**
 * Markerer at saken er tatt til behandling av en saksbehandler i Arena.
 *
 * @return true dersom saken ikke var markert fra før. Gjentatte kall for samme [sakId] er trygge
 * og returnerer false.
 */
fun markerArenaSakUnderBehandling(
    sakId: Int,
    saksnummer: Saksnummer,
    soknadId: UUID,
    brukeridAnsvarlig: String,
    aetatenhetAnsvarlig: String?,
    sakstatuskode: String?,
): Boolean {
    val insertStatement = ArenaSakUnderBehandlingTable.insertIgnore {
        it[ArenaSakUnderBehandlingTable.sakId] = sakId
        it[ArenaSakUnderBehandlingTable.saksnummer] = saksnummer
        it[ArenaSakUnderBehandlingTable.soknadId] = soknadId
        it[ArenaSakUnderBehandlingTable.brukeridAnsvarlig] = brukeridAnsvarlig
        it[ArenaSakUnderBehandlingTable.aetatenhetAnsvarlig] = aetatenhetAnsvarlig
        it[ArenaSakUnderBehandlingTable.sakstatuskode] = sakstatuskode
    }
    return insertStatement.insertedCount > 0
}

@OptIn(ExperimentalTime::class)
fun erArenaSakUnderBehandling(soknadId: UUID): ArenaBehandlingStatus? =
    ArenaSakUnderBehandlingTable.selectAll()
        .where { ArenaSakUnderBehandlingTable.soknadId eq soknadId }
        .map {
            ArenaBehandlingStatus(
                underBehandlingIArena = true,
                brukeridAnsvarlig = it[ArenaSakUnderBehandlingTable.brukeridAnsvarlig],
                aetatenhetAnsvarlig = it[ArenaSakUnderBehandlingTable.aetatenhetAnsvarlig],
                observertAt = it[ArenaSakUnderBehandlingTable.observertAt].toString(),
            )
        }
        .firstOrNull()

/**
 * Advarsel til vår egen saksbehandlingsløsning om at saken allerede er tatt til behandling i Arena.
 * Dette er ikke en tilstand i vår egen saksflyt, og påvirker ikke søknadens status.
 */
@Serializable
data class ArenaBehandlingStatus(
    val underBehandlingIArena: Boolean,
    val brukeridAnsvarlig: String? = null,
    val aetatenhetAnsvarlig: String? = null,
    val observertAt: String? = null,
)

enum class ArenaMeldingType {
    TILSKUDDSBREV_GODKJENT,
    TILTAKSGJENNOMFORING,
    TILTAKSSAK_ENDRET
}
