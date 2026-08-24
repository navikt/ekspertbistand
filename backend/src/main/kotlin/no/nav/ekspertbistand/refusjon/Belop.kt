package no.nav.ekspertbistand.refusjon

/**
 * Parser og validerer et refusjonsbeløp i hele kroner.
 *
 *  - Kun heltall: "12500" -> 12500
 *  - Må være > 0 og <= MAKS_BELOP_KRONER
 *  - Ugyldig format (desimaler, tegn, tomt) kaster [UgyldigBelopException]
 */
const val MAKS_BELOP_KRONER: Int = 1_000_000

class UgyldigBelopException(message: String) : IllegalArgumentException(message)

fun belopKroner(input: String): Int {
    val kroner = input.trim().toIntOrNull()
        ?: throw UgyldigBelopException("Beløpet må være et helt antall kroner: «$input»")

    if (kroner <= 0) {
        throw UgyldigBelopException("Beløpet må være større enn 0")
    }
    if (kroner > MAKS_BELOP_KRONER) {
        throw UgyldigBelopException("Beløpet overstiger maksgrensen")
    }

    return kroner
}
