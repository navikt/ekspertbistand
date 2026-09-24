package no.nav.ekspertbistand.oebs.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Status/kvittering fra OeBS, mottatt via Team VALP sine status-topics. Holder **siste** status per
 * bestilling og bærer flagg for manuell oppfølging ved feilede bestillinger/utbetalinger, slik at en
 * feilet betaling gir et tydelig signal for manuell oppfølging (jf. spec-avklaring A-8).
 *
 * Dette er **arbeidsdata** (siste tilstand, overskrives) — til forskjell fra revisjonssporet i
 * [OebsMottattStatus] som er append-only og aldri overskrives.
 */
object OebsBestillingStatus : Table("oebs_bestilling_status") {
    val bestillingsnummer = text("bestillingsnummer")
    val status = text("status")
    val trengerManuellOppfolging = bool("trenger_manuell_oppfolging").default(false)
    @OptIn(kotlin.time.ExperimentalTime::class)
    val mottattTidspunkt = timestamp("mottatt_tidspunkt").defaultExpression(CurrentTimestamp)
    val rawJson = jsonb<JsonObject>("raw_json", Json)

    override val primaryKey = PrimaryKey(bestillingsnummer)
}
