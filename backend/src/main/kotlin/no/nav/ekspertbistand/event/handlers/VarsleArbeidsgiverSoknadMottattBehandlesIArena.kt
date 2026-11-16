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
 * Når vi mottar en søknad så oppretter vi en tiltaksgjennomføring i Arena.
 * Dette resulterer i eventen [no.nav.ekspertbistand.event.EventData.TiltaksgjennomforingOpprettet]
 *
 * Denne handleren oppretter da en sak og en beskjed i notifikasjonsplatformen om at søknad er mottatt og under
 * behandling. Når begge kallene har lyktes, publiseres [EventData.ArbeidsgiverVarsletSoknadMottatt] med det som ble
 * sendt.
 */
class VarsleArbeidsgiverSoknadMottattBehandlesIArena @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.TiltaksgjennomforingOpprettet> {

    override val id: String = "VarsleArbeidsgiverSoknadMottatt"
    override val eventType = EventData.TiltaksgjennomforingOpprettet::class

    private val nySakSubTask = "notifikasjonsplatform_ny_sak"
    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val kvittertSubTask = "arbeidsgiver_varslet_kvittert"

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.TiltaksgjennomforingOpprettet>): EventHandledResult {
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

fun String.tilFødselsdato(): String {
    if (length != 11) throw IllegalArgumentException("Fødselsnummer må være eksakt 11 tegn langt")
    return "${substring(0, 2)}.${substring(2, 4)}.${substring(4, 6)}"
}
