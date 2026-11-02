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
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.VedtakForEvent
import no.nav.ekspertbistand.saksbehandling.fattVedtak
import no.nav.ekspertbistand.saksbehandling.hentSakIdForSoknad
import no.nav.ekspertbistand.saksbehandling.vedtakForEvent
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val fattVedtakSubtask = "vedtak_fattet"

/**
 * Lagrer vedtaket, setter saken til `INNVILGET` eller `AVSLATT` og publiserer
 * [EventData.SakOppdatert] til saksloggen. Se `specifications/beslutter_vedtak.md`.
 *
 * Status, versjon og habilitet sjekkes på nytt på den låste saken, se
 * [no.nav.ekspertbistand.saksbehandling.beslutningHindring]. Lagres ikke vedtaket, får saksloggen
 * ingen post. Det er greit når saken har et likt vedtak fra en annen event, ellers feiler eventen.
 * Lagringen, eventen og idempotens-raden skrives i samme transaksjon.
 */
class FattVedtak(
    private val database: Database,
) : EventHandler<EventData.VedtakFattet> {
    override val id: String = "Fatt vedtak"
    override val eventType: KClass<EventData.VedtakFattet> = EventData.VedtakFattet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VedtakFattet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, fattVedtakSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i VedtakFattet")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved vedtak")

                if (fattVedtak(data, event.id)) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = sakId.toString(),
                            soknadId = data.soknadId,
                            utfortAvRolle = AktorRolle.BESLUTTER,
                            utfortAvIdent = data.fattetAvIdent,
                            notat = "Vedtak fattet: ${data.utfall.visningsnavn()}",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                } else {
                    val vedtak = vedtakForEvent(data, event.id)
                    if (vedtak !is VedtakForEvent.Duplikat) {
                        return@transaction unrecoverableError("Vedtak for sak $sakId ble ikke fattet: $vedtak")
                    }
                    log.info("Vedtak for sak {} er allerede fattet likt av event {}", sakId, vedtak.eventId)
                }
                idempotencyGuard.guard(event, fattVedtakSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av vedtak", e)
            }
        }
    }

    private fun Saksstatus.visningsnavn(): String = when (this) {
        Saksstatus.AVSLATT -> "avslått"
        else -> "innvilget"
    }
}
