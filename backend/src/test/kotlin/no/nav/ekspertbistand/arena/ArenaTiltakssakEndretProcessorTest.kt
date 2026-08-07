package no.nav.ekspertbistand.arena

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.arena.ArenaTiltaksgjennomforingEndretProcessorTest.Companion.soknad
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArenaTiltakssakEndretProcessorTest {

    companion object {
        const val AAR = 2026
        const val LOPENRSAK = 202
        val SAKSNUMMER = asSaksnummer(aar = AAR, loepenrSak = LOPENRSAK)

        /**
         * Konvolutt basert på eksempelmeldingen i
         * specifications/arena_tiltakssak_endret_under_behandling.md
         */
        fun kafkaMelding(
            sakId: Int,
            opType: String = "U",
            sakskode: String = "TILT",
            brukeridAnsvarlig: String? = "K123456",
            aetatenhetAnsvarlig: String? = "1899",
            beforeBrukeridAnsvarlig: String? = "1899",
            sakstatuskode: String = "AKTIV",
            aar: Int = AAR,
            lopenrsak: Int = LOPENRSAK,
        ): String =
            //language=JSON
            """
            {
              "table": "SIAMO.SAK",
              "op_type": "$opType",
              "op_ts": "2026-01-07 10:07:01.000000",
              "current_ts": "2026-01-07 10:07:10.692004",
              "pos": "00000000790177154127",
              "before": ${tiltakssak(sakId, sakskode, beforeBrukeridAnsvarlig, aetatenhetAnsvarlig, sakstatuskode, aar, lopenrsak)},
              "after": ${tiltakssak(sakId, sakskode, brukeridAnsvarlig, aetatenhetAnsvarlig, sakstatuskode, aar, lopenrsak)}
            }
            """

        fun kafkaDeleteMelding(sakId: Int): String =
            //language=JSON
            """
            {
              "table": "SIAMO.SAK",
              "op_type": "D",
              "before": ${tiltakssak(sakId, "TILT", "K123456", "1899", "AKTIV", AAR, LOPENRSAK)}
            }
            """

        private fun tiltakssak(
            sakId: Int,
            sakskode: String,
            brukeridAnsvarlig: String?,
            aetatenhetAnsvarlig: String?,
            sakstatuskode: String,
            aar: Int,
            lopenrsak: Int,
        ): String =
            //language=JSON
            """
            {
                "SAK_ID": $sakId,
                "SAKSKODE": "$sakskode",
                "REG_DATO": "07.01.2026 10.07.01",
                "REG_USER": "ARENA_AP",
                "MOD_DATO": "07.01.2026 10.07.01",
                "MOD_USER": "ARENA_AP",
                "TABELLNAVNALIAS": "SAK",
                "OBJEKT_ID": $sakId,
                "AAR": $aar,
                "LOPENRSAK": $lopenrsak,
                "SAKSTATUSKODE": "$sakstatuskode",
                "BRUKERID_ANSVARLIG": ${brukeridAnsvarlig?.let { "\"$it\"" } ?: "null"},
                "AETATENHET_ANSVARLIG": ${aetatenhetAnsvarlig?.let { "\"$it\"" } ?: "null"},
                "STATUS_ENDRET": "07.01.2026 10.07.01",
                "ARKIVNOKKEL": null,
                "PARTISJON": null,
                "ER_UTLAND": "N"
            }
            """

        fun createConsumerRecord(melding: String?, timestamp: Instant = Instant.parse("2026-01-07T10:07:01.00Z")) =
            ConsumerRecord(
                ArenaTiltakssakEndretProcessor.TOPIC,
                0,
                0,
                timestamp.toEpochMilli(),
                TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE,
                ConsumerRecord.NULL_SIZE,
                "key",
                melding,
                RecordHeaders(),
                Optional.empty()
            )
    }

    private fun queuedEvents(db: org.jetbrains.exposed.v1.jdbc.Database) = transaction(db) {
        QueuedEvents.selectAll().map { it.tilQueuedEvent() }
    }

    @Test
    fun `1 - brukerid lik aetatenhet gir ingen event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 1, brukeridAnsvarlig = "1899", aetatenhetAnsvarlig = "1899"))
        )

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `2 - divergerende felter og kjent saksnummer publiserer event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 13769058))
        )

        val events = queuedEvents(db.config.jdbcDatabase)
        assertEquals(1, events.size)
        val eventData = assertIs<EventData.SaksbehandlingStartetIArena>(events.first().eventData)
        assertEquals(13769058, eventData.tiltakssakEndret.sakId)
        assertEquals(SAKSNUMMER, eventData.tiltakssakEndret.saksnummer)
        assertEquals("K123456", eventData.tiltakssakEndret.brukeridAnsvarlig)
        assertEquals(soknad.id, eventData.soknad.id)
    }

    @Test
    fun `3 - ukjent saksnummer gir ingen event og ingen exception`() = testApplicationWithDatabase { db ->
        assertDoesNotThrow {
            ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
                createConsumerRecord(kafkaMelding(sakId = 1, aar = 1999, lopenrsak = 1))
            )
        }

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `4 - op_type I gir ingen event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 1, opType = "I"))
        )

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `5 - delete-melding uten after gir ingen event og ingen exception`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        assertDoesNotThrow {
            ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
                createConsumerRecord(kafkaDeleteMelding(sakId = 1))
            )
        }

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `6 - annen SAKSKODE enn TILT gir ingen event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 1, sakskode = "ARBS"))
        )

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `7 - samme SAK_ID to ganger gir kun ett event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        val processor = ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH)
        val record = createConsumerRecord(kafkaMelding(sakId = 42))

        processor.processRecord(record)
        processor.processRecord(record)

        assertEquals(1, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `8 - tombstone gir ingen event og ingen exception`() = testApplicationWithDatabase { db ->
        assertDoesNotThrow {
            ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
                // NB: må bruke createConsumerRecord, som setter et eksplisitt timestamp. Kafka sin
                // 5-args-konstruktør setter NO_TIMESTAMP (-1), som er før Instant.EPOCH — da ville
                // testen returnere på startProcessingAt-vakten uten å nå tombstone-grenen.
                createConsumerRecord(null)
            )
        }

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `9 - melding foer startProcessingAt gir ingen event`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(
            db.config.jdbcDatabase,
            Instant.parse("2026-02-26T10:00:00.00Z")
        ).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 1), Instant.parse("2026-02-25T10:00:00.00Z"))
        )

        assertEquals(0, queuedEvents(db.config.jdbcDatabase).size)
    }

    @Test
    fun `10 - konvolutt med UPPERCASE kolonnenavn parses korrekt`() {
        val melding = json.decodeFromString<TiltakssakEndretKafkaMelding>(kafkaMelding(sakId = 13769058))

        assertEquals("U", melding.opType)
        assertEquals("SIAMO.SAK", melding.table)
        assertTrue(melding.erOppdatering)

        val after = melding.after!!
        assertEquals(13769058, after.sakId)
        assertEquals("TILT", after.sakskode)
        assertEquals(AAR, after.aar)
        assertEquals(LOPENRSAK, after.lopenrsak)
        assertEquals(TiltakssakEndret.Sakstatuskode.AKTIV, after.sakstatuskode)
        assertEquals("K123456", after.brukeridAnsvarlig)
        assertEquals("1899", after.aetatenhetAnsvarlig)
        assertEquals("2026202", after.saksnummer)
        assertTrue(after.erTiltakssak)
        assertTrue(after.erTattAvSaksbehandler)
    }

    @Test
    fun `11 - ukjent SAKSTATUSKODE feller ikke prosesseringen`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        assertDoesNotThrow {
            ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
                createConsumerRecord(kafkaMelding(sakId = 1, sakstatuskode = "UKJENT"))
            )
        }

        val events = queuedEvents(db.config.jdbcDatabase)
        assertEquals(1, events.size)
        val eventData = assertIs<EventData.SaksbehandlingStartetIArena>(events.first().eventData)
        assertNull(eventData.tiltakssakEndret.sakstatuskode)
    }

    @Test
    fun `12 - ukjente felter i after ignoreres`() {
        val after = json.decodeFromString<TiltakssakEndretKafkaMelding>(kafkaMelding(sakId = 1)).after!!

        // PARTISJON og ARKIVNOKKEL finnes i meldingen, men ikke i DTO-en
        assertEquals(1, after.sakId)
    }

    @Test
    fun `13 - before er tilgjengelig ved op_type U`() {
        val melding = json.decodeFromString<TiltakssakEndretKafkaMelding>(kafkaMelding(sakId = 1))

        assertEquals("1899", melding.before?.brukeridAnsvarlig)
        assertEquals("K123456", melding.after?.brukeridAnsvarlig)
    }

    @Test
    fun `14 - ugyldig JSON kaster exception`() = testApplicationWithDatabase { db ->
        assertThrows<Exception> {
            ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
                createConsumerRecord("{ dette er ikke gyldig json")
            )
        }
    }

    @Test
    fun `14b - brukeridLiknerSaksbehandlerIdent gjenkjenner varierende identformater`() {
        fun medBrukerid(brukerid: String?) = TiltakssakEndret(
            sakId = 1,
            aar = AAR,
            lopenrsak = LOPENRSAK,
            brukeridAnsvarlig = brukerid,
        )

        assertTrue(medBrukerid("KG0219").brukeridLiknerSaksbehandlerIdent)
        assertTrue(medBrukerid("KGB0219").brukeridLiknerSaksbehandlerIdent)
        assertFalse(medBrukerid("1899").brukeridLiknerSaksbehandlerIdent)
        assertFalse(medBrukerid(null).brukeridLiknerSaksbehandlerIdent)
    }

    @Test
    fun `14b - ukjent identformat hindrer ikke at saken markeres`() = testApplicationWithDatabase { db ->
        transaction { insertArenaSak(SAKSNUMMER, 1, soknad) }

        ArenaTiltakssakEndretProcessor(db.config.jdbcDatabase, Instant.EPOCH).processRecord(
            createConsumerRecord(kafkaMelding(sakId = 1, brukeridAnsvarlig = "XYZ"))
        )

        val events = queuedEvents(db.config.jdbcDatabase)
        assertEquals(1, events.size)
        val eventData = assertIs<EventData.SaksbehandlingStartetIArena>(events.first().eventData)
        assertEquals("XYZ", eventData.tiltakssakEndret.brukeridAnsvarlig)
        assertFalse(eventData.tiltakssakEndret.brukeridLiknerSaksbehandlerIdent)
    }

    @Test
    fun `kafka consumer feiler ikke`() {
        assertDoesNotThrow {
            ArenaTiltakssakEndretProcessor.consumer
        }
    }

    @Test
    fun `kafka config bruker forventet groupId og earliest`() {
        assertEquals("fager.ekspertbistand.tiltakssakendret", ArenaTiltakssakEndretProcessor.kafkaConfig.groupId)
        assertEquals(
            no.nav.ekspertbistand.infrastruktur.AutoOffsetReset.EARLIEST,
            ArenaTiltakssakEndretProcessor.kafkaConfig.autoOffsetReset
        )
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}
