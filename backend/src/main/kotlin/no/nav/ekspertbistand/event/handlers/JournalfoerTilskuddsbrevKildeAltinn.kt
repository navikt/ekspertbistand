package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.QueuedEvents
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Når en søknad om ekspertbistand godkjennes i Arena blir det opprettet et tilsagn.
 * Dette tilsagnet blir lagt på kafka og plukkes opp i [no.nav.ekspertbistand.arena.ArenaTilsagnsbrevProcessor]
 * som produserer en [no.nav.ekspertbistand.event.EventData.TilskuddsbrevMottattKildeAltinn]-event dersom vi ikke har en
 * registrert søknad på tilsagnet. Dette vil kun skje i overgangsperioden, der noen søknader har vært sendt inn via Altinn men ikke godkjent enda.
 *
 * Denne handleren tar imot eventen, genererer et tilskuddsbrev i PDF-format og
 * journalfører dette i DokArkiv.
 * Etter journalføring publiseres en ny event [no.nav.ekspertbistand.event.EventData.TilskuddsbrevJournalfoertKildeAltinn]
 * som inneholder informasjon om journalpostId og dokumentId.
 */
class JournalfoerTilskuddsbrevKildeAltinn(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
) : EventHandler<EventData.TilskuddsbrevMottattKildeAltinn> {
    private val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
    private val tittel = "Tilskuddsbrev ekspertbistand"
    override val id = "Journalfoer Tilskuddsbrev Søknad via Altinn"
    override val eventType = EventData.TilskuddsbrevMottattKildeAltinn::class
    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottattKildeAltinn>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserJournalpostEventSubtask)) {
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
        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)
        return EventHandledResult.Success()
    }
}