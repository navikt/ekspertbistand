package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

class SkrivSaksloggForRefusjonskrav(
    private val database: Database,
) : EventHandler<EventData.RefusjonskravMottatt> {
    override val id: String = "Skriv sakslogg for refusjonskrav"
    override val eventType: KClass<EventData.RefusjonskravMottatt> = EventData.RefusjonskravMottatt::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val subtask = "sakslogg"

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.RefusjonskravMottatt>): EventHandledResult {
        return try {
            transaction(database) {
                if (idempotencyGuard.isGuarded(event.id, subtask)) {
                    return@transaction success()
                }
                val data = event.data
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfortAvRolle = AktorRolle.SYSTEM,
                        utfortAvIdent = null,
                        notat = "Refusjonskrav mottatt fra arbeidsgiver",
                        tidspunkt = data.tidspunkt,
                    )
                )
                idempotencyGuard.guard(event, subtask)
                success()
            }
        } catch (e: Exception) {
            transientError("Feil ved publisering av sakslogg for refusjonskrav", e)
        }
    }
}
