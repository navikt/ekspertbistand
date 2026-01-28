package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Etter at tilskuddsbrev er journalført, lagres tilsagnsdata i databasen.
 * Dette gjør at vi kan vise tilskuddsbrev til arbeidsgiver.
 */
class LagreTilsagnsData(
    private val database: Database,
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {
    override val id = "LagreTilsagnsData"
    override val eventType = EventData.TilskuddsbrevJournalfoert::class

    private val logger = logger()

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        return transaction(database) {
            val skjemaId = event.data.skjema.id
            if (skjemaId == null) {
                unrecoverableError("skjemaId er null")
            } else {
                val tilsagnData = event.data.tilsagnData
                insertTilsagndata(UUID.fromString(skjemaId), tilsagnData)
                logger.info("Lagret tilsagndata for skjema med id $skjemaId")

                QueuedEvents.insert {
                    it[eventData] = EventData.TilsagnsdataLagret(
                        skjema = event.data.skjema,
                        tilsagnData = event.data.tilsagnData
                    )
                }

                success()
            }
        }
    }
}
