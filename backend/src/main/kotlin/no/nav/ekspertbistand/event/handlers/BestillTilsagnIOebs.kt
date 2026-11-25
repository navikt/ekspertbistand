package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.oebs.OebsKlient
import no.nav.ekspertbistand.oebs.integration.NavEnhetNummer
import no.nav.ekspertbistand.oebs.integration.OkonomiPart
import no.nav.ekspertbistand.oebs.integration.OpprettBestilling
import no.nav.ekspertbistand.oebs.integration.Organisasjonsnummer
import no.nav.ekspertbistand.oebs.integration.Periode
import no.nav.ekspertbistand.oebs.integration.Regnskapskonto
import no.nav.ekspertbistand.oebs.integration.Artskonto
import no.nav.ekspertbistand.oebs.integration.Valuta
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.bestillingFinnesForVedtak
import no.nav.ekspertbistand.saksbehandling.hentBestillingGrunnlagForEvent
import no.nav.ekspertbistand.saksbehandling.lagreBestilling
import no.nav.ekspertbistand.saksbehandling.vedtakKanFattes
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime

private const val bestillingSubtask = "oebs_bestilling_vedtak"

/**
 * Bestiller tilsagnet i OeBS når et vedtak er innvilget, så pengene settes av. Se
 * `specifications/sette_av_penger_oebs_vedtak.md`, 3.
 *
 * Handlerne på [EventData.VedtakFattet] kjører hver for seg, så denne slår opp vedtaket [FattVedtak]
 * lagret for samme event:
 * - ikke lagret, men eventen kan fortsatt lagres: [FattVedtak] har ikke kjørt ennå, prøv igjen
 * - ikke lagret, og eventen kan ikke lagres: [FattVedtak] avviste den, ingen bestilling
 * - avslått, eller vedtaket har allerede en bestilling: ingen bestilling
 *
 * Løpenummeret, outbox-raden, `sak_bestilling`, referansen i `oebs_bestilling_referanse` og
 * idempotens-raden skrives i samme transaksjon. En ny kjøring etter feil bruker derfor samme
 * løpenummer og bestiller ikke to ganger.
 *
 * Saksloggen skrives av [SkrivSaksloggForOebsBestillingStatus] når VALP melder status. Se
 * `specifications/oebs_status_til_saksgang.md`.
 *
 * Kostnadsstedet er det saksbehandler valgte, fra [EventData.VedtakFattet.vedtak]. Vedtak fra før
 * feltet fantes (bare i dev) bruker sakens behandlende enhet. Se
 * `specifications/kostnadssted_forelopig_vedtak.md`.
 */
class BestillTilsagnIOebs(
    private val database: Database,
    private val oebsKlient: OebsKlient = OebsKlient(),
) : EventHandler<EventData.VedtakFattet> {
    override val id: String = "Bestill tilsagn i OeBS"
    override val eventType: KClass<EventData.VedtakFattet> = EventData.VedtakFattet::class

    private val idempotencyGuard = idempotencyGuard(database)
    private val log = logger()

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.VedtakFattet>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, bestillingSubtask)) {
            return success()
        }

        return transaction(database) {
            try {
                val grunnlag = hentBestillingGrunnlagForEvent(event.id)
                if (grunnlag == null) {
                    return@transaction if (vedtakKanFattes(event.data)) {
                        transientError("Vedtaket for sak ${event.data.sakId} er ikke lagret ennå")
                    } else {
                        log.info("Vedtaket for sak {} ble avvist, ingen bestilling", event.data.sakId)
                        idempotencyGuard.guard(event, bestillingSubtask)
                        success()
                    }
                }

                if (grunnlag.utfall == Saksstatus.AVSLATT) {
                    idempotencyGuard.guard(event, bestillingSubtask)
                    return@transaction success()
                }

                if (bestillingFinnesForVedtak(grunnlag.vedtakId)) {
                    log.info("Vedtaket for sak {} har allerede en bestilling", grunnlag.sakId)
                    idempotencyGuard.guard(event, bestillingSubtask)
                    return@transaction success()
                }

                val saksnummer = grunnlag.saksnummer
                val belop = grunnlag.belop
                val periodeFra = grunnlag.periodeFra
                val periodeTil = grunnlag.periodeTil
                val kostnadssted = event.data.vedtak.kostnadssted
                    ?: grunnlag.behandlendeEnhet?.also {
                        log.info("Vedtaket for sak {} mangler kostnadssted, bruker behandlende enhet", grunnlag.sakId)
                    }
                if (saksnummer == null || belop == null || periodeFra == null || periodeTil == null || kostnadssted == null) {
                    return@transaction unrecoverableError(
                        "Innvilget vedtak for sak ${grunnlag.sakId} mangler saksnummer, beløp, periode eller kostnadssted"
                    )
                }

                val bestillingsnummer = with(oebsKlient) {
                    opprettBestilling(grunnlag.sakId, grunnlag.soknadId, saksnummer) { nr ->
                        OpprettBestilling(
                            bestillingsnummer = nr,
                            regnskapskonto = Regnskapskonto.TILTAK_DRIFTSTILSKUDD,
                            artskonto = Artskonto.EKSPERTBISTAND,
                            arrangor = OpprettBestilling.Arrangor.Norsk(Organisasjonsnummer(grunnlag.virksomhetsnummer)),
                            kostnadssted = NavEnhetNummer(kostnadssted),
                            avtalenummer = null,
                            belop = belop,
                            periode = Periode(start = periodeFra, slutt = periodeTil.plus(1, DateTimeUnit.DAY)),
                            behandletAv = OkonomiPart.NavAnsatt(grunnlag.innstiltAvIdent),
                            behandletTidspunkt = grunnlag.innstiltTidspunkt,
                            besluttetAv = OkonomiPart.NavAnsatt(grunnlag.fattetAvIdent),
                            besluttetTidspunkt = grunnlag.fattetTidspunkt,
                            valuta = Valuta.NOK,
                        )
                    }
                }
                lagreBestilling(grunnlag, bestillingsnummer)
                idempotencyGuard.guard(event, bestillingSubtask)
                log.info("Bestilling {} til OeBS lagt i outbox for sak {}", bestillingsnummer, grunnlag.sakId)
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved bestilling til OeBS etter vedtak", e)
            }
        }
    }
}
