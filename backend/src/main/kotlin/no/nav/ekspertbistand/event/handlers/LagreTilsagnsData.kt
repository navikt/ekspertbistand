package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

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
                EventHandledResult.UnrecoverableError("skjemaId er null")
            }

            val tilsagnData = event.data.tilsagnData
            insertTilsagndata(UUID.fromString(skjemaId), tilsagnData)
            logger.info("Lagret tilsagndata for skjema med id $skjemaId")

            EventHandledResult.Success()
        }
    }
}
