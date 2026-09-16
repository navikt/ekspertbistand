package no.nav.ekspertbistand.oebs.model

import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiProducer
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Outbox for utgående tiltaksøkonomi-meldinger.
 *
 * Poenget med mønsteret: kallere skriver meldingen til denne tabellen i **samme transaksjon** som
 * sin egen forretningsendring ([leggIOutbox] finnes kun på [JdbcTransaction], så skriving uten
 * transaksjon er en kompileringsfeil). En bakgrunnspoller publiserer radene til Kafka etterpå.
 * Slik har vi ingen hard avhengighet til Kafka-oppetid, og publiseringsfeil bæres ikke innover i
 * forretningslogikken — de blir liggende i outbox-en og prøves igjen.
 */
object OebsOutbox : Table("oebs_outbox") {
    val id = long("id").autoIncrement()
    val bestillingsnummer = text("bestillingsnummer")
    val meldingstype = text("meldingstype")
    val meldingJson = jsonb<OebsBestillingMelding>("melding_json", OebsBestillingMelding.json)
    val status = enumerationByName<OutboxStatus>("status", 32).default(OutboxStatus.PENDING)
    val attempts = integer("attempts").default(0)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

enum class OutboxStatus {
    PENDING,
    PUBLISHED,
}

/**
 * Legger meldingen i outbox-en i kallerens pågående transaksjon — commiter og rulles tilbake med
 * den. Dette er eneste vei inn i outbox-en; skriv aldri til [OebsOutbox] direkte.
 */
fun JdbcTransaction.leggIOutbox(melding: OebsBestillingMelding) {
    OebsOutbox.insert {
        it[bestillingsnummer] = melding.bestillingsnummer
        it[meldingstype] = melding.meldingstype
        it[meldingJson] = melding
    }
}

/** Kafka record key — ordering per bestilling. */
val OebsBestillingMelding.bestillingsnummer: String
    get() = when (this) {
        is OebsBestillingMelding.Bestilling -> payload.bestillingsnummer
        is OebsBestillingMelding.Annullering -> payload.bestillingsnummer
        is OebsBestillingMelding.Faktura -> payload.bestillingsnummer
        is OebsBestillingMelding.GjorOppBestilling -> payload.bestillingsnummer
    }

/** Diskriminator lagret i egen kolonne for enkel filtrering/observabilitet. */
val OebsBestillingMelding.meldingstype: String
    get() = when (this) {
        is OebsBestillingMelding.Bestilling -> "BESTILLING"
        is OebsBestillingMelding.Annullering -> "ANNULLERING"
        is OebsBestillingMelding.Faktura -> "FAKTURA"
        is OebsBestillingMelding.GjorOppBestilling -> "GJOR_OPP_BESTILLING"
    }

/**
 * 🔴 RØD SONE — skriv selv.
 *
 * Poller som drenerer outbox-en til Kafka. Kjernelogikken har harde krav:
 * - hent neste PENDING-rad med `FOR UPDATE SKIP_LOCKED` (se [no.nav.ekspertbistand.event.EventQueue]),
 * - publiser via [TiltaksokonomiProducer.send] (returnerer `RecordMetadata`),
 * - skriv revisjonsspor med [loggSendtMelding] (topic/partition/offset fra metadata) og marker
 *   PUBLISHED **i samme transaksjon** slik at vi får at-least-once uten å miste meldinger, og slik at
 *   revisjonssporet (etterlevelse) alltid stemmer med det som faktisk ble publisert,
 * - håndter retry/attempts og backoff ved feil, uten å blø feilen innover.
 *
 * Transaksjonsgrensene og at-least-once-garantien er sikkerhets-/økonomikritiske og skal
 * implementeres og forstås av teamet, ikke genereres. Ikke wiret inn i oppstart før dette er skrevet.
 */
class OebsOutboxPoller(
    @Suppress("unused") private val database: Database,
    @Suppress("unused") private val producer: TiltaksokonomiProducer,
) {
    suspend fun startProcessing(): Nothing =
        TODO("Rød sone: implementer outbox-poller (SKIP_LOCKED-poll → publiser → marker PUBLISHED i én transaksjon, med retry).")
}
