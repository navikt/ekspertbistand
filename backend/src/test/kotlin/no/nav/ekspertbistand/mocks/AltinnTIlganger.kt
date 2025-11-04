package no.nav.ekspertbistand.mocks

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse

fun ApplicationTestBuilder.mockAltinnTilganger(
    tilgangerResponse: AltinnTilgangerClientResponse
) {
    externalServices {
        hosts(AltinnTilgangerClient.ingress) {
            install(ContentNegotiation) {
                json()
            }

            routing {
                post("altinn-tilganger") {
                    call.respond(tilgangerResponse)
                }
            }
        }
    }
}