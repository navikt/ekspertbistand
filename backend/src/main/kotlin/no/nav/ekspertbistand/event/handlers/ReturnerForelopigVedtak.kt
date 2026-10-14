package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import no.nav.ekspertbistand.saksbehandling.returnerForelopigVedtak
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val returnerForelopigVedtakSubtask = "forelopig_vedtak_returnert"

/**
 * Lagrer returen i `sak_retur`, setter saken tilbake til `UNDER_BEHANDLING` og publiserer
 * [EventData.SakOppdatert] til saksloggen, uten forklaringen. Saksbehandler og beslutter beholdes.
 * Se `specifications/beslutter_vedtak.md`.
 *
 * Er saken ikke lenger til beslutning på samme versjon, lagres ingenting og saksloggen får ingen
 * post. Lagringen, eventen og idempotens-raden skrives i samme transaksjon.
 */
class ReturnerForelopigVedtak(
    private val database: Database,
) : EventHandler<EventData.ForelopigVedtakReturnert> {
    override val id: String = "Returner foreløpig vedtak"
    override val eventType: KClass<EventData.ForelopigVedtakReturnert> = EventData.ForelopigVedtakReturnert::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.ForelopigVedtakReturnert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, returnerForelopigVedtakSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i ForelopigVedtakReturnert")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved retur")

                if (returnerForelopigVedtak(data, event.id)) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = sakId.toString(),
                            soknadId = data.soknadId,
                            utfortAvRolle = AktorRolle.BESLUTTER,
                            utfortAvIdent = data.returnertAvIdent,
                            notat = "Sendt i retur til saksbehandler",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                } else {
                    log.warn("Retur for sak {} ble ikke lagret: saken kan ikke lenger besluttes", sakId)
                }
                idempotencyGuard.guard(event, returnerForelopigVedtakSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av retur", e)
            }
        }
    }
}
