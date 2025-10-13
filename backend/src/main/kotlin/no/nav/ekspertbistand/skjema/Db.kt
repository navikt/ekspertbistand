package no.nav.ekspertbistand.skjema

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object SkjemaTable : Table("skjema") {
    val id = uuid("id")

    // Virksomhet
    val virksomhetsnummer = text("virksomhetsnummer").index()
    val kontaktpersonNavn = text("kontaktperson_navn")
    val kontaktpersonEpost = text("kontaktperson_epost")
    val kontaktpersonTelefon = text("kontaktperson_telefon")

    // Ansatt
    val ansattFodselsnummer = text("ansatt_fodselsnummer")
    val ansattNavn = text("ansatt_navn")

    // Ekspert
    val ekspertNavn = text("ekspert_navn")
    val ekspertVirksomhet = text("ekspert_virksomhet")
    val ekspertKompetanse = text("ekspert_kompetanse")
    val ekspertProblemstilling = text("ekspert_problemstilling")

    // Tiltak
    val tiltakForTilrettelegging = text("tiltak_for_tilrettelegging")

    // Bestilling
    val bestillingKostnad = text("bestilling_kostnad")
    val bestillingStartDato = text("bestilling_start_dato")

    // NAV
    val navKontakt = text("nav_kontakt")

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
    val kontaktpersonNavn = text("kontaktperson_navn").nullable()
    val kontaktpersonEpost = text("kontaktperson_epost").nullable()
    val kontaktpersonTelefon = text("kontaktperson_telefon").nullable()

    // Ansatt
    val ansattFodselsnummer = text("ansatt_fodselsnummer").nullable()
    val ansattNavn = text("ansatt_navn").nullable()

    // Ekspert
    val ekspertNavn = text("ekspert_navn").nullable()
    val ekspertVirksomhet = text("ekspert_virksomhet").nullable()
    val ekspertKompetanse = text("ekspert_kompetanse").nullable()
    val ekspertProblemstilling = text("ekspert_problemstilling").nullable()

    // Tiltak
    val tiltakForTilrettelegging = text("tiltak_for_tilrettelegging").nullable()

    // Bestilling
    val bestillingKostnad = text("bestilling_kostnad").nullable()
    val bestillingStartDato = text("bestilling_start_dato").nullable()

    // NAV
    val navKontakt = text("nav_kontakt").nullable()

    // Metadata
    val opprettetAv = text("opprettet_av").index()
    @OptIn(ExperimentalTime::class)
    val opprettetTidspunkt = text("opprettet_tidspunkt").clientDefault {
        Clock.System.now().toString()
    }
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
        kontaktperson = DTO.Kontaktperson(
            navn = this[SkjemaTable.kontaktpersonNavn],
            epost = this[SkjemaTable.kontaktpersonEpost],
            telefon = this[SkjemaTable.kontaktpersonTelefon]
        )
    ),
    ansatt = DTO.Ansatt(
        fodselsnummer = this[SkjemaTable.ansattFodselsnummer],
        navn = this[SkjemaTable.ansattNavn]
    ),
    ekspert = DTO.Ekspert(
        navn = this[SkjemaTable.ekspertNavn],
        virksomhet = this[SkjemaTable.ekspertVirksomhet],
        kompetanse = this[SkjemaTable.ekspertKompetanse],
        problemstilling = this[SkjemaTable.ekspertProblemstilling]
    ),
    tiltak = DTO.Tiltak(
        forTilrettelegging = this[SkjemaTable.tiltakForTilrettelegging]
    ),
    bestilling = DTO.Bestilling(
        kostnad = this[SkjemaTable.bestillingKostnad],
        startDato = this[SkjemaTable.bestillingStartDato]
    ),
    nav = DTO.Nav(
        kontakt = this[SkjemaTable.navKontakt]
    ),
    opprettetAv = this[SkjemaTable.opprettetAv],
    opprettetTidspunkt = this[SkjemaTable.opprettetTidspunkt]
)

fun ResultRow.tilUtkastDTO() = DTO.Utkast(
    id = this[UtkastTable.id].value.toString(),
    virksomhet = this[UtkastTable.virksomhetsnummer]?.let { virksomhetsnummer ->
        DTO.Virksomhet(
            virksomhetsnummer = virksomhetsnummer,
            kontaktperson = DTO.Kontaktperson(
                navn = this[UtkastTable.kontaktpersonNavn] ?: "",
                epost = this[UtkastTable.kontaktpersonEpost] ?: "",
                telefon = this[UtkastTable.kontaktpersonTelefon] ?: ""
            )
        )
    },
    ansatt = this[UtkastTable.ansattFodselsnummer]?.let { fodselsnummer ->
        DTO.Ansatt(
            fodselsnummer = fodselsnummer,
            navn = this[UtkastTable.ansattNavn] ?: ""
        )
    },
    ekspert = this[UtkastTable.ekspertNavn]?.let { navn ->
        DTO.Ekspert(
            navn = navn,
            virksomhet = this[UtkastTable.ekspertVirksomhet] ?: "",
            kompetanse = this[UtkastTable.ekspertKompetanse] ?: "",
            problemstilling = this[UtkastTable.ekspertProblemstilling] ?: ""
        )
    },
    tiltak = this[UtkastTable.tiltakForTilrettelegging]?.let { forTilrettelegging ->
        DTO.Tiltak(forTilrettelegging = forTilrettelegging)
    },
    bestilling = this[UtkastTable.bestillingKostnad]?.let { kostnad ->
        DTO.Bestilling(
            kostnad = kostnad,
            startDato = this[UtkastTable.bestillingStartDato] ?: ""
        )
    },
    nav = this[UtkastTable.navKontakt]?.let { kontakt ->
        DTO.Nav(kontakt = kontakt)
    },
    opprettetAv = this[UtkastTable.opprettetAv],
    opprettetTidspunkt = this[UtkastTable.opprettetTidspunkt]
)