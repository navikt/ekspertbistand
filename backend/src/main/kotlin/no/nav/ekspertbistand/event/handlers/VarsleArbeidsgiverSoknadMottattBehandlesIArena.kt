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
import no.nav.ekspertbistand.event.domain.SoknadDomainV2
import no.nav.ekspertbistand.event.domain.tilDomain
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        val varsel = arbeidsgiverVarsel(
            soknadId = soknadId,
            virksomhet = event.data.soknad.virksomhet.tilDomain(),
            ansatt = event.data.soknad.ansatt.tilDomain(),
        )
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

    private fun arbeidsgiverVarsel(
        soknadId: String,
        virksomhet: SoknadDomainV2.Virksomhet,
        ansatt: SoknadDomainV2.Ansatt,
    ) = ArbeidsgiverVarselDomain(
        merkelapp = merkelapp,
        mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
        virksomhetsnummer = virksomhet.virksomhetsnummer,
        grupperingsid = soknadId,
        sak = ArbeidsgiverVarselDomain.Sak(
            tittel = "Ekspertbistand ${ansatt.navn} f. ${ansatt.fnr.tilFødselsdato()}",
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

fun String.tilFødselsdato(): String {
    if (length != 11) throw IllegalArgumentException("Fødselsnummer må være eksakt 11 tegn langt")
    return "${substring(0, 2)}.${substring(2, 4)}.${substring(4, 6)}"
}
