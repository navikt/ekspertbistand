package no.nav.ekspertbistand.oebs.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import no.nav.ekspertbistand.infrastruktur.isActiveAndNotTerminating
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.TiltaksokonomiProducer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.ForUpdate
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption.PostgreSQL.MODE.SKIP_LOCKED
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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
 * Poller som drenerer outbox-en til Kafka. Kjører som bakgrunnsprosess (se
 * [no.nav.ekspertbistand.oebs.OebsProcessor]) og speiler poll-mønsteret i
 * [no.nav.ekspertbistand.event.EventQueue].
 *
 * 🔴 **RØD SONE — økonomikritisk.** Transaksjonsgrensene under er selve garantien, ikke pynt.
 *
 * **At-least-once i én transaksjon.** For hver rad kjører alt innenfor **én** transaksjon:
 * 1. Hent neste PENDING-rad med `SELECT … FOR UPDATE SKIP_LOCKED` (eldste id først). Radlåsen holdes
 *    gjennom hele publiseringen, så to pod-er aldri publiserer samme rad samtidig — de hopper over
 *    (`SKIP_LOCKED`) og tar hver sin rad i stedet for å vente.
 * 2. Publiser via [TiltaksokonomiProducer.send], som blokkerer til `acks=all` og **kaster** ved feil.
 * 3. Først når publiseringen er bekreftet: skriv revisjonsspor ([loggSendtMelding] med topic/partition/
 *    offset fra `RecordMetadata`) og marker raden PUBLISHED — i **samme** transaksjon som låsen og
 *    publiseringen.
 *
 * Rekkefølgen gir garantien: vi markerer aldri PUBLISHED før meldingen faktisk er ute, og
 * revisjonssporet stemmer alltid med det som ble publisert. Krasjer vi mellom vellykket publisering
 * og commit, ruller transaksjonen tilbake og raden forblir PENDING → den republiseres. Duplikatet er
 * ufarlig: produsenten er idempotent (`enable.idempotence=true`) og OeBS deduplikerer på
 * bestillings-/fakturanummer. Det er det bevisste valget bak **at-least-once** framfor at-most-once —
 * vi tåler duplikat, men aldri tap.
 *
 * **Feilhåndtering.** Er Kafka nede kaster [TiltaksokonomiProducer.send] en Kafka-exception (ikke en
 * `SQLException`), så Exposed re-kjører ikke blokken og vi republiserer ikke ved en halv-feilet
 * transaksjon. Transaksjonen ruller tilbake (ingen halvskrevet revisjonslogg), raden forblir PENDING,
 * og vi venter [feilBackoff] før neste forsøk i stedet for å blø feilen innover i forretningslogikken.
 */
class OebsOutboxPoller(
    private val database: Database,
    private val producer: TiltaksokonomiProducer,
    private val pollInterval: Duration = 5.seconds,
    private val feilBackoff: Duration = 30.seconds,
) {
    private val log = logger()

    /**
     * Kjører til applikasjonen skrur seg av ([isActiveAndNotTerminating]). Drenerer én rad om gangen:
     * ved tom kø venter vi [pollInterval], ved publiseringsfeil [feilBackoff], ellers fortsetter vi
     * umiddelbart til neste rad så en opphopning tømmes raskt.
     */
    suspend fun startProcessing() = withContext(Dispatchers.IO) {
        while (isActiveAndNotTerminating) {
            val publiserte = try {
                drenerNestePending()
            } catch (e: Exception) {
                log.error("Publisering av outbox-melding feilet; raden forblir PENDING og prøves igjen.", e)
                delay(feilBackoff)
                continue
            }

            if (!publiserte) {
                delay(pollInterval)
            }
        }
    }

    /**
     * Publiserer neste PENDING-rad i én transaksjon (lås → publiser → logg + marker PUBLISHED).
     * Returnerer `true` hvis en rad ble publisert, `false` hvis køen var tom.
     */
    @OptIn(ExperimentalTime::class)
    private fun drenerNestePending(): Boolean = transaction(database) {
        val rad = OebsOutbox
            .selectAll()
            .where { OebsOutbox.status eq OutboxStatus.PENDING }
            .orderBy(OebsOutbox.id, SortOrder.ASC)
            .limit(1)
            .forUpdate(ForUpdate(SKIP_LOCKED))
            .firstOrNull()
            ?: return@transaction false

        val id = rad[OebsOutbox.id]
        val melding = rad[OebsOutbox.meldingJson]
        val value = OebsBestillingMelding.json.encodeToString(melding)

        // Blokkerer til acks=all; kaster ved feil → hele transaksjonen ruller tilbake, raden forblir PENDING.
        val metadata = producer.send(rad[OebsOutbox.bestillingsnummer], value)

        loggSendtMelding(melding, metadata.topic(), metadata.partition(), metadata.offset())
        OebsOutbox.update({ OebsOutbox.id eq id }) {
            it[status] = OutboxStatus.PUBLISHED
            it[attempts] = rad[attempts] + 1
            it[updatedAt] = CurrentTimestamp
        }
        true
    }
}
