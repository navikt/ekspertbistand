package no.nav.ekspertbistand.skjema

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.DbConfig
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun Application.skjemaApiV1(
    dbConfig: DbConfig,
    altinnTilgangerClient: AltinnTilgangerClient,
) {
    routing {
        authenticate(TOKENX_PROVIDER) {

            route("/api/skjema/v1") {
                /**
                 * POST /api/skjema/v1 => oppretter utkast
                 * GET /api/skjema/v1/{id} => henter skjema på id (man må selv sjekke status på returnert data)
                 * PATCH /api/skjema/v1/{id} => oppdaterer utkast (http 409 hvis utkast er sendt inn)
                 *
                 * GET /api/skjema/v1?status={status?} => henter alle skjema med status, alle statuser hvis ikke satt
                 * DELETE /api/skjema/v1/{id} => sletter utkast (http 409 hvis utkast er sendt inn)
                 * PUT /api/skjema/v1/{id} => sender inn skjema (payload valideres iht json schema)
                 */
                post {
                    val principal = call.principal<TokenXPrincipal>()!!
                    val opprettetUtkast = suspendTransaction(dbConfig.database) {
                        UtkastTable.insertReturning {
                            it[opprettetAv] = principal.pid
                        }.single().tilUtkastDTO()
                    }
                    call.respond(
                        HttpStatusCode.Created,
                        opprettetUtkast
                    )
                }

                get {
                    val statusParam = call.request.queryParameters["status"]?.let {
                        try {
                            SkjemaStatus.valueOf(it.uppercase())
                        } catch (_: IllegalArgumentException) {
                            call.respond(
                                status = HttpStatusCode.BadRequest,
                                message = "ugyldig parameter status='$it', gyldige verdier er: ${SkjemaStatus.entries.toTypedArray()}"
                            )
                            return@get
                        }
                    } ?: SkjemaStatus.innsendt // TODO: null == alle statuser

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)
                    val orgnummerForAltinn3 =
                        tilganger.tilgangTilOrgNr[AltinnTilgangerClient.altinn3Ressursid] ?: emptySet()
                    val orgnummerForAltinn2 =
                        tilganger.tilgangTilOrgNr[AltinnTilgangerClient.altinn2Tjenestekode] ?: emptySet()
                    val organisasjoner = orgnummerForAltinn3 + orgnummerForAltinn2

                    // TODO: isError?
                    if (organisasjoner.isEmpty()) {
                        call.respond(emptyList<DTO.Skjema>())
                        return@get
                    }


                    when (statusParam) {
                        SkjemaStatus.utkast -> {
                            val results = suspendTransaction(dbConfig.database) {
                                UtkastTable.selectAll()
                                    .where { UtkastTable.opprettetAv eq principal.pid }
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

                get("/{id}") {
                    val idParam = try {
                        UUID.fromString(call.pathParameters["id"]!!)
                    } catch (_: IllegalArgumentException) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@get
                    }

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)
                    val eksisterende = suspendTransaction(dbConfig.database) {
                        findSkjemaOrUtkastById(idParam)
                    }

                    if (eksisterende == null) {
                        call.respond(status = HttpStatusCode.NotFound, message = "skjema ikke funnet")
                        return@get
                    }

                    val harTilgang = when (eksisterende) {
                        is DTO.Skjema -> tilganger.harTilgang(eksisterende.organisasjonsnummer)

                        is DTO.Utkast -> if (eksisterende.organisasjonsnummer.isNullOrEmpty()) {
                            // utkast uten orgnummer kan kun leses av den som opprettet det
                            eksisterende.opprettetAv == principal.pid
                        } else {
                            tilganger.harTilgang(eksisterende.organisasjonsnummer)
                        }
                    }

                    if (!harTilgang) {
                        call.respond(
                            status = HttpStatusCode.Forbidden,
                            message = "bruker har ikke tilgang til organisasjon"
                        )
                        return@get
                    }

                    call.respond(eksisterende)
                }

                patch("/{id}") {
                    val idParam = try {
                        UUID.fromString(call.pathParameters["id"]!!)
                    } catch (_: IllegalArgumentException) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@patch
                    }

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)
                    val eksisterende = suspendTransaction(dbConfig.database) {
                        findSkjemaOrUtkastById(idParam)
                    }

                    if (eksisterende == null || eksisterende !is DTO.Utkast) {
                        call.respond(
                            status = HttpStatusCode.Conflict,
                            message = "skjema ikke i utkast-status"
                        )
                        return@patch
                    }

                    val oppdatertUtkast = call.receive<DTO.Utkast>()

                    (oppdatertUtkast.organisasjonsnummer ?: eksisterende.organisasjonsnummer)?.let { orgnr ->
                        if (!tilganger.harTilgang(orgnr)) {
                            call.respond(
                                status = HttpStatusCode.Forbidden,
                                message = "bruker har ikke tilgang til organisasjon"
                            )
                            return@patch
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
                    return@patch
                }

                delete("/{id}") {
                    val idParam = try {
                        UUID.fromString(call.pathParameters["id"]!!)
                    } catch (_: IllegalArgumentException) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@delete
                    }

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)
                    val eksisterende = suspendTransaction(dbConfig.database) {
                        findSkjemaOrUtkastById(idParam)
                    }

                    if (eksisterende == null || eksisterende !is DTO.Utkast) {
                        call.respond(
                            status = HttpStatusCode.Conflict,
                            message = "skjema ikke i utkast-status"
                        )
                        return@delete
                    }

                    if (eksisterende.organisasjonsnummer != null && !tilganger.harTilgang(eksisterende.organisasjonsnummer)) {
                        call.respond(
                            status = HttpStatusCode.Forbidden,
                            message = "bruker har ikke tilgang til organisasjon"
                        )
                        return@delete
                    }

                    suspendTransaction(dbConfig.database) {
                        SkjemaTable.deleteWhere { SkjemaTable.id eq idParam }
                    }

                    call.respond(status = HttpStatusCode.NoContent, message = "utkast slettet")
                }

                put("/{id}") {
                    val idParam = try {
                        UUID.fromString(call.pathParameters["id"]!!)
                    } catch (_: IllegalArgumentException) {
                        call.respond(status = HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@put
                    }

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)
                    val eksisterende = suspendTransaction(dbConfig.database) {
                        findSkjemaOrUtkastById(idParam)
                    }

                    if (eksisterende == null || eksisterende !is DTO.Utkast) {
                        call.respond(
                            status = HttpStatusCode.Conflict,
                            message = "skjema ikke i utkast-status"
                        )
                        return@put
                    }

                    val skjema = call.receive<DTO.Skjema>()

                    if (!tilganger.harTilgang(skjema.organisasjonsnummer)) {
                        call.respond(
                            status = HttpStatusCode.Forbidden,
                            message = "bruker har ikke tilgang til organisasjon"
                        )
                        return@put
                    }

                    val innsendt = suspendTransaction(dbConfig.database) {
                        SkjemaTable.insertReturning {
                            it[id] = idParam
                            it[tittel] = skjema.tittel
                            it[beskrivelse] = skjema.beskrivelse
                            it[organisasjonsnummer] = skjema.organisasjonsnummer
                            it[opprettetAv] = principal.pid
                        }.single().tilSkjemaDTO().also {
                            UtkastTable.deleteWhere { UtkastTable.id eq idParam }
                        }
                    }

                    call.respond(innsendt)
                }
            }
        }
    }
}

@Suppress("EnumEntryName")
enum class SkjemaStatus {
    utkast,
    innsendt,
}

object SkjemaTable : Table("skjema") {
    val id = uuid("id")
    val tittel = text("tittel")
    val organisasjonsnummer = text("organisasjonsnummer").index()
    val beskrivelse = text("beskrivelse")
    val opprettetAv = text("opprettet_av")

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }

    override val primaryKey = PrimaryKey(id)
}

object UtkastTable : UUIDTable("utkast") {
    val tittel = text("tittel").nullable()
    val organisasjonsnummer = text("organisasjonsnummer").nullable().index()
    val beskrivelse = text("beskrivelse").nullable()
    val opprettetAv = text("opprettet_av").index()

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }
}

suspend fun findSkjemaOrUtkastById(id: UUID): DTO? =
    SkjemaTable.selectAll()
        .where { SkjemaTable.id eq id }
        .singleOrNull()?.tilSkjemaDTO()
        ?: // If not found, try UtkastTable
        UtkastTable.selectAll()
            .where { UtkastTable.id eq id }
            .singleOrNull()?.tilUtkastDTO()

fun ResultRow.tilSkjemaDTO() = DTO.Skjema(
    id = this[SkjemaTable.id].toString(),
    tittel = this[SkjemaTable.tittel],
    organisasjonsnummer = this[SkjemaTable.organisasjonsnummer],
    beskrivelse = this[SkjemaTable.beskrivelse],
    opprettetAv = this[SkjemaTable.opprettetAv],
    opprettetTidspunkt = this[SkjemaTable.opprettetTidspunkt]
)

fun ResultRow.tilUtkastDTO() = DTO.Utkast(
    id = this[UtkastTable.id].value.toString(),
    tittel = this[UtkastTable.tittel],
    organisasjonsnummer = this[UtkastTable.organisasjonsnummer],
    beskrivelse = this[UtkastTable.beskrivelse],
    opprettetAv = this[UtkastTable.opprettetAv],
    opprettetTidspunkt = this[UtkastTable.opprettetTidspunkt]
)

sealed interface DTO {
    @Serializable
    data class Skjema(
        val id: String,
        val organisasjonsnummer: String,
        val tittel: String,
        val beskrivelse: String,
        val opprettetAv: String?,
        val opprettetTidspunkt: String?
    ) : DTO {
        val status = SkjemaStatus.innsendt
    }

    @Serializable
    data class Utkast(
        val id: String? = null,
        val organisasjonsnummer: String?,
        val tittel: String? = null,
        val beskrivelse: String? = null,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
    ) : DTO {
        val status = SkjemaStatus.utkast
    }
}
