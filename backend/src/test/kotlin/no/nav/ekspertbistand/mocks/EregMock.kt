package no.nav.ekspertbistand.mocks

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.ekspertbistand.ereg.EregClient

fun ApplicationTestBuilder.mockEreg(responseProvider: (String) -> String) {
    externalServices {

        hosts(EregClient.ingress) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("${EregClient.API_PATH}{orgnr}") {
                    val orgnr = call.parameters["orgnr"] ?: ""
                    call.respondText(
                        responseProvider(orgnr),
                        ContentType.Application.Json
                    )
                }
            }
        }
    }
}
