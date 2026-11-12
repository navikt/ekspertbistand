package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.domain.tilDomain
import no.nav.ekspertbistand.event.domain.tilSaksStatus
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.EksterntVarsel
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.merkelapp
import no.nav.ekspertbistand.notifikasjon.sendevindu
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.soknad.kvitteringsLenkeV2
import no.nav.ekspertbistand.soknad.vedtakLenke
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val nyBeskjedSubtask = "notifikasjonsplatform_ny_beskjed"
private const val nyStatusSakSubtask = "notifikasjonsplatform_ny_status_sak"
private const val kvittertSubtask = "arbeidsgiver_varslet_kvittert"

/**
 * Varsler arbeidsgiver om vedtaket i ny saksbehandling, når vedtaksbrevet er journalført i arbeidsgivermappa. Se
 * `specifications/varsle_arbeidsgiver_vedtak.md`.
 *
 * Sender beskjed med eksternt varsel og setter saken til ferdig i notifikasjonsplattformen. Når begge kallene har
 * lyktes, publiseres [EventData.ArbeidsgiverVarsletVedtakFattet] med det som ble sendt. Varselet bygges bare fra
 * eventen, så et nytt forsøk sender det samme.
 */
class VarsleArbeidsgiverVedtakFattet @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.VedtaksbrevArbeidsgiverJournalfoert> {
    override val id: String = "VarsleArbeidsgiverVedtakFattet"
    override val eventType: KClass<EventData.VedtaksbrevArbeidsgiverJournalfoert> =
        EventData.VedtaksbrevArbeidsgiverJournalfoert::class

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VedtaksbrevArbeidsgiverJournalfoert>): EventHandledResult {
        val data = event.data
        val varsel = arbeidsgiverVarsel(data)

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubtask)) {
            try {
                produsentApiKlient.opprettNyBeskjed(
                    grupperingsid = varsel.grupperingsid,
                    eksternId = varsel.beskjed.eksternId,
                    virksomhetsnummer = varsel.virksomhetsnummer,
                    tekst = varsel.beskjed.tekst,
                    lenke = varsel.beskjed.lenke,
                    eksternVarsel = varsel.beskjed.eksterntVarsel.let {
                        EksterntVarsel(
                            epostTittel = it.epostTittel,
                            epostHtmlBody = it.epostHtmlBody,
                            smsTekst = it.smsTekst,
                        )
                    },
                )
            } catch (e: Exception) {
                return transientError("Klarte ikke opprette beskjed om vedtak for sak ${data.sakId}", e)
            }
            idempotencyGuard.guard(event, nyBeskjedSubtask)
        }

        if (!idempotencyGuard.isGuarded(event.id, nyStatusSakSubtask)) {
            try {
                produsentApiKlient.nyStatusSak(
                    grupperingsid = varsel.grupperingsid,
                    status = varsel.saksstatus.status.tilSaksStatus(),
                    statusTekst = varsel.saksstatus.statusTekst,
                    nyLenkeTilSak = varsel.saksstatus.nyLenkeTilSak,
                )
            } catch (e: Exception) {
                return transientError("Klarte ikke oppdatere saksstatus om vedtak for sak ${data.sakId}", e)
            }
            idempotencyGuard.guard(event, nyStatusSakSubtask)
        }

        if (idempotencyGuard.isGuarded(event.id, kvittertSubtask)) {
            return success()
        }

        return transaction(database) {
            try {
                publishEventQueue(
                    EventData.ArbeidsgiverVarsletVedtakFattet(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfall = data.utfall,
                        journalpostId = data.journalpostId,
                        varsel = varsel,
                        tidspunkt = clock.now(),
                    ),
                )
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfortAvRolle = AktorRolle.SYSTEM,
                        utfortAvIdent = null,
                        notat = "Arbeidsgiver varslet om vedtak",
                        tidspunkt = clock.now(),
                    ),
                )
                idempotencyGuard.guard(event, kvittertSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved publisering etter varsling av arbeidsgiver om vedtak", e)
            }
        }
    }
}

private const val epostTittel = "Nav – angående søknad om ekspertbistand"

private fun eksterntVarselTekst(virksomhetsnavn: String) =
    "$virksomhetsnavn har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."

private fun beskjedTekst(utfall: VedtakDomain.Utfall) = when (utfall) {
    VedtakDomain.Utfall.INNVILGE,
    VedtakDomain.Utfall.INNVILGE_REDUSERT -> "Søknaden er godkjent og ekspertbistand kan nå tas i bruk."
    VedtakDomain.Utfall.AVSLAA -> "Søknaden om ekspertbistand er avslått."
}

/** Samme tekst som statusbanneret på vedtakssiden (`frontend/client/src/v2/pages/VedtakPage.tsx`). */
private fun statusTekst(utfall: VedtakDomain.Utfall) = when (utfall) {
    VedtakDomain.Utfall.INNVILGE -> "Søknad godkjent"
    VedtakDomain.Utfall.INNVILGE_REDUSERT -> "Søknad godkjent med redusert beløp"
    VedtakDomain.Utfall.AVSLAA -> "Søknad avslått"
}

internal fun arbeidsgiverVarsel(data: EventData.VedtaksbrevArbeidsgiverJournalfoert) = ArbeidsgiverVarselDomain(
    merkelapp = merkelapp,
    mottaker = ArbeidsgiverVarselDomain.Mottaker(altinnRessursId = altinn3Ressursid),
    virksomhetsnummer = data.virksomhetsnummer,
    grupperingsid = data.soknadId,
    beskjed = ArbeidsgiverVarselDomain.Beskjed(
        eksternId = "${data.soknadId}-vedtak",
        tekst = beskjedTekst(data.utfall),
        lenke = vedtakLenke(data.soknadId),
        eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
            epostTittel = epostTittel,
            epostHtmlBody = eksterntVarselTekst(data.virksomhetsnavn),
            smsTekst = eksterntVarselTekst(data.virksomhetsnavn),
            sendevindu = sendevindu.tilDomain(),
        ),
    ),
    saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
        status = ArbeidsgiverVarselDomain.Status.FERDIG,
        statusTekst = statusTekst(data.utfall),
        nyLenkeTilSak = kvitteringsLenkeV2(data.soknadId),
    ),
)
