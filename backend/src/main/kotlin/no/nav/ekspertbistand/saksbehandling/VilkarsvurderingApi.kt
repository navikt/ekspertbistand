package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
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
 * Begge sjekker i tillegg Tilgangsmaskinen (kjerneregler). GET sporingslogger til ArcSight.
 * Eksterne tilgangssjekker er fail-closed (503). Status og vilkårsrad sjekkes på den låste
 * sakraden før [EventData.VilkarsvurderingOppdatert] publiseres.
 *
 * PATCH svarer 202 Accepted: selve lagringen gjøres asynkront av
 * [no.nav.ekspertbistand.event.handlers.OppdaterVilkarsvurdering]. Tidspunktet for vurderingen
 * settes her og ligger på eventen, og er det som lagres i databasen.
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
                        publiserVilkarsvurdering(
                            sakId = sakId,
                            request = request,
                            navIdent = principal.navIdent,
                            tidspunkt = Clock.System.now(),
                        )
                    }

                    when (resultat) {
                        PubliserVilkarResultat.Publisert -> {
                            log.info("Vilkårsvurdering publisert for sakId={}", sakId)
                            call.respond(HttpStatusCode.Accepted)
                        }

                        PubliserVilkarResultat.SakIkkeFunnet ->
                            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                        PubliserVilkarResultat.VilkarIkkeFunnet ->
                            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke vilkår på saken"))

                        PubliserVilkarResultat.IkkeUnderBehandling ->
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

sealed interface PubliserVilkarResultat {
    data object Publisert : PubliserVilkarResultat
    data object SakIkkeFunnet : PubliserVilkarResultat
    data object VilkarIkkeFunnet : PubliserVilkarResultat
    data object IkkeUnderBehandling : PubliserVilkarResultat
}

/**
 * Låser saken, sjekker status og at vilkårsraden finnes, og publiserer
 * [EventData.VilkarsvurderingOppdatert] i kallerens transaksjon. Selve lagringen gjøres av
 * [no.nav.ekspertbistand.event.handlers.OppdaterVilkarsvurdering].
 * [tidspunkt] settes av endepunktet og er det som lagres i databasen.
 */
@OptIn(ExperimentalTime::class)
fun JdbcTransaction.publiserVilkarsvurdering(
    sakId: UUID,
    request: VilkarsvurderingRequest,
    navIdent: String,
    tidspunkt: Instant,
): PubliserVilkarResultat {
    val sak = SakTable
        .select(SakTable.soknadId, SakTable.status)
        .where { SakTable.sakId eq sakId }
        .forUpdate(ForUpdateOption.ForUpdate)
        .singleOrNull()
        ?: return PubliserVilkarResultat.SakIkkeFunnet

    if (sak[SakTable.status] != Saksstatus.UNDER_BEHANDLING.name) {
        return PubliserVilkarResultat.IkkeUnderBehandling
    }

    if (!vilkarFinnes(sakId, request.vilkar)) {
        return PubliserVilkarResultat.VilkarIkkeFunnet
    }

    publishEventQueue(
        EventData.VilkarsvurderingOppdatert(
            sakId = sakId.toString(),
            soknadId = sak[SakTable.soknadId].toString(),
            vurdering = request.copy(notat = request.notat?.trim()?.ifEmpty { null }),
            vurdertAvIdent = navIdent,
            tidspunkt = tidspunkt,
        )
    )
    return PubliserVilkarResultat.Publisert
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
