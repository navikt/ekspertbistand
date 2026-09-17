@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs.integration

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.oebs.model.FAGSYSTEM_KILDE
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Lokal speiling av Team VALP sine status-DTO-er for tiltaksøkonomi/OeBS.
 *
 * Kilde: navikt/mulighetsrommet
 * (common/tiltaksokonomi-client — [BestillingStatus] og [FakturaStatus]).
 *
 * Vi eier ikke denne kontrakten, men konsumerer statusmeldingene VALP publiserer. Modellen speiles
 * lokalt (ikke som avhengighet, jf. spec-beslutning 8) med samme feltnavn og enum-verdier, slik at
 * [TiltaksokonomiConsumer] kan deserialisere til sterkt typede modeller i stedet for å lese løse
 * felter ut av en `JsonObject`.
 *
 * ⚠️ KONTRAKT: Wire-formatet (feltnavn, enum-navn og serialisering av [Instant]) MÅ matche VALP
 * eksakt. VALP serialiserer `Instant` som ISO-8601-streng (`InstantSerializer` →
 * `java.time.Instant.parse`), som er wire-kompatibelt med `kotlin.time.Instant` sin default-
 * serialisering.
 */

/** Status/kvittering for en bestilling, mottatt på `tiltaksokonomi.bestilling-status-v1`. */
@Serializable
data class BestillingStatus(
    val bestillingsnummer: String,
    val status: BestillingStatusType,
)

enum class BestillingStatusType {
    /** Sendt til OeBS, venter på kvittering. */
    SENDT,

    /** OK kvittering fra OeBS. */
    AKTIV,

    /** Mottatt kvittering på annullering fra OeBS. */
    ANNULLERT,

    /** Sendt annullering til OeBS. */
    ANNULLERING_SENDT,

    OPPGJORT,

    /** Krever manuell oppfølging. */
    FEILET,
}

/** Status/kvittering for en faktura, mottatt på `tiltaksokonomi.faktura-status-v1`. */
@Serializable
data class FakturaStatus(
    val fakturanummer: String,
    val status: FakturaStatusType,
    val fakturaStatusSistOppdatert: Instant,
)

enum class FakturaStatusType {
    /** Sendt til OeBS, ikke mottatt kvittering enda. */
    SENDT,

    /** Fakturaen er prosessert ok i OeBS, men ikke sendt til banken. */
    IKKE_BETALT,

    /** Noe av beløpet er sendt til banken (visstnok lite brukt). */
    DELVIS_BETALT,

    /** Betyr at hele beløpet er sendt til banken. */
    FULLT_BETALT,

    /** Krever manuell oppfølging. */
    FEILET,
}

/**
 * Consumerens sterkt typede representasjon av en innkommende statusmelding fra VALP. Wrapper de to
 * separate VALP-kontraktene ([BestillingStatus]/[FakturaStatus] — som ikke deler supertype hos VALP)
 * slik at [TiltaksokonomiConsumer] kan rute og tolke dem uniformt.
 *
 * [referanse] er nummeret meldingen gjelder (bestillings- eller fakturanummer). Begge har vår
 * fagsystembokstav ([FAGSYSTEM_KILDE]) som første tegn, så den brukes til å filtrere ut andre
 * kilders meldinger på de delte status-topicene.
 */
sealed interface OebsStatusMelding {
    val referanse: String

    data class Bestilling(val status: BestillingStatus) : OebsStatusMelding {
        override val referanse: String get() = status.bestillingsnummer
    }

    data class Faktura(val status: FakturaStatus) : OebsStatusMelding {
        override val referanse: String get() = status.fakturanummer
    }
}
