package no.nav.ekspertbistand.skjema

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.infrastruktur.DbConfig
import no.nav.ekspertbistand.infrastruktur.TokenXPrincipal
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.r2dbc.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.*

class SkjemaApi(
    private val dbConfig: DbConfig,
    private val altinnTilgangerClient: AltinnTilgangerClient,
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
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val organisasjoner = tilganger.organisasjoner

        if (organisasjoner.isEmpty()) {
            call.respond(emptyList<DTO.Skjema>())
            return
        }

        when (statusParam) {
            SkjemaStatus.utkast -> {
                val results = suspendTransaction(dbConfig.database) {
                    UtkastTable.selectAll()
                        .where { UtkastTable.opprettetAv eq innloggetBruker }
                        .orWhere { UtkastTable.virksomhetsnummer inList organisasjoner }
                        .orderBy(UtkastTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilUtkastDTO() }
                }
                call.respond(results)
            }

            SkjemaStatus.innsendt -> {
                val results = suspendTransaction(dbConfig.database) {
                    SkjemaTable.selectAll()
                        .where { SkjemaTable.virksomhetsnummer inList organisasjoner }
                        .orderBy(SkjemaTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilSkjemaDTO() }
                }
                call.respond(results)
            }
        }
    }

    suspend fun RoutingContext.hentSkjemaById(idParam: UUID) {
        val eksisterende = suspendTransaction(dbConfig.database) {
            findSkjemaOrUtkastById(idParam)
        }

        if (eksisterende == null) {
            call.respond(status = HttpStatusCode.NotFound, message = "skjema ikke funnet")
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val harTilgang = when (eksisterende) {
            is DTO.Skjema -> tilganger.harTilgang(eksisterende.virksomhet.virksomhetsnummer)

            is DTO.Utkast -> if (eksisterende.virksomhet?.virksomhetsnummer == null) {
                // utkast uten orgnummer kan kun leses av den som opprettet det
                eksisterende.opprettetAv == innloggetBruker
            } else {
                tilganger.harTilgang(eksisterende.virksomhet.virksomhetsnummer)
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

        val orgnr = oppdatertUtkast.virksomhet?.virksomhetsnummer
            ?: eksisterende.virksomhet?.virksomhetsnummer

        if (orgnr != null && !tilganger.harTilgang(orgnr)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        val oppdatert = suspendTransaction(dbConfig.database) {
            UtkastTable.updateReturning(
                where = { UtkastTable.id eq idParam }
            ) { utkast ->
                oppdatertUtkast.virksomhet?.also { v ->
                    utkast[virksomhetsnummer] = v.virksomhetsnummer
                    utkast[kontaktpersonNavn] = v.kontaktperson.navn
                    utkast[kontaktpersonEpost] = v.kontaktperson.epost
                    utkast[kontaktpersonTelefon] = v.kontaktperson.telefon
                }
                oppdatertUtkast.ansatt?.also { a ->
                    utkast[ansattFodselsnummer] = a.fodselsnummer
                    utkast[ansattNavn] = a.navn
                }
                oppdatertUtkast.ekspert?.also { e ->
                    utkast[ekspertNavn] = e.navn
                    utkast[ekspertVirksomhet] = e.virksomhet
                    utkast[ekspertKompetanse] = e.kompetanse
                    utkast[ekspertProblemstilling] = e.problemstilling
                }
                oppdatertUtkast.tiltak?.also { t ->
                    utkast[tiltakForTilrettelegging] = t.forTilrettelegging
                }
                oppdatertUtkast.bestilling?.also { b ->
                    utkast[bestillingKostnad] = b.kostnad
                    utkast[bestillingStartDato] = b.startDato
                }
                oppdatertUtkast.nav?.also { n ->
                    utkast[navKontakt] = n.kontakt
                }
            }.single().tilUtkastDTO()
        }

        call.respond(oppdatert)
    }

    suspend fun RoutingContext.slettUtkast(idParam: UUID) {
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
        val virksomhetsnummer = eksisterende.virksomhet?.virksomhetsnummer

        if (virksomhetsnummer != null && !tilganger.harTilgang(virksomhetsnummer)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        suspendTransaction(dbConfig.database) {
            UtkastTable.deleteWhere { UtkastTable.id eq idParam }
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

        if (!tilganger.harTilgang(skjema.virksomhet.virksomhetsnummer)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        val innsendt = suspendTransaction(dbConfig.database) {
            SkjemaTable.insertReturning {
                it[id] = idParam
                it[virksomhetsnummer] = skjema.virksomhet.virksomhetsnummer
                it[kontaktpersonNavn] = skjema.virksomhet.kontaktperson.navn
                it[kontaktpersonEpost] = skjema.virksomhet.kontaktperson.epost
                it[kontaktpersonTelefon] = skjema.virksomhet.kontaktperson.telefon
                it[ansattFodselsnummer] = skjema.ansatt.fodselsnummer
                it[ansattNavn] = skjema.ansatt.navn
                it[ekspertNavn] = skjema.ekspert.navn
                it[ekspertVirksomhet] = skjema.ekspert.virksomhet
                it[ekspertKompetanse] = skjema.ekspert.kompetanse
                it[ekspertProblemstilling] = skjema.ekspert.problemstilling
                it[tiltakForTilrettelegging] = skjema.tiltak.forTilrettelegging
                it[bestillingKostnad] = skjema.bestilling.kostnad
                it[bestillingStartDato] = skjema.bestilling.startDato
                it[navKontakt] = skjema.nav.kontakt
                it[opprettetAv] = innloggetBruker
            }.single().tilSkjemaDTO().also {
                UtkastTable.deleteWhere { UtkastTable.id eq idParam }
            }
        }

        call.respond(innsendt)
    }

    private val RoutingContext.subjectToken
        get() = call.principal<TokenXPrincipal>()!!.subjectToken

    private val RoutingContext.innloggetBruker
        get() = call.principal<TokenXPrincipal>()!!.pid
}

sealed interface DTO {
    @Serializable
    data class Skjema(
        val id: String? = null,
        val virksomhet: Virksomhet,
        val ansatt: Ansatt,
        val ekspert: Ekspert,
        val tiltak: Tiltak,
        val bestilling: Bestilling,
        val nav: Nav,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
    ) : DTO {
        val status = SkjemaStatus.innsendt
    }

    @Serializable
    data class Utkast(
        val id: String? = null,
        val virksomhet: Virksomhet? = null,
        val ansatt: Ansatt? = null,
        val ekspert: Ekspert? = null,
        val tiltak: Tiltak? = null,
        val bestilling: Bestilling? = null,
        val nav: Nav? = null,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
    ) : DTO {
        val status = SkjemaStatus.utkast
    }

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val kontaktperson: Kontaktperson
    )

    @Serializable
    data class Kontaktperson(
        val navn: String,
        val epost: String,
        val telefon: String
    )

    @Serializable
    data class Ansatt(
        val fodselsnummer: String,
        val navn: String
    )

    @Serializable
    data class Ekspert(
        val navn: String,
        val virksomhet: String,
        val kompetanse: String,
        val problemstilling: String
    )

    @Serializable
    data class Tiltak(
        val forTilrettelegging: String
    )

    @Serializable
    data class Bestilling(
        @Contextual
        val kostnad: String,
        val startDato: String
    )

    @Serializable
    data class Nav(
        val kontakt: String
    )
}

@Suppress("EnumEntryName")
enum class SkjemaStatus {
    utkast,
    innsendt,
}
