package no.nav.ekspertbistand.arena

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST
import no.nav.ekspertbistand.arena.TiltaksgjennomforingEndret.TiltakStatusKode.GJENNOMFOR
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.skjema.DTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ArenaTiltaksgjennomforingEndretProcessorTest {

    @Test
    fun `eksempelmelding for tiltak som ikke er EKSPEBIST skal ikke behandles`() = testApplicationWithDatabase { db ->
        ArenaTiltaksgjennomforingEndretProcessor(
            db.config.jdbcDatabase
        ).processRecord(
            ConsumerRecord(
                ArenaTiltaksgjennomforingEndretProcessor.TOPIC, 0, 0,
                "key",
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
                db.config.jdbcDatabase
            ).processRecord(
                ConsumerRecord(
                    ArenaTiltaksgjennomforingEndretProcessor.TOPIC, 0, 0,
                    "key",
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
            val tiltakgjennomforingId = 123
            transaction {
                insertArenaSak("2019319383", tiltakgjennomforingId, skjema)
            }
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase
            ).processRecord(
                ConsumerRecord(
                    ArenaTiltaksgjennomforingEndretProcessor.TOPIC, 0, 0,
                    "key",
                    kafkaMelding(tiltakgjennomforingId, "EKSPEBIST", GJENNOMFOR)
                )
            )
            transaction(db.config.jdbcDatabase) {
                assertEquals(0, QueuedEvents.selectAll().count())
            }
        }

    @Test
    fun `eksempelmelding for tiltak som er EKSPEBIST, status == AVLYST og opprettet av oss skal behandles`() =
        testApplicationWithDatabase { db ->
            val tiltakgjennomforingId = 1337
            transaction {
                insertArenaSak("2019319383", tiltakgjennomforingId, skjema)
            }
            ArenaTiltaksgjennomforingEndretProcessor(
                db.config.jdbcDatabase
            ).processRecord(
                ConsumerRecord(
                    ArenaTiltaksgjennomforingEndretProcessor.TOPIC, 0, 0,
                    "key",
                    kafkaMelding(tiltakgjennomforingId, "EKSPEBIST", AVLYST)
                )
            )
            transaction(db.config.jdbcDatabase) {
                val queuedEvents = QueuedEvents.selectAll().map { it.tilQueuedEvent() }
                assertEquals(1, queuedEvents.count())
                val eventData = queuedEvents.first().eventData as EventData.SøknadAvlystIArena
                assertNotNull(eventData)
                assertEquals(tiltakgjennomforingId, eventData.tiltaksgjennomforingEndret.tiltakgjennomforingId)

            }
        }

    @Test
    fun `kafka consumer feiler ikke`() {
        assertDoesNotThrow {
            ArenaTiltaksgjennomforingEndretProcessor.consumer
        }
    }
}

/**
 * hjelpefunksjon som lager kafkamelding med gitt statusendring
 *
 * basert på eksempelmelding fra Arena-teamet på slack
 */
private fun kafkaMelding(
    tiltakgjennomforingId: Int,
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
    "TILTAKGJENNOMFORING_ID": $tiltakgjennomforingId,
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
    "TILTAKGJENNOMFORING_ID": $tiltakgjennomforingId,
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
    opprettetAv = "Noen Noensen"
)