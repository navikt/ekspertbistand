package no.nav.ekspertbistand.refusjon

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.saksbehandling.SakIkkeFunnetException
import no.nav.ekspertbistand.saksbehandling.SakKildeArenaException
import no.nav.ekspertbistand.saksbehandling.SoknadIkkeFunnetException
import no.nav.ekspertbistand.saksbehandling.SoknadIkkeGodkjentException
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.findSoknadById
import no.nav.ekspertbistand.soknad.subjectToken
import no.nav.ekspertbistand.vedlegg.Opplasting
import no.nav.ekspertbistand.vedlegg.erGyldigPdf
import no.nav.ekspertbistand.vedlegg.forStorFilMelding
import no.nav.ekspertbistand.vedlegg.lesOpplasting
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.Locale
import java.util.UUID

private const val MAKS_FIL_STORRELSE_BYTES = 10 * 1024 * 1024
private const val MAKS_ANTALL_FILER = 5
private const val REFUSJONSKRAV_FINNES_MELDING = "Refusjonskrav er allerede sendt inn for denne saken"
private const val ARENA_MELDING = "Refusjonskrav for denne saken sendes ikke inn her"
private const val BELOP_MANGLER_MELDING = "Fant ikke innvilget beløp for saken"

private val refusjonMottattCounter = Metrics.meterRegistry.counter("refusjon_mottatt_total")
private val refusjonStatusHentetCounter = Metrics.meterRegistry.counter("refusjon_status_hentet_total")
private val refusjonVedleggLastetNedCounter =
    Metrics.meterRegistry.counter("refusjon_vedlegg_lastet_ned_total")

internal enum class Avvisningsarsak { ARENA, OVER_INNVILGET, BELOP_MANGLER }

internal fun refusjonAvvistCounter(arsak: Avvisningsarsak) =
    Metrics.meterRegistry.counter("refusjon_avvist_total", "arsak", arsak.name)

private fun formaterKroner(kroner: Int): String = "%,d".format(Locale.forLanguageTag("nb-NO"), kroner)

@Serializable
data class MaksBelopDto(val maksBelopKroner: Int)

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

        if (refusjonDb.finnRefusjonskravStatus(soknadId) != null) {
            call.respond(HttpStatusCode.Conflict, REFUSJONSKRAV_FINNES_MELDING)
            return
        }

        // Tidlig sjekk før filene leses og skannes. lagreRefusjonskrav kontrollerer på nytt under lås.
        val maksBelopKroner = try {
            refusjonDb.hentMaksBelopKroner(soknadId)
        } catch (_: SakIkkeFunnetException) {
            call.respond(HttpStatusCode.NotFound, "sak ikke funnet")
            return
        } catch (_: SakKildeArenaException) {
            avvis(Avvisningsarsak.ARENA, HttpStatusCode.Conflict, ARENA_MELDING)
            return
        } catch (_: InnvilgetBelopManglerException) {
            avvis(Avvisningsarsak.BELOP_MANGLER, HttpStatusCode.Conflict, BELOP_MANGLER_MELDING)
            return
        }

        val opplasting = call.lesOpplasting(MAKS_ANTALL_FILER, MAKS_FIL_STORRELSE_BYTES, setOf("belop"))
        val (filer, felter) = when (opplasting) {
            is Opplasting.Ok -> opplasting.filer to opplasting.felter
            is Opplasting.ForMangeFiler -> {
                call.respond(HttpStatusCode.BadRequest, "Maks $MAKS_ANTALL_FILER filer tillatt")
                return
            }
            is Opplasting.ForStorFil -> {
                call.respond(HttpStatusCode.PayloadTooLarge, forStorFilMelding(opplasting.filnavn))
                return
            }
        }

        val belopOre = try {
            belopKronerTilOre(felter["belop"] ?: "")
        } catch (e: UgyldigBelopException) {
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Ugyldig beløp")
            return
        }

        if (belopOre > maksBelopKroner.toLong() * 100) {
            avvisOverInnvilget(soknadId, belopOre, maksBelopKroner)
            return
        }

        if (filer.isEmpty()) {
            call.respond(HttpStatusCode.BadRequest, "Minst én fil må lastes opp")
            return
        }

        for ((filnavn, bytes) in filer) {
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
                    log.error("ClamAV feilet for fil: soknadId={}", soknadId)
                    call.respond(HttpStatusCode.ServiceUnavailable, "Virusskanning feilet, prøv igjen")
                    return
                }

                ClamAvClient.ScanStatus.OK -> {}
            }
        }

        try {
            refusjonDb.lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = belopOre,
                filer = filer.map { RefusjonsfilInput(it.filnavn, it.innhold) },
            )
        } catch (_: SoknadIkkeFunnetException) {
            call.respond(HttpStatusCode.NotFound, "søknad ikke funnet")
            return
        } catch (_: SakIkkeFunnetException) {
            call.respond(HttpStatusCode.NotFound, "sak ikke funnet")
            return
        } catch (_: SoknadIkkeGodkjentException) {
            call.respond(HttpStatusCode.Conflict, "Søknaden må være godkjent før du kan sende refusjonskrav")
            return
        } catch (_: RefusjonskravFinnesAlleredeException) {
            call.respond(HttpStatusCode.Conflict, REFUSJONSKRAV_FINNES_MELDING)
            return
        } catch (_: SakKildeArenaException) {
            avvis(Avvisningsarsak.ARENA, HttpStatusCode.Conflict, ARENA_MELDING)
            return
        } catch (_: InnvilgetBelopManglerException) {
            avvis(Avvisningsarsak.BELOP_MANGLER, HttpStatusCode.Conflict, BELOP_MANGLER_MELDING)
            return
        } catch (e: BelopOverInnvilgetException) {
            avvisOverInnvilget(soknadId, belopOre, e.maksBelopKroner)
            return
        }

        log.info("Mottok refusjonskrav med {} vedlegg: soknadId={}, belopOre={}", filer.size, soknadId, belopOre)
        refusjonMottattCounter.increment()

        call.respond(HttpStatusCode.Created)
    }

    /**
     * Maksbeløpet arbeidsgiver kan kreve: innvilget beløp fra vedtaket. Tilgang sjekkes før saken slås opp,
     * så svarkodene ikke avslører kilde eller vedtak for virksomheter brukeren ikke har tilgang til.
     */
    suspend fun RoutingContext.hentMaksBelop(soknadId: UUID) {
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

        val maksBelopKroner = try {
            refusjonDb.hentMaksBelopKroner(soknadId)
        } catch (_: SakIkkeFunnetException) {
            call.respond(HttpStatusCode.NotFound, "sak ikke funnet")
            return
        } catch (_: SakKildeArenaException) {
            call.respond(HttpStatusCode.Conflict, ARENA_MELDING)
            return
        } catch (_: InnvilgetBelopManglerException) {
            call.respond(HttpStatusCode.Conflict, BELOP_MANGLER_MELDING)
            return
        }

        call.respond(MaksBelopDto(maksBelopKroner = maksBelopKroner))
    }

    private suspend fun RoutingContext.avvis(arsak: Avvisningsarsak, status: HttpStatusCode, melding: String) {
        refusjonAvvistCounter(arsak).increment()
        call.respond(status, melding)
    }

    private suspend fun RoutingContext.avvisOverInnvilget(soknadId: UUID, belopOre: Long, maksBelopKroner: Int) {
        val maksOre = maksBelopKroner.toLong() * 100
        log.info("Avviste refusjonskrav over innvilget beløp: soknadId={}, belopOre={}, maksOre={}", soknadId, belopOre, maksOre)
        avvis(
            Avvisningsarsak.OVER_INNVILGET,
            HttpStatusCode.BadRequest,
            "Beløpet kan ikke være høyere enn innvilget beløp (${formaterKroner(maksBelopKroner)} kr)",
        )
    }

    /**
     * Returnerer metadata om siste refusjonskrav (beløp, tidspunkt, vedleggsliste).
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
