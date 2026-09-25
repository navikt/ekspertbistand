package no.nav.ekspertbistand.infrastruktur

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * Kastes når et tekstfelt i en innkommende DTO inneholder en verdi som ikke passerer
 * [standardTekstSjekker]. Bærer feltstien (f.eks. `behovForBistand.begrunnelse`) og en
 * generisk [aarsak] for hvilken sjekk som feilet, men aldri selve verdien, slik at
 * angriperinput verken logges eller ekkoes tilbake i respons.
 */
class UgyldigInputException(val feltsti: String, val aarsak: String) :
    IllegalArgumentException("Ugyldig verdi i felt '$feltsti': $aarsak")

/**
 * En enkelt sjekk som kan kjøres på en tekstverdi. Returner `null` når verdien er ok,
 * ellers en kort grunn (uten selve verdien). Nye sjekker legges til i [standardTekstSjekker].
 */
fun interface TekstSjekk {
    fun sjekk(verdi: String): String?
}

/** Avviser verdier som inneholder `<` eller `>` (blokkerer script-/tag-injeksjon). */
val IngenVinkelparenteser = TekstSjekk { verdi ->
    if (verdi.any { it == '<' || it == '>' }) "inneholder < eller >" else null
}

/** Avviser kontrolltegn, med unntak av vanlig whitespace (tab, linjeskift). */
val IngenKontrolltegn = TekstSjekk { verdi ->
    if (verdi.any { it.isISOControl() && it != '\t' && it != '\n' && it != '\r' })
        "inneholder kontrolltegn"
    else null
}

/** Øvre grense for lengden på et enkelt tekstfelt (vern mot oversized payloads). */
const val MAKS_TEKST_LENGDE = 10_000

/** Avviser tekst som er lengre enn [MAKS_TEKST_LENGDE]. */
val IngenForLangTekst = TekstSjekk { verdi ->
    if (verdi.length > MAKS_TEKST_LENGDE) "for lang (over $MAKS_TEKST_LENGDE tegn)" else null
}

/**
 * Avviser usynlige Unicode-formattegn (kategori Cf): bidireksjonelle overstyringer/isolater
 * (U+202A–202E, U+2066–2069), zero-width-tegn (U+200B–200F), BOM (U+FEFF) m.fl. Beskytter mot
 * «Trojan Source»-angrep og homoglyf-/tekstspoofing. Vanlig norsk tekst inneholder ikke slike tegn.
 */
val IngenUsynligeFormatTegn = TekstSjekk { verdi ->
    if (verdi.any { Character.getType(it) == Character.FORMAT.toInt() })
        "inneholder usynlige formatkontrolltegn"
    else null
}

/**
 * Startsettet av tekstsjekker. Bevisst enkelt; utvid ved å legge til flere [TekstSjekk]
 * her, så slår de automatisk inn på alle tekstfelt uten andre endringer.
 */
val standardTekstSjekker: List<TekstSjekk> = listOf(
    IngenVinkelparenteser,
    IngenKontrolltegn,
    IngenForLangTekst,
    IngenUsynligeFormatTegn,
)

/** Øvre grense for antall elementer i en samling (vern mot svært store arrays). */
const val MAKS_LISTE_STORRELSE = 100

/**
 * Validerer alle `String`- og `String`-samlingsfelt i [dto] rekursivt mot [sjekker].
 * Går ned i nøstede data-klasser og samlinger. Kaster [UgyldigInputException] med feltsti
 * ved første brudd. Ikke-tekstfelt (tall, enum, dato, boolean) hoppes over.
 */
fun valider(dto: Any, sjekker: List<TekstSjekk> = standardTekstSjekker) {
    val besokt: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    validerVerdi(dto, "", sjekker, besokt)
}

private fun validerVerdi(
    verdi: Any?,
    sti: String,
    sjekker: List<TekstSjekk>,
    besokt: MutableSet<Any>,
) {
    when (verdi) {
        null -> return

        is String -> {
            for (sjekk in sjekker) {
                val aarsak = sjekk.sjekk(verdi)
                if (aarsak != null) throw UgyldigInputException(sti, aarsak)
            }
        }

        is Collection<*> -> {
            if (verdi.size > MAKS_LISTE_STORRELSE) {
                throw UgyldigInputException(sti, "for mange elementer (over $MAKS_LISTE_STORRELSE)")
            }
            verdi.forEachIndexed { index, element ->
                validerVerdi(element, "$sti[$index]", sjekker, besokt)
            }
        }

        else -> {
            if (verdi::class.isData && besokt.add(verdi)) {
                for (prop in verdi::class.memberProperties) {
                    val barnSti = if (sti.isEmpty()) prop.name else "$sti.${prop.name}"
                    validerVerdi(prop.getter.call(verdi), barnSti, sjekker, besokt)
                }
            }
        }
    }
}

/**
 * Samler feltstiene til alle tekstfelt ([String] og `Collection<String>`) som [valider] vil
 * besøke i DTO-typen [kClass]. Speiler typevalgene i [validerVerdi] og brukes av testene
 * (statisk analyse) for å garantere at ingen tekstfelt slipper unna valideringen når nye felt
 * eller DTO-er legges til. Samlingsfelt merkes med `[]`.
 */
fun tekstFeltStier(
    kClass: KClass<*>,
    prefiks: String = "",
    besokt: MutableSet<KClass<*>> = mutableSetOf(),
): Set<String> {
    if (!besokt.add(kClass)) return emptySet()
    val stier = mutableSetOf<String>()
    for (prop in kClass.memberProperties) {
        val sti = if (prefiks.isEmpty()) prop.name else "$prefiks.${prop.name}"
        val klassifisering = prop.returnType.classifier as? KClass<*> ?: continue
        when {
            klassifisering == String::class -> stier += sti

            klassifisering.java.let { Collection::class.java.isAssignableFrom(it) } -> {
                val element = prop.returnType.arguments.firstOrNull()?.type?.classifier as? KClass<*>
                when {
                    element == String::class -> stier += "$sti[]"
                    element != null && element.isData -> stier += tekstFeltStier(element, "$sti[]", besokt)
                }
            }

            klassifisering.isData -> stier += tekstFeltStier(klassifisering, sti, besokt)
        }
    }
    besokt.remove(kClass)
    return stier
}
