package no.nav.ekspertbistand.dokarkiv

import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserJournalpostEventSubtask = "journalpost_opprettet_event"
private const val tittel = "Søknad om ekspertbistand godkjent" //TODO: Hva skal denne være?

class TiltaksgjennomføringMottattHandler(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val database: Database,
    private val idempotencyGuard: IdempotencyGuard,
) : EventHandler<EventData.TilskuddsbrevMottatt> {
    override val id: String = "TiltaksgjennomføringMottattHandler"
    override val eventType: KClass<EventData.TilskuddsbrevMottatt> =
        EventData.TilskuddsbrevMottatt::class

    override suspend fun handle(event: Event<EventData.TilskuddsbrevMottatt>): EventHandledResult {
        val skjema = event.data.skjema
        val skjemaId = skjema.id ?: return EventHandledResult.UnrecoverableError("Skjema mangler id")

        val soknadPdf = try {
            dokgenClient.genererSoknadPdf(skjema) //TODO: dette skal vel være tilsagn?
        } catch (e: Exception) {
            return EventHandledResult.TransientError("Klarte ikke generere søknad-PDF: ${e.message}")
        }

        val journalpostResponse = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                eksternReferanseId = skjemaId,
                dokumentPdfAsBytes = soknadPdf,
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
                it[eventData] = EventData.TilskuddsbrevJournalfoert(
                    skjema = skjema,
                    dokumentId = dokumentInfoId,
                    journaldpostId = journalpostId,
                )
            }
        }
        idempotencyGuard.guard(event, publiserJournalpostEventSubtask)
        return EventHandledResult.Success()
    }
}
