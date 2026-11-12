package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable

/**
 * Et varsel til arbeidsgiver i notifikasjonsplattformen, slik vi bestilte det: beskjeden med eksternt varsel, og
 * den nye statusen på saken. Har med det klienten bestemmer (merkelapp, mottaker og sendevindu), fordi det avgjør
 * hvem som får varselet og når.
 *
 * Har bare organisasjonsdata, søknads-id i lenkene og faste tekster.
 */
@Serializable
data class ArbeidsgiverVarselDomain(
    val merkelapp: String,
    val mottaker: Mottaker,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val beskjed: Beskjed,
    val saksstatus: Saksstatus,
) {
    @Serializable
    data class Mottaker(val altinnRessursId: String)

    @Serializable
    data class Beskjed(
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val eksterntVarsel: EksterntVarsel,
    )

    @Serializable
    data class EksterntVarsel(
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendevindu: Sendevindu,
    )

    @Serializable
    data class Saksstatus(
        val status: Status,
        val statusTekst: String,
        val nyLenkeTilSak: String,
    )

    @Serializable
    enum class Status { FERDIG }

    @Serializable
    enum class Sendevindu { NKS_AAPNINGSTID }
}
