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
import no.nav.ekspertbistand.saksbehandling.endreSaksnotat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val endreSaksnotatSubtask = "saksnotat_endret"

/**
 * Endrer teksten i notatet i [EventData.SaksnotatEndret] og publiserer [EventData.SakOppdatert] («Notat endret»)
 * til saksloggen, men bare når notatet finnes og er skrevet av [EventData.SaksnotatEndret.endretAvIdent].
 * Se `specifications/notat_i_saken.md`.
 */
class EndreSaksnotat(
    private val database: Database,
) : EventHandler<EventData.SaksnotatEndret> {
    override val id: String = "Endre saksnotat"
    override val eventType: KClass<EventData.SaksnotatEndret> = EventData.SaksnotatEndret::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SaksnotatEndret>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, endreSaksnotatSubtask)) {
            return success()
        }

        val data = event.data
        val notatId = try {
            UUID.fromString(data.notatId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig notatId i SaksnotatEndret")
        }

        return transaction(database) {
            try {
                val endret = endreSaksnotat(
                    notatId = notatId,
                    ident = data.endretAvIdent,
                    tekst = data.tekst,
                    tidspunkt = data.tidspunkt,
                )
                if (endret) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = data.sakId,
                            soknadId = data.soknadId,
                            utfortAvRolle = data.endretAvRolle,
                            utfortAvIdent = data.endretAvIdent,
                            notat = "Notat endret",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                }
                idempotencyGuard.guard(event, endreSaksnotatSubtask)
                log.info("Endring av saksnotat {} for sakId={} behandlet, endret={}", notatId, data.sakId, endret)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved endring av saksnotat", e)
            }
        }
    }
}
