@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.oebs.integration.BestillingStatus
import no.nav.ekspertbistand.oebs.integration.BestillingStatusType
import no.nav.ekspertbistand.oebs.integration.FakturaStatus
import no.nav.ekspertbistand.oebs.integration.FakturaStatusType
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer
import no.nav.ekspertbistand.oebs.model.OebsBestillingStatus
import no.nav.ekspertbistand.oebs.model.OebsMottattStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🔴 Rød sone — tester for statustolkningen ([TiltaksokonomiConsumer]). Kjører hele
 * `processRecord`-flyten (deserialiser → filtrer på vår kilde → tolk → upsert + revisjonsspor) mot
 * den lokale test-Postgres-en (`backend/docker-compose.yml`, port 5532).
 */
class TiltaksokonomiConsumerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private var offset = 0L

    private fun record(topic: String, key: String, value: String): ConsumerRecord<String?, String?> =
        ConsumerRecord(topic, 0, offset++, key, value)

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
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.FEILET))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }

        val rad = assertNotNull(statusRad(database, nr))
        assertEquals("FEILET", rad[OebsBestillingStatus.status])
        assertTrue(rad[OebsBestillingStatus.trengerManuellOppfolging])
        assertNotNull(rad[OebsBestillingStatus.feilmelding])
    }

    @Test
    fun `bestilling AKTIV krever ikke oppfolging`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val consumer = TiltaksokonomiConsumer(database)
        val nr = "E-2026/11000-1"
        val value = json.encodeToString(BestillingStatus(nr, BestillingStatusType.AKTIV))

        runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, nr, value)) }

        val rad = assertNotNull(statusRad(database, nr))
        assertEquals("AKTIV", rad[OebsBestillingStatus.status])
        assertFalse(rad[OebsBestillingStatus.trengerManuellOppfolging])
        assertNull(rad[OebsBestillingStatus.feilmelding])
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
                        json.encodeToString(BestillingStatus(bestillingsnummer, BestillingStatusType.FEILET)),
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
    fun `melding fra annen kilde ignoreres uten a lagre status eller revisjonsspor`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val consumer = TiltaksokonomiConsumer(database)
            val fremmed = "A-2026/99999-1"
            val value = json.encodeToString(BestillingStatus(fremmed, BestillingStatusType.FEILET))

            runBlocking { consumer.processRecord(record(TiltaksokonomiConsumer.BESTILLING_STATUS_TOPIC, fremmed, value)) }

            assertNull(statusRad(database, fremmed), "andres meldinger skal ikke gi statusrad")
            val revisjon = transaction(database) {
                OebsMottattStatus
                    .selectAll()
                    .where { OebsMottattStatus.bestillingsnummer eq fremmed }
                    .count()
            }
            assertEquals(0, revisjon, "andres meldinger skal ikke logges i revisjonssporet")
        }
}
