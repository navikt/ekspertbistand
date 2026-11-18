package no.nav.ekspertbistand.soknad

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.SoknadDomainV1
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import no.nav.ekspertbistand.infrastruktur.erGyldigFodselsnummer
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.valider
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SoknadApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient,
    private val eregService: EregService,
    private val deltakerOppslag: DeltakerOppslag,
    private val deltakerOppslagRateLimit: DeltakerOppslagRateLimit,
) {
    val log = logger()

    // TODO: metrikk på antall utkast by alder

    /**
     * Oppretter utkastet først når deltakeren er bekreftet, med de samme sjekkene som [validerDeltaker].
     * Ingen utkast lagres ved feil. Se specifications/innsending_fra_utkast.md (4).
     */
    suspend fun RoutingContext.opprettUtkast() {
        val mottatt = mottaDeltaker() ?: return
        when (val deltaker = validerArbeidsforhold(mottatt) ?: return) {
            is ArbeidsforholdSjekk.IngenArbeidsforhold -> call.respondIngenArbeidsforhold()
            is ArbeidsforholdSjekk.Bekreftet -> {
                val opprettetUtkast = transaction(database) {
                    UtkastTable.insertReturning {
                        it[opprettetAv] = innloggetBruker
                        it[virksomhetsnummer] = mottatt.virksomhetsnummer
                        it[virksomhetsnavn] = mottatt.virksomhetsnavn
                        it[ansattFnr] = mottatt.fnr
                        it[ansattNavn] = deltaker.navn
                        it[ansattBekreftetTidspunkt] = Clock.System.now()
                    }.single().tilUtkastDTO()
                }
                call.respond(HttpStatusCode.Created, opprettetUtkast)
            }
        }
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
                        .orderBy(UtkastTable.opprettetTidspunkt to SortOrder.DESC)
                        .toList()
                        .map { it.tilUtkastDTO() }
                }
                call.respond(results)
            }

            SoknadStatusQueryParam.innsendt -> {
                val results = transaction(database) {
                    SoknadTable.selectAll()
                        .where {
                            (SoknadTable.virksomhetsnummer inList organisasjoner) and
                                (SoknadTable.arbeidsgiverTilgangTil greater Clock.System.now())
                        }
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
            is DTO.Utkast -> erOpprettetAvInnlogget(eksisterende)
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

        val oppdatertUtkast = call.receive<DTO.Utkast>().also { valider(it) }

        if (!erOpprettetAvInnlogget(eksisterende)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return
        }

        val nyEkspertVirksomhet = oppdatertUtkast.ekspert?.let { ekspert ->
            ekspertVirksomhet(ekspert.virksomhetOrgnr, lagretOrgnr = eksisterende.ekspert?.virksomhetOrgnr) ?: return
        }

        val oppdatert = transaction(database) {
            UtkastTable.updateReturning(
                where = { UtkastTable.id eq idParam }
            ) { utkast ->
                // Deltaker (fnr, navn), virksomhetsnummer og virksomhetsnavn settes bare av valider-deltaker.
                oppdatertUtkast.virksomhet?.also { v ->
                    utkast[kontaktpersonNavn] = v.kontaktperson.navn
                    utkast[kontaktpersonEpost] = v.kontaktperson.epost
                    utkast[kontaktpersonTelefon] = v.kontaktperson.telefonnummer
                }
                oppdatertUtkast.ekspert?.also { e ->
                    utkast[ekspertNavn] = e.navn
                    when (nyEkspertVirksomhet) {
                        is EkspertVirksomhet.Ny -> {
                            utkast[ekspertVirksomhetOrgnr] = nyEkspertVirksomhet.orgnr
                            utkast[ekspertVirksomhetNavn] = nyEkspertVirksomhet.navn
                            utkast[ekspertVirksomhet] = "${nyEkspertVirksomhet.navn} (${nyEkspertVirksomhet.orgnr})"
                        }

                        EkspertVirksomhet.Fjernet -> {
                            utkast[ekspertVirksomhetOrgnr] = null
                            utkast[ekspertVirksomhetNavn] = null
                            utkast[ekspertVirksomhet] = null
                        }

                        EkspertVirksomhet.Uendret, null -> Unit
                    }
                    utkast[ekspertKompetanse] = e.kompetanse
                    utkast[ekspertGodkjentUtdanningEllerAutorisasjon] = encodeStringList(e.godkjentUtdanningEllerAutorisasjon)
                    utkast[ekspertRelevantKompetanse] = encodeStringList(e.relevantKompetanse)
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

        if (!erOpprettetAvInnlogget(eksisterende)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til utkastet"
            )
            return
        }

        transaction(database) {
            UtkastTable.deleteWhere { UtkastTable.id eq idParam }
        }

        call.respond(HttpStatusCode.NoContent)
    }

    /**
     * Sender inn utkastet slik det er lagret. Request-body leses ikke. Se specifications/innsending_fra_utkast.md (6).
     */
    suspend fun RoutingContext.sendInnSoknad(idParam: UUID) {
        val utkast = transaction(database) {
            UtkastTable.selectAll().where { UtkastTable.id eq idParam }.singleOrNull()
        }

        if (utkast == null) {
            call.respondIkkeUtkast()
            return
        }

        if (utkast[UtkastTable.opprettetAv] != innloggetBruker) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til utkastet"
            )
            return
        }

        val soknad = validerKomplett(utkast)
        val adresse = eregService.hentForretningsadresse(soknad.virksomhetsnummer)

        val innsendt = transaction(database) {
            // Sletter først, så en samtidig innsending av samme utkast får 409 og ikke brudd på primærnøkkelen.
            if (UtkastTable.deleteWhere { UtkastTable.id eq idParam } == 0) return@transaction null

            val opprettet = SoknadTable.insertReturning {
                it[id] = idParam
                it[virksomhetsnummer] = soknad.virksomhetsnummer
                it[virksomhetsnavn] = soknad.virksomhetsnavn
                it[kontaktpersonNavn] = soknad.kontaktpersonNavn
                it[kontaktpersonEpost] = soknad.kontaktpersonEpost
                it[kontaktpersonTelefon] = soknad.kontaktpersonTelefon
                it[beliggenhetsadresse] = adresse
                it[ansattFnr] = soknad.ansattFnr
                it[ansattNavn] = soknad.ansattNavn
                it[ekspertNavn] = soknad.ekspertNavn
                it[ekspertVirksomhet] = soknad.ekspertVirksomhet
                it[ekspertVirksomhetNavn] = soknad.ekspertVirksomhetNavn
                it[ekspertVirksomhetOrgnr] = soknad.ekspertVirksomhetOrgnr
                it[ekspertKompetanse] = soknad.ekspertKompetanse
                it[ekspertGodkjentUtdanningEllerAutorisasjon] =
                    encodeStringList(soknad.ekspertGodkjentUtdanningEllerAutorisasjon)
                it[ekspertRelevantKompetanse] = encodeStringList(soknad.ekspertRelevantKompetanse)
                it[behovForBistand] = soknad.behovForBistand
                it[behovForBistandBegrunnelse] = soknad.behovForBistandBegrunnelse
                it[behovForBistandEstimertKostnad] = soknad.behovForBistandEstimertKostnad
                it[behovForBistandTimer] = soknad.behovForBistandTimer
                it[behovForBistandTilrettelegging] = soknad.behovForBistandTilrettelegging
                it[behovForBistandStartdato] = soknad.behovForBistandStartdato

                it[navKontaktPerson] = soknad.navKontaktperson
                it[opprettetAv] = innloggetBruker
                it[status] = SoknadStatus.innsendt.toString()
                it[arbeidsgiverTilgangTil] = Clock.System.now().plus(arbeidsgiverTilgangVarighet)
            }.single()

            publishEventQueue(EventData.SoknadInnsendt(opprettet.tilSoknadDomainV1()))

            opprettet.tilSoknadDTO()
        }

        if (innsendt == null) {
            call.respondIkkeUtkast()
            return
        }

        call.respond(innsendt)
    }

    /**
     * Validerer deltakeren, sjekker aktivt arbeidsforhold i virksomheten og henter navnet.
     * Se specifications/oppslag_deltaker_arbeidsforhold.md.
     */
    suspend fun RoutingContext.validerDeltaker(idParam: UUID) {
        val utkast = transaction(database) {
            findSoknadOrUtkastById(idParam)
        }

        if (utkast == null || utkast !is DTO.Utkast) {
            call.respond(
                status = HttpStatusCode.Conflict,
                message = "soknad ikke i utkast-status"
            )
            return
        }

        if (!erOpprettetAvInnlogget(utkast)) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til utkastet"
            )
            return
        }

        val mottatt = mottaDeltaker() ?: return

        if (utkast.ansatt?.fnr == mottatt.fnr &&
            utkast.virksomhet?.virksomhetsnummer == mottatt.virksomhetsnummer
        ) {
            call.respond(utkast)
            return
        }

        when (val deltaker = validerArbeidsforhold(mottatt) ?: return) {
            is ArbeidsforholdSjekk.IngenArbeidsforhold -> call.respondIngenArbeidsforhold()
            is ArbeidsforholdSjekk.Bekreftet -> call.respond(lagreDeltaker(idParam, mottatt, deltaker.navn))
        }
    }

    /**
     * Leser fnr og virksomhetsnummer, sjekker formatet og at innlogget bruker har Altinn-tilgang til virksomheten.
     * Virksomhetsnavnet hentes fra Altinn-tilgangene, så brukeren kan bare velge virksomheter hen har tilgang til.
     * Svarer 403 og gir null uten tilgang. Ugyldig input gir [UgyldigInputException].
     */
    private suspend fun RoutingContext.mottaDeltaker(): MottattDeltaker? {
        val request = call.receive<ValiderDeltakerRequest>().also { valider(it) }
        if (!erGyldigFodselsnummer(request.fnr)) {
            throw UgyldigInputException("fnr", "ugyldig fødselsnummer")
        }
        if (!request.virksomhetsnummer.matches(Regex("\\d{9}"))) {
            throw UgyldigInputException("virksomhetsnummer", "må være 9 siffer")
        }

        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val virksomhetsnavn = tilganger.navn(request.virksomhetsnummer)
        if (!tilganger.harTilgang(request.virksomhetsnummer) || virksomhetsnavn == null) {
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = "bruker har ikke tilgang til organisasjon"
            )
            return null
        }
        return MottattDeltaker(request.fnr, request.virksomhetsnummer, virksomhetsnavn)
    }

    /**
     * Slår opp arbeidsforholdet i Aa-registeret og navnet i PDL. Svarer og gir null ved
     * for mange oppslag (429) og når et oppslag feiler (503).
     */
    private suspend fun RoutingContext.validerArbeidsforhold(deltaker: MottattDeltaker): ArbeidsforholdSjekk? {
        if (!deltakerOppslagRateLimit.registrer(innloggetBruker, deltaker.fnr)) {
            call.respondFeil(
                HttpStatusCode.TooManyRequests,
                FOR_MANGE_OPPSLAG,
                "for mange oppslag på ulike fødselsnummer, prøv igjen om noen minutter"
            )
            return null
        }

        val harArbeidsforhold = try {
            deltakerOppslag.harAktivtArbeidsforhold(deltaker.fnr, deltaker.virksomhetsnummer)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            log.error("Oppslag av deltakers arbeidsforhold mot Aa-registeret feilet", e)
            call.respondOppslagFeilet()
            return null
        }

        if (!harArbeidsforhold) {
            return ArbeidsforholdSjekk.IngenArbeidsforhold
        }

        val navn = try {
            deltakerOppslag.hentNavn(deltaker.fnr)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            log.error("Oppslag av deltakers navn mot PDL feilet", e)
            call.respondOppslagFeilet()
            return null
        }

        return ArbeidsforholdSjekk.Bekreftet(navn)
    }

    /** Lagrer den bekreftede deltakeren og virksomheten. */
    private fun lagreDeltaker(
        idParam: UUID,
        deltaker: MottattDeltaker,
        navn: String,
    ): DTO.Utkast =
        transaction(database) {
            UtkastTable.updateReturning(
                where = { UtkastTable.id eq idParam }
            ) { utkast ->
                utkast[ansattFnr] = deltaker.fnr
                utkast[ansattNavn] = navn
                utkast[virksomhetsnummer] = deltaker.virksomhetsnummer
                utkast[virksomhetsnavn] = deltaker.virksomhetsnavn
                utkast[ansattBekreftetTidspunkt] = Clock.System.now()
            }.single().tilUtkastDTO()
        }

    /**
     * Ekspertens virksomhet ved PATCH. Bare orgnr leses fra klienten, og navnet slås opp i Ereg når orgnr er endret.
     * Ugyldig eller ukjent orgnr gir [UgyldigInputException]. Svarer 503 og gir null når Ereg feiler.
     * Se specifications/innsending_fra_utkast.md (5).
     */
    private suspend fun RoutingContext.ekspertVirksomhet(orgnr: String?, lagretOrgnr: String?): EkspertVirksomhet? {
        val nyttOrgnr = orgnr?.takeIf { it.isNotBlank() } ?: return EkspertVirksomhet.Fjernet
        if (nyttOrgnr == lagretOrgnr) return EkspertVirksomhet.Uendret
        if (!nyttOrgnr.matches(Regex("\\d{9}"))) {
            throw UgyldigInputException("ekspert.virksomhetOrgnr", "må være 9 siffer")
        }

        val navn = try {
            eregService.hentVirksomhetsnavn(nyttOrgnr)
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            log.error("Oppslag av ekspertens virksomhet mot Ereg feilet", e)
            call.respondFeil(
                HttpStatusCode.ServiceUnavailable,
                OPPSLAG_FEILET,
                "oppslag av virksomhet i Enhetsregisteret feilet, prøv igjen senere"
            )
            return null
        } ?: throw UgyldigInputException("ekspert.virksomhetOrgnr", "finnes ikke i Enhetsregisteret")

        return EkspertVirksomhet.Ny(nyttOrgnr, navn)
    }

    private suspend fun ApplicationCall.respondFeil(status: HttpStatusCode, kode: String, message: String) =
        respond(status, mapOf("kode" to kode, "message" to message))

    private suspend fun ApplicationCall.respondIkkeUtkast() =
        respond(status = HttpStatusCode.Conflict, message = "soknad ikke i utkast-status")

    private suspend fun ApplicationCall.respondOppslagFeilet() =
        respondFeil(HttpStatusCode.ServiceUnavailable, OPPSLAG_FEILET, "oppslag av deltaker feilet, prøv igjen senere")

    private suspend fun ApplicationCall.respondIngenArbeidsforhold() =
        respondFeil(HttpStatusCode.UnprocessableEntity, INGEN_ARBEIDSFORHOLD, "fant ikke aktivt arbeidsforhold i virksomheten")

    /** Utkast er private: bare den som opprettet utkastet kan se, endre, slette eller sende det inn. */
    private fun RoutingContext.erOpprettetAvInnlogget(utkast: DTO.Utkast): Boolean =
        utkast.opprettetAv == innloggetBruker

    fun slettGamleUtkast(
        ttl: Duration = 48.hours,
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
        /** Alltid true, fordi et utkast bare finnes med bekreftet deltaker. Ignoreres i innkommende utkast. */
        val deltakerBekreftet: Boolean = false,
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
    data class   Ekspert(
        val navn: String,
        val virksomhet: String,
        val virksomhetNavn: String? = null,
        val virksomhetOrgnr: String? = null,
        val kompetanse: String = "",
        val godkjentUtdanningEllerAutorisasjon: List<String> = emptyList(),
        val relevantKompetanse: List<String> = emptyList(),
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

/**
 * Aggregatroten for et søknads-event er søknadens id.
 *
 * id settes først etter persistering, så et event kan ikke publiseres før søknaden er lagret.
 */
val SoknadDomainV1.aggregateRootId: String
    get() = id ?: error("Soknad mangler id — event kan ikke publiseres før søknaden er persistert")

@Serializable
data class ValiderDeltakerRequest(
    val fnr: String,
    val virksomhetsnummer: String,
)

/** Deltakeren fra requesten, med virksomhetsnavnet fra Altinn-tilgangene til innlogget bruker. */
private data class MottattDeltaker(
    val fnr: String,
    val virksomhetsnummer: String,
    val virksomhetsnavn: String,
)

/** Resultatet av oppslagene i [SoknadApi.validerArbeidsforhold] når ingen av dem feilet. */
private sealed interface ArbeidsforholdSjekk {
    data class Bekreftet(val navn: String) : ArbeidsforholdSjekk
    data object IngenArbeidsforhold : ArbeidsforholdSjekk
}

/** Hva PATCH skal gjøre med ekspertens virksomhet, se [SoknadApi.ekspertVirksomhet]. */
private sealed interface EkspertVirksomhet {
    data object Uendret : EkspertVirksomhet
    data object Fjernet : EkspertVirksomhet
    data class Ny(val orgnr: String, val navn: String) : EkspertVirksomhet
}

const val FOR_MANGE_OPPSLAG = "FOR_MANGE_OPPSLAG"
const val INGEN_ARBEIDSFORHOLD = "INGEN_ARBEIDSFORHOLD"
const val OPPSLAG_FEILET = "OPPSLAG_FEILET"

@Suppress("EnumEntryName")
enum class SoknadStatus {
    utkast,
    innsendt,
    godkjent,
    avlyst,
    /** Nav har avslått søknaden. `avlyst` fra Arena kan bety både avslått og trukket. */
    avslatt,
}

@Suppress("EnumEntryName")
enum class SoknadStatusQueryParam {
    utkast,
    innsendt
}

val SoknadDomainV1.kvitteringsLenke: String
    get() = "$arbeidsgiverDomene/ekspertbistand/skjema/$id/kvittering"

fun kvitteringsLenkeV2(soknadId: String): String =
    "$arbeidsgiverDomene/ekspertbistand/v2/skjema/$soknadId/kvittering"

fun vedtakLenke(soknadId: String): String =
    "$arbeidsgiverDomene/ekspertbistand/v2/skjema/$soknadId/vedtak"

private val arbeidsgiverDomene: String
    get() = basedOnEnv(
        prod = "https://arbeidsgiver.nav.no",
        dev = "https://arbeidsgiver.intern.dev.nav.no",
        other = "https://arbeidsgiver.intern.dev.nav.no",
    )
