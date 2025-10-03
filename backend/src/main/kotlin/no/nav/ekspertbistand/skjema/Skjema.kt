package no.nav.ekspertbistand.skjema

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.DbConfig
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.*

fun Application.skjemaApiV1(
    dbConfig: DbConfig,
    altinnTilgangerClient: AltinnTilgangerClient,
) {
    suspend fun RoutingContext.opprettUtkast() {
        val opprettetUtkast = suspendTransaction(dbConfig.database) {
            UtkastTable.insertReturning {
                it[opprettetAv] = innloggetBruker
            }.single().tilUtkastDTO()
        }
        call.respond(
            HttpStatusCode.Created,
            opprettetUtkast
        )
    }

    suspend fun RoutingContext.hentAlleSkjema(statusParam: SkjemaStatus) {
        val organisasjoner = altinnTilgangerClient.hentTilganger(subjectToken).organisasjoner

        // TODO: isError?
        if (organisasjoner.isEmpty()) {
            call.respond(emptyList<DTO.Skjema>())
            return
        }


        when (statusParam) {
            SkjemaStatus.utkast -> {
                val results = suspendTransaction(dbConfig.database) {
                    UtkastTable.selectAll()
                        .where { UtkastTable.opprettetAv eq innloggetBruker }
                        .orWhere { UtkastTable.organisasjonsnummer inList organisasjoner }
                        .orderBy(UtkastTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilUtkastDTO() }
                }
                call.respond(results)
            }

            SkjemaStatus.innsendt -> {
                val results = suspendTransaction(dbConfig.database) {
                    SkjemaTable.selectAll()
                        .where { SkjemaTable.organisasjonsnummer inList organisasjoner }
                        .orderBy(SkjemaTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilSkjemaDTO() }
                }
                call.respond(results)
            }
        }
    }

    suspend fun RoutingContext.hentSkjemaById(
        idParam: UUID
    ) {
        val eksisterende = suspendTransaction(dbConfig.database) {
            findSkjemaOrUtkastById(idParam)
        }

        if (eksisterende == null) {
            call.respond(status = HttpStatusCode.NotFound, message = "skjema ikke funnet")
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val harTilgang = when (eksisterende) {
            is DTO.Skjema -> tilganger.harTilgang(eksisterende.organisasjonsnummer)

            is DTO.Utkast -> if (eksisterende.organisasjonsnummer.isNullOrEmpty()) {
                // utkast uten orgnummer kan kun leses av den som opprettet det
                eksisterende.opprettetAv == innloggetBruker
            } else {
                tilganger.harTilgang(eksisterende.organisasjonsnummer)
            }
        }

        if (!harTilgang) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        call.respond(eksisterende)
    }

    suspend fun RoutingContext.oppdaterUtkast(idParam: UUID) {
        val eksisterende = suspendTransaction(dbConfig.database) {
            findSkjemaOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "skjema ikke i utkast-status"
            )
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val oppdatertUtkast = call.receive<DTO.Utkast>()
        (oppdatertUtkast.organisasjonsnummer ?: eksisterende.organisasjonsnummer)?.let { orgnr ->
            if (!tilganger.harTilgang(orgnr)) {
                call.respond(
                    status = HttpStatusCode.Forbidden,
                    message = "bruker har ikke tilgang til organisasjon"
                )
                return
            }
        }

        val oppdatert = suspendTransaction(dbConfig.database) {
            UtkastTable.updateReturning(
                where = { UtkastTable.id eq idParam }
            ) { utkast ->
                oppdatertUtkast.tittel?.also { nyTittel ->
                    utkast[tittel] = nyTittel
                }
                oppdatertUtkast.beskrivelse?.also { nyBeskrivelse ->
                    utkast[beskrivelse] = nyBeskrivelse
                }
                oppdatertUtkast.organisasjonsnummer?.also { nyttOrgnummer ->
                    utkast[organisasjonsnummer] = nyttOrgnummer
                }
            }.single().tilUtkastDTO()
        }

        call.respond(oppdatert)
    }

    suspend fun RoutingContext.slettUtkast(
        idParam: UUID
    ) {
        val eksisterende = suspendTransaction(dbConfig.database) {
            findSkjemaOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "skjema ikke i utkast-status"
            )
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        if (eksisterende.organisasjonsnummer != null && !tilganger.harTilgang(eksisterende.organisasjonsnummer)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        suspendTransaction(dbConfig.database) {
            SkjemaTable.deleteWhere { SkjemaTable.id eq idParam }
        }

        call.respond(status = HttpStatusCode.NoContent, message = "utkast slettet")
    }

    suspend fun RoutingContext.sendInnSkjema(idParam: UUID) {
        val eksisterende = suspendTransaction(dbConfig.database) {
            findSkjemaOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "skjema ikke i utkast-status"
            )
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val skjema = call.receive<DTO.Skjema>()

        if (!tilganger.harTilgang(skjema.organisasjonsnummer)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        val innsendt = suspendTransaction(dbConfig.database) {
            SkjemaTable.insertReturning {
                it[id] = idParam
                it[tittel] = skjema.tittel
                it[beskrivelse] = skjema.beskrivelse
                it[organisasjonsnummer] = skjema.organisasjonsnummer
                it[opprettetAv] = innloggetBruker
            }.single().tilSkjemaDTO().also {
                UtkastTable.deleteWhere { UtkastTable.id eq idParam }
            }
        }

        call.respond(innsendt)
    }

    routing {
        authenticate(TOKENX_PROVIDER) {

            route("/api/skjema/v1") {
                /**
                 * POST /api/skjema/v1 => oppretter utkast
                 * GET /api/skjema/v1/{id} => henter skjema på id (man må selv sjekke status på returnert data)
                 * PATCH /api/skjema/v1/{id} => oppdaterer utkast (http 409 hvis utkast er sendt inn)
                 *
                 * GET /api/skjema/v1?status={status} => henter alle skjema med gitt status
                 * DELETE /api/skjema/v1/{id} => sletter utkast (http 409 hvis utkast er sendt inn)
                 * PUT /api/skjema/v1/{id} => sender inn skjema (payload valideres iht json schema)
                 */
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
                    val idParam : UUID = call.pathParameters.getRequired(
                        name = "id",
                        transform = UUID::fromString,
                    ) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@get
                    }

                    hentSkjemaById(idParam)
                }

                patch("/{id}") {
                    val idParam : UUID = call.pathParameters.getRequired(
                        name = "id",
                        transform = UUID::fromString,
                    ) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@patch
                    }

                    oppdaterUtkast(idParam)
                }

                delete("/{id}") {
                    val idParam : UUID = call.pathParameters.getRequired(
                        name = "id",
                        transform = UUID::fromString,
                    ) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@delete
                    }

                    slettUtkast(idParam)
                }

                put("/{id}") {
                    val idParam : UUID = call.pathParameters.getRequired(
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

private val RoutingContext.subjectToken
    get() = call.principal<TokenXPrincipal>()!!.subjectToken

private val RoutingContext.innloggetBruker
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

