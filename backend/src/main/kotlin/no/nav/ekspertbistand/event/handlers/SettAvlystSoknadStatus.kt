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
import no.nav.ekspertbistand.soknad.slettSøknadOm
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SettAvlystSoknadStatus @OptIn(ExperimentalTime::class) constructor(
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.SoknadAvlystIArena> {

    override val id = "Sett soknadstatus avlyst"
    override val eventType = EventData.SoknadAvlystIArena::class

    private val logger = logger()


    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SoknadAvlystIArena>): EventHandledResult {
        if (event.data.soknad.id == null) {
            throw RuntimeException("soknad.id er null")
        }

        return transaction(database) {
            try {
                val updates = SoknadTable.update(
                    where = { SoknadTable.id eq UUID.fromString(event.data.soknad.id) }) {
                    it[status] = SoknadStatus.avlyst.toString()
                    it[sletteTidspunkt] = clock.now().plus(slettSøknadOm)
                }
                if (updates == 0) {
                    unrecoverableError("Forsøkte å oppdatere status for soknad med id ${event.data.soknad.id}, men finner ikke soknad i databasen.")
                } else {
                    logger.info("Soknad med id ${event.data.soknad.id} satt til godkjent.")
                    success()
                }

            } catch (e: Exception) {
                rollback()
                transientError("Feil ved oppdatering av soknadstatus etter mottatt tilsagnsbrev", e)
            }
        }
    }
}