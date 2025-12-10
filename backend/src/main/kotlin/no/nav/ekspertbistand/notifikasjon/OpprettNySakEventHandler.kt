package no.nav.ekspertbistand.notifikasjon

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.skjema.DTO

private const val nySakSubTask = "notifikasjonsplatform_ny_sak"
private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

class OpprettNySakEventHandler(
    private val produsentApiKlient: ProdusentApiKlient,
    private val idempotencyGuard: IdempotencyGuard
) : EventHandler<EventData.TiltaksgjennomføringOpprettet> {

    // DO NOT CHANGE THIS!
    override val id: String =
        "8642b600-2601-47e2-9798-5849bb362433" //TODO: Skulle denne være en readable id? Kan dette endres nå?

    override suspend fun handle(event: Event<EventData.TiltaksgjennomføringOpprettet>): EventHandledResult {
        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            nySak(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nySakSubTask) },
                onFailure = { return EventHandledResult.TransientError(it.message!!) }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = { return EventHandledResult.TransientError(it.message!!) }
            )
        }

        return EventHandledResult.Success()
    }

    private suspend fun nySak(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNySak(
                grupperingsid = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tittel = "Ekspertbistand ${skjema.ansatt.navn} f. ${skjema.ansatt.fnr.tilFødselsdato()}",
                lenke = ""
            )
            Result.success("Opprettet sak for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }


    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Nav har mottatt deres søknad om ekspertbistand.",
                lenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/skjema/:id" //TODO: håndter// produksjonslink når prod er klart
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}

private fun String.tilFødselsdato(): String {
    if (length != 11) throw IllegalArgumentException("Fødselsnummer må være eksakt 11 tegn langt")
    return "${substring(0, 2)}.${substring(2, 4)}.${substring(4, 6)}"
}