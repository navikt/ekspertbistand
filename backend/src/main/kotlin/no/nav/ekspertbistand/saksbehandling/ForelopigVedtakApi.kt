package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.core.JoinType
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

private val log = LoggerFactory.getLogger("ForelopigVedtakApi")

const val SAK_UGYLDIG_KILDE = "SAK_UGYLDIG_KILDE"
const val VILKAR_IKKE_VURDERT = "VILKAR_IKKE_VURDERT"
const val UGYLDIG_SOKNADSSUM = "UGYLDIG_SOKNADSSUM"
const val BELOP_OVER_SATS = "BELOP_OVER_SATS"

private const val STANDARDTEKSTER_RESSURS = "/dokumentmaler/standardtekster/forelopig_vedtak.json"

/**
 * POST /api/saksbehandling/v1/saker/{sakId}/forelopig-vedtak => sender saken til beslutter
 * GET  /api/saksbehandling/v1/forelopig-vedtak/oppsett        => standardtekster og maks satser
 *
 * POST krever:
 * - rollen [Role.SAKSBEHANDLER]
 * - tilgang til sakens behandlende enhet og i Tilgangsmaskinen (kjerneregler)
 * - at saken har kilde [KildeTilBehandling.EKSPERTBISTAND]
 * - at innlogget bruker er saksbehandler på saken ([sjekkErSaksbehandlerPåSak])
 * - gyldige felter for utfallet, se [validerFelter]
 * - at saken er [Saksstatus.UNDER_BEHANDLING], at alle vilkår er vurdert og at beløpet er
 *   innenfor søknadssummen og gjeldende sats. Dette sjekkes på den låste sakraden.
 *
 * POST svarer 202 Accepted: lagringen gjøres asynkront av
 * [no.nav.ekspertbistand.event.handlers.LagreForelopigVedtak]. Begrunnelsene logges ikke.
 * Se `specifications/forelopig_vedtak.md`.
 *
 * Standardtekstene leses og valideres her, så appen ikke starter hvis en tekst mangler.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureForelopigVedtakApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val oppsett = ForelopigVedtakOppsett(
        standardtekster = lastStandardtekster(),
        maksSatser = MAKS_SATSER,
    )

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            get("/api/saksbehandling/v1/forelopig-vedtak/oppsett") {
                call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@get
                call.respond(oppsett)
            }

            post("/api/saksbehandling/v1/saker/{sakId}/forelopig-vedtak") {
                val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@post
                val sakId = call.sakIdParameter() ?: return@post

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
                    call.receive<ForelopigVedtakRequest>()
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to "ugyldig foreløpig vedtak"),
                    )
                }
                valider(request)
                val gyldig = validerFelter(request)

                val resultat = transaction(database) {
                    publiserForelopigVedtak(
                        sakId = sakId,
                        request = gyldig,
                        navIdent = principal.navIdent,
                        tidspunkt = Clock.System.now(),
                    )
                }

                when (resultat) {
                    PubliserForelopigVedtakResultat.Publisert -> {
                        log.info("Foreløpig vedtak publisert for sakId={}", sakId)
                        call.respond(HttpStatusCode.Accepted)
                    }

                    PubliserForelopigVedtakResultat.SakIkkeFunnet ->
                        call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    is PubliserForelopigVedtakResultat.Avvist -> {
                        log.info("Foreløpig vedtak for sakId={} avvist: {}", sakId, resultat.kode)
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
 * Feltreglene per utfall som kan sjekkes uten databasen. Trimmer begrunnelsene. Kaster
 * [UgyldigInputException] med feltstien ved første brudd, som gir 400.
 */
internal fun validerFelter(request: ForelopigVedtakRequest): ForelopigVedtakRequest {
    val begrunnelseDeltaker = request.begrunnelseDeltaker.trim()
    val begrunnelseArbeidsgiver = request.begrunnelseArbeidsgiver?.trim()?.ifEmpty { null }
    if (begrunnelseDeltaker.isEmpty()) {
        throw UgyldigInputException("begrunnelseDeltaker", "påkrevd")
    }

    when (request.utfall) {
        ForelopigUtfall.INNVILGE -> {
            if (request.innvilgetBelop != null) throw UgyldigInputException("innvilgetBelop", "skal ikke settes")
            if (begrunnelseArbeidsgiver != null) {
                throw UgyldigInputException("begrunnelseArbeidsgiver", "skal ikke settes")
            }
            validerTiltaksperiode(request.tiltaksperiodeFra, request.tiltaksperiodeTil)
        }

        ForelopigUtfall.INNVILGE_REDUSERT -> {
            if (begrunnelseArbeidsgiver == null) throw UgyldigInputException("begrunnelseArbeidsgiver", "påkrevd")
            val sats = validerTiltaksperiode(request.tiltaksperiodeFra, request.tiltaksperiodeTil)
            val belop = request.innvilgetBelop ?: throw UgyldigInputException("innvilgetBelop", "påkrevd")
            if (belop <= 0) throw UgyldigInputException("innvilgetBelop", "må være større enn 0")
            if (belop > sats.belop) throw UgyldigInputException("innvilgetBelop", "over maks sats")
        }

        ForelopigUtfall.AVSLAA -> {
            if (begrunnelseArbeidsgiver == null) throw UgyldigInputException("begrunnelseArbeidsgiver", "påkrevd")
            if (request.innvilgetBelop != null) throw UgyldigInputException("innvilgetBelop", "skal ikke settes")
            if (request.tiltaksperiodeFra != null) throw UgyldigInputException("tiltaksperiodeFra", "skal ikke settes")
            if (request.tiltaksperiodeTil != null) throw UgyldigInputException("tiltaksperiodeTil", "skal ikke settes")
        }
    }

    return request.copy(
        begrunnelseDeltaker = begrunnelseDeltaker,
        begrunnelseArbeidsgiver = begrunnelseArbeidsgiver,
    )
}

/** Returnerer satsen som gjelder på startdatoen. */
private fun validerTiltaksperiode(fra: LocalDate?, til: LocalDate?): MaksSats {
    if (fra == null) throw UgyldigInputException("tiltaksperiodeFra", "påkrevd")
    if (til == null) throw UgyldigInputException("tiltaksperiodeTil", "påkrevd")
    if (fra > til) throw UgyldigInputException("tiltaksperiodeTil", "før startdato")
    if (fra.year != til.year) throw UgyldigInputException("tiltaksperiodeTil", "ikke samme år som startdato")
    return gjeldendeMaksSats(fra) ?: throw UgyldigInputException("tiltaksperiodeFra", "ingen sats gjelder")
}

sealed interface PubliserForelopigVedtakResultat {
    data object Publisert : PubliserForelopigVedtakResultat
    data object SakIkkeFunnet : PubliserForelopigVedtakResultat
    data class Avvist(val kode: String, val melding: String) : PubliserForelopigVedtakResultat
}

/**
 * Låser saken, sjekker status, vilkår og beløp, og publiserer
 * [EventData.ForelopigVedtakSendtTilBeslutter] i kallerens transaksjon. For
 * [ForelopigUtfall.INNVILGE] settes beløpet til søknadssummen. [request] må være validert med
 * [validerFelter]. Redusert beløp som ikke er lavere enn søknadssummen, kaster
 * [UgyldigInputException].
 */
@OptIn(ExperimentalTime::class)
fun JdbcTransaction.publiserForelopigVedtak(
    sakId: UUID,
    request: ForelopigVedtakRequest,
    navIdent: String,
    tidspunkt: Instant,
): PubliserForelopigVedtakResultat {
    val sak = SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(SakTable.soknadId, SakTable.status, SoknadTable.behovForBistandEstimertKostnad)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?: return PubliserForelopigVedtakResultat.SakIkkeFunnet

    if (sak[SakTable.status] != Saksstatus.UNDER_BEHANDLING.name) {
        return PubliserForelopigVedtakResultat.Avvist(SAK_UGYLDIG_STATUS, "Saken er ikke under behandling")
    }

    if (hentVilkarsvurdering(sakId).any { it.godkjent == null }) {
        return PubliserForelopigVedtakResultat.Avvist(VILKAR_IKKE_VURDERT, "Alle vilkår må være vurdert")
    }

    val soknadssum = sak[SoknadTable.behovForBistandEstimertKostnad].trim().toIntOrNull()?.takeIf { it > 0 }
        ?: return PubliserForelopigVedtakResultat.Avvist(
            UGYLDIG_SOKNADSSUM,
            "Søknadssummen kan ikke tolkes som et beløp",
        )

    val innvilgetBelop = when (request.utfall) {
        ForelopigUtfall.INNVILGE -> {
            val sats = requireNotNull(gjeldendeMaksSats(requireNotNull(request.tiltaksperiodeFra)))
            if (soknadssum > sats.belop) {
                return PubliserForelopigVedtakResultat.Avvist(
                    BELOP_OVER_SATS,
                    "Søknadssummen er over maks sats. Velg innvilge med redusert beløp.",
                )
            }
            soknadssum
        }

        ForelopigUtfall.INNVILGE_REDUSERT -> {
            val belop = requireNotNull(request.innvilgetBelop)
            if (belop >= soknadssum) {
                throw UgyldigInputException("innvilgetBelop", "må være lavere enn søknadssummen")
            }
            belop
        }

        ForelopigUtfall.AVSLAA -> null
    }

    publishEventQueue(
        EventData.ForelopigVedtakSendtTilBeslutter(
            sakId = sakId.toString(),
            soknadId = sak[SakTable.soknadId].toString(),
            utfall = request.utfall,
            innvilgetBelop = innvilgetBelop,
            tiltaksperiodeFra = request.tiltaksperiodeFra,
            tiltaksperiodeTil = request.tiltaksperiodeTil,
            begrunnelseDeltaker = request.begrunnelseDeltaker,
            begrunnelseArbeidsgiver = request.begrunnelseArbeidsgiver,
            innstiltAvIdent = navIdent,
            tidspunkt = tidspunkt,
        )
    )
    return PubliserForelopigVedtakResultat.Publisert
}

/**
 * Leser standardtekstene og sjekker at akkurat [UTFALL_MED_STANDARDTEKST] har en tekst til
 * arbeidsgiver som ikke er blank. Kaster ved feil, så appen ikke starter. Begrunnelsen til
 * deltaker, og til arbeidsgiver ved avslag, har ingen standardtekst, fordi den skal skrives for
 * hver enkelt.
 */
internal fun lastStandardtekster(ressurs: String = STANDARDTEKSTER_RESSURS): Map<ForelopigUtfall, Standardtekst> {
    val innhold = requireNotNull(ForelopigVedtakOppsett::class.java.getResource(ressurs)) {
        "Fant ikke standardtekster $ressurs"
    }.readText()
    val tekster = Json.decodeFromString<Map<ForelopigUtfall, Standardtekst>>(innhold)

    require(tekster.keys == UTFALL_MED_STANDARDTEKST) {
        "Standardtekster skal finnes for $UTFALL_MED_STANDARDTEKST, fant ${tekster.keys}"
    }
    tekster.forEach { (utfall, tekst) ->
        require(tekst.arbeidsgiver.isNotBlank()) { "Mangler standardtekst til arbeidsgiver for $utfall" }
    }
    return tekster
}

private val UTFALL_MED_STANDARDTEKST = setOf(ForelopigUtfall.INNVILGE_REDUSERT)

@Serializable
data class ForelopigVedtakRequest(
    val utfall: ForelopigUtfall,
    val innvilgetBelop: Int? = null,
    val tiltaksperiodeFra: LocalDate? = null,
    val tiltaksperiodeTil: LocalDate? = null,
    val begrunnelseDeltaker: String,
    val begrunnelseArbeidsgiver: String? = null,
)

/** Tekstene kan inneholde `{soknadssum}` og `{innvilgetBelop}`, som frontend fletter inn. */
@Serializable
data class Standardtekst(
    val arbeidsgiver: String,
)

@Serializable
data class ForelopigVedtakOppsett(
    val standardtekster: Map<ForelopigUtfall, Standardtekst>,
    val maksSatser: List<MaksSats>,
)
