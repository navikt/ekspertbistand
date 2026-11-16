package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Når vi har opprettet sak for en søknad, publiseres [EventData.SakOpprettet].
 *
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen om at søknad er mottatt
 * og under behandling. Varselet er identisk med det [VarsleArbeidsgiverSoknadMottattBehandlesIArena]
 * sender for saker som behandles i Arena. Når begge kallene har lyktes, publiseres
 * [EventData.ArbeidsgiverVarsletSoknadMottatt] med det som ble sendt.
 */
class VarsleArbeidsgiverSakOpprettet @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.SakOpprettet> {

    override val id: String = "VarsleArbeidsgiverSakOpprettet"
    override val eventType = EventData.SakOpprettet::class

    private val nySakSubTask = "notifikasjonsplatform_ny_sak"
    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val kvittertSubTask = "arbeidsgiver_varslet_kvittert"

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SakOpprettet>): EventHandledResult {
        val soknadId = event.data.soknad.id ?: return unrecoverableError("soknad.id kan ikke være null")
        val varsel = arbeidsgiverVarselSoknadMottatt(event.data.soknad)

        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            try {
                produsentApiKlient.bestillSak(varsel)
            } catch (e: Exception) {
                return transientError("Feil ved opprettelse av sak i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nySakSubTask)
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            try {
                produsentApiKlient.bestillBeskjed(varsel)
            } catch (e: Exception) {
                return transientError("Feil ved opprettelse av beskjed i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nyBeskjedSubTask)
        }

        return publiserKvittering(idempotencyGuard, database, event, kvittertSubTask) {
            publishEventQueue(
                EventData.ArbeidsgiverVarsletSoknadMottatt(
                    soknadId = soknadId,
                    varsel = varsel,
                    tidspunkt = clock.now(),
                ),
            )
        }
    }
}
