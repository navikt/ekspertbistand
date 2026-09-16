@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Varig revisjonsspor (etterlevelse).
 *
 * Vi må kunne svare for hva vi har bestilt og hvilke svar vi fikk, uavhengig av at Kafka-topicene
 * har 90 dagers retention. Derfor logges både utgående meldinger og innkommende svar append-only i
 * egen database, som lever like lenge som forretningsbehovet.
 *
 * Dette skiller **revisjonsspor** (denne fila, aldri overskrevet) fra **arbeidsdata**
 * ([OebsOutbox] som dreneres, [OebsBestillingStatus] som holder siste tilstand).
 */

/** Utgående: nøyaktig hva vi publiserte til tiltaksøkonomi-topicen, med Kafka-koordinater. */
object OebsSendtMelding : Table("oebs_sendt_melding") {
    val id = long("id").autoIncrement()
    val bestillingsnummer = text("bestillingsnummer")
    val meldingstype = text("meldingstype")
    val meldingJson = jsonb<OebsBestillingMelding>("melding_json", OebsBestillingMelding.json)
    val kafkaTopic = text("kafka_topic")
    val kafkaPartition = integer("kafka_partition")
    val kafkaOffset = long("kafka_offset")
    val sendtTidspunkt = timestamp("sendt_tidspunkt").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

/** Inngående: alle svar vi mottok fra VALP/OeBS, rått og komplett (full historikk). */
object OebsMottattStatus : Table("oebs_mottatt_status") {
    val id = long("id").autoIncrement()
    val bestillingsnummer = text("bestillingsnummer")
    val kafkaTopic = text("kafka_topic")
    val kafkaPartition = integer("kafka_partition")
    val kafkaOffset = long("kafka_offset")
    val kafkaTidspunkt = timestamp("kafka_tidspunkt")
    val rawJson = jsonb<JsonObject>("raw_json", Json)
    val mottattTidspunkt = timestamp("mottatt_tidspunkt").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("oebs_mottatt_status_koordinat_idx", kafkaTopic, kafkaPartition, kafkaOffset)
    }
}

/**
 * Logger en publisert melding i revisjonssporet. Kalles av outbox-polleren i **samme transaksjon**
 * som den markerer outbox-raden publisert, slik at revisjonsloggen og publiseringen er atomiske.
 */
fun JdbcTransaction.loggSendtMelding(
    melding: OebsBestillingMelding,
    kafkaTopic: String,
    kafkaPartition: Int,
    kafkaOffset: Long,
) {
    OebsSendtMelding.insert {
        it[bestillingsnummer] = melding.bestillingsnummer
        it[meldingstype] = melding.meldingstype
        it[meldingJson] = melding
        it[OebsSendtMelding.kafkaTopic] = kafkaTopic
        it[OebsSendtMelding.kafkaPartition] = kafkaPartition
        it[OebsSendtMelding.kafkaOffset] = kafkaOffset
    }
}

/**
 * Logger et mottatt svar i revisjonssporet. Idempotent på Kafka-koordinatene: reprosessering av
 * samme melding (at-least-once) gir ikke dupliserte revisjonsrader.
 */
fun JdbcTransaction.loggMottattStatus(
    bestillingsnummer: String,
    kafkaTopic: String,
    kafkaPartition: Int,
    kafkaOffset: Long,
    kafkaTidspunkt: Instant,
    raw: JsonObject,
) {
    OebsMottattStatus.insertIgnore {
        it[OebsMottattStatus.bestillingsnummer] = bestillingsnummer
        it[OebsMottattStatus.kafkaTopic] = kafkaTopic
        it[OebsMottattStatus.kafkaPartition] = kafkaPartition
        it[OebsMottattStatus.kafkaOffset] = kafkaOffset
        it[OebsMottattStatus.kafkaTidspunkt] = kafkaTidspunkt
        it[rawJson] = raw
    }
}
