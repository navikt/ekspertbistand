package no.nav.ekspertbistand.mocks

import io.ktor.server.application.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import no.nav.ekspertbistand.oebs.integration.BestillingStatus
import no.nav.ekspertbistand.oebs.integration.BestillingStatusType
import no.nav.ekspertbistand.oebs.integration.FakturaStatus
import no.nav.ekspertbistand.oebs.integration.FakturaStatusType
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.OkonomiFagsystem
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer.Companion.BESTILLING_STATUS_TOPIC
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer.Companion.FAKTURA_STATUS_TOPIC
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer.Companion.FAGSYSTEM_HEADER_NAME
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiProducer
import no.nav.ekspertbistand.oebs.model.OebsMottattStatus
import no.nav.ekspertbistand.oebs.model.OebsOutbox
import no.nav.ekspertbistand.oebs.model.OebsOutboxPoller
import no.nav.ekspertbistand.oebs.model.OutboxStatus
import no.nav.ekspertbistand.oebs.model.meldingstype
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.apache.kafka.common.serialization.StringSerializer
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.Optional
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private val log = LoggerFactory.getLogger("LokalOebs")
private val intervall = 1.seconds

/**
 * Simulerer VALP og OeBS lokalt og i mock-backenden, uten Kafka. Se
 * `specifications/sette_av_penger_oebs_vedtak.md`, 4.
 *
 * Den ekte [OebsOutboxPoller] publiserer til en [MockProducer]. En egen løkke svarer på hver
 * publiserte outbox-rad gjennom den ekte [TiltaksokonomiConsumer.processRecord]. Svaret får
 * partisjon 0 og outbox-id som offset, så unik-indeksen på `oebs_mottatt_status` gjør det
 * idempotent, og løkken svarer bare én gang per rad, også etter omstart.
 */
fun Application.startLokalOebs(database: Database) {
    val producer = TiltaksokonomiProducer(producer = MockProducer(true, StringSerializer(), StringSerializer()))
    val poller = OebsOutboxPoller(database, producer, pollInterval = intervall, feilBackoff = intervall)
    val consumer = TiltaksokonomiConsumer(database)

    launch(Dispatchers.IO) { poller.startProcessing() }
    launch(Dispatchers.IO) {
        while (isActiveAndNotTerminating) {
            try {
                svarPaaPubliserte(database, consumer)
            } catch (e: Exception) {
                log.error("OeBS-mock: klarte ikke å svare på publiserte meldinger", e)
            }
            delay(intervall)
        }
    }
}

private data class SimulertSvar(val meldingstype: String, val topic: String, val referanse: String, val status: String, val json: String)

@OptIn(ExperimentalTime::class)
private suspend fun svarPaaPubliserte(database: Database, consumer: TiltaksokonomiConsumer) {
    val ubesvarte = transaction(database) {
        val besvarte = OebsMottattStatus
            .select(OebsMottattStatus.kafkaTopic, OebsMottattStatus.kafkaOffset)
            .where {
                (OebsMottattStatus.kafkaTopic inList TiltaksokonomiConsumer.TOPICS) and
                    (OebsMottattStatus.kafkaPartition eq 0)
            }
            .map { it[OebsMottattStatus.kafkaTopic] to it[OebsMottattStatus.kafkaOffset] }
            .toSet()
        OebsOutbox.selectAll()
            .where { OebsOutbox.status eq OutboxStatus.PUBLISHED }
            .orderBy(OebsOutbox.id)
            .map { it[OebsOutbox.id] to simulertSvar(it[OebsOutbox.meldingJson]) }
            .filterNot { (id, svar) -> (svar.topic to id) in besvarte }
    }

    ubesvarte.forEach { (id, svar) ->
        val headers = RecordHeaders().add(FAGSYSTEM_HEADER_NAME, OkonomiFagsystem.EKSPERTBISTAND.name.toByteArray())
        val record = ConsumerRecord<String?, String?>(
            svar.topic,
            0,
            id,
            Clock.System.now().toEpochMilliseconds(),
            TimestampType.CREATE_TIME,
            ConsumerRecord.NULL_SIZE,
            ConsumerRecord.NULL_SIZE,
            svar.referanse,
            svar.json,
            headers,
            Optional.empty(),
        )
        consumer.processRecord(record)
        log.info("OeBS-mock: {} {} -> {}", svar.meldingstype, svar.referanse, svar.status)
    }
}

@OptIn(ExperimentalTime::class)
private fun simulertSvar(melding: OebsBestillingMelding): SimulertSvar {
    fun bestilling(bestillingsnummer: String, status: BestillingStatusType) = SimulertSvar(
        meldingstype = melding.meldingstype,
        topic = BESTILLING_STATUS_TOPIC,
        referanse = bestillingsnummer,
        status = status.name,
        json = Json.encodeToString(BestillingStatus(bestillingsnummer, status)),
    )
    return when (melding) {
        is OebsBestillingMelding.Bestilling -> bestilling(melding.payload.bestillingsnummer, BestillingStatusType.AKTIV)
        is OebsBestillingMelding.Annullering -> bestilling(melding.payload.bestillingsnummer, BestillingStatusType.ANNULLERT)
        is OebsBestillingMelding.GjorOppBestilling -> bestilling(melding.payload.bestillingsnummer, BestillingStatusType.OPPGJORT)
        is OebsBestillingMelding.Faktura -> SimulertSvar(
            meldingstype = melding.meldingstype,
            topic = FAKTURA_STATUS_TOPIC,
            referanse = melding.payload.fakturanummer,
            status = FakturaStatusType.FULLT_BETALT.name,
            json = Json.encodeToString(
                FakturaStatus(melding.payload.fakturanummer, FakturaStatusType.FULLT_BETALT, Clock.System.now())
            ),
        )
    }
}
