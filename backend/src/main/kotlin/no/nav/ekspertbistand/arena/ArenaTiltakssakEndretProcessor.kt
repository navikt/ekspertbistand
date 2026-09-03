package no.nav.ekspertbistand.arena

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.soknad.DTO
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant

/**
 * Lytter på endringer i Arena-tabellen `SIAMO.SAK` for å oppdage at en saksbehandler har begynt å
 * behandle en ekspertbistandsak i Arena.
 *
 * Tiltaksansvarlig lagres på tiltakssaken (`sak.brukerid_ansvarlig`), ikke på tiltaksgjennomføringen.
 * Når sak/tiltaksgjennomføring opprettes via Tiltaksgjennomfoering API settes behandlende enhet som
 * tiltaksansvarlig, og feltet oppdateres til saksbehandlerens egen brukerident når vedkommende tar
 * saken. Derfor kan ikke [ArenaTiltaksgjennomforingEndretProcessor] brukes — den meldingen inneholder
 * kun `TILTAKSTATUSKODE`.
 *
 * Feltdokumentasjon:
 * https://confluence.adeo.no/spaces/ARENA/pages/478256186/Arena+-+Tjeneste+Kafka+-+Tiltakssak
 */
class ArenaTiltakssakEndretProcessor(
    val database: Database,
    val startProcessingAt: Instant,
) : ConsumerRecordProcessor {
    val log = logger()
    val teamLog = teamLogger()

    /**
     * `coerceInputValues` gjør at en ukjent `SAKSTATUSKODE` faller tilbake til null i stedet for å
     * felle consumeren. Vi tar ingen beslutninger på feltet.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun processRecord(record: ConsumerRecord<String?, String?>) {
        val recordTidspunkt = Instant.ofEpochMilli(record.timestamp())
        if (recordTidspunkt.isBefore(startProcessingAt)) {
            log.info("Mottok kakfa melding ${recordTidspunkt}, men vi starter å prosessere melding den $startProcessingAt")
            return
        }

        val value = record.value()
        if (value == null) {
            log.debug("skipping tombstone record")
            return
        }

        val kafkaMelding = try {
            json.decodeFromString<TiltakssakEndretKafkaMelding>(value)
        } catch (e: Exception) {
            teamLog.error("Kunne ikke parse TiltakssakEndretKafkaMelding. record: {}", record.toString())
            throw Exception("Kunne ikke parse TiltakssakEndretKafkaMelding. key: ${record.key()}", e)
        }

        val endring = kafkaMelding.after
        when {
            // kun oppdateringer er interessante. I er saksopprettelsen, D har ingen after.
            !kafkaMelding.erOppdatering -> return
            endring == null -> return
            !endring.erTiltakssak -> return
            !endring.erTattAvSaksbehandler -> return
        }

        // NB: skal logging av meldingsinnhold legges inn igjen, må den stå *etter* oppslaget mot
        // arena_sak. Forfiltrene over er ikke selektive — SAKSKODE er "TILT" for alle meldinger på
        // dette topicet, og ansvarlig-regelen slår til for enhver tiltakssak som er tatt til
        // behandling i Arena. Logget før oppslaget ville vi skrevet en saksbehandlerident per
        // tiltakssak i hele Arena, ikke bare våre egne.
        transaction(database) {
            hentArenaSakBySaksnummer(endring.saksnummer) {
                Json.decodeFromString<DTO.Soknad>(this[ArenaSakTable.soknad])
            }?.let { soknad ->
                val ikkeTidligereBehandlet = markerTiltakssakEndretMeldingSomBehandlet(endring.sakId)
                if (ikkeTidligereBehandlet) {
                    EventQueue.publishInTx(
                        EventData.SaksbehandlingStartetIArena(
                            soknad = soknad,
                            tiltakssakEndret = endring,
                        ),
                    )
                }
            }
        }
    }

    suspend fun startProcessing() {
        consumer.consume(this)
    }

    companion object {
        /**
         * Feltdokumentasjon:
         * https://confluence.adeo.no/spaces/ARENA/pages/478256186/Arena+-+Tjeneste+Kafka+-+Tiltakssak
         *
         * Topic:
         * https://github.com/navikt/arena-iac/tree/main/kafka-aiven/aapen-arena-tiltakssakendret-v1
         */
        val TOPIC = basedOnEnv(
            dev = "teamarenanais.aapen-arena-tiltakssakendret-v1-q2",
            other = "teamarenanais.aapen-arena-tiltakssakendret-v1-p",
        )

        val kafkaConfig = KafkaConsumerConfig(
            groupId = "fager.ekspertbistand.tiltakssakendret",
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
data class TiltakssakEndretKafkaMelding(
    @SerialName("op_type")
    val opType: String? = null,
    val table: String? = null,
    @SerialName("op_ts")
    val opTs: String? = null,
    /**
     * Beholdt for å dokumentere konvoluttformatet, men brukes ikke. Deteksjonsregelen er validert i
     * dev og er en ren tilstandssjekk på [after] — se [TiltakssakEndret.erTattAvSaksbehandler].
     * En before/after-diff ville ikke fanget saker som ble tatt til behandling før vi begynte å
     * konsumere topicet, og ville brutt under backfill.
     */
    val before: TiltakssakEndret? = null,
    val after: TiltakssakEndret? = null,
) {
    val erOppdatering: Boolean get() = opType == "U"
}

@Serializable
data class TiltakssakEndret(
    @SerialName("SAK_ID")
    val sakId: Int,
    @SerialName("SAKSKODE")
    val sakskode: String? = null,
    @SerialName("AAR")
    val aar: Int,
    @SerialName("LOPENRSAK")
    val lopenrsak: Int,
    @SerialName("SAKSTATUSKODE")
    val sakstatuskode: Sakstatuskode? = null,
    @SerialName("BRUKERID_ANSVARLIG")
    val brukeridAnsvarlig: String? = null,
    @SerialName("AETATENHET_ANSVARLIG")
    val aetatenhetAnsvarlig: String? = null,
    @SerialName("MOD_USER")
    val modUser: String? = null,
    @SerialName("MOD_DATO")
    val modDato: String? = null,
) {
    val saksnummer: Saksnummer get() = asSaksnummer(aar = aar, loepenrSak = lopenrsak)

    /** SAKSKODE er alltid TILT på dette topicet, men vi verifiserer for sikkerhets skyld. */
    val erTiltakssak: Boolean get() = sakskode == "TILT"

    /**
     * BRUKERID_ANSVARLIG er saksbehandlerens Arena-ident, AETATENHET_ANSVARLIG er Nav-enheten.
     * Ved opprettelse via Tiltaksgjennomfoering API settes behandlende enhet som tiltaksansvarlig,
     * slik at begge feltene har samme verdi. Når en saksbehandler tar saken settes
     * BRUKERID_ANSVARLIG til vedkommendes brukerident, og feltene divergerer.
     */
    val erTattAvSaksbehandler: Boolean
        get() = !brukeridAnsvarlig.isNullOrBlank() &&
                brukeridAnsvarlig != aetatenhetAnsvarlig

    enum class Sakstatuskode {
        AKTIV,   // Aktiv
        AVSLU,   // Lukket
        HIST,    // Historisert
        INAKT,   // Inaktiv
        OPRTV,   // Opprettet (RTV)
    }
}
