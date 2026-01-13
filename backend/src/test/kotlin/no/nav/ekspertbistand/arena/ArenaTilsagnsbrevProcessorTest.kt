package no.nav.ekspertbistand.arena

import io.ktor.http.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.handlers.insertSaksnummer
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.SkjemaStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArenaTilsagnsbrevProcessorTest {

    @Test
    fun `eksempelmelding for tiltak som ikke er EKSPERTBIST skal ikke behandles`() = testApplicationWithDatabase { db ->
        val saksnummer = Saksnummer("2019319383")
        transaction {
            insertSaksnummer(saksnummer, skjema)
        }

        ArenaTilsagnsbrevProcessor(
            db.config.jdbcDatabase
        ).processRecord(
            ConsumerRecord(
                ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                "key",
                kafkaMelding(
                    1,
                    42,
                    eksempelMelding("IKKE_EKSPERTBIST", 2019, 319383)
                )
            )
        )
        transaction(db.config.jdbcDatabase) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `eksempelmelding for tiltak som er EKSPERTBIST men ikke opprettet av oss skal ikke behandles`() =
        testApplicationWithDatabase { db ->
            val saksnummer = Saksnummer("201942")
            transaction {
                insertSaksnummer(saksnummer, skjema)
            }
            ArenaTilsagnsbrevProcessor(
                db.config.jdbcDatabase
            ).processRecord(
                ConsumerRecord(
                    ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                    "key",
                    kafkaMelding(
                        1,
                        42,
                        eksempelMelding(EKSPERTBISTAND_TILTAKSKODE, 2019, 319383)
                    )
                )
            )
            transaction(db.config.jdbcDatabase) {
                assertEquals(0, QueuedEvents.selectAll().count())
            }
        }

    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST og opprettet av oss skal behandles`() =
        testApplicationWithDatabase { db ->
            val saksnummer = Saksnummer("2019319383")
            transaction {
                insertSaksnummer(saksnummer, skjema)
            }
            ArenaTilsagnsbrevProcessor(
                db.config.jdbcDatabase
            ).processRecord(
                ConsumerRecord(
                    ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                    "key",
                    kafkaMelding(
                        1,
                        42,
                        eksempelMelding(EKSPERTBISTAND_TILTAKSKODE, 2019, 319383)
                    )
                )
            )
            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count())
                val eventData = queuedEvents.first().eventData as EventData.TilskuddsbrevMottatt
                assertNotNull(eventData)
                assertEquals(2019, eventData.tilsagnData.tilsagnNummer.aar)
                assertEquals(319383, eventData.tilsagnData.tilsagnNummer.loepenrSak)

            }
        }

    @Test
    fun `kafka consumer feiler ikke`() {
        // TODO: test consumer vha dummy kafka broker
        
        assertDoesNotThrow {
            ArenaTilsagnsbrevProcessor.consumer
        }
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
): String = """
{
	"table": "ARENA_TAG.TAG_TILSAGNSBREV",
	"op_type": "I",
	"op_ts": "2019-10-09 15:20:11.106331",
	"current_ts": "2019-10-09T15:32:38.324000",
	"pos": "00000000000000002324",
	"after": {
		"TILSAGNSBREV_ID": $tilsagnsbrevId,
		"TILSAGN_ID": $tilsagnId,
		"TILSAGN_DATA": ${
    Json.parseToJsonElement(tilsagnData).let { Json.encodeToString(JsonObject.serializer(), it.jsonObject) }
        .escapeIfNeeded()
},
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
fun eksempelMelding(tiltakKode: String, aar: Int, loepenrSak: Int) = """
    {
        "tilsagnNummer": {
            "aar": $aar,
            "loepenrSak": $loepenrSak,
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

private val skjema = DTO.Skjema(
    id = UUID.randomUUID().toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = "1337",
        virksomhetsnavn = "foo bar AS",
        kontaktperson = DTO.Kontaktperson(
            navn = "Donald Duck",
            epost = "Donald@duck.co",
            telefonnummer = "12345678"
        )
    ),
    ansatt = DTO.Ansatt(
        fnr = "12345678910",
        navn = "Ole Olsen"
    ),
    ekspert = DTO.Ekspert(
        navn = "Egon Olsen",
        virksomhet = "Olsenbanden AS",
        kompetanse = "Bankran",
    ),
    behovForBistand = DTO.BehovForBistand(
        behov = "Tilrettelegging",
        begrunnelse = "Tilrettelegging på arbeidsplassen",
        estimertKostnad = "4200",
        timer = "16",
        tilrettelegging = "Spesialtilpasset kontor",
        startdato = LocalDate.parse("2024-11-15")
    ),
    nav = DTO.Nav(
        kontaktperson = "Navn Navnesen"
    ),
    opprettetAv = "Noen Noensen",
    status = SkjemaStatus.innsendt,
)