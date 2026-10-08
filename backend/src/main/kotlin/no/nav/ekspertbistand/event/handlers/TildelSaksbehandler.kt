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
import no.nav.ekspertbistand.saksbehandling.tildelSak
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val tildelSakSubtask = "sak_tildelt"

/**
 * Tildeler saken til saksbehandleren i [EventData.SakTildeltSaksbehandler] og publiserer
 * [EventData.SakOppdatert] («Sak tildelt») til saksloggen. Se `specifications/tildel_meg_sak.md`.
 *
 * Saksloggen får posten også når en nyere tildeling allerede er brukt, fordi saksbehandleren
 * faktisk klikket. Oppdateringen, eventen og idempotens-raden skrives i samme transaksjon.
 */
class TildelSaksbehandler(
    private val database: Database,
) : EventHandler<EventData.SakTildeltSaksbehandler> {
    override val id: String = "Tildel saksbehandler"
    override val eventType: KClass<EventData.SakTildeltSaksbehandler> = EventData.SakTildeltSaksbehandler::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SakTildeltSaksbehandler>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, tildelSakSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i SakTildeltSaksbehandler")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved tildeling")

                val oppdatert = tildelSak(
                    soknadId = soknadId,
                    ident = data.saksbehandlerIdent,
                    navn = data.saksbehandlerNavn,
                    eventId = event.id,
                    tidspunkt = data.tidspunkt,
                )
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = sakId.toString(),
                        soknadId = data.soknadId,
                        utfortAvRolle = AktorRolle.SAKSBEHANDLER,
                        utfortAvIdent = data.saksbehandlerIdent,
                        notat = "Sak tildelt",
                        tidspunkt = data.tidspunkt,
                    )
                )
                idempotencyGuard.guard(event, tildelSakSubtask)
                log.info("Tildeling for sakId={} behandlet, oppdatert={}", sakId, oppdatert)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved tildeling av sak", e)
            }
        }
    }
}
