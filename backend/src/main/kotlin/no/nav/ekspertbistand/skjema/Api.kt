package no.nav.ekspertbistand.skjema

import kotlinx.serialization.Serializable

sealed interface DTO {
    @Serializable
    data class Skjema(
        val id: String,
        val organisasjonsnummer: String,
        val tittel: String,
        val beskrivelse: String,
        val opprettetAv: String?,
        val opprettetTidspunkt: String?
    ) : DTO {
        val status = SkjemaStatus.innsendt
    }

    @Serializable
    data class Utkast(
        val id: String? = null,
        val organisasjonsnummer: String?,
        val tittel: String? = null,
        val beskrivelse: String? = null,
        val opprettetAv: String? = null,
        val opprettetTidspunkt: String? = null,
    ) : DTO {
        val status = SkjemaStatus.utkast
    }
}

@Suppress("EnumEntryName")
enum class SkjemaStatus {
    utkast,
    innsendt,
}