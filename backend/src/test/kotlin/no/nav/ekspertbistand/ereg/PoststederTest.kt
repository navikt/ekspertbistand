package no.nav.ekspertbistand.ereg

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.executables.PoststedFetcher
import no.nav.ekspertbistand.executables.PoststedFetcher.Resultat
import no.nav.ekspertbistand.infrastruktur.logger
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class PoststederTest {
    private val log = logger()

    /**
     * Feiler kun ved 404 eller utdatert liste. Nettverksfeil logges, slik at bygget
     * ikke stopper når Bring er utilgjengelig.
     */
    @Test
    fun `poststeder json er oppdatert mot Bring`() = runBlocking {
        when (val resultat = PoststedFetcher().hentFraBring()) {
            is Resultat.Hentet -> {
                val avvik = avvikMelding(lokalePoststeder(), resultat.poststeder)
                if (avvik != null) fail(avvik)
            }

            Resultat.IkkeFunnet -> fail(
                """
                Fant ikke Bring sin postnummertabell (404):
                  ${PoststedFetcher.URL}

                Bring har trolig publisert en ny fil under ny lenke. Slik oppdaterer du:
                  1. Hent lenken til «Postnummerregister - ANSI» fra ${PoststedFetcher.KILDE}
                  2. Oppdater URL i $FETCHER_STI
                  3. Kjør main() i $FETCHER_STI og commit endringene
                """.trimIndent()
            )

            is Resultat.Feil -> log.error(
                "Kunne ikke sjekke poststeder.json mot Bring: {}",
                resultat.beskrivelse,
                resultat.cause,
            )
        }
    }

    @Test
    fun `parse leser postnummer og poststed fra Bring-formatet`() {
        val tsv = "0001\tOSLO\t0301\tOSLO\tP\r\n" +
                "1424\tSKI\t3207\tNORDRE FOLLO\tB\r\n" +
                "9990\tBÅTSFJORD\t5632\tBÅTSFJORD\tG\r\n" +
                "4640\tSØGNE\t4204\tKRISTIANSAND\tB\r\n" +
                "\r\n"

        assertEquals(
            mapOf(
                "0001" to "OSLO",
                "1424" to "SKI",
                "9990" to "BÅTSFJORD",
                "4640" to "SØGNE",
            ),
            PoststedFetcher.parse(tsv),
        )
    }

    private fun lokalePoststeder(): Map<String, String> = Json.decodeFromString(
        PoststederTest::class.java.getResource("/poststeder.json")!!.readText()
    )

    private fun avvikMelding(lokale: Map<String, String>, bring: Map<String, String>): String? {
        val nye = (bring.keys - lokale.keys).sorted().map { "$it ${bring[it]}" }
        val fjernet = (lokale.keys - bring.keys).sorted().map { "$it ${lokale[it]}" }
        val endret = (lokale.keys intersect bring.keys)
            .filter { lokale[it] != bring[it] }
            .sorted()
            .map { "$it ${lokale[it]} -> ${bring[it]}" }

        if (nye.isEmpty() && fjernet.isEmpty() && endret.isEmpty()) return null

        return buildString {
            appendLine("poststeder.json er utdatert mot Bring sin postnummertabell.")
            appendLine()
            appendSeksjon("Nye postnummer", nye)
            appendSeksjon("Fjernet", fjernet)
            appendSeksjon("Endret poststed", endret)
            appendLine()
            appendLine("Slik oppdaterer du listen:")
            appendLine("  1. Kjør main() i $FETCHER_STI")
            appendLine("  2. Se over endringene i backend/src/main/resources/poststeder.json")
            append("  3. Commit filen")
        }
    }

    private fun StringBuilder.appendSeksjon(tittel: String, linjer: List<String>) {
        appendLine("$tittel (${linjer.size}):")
        linjer.take(MAKS_LINJER).forEach { appendLine("  $it") }
        if (linjer.size > MAKS_LINJER) appendLine("  … og ${linjer.size - MAKS_LINJER} til")
    }

    companion object {
        private const val MAKS_LINJER = 20
        private const val FETCHER_STI =
            "backend/src/test/kotlin/no/nav/ekspertbistand/executables/PoststedFetcher.kt"
    }
}
