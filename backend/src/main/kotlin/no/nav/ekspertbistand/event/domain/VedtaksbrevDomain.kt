package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable

/**
 * Dataene i et vedtaksbrev, usladdet og ferdig formatert slik de stod i brevet. Med malen for utfall og mottaker
 * kan brevet rendres på nytt fra eventen alene. Verdiene er tekst, så brevet blir likt selv om formateringen
 * eller reglene for beløp og refusjonsfrist endres senere.
 *
 * Deltakers navn og begrunnelsen kan ha personopplysninger og helseopplysninger, og skal ikke logges.
 */
@Serializable
data class VedtaksbrevDomain(
    val dato: String,
    val deltaker: Deltaker,
    val arbeidsgiver: Arbeidsgiver,
    val soknadssum: String?,
    val innvilgetBelop: String?,
    val tiltaksperiode: Periode?,
    val refusjonsfrist: String?,
    val begrunnelse: String?,
    val navEnhet: String?,
    val saksbehandler: String?,
) {
    @Serializable
    data class Deltaker(val navn: String)

    @Serializable
    data class Arbeidsgiver(val navn: String, val orgnr: String)

    @Serializable
    data class Periode(val fra: String, val til: String)
}
