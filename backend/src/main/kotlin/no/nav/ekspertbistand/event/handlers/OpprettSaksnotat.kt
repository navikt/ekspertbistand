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
import no.nav.ekspertbistand.saksbehandling.lagreSaksnotat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val opprettSaksnotatSubtask = "saksnotat_opprettet"

/**
 * Lagrer notatet i [EventData.SaksnotatOpprettet] og publiserer [EventData.SakOppdatert] («Notat opprettet»)
 * til saksloggen. Finnes notatet fra før, skjer ingenting. Se `specifications/notat_i_saken.md`.
 */
class OpprettSaksnotat(
    private val database: Database,
) : EventHandler<EventData.SaksnotatOpprettet> {
    override val id: String = "Opprett saksnotat"
    override val eventType: KClass<EventData.SaksnotatOpprettet> = EventData.SaksnotatOpprettet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SaksnotatOpprettet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, opprettSaksnotatSubtask)) {
            return success()
        }

        val data = event.data
        val (notatId, sakId) = try {
            UUID.fromString(data.notatId) to UUID.fromString(data.sakId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig notatId eller sakId i SaksnotatOpprettet")
        }

        return transaction(database) {
            try {
                val lagret = lagreSaksnotat(
                    notatId = notatId,
                    sakId = sakId,
                    tekst = data.tekst,
                    rolle = data.skrevetAvRolle,
                    ident = data.skrevetAvIdent,
                    navn = data.skrevetAvNavn,
                    tidspunkt = data.tidspunkt,
                )
                if (lagret) {
                    publishEventQueue(
                        EventData.SakOppdatert(
                            sakId = data.sakId,
                            soknadId = data.soknadId,
                            utfortAvRolle = data.skrevetAvRolle,
                            utfortAvIdent = data.skrevetAvIdent,
                            notat = "Notat opprettet",
                            tidspunkt = data.tidspunkt,
                        )
                    )
                }
                idempotencyGuard.guard(event, opprettSaksnotatSubtask)
                log.info("Saksnotat {} for sakId={} behandlet, lagret={}", notatId, sakId, lagret)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved lagring av saksnotat", e)
            }
        }
    }
}
