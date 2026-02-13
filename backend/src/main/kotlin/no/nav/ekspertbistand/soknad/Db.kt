package no.nav.ekspertbistand.soknad

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.datetime.timestampParam
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/** Slett innsendte søknader om 12 + 3 måneder siden siste oppdatering */
val slettSøknadOm = 455.days

@OptIn(ExperimentalTime::class)
object SoknadTable : Table("soknad") {
    val id = uuid("id")

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
    val ekspertKompetanse = text("ekspert_kompetanse")

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
    val sletteTidspunkt =
        timestamp("slett_tidspunkt").defaultExpression(timestampParam(Clock.System.now().plus(slettSøknadOm)))

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

    // Ekspert
    val ekspertNavn = text("ekspert_navn").nullable()
    val ekspertVirksomhet = text("ekspert_virksomhet").nullable()
    val ekspertKompetanse = text("ekspert_kompetanse").nullable()

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

fun findSoknadOrUtkastById(id: UUID): DTO? =
    findSoknadById(id) ?: findUtkastById(id)

fun findUtkastById(id: UUID): DTO.Utkast? = UtkastTable.selectAll()
    .where { UtkastTable.id eq id }
    .singleOrNull()?.tilUtkastDTO()

fun findSoknadById(id: UUID): DTO.Soknad? = SoknadTable.selectAll()
    .where { SoknadTable.id eq id }
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
        kompetanse = this[SoknadTable.ekspertKompetanse],
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
            kompetanse = this[UtkastTable.ekspertKompetanse] ?: "",
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
