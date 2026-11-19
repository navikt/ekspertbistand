package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable

/**
 * Et varsel til arbeidsgiver i notifikasjonsplattformen, slik vi bestilte det: eventuelt ny sak, beskjeden med
 * eventuelt eksternt varsel, og eventuelt ny status på saken. Har med det klienten bestemmer (merkelapp, mottaker og
 * sendevindu), fordi det avgjør hvem som får varselet og når.
 *
 * [sak] har deltakers navn og fødselsdato i [Sak.tittel], slik arbeidsgiver ser saken. Er [sak] satt, har objektet
 * personopplysninger og skal ikke logges. Ellers har det bare organisasjonsdata, søknads-id i lenkene og faste
 * tekster.
 *
 * [sak] har standardverdi fordi eldre eventer (bare i dev) mangler feltet.
 */
@Serializable
data class ArbeidsgiverVarselDomain(
    val merkelapp: String,
    val mottaker: Mottaker,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val sak: Sak? = null,
    val beskjed: Beskjed,
    val saksstatus: Saksstatus?,
) {
    @Serializable
    data class Mottaker(val altinnRessursId: String)

    @Serializable
    data class Sak(
        val tittel: String,
        val lenke: String,
    )

    @Serializable
    data class Beskjed(
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val eksterntVarsel: EksterntVarsel?,
    )

    @Serializable
    data class EksterntVarsel(
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendevindu: Sendevindu,
    )

    /** [nyLenkeTilSak] er `null` når lenken på saken skal stå uendret. */
    @Serializable
    data class Saksstatus(
        val status: Status,
        val statusTekst: String,
        val nyLenkeTilSak: String?,
    )

    @Serializable
    enum class Status { FERDIG }

    @Serializable
    enum class Sendevindu { NKS_AAPNINGSTID }
}
