package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.refusjon.RefusjonskravStatuskode
import no.nav.ekspertbistand.refusjon.RefusjonskravTable
import no.nav.ekspertbistand.refusjon.hentNyesteRefusjonskrav
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SakTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

class LagreRefusjonskravNyInnsendingKrevd(private val database: Database) :
    EventHandler<EventData.RefusjonskravNyInnsendingKrevd> {
    override val id = "Lagre refusjonskrav ny innsending krevd"
    override val eventType: KClass<EventData.RefusjonskravNyInnsendingKrevd> = EventData.RefusjonskravNyInnsendingKrevd::class
    private val guard = idempotencyGuard(database)
    private val subtask = "refusjonskrav_vurdering_lagret"
    private val log = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.RefusjonskravNyInnsendingKrevd>): EventHandledResult {
        if (guard.isGuarded(event.id, subtask)) return success()
        val data = event.data
        val sakId: UUID
        val kravId: UUID
        val soknadId: UUID
        try {
            sakId = UUID.fromString(data.sakId)
            kravId = UUID.fromString(data.refusjonskravId)
            soknadId = UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig id i refusjonskravvurdering")
        }
        return transaction(database) {
            try {
                val sak = SakTable.select(SakTable.soknadId).where { SakTable.sakId eq sakId }
                    .forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
                if (sak == null || sak[SakTable.soknadId] != soknadId) {
                    return@transaction unrecoverableError("Sak eller søknad stemmer ikke i refusjonskravvurdering")
                }
                if (guard.isGuarded(event.id, subtask)) return@transaction success()
                val krav = RefusjonskravTable.select(RefusjonskravTable.sakId, RefusjonskravTable.status)
                    .where { RefusjonskravTable.id eq kravId }.singleOrNull()
                if (krav == null || krav[RefusjonskravTable.sakId] != sakId) {
                    return@transaction unrecoverableError("Refusjonskravet finnes ikke på saken")
                }
                if (krav[RefusjonskravTable.status] == RefusjonskravStatuskode.MOTTATT.name &&
                    hentNyesteRefusjonskrav(sakId)?.get(RefusjonskravTable.id)?.value == kravId
                ) {
                    RefusjonskravTable.update({ RefusjonskravTable.id eq kravId }) {
                        it[status] = RefusjonskravStatuskode.NY_INNSENDING_KREVES.name
                        it[arsaker] = data.arsaker.map { arsak -> arsak.name }
                        it[begrunnelse] = data.begrunnelse
                        it[vurdertAvIdent] = data.vurdertAvIdent
                        it[vurdertAvNavn] = data.vurdertAvNavn
                        it[vurdertTidspunkt] = data.tidspunkt
                    }
                    SakTable.update({ SakTable.sakId eq sakId }) { it[sistEndret] = data.tidspunkt }
                    publishEventQueue(EventData.SakOppdatert(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfortAvRolle = AktorRolle.SAKSBEHANDLER,
                        utfortAvIdent = data.vurdertAvIdent,
                        notat = "Ny innsending av refusjonskrav kreves",
                        tidspunkt = data.tidspunkt,
                    ))
                } else {
                    log.warn("Refusjonskravvurdering på sakId={} var ikke lenger aktuell", sakId)
                }
                guard.guard(event, subtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av refusjonskravvurdering", e)
            }
        }
    }
}
