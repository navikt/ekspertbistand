package no.nav.ekspertbistand.event.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Det foreløpige vedtaket beslutter godkjente. Beløp og periode er null ved [Utfall.AVSLAA].
 *
 * Begrunnelsene kan ha helseopplysninger, og [innstiltAvIdent] er en ident. Skal ikke logges.
 */
@OptIn(ExperimentalTime::class)
@Serializable
data class VedtakDomain(
    val versjon: Int,
    val utfall: Utfall,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
    val begrunnelseDeltaker: String,
    val begrunnelseArbeidsgiver: String?,
    val innstiltAvIdent: String,
    val sendtTidspunkt: Instant,
) {
    @Serializable
    enum class Utfall { INNVILGE, INNVILGE_REDUSERT, AVSLAA }
}
