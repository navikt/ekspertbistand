package no.nav.ekspertbistand.event.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Søknaden slik eventene i `event_log` og `arena_sak.soknad` har lagret den. Nøyaktig kopi av det
 * `DTO.Soknad` var da API-et og eventene delte modell, så samme JSON gir samme objekt. Ikke endre
 * feltnavn, nullbarhet eller standardverdier: da kan ikke eksisterende eventer leses.
 *
 * Har tre former for `ekspert` i prod: fritekst i [Ekspert.kompetanse] (før 2026-08-11), listene
 * med [Ekspert.virksomhetNavn]/[Ekspert.virksomhetOrgnr], og listene uten.
 * Se `specifications/innsending_fra_utkast.md`.
 *
 * Har fødselsnummer og navn på deltaker, og skal ikke logges.
 */
@Serializable
data class SoknadDomainV1(
    val id: String? = null,
    val virksomhet: Virksomhet,
    val ansatt: Ansatt,
    val ekspert: Ekspert,
    val behovForBistand: BehovForBistand,
    val nav: Nav,
    val opprettetAv: String? = null,
    val opprettetTidspunkt: String? = null,
    val status: Status = Status.innsendt,
) {
    @Suppress("EnumEntryName")
    @Serializable
    enum class Status { utkast, innsendt, godkjent, avlyst, avslatt }

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
        val telefonnummer: String,
    )

    @Serializable
    data class Ansatt(
        val fnr: String,
        val navn: String,
    )

    @Serializable
    data class Ekspert(
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
        val kontaktperson: String,
    )
}
