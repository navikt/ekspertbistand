package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Bestillingen til OeBS for et innvilget vedtak, én rad per vedtak. Speiler `sak_bestilling` fra V30.
 * [periodeTil] er inklusiv, som tiltaksperioden. Se `specifications/sette_av_penger_oebs_vedtak.md`.
 */
@OptIn(ExperimentalTime::class)
object SakBestillingTable : Table("sak_bestilling") {
    val id = long("id").autoIncrement()
    val sakId = javaUUID("sak_id")
    val vedtakId = long("vedtak_id")
    val bestillingsnummer = text("bestillingsnummer")
    val belop = integer("belop")
    val periodeFra = date("periode_fra")
    val periodeTil = date("periode_til")
    val opprettet = timestamp("opprettet")

    override val primaryKey = PrimaryKey(id)
}

/** Det vi trenger fra vedtak, foreløpig vedtak, sak og søknad for å bestille tilsagnet i OeBS. */
@OptIn(ExperimentalTime::class)
data class BestillingGrunnlag(
    val vedtakId: Long,
    val sakId: UUID,
    val saksnummer: String?,
    val soknadId: UUID,
    val utfall: Saksstatus,
    val belop: Int?,
    val periodeFra: LocalDate?,
    val periodeTil: LocalDate?,
    val behandlendeEnhet: String?,
    val virksomhetsnummer: String,
    val innstiltAvIdent: String,
    val innstiltTidspunkt: Instant,
    val fattetAvIdent: String,
    val fattetTidspunkt: Instant,
)

/** Grunnlaget for vedtaket som ble lagret for eventen, eller null. Må kalles i en pågående transaksjon. */
@OptIn(ExperimentalTime::class)
fun hentBestillingGrunnlagForEvent(eventId: Long): BestillingGrunnlag? =
    VedtakTable
        .join(ForelopigVedtakTable, JoinType.INNER, VedtakTable.forelopigVedtakId, ForelopigVedtakTable.id)
        .join(SakTable, JoinType.INNER, VedtakTable.sakId, SakTable.sakId)
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(
            VedtakTable.id,
            VedtakTable.sakId,
            VedtakTable.utfall,
            VedtakTable.fattetAvIdent,
            VedtakTable.fattetTidspunkt,
            SakTable.saksnummer,
            SakTable.soknadId,
            SakTable.behandlendeEnhet,
            ForelopigVedtakTable.innvilgetBelop,
            ForelopigVedtakTable.tiltaksperiodeFra,
            ForelopigVedtakTable.tiltaksperiodeTil,
            ForelopigVedtakTable.innstiltAvIdent,
            ForelopigVedtakTable.sendtTidspunkt,
            SoknadTable.virksomhetsnummer,
        )
        .where { VedtakTable.eventId eq eventId }
        .singleOrNull()
        ?.let {
            BestillingGrunnlag(
                vedtakId = it[VedtakTable.id],
                sakId = it[VedtakTable.sakId],
                saksnummer = it[SakTable.saksnummer],
                soknadId = it[SakTable.soknadId],
                utfall = Saksstatus.valueOf(it[VedtakTable.utfall]),
                belop = it[ForelopigVedtakTable.innvilgetBelop],
                periodeFra = it[ForelopigVedtakTable.tiltaksperiodeFra],
                periodeTil = it[ForelopigVedtakTable.tiltaksperiodeTil],
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
                virksomhetsnummer = it[SoknadTable.virksomhetsnummer],
                innstiltAvIdent = it[ForelopigVedtakTable.innstiltAvIdent],
                innstiltTidspunkt = it[ForelopigVedtakTable.sendtTidspunkt],
                fattetAvIdent = it[VedtakTable.fattetAvIdent],
                fattetTidspunkt = it[VedtakTable.fattetTidspunkt],
            )
        }

/** Om vedtaket allerede har en bestilling. Må kalles i en pågående transaksjon. */
fun bestillingFinnesForVedtak(vedtakId: Long): Boolean =
    !SakBestillingTable.select(SakBestillingTable.id)
        .where { SakBestillingTable.vedtakId eq vedtakId }
        .empty()

/**
 * Lagrer bestillingen for vedtaket i [grunnlag]. Krever beløp og periode. `vedtak_id UNIQUE`
 * stopper en ny bestilling for samme vedtak. Må kalles i en pågående transaksjon.
 */
@OptIn(ExperimentalTime::class)
fun lagreBestilling(grunnlag: BestillingGrunnlag, bestillingsnummer: String) {
    SakBestillingTable.insert {
        it[sakId] = grunnlag.sakId
        it[vedtakId] = grunnlag.vedtakId
        it[SakBestillingTable.bestillingsnummer] = bestillingsnummer
        it[belop] = requireNotNull(grunnlag.belop)
        it[periodeFra] = requireNotNull(grunnlag.periodeFra)
        it[periodeTil] = requireNotNull(grunnlag.periodeTil)
        it[opprettet] = grunnlag.fattetTidspunkt
    }
}
