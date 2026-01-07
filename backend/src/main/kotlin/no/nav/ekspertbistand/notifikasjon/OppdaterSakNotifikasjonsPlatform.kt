package no.nav.ekspertbistand.notifikasjon

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.skjema.DTO

private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
private const val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"

class OppdaterSakNotifikasjonsPlatform(
    private val idempotencyGuard: IdempotencyGuard,
    private val produsentApiKlient: ProdusentApiKlient
) : EventHandler<EventData.TilskuddsbrevJournalfoert> {
    override val id: String = "OppdaterSakNotifikasjonsPlatform"
    override val eventType = EventData.TilskuddsbrevJournalfoert::class

    override suspend fun handle(event: Event<EventData.TilskuddsbrevJournalfoert>): EventHandledResult {
        val skjema = event.data.skjema
        if (skjema.id == null) {
            return EventHandledResult.UnrecoverableError("Skjema mangler id")
        }

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = {
                    return EventHandledResult.TransientError(
                        it.message ?: "Klarte ikke opprette ny beskjed i notifikasjonsplatform"
                    )
                }
            )
        }

        if (!idempotencyGuard.isGuarded(event.id, nystatusSakSubTast)) {
            nyStatusSak(skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nystatusSakSubTast) },
                onFailure = {
                    return EventHandledResult.TransientError(
                        it.message ?: "Klarte ikke oppdatere saksstatus i notifikasjonsplatform"
                    )
                }
            )
        }

        return EventHandledResult.Success()
    }

    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                skjemaId = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Nav har godkjent deres søknad om ekspertbistand.", // TODO: hva skal tittel her være?
                lenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/skjema/:id" //TODO: håndter// produksjonslink når prod er klart
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyStatusSak(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.nyStatusSak(
                skjemaId = skjema.id!!,
                status = SaksStatus.FERDIG,
                statusTekst = "Søknad godkjent"
            )
            Result.success("Oppdaterte sakstatus for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}