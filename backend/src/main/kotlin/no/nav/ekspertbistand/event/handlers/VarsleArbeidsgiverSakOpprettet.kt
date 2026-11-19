package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        val varsel = arbeidsgiverVarsel(soknadId, event.data)
        val sak = varsel.sak ?: return unrecoverableError("Varselet om mottatt søknad mangler sak")

        if (!idempotencyGuard.isGuarded(event.id, nySakSubTask)) {
            try {
                produsentApiKlient.opprettNySak(
                    grupperingsid = varsel.grupperingsid,
                    virksomhetsnummer = varsel.virksomhetsnummer,
                    tittel = sak.tittel,
                    lenke = sak.lenke,
                )
            } catch (e: Exception) {
                return transientError("Feil ved opprettelse av sak i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nySakSubTask)
        }
        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            try {
                produsentApiKlient.opprettNyBeskjed(
                    grupperingsid = varsel.grupperingsid,
                    eksternId = varsel.beskjed.eksternId,
                    virksomhetsnummer = varsel.virksomhetsnummer,
                    tekst = varsel.beskjed.tekst,
                    lenke = varsel.beskjed.lenke,
                )
            } catch (e: Exception) {
                return transientError("Feil ved opprettelse av beskjed i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nyBeskjedSubTask)
        }

        if (idempotencyGuard.isGuarded(event.id, kvittertSubTask)) {
            return success()
        }
        return transaction(database) {
            try {
                publishEventQueue(
                    EventData.ArbeidsgiverVarsletSoknadMottatt(
                        soknadId = soknadId,
                        varsel = varsel,
                        tidspunkt = clock.now(),
                    ),
                )
                idempotencyGuard.guard(event, kvittertSubTask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved publisering etter varsling av arbeidsgiver om mottatt søknad", e)
            }
        }
    }

    private fun arbeidsgiverVarsel(soknadId: String, data: EventData.SakOpprettet) = ArbeidsgiverVarselDomain(
        merkelapp = merkelapp,
        mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
        virksomhetsnummer = data.soknad.virksomhet.virksomhetsnummer,
        grupperingsid = soknadId,
        sak = ArbeidsgiverVarselDomain.Sak(
            tittel = "Ekspertbistand ${data.soknad.ansatt.navn} f. ${data.soknad.ansatt.fnr.tilFødselsdato()}",
            lenke = kvitteringsLenke(soknadId),
        ),
        beskjed = ArbeidsgiverVarselDomain.Beskjed(
            eksternId = "$soknadId-soknad-mottatt",
            tekst = "Nav har mottatt deres søknad om ekspertbistand.",
            lenke = kvitteringsLenke(soknadId),
            eksterntVarsel = null,
        ),
        saksstatus = null,
    )
}
