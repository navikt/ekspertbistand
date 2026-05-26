package no.nav.ekspertbistand.saksbehandler

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal

suspend fun Application.configureSaksbehandlerApiV1() {
    val entraProxyClient = dependencies.resolve<EntraProxyClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandler/v1") {
                get("/me") {
                    val principal = call.principal<AzureAdPrincipal>()!!
                    val navIdent = principal.navIdent
                    val roles = Role.fromGroups(principal.groups)

                    val ansatt = entraProxyClient.hentAnsatt(navIdent)
                    val enheter = entraProxyClient.hentEnheter(navIdent)

                    call.respond(
                        SaksbehandlerInfo(
                            navIdent = ansatt.navIdent,
                            visningNavn = ansatt.visningNavn,
                            fornavn = ansatt.fornavn,
                            etternavn = ansatt.etternavn,
                            epost = ansatt.epost,
                            enhet = ansatt.enhet,
                            tident = ansatt.tident,
                            enheter = enheter,
                            roller = roles,
                        )
                    )
                }
            }
        }
    }
}

