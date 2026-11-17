package no.nav.ekspertbistand.soknad

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.saksbehandling.gjeldendeMaksSats
import no.nav.ekspertbistand.saksbehandling.tolkSoknadssum
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Et utkast der alle påkrevde felt er fylt ut og gyldige. Lages bare av [validerKomplett], og er det
 * innsendingen kopierer til `soknad`.
 */
data class InnsendingsklarSoknad(
    val virksomhetsnummer: String,
    val virksomhetsnavn: String,
    val kontaktpersonNavn: String,
    val kontaktpersonEpost: String,
    val kontaktpersonTelefon: String,
    val ansattFnr: String,
    val ansattNavn: String,
    val ekspertNavn: String,
    val ekspertVirksomhet: String,
    val ekspertVirksomhetNavn: String,
    val ekspertVirksomhetOrgnr: String,
    val ekspertKompetanse: String,
    val ekspertGodkjentUtdanningEllerAutorisasjon: List<String>,
    val ekspertRelevantKompetanse: List<String>,
    val behovForBistandBegrunnelse: String,
    val behovForBistand: String,
    val behovForBistandEstimertKostnad: String,
    val behovForBistandTimer: String,
    val behovForBistandTilrettelegging: String,
    val behovForBistandStartdato: LocalDate,
    val navKontaktperson: String,
)

private val niSiffer = Regex("""\d{9}""")
private val telefonnummer = Regex("""\d{8}""")
private val epost = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")
private val oslo = TimeZone.of("Europe/Oslo")

@OptIn(ExperimentalTime::class)
private fun dagensDato(): LocalDate = Clock.System.now().toLocalDateTime(oslo).date

/**
 * Sjekker at utkastet kan sendes inn. Reglene speiler `soknadSchema` i frontend, se
 * `specifications/innsending_fra_utkast.md` (3). Leser raden i [UtkastTable] og ikke [DTO.Utkast], fordi
 * [tilUtkastDTO] fyller inn tom tekst og dagens dato der kolonnen er null.
 *
 * Kaster [UgyldigInputException] med feltstien til første feil.
 */
@OptIn(ExperimentalTime::class)
fun validerKomplett(utkast: ResultRow, idag: LocalDate = dagensDato()): InnsendingsklarSoknad {
    val virksomhetsnummer = utkast[UtkastTable.virksomhetsnummer]
        .kreverMonster("virksomhet.virksomhetsnummer", niSiffer, "må være 9 siffer")
    val virksomhetsnavn = utkast[UtkastTable.virksomhetsnavn].kreverTekst("virksomhet.virksomhetsnavn")
    val kontaktpersonNavn = utkast[UtkastTable.kontaktpersonNavn].kreverTekst("virksomhet.kontaktperson.navn")
    val kontaktpersonEpost = utkast[UtkastTable.kontaktpersonEpost]
        .kreverMonster("virksomhet.kontaktperson.epost", epost, "ugyldig e-postadresse")
    val kontaktpersonTelefon = utkast[UtkastTable.kontaktpersonTelefon]
        .kreverMonster("virksomhet.kontaktperson.telefonnummer", telefonnummer, "må være 8 siffer")

    val ansattFnr = utkast[UtkastTable.ansattFnr]
    val ansattNavn = utkast[UtkastTable.ansattNavn]
    if (utkast[UtkastTable.ansattBekreftetTidspunkt] == null || ansattFnr == null || ansattNavn.isNullOrBlank()) {
        throw UgyldigInputException("ansatt.fnr", "deltaker er ikke bekreftet")
    }

    val ekspertNavn = utkast[UtkastTable.ekspertNavn].kreverTekst("ekspert.navn")
    val ekspertVirksomhet = utkast[UtkastTable.ekspertVirksomhet].kreverTekst("ekspert.virksomhet")
    val ekspertVirksomhetNavn = utkast[UtkastTable.ekspertVirksomhetNavn].kreverTekst("ekspert.virksomhetNavn")
    val ekspertVirksomhetOrgnr = utkast[UtkastTable.ekspertVirksomhetOrgnr]
        .kreverMonster("ekspert.virksomhetOrgnr", niSiffer, "må være 9 siffer")
    val godkjentUtdanning = parseJsonStringList(utkast[UtkastTable.ekspertGodkjentUtdanningEllerAutorisasjon])
        .kreverMinstEn("ekspert.godkjentUtdanningEllerAutorisasjon")
    val relevantKompetanse = parseJsonStringList(utkast[UtkastTable.ekspertRelevantKompetanse])
        .kreverMinstEn("ekspert.relevantKompetanse")

    val begrunnelse = utkast[UtkastTable.behovForBistandBegrunnelse].kreverTekst("behovForBistand.begrunnelse")
    val behov = utkast[UtkastTable.behovForBistand].kreverTekst("behovForBistand.behov")
    val tilrettelegging = utkast[UtkastTable.behovForBistandTilrettelegging]
        .kreverTekst("behovForBistand.tilrettelegging")
    val timer = utkast[UtkastTable.behovForBistandTimer]
    if (timer?.trim()?.toIntOrNull()?.takeIf { it > 0 } == null) {
        throw UgyldigInputException("behovForBistand.timer", "må være et positivt heltall")
    }

    val startdato = utkast[UtkastTable.behovForBistandStartdato]
        ?: throw UgyldigInputException("behovForBistand.startdato", "mangler")
    if (startdato < idag) {
        throw UgyldigInputException("behovForBistand.startdato", "kan ikke være i fortiden")
    }
    val sats = gjeldendeMaksSats(startdato)
        ?: throw UgyldigInputException("behovForBistand.startdato", "ingen sats gjelder for startdato")
    val estimertKostnad = utkast[UtkastTable.behovForBistandEstimertKostnad]
    val soknadssum = estimertKostnad?.let(::tolkSoknadssum)
        ?: throw UgyldigInputException("behovForBistand.estimertKostnad", "må være et positivt heltall")
    if (soknadssum > sats.belop) {
        throw UgyldigInputException("behovForBistand.estimertKostnad", "over maksgrensen på ${sats.belop} kr")
    }

    val navKontaktperson = utkast[UtkastTable.navKontaktPerson].kreverTekst("nav.kontaktperson")

    return InnsendingsklarSoknad(
        virksomhetsnummer = virksomhetsnummer,
        virksomhetsnavn = virksomhetsnavn,
        kontaktpersonNavn = kontaktpersonNavn,
        kontaktpersonEpost = kontaktpersonEpost,
        kontaktpersonTelefon = kontaktpersonTelefon,
        ansattFnr = ansattFnr,
        ansattNavn = ansattNavn,
        ekspertNavn = ekspertNavn,
        ekspertVirksomhet = ekspertVirksomhet,
        ekspertVirksomhetNavn = ekspertVirksomhetNavn,
        ekspertVirksomhetOrgnr = ekspertVirksomhetOrgnr,
        ekspertKompetanse = utkast[UtkastTable.ekspertKompetanse] ?: "",
        ekspertGodkjentUtdanningEllerAutorisasjon = godkjentUtdanning,
        ekspertRelevantKompetanse = relevantKompetanse,
        behovForBistandBegrunnelse = begrunnelse,
        behovForBistand = behov,
        behovForBistandEstimertKostnad = estimertKostnad,
        behovForBistandTimer = timer,
        behovForBistandTilrettelegging = tilrettelegging,
        behovForBistandStartdato = startdato,
        navKontaktperson = navKontaktperson,
    )
}

private fun String?.kreverTekst(feltsti: String): String =
    takeUnless { it.isNullOrBlank() } ?: throw UgyldigInputException(feltsti, "mangler")

private fun String?.kreverMonster(feltsti: String, monster: Regex, aarsak: String): String {
    val verdi = kreverTekst(feltsti)
    if (!monster.matches(verdi.trim())) throw UgyldigInputException(feltsti, aarsak)
    return verdi
}

private fun List<String>.kreverMinstEn(feltsti: String): List<String> =
    ifEmpty { throw UgyldigInputException(feltsti, "må ha minst ett valg") }
