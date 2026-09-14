package no.nav.ekspertbistand.oebs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.infrastruktur.ConsumerRecordProcessor
import no.nav.ekspertbistand.infrastruktur.CoroutineKafkaConsumer
import no.nav.ekspertbistand.infrastruktur.KafkaConsumerConfig
import no.nav.ekspertbistand.infrastruktur.AutoOffsetReset
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.teamLogger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Instant

/**
 * Status/kvittering fra OeBS, mottatt via Team VALP sine status-topics. Bærer flagg for manuell
 * oppfølging ved feilede bestillinger/utbetalinger, slik at en feilet betaling gir et tydelig
 * signal for manuell oppfølging (jf. spec-avklaring A-8).
 */
object OebsBestillingStatus : Table("oebs_bestilling_status") {
    val bestillingsnummer = text("bestillingsnummer")
    val status = text("status")
    val feilmelding = text("feilmelding").nullable()
    val trengerManuellOppfolging = bool("trenger_manuell_oppfolging").default(false)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val mottattTidspunkt = timestamp("mottatt_tidspunkt").defaultExpression(CurrentTimestamp)
    val rawJson = jsonb<JsonObject>("raw_json", Json)

    override val primaryKey = PrimaryKey(bestillingsnummer)
}

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
 * Topicene inneholder meldinger for **alle** kilder/fagsystemer i tiltaksøkonomi, ikke bare våre.
 * Vi filtrerer derfor tidlig på vår fagsystembokstav ([FAGSYSTEM_KILDE]) og ignorerer alt annet.
 *
 * Skjelettet (subscribe, filtrering, upsert) er grønn sone. Selve tolkningen av hva som er en
 * feilet/avvist operasjon og når det krever manuell oppfølging ([tolkStatus]) er 🔴 rød sone.
 *
 * Ikke wiret inn i oppstart før [tolkStatus] er implementert.
 */
class BestillingStatusConsumer(
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

        val raw = json.decodeFromString<JsonObject>(value)

        val bestillingsnummer = bestillingsnummer(raw)
        if (bestillingsnummer == null) {
            teamLog.warn("Status-melding uten bestillingsnummer på {} – hopper over. record={}", record.topic(), record)
            return
        }

        if (!gjelderOss(bestillingsnummer)) {
            // Topicen inneholder meldinger for alle kilder; ignorer andres uten å tolke dem.
            log.debug("Hopper over status for {} på {} – ikke vår kilde", bestillingsnummer, record.topic())
            return
        }

        // Etterlevelse: logg svaret varig FØR tolkning, slik at revisjonssporet fanger alt vi mottok
        // selv om tolkningen (rød sone) ikke er ferdig. Idempotent på Kafka-koordinatene.
        transaction(database) {
            loggMottattStatus(
                bestillingsnummer = bestillingsnummer,
                kafkaTopic = record.topic(),
                kafkaPartition = record.partition(),
                kafkaOffset = record.offset(),
                kafkaTidspunkt = Instant.fromEpochMilliseconds(record.timestamp()),
                raw = raw,
            )
        }

        val oppdatering = tolkStatus(bestillingsnummer, raw)

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
     * Leser bestillingsnummeret meldingen gjelder. Brukes til å rute meldingen til rett fagsystem
     * ([gjelderOss]). Feltnavnet er en kontrakt-antagelse og må verifiseres mot VALP sitt faktiske
     * statusformat (samme kontraktforbehold som [tolkStatus]).
     */
    private fun bestillingsnummer(raw: JsonObject): String? =
        raw["bestillingsnummer"]?.jsonPrimitive?.contentOrNull

    /**
     * Status-topicene inneholder meldinger for alle kilder. Vår fagsystembokstav ([FAGSYSTEM_KILDE])
     * er første tegn i bestillingsnummeret, så vi behandler kun meldinger med vårt prefiks.
     */
    private fun gjelderOss(bestillingsnummer: String): Boolean =
        bestillingsnummer.startsWith(FAGSYSTEM_KILDE)

    /**
     * 🔴 RØD SONE — skriv selv.
     *
     * Tolker en status-melding fra VALP (som allerede er filtrert til å gjelde oss) til en
     * [StatusOppdatering]. Må avgjøre hvilke statuser som betyr feilet/avvist bestilling eller
     * utbetaling, og sette [StatusOppdatering.trengerManuellOppfolging] deretter. Dette er
     * feilhåndtering av avviste OeBS-operasjoner og er økonomikritisk — den skal implementeres og
     * forstås av teamet, mot VALP sitt faktiske statusformat (kontraktverifiseres).
     */
    private fun tolkStatus(bestillingsnummer: String, raw: JsonObject): StatusOppdatering {
        TODO("Rød sone: tolk VALP-statusmelding for $bestillingsnummer og avgjør behov for manuell oppfølging.")
    }

    companion object {
        // VALP publiserer status på egne topics; samme navn i dev og prod.
        val TOPICS = setOf(
            "team-mulighetsrommet.tiltaksokonomi.bestilling-status-v1",
            "team-mulighetsrommet.tiltaksokonomi.faktura-status-v1",
        )

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
