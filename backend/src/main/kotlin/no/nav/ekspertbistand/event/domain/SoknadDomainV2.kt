package no.nav.ekspertbistand.event.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * En innsendt søknad, slik eventene lagrer den. Skilt fra [no.nav.ekspertbistand.soknad.DTO.Soknad], så API-et
 * kan endres uten at eventene i `event_log` knekker. Nullbarheten følger en innsendt søknad, ikke et utkast.
 * Lages med [no.nav.ekspertbistand.soknad.DTO.Soknad.tilDomain]. Se
 * `specifications/journalfor_vedtaksbrev_arbeidsgiver.md`.
 *
 * Har fødselsnummer og navn på deltaker, og skal ikke logges.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class SoknadDomainV2(
    val id: String,
    val virksomhet: Virksomhet,
    val ansatt: Ansatt,
    val ekspert: Ekspert,
    val behovForBistand: BehovForBistand,
    val nav: Nav,
    val opprettetAv: String,
    val opprettetTidspunkt: Instant,
    val status: Status,
) {
    @Suppress("EnumEntryName")
    @Serializable
    enum class Status { innsendt, godkjent, avlyst, avslatt }

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
        val kontaktperson: Kontaktperson,
        val beliggenhetsadresse: String?,
    )

    @Serializable
    data class Kontaktperson(val navn: String, val epost: String, val telefonnummer: String)

    @Serializable
    data class Ansatt(val fnr: String, val navn: String)

    @Serializable
    data class Ekspert(
        val navn: String,
        val virksomhet: String,
        val virksomhetNavn: String?,
        val virksomhetOrgnr: String?,
        val godkjentUtdanningEllerAutorisasjon: List<String>,
        val relevantKompetanse: List<String>,
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
    data class Nav(val kontaktperson: String)
}
