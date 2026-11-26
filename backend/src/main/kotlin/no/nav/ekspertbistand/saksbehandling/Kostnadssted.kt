package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable

/** Enheten i OeBS som belastes for tilskuddet. [navn] er uten enhetsnummer. */
@Serializable
data class Kostnadssted(val enhetsnummer: String, val navn: String)

/**
 * Kostnadsstedene saksbehandler kan velge ved innvilgelse. Midlertidig liste til kort 727 har
 * avklart alle kostnadssteder. Se `specifications/kostnadssted_forelopig_vedtak.md`.
 */
val KOSTNADSSTEDER: List<Kostnadssted> = listOf(
    Kostnadssted("0291", "Nav arbeidslivssenter Øst-Viken"),
    Kostnadssted("0391", "Nav arbeidslivssenter Oslo"),
    Kostnadssted("0491", "Nav arbeidslivssenter Innlandet"),
    Kostnadssted("0691", "Nav arbeidslivssenter Vest-Viken"),
    Kostnadssted("0891", "Nav arbeid og helse Vestfold og Telemark"),
    Kostnadssted("1091", "Nav arbeidslivssenter Agder"),
    Kostnadssted("1191", "Nav arbeidslivssenter Rogaland"),
    Kostnadssted("1291", "Nav arbeidslivssenter Vestland"),
    Kostnadssted("1591", "Nav arbeidslivssenter Møre og Romsdal"),
    Kostnadssted("1891", "Nav arbeidslivssenter Nordland"),
    Kostnadssted("1991", "Nav arbeidslivssenter Troms og Finnmark"),
    Kostnadssted("5772", "Nav arbeidslivssenter Trøndelag"),
)

private val KOSTNADSSTEDER_ETTER_NUMMER = KOSTNADSSTEDER.associateBy { it.enhetsnummer }

fun finnKostnadssted(enhetsnummer: String): Kostnadssted? = KOSTNADSSTEDER_ETTER_NUMMER[enhetsnummer]
