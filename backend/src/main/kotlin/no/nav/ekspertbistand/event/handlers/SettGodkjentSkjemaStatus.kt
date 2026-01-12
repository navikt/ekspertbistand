package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
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
) : EventHandler<EventData.TilskuddsbrevMottatt> {

    override val id = "Sett skjemastatus godkjent"
    override val eventType: KClass<EventData.TilskuddsbrevMottatt> = EventData.TilskuddsbrevMottatt::class

    private val logger = logger()

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottatt>): EventHandledResult {
        if (event.data.skjema.id == null) {
            throw RuntimeException("skjemaId er null")
        }

        return transaction(database) {
            try {
                val updates = SkjemaTable.update(
                    where = { SkjemaTable.id eq UUID.fromString(event.data.skjema.id) }) {
                    it[status] = SkjemaStatus.godkjent.toString()
                }
                if (updates == 1) {
                    logger.info("Skjema med id ${event.data.skjema.id} satt til godkjent.")
                    EventHandledResult.Success()
                } else if (updates == 0) {
                    EventHandledResult.UnrecoverableError("Forsøkte å oppdatere status for skjema med id ${event.data.skjema.id}, men finner ikke skjema i databasen.")
                } else {
                    rollback()
                    EventHandledResult.TransientError("Feil ved oppdatering av skjemastatus: Finner mer enn ett skjema med id ${event.data.skjema.id}")
                }

            } catch (e: Exception) {
                rollback()
                EventHandledResult.TransientError("Feil ved oppdatering av skjemastatus etter mottatt tilsagnsbrev: ${e.message}")
            }
        }
    }
}