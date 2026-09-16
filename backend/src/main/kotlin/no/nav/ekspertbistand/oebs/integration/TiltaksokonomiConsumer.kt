package no.nav.ekspertbistand.oebs.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.infrastruktur.ConsumerRecordProcessor
import no.nav.ekspertbistand.infrastruktur.CoroutineKafkaConsumer
import no.nav.ekspertbistand.infrastruktur.KafkaConsumerConfig
import no.nav.ekspertbistand.infrastruktur.AutoOffsetReset
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.teamLogger
import no.nav.ekspertbistand.oebs.model.FAGSYSTEM_KILDE
import no.nav.ekspertbistand.oebs.model.OebsBestillingStatus
import no.nav.ekspertbistand.oebs.model.loggMottattStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Instant

/** Resultatet av å tolke en status-melding fra OeBS/VALP. */
data class StatusOppdatering(
    val bestillingsnummer: String,
    val status: String,
    val feilmelding: String?,
    val trengerManuellOppfolging: Boolean,
)

/**
 * Konsumerer VALP sine status-topics for bestilling og faktura, og lagrer siste status per
 * bestilling i [OebsBestillingStatus].
 *
 * Meldingene deserialiseres til de sterkt typede, lokalt speilede kontraktene
 * ([BestillingStatus]/[FakturaStatus], wrappet i [OebsStatusMelding]) i stedet for å leses ut av en
 * løs `JsonObject`. Råmeldingen beholdes ved siden av for det varige revisjonssporet
 * ([loggMottattStatus]).
 *
 * Topicene inneholder meldinger for **alle** kilder/fagsystemer i tiltaksøkonomi, ikke bare våre.
 * Vi filtrerer derfor tidlig på vår fagsystembokstav ([FAGSYSTEM_KILDE]) og ignorerer alt annet.
 *
 * Skjelettet (subscribe, deserialisering, filtrering, upsert) er grønn sone. Selve tolkningen av
 * hva som er en feilet/avvist operasjon og når det krever manuell oppfølging ([tolkStatus]) er
 * 🔴 rød sone.
 *
 * Ikke wiret inn i oppstart før [tolkStatus] er implementert.
 */
class TiltaksokonomiConsumer(
    private val database: Database,
) : ConsumerRecordProcessor {
    private val log = logger()
    private val teamLog = teamLogger()
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(kotlin.time.ExperimentalTime::class)
    override suspend fun processRecord(record: ConsumerRecord<String?, String?>) {
        val value = record.value()
        if (value == null) {
            log.debug("skipping tombstone record på {}", record.topic())
            return
        }

        // Rå melding beholdes for revisjonssporet; typet modell brukes til ruting og tolkning.
        val raw = json.decodeFromString<JsonObject>(value)

        val melding = deserialiserStatus(record.topic(), value)
        if (melding == null) {
            teamLog.warn("Ukjent/ugyldig status-melding på {} – hopper over. record={}", record.topic(), record)
            return
        }

        if (!gjelderOss(melding.referanse)) {
            // Topicen inneholder meldinger for alle kilder; ignorer andres uten å tolke dem.
            log.debug("Hopper over status for {} på {} – ikke vår kilde", melding.referanse, record.topic())
            return
        }

        // Etterlevelse: logg svaret varig FØR tolkning, slik at revisjonssporet fanger alt vi mottok
        // selv om tolkningen (rød sone) ikke er ferdig. Idempotent på Kafka-koordinatene.
        transaction(database) {
            loggMottattStatus(
                bestillingsnummer = melding.referanse,
                kafkaTopic = record.topic(),
                kafkaPartition = record.partition(),
                kafkaOffset = record.offset(),
                kafkaTidspunkt = Instant.fromEpochMilliseconds(record.timestamp()),
                raw = raw,
            )
        }

        val oppdatering = tolkStatus(melding)

        transaction(database) {
            OebsBestillingStatus.upsert {
                it[OebsBestillingStatus.bestillingsnummer] = oppdatering.bestillingsnummer
                it[status] = oppdatering.status
                it[feilmelding] = oppdatering.feilmelding
                it[trengerManuellOppfolging] = oppdatering.trengerManuellOppfolging
                it[mottattTidspunkt] = CurrentTimestamp
                it[rawJson] = raw
            }
        }

        if (oppdatering.trengerManuellOppfolging) {
            teamLog.warn("Bestilling {} krever manuell oppfølging: {}", oppdatering.bestillingsnummer, oppdatering.feilmelding)
        }
    }

    /**
     * Deserialiserer meldingsverdien til rett sterkt typet kontrakt basert på hvilket status-topic
     * den kom på. Returnerer `null` for ukjente topics eller meldinger som ikke lar seg parse mot
     * kontrakten (logges og hoppes over av kalleren).
     */
    private fun deserialiserStatus(topic: String, value: String): OebsStatusMelding? =
        try {
            when (topic) {
                BESTILLING_STATUS_TOPIC ->
                    OebsStatusMelding.Bestilling(json.decodeFromString<BestillingStatus>(value))

                FAKTURA_STATUS_TOPIC ->
                    OebsStatusMelding.Faktura(json.decodeFromString<FakturaStatus>(value))

                else -> null
            }
        } catch (e: Exception) {
            teamLog.warn("Klarte ikke deserialisere status-melding på {}", topic, e)
            null
        }

    /**
     * Status-topicene inneholder meldinger for alle kilder. Vår fagsystembokstav ([FAGSYSTEM_KILDE])
     * er første tegn i bestillings-/fakturanummeret, så vi behandler kun meldinger med vårt prefiks.
     */
    private fun gjelderOss(referanse: String): Boolean =
        referanse.startsWith(FAGSYSTEM_KILDE)

    /**
     * 🔴 RØD SONE — skriv selv.
     *
     * Tolker en sterkt typet status-melding fra VALP (som allerede er filtrert til å gjelde oss) til
     * en [StatusOppdatering]. Må avgjøre hvilke [BestillingStatusType]/[FakturaStatusType]-verdier
     * som betyr feilet/avvist bestilling eller utbetaling, og sette
     * [StatusOppdatering.trengerManuellOppfolging] deretter. Dette er feilhåndtering av avviste
     * OeBS-operasjoner og er økonomikritisk — den skal implementeres og forstås av teamet.
     */
    private fun tolkStatus(melding: OebsStatusMelding): StatusOppdatering {
        TODO("Rød sone: tolk VALP-statusmelding ${melding.referanse} og avgjør behov for manuell oppfølging.")
    }

    companion object {
        // VALP publiserer status på egne topics; samme navn i dev og prod.
        const val BESTILLING_STATUS_TOPIC = "team-mulighetsrommet.tiltaksokonomi.bestilling-status-v1"
        const val FAKTURA_STATUS_TOPIC = "team-mulighetsrommet.tiltaksokonomi.faktura-status-v1"

        val TOPICS = setOf(BESTILLING_STATUS_TOPIC, FAKTURA_STATUS_TOPIC)

        val kafkaConfig = KafkaConsumerConfig(
            groupId = "fager.ekspertbistand.tiltaksokonomi-status",
            topics = TOPICS,
            // Ny consumer group uten committede offsets. Settes tilbake til NONE når den har
            // committet i prod (jf. mønster i CoroutineKafkaConsumer).
            autoOffsetReset = AutoOffsetReset.EARLIEST,
        )

        val consumer by lazy { CoroutineKafkaConsumer(kafkaConfig) }
    }

    suspend fun startProcessing() {
        consumer.consume(this)
    }
}
