package no.nav.ekspertbistand.sak

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
