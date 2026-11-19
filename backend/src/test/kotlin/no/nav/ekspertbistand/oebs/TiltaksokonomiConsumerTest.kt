@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain
import no.nav.ekspertbistand.event.domain.OebsFakturaStatusDomain
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.oebs.integration.BestillingStatus
import no.nav.ekspertbistand.oebs.integration.BestillingStatusType
import no.nav.ekspertbistand.oebs.integration.FakturaStatus
import no.nav.ekspertbistand.oebs.integration.FakturaStatusType
import no.nav.ekspertbistand.oebs.integration.OebsFeil
import no.nav.ekspertbistand.oebs.integration.OkonomiFagsystem
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer
import no.nav.ekspertbistand.oebs.model.OebsBestillingStatus
import no.nav.ekspertbistand.oebs.model.OebsMottattStatus
import no.nav.ekspertbistand.oebs.model.lagreBestillingReferanse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🔴 Rød sone — tester for statustolkningen ([TiltaksokonomiConsumer]). Kjører hele
 * `processRecord`-flyten (filtrer på fagsystem-header → deserialiser → tolk → upsert + revisjonsspor)
 * mot den lokale test-Postgres-en (`backend/docker-compose.yml`, port 5532).
 */
class TiltaksokonomiConsumerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var offset = 0L

    private fun record(
        topic: String,
        key: String,
        value: String,
        fagsystem: String? = OkonomiFagsystem.EKSPERTBISTAND.name,
    ): ConsumerRecord<String?, String?> {
        val rec = ConsumerRecord<String?, String?>(topic, 0, offset++, key, value)
        if (fagsystem != null) {
            rec.headers().add(TiltaksokonomiConsumer.FAGSYSTEM_HEADER_NAME, fagsystem.toByteArray())
        }
        return rec
    }

    private fun statusRad(database: Database, referanse: String) =
        transaction(database) {
            OebsBestillingStatus
                .selectAll()
                .where { OebsBestillingStatus.bestillingsnummer eq referanse }
                .singleOrNull()
        }

    @Test
    fun `bestilling FEILET flagges for manuell oppfolging`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/10000-1"
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.FEILET, Clock.System.now()))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }

        val rad = assertNotNull(statusRad(database, nr))
        assertEquals("FEILET", rad[OebsBestillingStatus.status])
        assertTrue(rad[OebsBestillingStatus.trengerManuellOppfolging])
    }

    @Test
    fun `bestilling AKTIV krever ikke oppfolging`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/11000-1"
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.AKTIV, Clock.System.now()))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }

        val rad = assertNotNull(statusRad(database, nr))
        assertEquals("AKTIV", rad[OebsBestillingStatus.status])
        assertFalse(rad[OebsBestillingStatus.trengerManuellOppfolging])
    }

    @Test
    fun `faktura FEILET nokles pa fakturanummer og flagges`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val fakturanummer = "E-2026/12000-1-1"
        val value = json.encodeToString(
            FakturaStatus(fakturanummer, FakturaStatusType.FEILET, Clock.System.now())
        )

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.FAKTURA_STATUS_TOPIC, fakturanummer, value)) }

        val rad = assertNotNull(statusRad(database, fakturanummer))
        assertEquals("FEILET", rad[OebsBestillingStatus.status])
        assertTrue(rad[OebsBestillingStatus.trengerManuellOppfolging])
    }

    @Test
    fun `faktura FULLT_BETALT overskriver ikke oppfolgingsflagg pa bestillingen`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val bestillingsnummer = "E-2026/13000-1"
            val fakturanummer = "$bestillingsnummer-1"

            // Bestillingen feiler -> flagges for oppfølging.
            runBlocking {
                consumer.processRecord(
                    record(
                        TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC,
                        bestillingsnummer,
                        json.encodeToString(BestillingStatus(bestillingsnummer, BestillingStatusType.FEILET, Clock.System.now())),
                    )
                )
                // En senere vellykket faktura skal IKKE nullstille bestillingens flagg (egen rad).
                consumer.processRecord(
                    record(
                        TiltaksokonomiConsumer.FAKTURA_STATUS_TOPIC,
                        fakturanummer,
                        json.encodeToString(
                            FakturaStatus(fakturanummer, FakturaStatusType.FULLT_BETALT, Clock.System.now())
                        ),
                    )
                )
            }

            val bestilling = assertNotNull(statusRad(database, bestillingsnummer))
            assertTrue(bestilling[OebsBestillingStatus.trengerManuellOppfolging], "bestillingens flagg skal bestå")

            val faktura = assertNotNull(statusRad(database, fakturanummer))
            assertEquals("FULLT_BETALT", faktura[OebsBestillingStatus.status])
            assertFalse(faktura[OebsBestillingStatus.trengerManuellOppfolging])
        }

    @Test
    fun `melding for annet fagsystem ignoreres uten a lagre status eller revisjonsspor`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val fremmed = "E-2026/99999-1"
            val value = json.encodeToString(BestillingStatus(fremmed, BestillingStatusType.FEILET, Clock.System.now()))

            runBlocking {
                consumer.processRecord(
                    record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, fremmed, value, fagsystem = "TILTAKSADMINISTRASJON")
                )
            }

            assertNull(statusRad(database, fremmed), "andre fagsystemers meldinger skal ikke gi statusrad")
            val revisjon = transaction(database) {
                OebsMottattStatus
                    .selectAll()
                    .where { OebsMottattStatus.bestillingsnummer eq fremmed }
                    .count()
            }
            assertEquals(0, revisjon, "andre fagsystemers meldinger skal ikke logges i revisjonssporet")
        }

    @Test
    fun `melding som bryter kontrakten kaster slik at den proves pa nytt`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val fakturanummer = "E-2026/97000-1-1"
            val gammeltFeltnavn =
                """{"fakturanummer":"$fakturanummer","status":"IKKE_BETALT","fakturaStatusSistOppdatert":"2026-11-16T12:00:00Z"}"""

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    consumer.processRecord(record(TiltaksokonomiConsumer.FAKTURA_STATUS_TOPIC, fakturanummer, gammeltFeltnavn))
                }
            }
            assertNull(statusRad(database, fakturanummer))
        }

    @Test
    fun `melding uten fagsystem-header ignoreres`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val nr = "E-2026/98000-1"
            val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.FEILET, Clock.System.now()))

            runBlocking {
                consumer.processRecord(
                    record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value, fagsystem = null)
                )
            }

            assertNull(statusRad(database, nr), "melding uten header skal ikke gi statusrad")
        }

    private fun lagreReferanse(database: Database, bestillingsnummer: String, sakId: UUID, soknadId: UUID) =
        transaction(database) { lagreBestillingReferanse(bestillingsnummer, sakId, soknadId) }

    private fun publiserteEventer(database: Database): List<EventData> = transaction(database) {
        QueuedEvents.selectAll().orderBy(QueuedEvents.id).map { it.tilQueuedEvent().eventData }
    }

    @Test
    fun `bestilling med referanse publiseres pa soknaden med feilkode og kvittering`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val nr = "E-2026/1000001-1"
            val sakId = UUID.randomUUID()
            val soknadId = UUID.randomUUID()
            lagreReferanse(database, nr, sakId, soknadId)
            val tidspunkt = Instant.parse("2026-11-17T10:30:28Z")
            val value = json.encodeToString(
                BestillingStatus(
                    nr, BestillingStatusType.FEILET, tidspunkt,
                    OebsFeil(kode = "PO_PDOI_CHARGE_FAILED", melding = "Kontering feilet"),
                )
            )
            val rec = record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)

            runBlocking { consumer.processRecord(rec) }

            val event = publiserteEventer(database).single() as EventData.OebsBestillingStatusMottatt
            assertEquals(sakId.toString(), event.sakId)
            assertEquals(soknadId.toString(), event.soknadId)
            assertEquals(nr, event.bestilling.bestillingsnummer)
            assertEquals(OebsBestillingStatusDomain.Status.FEILET, event.bestilling.status)
            assertEquals(tidspunkt, event.bestilling.statusSistOppdatert)
            assertEquals("PO_PDOI_CHARGE_FAILED", event.bestilling.feilKode)
            assertEquals("Kontering feilet", event.bestilling.feilMelding)
            assertEquals(rec.topic(), event.bestilling.kvittering.kafkaTopic)
            assertEquals(rec.partition(), event.bestilling.kvittering.kafkaPartition)
            assertEquals(rec.offset(), event.bestilling.kvittering.kafkaOffset)
        }

    @Test
    fun `samme melding levert to ganger gir en event`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/1000002-1"
        lagreReferanse(database, nr, UUID.randomUUID(), UUID.randomUUID())
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.AKTIV, Clock.System.now()))
        val rec = record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)

        runBlocking {
            consumer.processRecord(rec)
            consumer.processRecord(rec)
        }

        assertEquals(1, publiserteEventer(database).size)
    }

    @Test
    fun `faktura publiseres pa soknaden til bestillingen`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val bestillingsnummer = "E-2026/1000003-1"
        val fakturanummer = "$bestillingsnummer-2"
        val sakId = UUID.randomUUID()
        val soknadId = UUID.randomUUID()
        lagreReferanse(database, bestillingsnummer, sakId, soknadId)
        val value = json.encodeToString(FakturaStatus(fakturanummer, FakturaStatusType.FULLT_BETALT, Clock.System.now()))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.FAKTURA_STATUS_TOPIC, fakturanummer, value)) }

        val event = publiserteEventer(database).single() as EventData.OebsFakturaStatusMottatt
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(soknadId.toString(), event.soknadId)
        assertEquals(fakturanummer, event.faktura.fakturanummer)
        assertEquals(bestillingsnummer, event.faktura.bestillingsnummer)
        assertEquals(OebsFakturaStatusDomain.Status.FULLT_BETALT, event.faktura.status)
    }

    @Test
    fun `testnummer uten referanse lagres uten event`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/900001-1"
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.AKTIV, Clock.System.now()))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }

        assertNotNull(statusRad(database, nr))
        assertTrue(publiserteEventer(database).isEmpty())
    }

    @Test
    fun `ekte nummer uten referanse kaster og lagrer ingenting`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/1000004-1"
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.AKTIV, Clock.System.now()))

        assertFailsWith<IllegalStateException> {
            runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }
        }

        assertNull(statusRad(database, nr))
        val revisjon = transaction(database) {
            OebsMottattStatus.selectAll().where { OebsMottattStatus.bestillingsnummer eq nr }.count()
        }
        assertEquals(0, revisjon, "revisjonssporet rulles tilbake, så meldingen behandles på nytt")
        assertTrue(publiserteEventer(database).isEmpty())
    }
}
