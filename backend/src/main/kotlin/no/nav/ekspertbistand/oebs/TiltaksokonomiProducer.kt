package no.nav.ekspertbistand.oebs

import no.nav.ekspertbistand.infrastruktur.logger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import java.lang.System.getenv

/**
 * Full topic-navn = `<namespace>.<metadata.name>` = `fager.ekspertbistand.bestillinger-v1`.
 * Samme streng i dev og prod; pool/cluster skiller miljøene (se topic-manifestene under `nais/`).
 */
const val BESTILLINGER_TOPIC = "fager.ekspertbistand.bestillinger-v1"

/**
 * Tynn innpakning rundt [KafkaProducer] for utgående tiltaksøkonomi-meldinger.
 *
 * Produsenten er idempotent (`enable.idempotence=true`, `acks=all`) slik at en retry etter en
 * tvetydig feil ikke gir duplikat på topicen. Selve at-least-once-garantien og feiltoleransen mot
 * Kafka-nedetid ligger i outbox-en ([OebsOutbox]) — denne klassen gjør kun selve publiseringen.
 * Se topic-manifestene under `nais/` (dev/prod-gcp-topic-bestillinger.yaml).
 */
class TiltaksokonomiProducer(
    private val producer: Producer<String, String> = KafkaProducer(kafkaProducerProperties()),
    private val topic: String = BESTILLINGER_TOPIC,
) : AutoCloseable {
    private val log = logger()

    /**
     * Publiserer og blokkerer til meldingen er bekreftet av alle in-sync replicas.
     * Kaster ved feil slik at outbox-en beholder raden og prøver igjen senere.
     */
    fun send(key: String, value: String): RecordMetadata {
        val metadata = producer.send(ProducerRecord(topic, key, value)).get()
        log.info(
            "Publiserte melding til {} partition={} offset={}",
            topic,
            metadata.partition(),
            metadata.offset(),
        )
        return metadata
    }

    override fun close() = producer.close()
}

internal fun kafkaProducerProperties(): Map<String, Any?> = buildMap {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, getenv("KAFKA_BROKERS") ?: "localhost:9092")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.canonicalName)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.canonicalName)
    // At-least-once uten duplikater ved retries.
    put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
    put(ProducerConfig.ACKS_CONFIG, "all")
    put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1)
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
