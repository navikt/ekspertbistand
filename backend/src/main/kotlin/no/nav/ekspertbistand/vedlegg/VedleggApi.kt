package no.nav.ekspertbistand.vedlegg

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.soknad.findSoknadById
import no.nav.ekspertbistand.soknad.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val log = logger()

private const val MAKS_FIL_STORRELSE_BYTES = 10 * 1024 * 1024
private const val MAKS_ANTALL_FILER = 5

private val vedleggLastetOppCounter = Metrics.meterRegistry.counter(
    "vedlegg_lastet_opp_total",
    "type", VedleggType.SLUTTRAPPORT.name,
)

class VedleggApi(
    private val database: Database,
    private val vedleggDb: VedleggDb,
    private val clamAvClient: ClamAvClient,
    private val altinnTilgangerClient: AltinnTilgangerClient,
) {
    suspend fun RoutingContext.lastOppSluttrapport(soknadId: UUID) {
        val soknad = transaction(database) { findSoknadById(soknadId) }

        if (soknad == null) {
            call.respond(HttpStatusCode.NotFound, "søknad ikke funnet")
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        if (!tilganger.harTilgang(soknad.virksomhet.virksomhetsnummer)) {
            call.respond(HttpStatusCode.Forbidden, "bruker har ikke tilgang til organisasjon")
            return
        }

        val multipart = call.receiveMultipart()
        data class UploadedFile(val filnavn: String, val innhold: ByteArray)
        val filer = mutableListOf<UploadedFile>()

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val filnavn = part.originalFileName?.ifBlank { null } ?: "vedlegg.pdf"
                val bytes = part.provider().readRemaining().readBytes()
                filer.add(UploadedFile(filnavn, bytes))
            }
            part.dispose()
        }

        if (filer.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Minst én fil må lastes opp")
            return
        }
        if (filer.size > MAKS_ANTALL_FILER) {
            call.respond(HttpStatusCode.BadRequest, "Maks $MAKS_ANTALL_FILER filer tillatt")
            return
        }

        for ((filnavn, bytes) in filer) {
            if (bytes.size > MAKS_FIL_STORRELSE_BYTES) {
                call.respond(HttpStatusCode.BadRequest, "Filen '$filnavn' overskrider maks 10 MB")
                return
            }
            if (!erGyldigPdf(bytes)) {
                call.respond(HttpStatusCode.BadRequest, "Filen '$filnavn' er ikke en gyldig PDF")
                return
            }
        }

        for ((filnavn, bytes) in filer) {
            val scanResultat = try {
                clamAvClient.scan(filnavn, bytes)
            } catch (e: Exception) {
                log.error("ClamAV utilgjengelig: soknadId={}", soknadId, e)
                Metrics.meterRegistry.counter("clamav_scan_resultat_total", "result", "UNAVAILABLE").increment()
                call.respond(HttpStatusCode.ServiceUnavailable, "Virusskanning utilgjengelig, prøv igjen")
                return
            }

            Metrics.meterRegistry.counter("clamav_scan_resultat_total", "result", scanResultat.status.name).increment()

            when (scanResultat.status) {
                ClamAvClient.ScanStatus.FOUND -> {
                    call.respond(HttpStatusCode.UnprocessableEntity, "Virus funnet i filen '$filnavn'")
                    return
                }
                ClamAvClient.ScanStatus.ERROR -> {
                    log.error("ClamAV feilet for fil: soknadId={}, filnavn={}", soknadId, filnavn)
                    call.respond(HttpStatusCode.ServiceUnavailable, "Virusskanning feilet, prøv igjen")
                    return
                }
                ClamAvClient.ScanStatus.OK -> {}
            }
        }

        for ((filnavn, bytes) in filer) {
            vedleggDb.lagreVedlegg(
                soknadId = soknadId,
                type = VedleggType.SLUTTRAPPORT,
                filnavn = filnavn,
                innhold = bytes,
            )
        }

        log.info("Lastet opp {} vedlegg: soknadId={}", filer.size, soknadId)
        vedleggLastetOppCounter.increment(filer.size.toDouble())

        call.respond(HttpStatusCode.Created)
    }
}

fun erGyldigPdf(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
    bytes[0] == 0x25.toByte() &&
    bytes[1] == 0x50.toByte() &&
    bytes[2] == 0x44.toByte() &&
    bytes[3] == 0x46.toByte()
