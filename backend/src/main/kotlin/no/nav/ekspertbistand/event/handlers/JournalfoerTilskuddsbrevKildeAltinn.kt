package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.QueuedEvents
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class JournalfoerTilskuddsbrevKildeAltinn(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
) : EventHandler<EventData.TilskuddsbrevMottattKildeAltinn> {
    private val jtkapubliserJournalpostEventSubtask = "jtka_journalpost_opprettet_event"
    private val tittel = "Tilskuddsbrev ekspertbistand"
    override val id = "JournalfoerTilskuddsbrev"
    override val eventType = EventData.TilskuddsbrevMottattKildeAltinn::class
    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottattKildeAltinn>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, jtkapubliserJournalpostEventSubtask)) {
            return EventHandledResult.Success()
        }

        val tilsagnPdf = try {
            dokgenClient.genererTilskuddsbrevPdf(event.data.tilsagnData)
        } catch (e: Exception) {
            return transientError("Klarte ikke generere søknad-PDF: ${e.message}", e)
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = event.data.tilsagnData.tiltakArrangor.orgNummer.toString(),
                eksternReferanseId = event.data.tilsagnData.tilsagnNummer.let { "${it.aar}${it.loepenrSak}-${it.loepenrTilsagn}" },
                dokumentPdfAsBytes = tilsagnPdf,
            )
        } catch (e: Exception) {
            return transientError("Feil ved opprettelse av journalpost: ${e.message}", e)
        }

        if (!journalpostResponse.journalpostferdigstilt) {
            return transientError("Journalpost ikke ferdigstilt")
        }

        val dokumentInfoId = journalpostResponse.dokumenter.firstOrNull()?.dokumentInfoId?.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler dokumentInfoId")
        val journalpostId = journalpostResponse.journalpostId.toIntOrNull()
            ?: return unrecoverableError("DokArkiv mangler gyldig journalpostId")

        transaction(database) {
            QueuedEvents.insert {
                it[eventData] = EventData.TilskuddsbrevJournalfoertKildeAltinn(
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                )
            }
        }
        idempotencyGuard.guard(event, jtkapubliserJournalpostEventSubtask)
        return EventHandledResult.Success()
    }
}