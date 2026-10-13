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
import no.nav.ekspertbistand.saksbehandling.tildelBeslutter
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val tildelBeslutterSubtask = "beslutter_tildelt"

/**
 * Tildeler saken til beslutteren i [EventData.SakTildeltBeslutter] og publiserer
 * [EventData.SakOppdatert] («Beslutter tildelt») til saksloggen. Se `specifications/listevisning.md`.
 *
 * [tildelBeslutter] sjekker status og habilitet på nytt. Saksloggen får posten bare når saken ble
 * oppdatert. Oppdateringen, eventen og idempotens-raden skrives i samme transaksjon.
 */
class TildelBeslutter(
    private val database: Database,
) : EventHandler<EventData.SakTildeltBeslutter> {
    override val id: String = "Tildel beslutter"
    override val eventType: KClass<EventData.SakTildeltBeslutter> = EventData.SakTildeltBeslutter::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SakTildeltBeslutter>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, tildelBeslutterSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i SakTildeltBeslutter")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved tildeling av beslutter")

                val oppdatert = tildelBeslutter(
                    soknadId = soknadId,
                    ident = data.beslutterIdent,
                    navn = data.beslutterNavn,
                    eventId = event.id,
                    tidspunkt = data.tidspunkt,
                )
                if (oppdatert) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = sakId.toString(),
                            soknadId = data.soknadId,
                            utfortAvRolle = AktorRolle.BESLUTTER,
                            utfortAvIdent = data.beslutterIdent,
                            notat = "Beslutter tildelt",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                }
                idempotencyGuard.guard(event, tildelBeslutterSubtask)
                log.info("Tildeling av beslutter for sakId={} behandlet, oppdatert={}", sakId, oppdatert)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved tildeling av beslutter", e)
            }
        }
    }
}
