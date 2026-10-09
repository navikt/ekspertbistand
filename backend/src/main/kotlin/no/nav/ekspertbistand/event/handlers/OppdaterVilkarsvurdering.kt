package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.SaksvilkarTable
import no.nav.ekspertbistand.saksbehandling.VilkarsvurderingRequest
import no.nav.ekspertbistand.saksbehandling.vilkarFinnes
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val lagreVilkarsvurderingSubtask = "vilkarsvurdering_lagret"

/**
 * Lagrer vurderingen i [EventData.VilkarsvurderingOppdatert] i `saksvilkar` og oppdaterer
 * `sak.sist_endret`. Tidspunktet kommer fra eventen, satt av `PATCH …/vilkarsvurdering`.
 *
 * Er saken ikke lenger under behandling, eller finnes det allerede en nyere vurdering av vilkåret,
 * lagres ingenting og eventen regnes som behandlet. Oppdateringen og idempotens-raden skrives i
 * samme transaksjon.
 */
class OppdaterVilkarsvurdering(
    private val database: Database,
) : EventHandler<EventData.VilkarsvurderingOppdatert> {
    override val id: String = "Oppdater vilkårsvurdering"
    override val eventType: KClass<EventData.VilkarsvurderingOppdatert> = EventData.VilkarsvurderingOppdatert::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VilkarsvurderingOppdatert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, lagreVilkarsvurderingSubtask)) {
            return success()
        }

        val data = event.data
        val sakId = try {
            UUID.fromString(data.sakId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig sakId i VilkarsvurderingOppdatert")
        }

        return transaction(database) {
            try {
                val resultat = lagreVilkarsvurdering(
                    sakId = sakId,
                    vurdering = data.vurdering,
                    navIdent = data.vurdertAvIdent,
                    tidspunkt = data.tidspunkt,
                )
                when (resultat) {
                    LagreVilkarResultat.SakIkkeFunnet ->
                        return@transaction unrecoverableError("Fant ikke sak $sakId ved vilkårsvurdering")

                    LagreVilkarResultat.VilkarIkkeFunnet ->
                        return@transaction unrecoverableError(
                            "Fant ikke vilkår ${data.vurdering.vilkar} på sak $sakId ved vilkårsvurdering"
                        )

                    LagreVilkarResultat.IkkeUnderBehandling ->
                        log.warn("Sak {} er ikke under behandling, vilkårsvurderingen lagres ikke", sakId)

                    LagreVilkarResultat.Utdatert ->
                        log.info("Vilkår {} på sak {} har en nyere vurdering, lagres ikke", data.vurdering.vilkar, sakId)

                    LagreVilkarResultat.Lagret ->
                        log.info("Vilkår {} på sak {} lagret", data.vurdering.vilkar, sakId)
                }
                idempotencyGuard.guard(event, lagreVilkarsvurderingSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av vilkårsvurdering", e)
            }
        }
    }
}

private sealed interface LagreVilkarResultat {
    data object Lagret : LagreVilkarResultat
    /** Vilkåret har allerede en vurdering med nyere tidspunkt. */
    data object Utdatert : LagreVilkarResultat
    data object SakIkkeFunnet : LagreVilkarResultat
    data object VilkarIkkeFunnet : LagreVilkarResultat
    data object IkkeUnderBehandling : LagreVilkarResultat
}

/**
 * Låser saken og lagrer bare hvis den fortsatt er [Saksstatus.UNDER_BEHANDLING]. Oppdaterer kun en
 * eksisterende vilkårsrad (opprettet av [OpprettSak.opprettVilkarForSak]); finnes ikke raden, legges
 * den ikke til. En vurdering med eldre [tidspunkt] enn den som er lagret, skriver ikke over.
 * Må kalles i en pågående transaksjon.
 */
@OptIn(ExperimentalTime::class)
private fun lagreVilkarsvurdering(
    sakId: UUID,
    vurdering: VilkarsvurderingRequest,
    navIdent: String,
    tidspunkt: Instant,
): LagreVilkarResultat {
    val status = SakTable
        .select(SakTable.status)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?.get(SakTable.status)
        ?: return LagreVilkarResultat.SakIkkeFunnet

    if (status != Saksstatus.UNDER_BEHANDLING.name) {
        return LagreVilkarResultat.IkkeUnderBehandling
    }

    val oppdatert = SaksvilkarTable.update({
        (SaksvilkarTable.sakId eq sakId) and
                (SaksvilkarTable.vilkarId eq vurdering.vilkar.name) and
                (SaksvilkarTable.vurdertTidspunkt.isNull() or (SaksvilkarTable.vurdertTidspunkt lessEq tidspunkt))
    }) {
        it[godkjent] = vurdering.godkjent
        it[notat] = vurdering.notat
        it[vurdertTidspunkt] = tidspunkt
        it[vurdertAvIdent] = navIdent
    }
    if (oppdatert == 0) {
        return if (vilkarFinnes(sakId, vurdering.vilkar)) {
            LagreVilkarResultat.Utdatert
        } else {
            LagreVilkarResultat.VilkarIkkeFunnet
        }
    }
    SakTable.update({ SakTable.sakId eq sakId }) {
        it[sistEndret] = tidspunkt
    }
    return LagreVilkarResultat.Lagret
}
