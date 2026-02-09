package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.reflect.KClass

class SettGodkjentSoknadStatus(
    private val database: Database
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {

    override val id = "Sett soknadstatus godkjent"
    override val eventType: KClass<EventData.TilskuddsbrevJournalfoert> = EventData.TilskuddsbrevJournalfoert::class

    private val logger = logger()

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        checkNotNull(event.data.soknad.id) { "soknad.id kan ikke være null" }

        return transaction(database) {
            try {
                val updates = SoknadTable.update(
                    where = { SoknadTable.id eq UUID.fromString(event.data.soknad.id) }) {
                    it[status] = SoknadStatus.godkjent.toString()
                }
                if (updates == 0) {
                    unrecoverableError("Forsøkte å oppdatere status for søknad med id ${event.data.soknad.id}, men finner ikke søknad i databasen.")
                } else {
                    logger.info("søknad med id ${event.data.soknad.id} satt til godkjent.")
                    success()
                }

            } catch (e: Exception) {
                rollback()
                transientError("Feil ved oppdatering av søknadstatus etter mottatt tilsagnsbrev", e)
            }
        }
    }
}