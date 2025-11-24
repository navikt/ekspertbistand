package no.nav.ekspertbistand.mocks

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.arena.ArenaClient


fun ApplicationTestBuilder.mockTiltaksgjennomfoering(
    responseProvider: (String) -> String
) {
    externalServices {
        hosts(ArenaClient.ingress) {
            routing {
                post(ArenaClient.API_PATH) {
                    val payload = call.receive<String>()
                    val response = responseProvider(payload)
                    call.respondText(response, contentType = io.ktor.http.ContentType.Application.Json)
                }
            }
        }
    }
}