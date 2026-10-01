package no.nav.ekspertbistand.sak

import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
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

class SoknadIkkeFunnetException : RuntimeException()
class SoknadIkkeGodkjentException : RuntimeException()
class SakIkkeFunnetException : RuntimeException()

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
