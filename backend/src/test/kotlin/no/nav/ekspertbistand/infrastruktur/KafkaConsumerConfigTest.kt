package no.nav.ekspertbistand.infrastruktur

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class KafkaConsumerConfigTest {

    @Test
    fun `default autoOffsetReset er none slik at eksisterende consumere er uendret`() {
        val config = KafkaConsumerConfig(
            topics = setOf("et-topic"),
            groupId = "en-gruppe",
        )

        assertEquals(AutoOffsetReset.NONE, config.autoOffsetReset)
        assertEquals("none", kafkaConsumerProperties(config)[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG])
    }

    @Test
    fun `autoOffsetReset EARLIEST gir earliest`() {
        val config = KafkaConsumerConfig(
            topics = setOf("et-topic"),
            groupId = "en-gruppe",
            autoOffsetReset = AutoOffsetReset.EARLIEST,
        )

        assertEquals("earliest", kafkaConsumerProperties(config)[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG])
    }
}
