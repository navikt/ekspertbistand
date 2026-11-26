package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatus
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val log = LoggerFactory.getLogger("SluttrapportVurderingApi")

const val SLUTTRAPPORT_IKKE_NYESTE = "SLUTTRAPPORT_IKKE_NYESTE"
const val SLUTTRAPPORT_ALLEREDE_VURDERT = "SLUTTRAPPORT_ALLEREDE_VURDERT"
const val MAKS_BEGRUNNELSE_SLUTTRAPPORT = 4000

/**
 * POST /api/saksbehandling/v1/saker/{sakId}/sluttrapport/{sluttrapportId}/vurdering
 *
 * Krever:
 * - rollen [Role.SAKSBEHANDLER]
 * - tilgang til sakens behandlende enhet og i Tilgangsmaskinen (kjerneregler)
 * - at saken har kilde [KildeTilBehandling.EKSPERTBISTAND]
 * - at innlogget bruker er saksbehandler på saken ([sjekkErSaksbehandlerPåSak])
 * - gyldig begrunnelse for utfallet, se [validerFelter]
 * - at saken er [Saksstatus.INNVILGET], og at sluttrapporten er nyeste innsending på saken og har
 *   status [SluttrapportStatus.MOTTATT]. Dette sjekkes på den låste sakraden.
 *
 * Svarer 202 Accepted: lagringen gjøres asynkront av
 * [no.nav.ekspertbistand.event.handlers.LagreSluttrapportBekreftet] og
 * [no.nav.ekspertbistand.event.handlers.LagreSluttrapportNyInnsendingKrevd]. Begrunnelsen logges ikke.
 * Se `specifications/vurdering_sluttrapport.md`.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureSluttrapportVurderingApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            post("/api/saksbehandling/v1/saker/{sakId}/sluttrapport/{sluttrapportId}/vurdering") {
                val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@post
                val sakId = call.sakIdParameter() ?: return@post
                val sluttrapportId = call.parameters["sluttrapportId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "ugyldig sluttrapportId"),
                    )

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

                val request = try {
                    call.receive<SluttrapportVurderingRequest>()
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "ugyldig vurdering av sluttrapport"),
                    )
                }
                valider(request)
                val gyldig = validerFelter(request)

                val resultat = transaction(database) {
                    publiserSluttrapportVurdering(
                        sakId = sakId,
                        sluttrapportId = sluttrapportId,
                        request = gyldig,
                        navIdent = principal.navIdent,
                        navn = principal.navn,
                        tidspunkt = Clock.System.now(),
                    )
                }

                when (resultat) {
                    PubliserSluttrapportVurderingResultat.Publisert -> {
                        log.info("Vurdering av sluttrapport publisert for sakId={}: {}", sakId, gyldig.utfall)
                        call.respond(HttpStatusCode.Accepted)
                    }

                    PubliserSluttrapportVurderingResultat.SakIkkeFunnet ->
                        call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    PubliserSluttrapportVurderingResultat.SluttrapportIkkeFunnet ->
                        call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sluttrapport"))

                    is PubliserSluttrapportVurderingResultat.Avvist -> {
                        log.info("Vurdering av sluttrapport for sakId={} avvist: {}", sakId, resultat.kode)
                        call.respond(
                            HttpStatusCode.Conflict,
                            mapOf("kode" to resultat.kode, "message" to resultat.melding),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Trimmer begrunnelsen og sjekker at den er satt bare ved [SluttrapportUtfall.NY_INNSENDING_KREVES].
 * Kaster [UgyldigInputException] med feltnavnet, som gir 400.
 */
internal fun validerFelter(request: SluttrapportVurderingRequest): SluttrapportVurderingRequest {
    val begrunnelse = request.begrunnelse?.trim()?.ifEmpty { null }
    when (request.utfall) {
        SluttrapportUtfall.BEKREFTET ->
            if (begrunnelse != null) throw UgyldigInputException("begrunnelse", "skal ikke settes")

        SluttrapportUtfall.NY_INNSENDING_KREVES -> {
            if (begrunnelse == null) throw UgyldigInputException("begrunnelse", "påkrevd")
            if (begrunnelse.length > MAKS_BEGRUNNELSE_SLUTTRAPPORT) {
                throw UgyldigInputException("begrunnelse", "for lang (over $MAKS_BEGRUNNELSE_SLUTTRAPPORT tegn)")
            }
        }
    }
    return request.copy(begrunnelse = begrunnelse)
}

sealed interface PubliserSluttrapportVurderingResultat {
    data object Publisert : PubliserSluttrapportVurderingResultat
    data object SakIkkeFunnet : PubliserSluttrapportVurderingResultat
    data object SluttrapportIkkeFunnet : PubliserSluttrapportVurderingResultat
    data class Avvist(val kode: String, val melding: String) : PubliserSluttrapportVurderingResultat
}

/**
 * Låser saken, sjekker at den er [Saksstatus.INNVILGET] og at [sluttrapportId] er nyeste
 * sluttrapport på saken med status [SluttrapportStatus.MOTTATT], og publiserer
 * [EventData.SluttrapportBekreftet] eller [EventData.SluttrapportNyInnsendingKrevd] i kallerens
 * transaksjon. [request] må være validert med [validerFelter].
 *
 * Låsen på sakraden serialiserer vurderingen mot nye innsendinger, som låser samme rad i
 * [hentGodkjentSakIdForUpdate].
 */
@OptIn(ExperimentalTime::class)
fun JdbcTransaction.publiserSluttrapportVurdering(
    sakId: UUID,
    sluttrapportId: UUID,
    request: SluttrapportVurderingRequest,
    navIdent: String,
    navn: String,
    tidspunkt: Instant,
): PubliserSluttrapportVurderingResultat {
    val sak = SakTable
        .select(SakTable.soknadId, SakTable.status)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?: return PubliserSluttrapportVurderingResultat.SakIkkeFunnet

    if (sak[SakTable.status] != Saksstatus.INNVILGET.name) {
        return PubliserSluttrapportVurderingResultat.Avvist(SAK_UGYLDIG_STATUS, "Saken er ikke innvilget")
    }

    val sluttrapporter = SluttrapportTable
        .select(SluttrapportTable.id, SluttrapportTable.status)
        .where { SluttrapportTable.sakId eq sakId }
        .orderBy(
            SluttrapportTable.opprettet to SortOrder.DESC,
            SluttrapportTable.id to SortOrder.DESC,
        )
        .toList()

    val sluttrapport = sluttrapporter.firstOrNull { it[SluttrapportTable.id] == sluttrapportId }
        ?: return PubliserSluttrapportVurderingResultat.SluttrapportIkkeFunnet

    if (sluttrapporter.first()[SluttrapportTable.id] != sluttrapportId) {
        return PubliserSluttrapportVurderingResultat.Avvist(
            SLUTTRAPPORT_IKKE_NYESTE,
            "Det er kommet en nyere sluttrapport. Last inn siden på nytt.",
        )
    }

    if (sluttrapport[SluttrapportTable.status] != SluttrapportStatus.MOTTATT.name) {
        return PubliserSluttrapportVurderingResultat.Avvist(
            SLUTTRAPPORT_ALLEREDE_VURDERT,
            "Sluttrapporten er allerede vurdert",
        )
    }

    val soknadId = sak[SakTable.soknadId].toString()
    publishEventQueue(
        when (request.utfall) {
            SluttrapportUtfall.BEKREFTET -> EventData.SluttrapportBekreftet(
                sluttrapportId = sluttrapportId.toString(),
                sakId = sakId.toString(),
                soknadId = soknadId,
                vurdertAvIdent = navIdent,
                vurdertAvNavn = navn,
                tidspunkt = tidspunkt,
            )

            SluttrapportUtfall.NY_INNSENDING_KREVES -> EventData.SluttrapportNyInnsendingKrevd(
                sluttrapportId = sluttrapportId.toString(),
                sakId = sakId.toString(),
                soknadId = soknadId,
                vurdertAvIdent = navIdent,
                vurdertAvNavn = navn,
                begrunnelse = requireNotNull(request.begrunnelse),
                tidspunkt = tidspunkt,
            )
        }
    )
    return PubliserSluttrapportVurderingResultat.Publisert
}

@Serializable
enum class SluttrapportUtfall { BEKREFTET, NY_INNSENDING_KREVES }

@Serializable
data class SluttrapportVurderingRequest(
    val utfall: SluttrapportUtfall,
    val begrunnelse: String? = null,
)

/** Sluttrapporten slik saksbehandler ser den. Arbeidsgiverens API bruker `SluttrapportStatusDto`. */
@Serializable
data class SaksbehandlingSluttrapportDto(
    val sluttrapportId: String,
    val filnavn: String,
    val opprettet: String,
    val storrelse: Int,
    val status: SluttrapportStatus,
    val vurdertAvIdent: String?,
    val vurdertAvNavn: String?,
    val vurdertTidspunkt: String?,
    val begrunnelse: String?,
)
