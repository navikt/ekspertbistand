package no.nav.ekspertbistand.infrastruktur

import kotlinx.coroutines.*
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.lang.System.getenv
import kotlin.coroutines.cancellation.CancellationException

data class KafkaConsumerConfig(
    val topics: Set<String>,
    val groupId: String
)

class CoroutineKafkaConsumer(
    private val config: KafkaConsumerConfig,
) {
    private val log = logger()

    private val properties = buildMap {
        put(ConsumerConfig.GROUP_ID_CONFIG, config.groupId)
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, (getenv("KAFKA_BROKERS") ?: "localhost:9092"))
        put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "60000")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        if (!getenv("KAFKA_KEYSTORE_PATH").isNullOrBlank()) {
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, getenv("KAFKA_KEYSTORE_PATH"))
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, getenv("KAFKA_CREDSTORE_PASSWORD"))
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, getenv("KAFKA_TRUSTSTORE_PATH"))
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, getenv("KAFKA_CREDSTORE_PASSWORD"))
        }
    }

    private val kafkaConsumer = KafkaConsumer<String?, String?>(properties)

    suspend fun consume(processor: ConsumerRecordProcessor) = withContext(Dispatchers.IO) {
        kafkaConsumer.use { consumer ->
            consumer.subscribe(config.topics)

            while (isActive) {
                try {
                    val records = consumer.poll(java.time.Duration.ofMillis(1000))
                    if (records.any()) {
                        for (record in records) {
                            processor.processRecord(record)
                        }
                        consumer.commitSync()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Feil ved prosessering av kafka-melding", e)
                    delay(5000) // TODO: backoff
                }
            }
        }
    }

    suspend fun batchConsume(processor: ConsumerRecordProcessor) = withContext(Dispatchers.IO) {
        kafkaConsumer.use { consumer ->
            consumer.subscribe(config.topics)

            while (isActive) {
                try {
                    val records = consumer.poll(java.time.Duration.ofMillis(1000))
                    if (records.any()) {
                        processor.processRecords(records)
                        consumer.commitSync()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Feil ved prosessering av kafka-melding", e)
                    delay(5000) // TODO: backoff
                }
            }
        }
    }
}

interface ConsumerRecordProcessor {
    suspend fun processRecord(record: ConsumerRecord<String?, String?>)
    suspend fun processRecords(records: ConsumerRecords<String?, String?>) {
        for (record in records) {
            processRecord(record)
        }
    }
}