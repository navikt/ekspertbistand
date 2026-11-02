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
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.hentVedtakUtfallForEvent
import no.nav.ekspertbistand.saksbehandling.vedtakKanFattes
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.arbeidsgiverTilgangVarighet
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val settSoknadStatusSubtask = "soknadstatus_vedtak_satt"

/**
 * Setter søknaden til `godkjent` eller `avslatt` når vedtaket er fattet. Se
 * `specifications/beslutter_vedtak.md`, 4b.
 *
 * `godkjent` åpner for refusjon og vedlegg, så søknaden oppdateres bare når [FattVedtak] har
 * lagret vedtaket for samme event. Handlerne kjører hver for seg, så denne slår opp vedtaket:
 * - lagret: søknaden oppdateres, men bare fra `innsendt`
 * - ikke lagret, men eventen kan fortsatt lagres: [FattVedtak] har ikke kjørt ennå, prøv igjen
 * - ikke lagret, og eventen kan ikke lagres: [FattVedtak] avviste den, ingen endring
 */
class SettSoknadStatusVedtakFattet @OptIn(ExperimentalTime::class) constructor(
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.VedtakFattet> {
    override val id: String = "Sett soknadstatus etter vedtak"
    override val eventType: KClass<EventData.VedtakFattet> = EventData.VedtakFattet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VedtakFattet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, settSoknadStatusSubtask)) {
            return success()
        }

        val soknadId = try {
            UUID.fromString(event.data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i VedtakFattet")
        }

        return transaction(database) {
            try {
                val utfall = hentVedtakUtfallForEvent(event.id)
                if (utfall == null) {
                    return@transaction if (vedtakKanFattes(event.data)) {
                        transientError("Vedtaket for søknad $soknadId er ikke lagret ennå")
                    } else {
                        log.info("Vedtaket for søknad {} ble avvist, søknadsstatus endres ikke", soknadId)
                        idempotencyGuard.guard(event, settSoknadStatusSubtask)
                        success()
                    }
                }

                val nyStatus = when (utfall) {
                    Saksstatus.AVSLATT -> SoknadStatus.avslatt
                    else -> SoknadStatus.godkjent
                }
                val oppdatert = SoknadTable.update(
                    where = { (SoknadTable.id eq soknadId) and (SoknadTable.status eq SoknadStatus.innsendt.name) }
                ) {
                    it[status] = nyStatus.name
                    it[arbeidsgiverTilgangTil] = clock.now().plus(arbeidsgiverTilgangVarighet)
                }

                when {
                    oppdatert > 0 -> {
                        log.info("Søknad {} satt til {} etter vedtak", soknadId, nyStatus)
                        idempotencyGuard.guard(event, settSoknadStatusSubtask)
                        success()
                    }

                    SoknadTable.select(SoknadTable.id).where { SoknadTable.id eq soknadId }.empty() ->
                        unrecoverableError("Fant ikke søknad $soknadId ved vedtak")

                    else -> {
                        log.info("Søknad {} var ikke innsendt, status endres ikke", soknadId)
                        idempotencyGuard.guard(event, settSoknadStatusSubtask)
                        success()
                    }
                }
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved oppdatering av søknadsstatus etter vedtak", e)
            }
        }
    }
}
