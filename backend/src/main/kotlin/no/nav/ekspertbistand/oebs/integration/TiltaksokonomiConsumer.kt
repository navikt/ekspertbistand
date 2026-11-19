package no.nav.ekspertbistand.oebs.integration

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain
import no.nav.ekspertbistand.event.domain.OebsFakturaStatusDomain
import no.nav.ekspertbistand.event.domain.OebsKvitteringDomain
import no.nav.ekspertbistand.event.domain.tilDomain
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.ConsumerRecordProcessor
import no.nav.ekspertbistand.infrastruktur.CoroutineKafkaConsumer
import no.nav.ekspertbistand.infrastruktur.KafkaConsumerConfig
import no.nav.ekspertbistand.infrastruktur.AutoOffsetReset
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.teamLogger
import no.nav.ekspertbistand.oebs.model.OebsBestillingStatus
import no.nav.ekspertbistand.oebs.model.bestillingsnummerForFaktura
import no.nav.ekspertbistand.oebs.model.erTestnummer
import no.nav.ekspertbistand.oebs.model.hentBestillingReferanse
import no.nav.ekspertbistand.oebs.model.loggMottattStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.Instant

/** Resultatet av å tolke en status-melding fra OeBS/VALP. */
data class StatusOppdatering(
    val bestillingsnummer: String,
    val status: String,
) {
    /**
     * `FEILET` er den eneste statusen som betyr avvist/feilet operasjon (jf. enum-dokumentasjonen i
     * [BestillingStatusType]/[FakturaStatusType]). Utledes fra [status] framfor å lagres eksplisitt.
     */
    val feilet: Boolean
        get() = status == BestillingStatusType.FEILET.name || status == FakturaStatusType.FEILET.name
}

/**
 * Konsumerer VALP sine status-topics for bestilling og faktura, lagrer siste status per
 * bestilling i [OebsBestillingStatus] og publiserer statusen som event på søknaden
 * ([EventData.OebsBestillingStatusMottatt]/[EventData.OebsFakturaStatusMottatt]).
 *
 * Meldingene deserialiseres til de sterkt typede, lokalt speilede kontraktene
 * ([BestillingStatus]/[FakturaStatus], wrappet i [OebsStatusMelding]) i stedet for å leses ut av en
 * løs `JsonObject`. Råmeldingen beholdes ved siden av for det varige revisjonssporet
 * ([loggMottattStatus]).
 *
 * Topicene inneholder meldinger for **alle** kilder/fagsystemer i tiltaksøkonomi, ikke bare våre.
 * VALP setter en `fagsystem`-header på hver status-melding, så vi filtrerer tidlig på den
 * ([FAGSYSTEM_HEADER_NAME] = [OkonomiFagsystem.EKSPERTBISTAND]) og ignorerer alt annet.
 *
 * Tolkningen ([tolkStatus]) — avgjørelsen av hva som er en feilet/avvist operasjon og når det krever
 * manuell oppfølging — er 🔴 rød sone (økonomikritisk) og implementert her.
 *
 * Startes av [no.nav.ekspertbistand.oebs.startOebsProsessering], som `main()` bare kaller i dev.
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

        if (!gjelderOss(record)) {
            log.debug("Hopper over status på {} – ikke vårt fagsystem", record.topic())
            return
        }

        val raw = json.decodeFromString<JsonObject>(value)

        val melding = try {
            deserialiserStatus(record.topic(), value)
        } catch (e: Exception) {
            teamLog.error("Status-melding bryter kontrakten. record={}", record, e)
            log.error(
                "Status-melding bryter kontrakten: topic={} partition={} offset={} feil={}. Prøver på nytt. Detaljer i teamLogs.",
                record.topic(), record.partition(), record.offset(), e.javaClass.simpleName,
            )
            // Uten cause: unntaksteksten gjengir meldingsinnholdet, som bare skal i teamLogs.
            throw IllegalStateException(
                "Status-melding bryter kontrakten: topic=${record.topic()} partition=${record.partition()} offset=${record.offset()}"
            )
        }

        val oppdatering = tolkStatus(melding)
        val kafkaTidspunkt = Instant.fromEpochMilliseconds(record.timestamp())

        // Revisjonsspor, siste status og eventen i én transaksjon. Eventen publiseres bare første gang
        // vi behandler meldingen, så samme Kafka-melding levert på nytt gir ingen ny event.
        transaction(database) {
            val nyMelding = loggMottattStatus(
                bestillingsnummer = melding.referanse,
                kafkaTopic = record.topic(),
                kafkaPartition = record.partition(),
                kafkaOffset = record.offset(),
                kafkaTidspunkt = kafkaTidspunkt,
                raw = raw,
            )

            OebsBestillingStatus.upsert {
                it[OebsBestillingStatus.bestillingsnummer] = oppdatering.bestillingsnummer
                it[status] = oppdatering.status
                it[trengerManuellOppfolging] = oppdatering.feilet
                it[mottattTidspunkt] = CurrentTimestamp
                it[rawJson] = raw
            }

            if (nyMelding) {
                publiserTilSaksgangen(melding, record)
            }
        }

        if (oppdatering.feilet) {
            teamLog.error(
                "Status for {} er FEILET og krever manuell oppfølging. record={} raw={}",
                melding.referanse, record, raw,
            )
            log.error(
                "Status for {} er FEILET og krever manuell oppfølging, feilKode={}. Detaljer i teamLogs.",
                melding.referanse, melding.feilKode,
            )
        }
    }

    /**
     * 🔴 RØD SONE: publiserer statusen på søknaden bestillingen hører til.
     *
     * Mangler referansen, er det enten et manuelt testnummer ([erTestnummer]), som lagres uten event,
     * eller en feil hos oss. Feilen kaster, så transaksjonen rulles tilbake og meldingen prøves på
     * nytt. Partisjonen blokkeres til feilen er rettet, fordi statusen ikke skal gå tapt for
     * saksgangen.
     */
    private fun JdbcTransaction.publiserTilSaksgangen(
        melding: OebsStatusMelding,
        record: ConsumerRecord<String?, String?>,
    ) {
        val bestillingsnummer = when (melding) {
            is OebsStatusMelding.Bestilling -> melding.status.bestillingsnummer
            is OebsStatusMelding.Faktura -> bestillingsnummerForFaktura(melding.status.fakturanummer)
        }

        val referanse = hentBestillingReferanse(bestillingsnummer)
        if (referanse == null) {
            if (erTestnummer(bestillingsnummer)) {
                log.info("Status for testnummer {} publiseres ikke til saksgangen", melding.referanse)
                return
            }
            teamLog.error("Status for {} har ingen referanse til sak. record={}", melding.referanse, record)
            log.error(
                "Status for {} har ingen referanse til sak: topic={} partition={} offset={}. Prøver på nytt.",
                melding.referanse, record.topic(), record.partition(), record.offset(),
            )
            throw IllegalStateException(
                "Status for ${melding.referanse} har ingen referanse til sak: topic=${record.topic()} " +
                    "partition=${record.partition()} offset=${record.offset()}"
            )
        }

        val kvittering = OebsKvitteringDomain(
            kafkaTopic = record.topic(),
            kafkaPartition = record.partition(),
            kafkaOffset = record.offset(),
            mottattTidspunkt = Clock.System.now(),
        )
        val sakId = referanse.sakId.toString()
        val soknadId = referanse.soknadId.toString()
        val event = when (melding) {
            is OebsStatusMelding.Bestilling -> EventData.OebsBestillingStatusMottatt(
                sakId = sakId,
                soknadId = soknadId,
                bestilling = OebsBestillingStatusDomain(
                    bestillingsnummer = melding.status.bestillingsnummer,
                    status = melding.status.status.tilDomain(),
                    statusSistOppdatert = melding.status.statusSistOppdatert,
                    feilKode = melding.status.feilKode,
                    feilMelding = melding.status.feilMelding,
                    kvittering = kvittering,
                ),
            )

            is OebsStatusMelding.Faktura -> EventData.OebsFakturaStatusMottatt(
                sakId = sakId,
                soknadId = soknadId,
                faktura = OebsFakturaStatusDomain(
                    fakturanummer = melding.status.fakturanummer,
                    bestillingsnummer = bestillingsnummer,
                    status = melding.status.status.tilDomain(),
                    statusSistOppdatert = melding.status.statusSistOppdatert,
                    feilKode = melding.status.feilKode,
                    feilMelding = melding.status.feilMelding,
                    kvittering = kvittering,
                ),
            )
        }
        publishEventQueue(event)
        log.info("{} for {} publisert for sak {}", event::class.simpleName, melding.referanse, sakId)
    }

    /**
     * Deserialiserer meldingsverdien til rett sterkt typet kontrakt basert på hvilket status-topic
     * den kom på. Kaster for ukjente topics og for meldinger som ikke lar seg parse mot kontrakten,
     * slik at meldingen ikke committes, men prøves på nytt.
     */
    private fun deserialiserStatus(topic: String, value: String): OebsStatusMelding =
        when (topic) {
            BESTILLING_STATUS_TOPIC ->
                OebsStatusMelding.Bestilling(json.decodeFromString<BestillingStatus>(value))

            FAKTURA_STATUS_TOPIC ->
                OebsStatusMelding.Faktura(json.decodeFromString<FakturaStatus>(value))

            else -> throw IllegalArgumentException("Ukjent status-topic $topic")
        }

    /**
     * Status-topicene inneholder meldinger for alle fagsystemer. VALP setter en `fagsystem`-header
     * med [OkonomiFagsystem]-navnet på hver melding, så vi behandler kun meldinger merket med vårt
     * fagsystem. Mangler headeren, er meldingen ikke vår.
     */
    private fun gjelderOss(record: ConsumerRecord<String?, String?>): Boolean {
        val fagsystem = record.headers().lastHeader(FAGSYSTEM_HEADER_NAME)?.value()?.let { String(it) }
        return fagsystem == OkonomiFagsystem.EKSPERTBISTAND.name
    }

    /**
     * 🔴 RØD SONE — feilhåndtering av avviste OeBS-operasjoner (økonomikritisk).
     *
     * Tolker en sterkt typet, allerede vår-filtrert status-melding til en [StatusOppdatering] som
     * kalleren upserter i [OebsBestillingStatus].
     *
     * **Hva som krever manuell oppfølging.** Kun `FEILET` markerer en avvist/feilet operasjon (jf.
     * enum-dokumentasjonen i [BestillingStatusType]/[FakturaStatusType], der `FEILET` eksplisitt betyr
     * «Krever manuell oppfølging»). Alle andre verdier er normale steg i livssyklusen (sendt, aktiv,
     * betalt, oppgjort, annullert) og skal ikke trigge oppfølging. Vi feiler bevisst **eksplisitt** på
     * det ene kjente feil-tilfellet i stedet for å gjette på et sett «ok»-verdier. En ny enum-verdi
     * fra VALP når aldri hit: deserialiseringen feiler, og meldingen prøves på nytt til vi har tatt
     * stilling til verdien.
     *
     * **Nøkkel = [OebsStatusMelding.referanse].** Bestilling nøkles på bestillingsnummer, faktura på
     * fakturanummer — samme referanse som revisjonssporet ([loggMottattStatus]) bruker. Det gir egne
     * rader for bestilling og faktura, så en senere vellykket faktura-status aldri overskriver (og
     * dermed nullstiller) et oppfølgingsflagg satt av en feilet bestilling. Korrelasjonen tilbake til
     * bestillingen ligger uansett i nummeret (fakturanummer = `<bestillingsnummer>-<løpenr>`, jf. spec §4).
     *
     * Selve feilet/ikke-feilet-avgjørelsen er [StatusOppdatering.feilet] (utledet fra status), så vi
     * lagrer ikke et redundant flagg eller en syntetisk feilmelding — råmeldingen i revisjonssporet
     * har detaljene.
     */
    private fun tolkStatus(melding: OebsStatusMelding): StatusOppdatering = when (melding) {
        is OebsStatusMelding.Bestilling -> StatusOppdatering(
            bestillingsnummer = melding.status.bestillingsnummer,
            status = melding.status.status.name,
        )

        is OebsStatusMelding.Faktura -> StatusOppdatering(
            bestillingsnummer = melding.status.fakturanummer,
            status = melding.status.status.name,
        )
    }

    companion object {
        // VALP setter fagsystem-navnet i denne headeren på hver status-melding; jf. VALP sin
        // FAGSYSTEM_HEADER_NAME i tiltaksokonomi-client.
        const val FAGSYSTEM_HEADER_NAME = "fagsystem"

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
