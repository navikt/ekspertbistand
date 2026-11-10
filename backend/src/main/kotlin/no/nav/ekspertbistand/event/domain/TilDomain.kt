package no.nav.ekspertbistand.event.domain

import no.nav.ekspertbistand.saksbehandling.Adressebeskyttelse
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ForelopigVedtakDTO
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.VedtaksbrevData
import no.nav.ekspertbistand.saksbehandling.Vilkar
import no.nav.ekspertbistand.saksbehandling.VilkarsvurderingDTO
import no.nav.ekspertbistand.saksbehandling.hentVilkarsvurdering
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Oversettelser fra API-DTO-er, API-enums og tabellrader til domain-typene. Dette er det eneste stedet som kjenner
 * begge modellene. Enum-oversettelsene er uttømmende `when` uten `else`, så en ny API-verdi ikke kompilerer før
 * noen har bestemt hva den blir i domain.
 */

/** Feiler for et utkast, og for en søknad med fritekst i `kompetanse` i stedet for listene. */
@OptIn(ExperimentalTime::class)
fun DTO.Soknad.tilDomain(): SoknadDomainV2 {
    val id = checkNotNull(id) { "Søknaden mangler id" }
    val opprettetAv = checkNotNull(opprettetAv) { "Søknad $id mangler opprettetAv" }
    val opprettetTidspunkt = checkNotNull(opprettetTidspunkt) { "Søknad $id mangler opprettetTidspunkt" }
    check(ekspert.godkjentUtdanningEllerAutorisasjon.isNotEmpty()) {
        "Søknad $id mangler godkjentUtdanningEllerAutorisasjon"
    }
    check(ekspert.relevantKompetanse.isNotEmpty()) { "Søknad $id mangler relevantKompetanse" }

    return SoknadDomainV2(
        id = id,
        virksomhet = SoknadDomainV2.Virksomhet(
            virksomhetsnummer = virksomhet.virksomhetsnummer,
            virksomhetsnavn = virksomhet.virksomhetsnavn,
            kontaktperson = SoknadDomainV2.Kontaktperson(
                navn = virksomhet.kontaktperson.navn,
                epost = virksomhet.kontaktperson.epost,
                telefonnummer = virksomhet.kontaktperson.telefonnummer,
            ),
            beliggenhetsadresse = virksomhet.beliggenhetsadresse,
        ),
        ansatt = SoknadDomainV2.Ansatt(fnr = ansatt.fnr, navn = ansatt.navn),
        ekspert = SoknadDomainV2.Ekspert(
            navn = ekspert.navn,
            virksomhet = ekspert.virksomhet,
            virksomhetNavn = ekspert.virksomhetNavn,
            virksomhetOrgnr = ekspert.virksomhetOrgnr,
            godkjentUtdanningEllerAutorisasjon = ekspert.godkjentUtdanningEllerAutorisasjon,
            relevantKompetanse = ekspert.relevantKompetanse,
        ),
        behovForBistand = SoknadDomainV2.BehovForBistand(
            begrunnelse = behovForBistand.begrunnelse,
            behov = behovForBistand.behov,
            estimertKostnad = behovForBistand.estimertKostnad,
            timer = behovForBistand.timer,
            tilrettelegging = behovForBistand.tilrettelegging,
            startdato = behovForBistand.startdato,
        ),
        nav = SoknadDomainV2.Nav(kontaktperson = nav.kontaktperson),
        opprettetAv = opprettetAv,
        opprettetTidspunkt = Instant.parse(opprettetTidspunkt),
        status = status.tilDomain(),
    )
}

fun SoknadStatus.tilDomain(): SoknadDomainV2.Status = when (this) {
    SoknadStatus.innsendt -> SoknadDomainV2.Status.innsendt
    SoknadStatus.godkjent -> SoknadDomainV2.Status.godkjent
    SoknadStatus.avlyst -> SoknadDomainV2.Status.avlyst
    SoknadStatus.avslatt -> SoknadDomainV2.Status.avslatt
    SoknadStatus.utkast -> error("Søknad med status utkast kan ikke bli SoknadDomainV2")
}

fun Saksstatus.tilDomain(): SakDomain.Status = when (this) {
    Saksstatus.OPPRETTET -> SakDomain.Status.OPPRETTET
    Saksstatus.UNDER_BEHANDLING -> SakDomain.Status.UNDER_BEHANDLING
    Saksstatus.TIL_BESLUTNING -> SakDomain.Status.TIL_BESLUTNING
    Saksstatus.INNVILGET -> SakDomain.Status.INNVILGET
    Saksstatus.AVSLATT -> SakDomain.Status.AVSLATT
    Saksstatus.AVSLUTTET -> SakDomain.Status.AVSLUTTET
}

fun KildeTilBehandling.tilDomain(): SakDomain.KildeTilBehandling = when (this) {
    KildeTilBehandling.EKSPERTBISTAND -> SakDomain.KildeTilBehandling.EKSPERTBISTAND
    KildeTilBehandling.ARENA -> SakDomain.KildeTilBehandling.ARENA
}

fun Adressebeskyttelse.tilDomain(): SakDomain.Adressebeskyttelse = when (this) {
    Adressebeskyttelse.FORTROLIG -> SakDomain.Adressebeskyttelse.FORTROLIG
    Adressebeskyttelse.STRENGT_FORTROLIG -> SakDomain.Adressebeskyttelse.STRENGT_FORTROLIG
}

fun Vilkar.tilDomain(): SakDomain.Vilkarsvurdering.Vilkar = when (this) {
    Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD -> SakDomain.Vilkarsvurdering.Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD
    Vilkar.FYLLES_UT_I_SAMRAD_GODKJENT -> SakDomain.Vilkarsvurdering.Vilkar.FYLLES_UT_I_SAMRAD_GODKJENT
    Vilkar.ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING ->
        SakDomain.Vilkarsvurdering.Vilkar.ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING
    Vilkar.DELTAKER_HAR_SYKEFRAVAERSHISTORIKK -> SakDomain.Vilkarsvurdering.Vilkar.DELTAKER_HAR_SYKEFRAVAERSHISTORIKK
    Vilkar.EKSPERT_HAR_KOMPETANSE -> SakDomain.Vilkarsvurdering.Vilkar.EKSPERT_HAR_KOMPETANSE
}

fun ForelopigUtfall.tilDomain(): VedtakDomain.Utfall = when (this) {
    ForelopigUtfall.INNVILGE -> VedtakDomain.Utfall.INNVILGE
    ForelopigUtfall.INNVILGE_REDUSERT -> VedtakDomain.Utfall.INNVILGE_REDUSERT
    ForelopigUtfall.AVSLAA -> VedtakDomain.Utfall.AVSLAA
}

/** Brevmalene velges med [ForelopigUtfall], så handlerne må tilbake fra domain for å lage brevet. */
fun VedtakDomain.Utfall.tilForelopigUtfall(): ForelopigUtfall = when (this) {
    VedtakDomain.Utfall.INNVILGE -> ForelopigUtfall.INNVILGE
    VedtakDomain.Utfall.INNVILGE_REDUSERT -> ForelopigUtfall.INNVILGE_REDUSERT
    VedtakDomain.Utfall.AVSLAA -> ForelopigUtfall.AVSLAA
}

/** Statusen saken får når vedtaket fattes. Samme svar som [no.nav.ekspertbistand.saksbehandling.saksstatus]. */
fun VedtakDomain.Utfall.tilSaksstatus(): Saksstatus = when (this) {
    VedtakDomain.Utfall.INNVILGE, VedtakDomain.Utfall.INNVILGE_REDUSERT -> Saksstatus.INNVILGET
    VedtakDomain.Utfall.AVSLAA -> Saksstatus.AVSLATT
}

@OptIn(ExperimentalTime::class)
fun ForelopigVedtakDTO.tilDomain() = VedtakDomain(
    versjon = versjon,
    utfall = utfall.tilDomain(),
    innvilgetBelop = innvilgetBelop,
    tiltaksperiodeFra = tiltaksperiodeFra,
    tiltaksperiodeTil = tiltaksperiodeTil,
    begrunnelseDeltaker = begrunnelseDeltaker,
    begrunnelseArbeidsgiver = begrunnelseArbeidsgiver,
    innstiltAvIdent = innstiltAvIdent,
    sendtTidspunkt = sendtTidspunkt,
)

fun VedtaksbrevData.tilDomain() = VedtaksbrevDomain(
    dato = dato,
    deltaker = VedtaksbrevDomain.Deltaker(navn = deltaker.navn),
    arbeidsgiver = VedtaksbrevDomain.Arbeidsgiver(navn = arbeidsgiver.navn, orgnr = arbeidsgiver.orgnr),
    soknadssum = soknadssum,
    innvilgetBelop = innvilgetBelop,
    tiltaksperiode = tiltaksperiode?.let { VedtaksbrevDomain.Periode(fra = it.fra, til = it.til) },
    refusjonsfrist = refusjonsfrist,
    begrunnelse = begrunnelse,
    navEnhet = navEnhet,
    saksbehandler = saksbehandler,
)

/** Malene tar [VedtaksbrevData], så brevet må tilbake fra domain for å rendres på nytt. */
fun VedtaksbrevDomain.tilVedtaksbrevData() = VedtaksbrevData(
    dato = dato,
    deltaker = VedtaksbrevData.Deltaker(navn = deltaker.navn),
    arbeidsgiver = VedtaksbrevData.Arbeidsgiver(navn = arbeidsgiver.navn, orgnr = arbeidsgiver.orgnr),
    soknadssum = soknadssum,
    innvilgetBelop = innvilgetBelop,
    tiltaksperiode = tiltaksperiode?.let { VedtaksbrevData.Periode(fra = it.fra, til = it.til) },
    refusjonsfrist = refusjonsfrist,
    begrunnelse = begrunnelse,
    navEnhet = navEnhet,
    saksbehandler = saksbehandler,
)

@OptIn(ExperimentalTime::class)
fun VilkarsvurderingDTO.tilDomain() = SakDomain.Vilkarsvurdering(
    vilkar = vilkar.tilDomain(),
    godkjent = godkjent,
    notat = notat,
    vurdertAvIdent = vurdertAvIdent,
    vurdertTidspunkt = vurdertTidspunkt,
)

/**
 * Saken med vilkårsvurderingene, eller null når saken ikke finnes eller mangler behandlende enhet.
 * [behandlendeEnhetNavn] kommer fra den innloggede, siden enhetsnavnet ikke lagres på saken.
 * Må kalles i en pågående transaksjon.
 */
fun hentSakDomain(sakId: UUID, behandlendeEnhetNavn: String?): SakDomain? {
    val rad = SakTable.selectAll().where { SakTable.sakId eq sakId }.singleOrNull() ?: return null
    val behandlendeEnhet = rad[SakTable.behandlendeEnhet] ?: return null
    return SakDomain(
        sakId = sakId.toString(),
        status = Saksstatus.valueOf(rad[SakTable.status]).tilDomain(),
        kildeTilBehandling = KildeTilBehandling.valueOf(rad[SakTable.kildeTilBehandling]).tilDomain(),
        behandlendeEnhet = behandlendeEnhet,
        behandlendeEnhetNavn = behandlendeEnhetNavn,
        saksbehandlerIdent = rad[SakTable.saksbehandlerIdent],
        saksbehandlerNavn = rad[SakTable.saksbehandlerNavn],
        beslutterIdent = rad[SakTable.beslutterIdent],
        beslutterNavn = rad[SakTable.beslutterNavn],
        adressebeskyttelse = rad[SakTable.adressebeskyttelse]?.let(Adressebeskyttelse::valueOf)?.tilDomain(),
        skjermet = rad[SakTable.skjermet],
        vilkarsvurderinger = hentVilkarsvurdering(sakId).map { it.tilDomain() },
    )
}
