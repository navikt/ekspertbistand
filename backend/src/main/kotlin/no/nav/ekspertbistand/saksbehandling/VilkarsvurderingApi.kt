package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.Regelsett
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.tilgangsmaskin.Tilgangsresultat
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val log = LoggerFactory.getLogger("VilkarsvurderingApi")

const val IKKE_TILDELT_SAK = "IKKE_TILDELT_SAK"

/**
 * GET   /api/saksbehandling/v1/saker/{sakId}/vilkarsvurdering => alle vilkår for saken med vurdering
 * PATCH /api/saksbehandling/v1/saker/{sakId}/vilkarsvurdering => vurderer ett vilkår
 *
 * GET krever:
 * - rollen [Role.SAKSBEHANDLER] eller [Role.BESLUTTER]
 * - tilgang til sakens behandlende enhet ([AzureAdPrincipal.harTilgangTilEnhet])
 *
 * PATCH krever:
 * - rollen [Role.SAKSBEHANDLER]
 * - tilgang til sakens behandlende enhet ([AzureAdPrincipal.harTilgangTilEnhet])
 * - at innlogget bruker er saksbehandler på saken ([erSaksbehandlerPåSak])
 * - at saken er [Saksstatus.UNDER_BEHANDLING]
 *
 * Begge sjekker i tillegg Tilgangsmaskinen (kjerneregler) og sporingslogger til ArcSight.
 * Eksterne tilgangssjekker er fail-closed (503). Saksbehandler og status sjekkes på den låste
 * sakraden i samme transaksjon som vurderingen lagres og [EventData.VilkarsvurderingOppdatert]
 * publiseres.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureVilkarsvurderingApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val auditClient = dependencies.resolve<ArcSightAuditClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/saker/{sakId}/vilkarsvurdering") {
                get {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)
                    if (!principal.harRolle(Role.SAKSBEHANDLER) && !principal.harRolle(Role.BESLUTTER)) {
                        return@get call.respond(HttpStatusCode.Forbidden, KREVER_SAKSBEHANDLER_ELLER_BESLUTTER)
                    }
                    val sakId = call.sakIdParameter() ?: return@get

                    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    if (!call.sjekkTilgangTilEnhet(principal, sakId, sak.behandlendeEnhet)) return@get

                    if (!call.sjekkTilgangsmaskin(principal, sakId, sak.fnr, tilgangsmaskinClient)) return@get

                    val vurderinger = transaction(database) { hentVilkarsvurdering(sakId) }
                    auditClient.loggOppslag(
                        navIdent = principal.navIdent,
                        fnr = sak.fnr,
                        tillatt = true,
                        melding = "Saksbehandler har sett vilkårsvurdering i sak om ekspertbistand",
                    )
                    call.respond(vurderinger)
                }

                patch {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@patch call.respond(HttpStatusCode.Unauthorized)
                    if (!principal.harRolle(Role.SAKSBEHANDLER)) {
                        return@patch call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("message" to "krever rolle saksbehandler"),
                        )
                    }
                    val sakId = call.sakIdParameter() ?: return@patch

                    val request = try {
                        call.receive<VilkarsvurderingRequest>()
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        return@patch call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("message" to "ugyldig vilkårsvurdering"),
                        )
                    }
                    valider(request)

                    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    if (!call.sjekkTilgangTilEnhet(principal, sakId, sak.behandlendeEnhet)) return@patch
                    if (!call.sjekkTilgangsmaskin(principal, sakId, sak.fnr, tilgangsmaskinClient)) return@patch

                    val resultat = transaction(database) {
                        oppdaterVilkarsvurdering(
                            sakId = sakId,
                            request = request,
                            principal = principal,
                            tidspunkt = Clock.System.now(),
                        )
                    }

                    when (resultat) {
                        is OppdaterVilkarResultat.Oppdatert -> {
                            auditClient.loggOppslag(
                                navIdent = principal.navIdent,
                                fnr = sak.fnr,
                                tillatt = true,
                                melding = "Saksbehandler har vurdert vilkår i sak om ekspertbistand",
                                event = CefMessageEvent.UPDATE,
                            )
                            call.respond(resultat.vurdering)
                        }

                        OppdaterVilkarResultat.SakIkkeFunnet ->
                            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                        OppdaterVilkarResultat.IkkeTildelt -> {
                            log.info("Vilkårsvurdering avvist: saksbehandler er ikke tildelt sakId={}", sakId)
                            call.respond(
                                HttpStatusCode.Forbidden,
                                TilgangAvvistResponse(
                                    kode = IKKE_TILDELT_SAK,
                                    begrunnelse = "Du er ikke tildelt saken",
                                ),
                            )
                        }

                        OppdaterVilkarResultat.IkkeUnderBehandling ->
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("message" to "Saken er ikke under behandling"),
                            )
                    }
                }
            }
        }
    }
}

private data class SakTilgangsgrunnlag(
    val behandlendeEnhet: String?,
    val fnr: String,
    val saksbehandlerIndent: String?,
    val beslutterIndent: String?
)

private fun hentSakTilgangsgrunnlag(sakId: UUID): SakTilgangsgrunnlag? =
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(SakTable.behandlendeEnhet, SoknadTable.ansattFnr)
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.let {
            SakTilgangsgrunnlag(
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
                fnr = it[SoknadTable.ansattFnr],
                saksbehandlerIndent = it[SakTable.saksbehandlerIdent],
                beslutterIndent = it[SakTable.beslutterIdent],
            )
        }

/**
 * Alle vilkår i [Vilkar]-rekkefølge. Vilkår uten rad (saken er ikke projisert med `Sak-v3` ennå)
 * returneres som ikke vurdert.
 */
@OptIn(ExperimentalTime::class)
fun hentVilkarsvurdering(sakId: UUID): List<VilkarsvurderingDTO> {
    val rader = SaksvilkarTable.selectAll()
        .where { SaksvilkarTable.sakId eq sakId }
        .associateBy { it[SaksvilkarTable.vilkarId] }

    return Vilkar.entries.map { vilkar ->
        rader[vilkar.name]?.let { rad ->
            VilkarsvurderingDTO(
                vilkar = vilkar,
                godkjent = rad[SaksvilkarTable.godkjent],
                notat = rad[SaksvilkarTable.notat],
                vurdertAvIdent = rad[SaksvilkarTable.vurdertAvIdent],
                vurdertTidspunkt = rad[SaksvilkarTable.vurdertTidspunkt],
            )
        } ?: VilkarsvurderingDTO(vilkar = vilkar)
    }
}

sealed interface OppdaterVilkarResultat {
    data class Oppdatert(val vurdering: VilkarsvurderingDTO) : OppdaterVilkarResultat
    data object SakIkkeFunnet : OppdaterVilkarResultat
    data object IkkeTildelt : OppdaterVilkarResultat
    data object IkkeUnderBehandling : OppdaterVilkarResultat
}

/**
 * Låser saken, sjekker tildeling og status, lagrer vurderingen og publiserer
 * [EventData.VilkarsvurderingOppdatert] i kallerens transaksjon.
 */
@OptIn(ExperimentalTime::class)
fun JdbcTransaction.oppdaterVilkarsvurdering(
    sakId: UUID,
    request: VilkarsvurderingRequest,
    principal: AzureAdPrincipal,
    tidspunkt: Instant,
): OppdaterVilkarResultat {
    val navIdent = principal.navIdent
    val sak = SakTable
        .select(SakTable.soknadId, SakTable.status)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?: return OppdaterVilkarResultat.SakIkkeFunnet

    // Sakraden er låst over, så tildelingen kan ikke endres før vurderingen er lagret.
    if (!erSaksbehandlerPåSak(principal, sakId)) {
        return OppdaterVilkarResultat.IkkeTildelt
    }
    if (sak[SakTable.status] != Saksstatus.UNDER_BEHANDLING.name) {
        return OppdaterVilkarResultat.IkkeUnderBehandling
    }

    val notat = request.notat?.trim()?.ifEmpty { null }

    SaksvilkarTable.upsert {
        it[SaksvilkarTable.sakId] = sakId
        it[vilkarId] = request.vilkar.name
        it[godkjent] = request.godkjent
        it[SaksvilkarTable.notat] = notat
        it[vurdertTidspunkt] = tidspunkt
        it[vurdertAvIdent] = navIdent
    }
    SakTable.update({ SakTable.sakId eq sakId }) {
        it[sistEndret] = tidspunkt
    }
    publishEventQueue(
        EventData.VilkarsvurderingOppdatert(
            sakId = sakId.toString(),
            soknadId = sak[SakTable.soknadId].toString(),
            vilkar = request.vilkar,
            godkjent = request.godkjent,
            notat = notat,
            vurdertAvIdent = navIdent,
            tidspunkt = tidspunkt,
        )
    )

    return OppdaterVilkarResultat.Oppdatert(
        VilkarsvurderingDTO(
            vilkar = request.vilkar,
            godkjent = request.godkjent,
            notat = notat,
            vurdertAvIdent = navIdent,
            vurdertTidspunkt = tidspunkt,
        )
    )
}

/** `godkjent = null` nullstiller vurderingen. */
@Serializable
data class VilkarsvurderingRequest(
    val vilkar: Vilkar,
    val godkjent: Boolean?,
    val notat: String? = null,
)

@OptIn(ExperimentalTime::class)
@Serializable
data class VilkarsvurderingDTO(
    val vilkar: Vilkar,
    val godkjent: Boolean? = null,
    val notat: String? = null,
    val vurdertAvIdent: String? = null,
    val vurdertTidspunkt: Instant? = null,
)

/** Leser `sakId` fra stien. Svarer 400 og returnerer null hvis den ikke er en gyldig UUID. */
private suspend fun ApplicationCall.sakIdParameter(): UUID? {
    val sakId = parameters["sakId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    if (sakId == null) {
        respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig sakId"))
    }
    return sakId
}

/**
 * Sjekker med [AzureAdPrincipal.harTilgangTilEnhet] at saksbehandler har tilgang til sakens
 * behandlende enhet. Saker uten enhet er ikke tilgjengelige for noen. Svarer 403 og returnerer
 * false ved avslag. Fail-closed: feil mot entra-proxy gir 503.
 */
private suspend fun ApplicationCall.sjekkTilgangTilEnhet(
    principal: AzureAdPrincipal,
    sakId: UUID,
    behandlendeEnhet: String?,
): Boolean {
    val harTilgangTilEnhet = try {
        behandlendeEnhet != null && principal.harTilgangTilEnhet(behandlendeEnhet)
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        log.error("Henting av enheter fra entra-proxy feilet ({}), avviser oppslag", e.javaClass.simpleName)
        respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "tilgangskontroll er ikke tilgjengelig"))
        return false
    }
    if (!harTilgangTilEnhet) {
        log.info("Tilgang avvist: saksbehandler mangler tilgang til enhet for sakId={}", sakId)
        respond(
            HttpStatusCode.Forbidden,
            TilgangAvvistResponse(
                kode = IKKE_TILGANG_ENHET,
                begrunnelse = "Du har ikke tilgang til enheten som behandler saken",
            ),
        )
    }
    return harTilgangTilEnhet
}

/**
 * Sjekker at Tilgangsmaskinen (kjerneregler) godtar oppslag på den ansatte. Svarer 403 og
 * returnerer false ved avslag. Fail-closed: feil mot Tilgangsmaskinen gir 503.
 */
private suspend fun ApplicationCall.sjekkTilgangsmaskin(
    principal: AzureAdPrincipal,
    sakId: UUID,
    fnr: String,
    tilgangsmaskinClient: TilgangsmaskinClient,
): Boolean {
    val tilgang = try {
        tilgangsmaskinClient.evaluer(
            userToken = principal.subjectToken,
            brukerIdent = fnr,
            regelsett = Regelsett.KJERNE,
        )
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        log.error("Tilgangskontroll feilet for sakId={}, avviser oppslag", sakId, e)
        respond(
            HttpStatusCode.ServiceUnavailable,
            mapOf("message" to "tilgangskontroll er ikke tilgjengelig"),
        )
        return false
    }

    return when (tilgang) {
        Tilgangsresultat.Innvilget -> true
        is Tilgangsresultat.Avvist -> {
            log.info("Tilgang avvist av Tilgangsmaskinen for sakId={}", sakId)
            respond(
                HttpStatusCode.Forbidden,
                TilgangAvvistResponse(kode = tilgang.kode, begrunnelse = tilgang.begrunnelse),
            )
            false
        }
    }
}

private val KREVER_SAKSBEHANDLER_ELLER_BESLUTTER = mapOf("message" to "krever rolle saksbehandler eller beslutter")
