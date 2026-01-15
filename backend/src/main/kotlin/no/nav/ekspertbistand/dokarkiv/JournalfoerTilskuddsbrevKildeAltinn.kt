package no.nav.ekspertbistand.dokarkiv

import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "jtka_journalpost_opprettet_event"
private const val tittel = "Tilskuddsbrev ekspertbistand"

class JournalfoerTilskuddsbrevKildeAltinn(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
    private val idempotencyGuard: IdempotencyGuard,
) : EventHandler<EventData.TilskuddsbrevMottattKildeAltinn> {
    override val id = "JournalfoerTilskuddsbrev"
    override val eventType = EventData.TilskuddsbrevMottattKildeAltinn::class

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottattKildeAltinn>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
            return EventHandledResult.Success()
        }

        val tilsagnPdf = try {
            dokgenClient.genererTilskuddsbrevPdf(event.data.tilsagnData)
        } catch (e: Exception) {
            return EventHandledResult.TransientError("Klarte ikke generere søknad-PDF: ${e.message}")
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = event.data.tilsagnData.tiltakArrangor.orgNummer.toString(),
                eksternReferanseId = event.data.tilsagnData.tilsagnNummer.let { "${it.aar}${it.loepenrSak}-${it.loepenrTilsagn}" },
                dokumentPdfAsBytes = tilsagnPdf,
            )
        } catch (e: Exception) {
            return EventHandledResult.TransientError("Feil ved opprettelse av journalpost: ${e.message}")
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return EventHandledResult.TransientError("Journalpost ikke ferdigstilt")
        }

        val dokumentInfoId = journalpostResponse.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv mangler dokumentInfoId")
        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return EventHandledResult.UnrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            QueuedEvents.insert {
                it[eventData] = EventData.TilskuddsbrevJournalfoertKildeAltinn(
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                )
            }
        }
        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)
        return EventHandledResult.Success()
    }
}
