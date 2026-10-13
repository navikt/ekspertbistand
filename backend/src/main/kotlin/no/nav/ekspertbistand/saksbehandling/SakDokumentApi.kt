package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.refusjon.RefusjonDb
import no.nav.ekspertbistand.refusjon.RefusjonStatusDto
import no.nav.ekspertbistand.refusjon.RefusjonVedleggDto
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatusDto
import no.nav.ekspertbistand.vedlegg.VedleggDb
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Metadata og PDF-er for nyeste innsending per sak. Begge roller kan lese saker på sine enheter,
 * også når de ikke er tildelt saken. Filinnhold leses først etter tilgangssjekkene.
 *
 * GET /api/saksbehandling/v1/saker/{sakId}/sluttrapport og /refusjon gir metadata (204 uten innsending).
 * GET /api/saksbehandling/v1/saker/{sakId}/sluttrapport/fil og
 *     /refusjon/vedlegg/{vedleggId} gir PDF (404 uten vedlegg).
 * Filrutene laster ned som standard. `?visning=forhandsvisning` viser PDF i nettleseren.
 */
suspend fun Application.configureSakDokumentApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
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
                    call.respond(SluttrapportStatusDto(metadata.filnavn, metadata.lastetOpp))
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
            }
        }
    }
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
