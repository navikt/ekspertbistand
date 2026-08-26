package no.nav.ekspertbistand.vedlegg

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.soknad.getRequired
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID

suspend fun Application.configureVedleggApiV1() {
    val database = dependencies.resolve<Database>()
    val clamAvClient = dependencies.resolve<ClamAvClient>()
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val vedleggApi = VedleggApi(database, VedleggDb(database), clamAvClient, altinnTilgangerClient)

    routing {
        authenticate(TOKENX_PROVIDER) {
            route("/api/soknad/v1/{id}") {
                with(vedleggApi) {
                    post("/sluttrapport") {
                        val soknadId: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(HttpStatusCode.BadRequest, "ugyldig id")
                            return@post
                        }
                        lastOppSluttrapport(soknadId)
                    }
                    get("/sluttrapport") {
                        val soknadId: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(HttpStatusCode.BadRequest, "ugyldig id")
                            return@get
                        }
                        hentSluttrapportStatus(soknadId)
                    }
                }
            }
        }
    }
}
