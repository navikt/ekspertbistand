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
import no.nav.ekspertbistand.saksbehandling.slettSaksnotat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val slettSaksnotatSubtask = "saksnotat_slettet"

/**
 * Sletter notatet i [EventData.SaksnotatSlettet] og publiserer [EventData.SakOppdatert] («Notat slettet»)
 * til saksloggen, men bare når notatet finnes og er skrevet av [EventData.SaksnotatSlettet.slettetAvIdent].
 * Se `specifications/notat_i_saken.md`.
 */
class SlettSaksnotat(
    private val database: Database,
) : EventHandler<EventData.SaksnotatSlettet> {
    override val id: String = "Slett saksnotat"
    override val eventType: KClass<EventData.SaksnotatSlettet> = EventData.SaksnotatSlettet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SaksnotatSlettet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, slettSaksnotatSubtask)) {
            return success()
        }

        val data = event.data
        val notatId = try {
            UUID.fromString(data.notatId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig notatId i SaksnotatSlettet")
        }

        return transaction(database) {
            try {
                val slettet = slettSaksnotat(notatId = notatId, ident = data.slettetAvIdent)
                if (slettet) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = data.sakId,
                            soknadId = data.soknadId,
                            utfortAvRolle = data.slettetAvRolle,
                            utfortAvIdent = data.slettetAvIdent,
                            notat = "Notat slettet",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                }
                idempotencyGuard.guard(event, slettSaksnotatSubtask)
                log.info("Sletting av saksnotat {} for sakId={} behandlet, slettet={}", notatId, data.sakId, slettet)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved sletting av saksnotat", e)
            }
        }
    }
}
