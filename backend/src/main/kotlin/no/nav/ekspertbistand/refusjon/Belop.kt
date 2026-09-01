package no.nav.ekspertbistand.refusjon

/**
 * Parser og validerer et refusjonsbeløp.
 *
 * Frontend sender hele kroner (heltall), men vi lagrer i øre (Long) for å unngå
 * flyttall og for å kunne støtte øre-presisjon senere uten datamodell-endring.
 *
 *  - Kun heltall kroner: "12500" -> 1_250_000 øre
 *  - Må være > 0 og <= MAKS_BELOP_KRONER
 *  - Ugyldig format (desimaler, tegn, tomt) kaster [UgyldigBelopException]
 */
const val MAKS_BELOP_KRONER: Int = 1_000_000

class UgyldigBelopException(message: String) : IllegalArgumentException(message)

fun belopKronerTilOre(input: String): Long {
    val kroner = input.trim().toIntOrNull()
        ?: throw UgyldigBelopException("Beløpet må være et helt antall kroner: «$input»")

    if (kroner <= 0) {
        throw UgyldigBelopException("Beløpet må være større enn 0")
    }
    if (kroner > MAKS_BELOP_KRONER) {
        throw UgyldigBelopException("Beløpet overstiger maksgrensen")
    }

    return kroner.toLong() * 100
}
