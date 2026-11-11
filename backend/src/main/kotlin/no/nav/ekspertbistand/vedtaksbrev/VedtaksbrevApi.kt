package no.nav.ekspertbistand.vedtaksbrev

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.dokument.svarMedPdf
import no.nav.ekspertbistand.dokument.utenMellomlagring
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.domain.tilForelopigUtfall
import no.nav.ekspertbistand.event.domain.tilVedtaksbrevData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.Mottaker
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.findSoknadById
import no.nav.ekspertbistand.soknad.innloggetBruker
import no.nav.ekspertbistand.soknad.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val VEDTAKSBREV_FILNAVN = "Vedtaksbrev ekspertbistand.pdf"

/**
 * Vedtaket og vedtaksbrevet til arbeidsgiver, fra [VedtaksbrevTable]. Slår aldri opp i `event_log` eller Joark.
 * Tilgang som `hentMaksBelop`: søknaden (404), så Altinn (403), så vedtaket (404). Se
 * `specifications/vis_vedtak_arbeidsgiver.md`.
 *
 * Brevet har deltakers navn og begrunnelsen, så bare soknadId logges.
 */
@OptIn(ExperimentalTime::class)
class VedtaksbrevApi(
    private val database: Database,
    private val vedtaksbrevDb: VedtaksbrevDb,
    private val altinnTilgangerClient: AltinnTilgangerClient,
    private val dokumentService: DokumentService,
    private val clock: Clock = Clock.System,
) {
    private val log = logger()

    suspend fun RoutingContext.hentVedtak(soknadId: UUID) {
        val (_, vedtaksbrev) = hentMedTilgang(soknadId) ?: return
        call.utenMellomlagring()
        call.respond(vedtaksbrev.tilDto())
    }

    suspend fun RoutingContext.hentVedtaksbrevPdf(soknadId: UUID) {
        val (soknad, vedtaksbrev) = hentMedTilgang(soknadId) ?: return

        val pdf = dokumentService.genererVedtaksbrevPdf(
            vedtaksbrev.utfall.tilForelopigUtfall(),
            Mottaker.ARBEIDSGIVER,
            vedtaksbrev.brevdata.tilVedtaksbrevData(),
        )

        transaction(database) {
            publishEventQueue(
                EventData.VedtaksbrevVist(
                    sakId = vedtaksbrev.sakId.toString(),
                    soknadId = soknadId.toString(),
                    utfall = vedtaksbrev.utfall,
                    virksomhetsnummer = soknad.virksomhet.virksomhetsnummer,
                    vistTil = innloggetBruker,
                    tidspunkt = clock.now(),
                )
            )
        }
        log.info("Vedtaksbrev vist til arbeidsgiver: soknadId={}", soknadId)

        call.svarMedPdf(VEDTAKSBREV_FILNAVN, pdf, inline = false)
    }

    private suspend fun RoutingContext.hentMedTilgang(soknadId: UUID): Pair<DTO.Soknad, Vedtaksbrev>? {
        val soknad = transaction(database) { findSoknadById(soknadId, filterForArbeidsgiver = true) }
        if (soknad == null) {
            call.respond(HttpStatusCode.NotFound, "søknad ikke funnet")
            return null
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        if (!tilganger.harTilgang(soknad.virksomhet.virksomhetsnummer)) {
            call.respond(HttpStatusCode.Forbidden, "bruker har ikke tilgang til organisasjon")
            return null
        }

        val vedtaksbrev = vedtaksbrevDb.hentForSoknad(soknadId)
        if (vedtaksbrev == null) {
            call.respond(HttpStatusCode.NotFound, "vedtak ikke funnet")
            return null
        }
        return soknad to vedtaksbrev
    }
}

@Serializable
data class VedtaksbrevDto(
    val utfall: VedtaksbrevUtfall,
    val vedtaksdato: String,
    val innvilgetBelop: String?,
    val soknadssum: String?,
    val tiltaksperiode: Periode?,
    val refusjonsfrist: String?,
    val deltakerNavn: String,
    val virksomhetsnavn: String,
) {
    @Serializable
    data class Periode(val fra: String, val til: String)
}

@Serializable
enum class VedtaksbrevUtfall { INNVILGET, INNVILGET_REDUSERT, AVSLATT }

private fun VedtakDomain.Utfall.tilApi(): VedtaksbrevUtfall = when (this) {
    VedtakDomain.Utfall.INNVILGE -> VedtaksbrevUtfall.INNVILGET
    VedtakDomain.Utfall.INNVILGE_REDUSERT -> VedtaksbrevUtfall.INNVILGET_REDUSERT
    VedtakDomain.Utfall.AVSLAA -> VedtaksbrevUtfall.AVSLATT
}

@OptIn(ExperimentalTime::class)
private fun Vedtaksbrev.tilDto() = VedtaksbrevDto(
    utfall = utfall.tilApi(),
    vedtaksdato = brevdata.dato,
    innvilgetBelop = brevdata.innvilgetBelop,
    soknadssum = brevdata.soknadssum,
    tiltaksperiode = brevdata.tiltaksperiode?.let { VedtaksbrevDto.Periode(fra = it.fra, til = it.til) },
    refusjonsfrist = brevdata.refusjonsfrist,
    deltakerNavn = brevdata.deltaker.navn,
    virksomhetsnavn = brevdata.arbeidsgiver.navn,
)
