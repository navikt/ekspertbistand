package no.nav.ekspertbistand.vedtaksbrev

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.soknad.getRequired
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.UUID

suspend fun Application.configureVedtaksbrevApiV1() {
    val database = dependencies.resolve<Database>()
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val dokumentService = dependencies.resolve<DokumentService>()
    val vedtaksbrevApi = VedtaksbrevApi(database, VedtaksbrevDb(database), altinnTilgangerClient, dokumentService)

    routing {
        authenticate(TOKENX_PROVIDER) {
            route("/api/soknad/v1/{id}") {
                with(vedtaksbrevApi) {
                    get("/vedtak") {
                        val soknadId: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(HttpStatusCode.BadRequest, "ugyldig id")
                            return@get
                        }
                        hentVedtak(soknadId)
                    }
                    get("/vedtak/brev-pdf") {
                        val soknadId: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(HttpStatusCode.BadRequest, "ugyldig id")
                            return@get
                        }
                        hentVedtaksbrevPdf(soknadId)
                    }
                }
            }
        }
    }
}
