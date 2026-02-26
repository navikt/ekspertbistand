package no.nav.ekspertbistand.infrastruktur

import kotlinx.coroutines.*
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.lang.System.getenv
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

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
        put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100")
        put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "60000")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
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
            log.info("Successfully subscribed to $config")

            while (isActiveAndNotTerminating) {
                try {
                    val records = consumer.poll(1000.milliseconds.toJavaDuration())
                    log.info("polled {} records {}", records.count(), config)

                    if (records.any()) {
                        for (record in records) {
                            try {
                                processor.processRecord(record)
                            } catch (e: Exception) {
                                log.error("Feil ved prosessering av kafka-melding.", e)

                                // without seek next poll will advance the offset, regardless of autocommit=false
                                consumer.seek(TopicPartition(record.topic(), record.partition()), record.offset())

                                throw Exception("Feil ved prosessering av kafka-melding. partition=${record.partition()} offset=${record.offset()} $config", e)
                            }
                        }
                        log.info("committing offsets: {} {}", records.partitions().associateWith { tp -> records.records(tp).last().offset() }, config)
                        consumer.commitSync()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("Feil ved prosessering av kafka-melding. $config", e)
                    delay(5000)
                }
            }
        }
    }
}

interface ConsumerRecordProcessor {
    suspend fun processRecord(record: ConsumerRecord<String?, String?>)
}