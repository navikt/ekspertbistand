package no.nav.ekspertbistand.arena

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.infrastruktur.*
import org.apache.kafka.clients.consumer.ConsumerRecord


/**
 * https://confluence.adeo.no/spaces/ARENA/pages/366966551/Arena+-+Tjeneste+Kafka+-+Tilsagnsbrev
 * https://confluence.adeo.no/spaces/ARENA/pages/340837316/Arena+-+Funksjonalitet+-+Tilsagnsbrev+-+TAG
 */
class ArenaTilsagnsbrevProcessor(
    /**
     * funksjon som sjekker om tilsagnet skal behandles videre.
     * f.eks sjekk om vi er kilde for tilsagnet ved å se på saknummer (aar+loepenrSak) i tilsagnData
     */
    val skalBehandles: (TilsagnData) -> Boolean,

    /**
     * callback for kafka records som vi skal opprette SøknadGodkjent hendelse for
     * usage:
     * opprettSøknadGodkjentHendelse = { EventQueue.publish(SøknadGodkjent(...))  }
     */
    val opprettSøknadGodkjentHendelse: suspend (TilsagnsbrevKafkaMelding) -> Unit = { _ -> },
) : ConsumerRecordProcessor {
    val log = logger()
    val teamLog = teamLogger()
    val json: Json = Json { ignoreUnknownKeys = true }
    override suspend fun processRecord(record: ConsumerRecord<String?, String?>) {
        val tilsagnsbrevKafkaMelding = json.decodeFromString<JsonObject>(record.value() ?: "{}").let { wrapper ->
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

        if (tilsagnsbrevKafkaMelding == null) {
            log.error("Kunne ikke parse TilsagnsbrevKafkaMelding. key: {}", record.key())
            teamLog.error("Kunne ikke parse TilsagnsbrevKafkaMelding. record: {}", record)
            return
        }

        if (!tilsagnsbrevKafkaMelding.erEkspertbistand) {
            // ikke relevant
            return
        }

        // sjekk at vi er kilde til tilsagn,  tilsagnData.aar og tilsagnData.loepenrSak finnes i vårt system
        // hvis ikke hopp over
        if (!skalBehandles(tilsagnsbrevKafkaMelding.tilsagnData)) {
            return
        }

        // opprett SøknadGodkjent hendelse med relevante data
        opprettSøknadGodkjentHendelse(tilsagnsbrevKafkaMelding)
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
        get() = tilsagnData.tiltakKode == "EKSPERTBIST"
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