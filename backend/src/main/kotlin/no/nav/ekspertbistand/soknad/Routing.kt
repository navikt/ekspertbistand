package no.nav.ekspertbistand.soknad

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.saksbehandling.gjeldendeMaksSats
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
    val deltakerOppslag = dependencies.resolve<DeltakerOppslag>()
    val deltakerOppslagRateLimit = DeltakerOppslagRateLimit(database)
    val soknadApi = SoknadApi(database, altinnTilgangerClient, eregService, deltakerOppslag, deltakerOppslagRateLimit)
    val aggregateRetention = AggregateRetention(database)

    launch {
        while (isActiveAndNotTerminating) {
            try {
                soknadApi.slettGamleUtkast()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                log.error("Feil ved sletting av gamle utkast", e)
            }
            try {
                deltakerOppslagRateLimit.slettGamleOppslag()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                log.error("Feil ved sletting av gamle deltakeroppslag", e)
            }
            delay(10.minutes)
        }
    }

    launch {
        while (isActiveAndNotTerminating) {
            try {
                aggregateRetention.slettGamleAggregater()
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                log.error("Feil ved sletting av aggregater eldre enn oppbevaringstiden", e)
            }
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
             * POST /api/soknad/v1/{id}/valider-deltaker => validerer deltaker, sjekker arbeidsforhold og henter navn
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

                    get("/maks-sats") {
                        val startdato = call.request.queryParameters.getRequired(
                            name = "startdato",
                            transform = { LocalDate.parse(it) },
                        ) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Ugyldig startdato."))
                            return@get
                        }
                        val sats = gjeldendeMaksSats(startdato)
                        if (sats == null) {
                            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Ingen sats gjelder for startdato."))
                            return@get
                        }
                        call.respond(sats)
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

                    post("/{id}/valider-deltaker") {
                        val idParam: UUID = call.pathParameters.getRequired(
                            name = "id",
                            transform = UUID::fromString,
                        ) {
                            call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                            return@post
                        }

                        validerDeltaker(idParam)
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
