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
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.tilgangsmaskin.Tilgangsresultat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("SaksbehandlerApi")

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

                    val navIdent = principal.navIdent
                    val roller = Role.fromGroups(principal.groups)

                    try {
                        val ansatt = entraProxyClient.hentAnsatt(navIdent)
                        val enheter = principal.enheter()

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

                get("/saker/{sakId}/logg") {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val sakId = call.parameters.getRequired(
                        name = "sakId",
                        transform = UUID::fromString,
                    ) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig sakId"))
                        return@get
                    }

                    val fnr = database.hentAnsattFnrForSak(sakId)
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    val tilgang = try {
                        tilgangsmaskinClient.evaluer(userToken = principal.subjectToken, brukerIdent = fnr)
                    } catch (e: Exception) {
                        logger.error("Klarte ikke sjekke tilgang til sak i tilgangsmaskin", e)
                        return@get call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("message" to "Kunne ikke sjekke tilgang til saken."),
                        )
                    }
                    if (tilgang is Tilgangsresultat.Avvist) {
                        return@get call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("message" to "Du har ikke tilgang til saken."),
                        )
                    }

                    val rader = database.hentSakslogg(sakId)
                    val navn = entraProxyClient.slaaOppNavn(rader.mapNotNull { it.ident }.toSet())
                    call.respond(rader.tilSaksloggResponse(navn))
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

