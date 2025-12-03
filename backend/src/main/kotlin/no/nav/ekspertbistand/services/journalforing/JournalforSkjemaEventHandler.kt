package no.nav.ekspertbistand.services.journalforing

import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.services.IdempotencyGuard

class JournalforSkjemaEventHandler(
    private val dokgenClient: DokgenClient,
    private val dokArkivClient: DokArkivClient,
    private val idempotencyGuard: IdempotencyGuard,
) : EventHandler<EventData.SkjemaInnsendt> {

    // DO NOT CHANGE THIS!
    override val id: String = "6c0f0b05-0bf0-4d52-9c9f-10c13cb0e9bf"

    private val log = logger()

    override suspend fun handle(event: Event<EventData.SkjemaInnsendt>): EventHandledResult {
        val skjema = event.data.skjema
        val skjemaId = skjema.id ?: return EventHandledResult.UnrecoverableError(
            "Skjema mangler id og kan ikke journalføres"
        )

        val eksternReferanseId = "${skjemaId}-innsendt-skjema"

        val pdf = runCatching {
            dokgenClient.genererSoknadPdf(skjema)
        }.getOrElse { throwable ->
            log.warn("Generering av søknads-PDF feilet for skjema {}", skjemaId, throwable)
            return EventHandledResult.TransientError("Feil ved generering av PDF: ${throwable.message}")
        }

        val journalpost = runCatching {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = "Søknad om ekspertbistand fra ${skjema.ansatt.navn}",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                eksternReferanseId = eksternReferanseId,
                dokumentPdfAsBytes = pdf,
            )
        }.getOrElse { throwable ->
            log.warn("Opprettelse av journalpost feilet for skjema {}", skjemaId, throwable)
            return EventHandledResult.TransientError("Feil ved opprettelse av journalpost: ${throwable.message}")
        }

        if (!idempotencyGuard.isGuarded(event.id, JOURNALPOST_OPPRETTET_EVENT_SUBTASK)) {
            EventQueue.publish(
                EventData.JournalpostOpprettet(
                    skjemaId = skjemaId,
                    virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                    journalpostId = journalpost.journalpostId,
                    dokumentInfoId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId,
                    eksternReferanseId = eksternReferanseId,
                )
            )
            idempotencyGuard.guard(event, JOURNALPOST_OPPRETTET_EVENT_SUBTASK)
        }

        return EventHandledResult.Success()
    }

    companion object {
        private const val JOURNALPOST_OPPRETTET_EVENT_SUBTASK = "journalpost_opprettet_event"
    }
}
