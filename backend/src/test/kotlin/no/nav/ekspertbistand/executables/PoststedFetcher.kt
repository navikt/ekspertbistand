package no.nav.ekspertbistand.executables

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.infrastruktur.defaultHttpClient
import java.io.File

/**
 * Henter poststeder fra bring og skriver dem til en fil.
 * Kilde:
 * https://www.bring.no/tjenester/adressetjenester/postnummer
 *
 * Poststeder er definert som Tab-separerte felter (ANSI)
 * Mer om formatet her: https://www.bring.no/tjenester/adressetjenester/postnummer/postnummertabeller-veiledning
 *
 * I skrivende stund:
 * ```
 * # Layout, tabulatorseparert
 * | Post-nummer | Post-sted | Kommunekode (fylke 2 + kommune 2) | Kommune-navn | Kategori |
 * | ----------- | --------- | --------------------------------- | ------------ | -------- |
 * | 4           | 32        | 4                                 | 30           | 1        |
 *
 * Hvor kategori er:
 * G = Gateadresser (og stedsadresser), dvs. “grønne postkasser”
 * P = Postbokser
 * B = Både gateadresser og postbokser
 * S = Servicepostnummer (disse postnumrene er ikke i bruk til postadresser)
 * ```
 *
 * Eksempel data:
 * ```
 * 0001	OSLO	0301	OSLO	P
 * 0010	OSLO	0301	OSLO	B
 * 0015	OSLO	0301	OSLO	B
 * 0018	OSLO	0301	OSLO	S
 * 0021	OSLO	0301	OSLO	P
 * 0024	OSLO	0301	OSLO	P
 * 0026	OSLO	0301	OSLO	B
 * 0028	OSLO	0301	OSLO	P
 * ```
 *
 * Kjøres med enten repo-roten eller `backend/` som arbeidskatalog.
 * `PoststederTest` feiler når `poststeder.json` er utdatert mot Bring.
 */
class PoststedFetcher(
    private val client: HttpClient = defaultHttpClient { expectSuccess = false },
) {
    val targetFileName = "poststeder.json"
    val json = Json { prettyPrint = true }

    sealed interface Resultat {
        data class Hentet(val poststeder: Map<String, String>) : Resultat
        data object IkkeFunnet : Resultat
        data class Feil(val beskrivelse: String, val cause: Throwable? = null) : Resultat
    }

    suspend fun hentFraBring(): Resultat {
        val tsv = try {
            val response = client.get(URL)
            when (response.status) {
                HttpStatusCode.OK -> response.bodyAsText(
                    // csv fil er ANSI kodet, derfor ser fallbackCharset satt til ISO_8859_1
                    fallbackCharset = Charsets.ISO_8859_1
                )

                HttpStatusCode.NotFound -> return Resultat.IkkeFunnet
                else -> return Resultat.Feil("Uventet HTTP-status ${response.status} fra $URL")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Resultat.Feil("Klarte ikke hente $URL: ${e::class.simpleName}: ${e.message}", e)
        }
        return Resultat.Hentet(parse(tsv))
    }

    suspend fun fetchAndWriteToFile() {
        val poststeder = when (val resultat = hentFraBring()) {
            is Resultat.Hentet -> resultat.poststeder
            Resultat.IkkeFunnet -> error("Fant ikke $URL (404). Hent ny lenke fra $KILDE og oppdater URL.")
            is Resultat.Feil -> error(resultat.beskrivelse)
        }
        check(poststeder.size >= MINSTE_ANTALL) {
            "Fikk bare ${poststeder.size} postnummer fra Bring (forventet minst $MINSTE_ANTALL). Skriver ikke filen."
        }

        File(resourceDir(), targetFileName).writeText(json.encodeToString(poststeder))
    }

    private fun resourceDir(): File =
        listOf(File("src/main/resources"), File("backend/src/main/resources"))
            .firstOrNull { it.isDirectory }
            ?: error("Fant ikke src/main/resources. Kjør fra repo-roten eller backend/.")

    companion object {
        const val KILDE = "https://www.bring.no/tjenester/adressetjenester/postnummer"
        const val URL =
            "https://www.bring.no/tjenester/adressetjenester/postnummer/_/attachment/download/7f0186f6-cf90-4657-8b5b-70707abeb789:62b42f1b8274a60db4bba965e64c7cf2c43143e9/Postnummerregister-ansi.txt"
        const val MINSTE_ANTALL = 4_000

        fun parse(tsv: String): Map<String, String> =
            tsv.lines()
                .filter { it.isNotBlank() }
                .associate {
                    val (postnummer, poststed) = it.split("\t")
                    postnummer to poststed
                }
    }
}

fun main() = runBlocking {
    PoststedFetcher().fetchAndWriteToFile()
}
