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
import no.nav.ekspertbistand.event.domain.tilSaksStatus
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.notifikasjon.sendevindu
import no.nav.ekspertbistand.soknad.kvitteringsLenke
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Når tiltaksgjennomføringen er satt til AVLYST i Arena, publiseres [EventData.SoknadAvlystIArena].
 *
 * Denne handleren sender da en beskjed med eksternt varsel og setter saken til ferdig i notifikasjonsplatformen.
 * Når begge kallene har lyktes, publiseres [EventData.ArbeidsgiverVarsletSoknadAvlyst] med det som ble sendt.
 */
class VarsleArbeidsgiverSoknadAvlyst @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.SoknadAvlystIArena> {
    override val id: String = "Varsle Arbeidsgiver Soknad Avlyst"
    override val eventType = EventData.SoknadAvlystIArena::class

    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"
    private val kvittertSubTask = "arbeidsgiver_varslet_kvittert"

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SoknadAvlystIArena>): EventHandledResult {
        val soknadId = event.data.soknad.id ?: return unrecoverableError("soknad.id kan ikke være null")
        val varsel = arbeidsgiverVarsel(
            soknadId = soknadId,
            virksomhet = event.data.soknad.virksomhet.tilDomain(),
        )
        val saksstatus = varsel.saksstatus ?: return unrecoverableError("Varselet om avlyst søknad mangler saksstatus")

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            try {
                produsentApiKlient.opprettNyBeskjed(
                    grupperingsid = varsel.grupperingsid,
                    eksternId = varsel.beskjed.eksternId,
                    virksomhetsnummer = varsel.virksomhetsnummer,
                    tekst = varsel.beskjed.tekst,
                    lenke = varsel.beskjed.lenke,
                    eksternVarsel = varsel.beskjed.eksterntVarsel?.let {
                        EksterntVarsel(
                            epostTittel = it.epostTittel,
                            epostHtmlBody = it.epostHtmlBody,
                            smsTekst = it.smsTekst,
                        )
                    },
                )
            } catch (e: Exception) {
                return transientError("Klarte ikke opprette ny beskjed i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nyBeskjedSubTask)
        }

        if (!idempotencyGuard.isGuarded(event.id, nystatusSakSubTast)) {
            try {
                produsentApiKlient.nyStatusSak(
                    grupperingsid = varsel.grupperingsid,
                    status = saksstatus.status.tilSaksStatus(),
                    statusTekst = saksstatus.statusTekst,
                    nyLenkeTilSak = saksstatus.nyLenkeTilSak,
                )
            } catch (e: Exception) {
                return transientError("Klarte ikke oppdatere saksstatus i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nystatusSakSubTast)
        }

        if (idempotencyGuard.isGuarded(event.id, kvittertSubTask)) {
            return success()
        }
        return transaction(database) {
            try {
                publishEventQueue(
                    EventData.ArbeidsgiverVarsletSoknadAvlyst(
                        soknadId = soknadId,
                        varsel = varsel,
                        tidspunkt = clock.now(),
                    ),
                )
                idempotencyGuard.guard(event, kvittertSubTask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved publisering etter varsling av arbeidsgiver om avlyst søknad", e)
            }
        }
    }

    private fun arbeidsgiverVarsel(soknadId: String, virksomhet: SoknadDomainV2.Virksomhet): ArbeidsgiverVarselDomain {
        val eksterntVarselTekst = "${virksomhet.virksomhetsnavn} har fått svar på en søknad om ekspertbistand. " +
            "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
        return ArbeidsgiverVarselDomain(
            merkelapp = merkelapp,
            mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
            virksomhetsnummer = virksomhet.virksomhetsnummer,
            grupperingsid = soknadId,
            sak = null,
            beskjed = ArbeidsgiverVarselDomain.Beskjed(
                eksternId = "$soknadId-soknad-avlyst",
                tekst = "Søknaden om ekspertbistand trukket eller avslått.",
                lenke = kvitteringsLenke(soknadId),
                eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
                    epostTittel = "Nav – angående søknad om ekspertbistand",
                    epostHtmlBody = eksterntVarselTekst,
                    smsTekst = eksterntVarselTekst,
                    sendevindu = sendevindu.tilDomain(),
                ),
            ),
            saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
                status = ArbeidsgiverVarselDomain.Status.FERDIG,
                statusTekst = "Søknad trukket eller avslått",
                nyLenkeTilSak = null,
            ),
        )
    }
}
