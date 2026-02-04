package no.nav.ekspertbistand.soknad

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
suspend fun Application.configureSoknadApiV1() {
    val database = dependencies.resolve<Database>()
    val altinnTilgangerClient = dependencies.resolve<AltinnTilgangerClient>()
    val eregService = dependencies.resolve<EregService>()
    val soknadApi = SoknadApi(database, altinnTilgangerClient, eregService)

    launch {
        while (isActiveAndNotTerminating) {
            soknadApi.slettGamleUtkast()
            delay(10.minutes)
        }
    }

    launch {
        while (isActiveAndNotTerminating) {
            soknadApi.slettGamleInnsendteSoknader()
            delay(1.days)
        }
    }

    routing {
        authenticate(TOKENX_PROVIDER) {

            /**
             * POST /api/soknad/v1 => oppretter utkast
             * GET /api/soknad/v1/{id} => henter soknad på id (man må selv sjekke status på returnert data)
             * PATCH /api/soknad/v1/{id} => oppdaterer utkast (http 409 hvis utkast er sendt inn)
             *
             * GET /api/soknad/v1?status={status} => henter alle soknad med gitt status
             * DELETE /api/soknad/v1/{id} => sletter utkast (http 409 hvis utkast er sendt inn)
             * PUT /api/soknad/v1/{id} => sender inn soknad (payload valideres iht json schema)
             */
            route("/api/soknad/v1") {
                with(soknadApi) {
                    post {
                        opprettUtkast()
                    }

                    get {
                        val statusParam = call.request.queryParameters.getRequired(
                            name = "status",
                            default = SoknadStatusQueryParam.innsendt.name,
                            transform = SoknadStatusQueryParam::valueOf,
                        ) {
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = "ugyldig parameter status='$it', gyldige verdier er: ${SoknadStatusQueryParam.entries.toTypedArray()}"
                            )
                            return@get
                        }

                        hentAlleSoknader(statusParam)
                    }

                    get("/{id}") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@get
                        }

                        hentSoknadById(idParam)
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

                        sendInnSoknad(idParam)
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
