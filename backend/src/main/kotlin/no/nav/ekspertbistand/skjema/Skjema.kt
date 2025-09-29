package no.nav.ekspertbistand.skjema

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.DbConfig
import no.nav.ekspertbistand.infrastruktur.TOKENX_PROVIDER
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun Application.skjemaApiV1(
    dbConfig: DbConfig,
    altinnTilgangerClient: AltinnTilgangerClient,
) {
    routing {
        authenticate(TOKENX_PROVIDER) {
            route("/api/skjema/v1") {
                get {
                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)

                    val orgnummerForAltinn3 =
                        tilganger.tilgangTilOrgNr[AltinnTilgangerClient.altinn3Ressursid] ?: emptySet()
                    val orgnummerForAltinn2 =
                        tilganger.tilgangTilOrgNr[AltinnTilgangerClient.altinn2Tjenestekode] ?: emptySet()
                    val organisasjoner = orgnummerForAltinn3 + orgnummerForAltinn2
                    // TODO: kortslutte dersom tom liste her?

                    val results = suspendTransaction(dbConfig.database) {
                        SkjemaTable.selectAll()
                            .where { SkjemaTable.organisasjonsnummer inList organisasjoner }
                            .orderBy(SkjemaTable.opprettetTidspunkt to SortOrder.DESC)
                            .toList()
                            .map {
                                Skjema(
                                    id = it[SkjemaTable.id].value.toString(),
                                    organisasjonsnummer = it[SkjemaTable.organisasjonsnummer],
                                    tittel = it[SkjemaTable.tittel],
                                    beskrivelse = it[SkjemaTable.beskrivelse],
                                    opprettetAv = it[SkjemaTable.opprettetAv],
                                    opprettetTidspunkt = it[SkjemaTable.opprettetTidspunkt],
                                )
                            }

                    }
                    call.respond(results)
                }
                get("/{id}") {

                    val principal = call.principal<TokenXPrincipal>()!!
                    val tilganger = altinnTilgangerClient.hentTilganger(principal.subjectToken)

                    val idParam = try {
                        UUID.fromString(call.pathParameters["id"]!!)
                    } catch (_: IllegalArgumentException) {
                        call.respond(status = io.ktor.http.HttpStatusCode.BadRequest, message = "ugyldig id")
                        return@get
                    }

                    val result = suspendTransaction(dbConfig.database) {
                        SkjemaTable.selectAll()
                            .where { SkjemaTable.id eq idParam }
                            .singleOrNull()?.let {
                                Skjema(
                                    id = it[SkjemaTable.id].value.toString(),
                                    organisasjonsnummer = it[SkjemaTable.organisasjonsnummer],
                                    tittel = it[SkjemaTable.tittel],
                                    beskrivelse = it[SkjemaTable.beskrivelse],
                                    opprettetAv = it[SkjemaTable.opprettetAv],
                                    opprettetTidspunkt = it[SkjemaTable.opprettetTidspunkt],
                                )
                            }

                    }
                    if (result == null || tilganger.orgNrTilTilganger[result.organisasjonsnummer].isNullOrEmpty()) { // TODO: funker, men vær mer eksplisitt i tilgangssjekken
                        call.respond(status = io.ktor.http.HttpStatusCode.NotFound, message = "skjema ikke funnet")
                    } else {
                        call.respond(result)
                    }
                }
                post {

                }
            }
        }
    }
}

object SkjemaTable : UUIDTable("skjema") {
    val tittel = text("tittel")
    val organisasjonsnummer = text("organisasjonsnummer").index()
    val beskrivelse = text("beskrivelse")
    val opprettetAv = text("opprettet_av")

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }
}

@Serializable
data class Skjema(
    val id: String,
    val organisasjonsnummer: String,
    val tittel: String,
    val beskrivelse: String,
    val opprettetAv: String,
    val opprettetTidspunkt: String,
)
