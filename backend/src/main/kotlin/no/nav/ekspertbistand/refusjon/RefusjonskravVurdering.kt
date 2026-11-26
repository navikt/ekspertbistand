package no.nav.ekspertbistand.refusjon

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.domain.RefusjonskravArsakDomain

@Serializable
enum class RefusjonskravStatuskode { MOTTATT, NY_INNSENDING_KREVES }

@Serializable
enum class RefusjonskravArsak(val kontrolltekst: String) {
    BELOP_MAA_JUSTERES("Beløpet i refusjonskravet stemmer med de dokumenterte utgiftene."),
    DOKUMENT_IKKE_FAKTURA("Vedleggene er fakturaer som dokumenterer utgiftene til ekspertbistand."),
    DOKUMENT_IKKE_LESELIG("Vedleggene er leselige og av god kvalitet."),
    ANNET("For at vi skal kunne behandle refusjonskravet må du korrigere opplysningene. " +
        "Vennligst send refusjonskravet på nytt og kontroller at vedleggene du laster opp er korrekte. " +
        "Dokumentene må være leselige, relevante for refusjonskravet og inneholde nødvendig informasjon " +
        "for at vi skal kunne behandle saken.");

    fun tilDomain(): RefusjonskravArsakDomain = when (this) {
        BELOP_MAA_JUSTERES -> RefusjonskravArsakDomain.BELOP_MAA_JUSTERES
        DOKUMENT_IKKE_FAKTURA -> RefusjonskravArsakDomain.DOKUMENT_IKKE_FAKTURA
        DOKUMENT_IKKE_LESELIG -> RefusjonskravArsakDomain.DOKUMENT_IKKE_LESELIG
        ANNET -> RefusjonskravArsakDomain.ANNET
    }
}

fun RefusjonskravArsakDomain.tilArsak(): RefusjonskravArsak = when (this) {
    RefusjonskravArsakDomain.BELOP_MAA_JUSTERES -> RefusjonskravArsak.BELOP_MAA_JUSTERES
    RefusjonskravArsakDomain.DOKUMENT_IKKE_FAKTURA -> RefusjonskravArsak.DOKUMENT_IKKE_FAKTURA
    RefusjonskravArsakDomain.DOKUMENT_IKKE_LESELIG -> RefusjonskravArsak.DOKUMENT_IKKE_LESELIG
    RefusjonskravArsakDomain.ANNET -> RefusjonskravArsak.ANNET
}

fun refusjonskravBeskjed(arsaker: Collection<RefusjonskravArsak>): String {
    val innledning = if (RefusjonskravArsak.ANNET in arsaker) RefusjonskravArsak.ANNET.kontrolltekst
    else "For at vi skal kunne behandle refusjonskravet, må du korrigere opplysningene. Send refusjonskravet på nytt."
    val punkter = RefusjonskravArsak.entries.filter { it in arsaker && it != RefusjonskravArsak.ANNET }
    return innledning + if (punkter.isEmpty()) "" else
        "\n\nKontroller at:\n" + punkter.joinToString("\n") { "- ${it.kontrolltekst}" }
}
