package no.nav.ekspertbistand.soknad

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Arbeidsgiver har tilgang til en innsendt søknad i 12 + 3 måneder etter siste oppdatering.
 * Dataene beholdes lokalt i 10 år, se [AggregateRetention].
 */
val arbeidsgiverTilgangVarighet = 455.days

@OptIn(ExperimentalTime::class)
object SoknadTable : Table("soknad") {
    val id = javaUUID("id")

    // Virksomhet
    val virksomhetsnummer = text("virksomhetsnummer").index()
    val virksomhetsnavn = text("virksomhetsnavn")
    val kontaktpersonNavn = text("kontaktperson_navn")
    val kontaktpersonEpost = text("kontaktperson_epost")
    val kontaktpersonTelefon = text("kontaktperson_telefon")
    val beliggenhetsadresse = text("beliggenhetsadresse").nullable()

    // Ansatt
    val ansattFnr = text("ansatt_fnr")
    val ansattNavn = text("ansatt_navn")

    // Ekspert
    val ekspertNavn = text("ekspert_navn")
    val ekspertVirksomhet = text("ekspert_virksomhet")
    val ekspertVirksomhetNavn = text("ekspert_virksomhet_navn").nullable()
    val ekspertVirksomhetOrgnr = text("ekspert_virksomhet_orgnr").nullable()
    val ekspertKompetanse = text("ekspert_kompetanse")
    val ekspertGodkjentUtdanningEllerAutorisasjon =
        text("ekspert_godkjent_utdanning_eller_autorisasjon").nullable()
    val ekspertRelevantKompetanse = text("ekspert_relevant_kompetanse").nullable()

    // Behov for bistand
    val behovForBistandBegrunnelse = text("behov_for_bistand_begrunnelse")
    val behovForBistand = text("behov_for_bistand")
    val behovForBistandEstimertKostnad = text("behov_for_bistand_estimert_kostnad")
    val behovForBistandTimer = text("behov_for_bistand_timer")
    val behovForBistandTilrettelegging = text("behov_for_bistand_tilrettelegging")
    val behovForBistandStartdato = date("behov_for_bistand_startdato")

    // NAV
    val navKontaktPerson = text("nav_kontaktperson")

    // Metadata
    val opprettetAv = text("opprettet_av")

    val opprettetTidspunkt = timestamp("opprettet_tidspunkt").defaultExpression(CurrentTimestamp)
    val arbeidsgiverTilgangTil = timestamp("arbeidsgiver_tilgang_til")
        .clientDefault { Clock.System.now().plus(arbeidsgiverTilgangVarighet) }

    val status = text("status")

    override val primaryKey = PrimaryKey(id)
}


object UtkastTable : UUIDTable("utkast") {
    // Virksomhet
    val virksomhetsnummer = text("virksomhetsnummer").nullable().index()
    val virksomhetsnavn = text("virksomhetsnavn").nullable()
    val kontaktpersonNavn = text("kontaktperson_navn").nullable()
    val kontaktpersonEpost = text("kontaktperson_epost").nullable()
    val kontaktpersonTelefon = text("kontaktperson_telefon").nullable()

    // Ansatt
    val ansattFnr = text("ansatt_fnr").nullable()
    val ansattNavn = text("ansatt_navn").nullable()

    /** Satt når arbeidsforholdet er bekreftet i Aa-registeret og navnet er hentet fra PDL. */
    @OptIn(ExperimentalTime::class)
    val ansattBekreftetTidspunkt = timestamp("ansatt_bekreftet_tidspunkt").nullable()

    // Ekspert
    val ekspertNavn = text("ekspert_navn").nullable()
    val ekspertVirksomhet = text("ekspert_virksomhet").nullable()
    val ekspertVirksomhetNavn = text("ekspert_virksomhet_navn").nullable()
    val ekspertVirksomhetOrgnr = text("ekspert_virksomhet_orgnr").nullable()
    val ekspertKompetanse = text("ekspert_kompetanse").nullable()
    val ekspertGodkjentUtdanningEllerAutorisasjon =
        text("ekspert_godkjent_utdanning_eller_autorisasjon").nullable()
    val ekspertRelevantKompetanse = text("ekspert_relevant_kompetanse").nullable()

    // Behov for bistand
    val behovForBistandBegrunnelse = text("behov_for_bistand_begrunnelse").nullable()
    val behovForBistand = text("behov_for_bistand").nullable()
    val behovForBistandEstimertKostnad = text("behov_for_bistand_estimert_kostnad").nullable()
    val behovForBistandTimer = text("behov_for_bistand_timer").nullable()
    val behovForBistandTilrettelegging = text("behov_for_bistand_tilrettelegging").nullable()
    val behovForBistandStartdato = date("behov_for_bistand_startdato").nullable()

    // NAV
    val navKontaktPerson = text("nav_kontaktperson").nullable()

    // Metadata
    val opprettetAv = text("opprettet_av").index()

    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = timestamp("opprettet_tidspunkt").defaultExpression(CurrentTimestamp)
}

private val jsonParser = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

private fun parseJsonStringList(value: String?): List<String> =
    if (value.isNullOrBlank()) emptyList()
    else runCatching { jsonParser.decodeFromString(stringListSerializer, value) }.getOrElse { emptyList() }

internal fun encodeStringList(list: List<String>): String =
    jsonParser.encodeToString(stringListSerializer, list)

/** Se [findSoknadById] for [filterForArbeidsgiver]. */
@OptIn(ExperimentalTime::class)
fun findSoknadOrUtkastById(
    id: UUID,
    filterForArbeidsgiver: Boolean = true,
    now: Instant = Clock.System.now(),
): DTO? = findSoknadById(id, filterForArbeidsgiver, now) ?: findUtkastById(id)

fun findUtkastById(id: UUID): DTO.Utkast? = UtkastTable.selectAll()
    .where { UtkastTable.id eq id }
    .singleOrNull()?.tilUtkastDTO()

/**
 * Henter søknaden. Med [filterForArbeidsgiver] (standard) skjules søknaden når arbeidsgivers
 * tilgang er utløpt ([SoknadTable.arbeidsgiverTilgangTil]). Saksbehandling må sende `false`.
 */
@OptIn(ExperimentalTime::class)
fun findSoknadById(
    id: UUID,
    filterForArbeidsgiver: Boolean = true,
    now: Instant = Clock.System.now(),
): DTO.Soknad? = SoknadTable.selectAll()
    .where {
        if (filterForArbeidsgiver) {
            (SoknadTable.id eq id) and (SoknadTable.arbeidsgiverTilgangTil greater now)
        } else {
            SoknadTable.id eq id
        }
    }
    .singleOrNull()?.tilSoknadDTO()

@OptIn(ExperimentalTime::class)
fun ResultRow.tilSoknadDTO() = DTO.Soknad(
    id = this[SoknadTable.id].toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = this[SoknadTable.virksomhetsnummer],
        virksomhetsnavn = this[SoknadTable.virksomhetsnavn],
        kontaktperson = DTO.Kontaktperson(
            navn = this[SoknadTable.kontaktpersonNavn],
            epost = this[SoknadTable.kontaktpersonEpost],
            telefonnummer = this[SoknadTable.kontaktpersonTelefon],
        ),
        beliggenhetsadresse = this[SoknadTable.beliggenhetsadresse]
    ),
    ansatt = DTO.Ansatt(
        fnr = this[SoknadTable.ansattFnr],
        navn = this[SoknadTable.ansattNavn]
    ),
    ekspert = DTO.Ekspert(
        navn = this[SoknadTable.ekspertNavn],
        virksomhet = this[SoknadTable.ekspertVirksomhet],
        virksomhetNavn = this[SoknadTable.ekspertVirksomhetNavn],
        virksomhetOrgnr = this[SoknadTable.ekspertVirksomhetOrgnr],
        kompetanse = this[SoknadTable.ekspertKompetanse],
        godkjentUtdanningEllerAutorisasjon = parseJsonStringList(this[SoknadTable.ekspertGodkjentUtdanningEllerAutorisasjon]),
        relevantKompetanse = parseJsonStringList(this[SoknadTable.ekspertRelevantKompetanse]),
    ),
    behovForBistand = DTO.BehovForBistand(
        begrunnelse = this[SoknadTable.behovForBistandBegrunnelse],
        behov = this[SoknadTable.behovForBistand],
        estimertKostnad = this[SoknadTable.behovForBistandEstimertKostnad],
        timer = this[SoknadTable.behovForBistandTimer],
        tilrettelegging = this[SoknadTable.behovForBistandTilrettelegging],
        startdato = this[SoknadTable.behovForBistandStartdato],

        ),
    nav = DTO.Nav(
        kontaktperson = this[SoknadTable.navKontaktPerson]
    ),
    opprettetAv = this[SoknadTable.opprettetAv],
    opprettetTidspunkt = this[SoknadTable.opprettetTidspunkt].toString(),
    status = SoknadStatus.valueOf(this[SoknadTable.status])
)

@OptIn(ExperimentalTime::class)
fun ResultRow.tilUtkastDTO() = DTO.Utkast(
    id = this[UtkastTable.id].value.toString(),
    virksomhet = this[UtkastTable.virksomhetsnummer]?.let { virksomhetsnummer ->
        DTO.Virksomhet(
            virksomhetsnummer = virksomhetsnummer,
            virksomhetsnavn = this[UtkastTable.virksomhetsnavn] ?: "",
            kontaktperson = DTO.Kontaktperson(
                navn = this[UtkastTable.kontaktpersonNavn] ?: "",
                epost = this[UtkastTable.kontaktpersonEpost] ?: "",
                telefonnummer = this[UtkastTable.kontaktpersonTelefon] ?: ""
            )
        )
    },
    ansatt = this[UtkastTable.ansattFnr]?.let { fnr ->
        DTO.Ansatt(
            fnr = fnr,
            navn = this[UtkastTable.ansattNavn] ?: ""
        )
    },
    ekspert = this[UtkastTable.ekspertNavn]?.let { navn ->
        DTO.Ekspert(
            navn = navn,
            virksomhet = this[UtkastTable.ekspertVirksomhet] ?: "",
            virksomhetNavn = this[UtkastTable.ekspertVirksomhetNavn],
            virksomhetOrgnr = this[UtkastTable.ekspertVirksomhetOrgnr],
            kompetanse = this[UtkastTable.ekspertKompetanse] ?: "",
            godkjentUtdanningEllerAutorisasjon = parseJsonStringList(this[UtkastTable.ekspertGodkjentUtdanningEllerAutorisasjon]),
            relevantKompetanse = parseJsonStringList(this[UtkastTable.ekspertRelevantKompetanse]),
        )
    },
    behovForBistand = DTO.BehovForBistand(
        begrunnelse = this[UtkastTable.behovForBistandBegrunnelse] ?: "",
        behov = this[UtkastTable.behovForBistand] ?: "",
        estimertKostnad = this[UtkastTable.behovForBistandEstimertKostnad] ?: "",
        timer = this[UtkastTable.behovForBistandTimer] ?: "",
        tilrettelegging = this[UtkastTable.behovForBistandTilrettelegging] ?: "",
        startdato = this[UtkastTable.behovForBistandStartdato] ?: LocalDate.today(),

        ),
    nav = this[UtkastTable.navKontaktPerson]?.let { kontaktperson ->
        DTO.Nav(kontaktperson = kontaktperson)
    },
    opprettetAv = this[UtkastTable.opprettetAv],
    opprettetTidspunkt = this[UtkastTable.opprettetTidspunkt].toString()
)

@OptIn(ExperimentalTime::class)
private fun LocalDate.Companion.today(): LocalDate = Clock.System.now().toLocalDateTime(currentSystemDefault()).date
