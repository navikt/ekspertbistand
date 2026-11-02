package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.refusjon.refusjonsfrist
import no.nav.ekspertbistand.event.domain.SakDomain
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hvem et vedtaksbrev er til. Hvert utfall har én mal per mottaker, se [vedtaksbrevMal].
 * Se `specifications/forelopig_vedtak_brev.md`.
 */
enum class Mottaker {
    DELTAKER,
    ARBEIDSGIVER;

    val filnavn: String get() = "forelopig-vedtak-${name.lowercase()}.pdf"

    companion object {
        fun fra(verdi: String?): Mottaker? = entries.find { it.name.equals(verdi, ignoreCase = true) }
    }
}

/**
 * Alt et vedtaksbrev kan vise. Verdiene er ferdig formatert, så malene ikke trenger helpers.
 * Fødselsnummer, kontaktperson og ekspert er bevisst ikke med.
 */
@Serializable
data class VedtaksbrevData(
    val dato: String,
    val deltaker: Deltaker,
    val arbeidsgiver: Arbeidsgiver,
    val soknadssum: String?,
    val innvilgetBelop: String?,
    val tiltaksperiode: Periode?,
    val refusjonsfrist: String?,
    /** Begrunnelsen til mottakeren av brevet. Null for arbeidsgiver ved innvilgelse. */
    val begrunnelse: String?,
    val navEnhet: String?,
    val saksbehandler: String?,
) {
    @Serializable
    data class Deltaker(val navn: String)

    @Serializable
    data class Arbeidsgiver(val navn: String, val orgnr: String)

    @Serializable
    data class Periode(val fra: String, val til: String)
}

fun vedtaksbrevMal(utfall: ForelopigUtfall, mottaker: Mottaker): String = when (mottaker) {
    Mottaker.DELTAKER -> when (utfall) {
        ForelopigUtfall.INNVILGE -> "vedtakDeltakerInnvilget"
        ForelopigUtfall.INNVILGE_REDUSERT -> "vedtakDeltakerInnvilgetRedusert"
        ForelopigUtfall.AVSLAA -> "vedtakDeltakerAvslag"
    }

    Mottaker.ARBEIDSGIVER -> when (utfall) {
        ForelopigUtfall.INNVILGE -> "vedtakArbeidsgiverInnvilget"
        ForelopigUtfall.INNVILGE_REDUSERT -> "vedtakArbeidsgiverInnvilgetRedusert"
        ForelopigUtfall.AVSLAA -> "vedtakArbeidsgiverAvslag"
    }
}

/**
 * Bygger payloaden til brevet for [mottaker]. Bare begrunnelsen til [mottaker] kommer med.
 * Ved [ForelopigUtfall.INNVILGE] er beløpet [soknadssum], som i [publiserForelopigVedtak].
 * Tar verdiene hver for seg, så brevet ikke avhenger av hvor søknaden kommer fra.
 */
fun vedtaksbrevData(
    deltakerNavn: String,
    arbeidsgiver: VedtaksbrevData.Arbeidsgiver,
    soknadssum: Int?,
    utfall: ForelopigUtfall,
    belop: Int?,
    begrunnelseDeltaker: String,
    begrunnelseArbeidsgiver: String?,
    tiltaksperiodeFra: LocalDate?,
    tiltaksperiodeTil: LocalDate?,
    mottaker: Mottaker,
    navEnhet: String?,
    saksbehandler: String?,
    dato: LocalDate,
): VedtaksbrevData {
    val innvilgetBelop = when (utfall) {
        ForelopigUtfall.INNVILGE -> soknadssum
        ForelopigUtfall.INNVILGE_REDUSERT -> belop
        ForelopigUtfall.AVSLAA -> null
    }
    val periode = if (tiltaksperiodeFra != null && tiltaksperiodeTil != null) {
        VedtaksbrevData.Periode(fra = tiltaksperiodeFra.somBrevdato(), til = tiltaksperiodeTil.somBrevdato())
    } else {
        null
    }
    return VedtaksbrevData(
        dato = dato.somBrevdato(),
        deltaker = VedtaksbrevData.Deltaker(navn = deltakerNavn),
        arbeidsgiver = arbeidsgiver,
        soknadssum = soknadssum?.somBelop(),
        innvilgetBelop = innvilgetBelop?.somBelop(),
        tiltaksperiode = periode,
        refusjonsfrist = tiltaksperiodeTil?.takeIf { utfall != ForelopigUtfall.AVSLAA }
            ?.let { refusjonsfrist(it).somBrevdato() },
        begrunnelse = when (mottaker) {
            Mottaker.DELTAKER -> begrunnelseDeltaker
            Mottaker.ARBEIDSGIVER -> begrunnelseArbeidsgiver
        },
        navEnhet = navEnhet,
        saksbehandler = saksbehandler,
    )
}

/** Søknadssummen er fritekst fra arbeidsgiver. Null når den ikke er et positivt heltall. */
fun tolkSoknadssum(estimertKostnad: String): Int? = estimertKostnad.trim().toIntOrNull()?.takeIf { it > 0 }

/**
 * Navnet til saksbehandler i brevet, bare når det er saksbehandleren som innstilte som fortsatt har saken.
 * Ellers viser hilsenen bare enheten.
 */
fun saksbehandlerForBrev(saksbehandlerIdent: String?, saksbehandlerNavn: String?, innstiltAvIdent: String): String? =
    saksbehandlerNavn.takeIf { saksbehandlerIdent == innstiltAvIdent }

/** Enhetsnavnet finnes bare hos innlogget, som har bestått [sjekkTilgangTilEnhet] for enheten. */
fun AzureAdPrincipal.enhetsnavn(enhetsnummer: String?): String? =
    enheter.find { it.enhetnummer == enhetsnummer }?.navn

const val SLADDET_NAVN = "***********"

/**
 * Sladder deltakers navn ved kode 6 og 7, som Team Tiltak gjør i tilsagnsbrevet. Brukes bare på brevet som
 * journalføres: arbeidsgivermappa ligger på orgnr, så tilgangskontrollen på person i Gosys og Joark beskytter
 * ikke deltakeren der. Arbeidsgiver får brevet usladdet. Skjerming alene gir ingen sladding.
 */
fun VedtaksbrevData.sladdVedAdressebeskyttelse(adressebeskyttelse: SakDomain.Adressebeskyttelse?): VedtaksbrevData =
    when (adressebeskyttelse) {
        SakDomain.Adressebeskyttelse.FORTROLIG,
        SakDomain.Adressebeskyttelse.STRENGT_FORTROLIG -> copy(deltaker = VedtaksbrevData.Deltaker(SLADDET_NAVN))
        null -> this
    }

private val brevdatoFormat = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale.forLanguageTag("nb-NO"))

internal fun LocalDate.somBrevdato(): String = toJavaLocalDate().format(brevdatoFormat)

/** Hardt mellomrom som tusenskille, så beløpet ikke brytes over to linjer. */
internal fun Int.somBelop(): String = String.format(Locale.ROOT, "%,d", this).replace(',', '\u00A0')
