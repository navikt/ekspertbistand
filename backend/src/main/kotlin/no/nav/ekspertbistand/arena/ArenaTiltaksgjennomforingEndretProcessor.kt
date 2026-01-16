package no.nav.ekspertbistand.arena

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.skjema.DTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


class ArenaTiltaksgjennomforingEndretProcessor(
    val database: Database,
) : ConsumerRecordProcessor {
    val log = logger()
    val teamLog = teamLogger()
    val json: Json = Json { ignoreUnknownKeys = true }

    override suspend fun processRecord(record: ConsumerRecord<String?, String?>) {
        val value = record.value()
        if (value == null) {
            log.debug("skipping tombstone record")
            return
        }

        val kafkaMelding = try {
            json.decodeFromString<TiltaksgjennomforingEndretKafkaMelding>(value)
        } catch (e: Exception) {
            teamLog.error("Kunne ikke parse TiltaksgjennomforingEndretKafkaMelding. record: {}", record.toString())
            throw Exception("Kunne ikke parse TiltaksgjennomforingEndretKafkaMelding. key: ${record.key()}", e)
        }
        val endring = kafkaMelding.after
        if (!endring.erEkspertbistand) {
            // ikke relevant
            return
        }

        if (endring.tiltakStatusKode != TiltaksgjennomforingEndret.TiltakStatusKode.AVLYST) {
            // vi bryr oss kun om endringer som er avlyste/avslåtte tiltak
            return
        }

        // sjekk at vi er kilde til søknaden, tiltaksgjennomforingId finnes i vårt system
        val skjema = transaction(database) {
            hentArenaSakBytiltaksgjennomfoeringId(endring.tiltaksgjennomfoeringId) {
                Json.decodeFromString<DTO.Skjema>(this[ArenaSakTable.skjema])
            }
        }
        if (skjema != null) {
            // Det er vi som har opprettet tiltaket
            EventQueue.publish(
                EventData.SoknadAvlystIArena(
                    skjema = skjema,
                    tiltaksgjennomforingEndret = endring,
                )
            )
        } else {
            log.info("søknad sendt inn via altinn er avlyst i arena, tiltaksgjennomfoeringId=${endring.tiltaksgjennomfoeringId}")
            teamLog.info("søknad sendt inn via altinn er avlyst i arena, endring=${endring}")
            // søknad avslått på skjema sendt inn i altinn 2, håndtering av dette er ikke med i scope for nå
            // dette kan skje i en overgangsperiode
        }
    }

    suspend fun startProcessing() {
        consumer.consume(this)
    }

    companion object {
        /**
         * https://github.com/navikt/arena-iac/tree/62b001b6b119b1c569a6c8fa12767f02e9ad0ac3/kafka-aiven/aapen-arena-tiltakgjennomforingendret-v1
         */
        val TOPIC = basedOnEnv(
            dev = "teamarenanais.aapen-arena-tiltakgjennomforingendret-v1-q2",
            other = "teamarenanais.aapen-arena-tiltakgjennomforingendret-v1-p",
        )

        val kafkaConfig = KafkaConsumerConfig(
            groupId = "fager.ekspertbistand.tiltaksgjennomforingendret",
            topics = setOf(TOPIC),
        )

        val consumer by lazy {
            CoroutineKafkaConsumer(
                kafkaConfig
            )
        }
    }
}

@Serializable
data class TiltaksgjennomforingEndretKafkaMelding(
    val after: TiltaksgjennomforingEndret,
)

@Serializable
data class TiltaksgjennomforingEndret(
    @SerialName("TILTAKGJENNOMFORING_ID")
    val tiltaksgjennomfoeringId: Int,
    @SerialName("TILTAKSKODE")
    val tiltakKode: String,
    @SerialName("TILTAKSTATUSKODE")
    val tiltakStatusKode: TiltakStatusKode,
) {

    val erEkspertbistand: Boolean
        get() = tiltakKode == EKSPERTBISTAND_TILTAKSKODE

    enum class TiltakStatusKode {
        AVBRUTT,
        AVLYST,
        AVSLUTT,
        GJENNOMFOR,
        PLANLAGT,
    }
}