package no.nav.ekspertbistand.executables

import io.ktor.client.request.*
import io.ktor.client.statement.*
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
 */
class PoststedFetcher {
    val url =
        "https://www.bring.no/tjenester/adressetjenester/postnummer/_/attachment/download/7f0186f6-cf90-4657-8b5b-70707abeb789:62b42f1b8274a60db4bba965e64c7cf2c43143e9/Postnummerregister-ansi.txt"
    val client = defaultHttpClient()
    val resourceDir = File("backend/src/main/resources")
    val targetFileName = "poststeder.json"
    val json = Json { prettyPrint = true }

    suspend fun fetchAndWriteToFile() {
        val csv = client.get(url).bodyAsText(
            // csv fil er ANSI kodet, derfor ser fallbackCharset satt til ISO_8859_1
            fallbackCharset = Charsets.ISO_8859_1
        )

        val mapping = csv.lines()
            .filter { it.isNotBlank() }
            .associate {
                val (postnummer, poststed, kommunekode, kommunenavn, kategori) = it.split("\t")
                postnummer to poststed
            }

        File(resourceDir, targetFileName).also {
            resourceDir.mkdirs()
        }.writeText(json.encodeToString(mapping))
    }
}

fun main() = runBlocking {
    PoststedFetcher().fetchAndWriteToFile()
}