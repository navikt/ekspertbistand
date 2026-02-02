package no.nav.ekspertbistand.ereg

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.skjema.subjectToken

@Serializable
data class AdresseResponse(val adresse: String)

private val orgnrRegex = Regex("^\\d{9}$")

suspend fun Application.configureEregApiV1() {
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val eregService = dependencies.resolve<EregService>()

    routing {
        authenticate(TOKENX_PROVIDER) {
            get("/api/ereg/{orgnr}/adresse") {
                val orgnr = call.parameters["orgnr"]
                if (orgnr == null || !orgnrRegex.matches(orgnr)) {
                    call.respond(HttpStatusCode.BadRequest, "ugyldig orgnr")
                    return@get
                }

                val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
                if (!tilganger.harTilgang(orgnr)) {
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = "bruker har ikke tilgang til organisasjon"
                    )
                    return@get
                }

                val adresse = eregService.hentForretningsadresse(orgnr)

                if (adresse == null) {
                    call.respond(HttpStatusCode.NotFound, "adresse ikke funnet")
                    return@get
                }

                call.respond(AdresseResponse(adresse))
            }
        }
    }
}
