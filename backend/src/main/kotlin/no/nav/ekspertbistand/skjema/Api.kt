package no.nav.ekspertbistand.skjema

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SkjemaApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient,
) {
    val log = logger()

    // TODO: metrikk på antall utkast by alder

    suspend fun RoutingContext.opprettUtkast() {
        val opprettetUtkast = transaction(database) {
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
                val results = transaction(database) {
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
                val results = transaction(database) {
                    SkjemaTable.selectAll()
                        .where { SkjemaTable.virksomhetsnummer inList organisasjoner }
                        .orderBy(SkjemaTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilSkjemaDTO() }
                }
                call.respond(results)
            }

            else -> {
                throw IllegalArgumentException("Mottok ugyldig status param for henting av skjema: $statusParam")
            }
        }
    }

    suspend fun RoutingContext.hentSkjemaById(idParam: UUID) {
        val eksisterende = transaction(database) {
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
        val eksisterende = transaction(database) {
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

        val oppdatert = transaction(database) {
            UtkastTable.updateReturning(
                where = { UtkastTable.id eq idParam }
            ) { utkast ->
                oppdatertUtkast.virksomhet?.also { v ->
                    utkast[virksomhetsnummer] = v.virksomhetsnummer
                    utkast[virksomhetsnavn] = v.virksomhetsnavn
                    utkast[kontaktpersonNavn] = v.kontaktperson.navn
                    utkast[kontaktpersonEpost] = v.kontaktperson.epost
                    utkast[kontaktpersonTelefon] = v.kontaktperson.telefonnummer
                }
                oppdatertUtkast.ansatt?.also { a ->
                    utkast[ansattFnr] = a.fnr
                    utkast[ansattNavn] = a.navn
                }
                oppdatertUtkast.ekspert?.also { e ->
                    utkast[ekspertNavn] = e.navn
                    utkast[ekspertVirksomhet] = e.virksomhet
                    utkast[ekspertKompetanse] = e.kompetanse
                }
                oppdatertUtkast.behovForBistand?.also { t ->
                    utkast[behovForBistand] = t.behov
                    utkast[behovForBistandBegrunnelse] = t.begrunnelse
                    utkast[behovForBistandEstimertKostnad] = t.estimertKostnad
                    utkast[behovForBistandTimer] = t.timer
                    utkast[behovForBistandTilrettelegging] = t.tilrettelegging
                    utkast[behovForBistandStartdato] = t.startdato
                }
                oppdatertUtkast.nav?.also { n ->
                    utkast[navKontaktPerson] = n.kontaktperson
                }
            }.single().tilUtkastDTO()
        }

        call.respond(oppdatert)
    }

    suspend fun RoutingContext.slettUtkast(idParam: UUID) {
        val eksisterende = transaction(database) {
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

        transaction(database) {
            UtkastTable.deleteWhere { UtkastTable.id eq idParam }
        }

        call.respond(status = HttpStatusCode.NoContent, NullBody)
    }

    suspend fun RoutingContext.sendInnSkjema(idParam: UUID) {
        val eksisterende = transaction(database) {
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

        val innsendt = transaction(database) {
            val skjema = SkjemaTable.insertReturning {
                it[id] = idParam
                it[virksomhetsnummer] = skjema.virksomhet.virksomhetsnummer
                it[virksomhetsnavn] = skjema.virksomhet.virksomhetsnavn
                it[kontaktpersonNavn] = skjema.virksomhet.kontaktperson.navn
                it[kontaktpersonEpost] = skjema.virksomhet.kontaktperson.epost
                it[kontaktpersonTelefon] = skjema.virksomhet.kontaktperson.telefonnummer
                it[ansattFnr] = skjema.ansatt.fnr
                it[ansattNavn] = skjema.ansatt.navn
                it[ekspertNavn] = skjema.ekspert.navn
                it[ekspertVirksomhet] = skjema.ekspert.virksomhet
                it[ekspertKompetanse] = skjema.ekspert.kompetanse
                it[behovForBistand] = skjema.behovForBistand.behov
                it[behovForBistandBegrunnelse] = skjema.behovForBistand.begrunnelse
                it[behovForBistandEstimertKostnad] = skjema.behovForBistand.estimertKostnad
                it[behovForBistandTimer] = skjema.behovForBistand.timer
                it[behovForBistandTilrettelegging] = skjema.behovForBistand.tilrettelegging
                it[behovForBistandStartdato] = skjema.behovForBistand.startdato!!

                it[navKontaktPerson] = skjema.nav.kontaktperson
                it[opprettetAv] = innloggetBruker
                it[status] = SkjemaStatus.innsendt.toString()
            }.single().tilSkjemaDTO().also {
                UtkastTable.deleteWhere { UtkastTable.id eq idParam }
            }

            QueuedEvents.insert {
                it[eventData] = EventData.SkjemaInnsendt(skjema)
            }

            skjema
        }

        call.respond(innsendt)
    }

    fun slettGamleUtkast(
        ttl: Duration = 30.days,
        clock: Clock = Clock.System,
    ) = transaction {
        UtkastTable.deleteWhere {
            UtkastTable.opprettetTidspunkt lessEq (clock.now() - ttl)
        }.let {
            log.info("Slettet $it gamle utkast eldre enn $ttl")
        }
    }

}

sealed interface DTO {
    @Serializable
    data class Skjema(
        val id: String? = null,
        val virksomhet: Virksomhet,
        val ansatt: Ansatt,
        val ekspert: Ekspert,
        val behovForBistand: BehovForBistand,
        val nav: Nav,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
        val status: SkjemaStatus = SkjemaStatus.innsendt
    ) : DTO

    @Serializable
    data class Utkast(
        val id: String? = null,
        val virksomhet: Virksomhet? = null,
        val ansatt: Ansatt? = null,
        val ekspert: Ekspert? = null,
        val behovForBistand: BehovForBistand? = null,
        val nav: Nav? = null,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
    ) : DTO {
        val status = SkjemaStatus.utkast
    }

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
        val kontaktperson: Kontaktperson
    )

    @Serializable
    data class Kontaktperson(
        val navn: String,
        val epost: String,
        val telefonnummer: String
    )

    @Serializable
    data class Ansatt(
        val fnr: String,
        val navn: String
    )

    @Serializable
    data class Ekspert(
        val navn: String,
        val virksomhet: String,
        val kompetanse: String
    )

    @Serializable
    data class BehovForBistand(
        val begrunnelse: String,
        val behov: String,
        val estimertKostnad: String,
        val timer: String,
        val tilrettelegging: String,
        val startdato: LocalDate,
    )

    @Serializable
    data class Nav(
        val kontaktperson: String
    )
}

@Suppress("EnumEntryName")
enum class SkjemaStatus {
    utkast,
    innsendt,
    godkjent,
    avlyst,
    avslått
}
