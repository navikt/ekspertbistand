@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs.integration

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Lokal speiling av Team VALP sin melding for tiltaksøkonomi/OeBS.
 *
 * Kilde: navikt/mulighetsrommet
 * (common/tiltaksokonomi-client — OkonomiBestillingMelding).
 *
 * Vi eier ikke denne kontrakten, men bygger meldinger som Team VALP konsumerer. Modellen speiles
 * lokalt (ikke som avhengighet, jf. spec-beslutning 8) med samme `@SerialName`-diskriminatorer og
 * feltnavn.
 *
 * ⚠️ KONTRAKT: Wire-formatet (feltnavn, diskriminator og serialisering av verdityper som
 * [Periode]) MÅ matche VALP eksakt. Verifisert mot faktiske VALP-eksempelmeldinger i
 * `OebsBestillingMeldingContractTest`.
 */
@Serializable
sealed class OebsBestillingMelding {

    @Serializable
    @SerialName("BESTILLING")
    data class Bestilling(val payload: OpprettBestilling) : OebsBestillingMelding()

    @Serializable
    @SerialName("ANNULLERING")
    data class Annullering(val payload: AnnullerBestilling) : OebsBestillingMelding()

    @Serializable
    @SerialName("FAKTURA")
    data class Faktura(val payload: OpprettFaktura) : OebsBestillingMelding()

    @Serializable
    @SerialName("GJOR_OPP_BESTILLING")
    data class GjorOppBestilling(val payload: no.nav.ekspertbistand.oebs.integration.GjorOppBestilling) :
        OebsBestillingMelding()

    companion object {
        /**
         * JSON-instans for (de)serialisering av meldinger på wire og i outbox-kolonnen.
         * Default `classDiscriminator = "type"` matcher VALP sin bruk av kotlinx.serialization.
         */
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
data class OpprettBestilling(
    val bestillingsnummer: String,
    val tilskuddstype: Tilskuddstype,
    val tiltakskode: Tiltakskode,
    val arrangor: Arrangor,
    val kostnadssted: NavEnhetNummer,
    val avtalenummer: String?,
    val belop: Int,
    val periode: Periode,
    val behandletAv: OkonomiPart,
    val behandletTidspunkt: Instant,
    val besluttetAv: OkonomiPart,
    val besluttetTidspunkt: Instant,
    val valuta: Valuta,
) {
    @Serializable
    sealed class Arrangor {
        abstract val organisasjonsnummer: Organisasjonsnummer

        @Serializable
        @SerialName("no.nav.tiltak.okonomi.OpprettBestilling.Arrangor.Utenlandsk")
        data class Utenlandsk(
            override val organisasjonsnummer: Organisasjonsnummer,
            val navn: String,
            val gateNavn: String,
            val by: String,
            val postNummer: String,
            val landKode: String,
        ) : Arrangor()

        @Serializable
        @SerialName("no.nav.tiltak.okonomi.OpprettBestilling.Arrangor.Norsk")
        data class Norsk(
            override val organisasjonsnummer: Organisasjonsnummer,
        ) : Arrangor()
    }
}

@Serializable
data class AnnullerBestilling(
    val bestillingsnummer: String,
    val behandletAv: OkonomiPart,
    val behandletTidspunkt: Instant,
    val besluttetAv: OkonomiPart,
    val besluttetTidspunkt: Instant,
)

@Serializable
data class GjorOppBestilling(
    val bestillingsnummer: String,
    val behandletAv: OkonomiPart,
    val behandletTidspunkt: Instant,
    val besluttetAv: OkonomiPart,
    val besluttetTidspunkt: Instant,
)

@Serializable
data class OpprettFaktura(
    val fakturanummer: String,
    val bestillingsnummer: String,
    val betalingsinformasjon: Betalingsinformasjon,
    val belop: Int,
    val periode: Periode,
    val behandletAv: OkonomiPart,
    val behandletTidspunkt: Instant,
    val besluttetAv: OkonomiPart,
    val besluttetTidspunkt: Instant,
    val gjorOppBestilling: Boolean,
    val beskrivelse: String?,
    val valuta: Valuta,
) {
    @Serializable
    sealed class Betalingsinformasjon {
        @Serializable
        @SerialName("no.nav.tiltak.okonomi.OpprettFaktura.Betalingsinformasjon.BBan")
        data class BBan(
            val kontonummer: Kontonummer,
            val kid: Kid?,
        ) : Betalingsinformasjon()

        @Serializable
        @SerialName("no.nav.tiltak.okonomi.OpprettFaktura.Betalingsinformasjon.IBan")
        data class IBan(
            val bic: String,
            val iban: String,
            val bankNavn: String,
            val bankLandKode: String,
        ) : Betalingsinformasjon()
    }
}

/**
 * `part` er en konstruktør-property på sealed-baseklassen og serialiseres derfor som eget felt ved
 * siden av subklassens felter — det er derfor VALP-meldinger har både `part` og `navIdent`. Vår
 * speiling ligger i en annen pakke enn VALP, så diskriminatorene må settes eksplisitt til VALP sine
 * fullkvalifiserte navn (`no.nav.tiltak.okonomi...`), som er default hos VALP (ingen `@SerialName`).
 */
@Serializable
sealed class OkonomiPart(val part: String) {

    @Serializable
    @SerialName("no.nav.tiltak.okonomi.OkonomiPart.NavAnsatt")
    data class NavAnsatt(val navIdent: String) : OkonomiPart(navIdent)

    @Serializable
    @SerialName("no.nav.tiltak.okonomi.OkonomiPart.System")
    data class System(val kilde: OkonomiSystem) : OkonomiPart(kilde.name)
}

/**
 * Kildesystem slik OeBS/VALP kjenner det. Ekspertbistand er et eget kildesystem; verdien må være
 * lagt inn hos Team VALP før meldinger godtas.
 */
enum class OkonomiSystem {
    EKSPERTBISTAND,
}

/**
 * ⚠️ [TILTAK_EKSPERTBISTAND] finnes ikke hos VALP i dag og legges inn av Team VALP. Vi sender kun
 * denne verdien.
 */
enum class Tilskuddstype {
    TILTAK_EKSPERTBISTAND,
}

/**
 * ⚠️ [EKSPERTBISTAND] finnes ikke i VALP sin `Tiltakskode`-enum i dag og legges inn av Team VALP.
 */
enum class Tiltakskode {
    EKSPERTBISTAND,
}

enum class Valuta {
    NOK,
}

/** Periode `[start, slutt)` — slutt er eksklusiv, jf. VALP. */
@Serializable
data class Periode(
    val start: LocalDate,
    val slutt: LocalDate,
)

@JvmInline
@Serializable
value class Organisasjonsnummer(val value: String)

@JvmInline
@Serializable
value class NavEnhetNummer(val value: String)

@JvmInline
@Serializable
value class Kontonummer(val value: String)

@JvmInline
@Serializable
value class Kid(val value: String)
