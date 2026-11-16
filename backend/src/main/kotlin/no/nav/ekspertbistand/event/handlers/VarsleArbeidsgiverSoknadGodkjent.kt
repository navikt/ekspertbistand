package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.event.domain.tilSaksStatus
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.tilsagndata.concat
import no.nav.ekspertbistand.tilsagndata.findTilsagnDataBySoknadId
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Når en søknad, innsendt via ekspertbistand på arbeidsgiver.nav.no, er godkjent i Arena og tilsagnsdataene er
 * lagret, publiseres [EventData.TilsagnsdataLagret].
 *
 * Denne handleren sender da en beskjed med eksternt varsel og setter saken til ferdig i notifikasjonsplatformen,
 * én gang per tilsagn. Når begge kallene har lyktes, publiseres [EventData.ArbeidsgiverVarsletSoknadGodkjent] med
 * det som ble sendt.
 */
class VarsleArbeidsgiverSoknadGodkjent @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.TilsagnsdataLagret> {
    override val id: String = "VarsleArbeidsgiverSoknadGodkjent"
    override val eventType = EventData.TilsagnsdataLagret::class

    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"
    private val kvittertSubTask = "arbeidsgiver_varslet_kvittert"

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.TilsagnsdataLagret>): EventHandledResult {
        val soknad = event.data.soknad
        val soknadId = soknad.id ?: return unrecoverableError("soknad.id kan ikke være null")
        val tilsagnNummer = event.data.tilsagnData.tilsagnNummer

        val godkjentFlereGanger = transaction(database) {
            findTilsagnDataBySoknadId(UUID.fromString(soknadId)).size > 1
        }
        val varsel = ArbeidsgiverVarselDomain.soknadGodkjent(soknad, tilsagnNummer, godkjentFlereGanger)
        val saksstatus = varsel.saksstatus ?: return unrecoverableError("Varselet om godkjent søknad mangler saksstatus")

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
                    EventData.ArbeidsgiverVarsletSoknadGodkjent(
                        soknadId = soknadId,
                        tilsagnNummer = tilsagnNummer.concat(),
                        varsel = varsel,
                        tidspunkt = clock.now(),
                    ),
                )
                idempotencyGuard.guard(event, kvittertSubTask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved publisering etter varsling av arbeidsgiver om godkjent søknad", e)
            }
        }
    }
}
