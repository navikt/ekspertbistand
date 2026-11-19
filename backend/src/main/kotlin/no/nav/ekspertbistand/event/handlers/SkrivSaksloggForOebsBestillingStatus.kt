package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain.Status
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

/**
 * Skriver saksloggen når VALP melder status på en bestilling. Se
 * `specifications/oebs_status_til_saksgang.md`, 6.
 *
 * Statuser vi ikke har tatt stilling til ennå (annullering og oppgjør), gir [transientError] uten
 * idempotens-rad. Eventen prøves da på nytt til sakslogg for statusen er implementert.
 */
class SkrivSaksloggForOebsBestillingStatus(
    private val database: Database,
) : EventHandler<EventData.OebsBestillingStatusMottatt> {
    override val id: String = "Skriv sakslogg for status fra OeBS"
    override val eventType: KClass<EventData.OebsBestillingStatusMottatt> =
        EventData.OebsBestillingStatusMottatt::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val subtask = "sakslogg"

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.OebsBestillingStatusMottatt>): EventHandledResult {
        val bestilling = event.data.bestilling
        val notat = saksloggNotat(bestilling)
            ?: return transientError(
                "Sakslogg for status ${bestilling.status} er ikke implementert, bestilling ${bestilling.bestillingsnummer}"
            )

        return try {
            transaction(database) {
                if (idempotencyGuard.isGuarded(event.id, subtask)) {
                    return@transaction success()
                }
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = event.data.sakId,
                        soknadId = event.data.soknadId,
                        utfortAvRolle = AktorRolle.SYSTEM,
                        utfortAvIdent = null,
                        notat = notat,
                        tidspunkt = bestilling.statusSistOppdatert,
                    )
                )
                idempotencyGuard.guard(event, subtask)
                success()
            }
        } catch (e: Exception) {
            transientError("Feil ved publisering av sakslogg for status fra OeBS", e)
        }
    }
}

internal fun saksloggNotat(bestilling: OebsBestillingStatusDomain): String? {
    val nr = bestilling.bestillingsnummer
    return when (bestilling.status) {
        Status.SENDT -> "Bestilling til OeBS sendt: $nr"
        Status.AKTIV -> "Bestilling til OeBS opprettet: $nr"
        Status.FEILET -> buildString {
            append("Bestilling til OeBS feilet: $nr")
            bestilling.feilMelding?.let { append(". $it") }
            bestilling.feilKode?.let { append(" ($it)") }
        }

        Status.ANNULLERING_SENDT, Status.ANNULLERT, Status.OPPGJORT -> null
    }
}
