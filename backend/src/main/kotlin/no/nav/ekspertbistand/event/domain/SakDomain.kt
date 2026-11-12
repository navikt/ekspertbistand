package no.nav.ekspertbistand.event.domain

import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Saken slik den var da vedtaket ble fattet, med vilkårsvurderingene. Felt som avhenger av hvem som ser saken,
 * er ikke med. Lages med [hentSakDomain].
 *
 * Har identer, navn og notater fra vilkårsvurderingen, og skal ikke logges.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class SakDomain(
    val sakId: String,
    val status: Status,
    val kildeTilBehandling: KildeTilBehandling,
    val behandlendeEnhet: String,
    val behandlendeEnhetNavn: String?,
    val saksbehandlerIdent: String?,
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val beslutterNavn: String?,
    val adressebeskyttelse: Adressebeskyttelse?,
    val skjermet: Boolean,
    val vilkarsvurderinger: List<Vilkarsvurdering>,
) {
    @Serializable
    enum class Status { OPPRETTET, UNDER_BEHANDLING, TIL_BESLUTNING, INNVILGET, AVSLATT, AVSLUTTET }

    @Serializable
    enum class KildeTilBehandling { EKSPERTBISTAND, ARENA }

    @Serializable
    enum class Adressebeskyttelse { FORTROLIG, STRENGT_FORTROLIG }

    @Serializable
    data class Vilkarsvurdering(
        val vilkar: Vilkar,
        val godkjent: Boolean?,
        val notat: String?,
        val vurdertAvIdent: String?,
        val vurdertTidspunkt: Instant?,
    ) {
        @Serializable
        enum class Vilkar {
            DELTAKER_HAR_ARBEIDSFORHOLD,
            FYLLES_UT_I_SAMRAD_GODKJENT,
            ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING,
            DELTAKER_HAR_SYKEFRAVAERSHISTORIKK,
            EKSPERT_HAR_KOMPETANSE,
        }
    }
}
