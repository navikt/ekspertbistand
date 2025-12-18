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
        val tilskuddsbrevMelding = json.decodeFromString<JsonObject>(record.value() ?: "{}").let { wrapper ->
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
            log.error("Kunne ikke parse TilsagnsbrevKafkaMelding. key: {}", record.key())
            teamLog.error("Kunne ikke parse TilsagnsbrevKafkaMelding. record: {}", record)
            return
        }

        if (!tilskuddsbrevMelding.erEkspertbistand) {
            // ikke relevant
            return
        }

        // sjekk at vi er kilde til tilsagn, tilsagnData.aar og tilsagnData.loepenrSak finnes i vårt system
        // hvis ikke hopp over
        val skjema = transaction(database) {
            val loepenr = tilskuddsbrevMelding.tilsagnData.tilsagnNummer.loepenrSak
            val aar = tilskuddsbrevMelding.tilsagnData.tilsagnNummer.aar
            hentSkjemaForTiltaksgjennomføring(loepenr, aar) {
                Json.decodeFromString<DTO.Skjema>(this[ArenaSakTable.skjema])
            }
        }
        if (skjema != null){
            // Det er vi som har opprettet tiltaket
            EventQueue.publish(
                EventData.TilskuddsbrevMottatt(
                    skjema = skjema,
                    tilskuddsbrevId = tilskuddsbrevMelding.tilsagnBrevId,
                    tilskuddsnummer = tilskuddsbrevMelding.tilsagnData.tilsagnNummer
                )
            )
        }
    }

    companion object {
        const val TOPIC = "teamarenanais.aapen-arena-tilsagnsbrevgodkjent-v1"

        val kafkaConfig = KafkaConsumerConfig(
            groupId = "fager.ekspertbistand",
            topics = setOf(TOPIC),
        )

        /**
         * usage:
         * CoroutineScope(coroutineContext + Dispatchers.IO.limitedParallelism(1)).launch {
         *     ArenaTilsagnsbrevProcessor.startProcessing(
         *         ArenaTilsagnsbrevProcessor(...args)
         *     )
         * }
         */
        suspend fun startProcessing(processor: ArenaTilsagnsbrevProcessor) {
            CoroutineKafkaConsumer(
                kafkaConfig
            ).consume(
                processor
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