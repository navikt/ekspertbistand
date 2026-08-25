package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.arena.ArenaBehandlingStatus
import no.nav.ekspertbistand.arena.erArenaSakUnderBehandling
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.soknad.getRequired
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("SaksbehandlerApi")

suspend fun Application.configureSaksbehandlerApiV1() {
    val entraProxyClient = dependencies.resolve<EntraProxyClient>()
    val database = dependencies.resolve<Database>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1") {
                get("/meg") {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val navIdent = principal.navIdent
                    val roller = Role.fromGroups(principal.groups)

                    try {
                        val ansatt = entraProxyClient.hentAnsatt(navIdent)
                        val enheter = entraProxyClient.hentEnheter(navIdent)

                        val response = InnloggetAnsattResponse(
                            id = ansatt.navIdent,
                            navn = ansatt.visningNavn ?: "${ansatt.fornavn ?: ""} ${ansatt.etternavn ?: ""}".trim(),
                            epost = ansatt.epost ?: "",
                            enheter = enheter.map { enhet ->
                                AnsattEnhetResponse(
                                    id = enhet.enhetnummer,
                                    nummer = enhet.enhetnummer,
                                    navn = enhet.navn,
                                )
                            },
                            gjeldendeEnhet = AnsattEnhetResponse(
                                id = ansatt.enhet.enhetnummer,
                                nummer = ansatt.enhet.enhetnummer,
                                navn = ansatt.enhet.navn,
                            ),
                            roller = roller,
                        )

                        call.respond(response)
                    } catch (e: Exception) {
                        logger.error("Feil ved henting av ansattdata for navIdent", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Kunne ikke hente ansattdata.")
                        )
                    }
                }

                post("/enhet") {
                    call.respond(HttpStatusCode.NoContent)
                }

                get("/oversikt") {
                    call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    call.respond(OversiktResponse(saker = stubbedOversikt))
                }

                get("/soknad/{soknadId}/arena-behandling") {
                    call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val soknadId = call.parameters.getRequired(
                        name = "soknadId",
                        transform = UUID::fromString,
                    ) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig soknadId"))
                        return@get
                    }

                    val status = transaction(database) {
                        erArenaSakUnderBehandling(soknadId)
                    } ?: ArenaBehandlingStatus(underBehandlingIArena = false)

                    call.respond(status)
                }
            }

        }
    }
}

@Serializable
data class InnloggetAnsattResponse(
    val id: String,
    val navn: String,
    val epost: String,
    val enheter: List<AnsattEnhetResponse>,
    val gjeldendeEnhet: AnsattEnhetResponse,
    val roller: Set<Role>,
)

@Serializable
data class AnsattEnhetResponse(
    val id: String,
    val nummer: String,
    val navn: String,
)

@Serializable
data class OversiktResponse(
    val saker: List<OversiktRad>,
)

@Serializable
data class OversiktRad(
    val id: String,
    val virksomhet: String,
    val deltaker: String,
    val status: String,
    val saksbehandler: String,
    val opprettetDato: String,
    val tilsagnNummer: String? = null,
    // TODO: fyll fra arena_sak_under_behandling når oversikten kobles mot ekte data
    val underBehandlingIArena: Boolean = false,
)

private val stubbedOversikt = listOf(
    OversiktRad(
        id = "sak-1001",
        virksomhet = "Eksempel Bedrift AS",
        deltaker = "Ola Nordmann",
        status = "Til behandling",
        saksbehandler = "Silje Saksbehandler",
        opprettetDato = "2026-04-18",
        tilsagnNummer = "2026-101-1",
    ),
    OversiktRad(
        id = "sak-1002",
        virksomhet = "Demo Solutions AS",
        deltaker = "Eva Hansen",
        status = "Avventer svar",
        saksbehandler = "Silje Saksbehandler",
        opprettetDato = "2026-04-15",
        underBehandlingIArena = true,
    ),
    OversiktRad(
        id = "sak-1003",
        virksomhet = "Testfirma Norge AS",
        deltaker = "Per Pedersen",
        status = "Ferdigstilt",
        saksbehandler = "Vurderer Vilkårsen",
        opprettetDato = "2026-04-09",
        tilsagnNummer = "2026-087-2",
    ),
)

