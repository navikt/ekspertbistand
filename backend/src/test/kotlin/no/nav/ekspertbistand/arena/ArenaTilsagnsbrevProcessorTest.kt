package no.nav.ekspertbistand.arena

import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import kotlin.test.DefaultAsserter.fail
import kotlin.test.assertEquals

class ArenaTilsagnsbrevProcessorTest {

    @Test
    fun `eksempelmelding for tiltak som ikke er EKSPERTBIST skal ikke behandles`() = runTest {
        ArenaTilsagnsbrevProcessor(
            skalBehandles = { true },
            opprettSøknadGodkjentHendelse = { fail("uforventet callback") }
        ).processRecord(
            ConsumerRecord(
                ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                "key",
                kafkaMelding(
                    1,
                    42,
                    eksempelMelding("IKKE_EKSPERTBIST")
                )
            )
        )
    }

    @Test
    fun `eksempelmelding for tiltak som er EKSPERTBIST men ikke opprettet av oss skal ikke behandles`() = runTest {
        ArenaTilsagnsbrevProcessor(
            skalBehandles = { false },
            opprettSøknadGodkjentHendelse = { fail("uforventet callback") }
        ).processRecord(
            ConsumerRecord(
                ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                "key",
                kafkaMelding(
                    1,
                    42,
                    eksempelMelding(EKSPERTBISTAND_TILTAKSKODE)
                )
            )
        )
    }

    @Test
    fun `eksempelmelding for tiltak som er EKSPERTBIST og opprettet av oss skal behandles`() = runTest {
        val saksnummmer = "2019319383" // fra eksempelmelding
        val skalOpprettes = mutableListOf<TilsagnsbrevKafkaMelding>()

        ArenaTilsagnsbrevProcessor(
            skalBehandles = { "${it.tilsagnNummer.aar}${it.tilsagnNummer.loepenrSak}" == saksnummmer },
            opprettSøknadGodkjentHendelse = { skalOpprettes.add(it) }
        ).processRecord(
            ConsumerRecord(
                ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                "key",
                kafkaMelding(
                    1,
                    42,
                    eksempelMelding(EKSPERTBISTAND_TILTAKSKODE)
                )
            )
        )

        assertEquals(skalOpprettes.size, 1, "forventet 1 opprettet hendelse, fikk ${skalOpprettes.size}")
        assertEquals(skalOpprettes.first().tilsagnData.tilsagnNummer.aar, 2019)
        assertEquals(skalOpprettes.first().tilsagnData.tilsagnNummer.loepenrSak, 319383)
    }
}

/**
 * kafka meldinger kommer wrappet. se: https://confluence.adeo.no/spaces/ARENA/pages/340837316/Arena+-+Funksjonalitet+-+Tilsagnsbrev+-+TAG#ArenaFunksjonalitetTilsagnsbrevTAG-KafkaTopic
 *
 * hjelpefunksjon som lager en wrappet melding hvor TILSAGN_DATA blir escapet slik det kommer på kafka.
 */
private fun kafkaMelding(
    tilsagnsbrevId: Int,
    tilsagnId: Int,
    tilsagnData: String,
) : String = """
{
	"table": "ARENA_TAG.TAG_TILSAGNSBREV",
	"op_type": "I",
	"op_ts": "2019-10-09 15:20:11.106331",
	"current_ts": "2019-10-09T15:32:38.324000",
	"pos": "00000000000000002324",
	"after": {
		"TILSAGNSBREV_ID": $tilsagnsbrevId,
		"TILSAGN_ID": $tilsagnId,
		"TILSAGN_DATA": ${Json.parseToJsonElement(tilsagnData).let { Json.encodeToString(JsonObject.serializer(), it.jsonObject) }.escapeIfNeeded()},
		"REG_DATO": "2019-10-08 11:16:30",
		"REG_USER": "SIAMO",
		"MOD_DATO": "2019-10-08 11:16:30",
		"MOD_USER": "SIAMO"
	}
}
"""

/**
 * se eksempel: https://confluence.adeo.no/spaces/ARENA/pages/366966551/Arena+-+Tjeneste+Kafka+-+Tilsagnsbrev
 */
// language=JSON
fun eksempelMelding(tiltakKode: String) = """
    {
        "tilsagnNummer": {
            "aar": 2019,
            "loepenrSak": 319383,
            "loepenrTilsagn": 1
        },
        "tilsagnDato": "2019-09-25",
        "periode": {
            "fraDato": "2019-09-14",
            "tilDato": "2019-12-31"
        },
        "tiltakKode": "$tiltakKode",
        "tiltakNavn": "Enkeltplass Fag- og yrkesopplæring VGS og høyere yrkesfaglig utdanning",
        "administrasjonKode": "IND",
        "refusjonfristDato": "2020-02-29",
        "tiltakArrangor": {
            "arbgiverNavn": "TREIDER KOMPETANSE AS",
            "landKode": "NO",
            "postAdresse": "Nedre Vollgate 8",
            "postNummer": "0158",
            "postSted": "OSLO",
            "orgNummerMorselskap": 920053106,
            "orgNummer": 920130283,
            "kontoNummer": "32600596984",
            "maalform": "NO"
        },
        "totaltTilskuddbelop": 59300,
        "valutaKode": "NOK",
        "tilskuddListe": [
            {
                "tilskuddType": "Drift",
                "tilskuddBelop": 48000,
                "visTilskuddProsent": false,
                "tilskuddProsent": null
            },
            {
                "tilskuddType": "Drift",
                "tilskuddBelop": 1800,
                "visTilskuddProsent": false,
                "tilskuddProsent": null
            },
            {
                "tilskuddType": "Drift",
                "tilskuddBelop": 9500,
                "visTilskuddProsent": false,
                "tilskuddProsent": null
            }
        ],
        "deltaker": {
            "fodselsnr": "0101011990",
            "fornavn": "OLA",
            "etternavn": "NORDMANN",
            "landKode": "NO",
            "postAdresse": "Veien 1",
            "postNummer": "2013",
            "postSted": "SKJETTEN"
        },
        "antallDeltakere": null,
        "antallTimeverk": null,
        "navEnhet": {
            "navKontor": "0231",
            "navKontorNavn": "NAV Skedsmo",
            "postAdresse": "Postboks 294",
            "postNummer": "2001",
            "postSted": "LILLESTRØM",
            "telefon": "55553333",
            "faks": null
        },
        "beslutter": {
            "fornavn": "Bjarne",
            "etternavn": "Beslutter"
        },
        "saksbehandler": {
            "fornavn": "Stine",
            "etternavn": "Saksbehandler"
        },
        "kommentar": null
    }
""".trimIndent()