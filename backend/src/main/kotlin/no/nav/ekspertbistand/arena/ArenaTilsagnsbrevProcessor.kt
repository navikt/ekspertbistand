package no.nav.ekspertbistand.arena

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.skjema.DTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction


/**
 * https://confluence.adeo.no/spaces/ARENA/pages/366966551/Arena+-+Tjeneste+Kafka+-+Tilsagnsbrev
 * https://confluence.adeo.no/spaces/ARENA/pages/340837316/Arena+-+Funksjonalitet+-+Tilsagnsbrev+-+TAG
 */
class ArenaTilsagnsbrevProcessor(
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

        val kafkaMelding = json.decodeFromString<JsonObject>(value)
        val tilskuddsbrevMelding = kafkaMelding.let { wrapper ->
            wrapper["after"]?.let { after ->
                if (after is JsonObject) {
                    val tilsagnBrevId = after["TILSAGNSBREV_ID"]!!.jsonPrimitive.int
                    val tilsagnId = after["TILSAGN_ID"]!!.jsonPrimitive.int
                    val tilsagnData = json.decodeFromString<TilsagnData>(after["TILSAGN_DATA"]!!.jsonPrimitive.content)
                    TilsagnsbrevKafkaMelding(
                        tilsagnBrevId = tilsagnBrevId,
                        tilsagnId = tilsagnId,
                        tilsagnData = tilsagnData,
                    )
                } else {
                    null
                }
            }
        }

        if (tilskuddsbrevMelding == null) {
            teamLog.error("Kunne ikke parse TilsagnsbrevKafkaMelding. record: {}", record)
            throw Exception("Kunne ikke parse TilsagnsbrevKafkaMelding. key: ${record.key()}")
        }

        if (!tilskuddsbrevMelding.erEkspertbistand) {
            // ikke relevant
            return
        }

        // sjekk at vi er kilde til tilsagn, tilsagnData.aar og tilsagnData.loepenrSak finnes i vårt system

        val skjema = transaction(database) {
            hentArenaSakBySaksnummer(
                asSaksnummer(
                    aar = tilskuddsbrevMelding.tilsagnData.tilsagnNummer.aar,
                    loepenrSak = tilskuddsbrevMelding.tilsagnData.tilsagnNummer.loepenrSak
                )
            ) {
                Json.decodeFromString<DTO.Skjema>(this[ArenaSakTable.skjema])
            }
        }
        if (skjema != null) {
            // Det er vi som har opprettet tiltaket
            EventQueue.publish(
                EventData.TilskuddsbrevMottatt(
                    skjema = skjema,
                    tilsagnbrevId = tilskuddsbrevMelding.tilsagnBrevId,
                    tilsagnData = tilskuddsbrevMelding.tilsagnData
                )
            )
        } else {
            // søknad godkjent men sendt inn i gammel altinn 2 løsning
            // dette vil skje i overgangsperioden
            EventQueue.publish(
                EventData.TilskuddsbrevMottattKildeAltinn(
                    tilsagnbrevId = tilskuddsbrevMelding.tilsagnBrevId,
                    tilsagnData = tilskuddsbrevMelding.tilsagnData
                )
            )
        }
    }

    suspend fun startProcessing() {
        consumer.consume(this)
    }

    companion object {
        /**
         * https://github.com/navikt/arena-iac/tree/62b001b6b119b1c569a6c8fa12767f02e9ad0ac3/kafka-aiven/aapen-arena-tilsagnsbrevgodkjent-v1
         */
        val TOPIC = basedOnEnv(
            dev = "teamarenanais.aapen-arena-tilsagnsbrevgodkjent-v1-q2",
            other = "teamarenanais.aapen-arena-tilsagnsbrevgodkjent-v1",
        )

        val kafkaConfig = KafkaConsumerConfig(
            groupId = "fager.ekspertbistand.tilsagnsbrev",
            topics = setOf(TOPIC),
        )

        val consumer by lazy {
            CoroutineKafkaConsumer(
                kafkaConfig
            )
        }
    }
}

data class TilsagnsbrevKafkaMelding(
    val tilsagnBrevId: Int,
    val tilsagnId: Int,
    val tilsagnData: TilsagnData,
) {
    val erEkspertbistand: Boolean
        get() = tilsagnData.tiltakKode == EKSPERTBISTAND_TILTAKSKODE
}

@Serializable
data class TilsagnData(
    val tilsagnNummer: TilsagnNummer,
    val tilsagnDato: String,
    val periode: Periode,
    val tiltakKode: String,
    val tiltakNavn: String,
    val administrasjonKode: String,
    val refusjonfristDato: String,
    val tiltakArrangor: TiltakArrangor,
    val totaltTilskuddbelop: Int,
    val valutaKode: String,
    val tilskuddListe: List<Tilskudd>,
    val deltaker: Deltaker,
    val antallDeltakere: Int? = null,
    val antallTimeverk: Int? = null,
    val navEnhet: NavEnhet,
    val beslutter: Person,
    val saksbehandler: Person,
    val kommentar: String? = null,
) {
    @Serializable
    data class TilsagnNummer(
        val aar: Int,
        val loepenrSak: Int,
        val loepenrTilsagn: Int,
    )

    @Serializable
    data class Periode(
        val fraDato: String,
        val tilDato: String?,
    )

    @Serializable
    data class TiltakArrangor(
        val arbgiverNavn: String,
        val landKode: String,
        val postAdresse: String,
        val postNummer: String,
        val postSted: String,
        val orgNummerMorselskap: Long,
        val orgNummer: Long,
        val kontoNummer: String,
        val maalform: String,
    )

    @Serializable
    data class Tilskudd(
        val tilskuddType: String,
        val tilskuddBelop: Int,
        val visTilskuddProsent: Boolean,
        val tilskuddProsent: Double?,
    )

    @Serializable
    data class Deltaker(
        val fodselsnr: String,
        val fornavn: String,
        val etternavn: String,
        val landKode: String,
        val postAdresse: String,
        val postNummer: String,
        val postSted: String,
    )

    @Serializable
    data class NavEnhet(
        val navKontor: String,
        val navKontorNavn: String,
        val postAdresse: String,
        val postNummer: String,
        val postSted: String,
        val telefon: String,
        val faks: String?,
    )

    @Serializable
    data class Person(
        val fornavn: String,
        val etternavn: String,
    )
}