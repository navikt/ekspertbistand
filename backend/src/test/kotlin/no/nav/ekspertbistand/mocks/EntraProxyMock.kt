package no.nav.ekspertbistand.mocks

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.entraproxy.EntraProxyClient

fun ApplicationTestBuilder.mockEntraProxy(
    responseProvider: (navIdent: String) -> String
) {
    externalServices {
        hosts(EntraProxyClient.ingress) {
            routing {
                get("${EntraProxyClient.API_PATH}/{navIdent}") {
                    val navIdent = call.parameters["navIdent"]!!
                    val response = responseProvider(navIdent)
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

