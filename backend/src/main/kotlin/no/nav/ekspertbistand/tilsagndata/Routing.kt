package no.nav.ekspertbistand.tilsagndata

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.skjema.getRequired
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*

suspend fun Application.configureTilsagnDataApiV1() {
    val database = dependencies.resolve<Database>()
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val dokgenClient = dependencies.resolve<no.nav.ekspertbistand.dokgen.DokgenClient>()
    val tilsagnDataApi = TilsagnDataApi(database, altinnTilgangerClient, dokgenClient)

    routing {
        authenticate(TOKENX_PROVIDER) {
            route("/api/tilsagndata/v1") {
                with(tilsagnDataApi) {
                    get("/{id}/tilskuddsbrev-html") {
                        val skjemaId: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@get
                        }

                        hentTilskuddsbrevHtml(skjemaId)
                    }
                }
            }
        }
    }
}
