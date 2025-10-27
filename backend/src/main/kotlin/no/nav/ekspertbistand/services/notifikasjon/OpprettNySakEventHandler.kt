package no.nav.ekspertbistand.services.notifikasjon

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.skjema.DTO
import java.util.UUID
import kotlin.random.Random


class OpprettNySakEventHandler(
    private val produsentApiKlient: ProdusentApiKlient
) : EventHandler<Event.SkjemaInnsendt> {

    // DO NOT CHANGE THIS!
    override val id: String = "8642b600-2601-47e2-9798-5849bb362433"

    // må være suspending
    override fun handle(event: Event.SkjemaInnsendt): EventHandledResult {
        TODO()
    }

    suspend fun handle2(event: Event.SkjemaInnsendt): EventHandledResult {
        nySak(event.skjema).onFailure {
            return EventHandledResult.TransientError(it.message!!)
        }
        nyBeskjed(event.skjema).onFailure {
            return EventHandledResult.TransientError(it.message!!)
        }

        return EventHandledResult.Success()
    }

    private suspend fun nySak(skjema: DTO.Skjema): Result<String> {
        if (true) {// Har vi opprettet Beskjed for dette skjemaet allerede?
            return Result.success("Allerede opprettet sak for skjema ${skjema.id}")
        }
        return try {
            produsentApiKlient.opprettNySak(
                grupperingsid = UUID.randomUUID().toString(),
                merkelapp = "Ekspertbistand",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tittel = "Søknad om ekspertbistand",
                lenke = "https://ekspertbistand.nav.no/skjema/",
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: BeskjedOpprettetException) {
            Result.failure(ex)
        }
    }


    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        // Har vi opprettet sak for dette skjemaet allerede?
        if (true) {// Har vi opprettet Beskjed for dette skjemaet allerede?
            return Result.success("Allerede opprettet beskjed for skjema ${skjema.id}")
        }
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = UUID.randomUUID().toString(),
                merkelapp = "Ekspertbistand",
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Skjema innsendt",
                lenke = "https://ekspertbistand.nav.no/skjema/"
            )
            Result.success("Opprettet beskjed for skjema ${skjema.id}")
        } catch (ex: BeskjedOpprettetException) {
            Result.failure(ex)
        }
    }
}