package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.KvitteringPublisert
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


/**
 * Når vi har opprettet sak for en søknad, publiseres [EventData.SakOpprettet].
 *
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen om at søknad er mottatt
 * og under behandling. Varselet er identisk med det [VarsleArbeidsgiverSoknadMottattBehandlesIAltinn]
 * sender for saker som behandles i Arena.
 * Når alt har lyktes, publiserer handleren [EventData.ArbeidsgiverVarslet] som kvittering,
 * men bare hvis [no.nav.ekspertbistand.event.KvitteringPublisert] ikke har nøkkelen fra før.
 */
class VarsleArbeidsgiverSakOpprettet(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database
) : EventHandler<EventData.SakOpprettet> {

    override val id: String = "VarsleArbeidsgiverSakOpprettet"
    override val eventType = EventData.SakOpprettet::class

    private val nySakSubTask = "notifikasjonsplatform_ny_sak"
    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.SakOpprettet>): EventHandledResult {
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

        transaction(database) {
            val soknadId = event.data.soknad.id!!
            val nyKvittering = KvitteringPublisert.registrer(
                nokkel = KvitteringPublisert.soknadMottattNokkel(soknadId),
                soknadId = soknadId,
                type = KvitteringPublisert.Type.ARBEIDSGIVER_VARSLET_SOKNAD_MOTTATT,
            )
            if (nyKvittering) {
                publishEventQueue(
                    EventData.ArbeidsgiverVarslet(
                        soknadId = soknadId,
                        varsel = EventData.ArbeidsgiverVarslet.Varsel.SOKNAD_MOTTATT
                    )
                )
            }
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
                eksternId = KvitteringPublisert.soknadMottattNokkel(soknad.id),
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
