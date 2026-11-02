package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.ekspertbistand.dokarkiv.AvsenderMottaker
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.JournalpostType
import no.nav.ekspertbistand.dokarkiv.Sak
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.domain.tilForelopigUtfall
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.Mottaker
import no.nav.ekspertbistand.saksbehandling.VedtaksbrevData
import no.nav.ekspertbistand.saksbehandling.hentVedtakUtfallForEvent
import no.nav.ekspertbistand.saksbehandling.saksbehandlerForBrev
import no.nav.ekspertbistand.saksbehandling.sladdVedAdressebeskyttelse
import no.nav.ekspertbistand.saksbehandling.tolkSoknadssum
import no.nav.ekspertbistand.saksbehandling.vedtakKanFattes
import no.nav.ekspertbistand.saksbehandling.vedtaksbrevData
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val journalfoertSubtask = "vedtaksbrev_arbeidsgiver_journalfoert"
private val OSLO = TimeZone.of("Europe/Oslo")

/**
 * Journalfører vedtaksbrevet til arbeidsgiver i arbeidsgivermappa (orgnr), for alle utfall. Se
 * `specifications/journalfor_vedtaksbrev_arbeidsgiver.md`.
 *
 * Brevet bygges bare fra [EventData.VedtakFattet], ikke fra projeksjonene. Vedtaket [FattVedtak]
 * lagret for samme event avgjør om det skal journalføres:
 * - ikke lagret, men eventen kan fortsatt lagres: [FattVedtak] har ikke kjørt ennå, prøv igjen
 * - ikke lagret, og eventen kan ikke lagres: [FattVedtak] avviste den, ingen journalføring
 *
 * Deltakers navn sladdes ved kode 6 og 7, fordi tilgangskontrollen på person ikke gjelder i
 * arbeidsgivermappa. Innholdet i brevet logges ikke.
 */
class JournalfoerVedtaksbrevArbeidsgiver @OptIn(ExperimentalTime::class) constructor(
    private val dokumentService: DokumentService,
    private val dokArkivClient: DokArkivClient,
    private val fagsakIdService: FagsakIdService,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.VedtakFattet> {
    override val id: String = "Journalfoer vedtaksbrev arbeidsgiver"
    override val eventType: KClass<EventData.VedtakFattet> = EventData.VedtakFattet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VedtakFattet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, journalfoertSubtask)) {
            return success()
        }

        val data = event.data
        val (lagretUtfall, kanFattes) = transaction(database) {
            hentVedtakUtfallForEvent(event.id) to vedtakKanFattes(data)
        }
        if (lagretUtfall == null) {
            if (kanFattes) {
                return transientError("Vedtaket for sak ${data.sakId} er ikke lagret ennå")
            }
            log.info("Vedtaket for sak {} ble avvist, ingen journalføring av vedtaksbrev", data.sakId)
            idempotencyGuard.guard(event, journalfoertSubtask)
            return success()
        }

        val utfall = data.vedtak.utfall.tilForelopigUtfall()
        val brevdata = vedtaksbrevData(
            deltakerNavn = data.soknad.ansatt.navn,
            arbeidsgiver = VedtaksbrevData.Arbeidsgiver(
                navn = data.soknad.virksomhet.virksomhetsnavn,
                orgnr = data.soknad.virksomhet.virksomhetsnummer,
            ),
            soknadssum = tolkSoknadssum(data.soknad.behovForBistand.estimertKostnad),
            utfall = utfall,
            belop = data.vedtak.innvilgetBelop,
            begrunnelseDeltaker = data.vedtak.begrunnelseDeltaker,
            begrunnelseArbeidsgiver = data.vedtak.begrunnelseArbeidsgiver,
            tiltaksperiodeFra = data.vedtak.tiltaksperiodeFra,
            tiltaksperiodeTil = data.vedtak.tiltaksperiodeTil,
            mottaker = Mottaker.ARBEIDSGIVER,
            navEnhet = data.sak.behandlendeEnhetNavn,
            saksbehandler = saksbehandlerForBrev(
                data.sak.saksbehandlerIdent,
                data.sak.saksbehandlerNavn,
                data.vedtak.innstiltAvIdent,
            ),
            dato = data.tidspunkt.toLocalDateTime(OSLO).date,
        ).sladdVedAdressebeskyttelse(data.sak.adressebeskyttelse)

        val pdf = try {
            dokumentService.genererVedtaksbrevPdf(utfall, Mottaker.ARBEIDSGIVER, brevdata)
        } catch (e: Exception) {
            return transientError("Klarte ikke generere vedtaksbrev-PDF for sak ${data.sakId}", e)
        }

        val virksomhetsnummer = data.soknad.virksomhet.virksomhetsnummer
        val journalpost = try {
            dokArkivClient.opprettOgFerdigstillJournalpost(
                tittel = tittel(data.vedtak.utfall),
                virksomhetsnummer = virksomhetsnummer,
                sak = Sak.FagSak(fagsakIdService.opprettEllerHentFagsakId(soknadId = data.soknadId)),
                eksternReferanseId = "${data.soknadId}-vedtaksbrev-arbeidsgiver",
                dokumentPdfAsBytes = pdf,
                journalposttype = JournalpostType.UTGAAENDE,
                avsenderMottaker = AvsenderMottaker.orgnr(virksomhetsnummer),
                journalfoerendeEnhet = data.sak.behandlendeEnhet,
                navUserId = data.fattetAvIdent,
            )
        } catch (e: Exception) {
            return transientError("Feil ved journalføring av vedtaksbrev for sak ${data.sakId}", e)
        }

        if (!journalpost.journalpostferdigstilt) {
            return transientError("Journalpost for vedtaksbrev til sak ${data.sakId} ikke ferdigstilt")
        }
        val dokumentInfoId = journalpost.dokumenter.firstOrNull()?.dokumentInfoId
            ?: return unrecoverableError("DokArkiv mangler dokumentInfoId")

        return transaction(database) {
            try {
                publishEventQueue(
                    EventData.VedtaksbrevArbeidsgiverJournalfoert(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfall = data.vedtak.utfall,
                        virksomhetsnummer = virksomhetsnummer,
                        virksomhetsnavn = data.soknad.virksomhet.virksomhetsnavn,
                        journalpostId = journalpost.journalpostId,
                        dokumentInfoId = dokumentInfoId,
                        tidspunkt = clock.now(),
                    ),
                )
                publishEventQueue(
                    EventData.SakOppdatert(
                        sakId = data.sakId,
                        soknadId = data.soknadId,
                        utfortAvRolle = AktorRolle.SYSTEM,
                        utfortAvIdent = null,
                        notat = "Vedtaksbrev til arbeidsgiver journalført",
                        tidspunkt = clock.now(),
                    ),
                )
                idempotencyGuard.guard(event, journalfoertSubtask)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved publisering etter journalføring av vedtaksbrev", e)
            }
        }
    }

    private fun tittel(utfall: VedtakDomain.Utfall) = when (utfall) {
        VedtakDomain.Utfall.INNVILGE,
        VedtakDomain.Utfall.INNVILGE_REDUSERT -> "Vedtak om tilskudd til ekspertbistand"
        VedtakDomain.Utfall.AVSLAA -> "Avslag på søknad om ekspertbistand"
    }
}
