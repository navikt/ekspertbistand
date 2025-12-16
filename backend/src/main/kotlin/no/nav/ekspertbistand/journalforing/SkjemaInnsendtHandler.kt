package no.nav.ekspertbistand.journalforing

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.QueuedEvents
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

class SkjemaInnsendtHandler(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
) : EventHandler<EventData.SkjemaInnsendt> {
    override val id: String = "SkjemaInnsendtHandler"
    override val eventType = EventData.SkjemaInnsendt::class

    override suspend fun handle(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
        val skjema = event.data.skjema

        val pdf = try {
            dokgenClient.genererSoknadPdf(skjema)
        } catch (e: Exception) {
            return EventHandledResult.TransientError("Feil ved generering av PDF: ${e.message}")
        }

        val journalpost = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = "Søknad om ekspertbistand",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                eksternReferanseId = skjema.id ?: "skjema-${event.id}",
                dokumentPdfAsBytes = pdf,
            )
        } catch (e: Exception) {
            return EventHandledResult.TransientError("Feil ved oppretting av journalpost: ${e.message}")
        }

        val dokumentInfoId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv returnerte ingen dokumentInfoId")

        val journalpostId = journalpost.journalpostId.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv returnerte ugyldig journalpostId")


        transaction(database) {
            QueuedEvents.insert {
                it[eventData] = EventData.JournalpostOpprettet(
                    skjema = skjema,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                    behandlendeEnhetId = "1899",
                )
            }
        }

        return EventHandledResult.Success()
    }
}