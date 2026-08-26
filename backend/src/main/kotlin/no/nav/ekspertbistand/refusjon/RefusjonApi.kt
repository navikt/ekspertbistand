package no.nav.ekspertbistand.refusjon

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.findSoknadById
import no.nav.ekspertbistand.soknad.subjectToken
import no.nav.ekspertbistand.vedlegg.erGyldigPdf
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private const val MAKS_FIL_STORRELSE_BYTES = 10 * 1024 * 1024
private const val MAKS_ANTALL_FILER = 5
private const val MAKS_UTGIFTER_LENGDE = 2000

private val refusjonMottattCounter = Metrics.meterRegistry.counter("refusjon_mottatt_total")
private val refusjonStatusHentetCounter = Metrics.meterRegistry.counter("refusjon_status_hentet_total")
private val refusjonVedleggLastetNedCounter =
    Metrics.meterRegistry.counter("refusjon_vedlegg_lastet_ned_total")

class RefusjonApi(
    private val database: Database,
    private val refusjonDb: RefusjonDb,
    private val clamAvClient: ClamAvClient,
    private val altinnTilgangerClient: AltinnTilgangerClient,
) {
    private val log = logger()

    suspend fun RoutingContext.sendInnRefusjon(soknadId: UUID) {
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

        if (soknad.status != SoknadStatus.godkjent) {
            call.respond(HttpStatusCode.Conflict, "Søknaden må være godkjent før du kan sende refusjonskrav")
            return
        }

        val multipart = call.receiveMultipart()
        var utgifter: String? = null
        var belopRaw: String? = null
        data class UploadedFile(val filnavn: String, val innhold: ByteArray)
        val filer = mutableListOf<UploadedFile>()

        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> when (part.name) {
                    "utgifter" -> utgifter = part.value
                    "belop" -> belopRaw = part.value
                }

                is PartData.FileItem -> {
                    val filnavn = part.originalFileName?.ifBlank { null } ?: "vedlegg.pdf"
                    val bytes = part.provider().readRemaining().readByteArray()
                    filer.add(UploadedFile(filnavn, bytes))
                }

                else -> {}
            }
            part.dispose()
        }

        val utgifterVerdi = utgifter?.trim()
        if (utgifterVerdi.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Du må beskrive utgiftene")
            return
        }
        if (utgifterVerdi.length > MAKS_UTGIFTER_LENGDE) {
            call.respond(HttpStatusCode.BadRequest, "Beskrivelsen er for lang")
            return
        }

        val belopOre = try {
            belopKronerTilOre(belopRaw ?: "")
        } catch (e: UgyldigBelopException) {
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Ugyldig beløp")
            return
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

        refusjonDb.lagreRefusjonskrav(
            soknadId = soknadId,
            belopOre = belopOre,
            utgifter = utgifterVerdi,
            filer = filer.map { RefusjonsfilInput(it.filnavn, it.innhold) },
        )

        log.info("Mottok refusjonskrav med {} vedlegg: soknadId={}, belopOre={}", filer.size, soknadId, belopOre)
        refusjonMottattCounter.increment()

        call.respond(HttpStatusCode.Created)
    }

    /**
     * Returnerer metadata om siste refusjonskrav (beløp, utgifter, tidspunkt, vedleggsliste).
     * Filinnhold følger ikke med – vedlegg lastes ned separat via [lastNedVedlegg].
     */
    suspend fun RoutingContext.hentRefusjonStatus(soknadId: UUID) {
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

        val status = refusjonDb.finnRefusjonskravStatus(soknadId)
        if (status == null) {
            call.respond(HttpStatusCode.NoContent)
            return
        }

        refusjonStatusHentetCounter.increment()
        call.respond(
            RefusjonStatusDto(
                belopKroner = status.belopKroner,
                utgifter = status.utgifter,
                opprettet = status.opprettet,
                kontonummer = status.kontonummer,
                vedlegg = status.vedlegg.map {
                    RefusjonVedleggDto(id = it.id, filnavn = it.filnavn, storrelse = it.storrelse)
                },
            )
        )
    }

    /**
     * Laster ned ett refusjonsvedlegg. Krever tilgang til virksomheten OG at vedlegget
     * tilhører søknadens refusjonskrav (eierskapssjekk i [RefusjonDb.hentRefusjonsvedlegg]),
     * slik at man ikke kan hente vedlegg på tvers av søknader.
     */
    suspend fun RoutingContext.lastNedVedlegg(soknadId: UUID, vedleggId: UUID) {
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

        val vedlegg = refusjonDb.hentRefusjonsvedlegg(soknadId, vedleggId)
        if (vedlegg == null) {
            call.respond(HttpStatusCode.NotFound, "vedlegg ikke funnet")
            return
        }

        refusjonVedleggLastetNedCounter.increment()
        call.response.header(
            HttpHeaders.ContentDisposition,
            ContentDisposition.Attachment
                .withParameter(ContentDisposition.Parameters.FileName, vedlegg.filnavn)
                .toString(),
        )
        call.respondBytes(vedlegg.innhold, ContentType.Application.Pdf)
    }
}

@Serializable
data class RefusjonStatusDto(
    val belopKroner: Long,
    val utgifter: String,
    val opprettet: String,
    val kontonummer: String?,
    val vedlegg: List<RefusjonVedleggDto>,
)

@Serializable
data class RefusjonVedleggDto(
    val id: String,
    val filnavn: String,
    val storrelse: Int,
)
