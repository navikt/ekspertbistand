package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.tilsagndata.concat
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Etter at tilskuddsbrev er journalført, lagres tilsagnsdata i databasen.
 * Dette gjør at vi kan vise tilskuddsbrev til arbeidsgiver.
 */
class LagreTilsagnsDataKildeAltinn(
    private val database: Database,
) : EventHandler<EventData.TilskuddsbrevJournalfoertKildeAltinn> {
    override val id = "LagreTilsagnsData"
    override val eventType = EventData.TilskuddsbrevJournalfoertKildeAltinn::class

    private val logger = logger()

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoertKildeAltinn>): EventHandledResult {
        return transaction(database) {
            val tilsagnData = event.data.tilsagnData
            insertTilsagndata(null, tilsagnData)
            logger.info("Lagret tilsagndata for tilsagnnummer ${tilsagnData.tilsagnNummer.concat()} fra Altinn")

            success()
        }
    }
}
