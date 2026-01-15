package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database

private const val nySakSubTask = "notifikasjonsplatform_ny_sak"
private const val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

/**
 * Når vi mottar en søknad så oppretter vi en tiltaksgjennomføring i Arena.
 * Dette resulterer i eventen [no.nav.ekspertbistand.event.EventData.TiltaksgjennomføringOpprettet]
 *
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen om at søknad er mottatt og under behandling.
 */
class VarsleArbeidsgiverOmMottattSoknad(
    private val produsentApiKlient: ProdusentApiKlient,
    database: Database
) : EventHandler<EventData.TiltaksgjennomføringOpprettet> {

    private val idempotencyGuard = idempotencyGuard(database)

    override val id: String = "OpprettSakNotifikasjonPlatform"
    override val eventType = EventData.TiltaksgjennomføringOpprettet::class

    override suspend fun handle(event: Event<EventData.TiltaksgjennomføringOpprettet>): EventHandledResult {
        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            nySak(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nySakSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av sak i notifikasjonsplatform", it) }
            )
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            nyBeskjed(event.data.skjema).fold(
                onSuccess = { idempotencyGuard.guard(event, nyBeskjedSubTask) },
                onFailure = { return transientError("Feil ved opprettelse av beskjed i notifikasjonsplatform", it) }
            )
        }

        return success()
    }

    private suspend fun nySak(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNySak(
                skjemaId = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tittel = "Ekspertbistand ${skjema.ansatt.navn} f. ${skjema.ansatt.fnr.tilFødselsdato()}",
                lenke = skjema.kvitteringsLenke,
            )
            Result.success("Opprettet sak for skjema ${skjema.id}")
        } catch (ex: Exception) {
            Result.failure(ex)
        }
    }

    private suspend fun nyBeskjed(skjema: DTO.Skjema): Result<String> {
        return try {
            produsentApiKlient.opprettNyBeskjed(
                skjemaId = skjema.id!!,
                virksomhetsnummer = skjema.virksomhet.virksomhetsnummer,
                tekst = "Nav har mottatt deres søknad om ekspertbistand.",
                lenke = skjema.kvitteringsLenke,
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