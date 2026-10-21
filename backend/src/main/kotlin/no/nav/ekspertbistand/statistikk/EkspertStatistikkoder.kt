package no.nav.ekspertbistand.statistikk

import no.nav.ekspertbistand.soknad.DTO

/**
 * Faste valg for ekspertens kompetanse i søknadsskjemaet, som koder til statistikk.
 *
 * Arbeidsgiver kan også skrive inn egne verdier. De blir [ANNET_KODE], fordi fritekst kan inneholde
 * personopplysninger og ikke kan grupperes. Etikettene må være like `GODKJENT_UTDANNING_ALTERNATIVER`
 * og `RELEVANT_KOMPETANSE_ALTERNATIVER` i `frontend/client/src/pages/SkjemaSteg1Page.tsx`.
 */
enum class EkspertUtdanning(val etikett: String) {
    ERGOTERAPEUT("Ergoterapeut"),
    FYSIOTERAPEUT("Fysioterapeut"),
    KARRIEREVEILEDER("Karriereveileder"),
    PSYKOLOG("Psykolog"),
    SYKEPLEIER("Sykepleier"),
}

/** Se [EkspertUtdanning]. */
enum class EkspertKompetanse(val etikett: String) {
    RELASJONER_OG_KOMMUNIKASJON("Relasjoner og kommunikasjon på arbeidsplassen"),
    TILRETTELEGGING("Tilrettelegging på arbeidsplassen"),
    KONFLIKTHANDTERING("Konflikthåndtering"),
    OMSTILLINGSARBEID("Omstillingsarbeid (endring i arbeidslivet)"),
    ARBEIDSPLASSVURDERING("Arbeidsplassvurdering"),
    OFFENTLIGE_VIRKEMIDLER("God kjennskap til offentlige virkemidler"),
    KARRIEREVEILEDNING("Karriereveiledning"),
}

const val ANNET_KODE = "ANNET"

private val kjenteUtdanninger = EkspertUtdanning.entries.associate { it.etikett.lowercase() to it.name }
private val kjenteKompetanser = EkspertKompetanse.entries.associate { it.etikett.lowercase() to it.name }

/** Sorterte, unike koder skilt med komma. Null når ingenting er valgt. */
private fun koder(verdier: List<String>, kjente: Map<String, String>): String? =
    verdier.map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { kjente[it.lowercase()] ?: ANNET_KODE }
        .distinct()
        .sorted()
        .joinToString(",")
        .ifEmpty { null }

/** Eldre søknader har bare fritekstfeltet `kompetanse`. Det blir [ANNET_KODE], som i `DokumentService`. */
val DTO.Ekspert.utdanningKoder: String?
    get() = koder(godkjentUtdanningEllerAutorisasjon.ifEmpty { listOf(kompetanse) }, kjenteUtdanninger)

val DTO.Ekspert.kompetanseKoder: String?
    get() = koder(relevantKompetanse, kjenteKompetanser)
