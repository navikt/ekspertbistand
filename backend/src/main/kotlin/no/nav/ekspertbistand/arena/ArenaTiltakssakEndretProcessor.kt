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

        if (!kafkaMelding.erOppdatering) {
            // kun oppdateringer er interessante. I er saksopprettelsen, D har ingen after.
            return
        }

        val endring = kafkaMelding.after
        if (endring == null) {
            log.info(
                "Melding uten after (op_type={}) ignoreres for tiltakssak. key={}",
                kafkaMelding.opType,
                record.key()
            )
            return
        }

        if (!endring.erTiltakssak) {
            return
        }

        if (!endring.erTattAvSaksbehandler) {
            return
        }

        // sjekk at vi er kilde til saken, saksnummer finnes i vårt system
        val soknad = transaction(database) {
            hentArenaSakBySaksnummer(endring.saksnummer) {
                Json.decodeFromString<DTO.Soknad>(this[ArenaSakTable.soknad])
            }
        }

        if (soknad == null) {
            // sak i Arena vi ikke er kilde til: sendt inn via Altinn 2, eller opprettet direkte i Arena
            log.info("Tiltakssak tatt til behandling i Arena som vi ikke er kilde til, sakId=${endring.sakId}")
            return
        }

        // NB: loggingen står bevisst *etter* oppslaget mot arena_sak. Forfiltrene over er ikke
        // selektive — SAKSKODE er "TILT" for alle meldinger på dette topicet, og ansvarlig-regelen
        // slår til for enhver tiltakssak som er tatt til behandling i Arena. Logget før oppslaget
        // ville hver oppstart med startProcessingAt = EPOCH skrive en saksbehandlerident per
        // tiltakssak i hele Arena, ikke bare våre egne.
        if (NaisEnvironment.clusterName == "dev-gcp") {
            // TODO: fjern denne loggingen etter debug i dev. Gjerne før prodsetting
            log.info("TiltakssakEndret gjelder vår sak. TiltakssakEndretKafkaMelding. {}", kafkaMelding)
        }

        // Datagrunnlag for å bekrefte at tilstandsregelen sammenfaller med en reell endring av feltet,
        // slik at vi senere kan vurdere å stramme inn til en ren before/after-diff.
        teamLog.info(
            "TiltakssakEndret gjelder vår sak. sakId={} saksnummer={} beforeBrukeridAnsvarlig={} afterBrukeridAnsvarlig={} aetatenhetAnsvarlig={} brukeridEndret={} brukeridLiknerSaksbehandlerIdent={}",
            endring.sakId,
            endring.saksnummer,
            kafkaMelding.before?.brukeridAnsvarlig,
            endring.brukeridAnsvarlig,
            endring.aetatenhetAnsvarlig,
            kafkaMelding.before?.brukeridAnsvarlig != endring.brukeridAnsvarlig,
            endring.brukeridLiknerSaksbehandlerIdent,
        )

        val event = EventData.SaksbehandlingStartetIArena(
            soknad = soknad,
            tiltakssakEndret = endring,
        )
        transaction(database) {
            val ikkeTidligereBehandlet = markerTiltakssakEndretMeldingSomBehandlet(endring.sakId)
            if (ikkeTidligereBehandlet) {
                EventQueue.publish(event)
            } else {
                log.info("TiltakssakEndret melding for sakId=${endring.sakId} er allerede behandlet, hopper over.")
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
            // Midlertidig: consumer group er ny og har ingen committede offsets, og vi ønsker backfill
            // av saker som allerede er tatt til behandling i Arena.
            // TODO(#117): sett til AutoOffsetReset.NONE når consumeren er etablert i prod.
            autoOffsetReset = AutoOffsetReset.EARLIEST,
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

    /**
     * Kun for logging/verifisering — ikke beslutningsgrunnlag.
     *
     * Arena-identer, ikke NAV-identer. Observerte verdier i dev: KG0219, KGB0219 — varierende antall
     * bokstaver etterfulgt av siffer. Ikke lås antallet i noen av delene.
     *
     * Det som faktisk skiller er at en Nav-enhet er rent numerisk (1899) mens en Arena-ident
     * inneholder bokstaver.
     */
    val brukeridLiknerSaksbehandlerIdent: Boolean
        get() = brukeridAnsvarlig?.matches(Regex("^[A-ZÆØÅ]+\\d+$", RegexOption.IGNORE_CASE)) == true

    enum class Sakstatuskode {
        AKTIV,   // Aktiv
        AVSLU,   // Lukket
        HIST,    // Historisert
        INAKT,   // Inaktiv
        OPRTV,   // Opprettet (RTV)
    }
}
