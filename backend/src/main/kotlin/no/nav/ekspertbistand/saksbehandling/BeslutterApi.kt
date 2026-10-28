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
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
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

private val log = LoggerFactory.getLogger("BeslutterApi")

const val FORELOPIG_VEDTAK_ENDRET = "FORELOPIG_VEDTAK_ENDRET"
const val MAKS_FORKLARING_LENGDE = 1000

/**
 * POST /api/saksbehandling/v1/saker/{sakId}/vedtak => beslutter fatter vedtaket
 * POST /api/saksbehandling/v1/saker/{sakId}/retur  => beslutter sender foreløpig vedtak i retur
 *
 * Begge krever:
 * - rollen [Role.BESLUTTER]
 * - tilgang til sakens behandlende enhet og i Tilgangsmaskinen (kjerneregler)
 * - at saken har kilde [KildeTilBehandling.EKSPERTBISTAND]
 * - at innlogget bruker er beslutter på saken ([sjekkErBeslutterPåSak])
 * - at saken er [Saksstatus.TIL_BESLUTNING] og at `forelopigVedtakVersjon` er siste versjon.
 *   Dette sjekkes på den låste sakraden.
 *
 * Vedtak krever i tillegg at innlogget ikke sendte siste foreløpige vedtak (topartskontroll).
 * Retur sjekker ikke habilitet, fordi den ikke fatter noe vedtak.
 *
 * Svarer 202 Accepted: lagringen gjøres asynkront av [no.nav.ekspertbistand.event.handlers.FattVedtak]
 * og [no.nav.ekspertbistand.event.handlers.ReturnerForelopigVedtak]. Forklaringen logges ikke.
 * Se `specifications/beslutter_vedtak.md`.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureBeslutterApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/saker/{sakId}") {
                post("/vedtak") {
                    val (principal, sakId) = call.sjekkBeslutterTilgang(database, tilgangsmaskinClient)
                        ?: return@post
                    val request = call.mottaGyldig<VedtakRequest>("ugyldig vedtak") ?: return@post

                    val resultat = transaction(database) {
                        publiserBeslutning(
                            sakId = sakId,
                            forelopigVedtakVersjon = request.forelopigVedtakVersjon,
                            fattetAvIdent = principal.navIdent,
                        ) { soknadId, forelopigVedtak ->
                            EventData.VedtakFattet(
                                sakId = sakId.toString(),
                                soknadId = soknadId.toString(),
                                forelopigVedtakVersjon = forelopigVedtak.versjon,
                                utfall = forelopigVedtak.utfall.saksstatus,
                                fattetAvIdent = principal.navIdent,
                                fattetAvNavn = principal.navn,
                                tidspunkt = Clock.System.now(),
                            )
                        }
                    }
                    call.svar(sakId, "Vedtak", resultat)
                }

                post("/retur") {
                    val (principal, sakId) = call.sjekkBeslutterTilgang(database, tilgangsmaskinClient)
                        ?: return@post
                    val request = call.mottaGyldig<ReturRequest>("ugyldig retur")?.let(::validerRetur)
                        ?: return@post

                    val resultat = transaction(database) {
                        publiserBeslutning(
                            sakId = sakId,
                            forelopigVedtakVersjon = request.forelopigVedtakVersjon,
                            fattetAvIdent = null,
                        ) { soknadId, forelopigVedtak ->
                            EventData.ForelopigVedtakReturnert(
                                sakId = sakId.toString(),
                                soknadId = soknadId.toString(),
                                forelopigVedtakVersjon = forelopigVedtak.versjon,
                                aarsaker = request.aarsaker,
                                forklaring = request.forklaring,
                                returnertAvIdent = principal.navIdent,
                                returnertAvNavn = principal.navn,
                                tidspunkt = Clock.System.now(),
                            )
                        }
                    }
                    call.svar(sakId, "Retur", resultat)
                }
            }
        }
    }
}

/**
 * Rolle, enhet, Tilgangsmaskinen, kilde og tildeling, i den rekkefølgen. Svarer selv og
 * returnerer null ved avslag.
 */
private suspend fun ApplicationCall.sjekkBeslutterTilgang(
    database: Database,
    tilgangsmaskinClient: TilgangsmaskinClient,
): Pair<AzureAdPrincipal, UUID>? {
    val principal = principalMedRolle(Role.BESLUTTER) ?: return null
    val sakId = sakIdParameter() ?: return null

    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
    if (sak == null) {
        respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
        return null
    }
    if (!sjekkTilgangTilEnhet(principal, sak)) return null
    if (!sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return null
    if (sak.kildeTilBehandling != KildeTilBehandling.EKSPERTBISTAND) {
        respond(
            HttpStatusCode.Conflict,
            mapOf("kode" to SAK_UGYLDIG_KILDE, "message" to "Saken behandles i Arena"),
        )
        return null
    }
    if (!sjekkErBeslutterPåSak(principal, sak)) return null
    return principal to sakId
}

/** Leser og validerer kroppen. Svarer 400 og returnerer null hvis den ikke kan leses. */
private suspend inline fun <reified T : Any> ApplicationCall.mottaGyldig(feilmelding: String): T? {
    val request = try {
        receive<T>()
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        respond(HttpStatusCode.BadRequest, mapOf("message" to feilmelding))
        return null
    }
    valider(request)
    return request
}

/**
 * Trimmer forklaringen og sjekker at årsaker og forklaring er fylt ut. Kaster
 * [UgyldigInputException], som gir 400.
 */
internal fun validerRetur(request: ReturRequest): ReturRequest {
    val forklaring = request.forklaring.trim()
    if (request.aarsaker.isEmpty()) throw UgyldigInputException("aarsaker", "påkrevd")
    if (forklaring.isEmpty()) throw UgyldigInputException("forklaring", "påkrevd")
    if (forklaring.length > MAKS_FORKLARING_LENGDE) {
        throw UgyldigInputException("forklaring", "for lang (over $MAKS_FORKLARING_LENGDE tegn)")
    }
    return request.copy(forklaring = forklaring)
}

sealed interface PubliserBeslutningResultat {
    data object Publisert : PubliserBeslutningResultat
    data object SakIkkeFunnet : PubliserBeslutningResultat
    data class Avvist(val hindring: BeslutningHindring) : PubliserBeslutningResultat
}

/**
 * Låser saken, sjekker [beslutningHindring] og publiserer eventen fra [lagEvent] i kallerens
 * transaksjon. [fattetAvIdent] er null ved retur. [lagEvent] får søknaden og siste foreløpige vedtak.
 */
@OptIn(ExperimentalTime::class)
fun JdbcTransaction.publiserBeslutning(
    sakId: UUID,
    forelopigVedtakVersjon: Int,
    fattetAvIdent: String?,
    lagEvent: (soknadId: UUID, forelopigVedtak: SisteForelopigVedtakRef) -> EventData,
): PubliserBeslutningResultat {
    val sak = SakTable
        .select(SakTable.soknadId, SakTable.status)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?: return PubliserBeslutningResultat.SakIkkeFunnet

    val siste = hentSisteForelopigVedtakRef(sakId)
    val hindring = beslutningHindring(
        status = Saksstatus.valueOf(sak[SakTable.status]),
        siste = siste,
        forelopigVedtakVersjon = forelopigVedtakVersjon,
        fattetAvIdent = fattetAvIdent,
    )
    if (hindring != null || siste == null) {
        return PubliserBeslutningResultat.Avvist(hindring ?: BeslutningHindring.FORELOPIG_VEDTAK_ENDRET)
    }

    publishEventQueue(lagEvent(sak[SakTable.soknadId], siste))
    return PubliserBeslutningResultat.Publisert
}

private suspend fun ApplicationCall.svar(sakId: UUID, handling: String, resultat: PubliserBeslutningResultat) {
    when (resultat) {
        PubliserBeslutningResultat.Publisert -> {
            log.info("{} publisert for sakId={}", handling, sakId)
            respond(HttpStatusCode.Accepted)
        }

        PubliserBeslutningResultat.SakIkkeFunnet ->
            respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

        is PubliserBeslutningResultat.Avvist -> {
            log.info("{} for sakId={} avvist: {}", handling, sakId, resultat.hindring)
            when (resultat.hindring) {
                BeslutningHindring.UGYLDIG_STATUS -> respond(
                    HttpStatusCode.Conflict,
                    mapOf("kode" to SAK_UGYLDIG_STATUS, "message" to "Saken er ikke til beslutning"),
                )

                BeslutningHindring.FORELOPIG_VEDTAK_ENDRET -> respond(
                    HttpStatusCode.Conflict,
                    mapOf("kode" to FORELOPIG_VEDTAK_ENDRET, "message" to "Det foreløpige vedtaket er endret"),
                )

                BeslutningHindring.INHABIL -> respondTilgangAvvist(
                    kode = BESLUTTER_INHABIL,
                    begrunnelse = "Du sendte det foreløpige vedtaket og kan ikke fatte vedtaket",
                )
            }
        }
    }
}

@Serializable
data class VedtakRequest(
    val forelopigVedtakVersjon: Int,
)

@Serializable
data class ReturRequest(
    val forelopigVedtakVersjon: Int,
    val aarsaker: Set<ReturArsak>,
    val forklaring: String,
)
