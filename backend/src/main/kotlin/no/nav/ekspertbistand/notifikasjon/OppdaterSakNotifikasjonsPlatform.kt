package no.nav.ekspertbistand.notifikasjon

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.skjema.DTO

private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

class OppdaterSakNotifikasjonsPlatform(
    private val idempotencyGuard: IdempotencyGuard,
    private val produsentApiKlient: ProdusentApiKlient
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {
    override val id: String = "OppdaterSakNotifikasjonsPlatform"
    override val eventType = EventData.TilskuddsbrevJournalfoert::class

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            return EventHandledResult.Success()
        }

        val skjema = event.data.skjema
        if (skjema.id == null) {
            return EventHandledResult.UnrecoverableError("Skjema mangler id")
        }

        nyBeskjed(skjema).fold(
            onSuccess = { return EventHandledResult.Success() },
            onFailure = {
                return EventHandledResult.TransientError(
                    it.message ?: "Klarte ikke opprette ny beskjed i notifikasjonsplatform"
                )
            }
        )
    }

    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Nav har godkjent deres søknad om ekspertbistand.", // TODO: hva skal tittel her være?
                lenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/skjema/:id" //TODO: håndter// produksjonslink når prod er klart
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}