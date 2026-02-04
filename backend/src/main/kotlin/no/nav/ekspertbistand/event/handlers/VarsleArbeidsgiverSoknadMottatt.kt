package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database


/**
 * Når vi mottar en søknad så oppretter vi en tiltaksgjennomføring i Arena.
 * Dette resulterer i eventen [no.nav.ekspertbistand.event.EventData.TiltaksgjennomforingOpprettet]
 *
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen om at søknad er mottatt og under behandling.
 */
class VarsleArbeidsgiverSoknadMottatt(
    private val produsentApiKlient: ProdusentApiKlient,
    database: Database
) : EventHandler<EventData.TiltaksgjennomforingOpprettet> {

    override val id: String = "VarsleArbeidsgiverSoknadMottatt"
    override val eventType = EventData.TiltaksgjennomforingOpprettet::class

    private val nySakSubTask = "notifikasjonsplatform_ny_sak"
    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.TiltaksgjennomforingOpprettet>): EventHandledResult {
        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            nySak(event.data.soknad).fold(
                onSuccess = { idempotencyGuard.guard(event, nySakSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av sak i notifikasjonsplatform", it) }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(event.data.soknad).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av beskjed i notifikasjonsplatform", it) }
            )
        }

        return success()
    }

    private suspend fun nySak(soknad: DTO.Soknad): Result<String> {
        return try {
            produsentApiKlient.opprettNySak(
                grupperingsid = soknad.id!!,
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                tittel = "Ekspertbistand ${soknad.ansatt.navn} f. ${soknad.ansatt.fnr.tilFødselsdato()}",
                lenke = soknad.kvitteringsLenke,
            )
            Result.success("Opprettet sak for soknad ${soknad.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyBeskjed(soknad: DTO.Soknad): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                grupperingsid = soknad.id!!,
                eksternId = "${soknad.id}-soknad-mottatt",
                virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                tekst = "Nav har mottatt deres søknad om ekspertbistand.",
                lenke = soknad.kvitteringsLenke,
            )
            Result.success("Opprettet beskjed for soknad ${soknad.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }
}

fun String.tilFødselsdato(): String {
    if (length != 11) throw IllegalArgumentException("Fødselsnummer må være eksakt 11 tegn langt")
    return "${substring(0, 2)}.${substring(2, 4)}.${substring(4, 6)}"
}