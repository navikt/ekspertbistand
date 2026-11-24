package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.vedlegg.SluttrapportStatus
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val lagreSluttrapportVurderingSubtask = "sluttrapport_vurdering_lagret"

/**
 * Lagrer [EventData.SluttrapportBekreftet] på sluttrapport-raden og skriver sakslogg.
 * Se [lagreSluttrapportVurdering] og `specifications/vurdering_sluttrapport.md`.
 */
class LagreSluttrapportBekreftet(
    private val database: Database,
) : EventHandler<EventData.SluttrapportBekreftet> {
    override val id: String = "Lagre sluttrapport bekreftet"
    override val eventType: KClass<EventData.SluttrapportBekreftet> = EventData.SluttrapportBekreftet::class

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SluttrapportBekreftet>): EventHandledResult {
        val data = event.data
        return lagreSluttrapportVurdering(
            database = database,
            event = event,
            idempotencyGuard = idempotencyGuard,
            vurdering = SluttrapportVurdering(
                sluttrapportId = data.sluttrapportId,
                sakId = data.sakId,
                soknadId = data.soknadId,
                status = SluttrapportStatus.BEKREFTET,
                vurdertAvIdent = data.vurdertAvIdent,
                begrunnelse = null,
                tidspunkt = data.tidspunkt,
                notat = "Sluttrapport bekreftet",
            ),
        )
    }
}

/**
 * Lagrer [EventData.SluttrapportNyInnsendingKrevd] på sluttrapport-raden og skriver sakslogg.
 * Begrunnelsen lagres på raden, men står ikke i saksloggen og logges ikke.
 * Se [lagreSluttrapportVurdering] og `specifications/vurdering_sluttrapport.md`.
 */
class LagreSluttrapportNyInnsendingKrevd(
    private val database: Database,
) : EventHandler<EventData.SluttrapportNyInnsendingKrevd> {
    override val id: String = "Lagre sluttrapport ny innsending krevd"
    override val eventType: KClass<EventData.SluttrapportNyInnsendingKrevd> =
        EventData.SluttrapportNyInnsendingKrevd::class

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SluttrapportNyInnsendingKrevd>): EventHandledResult {
        val data = event.data
        return lagreSluttrapportVurdering(
            database = database,
            event = event,
            idempotencyGuard = idempotencyGuard,
            vurdering = SluttrapportVurdering(
                sluttrapportId = data.sluttrapportId,
                sakId = data.sakId,
                soknadId = data.soknadId,
                status = SluttrapportStatus.NY_INNSENDING_KREVES,
                vurdertAvIdent = data.vurdertAvIdent,
                begrunnelse = data.begrunnelse,
                tidspunkt = data.tidspunkt,
                notat = "Ny innsending av sluttrapport kreves",
            ),
        )
    }
}

@OptIn(ExperimentalTime::class)
private data class SluttrapportVurdering(
    val sluttrapportId: String,
    val sakId: String,
    val soknadId: String,
    val status: SluttrapportStatus,
    val vurdertAvIdent: String,
    val begrunnelse: String?,
    val tidspunkt: Instant,
    val notat: String,
)

private val log = LoggerFactory.getLogger("LagreSluttrapportVurdering")

/**
 * Oppdaterer sluttrapport-raden bare hvis den hører til saken og fortsatt er
 * [SluttrapportStatus.MOTTATT], og publiserer da [EventData.SakOppdatert]. Var raden allerede
 * vurdert (en duplikat vurdering som tapte kappløpet), lagres ingenting og saksloggen får ingen
 * post. Oppdatering, sakslogg og idempotens-raden skrives i samme transaksjon.
 */
@OptIn(ExperimentalTime::class)
private fun <T : EventData> EventHandler<T>.lagreSluttrapportVurdering(
    database: Database,
    event: Event<T>,
    idempotencyGuard: IdempotencyGuard,
    vurdering: SluttrapportVurdering,
): EventHandledResult {
    if (idempotencyGuard.isGuarded(event.id, lagreSluttrapportVurderingSubtask)) return success()

    val sluttrapportId: UUID
    val sakId: UUID
    try {
        sluttrapportId = UUID.fromString(vurdering.sluttrapportId)
        sakId = UUID.fromString(vurdering.sakId)
    } catch (_: IllegalArgumentException) {
        return unrecoverableError("Ugyldig id i ${event.data::class.simpleName}")
    }

    return transaction(database) {
        try {
            val oppdatert = SluttrapportTable.update({
                (SluttrapportTable.id eq sluttrapportId) and
                    (SluttrapportTable.sakId eq sakId) and
                    (SluttrapportTable.status eq SluttrapportStatus.MOTTATT.name)
            }) {
                it[status] = vurdering.status.name
                it[vurdertAvIdent] = vurdering.vurdertAvIdent
                it[vurdertTidspunkt] = vurdering.tidspunkt
                it[begrunnelse] = vurdering.begrunnelse
            }

            if (oppdatert == 1) {
                SakTable.update({ SakTable.sakId eq sakId }) {
                    it[sistEndret] = vurdering.tidspunkt
                }
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = vurdering.sakId,
                        soknadId = vurdering.soknadId,
                        utfortAvRolle = AktorRolle.SAKSBEHANDLER,
                        utfortAvIdent = vurdering.vurdertAvIdent,
                        notat = vurdering.notat,
                        tidspunkt = vurdering.tidspunkt,
                    )
                )
                log.info("Sluttrapport på sakId={} lagret som {}", sakId, vurdering.status)
            } else {
                log.warn("Sluttrapport på sakId={} var ikke MOTTATT, vurderingen ble ikke lagret", sakId)
            }
            idempotencyGuard.guard(event, lagreSluttrapportVurderingSubtask)
            success()
        } catch (e: Exception) {
            rollback()
            transientError("Feil ved lagring av sluttrapportvurdering", e)
        }
    }
}
