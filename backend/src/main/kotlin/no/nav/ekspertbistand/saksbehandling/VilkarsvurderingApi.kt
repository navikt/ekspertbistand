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
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private val log = LoggerFactory.getLogger("VilkarsvurderingApi")

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
 * - at innlogget bruker er saksbehandler på saken ([sjekkErSaksbehandlerPåSak])
 * - at saken er [Saksstatus.UNDER_BEHANDLING]
 *
 * Begge sjekker i tillegg Tilgangsmaskinen (kjerneregler) og sporingslogger til ArcSight.
 * Eksterne tilgangssjekker er fail-closed (503). Status sjekkes på den låste
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
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val sakId = call.sakIdParameter() ?: return@get

                    val sakTilgangsgrunnlag = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    if (!call.sjekkTilgangTilEnhet(principal, sakTilgangsgrunnlag)) return@get
                    if (!call.sjekkTilgangsmaskin(principal, sakTilgangsgrunnlag, tilgangsmaskinClient)) return@get

                    val vurderinger = transaction(database) { hentVilkarsvurdering(sakId) }
                    auditClient.loggOppslag(
                        navIdent = principal.navIdent,
                        fnr = sakTilgangsgrunnlag.ansattFnr,
                        tillatt = true,
                        melding = "Saksbehandler har sett vilkårsvurdering i sak om ekspertbistand",
                    )
                    call.respond(vurderinger)
                }

                patch {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@patch
                    val sakId = call.sakIdParameter() ?: return@patch

                    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@patch call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    if (!call.sjekkTilgangTilEnhet(principal, sak)) return@patch
                    if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return@patch
                    if (!call.sjekkErSaksbehandlerPåSak(principal, sak)) return@patch

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
                                fnr = sak.ansattFnr,
                                tillatt = true,
                                melding = "Saksbehandler har vurdert vilkår i sak om ekspertbistand",
                                event = CefMessageEvent.UPDATE,
                            )
                            call.respond(resultat.vurdering)
                        }

                        OppdaterVilkarResultat.SakIkkeFunnet ->
                            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                        OppdaterVilkarResultat.VilkarIkkeFunnet ->
                            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke vilkår på saken"))

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
    data object VilkarIkkeFunnet : OppdaterVilkarResultat
    data object IkkeUnderBehandling : OppdaterVilkarResultat
}

/**
 * Låser saken, sjekker status, oppdaterer vurderingen og publiserer
 * [EventData.VilkarsvurderingOppdatert] i kallerens transaksjon. Oppdaterer kun en eksisterende
 * vilkårsrad (opprettet av [opprettVilkarForSak]); finnes ikke raden, legges den ikke til.
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

    if (sak[SakTable.status] != Saksstatus.UNDER_BEHANDLING.name) {
        return OppdaterVilkarResultat.IkkeUnderBehandling
    }

    val notat = request.notat?.trim()?.ifEmpty { null }

    val oppdatert = SaksvilkarTable.update({
        (SaksvilkarTable.sakId eq sakId) and (SaksvilkarTable.vilkarId eq request.vilkar.name)
    }) {
        it[godkjent] = request.godkjent
        it[SaksvilkarTable.notat] = notat
        it[vurdertTidspunkt] = tidspunkt
        it[vurdertAvIdent] = navIdent
    }
    if (oppdatert == 0) {
        return OppdaterVilkarResultat.VilkarIkkeFunnet
    }
    SakTable.update({ SakTable.sakId eq sakId }) {
        it[sistEndret] = tidspunkt
    }
    publishEventQueue(
        EventData.VilkarsvurderingOppdatert(
            sakId = sakId.toString(),
            soknadId = sak[SakTable.soknadId].toString(),
            vurdering = request.copy(notat = notat),
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
