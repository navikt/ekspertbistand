@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Status på en bestilling (tilsagn) i OeBS, slik VALP meldte den. */
@Serializable
data class OebsBestillingStatusDomain(
    val bestillingsnummer: String,
    val status: Status,
    val statusSistOppdatert: Instant,
    val feilKode: String?,
    val feilMelding: String?,
    val kvittering: OebsKvitteringDomain,
) {
    @Serializable
    enum class Status { SENDT, AKTIV, ANNULLERING_SENDT, ANNULLERT, OPPGJORT, FEILET }
}

/** Status på en faktura (utbetaling) i OeBS, slik VALP meldte den. */
@Serializable
data class OebsFakturaStatusDomain(
    val fakturanummer: String,
    val bestillingsnummer: String,
    val status: Status,
    val statusSistOppdatert: Instant,
    val feilKode: String?,
    val feilMelding: String?,
    val kvittering: OebsKvitteringDomain,
) {
    @Serializable
    enum class Status { SENDT, IKKE_BETALT, DELVIS_BETALT, FULLT_BETALT, FEILET }
}

/** Hvor statusen kom fra. Peker på raden i revisjonssporet `oebs_mottatt_status`. */
@Serializable
data class OebsKvitteringDomain(
    val kafkaTopic: String,
    val kafkaPartition: Int,
    val kafkaOffset: Long,
    val mottattTidspunkt: Instant,
)
