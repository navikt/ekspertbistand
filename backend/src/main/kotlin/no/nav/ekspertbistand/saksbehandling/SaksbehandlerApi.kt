package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.ArenaBehandlingStatus
import no.nav.ekspertbistand.arena.erArenaSakUnderBehandling
import no.nav.ekspertbistand.entraproxy.Enhet
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.soknad.getRequired
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
suspend fun Application.configureSaksbehandlerApiV1() {
    val entraProxyClient = dependencies.resolve<EntraProxyClient>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val database = dependencies.resolve<Database>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1") {
                get("/meg") {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    call.respond(
                        InnloggetAnsattResponse(
                            id = principal.navIdent,
                            navn = principal.navn,
                            epost = principal.epost ?: "",
                            enheter = principal.enheter.map { it.tilResponse() },
                            gjeldendeEnhet = principal.gjeldendeEnhet.tilResponse(),
                            roller = Role.fromGroups(principal.groups),
                            updatedAt = principal.berikelseUpdatedAt,
                        )
                    )
                }

                get("/soknad/{soknadId}/arena-behandling") {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get

                    val soknadId = call.parameters.getRequired(
                        name = "soknadId",
                        transform = UUID::fromString,
                    ) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig soknadId"))
                        return@get
                    }

                    val sak = transaction(database) { hentSakIdForSoknad(soknadId)?.let { hentSakTilgangsgrunnlag(it) } }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
                    if (!call.sjekkTilgangTilEnhet(principal, sak)) return@get
                    if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return@get

                    val status = transaction(database) {
                        erArenaSakUnderBehandling(soknadId)
                    } ?: ArenaBehandlingStatus(underBehandlingIArena = false)

                    call.respond(status)
                }

                get("/saker/{sakId}/logg") {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val sakId = call.sakIdParameter() ?: return@get

                    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
                    if (!call.sjekkTilgangTilEnhet(principal, sak)) return@get
                    if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return@get

                    val rader = database.hentSakslogg(sakId)
                    val navn = entraProxyClient.slaaOppNavn(rader.mapNotNull { it.ident }.toSet())
                    call.respond(rader.tilSaksloggResponse(navn))
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Serializable
data class InnloggetAnsattResponse(
    val id: String,
    val navn: String,
    val epost: String,
    val enheter: List<AnsattEnhetResponse>,
    val gjeldendeEnhet: AnsattEnhetResponse,
    val roller: Set<Role>,
    /** Når dataene ble hentet fra entra-proxy. Til feilsøking av cachen i `AZURE_AD_PROVIDER`. */
    val updatedAt: Instant,
)

@Serializable
data class AnsattEnhetResponse(
    val id: String,
    val nummer: String,
    val navn: String,
)

private fun Enhet.tilResponse() = AnsattEnhetResponse(id = enhetnummer, nummer = enhetnummer, navn = navn)
