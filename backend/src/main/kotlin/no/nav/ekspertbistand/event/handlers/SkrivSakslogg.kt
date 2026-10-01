package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.SaksloggTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val skrivSaksloggSubtask = "sakslogg_skrevet"

/**
 * Skriver [EventData.SakOppdatert] til `sakslogg`, slik at hendelsen vises i saksloggen.
 *
 * Loggposten og idempotens-raden skrives i samme transaksjon, slik at en retry aldri gir
 * dobbel loggpost. Se `specifications/sakslogg.md`.
 */
class SkrivSakslogg(
    private val database: Database,
) : EventHandler<EventData.SakOppdatert> {
    override val id: String = "Skriv sakslogg"
    override val eventType: KClass<EventData.SakOppdatert> = EventData.SakOppdatert::class

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SakOppdatert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, skrivSaksloggSubtask)) {
            return success()
        }

        val data = event.data
        val sakId = try {
            UUID.fromString(data.sakId)
        } catch (_: IllegalArgumentException) {
            return unrecoverableError("Ugyldig sakId i SakOppdatert")
        }

        return try {
            transaction(database) {
                val sakFinnes = SakTable.select(SakTable.sakId)
                    .where { SakTable.sakId eq sakId }
                    .empty().not()
                if (!sakFinnes) {
                    return@transaction unrecoverableError("Fant ikke sak $sakId for SakOppdatert")
                }

                SaksloggTable.insert {
                    it[SaksloggTable.sakId] = sakId
                    it[utfortAvRolle] = data.utfortAvRolle.name
                    it[utfortAvIdent] = data.utfortAvIdent
                    it[notat] = data.notat
                    it[utfortAt] = data.tidspunkt
                }
                idempotencyGuard.guard(event, skrivSaksloggSubtask)
                success()
            }
        } catch (e: Exception) {
            transientError("Feil ved skriving til sakslogg", e)
        }
    }
}
