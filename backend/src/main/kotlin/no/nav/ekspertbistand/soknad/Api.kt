package no.nav.ekspertbistand.soknad

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
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
class SoknadApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient,
    private val eregService: EregService
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

    suspend fun RoutingContext.hentAlleSoknader(statusParam: SoknadStatusQueryParam) {
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val organisasjoner = tilganger.organisasjoner

        if (organisasjoner.isEmpty()) {
            call.respond(emptyList<DTO.Soknad>())
            return
        }

        when (statusParam) {
            SoknadStatusQueryParam.utkast -> {
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

            SoknadStatusQueryParam.innsendt -> {
                val results = transaction(database) {
                    SoknadTable.selectAll()
                        .where { SoknadTable.virksomhetsnummer inList organisasjoner }
                        .orderBy(SoknadTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilSoknadDTO() }
                }
                call.respond(results)
            }
        }
    }

    suspend fun RoutingContext.hentSoknadById(idParam: UUID) {
        val eksisterende = transaction(database) {
            findSoknadOrUtkastById(idParam)
        }

        if (eksisterende == null) {
            call.respond(status = HttpStatusCode.NotFound, message = "soknad ikke funnet")
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val harTilgang = when (eksisterende) {
            is DTO.Soknad -> tilganger.harTilgang(eksisterende.virksomhet.virksomhetsnummer)

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
            findSoknadOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "soknad ikke i utkast-status"
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
            findSoknadOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "soknad ikke i utkast-status"
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

    suspend fun RoutingContext.sendInnSoknad(idParam: UUID) {
        val eksisterende = transaction(database) {
            findSoknadOrUtkastById(idParam)
        }

        if (eksisterende == null || eksisterende !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "soknad ikke i utkast-status"
            )
            return
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val soknad = call.receive<DTO.Soknad>()

        if (!tilganger.harTilgang(soknad.virksomhet.virksomhetsnummer)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        val adresse = eregService.hentForretningsadresse(soknad.virksomhet.virksomhetsnummer)

        val innsendt = transaction(database) {
            val opprettet = SoknadTable.insertReturning {
                it[id] = idParam
                it[virksomhetsnummer] = soknad.virksomhet.virksomhetsnummer
                it[virksomhetsnavn] = soknad.virksomhet.virksomhetsnavn
                it[kontaktpersonNavn] = soknad.virksomhet.kontaktperson.navn
                it[kontaktpersonEpost] = soknad.virksomhet.kontaktperson.epost
                it[kontaktpersonTelefon] = soknad.virksomhet.kontaktperson.telefonnummer
                it[beliggenhetsadresse] = adresse
                it[ansattFnr] = soknad.ansatt.fnr
                it[ansattNavn] = soknad.ansatt.navn
                it[ekspertNavn] = soknad.ekspert.navn
                it[ekspertVirksomhet] = soknad.ekspert.virksomhet
                it[ekspertKompetanse] = soknad.ekspert.kompetanse
                it[behovForBistand] = soknad.behovForBistand.behov
                it[behovForBistandBegrunnelse] = soknad.behovForBistand.begrunnelse
                it[behovForBistandEstimertKostnad] = soknad.behovForBistand.estimertKostnad
                it[behovForBistandTimer] = soknad.behovForBistand.timer
                it[behovForBistandTilrettelegging] = soknad.behovForBistand.tilrettelegging
                it[behovForBistandStartdato] = soknad.behovForBistand.startdato

                it[navKontaktPerson] = soknad.nav.kontaktperson
                it[opprettetAv] = innloggetBruker
                it[status] = SoknadStatus.innsendt.toString()
            }.single().tilSoknadDTO().also {
                UtkastTable.deleteWhere { UtkastTable.id eq idParam }
            }

            QueuedEvents.insert {
                it[eventData] = EventData.SoknadInnsendt(opprettet)
            }

            opprettet
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

    fun slettGamleInnsendteSoknader(
        clock: Clock = Clock.System,
    ) = transaction {
        SoknadTable.deleteWhere {
            SoknadTable.sletteTidspunkt lessEq (clock.now())
        }.let {
            log.info("Slettet $it gamle innsendte søknader eldre enn $slettSøknadOm")
        }
    }

}

sealed interface DTO {
    /**
     * Default-verdier på soknad brukes da de blir satt etter persistering.
     */

    @Serializable
    data class Soknad(
        val id: String? = null,
        val virksomhet: Virksomhet,
        val ansatt: Ansatt,
        val ekspert: Ekspert,
        val behovForBistand: BehovForBistand,
        val nav: Nav,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
        val status: SoknadStatus = SoknadStatus.innsendt,
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
        val status = SoknadStatus.utkast
    }

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
        val kontaktperson: Kontaktperson,
        val beliggenhetsadresse: String? = null,
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
enum class SoknadStatus {
    utkast,
    innsendt,
    godkjent,
    avlyst
}

@Suppress("EnumEntryName")
enum class SoknadStatusQueryParam {
    utkast,
    innsendt
}

val DTO.Soknad.kvitteringsLenke: String
    get() = basedOnEnv(
        prod = "https://arbeidsgiver.nav.no",
        dev = "https://arbeidsgiver.intern.dev.nav.no",
        other = "https://arbeidsgiver.intern.dev.nav.no",
    ).let { domain -> "$domain/ekspertbistand/skjema/$id/kvittering" }
