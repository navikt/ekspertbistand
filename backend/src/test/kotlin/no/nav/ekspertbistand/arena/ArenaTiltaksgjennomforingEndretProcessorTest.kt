package no.nav.ekspertbistand.arena

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret.TiltakStatusKode.GJENNOMFOR
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.soknad.DTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArenaTiltaksgjennomforingEndretProcessorTest {

    @Test
    fun `eksempelmelding for tiltak som ikke er EKSPEBIST skal ikke behandles`() = testApplicationWithDatabase { db ->
        ArenaTiltaksgjennomforingEndretProcessor(
            db.config.jdbcDatabase,
            Instant.EPOCH
        ).processRecord(
            createConsumerRecord(
                kafkaMelding(123, "IKKE_EKSPEBIST", AVLYST)
            )
        )
        transaction(db.config.jdbcDatabase) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST men ikke opprettet av oss skal ikke behandles`() =
        testApplicationWithDatabase { db ->
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            ).processRecord(
                createConsumerRecord(
                    kafkaMelding(43, "EKSPEBIST", AVLYST)
                )
            )
            transaction(db.config.jdbcDatabase) {
                assertEquals(0, QueuedEvents.selectAll().count())
            }
        }

    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST, status != AVLYST og opprettet av oss skal ikke behandles`() =
        testApplicationWithDatabase { db ->
            val tiltaksgjennomfoeringId = 123
            transaction {
                insertArenaSak("2019319383", tiltaksgjennomfoeringId, soknad)
            }
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            ).processRecord(
                createConsumerRecord(
                    kafkaMelding(tiltaksgjennomfoeringId, "EKSPEBIST", GJENNOMFOR)
                )
            )
            transaction(db.config.jdbcDatabase) {
                assertEquals(0, QueuedEvents.selectAll().count())
            }
        }

    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST, status == AVLYST og opprettet av oss skal behandles`() =
        testApplicationWithDatabase { db ->
            val tiltaksgjennomfoeringId = 1337
            transaction {
                insertArenaSak("2019319383", tiltaksgjennomfoeringId, soknad)
            }
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            ).processRecord(
                createConsumerRecord(
                    kafkaMelding(tiltaksgjennomfoeringId, "EKSPEBIST", AVLYST)
                )
            )
            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count())
                val eventData = queuedEvents.first().eventData as EventData.SoknadAvlystIArena
                assertNotNull(eventData)
                assertEquals(tiltaksgjennomfoeringId, eventData.tiltaksgjennomforingEndret.tiltaksgjennomfoeringId)

            }
        }


    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST, status == AVLYST og opprettet av oss skal ikke behandles før startProcessingAt`() =
        testApplicationWithDatabase { db ->
            val tiltaksgjennomfoeringId = 1337
            transaction {
                insertArenaSak("2019319383", tiltaksgjennomfoeringId, soknad)
            }
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.parse("2026-02-26T10:00:00.00Z")
            ).processRecord(
                createConsumerRecord(
                    kafkaMelding(tiltaksgjennomfoeringId, "EKSPEBIST", AVLYST),
                    Instant.parse("2026-02-25T10:00:00.00Z"),
                )
            )
            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(0, queuedEvents.count())
            }
        }

    @Test
    fun `kafka consumer feiler ikke`() {
        assertDoesNotThrow {
            ArenaTiltaksgjennomforingEndretProcessor.consumer
        }
    }


    @Test
    fun `tombstone skippes`() = testApplicationWithDatabase { db ->
        assertDoesNotThrow {
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase,
                Instant.EPOCH
            ).processRecord(
                ConsumerRecord(
                    ArenaTilsagnsbrevProcessor.TOPIC, 0, 0,
                    "key",
                    null
                )
            )
        }
    }
}

private fun createConsumerRecord(melding: String?, timestamp: Instant = Instant.parse("2026-01-01T00:00:00.00Z")) =
    ConsumerRecord(
        ArenaTilsagnsbrevProcessor.TOPIC,
        0,
        0,
        timestamp.toEpochMilli(),
        TimestampType.NO_TIMESTAMP_TYPE,
        ConsumerRecord.NULL_SIZE,
        ConsumerRecord.NULL_SIZE,
        "key",
        melding,
        RecordHeaders(),
        Optional.empty<Int?>()
    )

/**
 * hjelpefunksjon som lager kafkamelding med gitt statusendring
 *
 * basert på eksempelmelding fra Arena-teamet på slack
 */
private fun kafkaMelding(
    tiltaksgjennomfoeringId: Int,
    tiltakskode: String,
    tiltakstatuskode: TiltaksgjennomforingEndret.TiltakStatusKode,
): String =
    //language=JSON
    """
{
  "table": "SIAMO.TILTAKGJENNOMFORING",
  "op_type": "U",
  "op_ts": "2026-01-01 00:00:06.000000",
  "current_ts": "2026-01-01 00:00:10.692004",
  "pos": "00000000790177154127",
  "before": {
    "TILTAKGJENNOMFORING_ID": $tiltaksgjennomfoeringId,
    "SAK_ID": 13706920,
    "TILTAKSKODE": "$tiltakskode",
    "ANTALL_DELTAKERE": 1,
    "ANTALL_VARIGHET": null,
    "DATO_FRA": "2025-01-01 00:00:00",
    "DATO_TIL": "2025-12-31 00:00:00",
    "FAGPLANKODE": null,
    "MAALEENHET_VARIGHET": null,
    "TEKST_FAGBESKRIVELSE": null,
    "TEKST_KURSSTED": null,
    "TEKST_MAALGRUPPE": null,
    "STATUS_TREVERDIKODE_INNSOKNING": "J",
    "REG_DATO": "2025-03-18 10:12:40",
    "REG_USER": "JSZ0219",
    "MOD_DATO": "2025-03-18 10:42:44",
    "MOD_USER": "JSZ0219",
    "LOKALTNAVN": "Test -  Ekspertbistand",
    "TILTAKSTATUSKODE": "GJENNOMFOR",
    "PROSENT_DELTID": 100,
    "KOMMENTAR": null,
    "ARBGIV_ID_ARRANGOR": 3842,
    "PROFILELEMENT_ID_GEOGRAFI": null,
    "KLOKKETID_FREMMOTE": null,
    "DATO_FREMMOTE": null,
    "BEGRUNNELSE_STATUS": null,
    "AVTALE_ID": null,
    "AKTIVITET_ID": 134120751,
    "DATO_INNSOKNINGSTART": null,
    "GML_FRA_DATO": null,
    "GML_TIL_DATO": null,
    "AETAT_FREMMOTEREG": "0219",
    "AETAT_KONTERINGSSTED": "0420",
    "OPPLAERINGNIVAAKODE": null,
    "TILTAKGJENNOMFORING_ID_REL": null,
    "PROFILELEMENT_ID_OPPL_TILTAK": null,
    "DATO_OPPFOLGING_OK": null,
    "PARTISJON": null,
    "MAALFORM_KRAVBREV": "NO",
    "EKSTERN_ID": null
  },
  "after": {
    "TILTAKGJENNOMFORING_ID": $tiltaksgjennomfoeringId,
    "SAK_ID": 13706920,
    "TILTAKSKODE": "$tiltakskode",
    "ANTALL_DELTAKERE": 1,
    "ANTALL_VARIGHET": null,
    "DATO_FRA": "2025-01-01 00:00:00",
    "DATO_TIL": "2025-12-31 00:00:00",
    "FAGPLANKODE": null,
    "MAALEENHET_VARIGHET": null,
    "TEKST_FAGBESKRIVELSE": null,
    "TEKST_KURSSTED": null,
    "TEKST_MAALGRUPPE": null,
    "STATUS_TREVERDIKODE_INNSOKNING": "J",
    "REG_DATO": "2025-03-18 10:12:40",
    "REG_USER": "JSZ0219",
    "MOD_DATO": "2026-01-01 00:00:00",
    "MOD_USER": "GRENSESN",
    "LOKALTNAVN": "Test -  Ekspertbistand",
    "TILTAKSTATUSKODE": "$tiltakstatuskode",
    "PROSENT_DELTID": 100,
    "KOMMENTAR": null,
    "ARBGIV_ID_ARRANGOR": 3842,
    "PROFILELEMENT_ID_GEOGRAFI": null,
    "KLOKKETID_FREMMOTE": null,
    "DATO_FREMMOTE": null,
    "BEGRUNNELSE_STATUS": null,
    "AVTALE_ID": null,
    "AKTIVITET_ID": 134120751,
    "DATO_INNSOKNINGSTART": null,
    "GML_FRA_DATO": null,
    "GML_TIL_DATO": null,
    "AETAT_FREMMOTEREG": "0219",
    "AETAT_KONTERINGSSTED": "0420",
    "OPPLAERINGNIVAAKODE": null,
    "TILTAKGJENNOMFORING_ID_REL": null,
    "PROFILELEMENT_ID_OPPL_TILTAK": null,
    "DATO_OPPFOLGING_OK": null,
    "PARTISJON": null,
    "MAALFORM_KRAVBREV": "NO",
    "EKSTERN_ID": null
  }
}
"""

private val soknad = DTO.Soknad(
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
    opprettetAv = "Noen Noensen"
)