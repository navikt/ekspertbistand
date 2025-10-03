package no.nav.ekspertbistand.skjema

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.DbConfig
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import java.util.*

fun Application.skjemaApiV1(
    dbConfig: DbConfig,
    altinnTilgangerClient: AltinnTilgangerClient,
) {
    val skjemaApi = SkjemaApi(dbConfig, altinnTilgangerClient)

    routing {
        authenticate(TOKENX_PROVIDER) {

            /**
             * POST /api/skjema/v1 => oppretter utkast
             * GET /api/skjema/v1/{id} => henter skjema på id (man må selv sjekke status på returnert data)
             * PATCH /api/skjema/v1/{id} => oppdaterer utkast (http 409 hvis utkast er sendt inn)
             *
             * GET /api/skjema/v1?status={status} => henter alle skjema med gitt status
             * DELETE /api/skjema/v1/{id} => sletter utkast (http 409 hvis utkast er sendt inn)
             * PUT /api/skjema/v1/{id} => sender inn skjema (payload valideres iht json schema)
             */
            route("/api/skjema/v1") {
                with(skjemaApi) {
                    post {
                        opprettUtkast()
                    }

                    get {
                        val statusParam = call.request.queryParameters.getRequired(
                            name = "status",
                            default = SkjemaStatus.innsendt.name,
                            transform = SkjemaStatus::valueOf,
                        ) {
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = "ugyldig parameter status='$it', gyldige verdier er: ${SkjemaStatus.entries.toTypedArray()}"
                            )
                            return@get
                        }

                        hentAlleSkjema(statusParam)
                    }

                    get("/{id}") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@get
                        }

                        hentSkjemaById(idParam)
                    }

                    patch("/{id}") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@patch
                        }

                        oppdaterUtkast(idParam)
                    }

                    delete("/{id}") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@delete
                        }

                        slettUtkast(idParam)
                    }

                    put("/{id}") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@put
                        }

                        sendInnSkjema(idParam)
                    }
                }
            }
        }
    }
}


internal val RoutingContext.subjectToken
    get() = call.principal<TokenXPrincipal>()!!.subjectToken

internal val RoutingContext.innloggetBruker
    get() = call.principal<TokenXPrincipal>()!!.pid

inline fun <T> Parameters.getRequired(
    name: String,
    default: String? = null,
    transform: (String) -> T,
    onError: (name: String) -> Nothing
): T {
    val paramValue = this[name] ?: default
    if (paramValue == null) {
        onError(name)
    }
    return try {
        transform(paramValue)
    } catch (_: IllegalArgumentException) {
        onError(name)
    }
}
