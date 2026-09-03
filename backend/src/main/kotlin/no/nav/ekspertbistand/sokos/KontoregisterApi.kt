package no.nav.ekspertbistand.sokos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.saksbehandling.Role

private val orgnrRegex = Regex("^\\d{9}$")

suspend fun Application.configureKontoregisterApiV1() {
    val kontoregisterClient = dependencies.resolve<KontoregisterClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            get("/api/saksbehandling/v1/virksomhet/{orgnr}/kontonummer") {
                val orgnr = call.parameters["orgnr"]
                if (orgnr == null || !orgnrRegex.matches(orgnr)) {
                    call.respond(HttpStatusCode.BadRequest, "ugyldig orgnr")
                    return@get
                }

                val principal = call.principal<AzureAdPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val roller = Role.fromGroups(principal.groups)
                if (Role.SAKSBEHANDLER !in roller && Role.BESLUTTER !in roller) {
                    call.respond(HttpStatusCode.Forbidden, "krever rolle saksbehandler eller beslutter")
                    return@get
                }

                val kontooppslag = kontoregisterClient.hentKontonummer(orgnr)
                if (kontooppslag == null) {
                    call.respond(HttpStatusCode.NotFound, "kontonummer ikke funnet")
                    return@get
                }

                call.respond(KontonummerResponse(kontonummer = kontooppslag.kontonr))
            }
        }

        authenticate(TOKENX_PROVIDER) {
            get("/api/soknad/v1/virksomhet/kontonummer-finnes/{orgnr}") {
                val orgnr = call.parameters["orgnr"]
                if (orgnr == null || !orgnrRegex.matches(orgnr)) {
                    call.respond(HttpStatusCode.BadRequest, "ugyldig orgnr")
                    return@get
                }

                val kontooppslag = kontoregisterClient.hentKontonummer(orgnr)
                call.respond(KontonummerFinnesResponse(finnes = kontooppslag != null))
            }
        }
    }
}

@Serializable
data class KontonummerResponse(
    val kontonummer: String,
)

@Serializable
data class KontonummerFinnesResponse(
    val finnes: Boolean,
)
