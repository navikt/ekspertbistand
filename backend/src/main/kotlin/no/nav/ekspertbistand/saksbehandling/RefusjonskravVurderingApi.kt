package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.refusjon.RefusjonVedleggDto
import no.nav.ekspertbistand.refusjon.RefusjonskravArsak
import no.nav.ekspertbistand.refusjon.RefusjonskravStatuskode
import no.nav.ekspertbistand.refusjon.RefusjonskravTable
import no.nav.ekspertbistand.refusjon.hentNyesteRefusjonskrav
import no.nav.ekspertbistand.refusjon.refusjonskravBeskjed
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
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

const val SLUTTRAPPORT_IKKE_BEKREFTET = "SLUTTRAPPORT_IKKE_BEKREFTET"
const val REFUSJONSKRAV_IKKE_NYESTE = "REFUSJONSKRAV_IKKE_NYESTE"
const val REFUSJONSKRAV_ALLEREDE_VURDERT = "REFUSJONSKRAV_ALLEREDE_VURDERT"

@Serializable
data class RefusjonskravVurderingRequest(val arsaker: List<RefusjonskravArsak>)

internal fun validerRefusjonskravVurdering(request: RefusjonskravVurderingRequest): List<RefusjonskravArsak> {
    if (request.arsaker.isEmpty() || request.arsaker.distinct().size != request.arsaker.size) {
        throw UgyldigInputException("arsaker", "Velg minst én årsak. Hver årsak kan bare velges én gang.")
    }
    return RefusjonskravArsak.entries.filter { it in request.arsaker }
}

@OptIn(ExperimentalTime::class)
suspend fun Application.configureRefusjonskravVurderingApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val log = LoggerFactory.getLogger("RefusjonskravVurderingApi")
    routing {
        authenticate(AZURE_AD_PROVIDER) {
            post("/api/saksbehandling/v1/saker/{sakId}/refusjon/{refusjonskravId}/vurdering") {
                val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@post
                val sakId = call.sakIdParameter() ?: return@post
                val kravId = call.parameters["refusjonskravId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig refusjonskravId"))
                val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                    ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
                if (!call.sjekkTilgangTilEnhet(principal, sak)) return@post
                if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return@post
                if (!call.sjekkErSaksbehandlerPåSak(principal, sak)) return@post
                val request = try {
                    call.receive<RefusjonskravVurderingRequest>()
                } catch (e: Exception) {
                    e.rethrowIfCancellation()
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldige arsaker"))
                }
                val arsaker = validerRefusjonskravVurdering(request)
                val resultat = transaction(database) {
                    publiserRefusjonskravVurdering(sakId, kravId, arsaker, principal.navIdent, principal.navn, Clock.System.now())
                }
                log.info("Dokumentkontroll av refusjonskrav på sakId={} ga {}", sakId, resultat.kode)
                call.respond(resultat.status, mapOf("kode" to resultat.kode, "message" to resultat.melding))
            }
        }
    }
}

data class RefusjonskravVurderingResultat(val status: HttpStatusCode, val kode: String, val melding: String)

@OptIn(ExperimentalTime::class)
internal fun JdbcTransaction.publiserRefusjonskravVurdering(
    sakId: UUID,
    kravId: UUID,
    arsaker: List<RefusjonskravArsak>,
    navIdent: String,
    navn: String,
    tidspunkt: Instant,
): RefusjonskravVurderingResultat {
    val sak = SakTable.select(
        SakTable.soknadId, SakTable.status, SakTable.kildeTilBehandling, SakTable.saksbehandlerIdent,
    ).where { SakTable.sakId eq sakId }.forUpdate(ForUpdateOption.ForUpdate).singleOrNull()
        ?: return RefusjonskravVurderingResultat(HttpStatusCode.NotFound, "SAK_IKKE_FUNNET", "Fant ikke sak")
    if (sak[SakTable.kildeTilBehandling] != KildeTilBehandling.EKSPERTBISTAND.name) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Conflict, SAK_UGYLDIG_KILDE, "Saken behandles i Arena")
    }
    if (sak[SakTable.saksbehandlerIdent] != navIdent) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Forbidden, IKKE_TILDELT_SAK, "Du er ikke tildelt saken")
    }
    if (sak[SakTable.status] != Saksstatus.INNVILGET.name) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Conflict, SAK_UGYLDIG_STATUS, "Saken er ikke innvilget")
    }
    val krav = RefusjonskravTable.select(RefusjonskravTable.sakId, RefusjonskravTable.status)
        .where { RefusjonskravTable.id eq kravId }.singleOrNull()
    if (krav == null || krav[RefusjonskravTable.sakId] != sakId) {
        return RefusjonskravVurderingResultat(HttpStatusCode.NotFound, "REFUSJONSKRAV_IKKE_FUNNET", "Fant ikke refusjonskrav")
    }
    if (hentNyesteRefusjonskrav(sakId)?.get(RefusjonskravTable.id)?.value != kravId) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Conflict, REFUSJONSKRAV_IKKE_NYESTE, "Det finnes et nyere refusjonskrav")
    }
    if (krav[RefusjonskravTable.status] != RefusjonskravStatuskode.MOTTATT.name) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Conflict, REFUSJONSKRAV_ALLEREDE_VURDERT, "Refusjonskravet er allerede vurdert")
    }
    val sluttrapportStatus = SluttrapportTable.select(SluttrapportTable.status)
        .where { SluttrapportTable.sakId eq sakId }
        .orderBy(SluttrapportTable.opprettet to SortOrder.DESC, SluttrapportTable.id to SortOrder.DESC)
        .limit(1).singleOrNull()?.get(SluttrapportTable.status)
    if (sluttrapportStatus != SluttrapportStatus.BEKREFTET.name) {
        return RefusjonskravVurderingResultat(HttpStatusCode.Conflict, SLUTTRAPPORT_IKKE_BEKREFTET, "Sluttrapporten må være bekreftet først")
    }
    publishEventQueue(EventData.RefusjonskravNyInnsendingKrevd(
        refusjonskravId = kravId.toString(),
        sakId = sakId.toString(),
        soknadId = sak[SakTable.soknadId].toString(),
        arsaker = arsaker.map { it.tilDomain() },
        begrunnelse = refusjonskravBeskjed(arsaker),
        vurdertAvIdent = navIdent,
        vurdertAvNavn = navn,
        tidspunkt = tidspunkt,
    ))
    return RefusjonskravVurderingResultat(HttpStatusCode.Accepted, "VURDERING_MOTTATT", "Vurderingen er mottatt")
}

@Serializable
data class SaksbehandlingRefusjonDto(
    val refusjonskravId: String,
    val belopKroner: Long,
    val opprettet: String,
    val kontonummer: String?,
    val vedlegg: List<RefusjonVedleggDto>,
    val status: RefusjonskravStatuskode,
    val arsaker: List<RefusjonskravArsak>,
    val begrunnelse: String?,
    val vurdertAvIdent: String?,
    val vurdertAvNavn: String?,
    val vurdertTidspunkt: String?,
)
