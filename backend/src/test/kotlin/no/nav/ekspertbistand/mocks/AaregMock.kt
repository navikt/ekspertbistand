package no.nav.ekspertbistand.mocks

import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.aareg.AaregClient


fun ApplicationTestBuilder.mockAareg(
    responseProvider: () -> String
) {
    externalServices {
        hosts(AaregClient.ingress) {
            routing {
                get(AaregClient.API_PATH) {
                    val response = responseProvider()
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}

