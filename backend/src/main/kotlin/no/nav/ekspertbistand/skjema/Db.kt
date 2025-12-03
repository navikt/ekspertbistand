package no.nav.ekspertbistand.skjema

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
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object SkjemaTable : Table("skjema") {
    val id = uuid("id")

    // Virksomhet
    val virksomhetsnummer = text("virksomhetsnummer").index()
    val virksomhetsnavn = text("virksomhetsnavn")
    val kontaktpersonNavn = text("kontaktperson_navn")
    val kontaktpersonEpost = text("kontaktperson_epost")
    val kontaktpersonTelefon = text("kontaktperson_telefon")

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
    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }

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

fun findSkjemaOrUtkastById(id: UUID): DTO? =
    SkjemaTable.selectAll()
        .where { SkjemaTable.id eq id }
        .singleOrNull()?.tilSkjemaDTO()
        ?: // If not found, try UtkastTable
        UtkastTable.selectAll()
            .where { UtkastTable.id eq id }
            .singleOrNull()?.tilUtkastDTO()

fun ResultRow.tilSkjemaDTO() = DTO.Skjema(
    id = this[SkjemaTable.id].toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = this[SkjemaTable.virksomhetsnummer],
        virksomhetsnavn = this[SkjemaTable.virksomhetsnavn],
        kontaktperson = DTO.Kontaktperson(
            navn = this[SkjemaTable.kontaktpersonNavn],
            epost = this[SkjemaTable.kontaktpersonEpost],
            telefonnummer = this[SkjemaTable.kontaktpersonTelefon]
        )
    ),
    ansatt = DTO.Ansatt(
        fnr = this[SkjemaTable.ansattFnr],
        navn = this[SkjemaTable.ansattNavn]
    ),
    ekspert = DTO.Ekspert(
        navn = this[SkjemaTable.ekspertNavn],
        virksomhet = this[SkjemaTable.ekspertVirksomhet],
        kompetanse = this[SkjemaTable.ekspertKompetanse],
    ),
    behovForBistand = DTO.BehovForBistand(
        begrunnelse = this[SkjemaTable.behovForBistandBegrunnelse],
        behov = this[SkjemaTable.behovForBistand],
        estimertKostnad = this[SkjemaTable.behovForBistandEstimertKostnad],
        timer = this[SkjemaTable.behovForBistandTimer],
        tilrettelegging = this[SkjemaTable.behovForBistandTilrettelegging],
        startdato = this[SkjemaTable.behovForBistandStartdato],

    ),
    nav = DTO.Nav(
        kontaktperson = this[SkjemaTable.navKontaktPerson]
    ),
    opprettetAv = this[SkjemaTable.opprettetAv],
    opprettetTidspunkt = this[SkjemaTable.opprettetTidspunkt]
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
