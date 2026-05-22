package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SaksbehandlerApi")

suspend fun Application.configureSaksbehandlerApiV1() {
    val entraProxyClient = dependencies.resolve<EntraProxyClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/ansatte") {
                get("/meg") {
                    val principal = call.principal<AzureAdPrincipal>()
                        ?: return@get call.respond(HttpStatusCode.Unauthorized)

                    val navIdent = principal.navIdent

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
)

@Serializable
data class AnsattEnhetResponse(
    val id: String,
    val nummer: String,
    val navn: String,
)
