package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
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
