package no.nav.ekspertbistand.mocks

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import no.nav.ekspertbistand.dokdist.DistribuerJournalpostRequest
import no.nav.ekspertbistand.dokdist.DistribuerJournalpostResponse
import no.nav.ekspertbistand.dokdist.DokDistClient

fun ApplicationTestBuilder.mockDokDist(
    status: HttpStatusCode = HttpStatusCode.OK,
    responseProvider: (DistribuerJournalpostRequest) -> DistribuerJournalpostResponse,
) {
    externalServices {
        hosts(DokDistClient.ingress) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post(DokDistClient.API_PATH) {
                    val payload = call.receive<DistribuerJournalpostRequest>()
                    call.respond(status, responseProvider(payload))
                }
            }
        }
    }
}
