package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.ExperimentalTime

/**
 * Navs behandling av en søknad. Se `specifications/sak-datamodell.md`.
 *
 * Speiler `sak` fra V12. Fremmednøkler og CHECK-constraints håndheves av databasen.
 */
@OptIn(ExperimentalTime::class)
object SakTable : Table("sak") {
    val sakId = uuid("sak_id").databaseGenerated()
    val soknadId = uuid("soknad_id")
    val status = text("status")
    val kildeTilBehandling = text("kilde_til_behandling")
    val behandlendeEnhet = text("behandlende_enhet").nullable()
    val saksbehandlerIdent = text("saksbehandler_ident").nullable()
    val beslutterIdent = text("beslutter_ident").nullable()
    val foreslattUtfall = text("foreslatt_utfall").nullable()
    val arenaSakId = text("arena_sak_id").nullable()
    val refusjonId = uuid("refusjon_id").nullable()
    val sluttrapportId = uuid("sluttrapport_id").nullable()
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
    val sistEndret = timestamp("sist_endret").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sakId)
}

fun JdbcTransaction.hentGodkjentSakIdForUpdate(soknadId: UUID): UUID {
    val soknadStatus = SoknadTable
        .select(SoknadTable.status)
        .where { SoknadTable.id eq soknadId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.get(SoknadTable.status)
        ?: throw SoknadIkkeFunnetException()

    if (soknadStatus != SoknadStatus.godkjent.name) {
        throw SoknadIkkeGodkjentException()
    }

    return SakTable
        .selectAll()
        .where { SakTable.soknadId eq soknadId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.get(SakTable.sakId)
        ?: throw SakIkkeFunnetException()
}

/**
 * Vilkårsvurderingen for en sak, én rad per [Vilkar]. Se `specifications/sak-datamodell.md`.
 *
 * Speiler `saksvilkar` fra V19. `godkjent = null` betyr «ikke vurdert ennå».
 */
@OptIn(ExperimentalTime::class)
object SaksvilkarTable : Table("saksvilkar") {
    val sakId = uuid("sak_id")
    val vilkarId = text("vilkar_id")
    val godkjent = bool("godkjent").nullable()
    val notat = text("notat").nullable()
    val vurdertTidspunkt = timestamp("vurdert_tidspunkt").nullable()
    val vurdertAvIdent = text("vurdert_av_ident").nullable()

    override val primaryKey = PrimaryKey(sakId, vilkarId)
}

/** Lagres i `saksvilkar.vilkar_id` med [name]. Nye verdier krever re-kjøring av SakProjection for eksisterende saker. */
@Serializable
enum class Vilkar {
    DELTAKER_HAR_ARBEIDSFORHOLD,
    FYLLES_UT_I_SAMRAD_GODKJENT,
    ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING,
    DELTAKER_HAR_SYKEFRAVAERSHISTORIKK,
    EKSPERT_HAR_KOMPETANSE,
}

/**
 * Oppretter alle vilkår for saken som ikke vurdert. Idempotent: eksisterende rader beholdes.
 * Må kalles i en pågående transaksjon.
 */
fun opprettVilkarForSak(sakId: UUID) {
    SaksvilkarTable.batchInsert(Vilkar.entries, ignore = true, shouldReturnGeneratedValues = false) { vilkar ->
        this[SaksvilkarTable.sakId] = sakId
        this[SaksvilkarTable.vilkarId] = vilkar.name
    }
}

class SoknadIkkeFunnetException : RuntimeException()
class SoknadIkkeGodkjentException : RuntimeException()
class SakIkkeFunnetException : RuntimeException()

/**
 * Hendelseslogg for en sak. Se `specifications/sakslogg.md`.
 *
 * Speiler `sakslogg` fra V12, med `utfort_av_type` fjernet i V17.
 */
@OptIn(ExperimentalTime::class)
object SaksloggTable : Table("sakslogg") {
    val saksloggId = uuid("sakslogg_id").databaseGenerated()
    val sakId = uuid("sak_id")
    val utfortAvRolle = text("utfort_av_rolle")
    val utfortAvIdent = text("utfort_av_ident").nullable()
    val notat = text("notat").nullable()
    val utfortAt = timestamp("utfort_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(saksloggId)
}

enum class Saksstatus {
    OPPRETTET,
    UNDER_BEHANDLING,
    TIL_BESLUTNING,
    INNVILGET,
    AVSLATT,
    AVSLUTTET, //Refundert?
}

enum class KildeTilBehandling {
    EKSPERTBISTAND,
    ARENA,
}

/** Rollen aktøren hadde da handlingen i saksloggen ble utført. `SYSTEM` har ingen ident. */
@Serializable
enum class AktorRolle {
    SAKSBEHANDLER,
    BESLUTTER,
    SYSTEM,
}
