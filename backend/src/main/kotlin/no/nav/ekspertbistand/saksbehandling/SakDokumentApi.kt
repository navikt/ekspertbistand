package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.refusjon.RefusjonDb
import no.nav.ekspertbistand.refusjon.RefusjonStatusDto
import no.nav.ekspertbistand.refusjon.RefusjonVedleggDto
import no.nav.ekspertbistand.soknad.findSoknadById
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatusDto
import no.nav.ekspertbistand.vedlegg.VedleggDb
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val log = LoggerFactory.getLogger("SakDokumentApi")

/**
 * Metadata og PDF-er for nyeste innsending per sak. Begge roller kan lese saker på sine enheter,
 * også når de ikke er tildelt saken. Filinnhold leses først etter tilgangssjekkene.
 *
 * GET /api/saksbehandling/v1/saker/{sakId}/sluttrapport og /refusjon gir metadata (204 uten innsending).
 * GET /api/saksbehandling/v1/saker/{sakId}/sluttrapport/fil og
 *     /refusjon/vedlegg/{vedleggId} gir PDF (404 uten vedlegg).
 * Filrutene laster ned som standard. `?visning=forhandsvisning` viser PDF i nettleseren.
 *
 * Vedtaksbrev på foreløpig vedtak (`specifications/forelopig_vedtak_brev.md`):
 * POST …/forelopig-vedtak/forhandsvisning?mottaker=deltaker|arbeidsgiver lager brevet fra et
 *     foreløpig vedtak som ikke er sendt. Samme tilgangskrav som innsendingen. Lagrer ingenting.
 * GET  …/forelopig-vedtak/brev/{mottaker} lager brevet fra siste lagrede foreløpige vedtak, for
 *     saksbehandler og beslutter (404 uten vedtak).
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureSakDokumentApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val dokumentService = dependencies.resolve<DokumentService>()
    val vedleggDb = VedleggDb(database)
    val refusjonDb = RefusjonDb(database)

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/saker/{sakId}") {
                get("/sluttrapport") {
                    val sak = call.hentTilgjengeligSak(database, tilgangsmaskinClient) ?: return@get
                    val metadata = vedleggDb.finnSluttrapportMetadataForSak(sak.sakId)
                        ?: return@get call.respond(HttpStatusCode.NoContent)

                    call.utenMellomlagring()
                    dokumentHentet("sluttrapport", "metadata")
                    call.respond(
                        SluttrapportStatusDto(
                            filnavn = metadata.filnavn,
                            opprettet = metadata.lastetOpp,
                            storrelse = metadata.storrelse,
                        )
                    )
                }

                get("/sluttrapport/fil") {
                    val sak = call.hentTilgjengeligSak(database, tilgangsmaskinClient) ?: return@get
                    val inline = call.inlineVisning() ?: return@get
                    val fil = vedleggDb.hentNyesteSluttrapportForSak(sak.sakId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sluttrapport"))

                    call.svarMedPdf(fil.filnavn, fil.innhold, inline)
                    dokumentHentet("sluttrapport", if (inline) "forhandsvisning" else "nedlasting")
                }

                get("/refusjon") {
                    val sak = call.hentTilgjengeligSak(database, tilgangsmaskinClient) ?: return@get
                    val status = refusjonDb.finnRefusjonskravStatusForSak(sak.sakId)
                        ?: return@get call.respond(HttpStatusCode.NoContent)

                    call.utenMellomlagring()
                    dokumentHentet("refusjon", "metadata")
                    call.respond(
                        RefusjonStatusDto(
                            belopKroner = status.belopKroner,
                            utgifter = status.utgifter,
                            opprettet = status.opprettet,
                            kontonummer = status.kontonummer,
                            vedlegg = status.vedlegg.map {
                                RefusjonVedleggDto(it.id, it.filnavn, it.storrelse)
                            },
                        )
                    )
                }

                get("/refusjon/vedlegg/{vedleggId}") {
                    val sak = call.hentTilgjengeligSak(database, tilgangsmaskinClient) ?: return@get
                    val vedleggId = call.parameters["vedleggId"]?.let {
                        runCatching { UUID.fromString(it) }.getOrNull()
                    } ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig vedleggId"))
                    val inline = call.inlineVisning() ?: return@get
                    val fil = refusjonDb.hentNyesteRefusjonsvedleggForSak(sak.sakId, vedleggId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke vedlegg"))

                    call.svarMedPdf(fil.filnavn, fil.innhold, inline)
                    dokumentHentet("refusjon", if (inline) "forhandsvisning" else "nedlasting")
                }

                post("/forelopig-vedtak/forhandsvisning") {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@post
                    val sakId = call.sakIdParameter() ?: return@post
                    val mottaker = call.mottakerParameter(call.request.queryParameters["mottaker"]) ?: return@post

                    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
                    if (!call.sjekkTilgangTilEnhet(principal, sak)) return@post
                    if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return@post
                    if (sak.kildeTilBehandling != KildeTilBehandling.EKSPERTBISTAND) {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("kode" to SAK_UGYLDIG_KILDE, "message" to "Saken behandles i Arena"),
                        )
                    }
                    if (!call.sjekkErSaksbehandlerPåSak(principal, sak)) return@post
                    if (sak.status != Saksstatus.UNDER_BEHANDLING) {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("kode" to SAK_UGYLDIG_STATUS, "message" to "Saken er ikke under behandling"),
                        )
                    }

                    val request = try {
                        call.receive<ForelopigVedtakRequest>()
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        return@post call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("message" to "ugyldig foreløpig vedtak"),
                        )
                    }
                    valider(request)
                    val vedtak = validerFelter(request)

                    val (soknad, saksbehandlerNavn) = transaction(database) {
                        findSoknadById(sak.soknadId) to hentSaksbehandlerNavn(sakId, principal.navIdent)
                    }
                    if (soknad == null) {
                        return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke søknad"))
                    }
                    if (tolkSoknadssum(soknad.behovForBistand.estimertKostnad) == null) {
                        return@post call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("kode" to UGYLDIG_SOKNADSSUM, "message" to "Søknadssummen kan ikke tolkes som et beløp"),
                        )
                    }

                    val data = vedtaksbrevData(
                        soknad = soknad,
                        utfall = vedtak.utfall,
                        belop = vedtak.innvilgetBelop,
                        begrunnelseDeltaker = vedtak.begrunnelseDeltaker,
                        begrunnelseArbeidsgiver = vedtak.begrunnelseArbeidsgiver,
                        tiltaksperiodeFra = vedtak.tiltaksperiodeFra,
                        tiltaksperiodeTil = vedtak.tiltaksperiodeTil,
                        mottaker = mottaker,
                        navEnhet = principal.enhetsnavn(sak.behandlendeEnhet),
                        saksbehandler = saksbehandlerNavn,
                        dato = Clock.System.now().toLocalDateTime(OSLO).date,
                    )
                    val pdf = call.genererVedtaksbrev(dokumentService, sakId, vedtak.utfall, mottaker, data)
                        ?: return@post

                    call.svarMedPdf(mottaker.filnavn, pdf, inline = true)
                    dokumentHentet("forelopig_vedtak", "forhandsvisning")
                }

                get("/forelopig-vedtak/brev/{mottaker}") {
                    val sak = call.hentTilgjengeligSak(database, tilgangsmaskinClient) ?: return@get
                    val mottaker = call.mottakerParameter(call.parameters["mottaker"]) ?: return@get
                    val inline = call.inlineVisning() ?: return@get
                    val principal = call.principal<AzureAdPrincipal>() ?: return@get

                    val lagret = transaction(database) {
                        val vedtak = hentSisteForelopigVedtak(sak.sakId) ?: return@transaction null
                        val soknad = findSoknadById(sak.soknadId) ?: return@transaction null
                        Triple(vedtak, soknad, hentSaksbehandlerNavn(sak.sakId, vedtak.innstiltAvIdent))
                    } ?: return@get call.respond(
                        HttpStatusCode.NotFound,
                        mapOf("message" to "fant ikke foreløpig vedtak"),
                    )
                    val (vedtak, soknad, saksbehandlerNavn) = lagret

                    val data = vedtaksbrevData(
                        soknad = soknad,
                        utfall = vedtak.utfall,
                        belop = vedtak.innvilgetBelop,
                        begrunnelseDeltaker = vedtak.begrunnelseDeltaker,
                        begrunnelseArbeidsgiver = vedtak.begrunnelseArbeidsgiver,
                        tiltaksperiodeFra = vedtak.tiltaksperiodeFra,
                        tiltaksperiodeTil = vedtak.tiltaksperiodeTil,
                        mottaker = mottaker,
                        navEnhet = principal.enhetsnavn(sak.behandlendeEnhet),
                        saksbehandler = saksbehandlerNavn,
                        dato = vedtak.sendtTidspunkt.toLocalDateTime(OSLO).date,
                    )
                    val pdf = call.genererVedtaksbrev(dokumentService, sak.sakId, vedtak.utfall, mottaker, data)
                        ?: return@get

                    call.svarMedPdf(mottaker.filnavn, pdf, inline)
                    dokumentHentet("forelopig_vedtak", if (inline) "forhandsvisning" else "nedlasting")
                }
            }
        }
    }
}

private val OSLO = TimeZone.of("Europe/Oslo")

private suspend fun ApplicationCall.mottakerParameter(verdi: String?): Mottaker? {
    val mottaker = Mottaker.fra(verdi)
    if (mottaker == null) respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig mottaker"))
    return mottaker
}

/** Enhetsnavnet finnes bare hos innlogget, som har bestått [sjekkTilgangTilEnhet] for enheten. */
private fun AzureAdPrincipal.enhetsnavn(enhetsnummer: String?): String? =
    enheter.find { it.enhetnummer == enhetsnummer }?.navn

/** Navnet til saksbehandler på saken, bare når det er [ident] som har saken. */
private fun hentSaksbehandlerNavn(sakId: UUID, ident: String): String? =
    SakTable.select(SakTable.saksbehandlerIdent, SakTable.saksbehandlerNavn)
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.takeIf { it[SakTable.saksbehandlerIdent] == ident }
        ?.get(SakTable.saksbehandlerNavn)

/** Svarer 503 og returnerer null når PDF-en ikke kan lages. Innholdet i brevet logges ikke. */
private suspend fun ApplicationCall.genererVedtaksbrev(
    dokumentService: DokumentService,
    sakId: UUID,
    utfall: ForelopigUtfall,
    mottaker: Mottaker,
    data: VedtaksbrevData,
): ByteArray? = try {
    dokumentService.genererVedtaksbrevPdf(utfall, mottaker, data)
} catch (e: Exception) {
    e.rethrowIfCancellation()
    log.warn("Kunne ikke lage vedtaksbrev til {} for sakId={}: {}", mottaker, sakId, e.javaClass.simpleName)
    respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "kunne ikke lage brev"))
    null
}

private suspend fun ApplicationCall.hentTilgjengeligSak(
    database: Database,
    tilgangsmaskinClient: TilgangsmaskinClient,
): SakTilgangsgrunnlag? {
    val principal = principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return null
    val sakId = sakIdParameter() ?: return null
    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
    if (sak == null) {
        respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
        return null
    }
    if (!sjekkTilgangTilEnhet(principal, sak)) return null
    if (!sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return null
    return sak
}

private suspend fun ApplicationCall.inlineVisning(): Boolean? = when (request.queryParameters["visning"]) {
    null, "nedlasting" -> false
    "forhandsvisning" -> true
    else -> {
        respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig visning"))
        null
    }
}

private fun ApplicationCall.utenMellomlagring() {
    response.header(HttpHeaders.CacheControl, "no-store")
    response.header(HttpHeaders.Pragma, "no-cache")
    response.header("X-Content-Type-Options", "nosniff")
}

private suspend fun ApplicationCall.svarMedPdf(filnavn: String, innhold: ByteArray, inline: Boolean) {
    utenMellomlagring()
    val disposisjon = if (inline) ContentDisposition.Inline else ContentDisposition.Attachment
    val trygtFilnavn = filnavn.substringAfterLast('/').substringAfterLast('\\')
        .substringBeforeLast('.')
        .map { tegn -> if (tegn.code < 128 && (tegn.isLetterOrDigit() || tegn in "-_.")) tegn else '_' }
        .joinToString("")
        .take(90)
        .ifBlank { "dokument" } + ".pdf"
    response.header(
        HttpHeaders.ContentDisposition,
        disposisjon.withParameter(ContentDisposition.Parameters.FileName, trygtFilnavn).toString(),
    )
    respondBytes(innhold, ContentType.Application.Pdf)
}

private fun dokumentHentet(type: String, handling: String) {
    Metrics.meterRegistry.counter(
        "saksbehandling_dokument_hentet_total",
        "type", type,
        "handling", handling,
    ).increment()
}
