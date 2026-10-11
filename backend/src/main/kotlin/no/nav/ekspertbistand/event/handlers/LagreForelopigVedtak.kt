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
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import no.nav.ekspertbistand.saksbehandling.lagreForelopigVedtak
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val lagreForelopigVedtakSubtask = "forelopig_vedtak_lagret"

/**
 * Lagrer det foreløpige vedtaket, setter saken til `TIL_BESLUTNING` og publiserer
 * [EventData.SakOppdatert] til saksloggen. Se `specifications/forelopig_vedtak.md`.
 *
 * Er saken ikke lenger `UNDER_BEHANDLING` (for eksempel en duplikat innsending som tapte
 * kappløpet), lagres ingenting og saksloggen får ingen post. Lagringen, eventen og
 * idempotens-raden skrives i samme transaksjon.
 */
class LagreForelopigVedtak(
    private val database: Database,
) : EventHandler<EventData.ForelopigVedtakSendtTilBeslutter> {
    override val id: String = "Lagre foreløpig vedtak"
    override val eventType: KClass<EventData.ForelopigVedtakSendtTilBeslutter> =
        EventData.ForelopigVedtakSendtTilBeslutter::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.ForelopigVedtakSendtTilBeslutter>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, lagreForelopigVedtakSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i ForelopigVedtakSendtTilBeslutter")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved foreløpig vedtak")

                val lagret = lagreForelopigVedtak(data, event.id)
                if (lagret) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = sakId.toString(),
                            soknadId = data.soknadId,
                            utfortAvRolle = AktorRolle.SAKSBEHANDLER,
                            utfortAvIdent = data.innstiltAvIdent,
                            notat = "Foreløpig vedtak sendt til beslutter: ${data.utfall.visningsnavn()}",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                } else {
                    log.warn("Sak {} var ikke under behandling, foreløpig vedtak ble ikke lagret", sakId)
                }
                idempotencyGuard.guard(event, lagreForelopigVedtakSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av foreløpig vedtak", e)
            }
        }
    }

    private fun ForelopigUtfall.visningsnavn(): String = when (this) {
        ForelopigUtfall.INNVILGE -> "Innvilge"
        ForelopigUtfall.INNVILGE_REDUSERT -> "Innvilge redusert beløp"
        ForelopigUtfall.AVSLAA -> "Avslå"
    }
}
