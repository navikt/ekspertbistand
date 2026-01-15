package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.skjema.SkjemaStatus
import no.nav.ekspertbistand.skjema.SkjemaTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.reflect.KClass

class SettGodkjentSkjemaStatus(
    private val database: Database
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {

    override val id = "Sett skjemastatus godkjent"
    override val eventType: KClass<EventData.TilskuddsbrevJournalfoert> = EventData.TilskuddsbrevJournalfoert::class

    private val logger = logger()

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        if (event.data.skjema.id == null) {
            throw RuntimeException("skjemaId er null")
        }

        return transaction(database) {
            try {
                val updates = SkjemaTable.update(
                    where = { SkjemaTable.id eq UUID.fromString(event.data.skjema.id) }) {
                    it[status] = SkjemaStatus.godkjent.toString()
                }
                if (updates == 0) {
                    unrecoverableError("Forsøkte å oppdatere status for skjema med id ${event.data.skjema.id}, men finner ikke skjema i databasen.")
                } else {
                    logger.info("Skjema med id ${event.data.skjema.id} satt til godkjent.")
                    success()
                }

            } catch (e: Exception) {
                rollback()
                transientError("Feil ved oppdatering av skjemastatus etter mottatt tilsagnsbrev: ${e.message}", e)
            }
        }
    }
}