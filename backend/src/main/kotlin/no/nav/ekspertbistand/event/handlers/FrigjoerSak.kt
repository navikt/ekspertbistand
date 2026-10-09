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
import no.nav.ekspertbistand.saksbehandling.frigjoerSak
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val frigjoerSakSubtask = "sak_frigjort"

/**
 * Frigjør saken i [EventData.SakFrigjort] og publiserer [EventData.SakOppdatert] («Sak frigjort»)
 * til saksloggen, men bare når saken faktisk ble frigjort. Har en kollega tatt saken i mellomtiden,
 * skjer ingenting. Se `specifications/tildel_meg_sak.md`.
 */
class FrigjoerSak(
    private val database: Database,
) : EventHandler<EventData.SakFrigjort> {
    override val id: String = "Frigjør sak"
    override val eventType: KClass<EventData.SakFrigjort> = EventData.SakFrigjort::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SakFrigjort>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, frigjoerSakSubtask)) {
            return success()
        }

        val data = event.data
        val soknadId = try {
            UUID.fromString(data.soknadId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig soknadId i SakFrigjort")
        }

        return transaction(database) {
            try {
                val sakId = hentSakIdForSoknad(soknadId)
                    ?: return@transaction unrecoverableError("Fant ikke sak for søknad $soknadId ved frigjøring")

                val oppdatert = frigjoerSak(
                    soknadId = soknadId,
                    ident = data.saksbehandlerIdent,
                    eventId = event.id,
                    tidspunkt = data.tidspunkt,
                )
                if (oppdatert) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = sakId.toString(),
                            soknadId = data.soknadId,
                            utfortAvRolle = AktorRolle.SAKSBEHANDLER,
                            utfortAvIdent = data.saksbehandlerIdent,
                            notat = "Sak frigjort",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                }
                idempotencyGuard.guard(event, frigjoerSakSubtask)
                log.info("Frigjøring for sakId={} behandlet, oppdatert={}", sakId, oppdatert)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved frigjøring av sak", e)
            }
        }
    }
}
