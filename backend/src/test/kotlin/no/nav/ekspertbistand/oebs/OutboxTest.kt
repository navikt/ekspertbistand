@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.oebs.integration.AnnullerBestilling
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.OkonomiPart
import no.nav.ekspertbistand.oebs.integration.OkonomiFagsystem
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiProducer
import no.nav.ekspertbistand.oebs.model.OebsOutbox
import no.nav.ekspertbistand.oebs.model.OebsOutboxPoller
import no.nav.ekspertbistand.oebs.model.OebsSendtMelding
import no.nav.ekspertbistand.oebs.model.OutboxStatus
import no.nav.ekspertbistand.oebs.model.bestillingsnummer
import no.nav.ekspertbistand.oebs.model.leggIOutbox
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🔴 Rød sone — tester for den økonomikritiske outbox-polleren.
 *
 * Krever den lokale test-Postgres-en (`backend/docker-compose.yml`, port 5532); kjøres i CI.
 * Testene kjører den ekte [OebsOutboxPoller] mot en [MockProducer], så transaksjonsgrensene
 * (publiser -> logg + marker PUBLISHED i én transaksjon) faktisk utøves.
 */
class OutboxTest {

    private fun melding(bestillingsnummer: String): OebsBestillingMelding {
        val part = OkonomiPart.Fagsystem(OkonomiFagsystem.EKSPERTBISTAND)
        val naa = Clock.System.now()
        return OebsBestillingMelding.Annullering(
            AnnullerBestilling(
                bestillingsnummer = bestillingsnummer,
                behandletAv = part,
                behandletTidspunkt = naa,
                besluttetAv = part,
                besluttetTidspunkt = naa,
            )
        )
    }

    private fun status(database: Database, bestillingsnummer: String): OutboxStatus =
        transaction(database) {
            OebsOutbox
                .selectAll()
                .where { OebsOutbox.bestillingsnummer eq bestillingsnummer }
                .single()[OebsOutbox.status]
        }

    @Test
    fun `poller publiserer PENDING-rad og markerer PUBLISHED i samme transaksjon`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val bestillingsnummer = "E-2026/10000-1"
            val melding = melding(bestillingsnummer)

            transaction(database) { leggIOutbox(melding) }

            val mock = MockProducer(true, StringSerializer(), StringSerializer())
            val poller = OebsOutboxPoller(
                database = database,
                producer = TiltaksokonomiProducer(mock),
                pollInterval = 20.milliseconds,
                feilBackoff = 20.milliseconds,
            )

            runBlocking {
                val job = launch { poller.startProcessing() }
                withTimeout(5.seconds) {
                    while (status(database, bestillingsnummer) != OutboxStatus.PUBLISHED) {
                        delay(20.milliseconds)
                    }
                }
                job.cancelAndJoin()
            }

            // Publisert nøyaktig én gang, med bestillingsnummeret som Kafka-key.
            assertEquals(1, mock.history().size)
            assertEquals(bestillingsnummer, mock.history().single().key())

            transaction(database) {
                val rad = OebsOutbox
                    .selectAll()
                    .where { OebsOutbox.bestillingsnummer eq bestillingsnummer }
                    .single()
                assertEquals(OutboxStatus.PUBLISHED, rad[OebsOutbox.status])
                assertEquals(1, rad[OebsOutbox.attempts])

                // Revisjonssporet er skrevet i samme transaksjon som PUBLISHED-markeringen.
                val sendt = OebsSendtMelding
                    .selectAll()
                    .where { OebsSendtMelding.bestillingsnummer eq bestillingsnummer }
                    .single()
                assertEquals(melding.bestillingsnummer, sendt[OebsSendtMelding.bestillingsnummer])
            }
        }

    @Test
    fun `publiseringsfeil lar raden bli liggende som PENDING for retry`() =
        testApplicationWithDatabase { db ->
            val database = db.config.jdbcDatabase
            val bestillingsnummer = "E-2026/20000-1"

            transaction(database) { leggIOutbox(melding(bestillingsnummer)) }

            val mock = MockProducer(true, StringSerializer(), StringSerializer())
            // Simuler at Kafka er nede: send() kaster synkront.
            mock.sendException = RuntimeException("simulert Kafka-feil")

            val poller = OebsOutboxPoller(
                database = database,
                producer = TiltaksokonomiProducer(mock),
                pollInterval = 20.milliseconds,
                feilBackoff = 20.milliseconds,
            )

            runBlocking {
                val job = launch { poller.startProcessing() }
                // La polleren gjøre flere forsøk før vi sjekker.
                delay(300.milliseconds)
                job.cancelAndJoin()
            }

            // Ingenting publisert, ingen revisjonsrad, og raden er urørt (rullet tilbake) -> klar for retry.
            assertTrue(mock.history().isEmpty())
            assertEquals(OutboxStatus.PENDING, status(database, bestillingsnummer))

            transaction(database) {
                val rad = OebsOutbox
                    .selectAll()
                    .where { OebsOutbox.bestillingsnummer eq bestillingsnummer }
                    .single()
                // attempts rulles tilbake sammen med resten av transaksjonen -> revisjonssporet skal
                // aldri divergere fra det som faktisk ble publisert.
                assertEquals(0, rad[OebsOutbox.attempts])

                val sendt = OebsSendtMelding
                    .selectAll()
                    .where { OebsSendtMelding.bestillingsnummer eq bestillingsnummer }
                    .count()
                assertEquals(0, sendt)
            }
        }
}
