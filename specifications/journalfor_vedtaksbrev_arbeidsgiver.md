# Journalfør vedtaksbrev til arbeidsgiver

Trello: https://trello.com/c/is5SjNKk/691-journalf%C3%B8r-tilsagnsbrev-til-arbeidsgiver
Kort-ID: `6ae4f422055c7253637ebdcd`

## Mål

Når beslutter har fattet vedtak på en søknad i ny saksbehandling, lager
backend vedtaksbrevet til arbeidsgiver som PDF og journalfører det i Dokarkiv
i arbeidsgivermappa. Det gjelder alle tre utfall:

- innvilget (`vedtakArbeidsgiverInnvilget`)
- innvilget med redusert beløp (`vedtakArbeidsgiverInnvilgetRedusert`)
- avslått (`vedtakArbeidsgiverAvslag`)

Etter journalføringen publiseres `VedtaksbrevArbeidsgiverJournalfoert`, med nok
data til at «Varsle arbeidsgiver om vedtak» (kort 645) kan varsle uten
databaseoppslag. Journalføringen vises i saksloggen.

Kortet gjelder bare backend. Visningen for arbeidsgiver er kort 693 og venter
på skisser og på kort 682.

## Bakgrunn

Fra før:

- `VedtakFattet` publiseres av vedtaks-API-et. `FattVedtak` lagrer raden i
  `vedtak` med `forelopig_vedtak_id`, `fattet_av_navn`, `fattet_tidspunkt` og
  `event_id`. `SettSoknadStatusVedtakFattet` og `BestillTilsagnIOebs` kjører
  etter, og sjekker at vedtaket er lagret for samme event med
  `hentVedtakUtfallForEvent` og `vedtakKanFattes`.
- `VedtakFattet` har i dag bare `sakId`, `soknadId`, `forelopigVedtakVersjon`,
  `utfall` (`Saksstatus`), `fattetAvIdent`, `fattetAvNavn` og `tidspunkt`. Hva
  vedtaket sier, står i `ForelopigVedtakSendtTilBeslutter` og projeksjonen
  `forelopig_vedtak`.
- `VedtakFattet` publiseres bare fra `BeslutterApi`, og saksrutene er ikke
  åpnet i prod (`Application.kt`, `basedOnEnvSuspending(prod = {})`). Eventen
  har derfor aldri vært i prod og kan endres. Den lages også i
  `LokaleSaker.kt` og i testene.
- Malene for arbeidsgiver ligger i
  `backend/src/main/resources/dokumentmaler/templates/` (`forelopig_vedtak_brev.md`).
  `vedtaksbrevData(...)` og `vedtaksbrevMal(...)` i
  `saksbehandling/Vedtaksbrev.kt` bygger dataene, og
  `DokumentService.genererVedtaksbrevPdf` lager PDF/A med Gotenberg. I dag
  brukes de bare til forhåndsvisning i `SakDokumentApi.kt`. Den spec-en sier:
  «Forhåndsvisningen ser ut akkurat som brevet som sendes» (Ken, 2026-10-13).
- Malene bruker `deltaker.navn`, `arbeidsgiver.navn`, `dato`, beløp,
  tiltaksperiode, `begrunnelse` (redusert og avslag), `navEnhet` og
  `saksbehandler`. Toppen sier «Unntatt offentlighet, jf. offl. § 13».
- Forhåndsvisningen henter `navEnhet` med `principal.enhetsnavn(behandlendeEnhet)`
  (Entra) og `saksbehandler` med `hentSaksbehandlerNavn(sakId, innstiltAvIdent)`.
  Begge er private i `SakDokumentApi.kt`. En handler har ingen innlogget
  bruker, men beslutter har det når vedtaket fattes. Beslutter må også ha
  sakens enhet (`sjekkTilgangTilEnhet`), så enhetsnavnet finnes alltid da.
- `DokArkivClient.opprettOgFerdigstillJournalpost` journalfører med
  `bruker = ORGNR` (arbeidsgivermappa), tema `TIL`, behandlingstema `ab0423`,
  kanal `NAV_NO`, `brevkode = altinn3Ressursid` (`nav_tiltak_ekspertbistand`),
  `journalfoerendeEnhet = "9999"` og fagsak fra `FagsakIdService` (per søknad).
  Kallet bruker systemtoken og sender ingen ansatt-ident.
  409 Conflict på `eksternReferanseId` gir samme svar som 201, så et nytt forsøk
  gir ikke en journalpost til.
- `JournalfoerTilskuddsbrev` (Arena) er mønsteret: lag PDF, journalfør
  `UTGAAENDE` til orgnr, publiser `TilskuddsbrevJournalfoert` og guard.
- `sak.adressebeskyttelse` (`FORTROLIG`/`STRENGT_FORTROLIG`) og `sak.skjermet`
  settes av `OpprettSak`. Kode 6 i utlandet lagres som `STRENGT_FORTROLIG`.
- Malene til arbeidsgiver har ikke fødselsnummer. Den eneste
  personopplysningen om deltaker er `deltaker.navn`, og i tillegg
  begrunnelsen saksbehandler har skrevet.
- `EventManager` gir `TransientError` for eventer uten handler. Eventer som
  bare skal lagres, får en no-op-handler (`TilskuddsbrevVistNoop`).

## Skisser

Ingen. Kortet gjelder backend. Hanna skal lage skisser for innvilget, innvilget
med redusert beløp og avslått (kommentar på kortet, 2026-11-02). De hører til
visningen i kort 693 (<https://trello.com/c/mmcwqp5V>).

## Beslutninger

Kortet har ingen sjekklister eller vedlegg (sjekket 2026-11-02).

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen og kommentarer, 2026-10-30 og 2026-11-02 | Brevet til arbeidsgiver journalføres når vedtaket er fattet. Henger sammen med `3moZGD2U` (vedtak og tilsagn) og `jRixpurm` (kode 6/7 og sladding i arbeidsgivermappa). Tre tilstander: innvilget, innvilget med redusert beløp og avslått. Løses før arbeidsgiver varsles. Visningen avhenger av kort 682 og skisser, som Hanna skal lage. |
| Ken, 2026-10-30 til 2026-11-02 (også kort 645, `varsle_arbeidsgiver_vedtak.md`): omfang, varsel og visning | Bare backend. Visningen er skilt ut i kort 693 (`mmcwqp5V`). Alle vedtak varsles, også avslag, via notifikasjonsplattformen, ikke Altinn. Eventen etter journalføringen har nok data til varselet, uten databaseoppslag, og designes her. Arbeidsgiver får brevet usladdet, rendret med Gotenberg som forhåndsvisningen, og ser og laster det ned i vår løsning. PDF-er hentes aldri fra Joark til arbeidsgiver. En blob på saken kan vurderes. |
| Ken, 2026-11-01 og 2026-11-02, ROS 2106 - 28079, `jRixpurm` og `navikt/tiltak-tilsagnsbrev` ([`Tilsagn.getSladdetVersjon`](https://github.com/navikt/tiltak-tilsagnsbrev/blob/e209755995fbdff10e4aaf3748afc023767433a7/src/main/java/no/nav/tag/tilsagnsbrev/dto/tilsagnsbrev/Tilsagn.java#L67), [`Oppgaver.opprettPdfDok`](https://github.com/navikt/tiltak-tilsagnsbrev/blob/e209755995fbdff10e4aaf3748afc023767433a7/src/main/java/no/nav/tag/tilsagnsbrev/behandler/Oppgaver.java), lest 2026-11-01): sladding | Deltaker sladdes i det journalførte brevet ved kode 6 og 7 med `***********`, som Team Tiltak. Team Tiltak lager to PDF-er: Joark-kopien sladdes ved `erKode6Eller7()` (navn, fødselsnummer og adresse), PDF-en til arbeidsgiver sladdes ikke. Skjerming gir ingen sladding. |
| Ken, 2026-11-01 og 2026-11-02, Slack, Team Dokumentløsninger (<https://nav-it.slack.com/archives/C6W9E5GPJ/p1768916740867909>), Team Tiltak og Mulighetsrommet: journalpost | `journalfoerendeEnhet` er `9999` fordi journalføringen er automatisk, som hos Team Tiltak og Mulighetsrommet. Vi sender ingen `Nav-User-Id`. Kanal `NAV_NO` passer når dokumentet vises på nav.no og mottaker varsles (Alexander). Brevkoden er `nav_tiltak_ekspertbistand`. Dokumentløsninger har ingen sterke meninger om den. |
| Ken, 2026-11-01 og 2026-11-02, og minne: eventmodell | `event_log` og eventkøen er masterdata, og en begrunnelse kan ligge i en event. `event_log` endres aldri, og eventer i prod endres aldri. `SakProjection` får ikke funksjonalitet for nye saker. `VedtakFattet` får søknaden, saken (med vilkårsvurderinger) og vedtaket, så handlere ikke leser projeksjonene. Det støtter også ikke-benekting. Eventer får egne domain-typer (`SoknadDomainV2`, `SakDomain`, `VedtakDomain`), ikke API-DTO-er, så API-et kan endres uten at eventene knekker. Domain-typene får også egne enums i `event/domain/`, og oversettelsen kaster når en verdi ikke kan oversettes. `SoknadDomainV2` følger en innsendt søknad, ikke nullbarheten for utkast. |
| Ken, 2026-11-01, og spørring mot `event_log` i prod, 2026-11-01: søknadsdata | 14 360 rader i 9 eventtyper har `soknad`. Alle har `id`, `opprettetAv`, gyldig `opprettetTidspunkt` og `beliggenhetsadresse`. `status` er utelatt (`innsendt`) unntatt i `tilskuddsbrevVist` (`godkjent`, eller `soknad` lik null i 696 rader). `ekspert` har tre former: fritekst i `kompetanse` (før 2026-08-11), listene med `virksomhetNavn`/`virksomhetOrgnr`, og listene uten. `SoknadDomainV1` blir en nøyaktig kopi av `DTO.Soknad` for eventene i prod. JSON-en er lik, så typen kan byttes uten at `event_log` endres. Eget kort. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Lytter på `VedtakFattet`.** Handleren kjører etter
  `SettSoknadStatusVedtakFattet`. Den bruker `hentVedtakUtfallForEvent` og
  `vedtakKanFattes` bare for å sjekke at `FattVedtak` godtok eventen, som
  `BestillTilsagnIOebs` gjør. Det er ikke for å hente data. En `VedtakFattet` som
  `FattVedtak` avviste (kappløp), skal ikke gi et vedtaksbrev.
- **Domain-typene ligger i `event/domain/`**, sammen med funksjonene som
  oversetter fra API-DTO-er og tabellrader (`tilDomain()`). Oversettelsen er
  det eneste stedet som kjenner begge modellene. Handlere leser bare
  domain-typene.
- **`SoknadDomainV2` følger en innsendt søknad, ikke utkastet** (Ken,
  2026-11-01). `DTO.Soknad` er nullbar for å støtte utkast. I
  `soknad`-tabellen er `id`, `opprettetAv`, `opprettetTidspunkt`, `status` og
  listene alltid satt, så de er påkrevde i `SoknadDomainV2`. Det som er nullbart i
  tabellen, er nullbart i `SoknadDomainV2` også: `beliggenhetsadresse` (fra
  Enhetsregisteret) og ekspertens `virksomhetNavn` og `virksomhetOrgnr`.
  Ingen felt har standardverdier, så alle felt står i JSON-en.
  `opprettetTidspunkt` er `Instant`, ikke tekst.
- **`SoknadDomainV2.Ekspert` har ikke `kompetanse`.** Fritekstfeltet ble byttet
  ut med `godkjentUtdanningEllerAutorisasjon` og `relevantKompetanse` 2026-08-11
  (`61bda8c`). Siden da sender frontend alltid `""`, og skjemaet krever minst
  ett valg i hver liste. `tilDomain()` feiler når en av listene er tom. Saker
  med kilde `EKSPERTBISTAND` opprettes når søknaden er journalført, og bare
  utenfor prod. Søknader i ny saksbehandling har derfor alltid listene. Gamle
  søknader i dev uten lister gir 500 når beslutter fatter vedtak.
- **`beliggenhetsadresse` er nullbar, selv om alle eventer i prod har den.**
  `eregService.hentForretningsadresse` kan gi null, og kolonnen er nullbar.
- **Domain-typene har egne enums** (Ken, 2026-11-02). De ligger nestet i
  typen de hører til: `SoknadDomainV2.Status`, `SakDomain.Status`,
  `SakDomain.KildeTilBehandling`, `SakDomain.Adressebeskyttelse`,
  `SakDomain.Vilkarsvurdering.Vilkar` og `VedtakDomain.Utfall`. Nestingen
  unngår navnekollisjon med enumene i `soknad` og `saksbehandling`. Verdiene
  har samme navn som i dag, så JSON-en ser lik ut. `SoknadDomainV2.Status`
  har ikke `utkast`, fordi en innsendt søknad aldri er et utkast.
- **Enumene oversettes med `tilDomain()` og en uttømmende `when` uten `else`.**
  En verdi som ikke kan oversettes, kaster `IllegalStateException`. I dag
  gjelder det bare `SoknadStatus.utkast`. Får API-enumen en ny verdi, stopper
  kompilatoren til noen har bestemt hva den blir i domain. Alternativet var
  `valueOf(name)`. Det ble forkastet: da kobles enumene på navn, og feilen
  kommer først når eventen publiseres.
- **Felt som finnes i eventer i dag, beholder enumene sine**, for eksempel
  `VedtakFattet.utfall: Saksstatus`. Domain-enumene gjelder de nye
  domain-typene og den nye eventen.
- **`VedtakDomain` er det foreløpige vedtaket beslutter godkjente**: utfall,
  beløp, periode, begrunnelser, hvem som innstilte og når det ble sendt. Hvem
  som fattet, når og `utfall: Saksstatus` står allerede øverst i
  `VedtakFattet`, og `FattVedtak`, `SettSoknadStatusVedtakFattet`,
  `BestillTilsagnIOebs` og statistikken bruker dem. De blir stående. `init`
  krever at `vedtak.versjon == forelopigVedtakVersjon`,
  `vedtak.utfall.tilSaksstatus() == utfall`, `soknad.id == soknadId` og
  `sak.sakId == sakId`.
- **`SakDomain` er saken slik den var da vedtaket ble fattet**, med
  vilkårsvurderingene, men uten feltene som avhenger av hvem som ser saken
  (`kanTildeleMeg` og lignende i `SakDetaljer`).
- **Dataene leses i samme transaksjon og under samme lås** som sjekken av
  versjonen i `publiserBeslutning`. Eventen og vedtaket som fattes, er da like.
- **Nye felt er påkrevde, uten standardverdier.** `VedtakFattet` har aldri
  vært i prod, og `event_log` i dev har ingen `vedtakFattet` (Ken,
  2026-11-01). Endringen bryter derfor ingen lagrede eventer. Alternativet var valgfrie felt med fallback til
  projeksjonen. Det ble forkastet: da måtte handleren fortsatt lete i
  projeksjonen, og eventen ville ikke være komplett.
- **Brevdato er `VedtakFattet.tidspunkt`** (Oslo). Forhåndsvisningen etter
  innsending viser datoen det foreløpige vedtaket ble sendt. Det endelige
  brevet skal ha vedtaksdatoen.
- **Hilsen som i forhåndsvisningen, låst når vedtaket fattes.** API-et setter
  `sak.behandlendeEnhetNavn` med `principal.enhetsnavn(behandlendeEnhet)`.
  Funksjonen flyttes fra `SakDokumentApi.kt` til `Vedtaksbrev.kt`. Handleren
  bruker `sak.saksbehandlerNavn` når `sak.saksbehandlerIdent` er
  `vedtak.innstiltAvIdent`, ellers null. Det er samme regel som
  `hentSaksbehandlerNavn` i forhåndsvisningen. Norg trengs ikke.
- **`journalfoerendeEnhet` er `9999`, og vi sender ingen `Nav-User-Id`** (Ken,
  2026-11-02). Journalføringen er automatisk, som hos Team Tiltak og
  Mulighetsrommet. `DokArkivClient` beholder `"9999"` for alle kall, med en
  kommentar om at det er automatisk journalføring.
- **Kanal er `NAV_NO`**, som i dag (Alexander).
- **`eksternReferanseId = "<soknadId>-vedtaksbrev-arbeidsgiver"`.** Det er ett
  vedtak per søknad. Dokarkiv gir 409 og samme journalpost ved et nytt forsøk.
- **Tittel**: «Vedtak om tilskudd til ekspertbistand» ved innvilgelse og
  redusert beløp, «Avslag på søknad om ekspertbistand» ved avslag.
- **`utfall` i eventen er `VedtakDomain.Utfall`** (`INNVILGE`, `INNVILGE_REDUSERT`,
  `AVSLAA`). Det forteller hvilket brev som ble journalført.
  `Saksstatus` skiller ikke redusert beløp fra full innvilgelse.
- **Saksloggen via `SakOppdatert`** (`SYSTEM`, notat «Vedtaksbrev til
  arbeidsgiver journalført»), publisert i samme transaksjon som eventen.
- **No-op-handler** for `VedtaksbrevArbeidsgiverJournalfoert` til kort 645
  bygges. Den fjernes når varslingen registreres.
- **Sladding samles i én funksjon i `Vedtaksbrev.kt`.** Den tar
  `sak.adressebeskyttelse` fra eventen og bytter `deltaker.navn` med `***********` når den er
  `STRENGT_FORTROLIG` eller `FORTROLIG` (Ken, 2026-11-01). Bare PDF-en som
  journalføres, sladdes. Grunnen er at arbeidsgivermappa ligger på orgnr, så
  tilgangskontrollen på person i Gosys og Joark ikke beskytter deltakeren mot
  Nav-ansatte. Arbeidsgiver vet allerede hvem søknaden gjelder, og får brevet
  usladdet. Dette er avklart i ROS 2106 - 28079, «Risiko for eksponering
  av stedslokaliserende informasjon for Kode 6/7 brukere». Begrunnelsen
  sladdes ikke, fordi saksbehandler skriver den til arbeidsgiver. Skjermede
  (egne ansatte) sladdes ikke, som hos Team Tiltak.

## Avgrensning

Ikke i dette kortet:

- Visning av vedtaksbrevet for arbeidsgiver, og API-et den trenger (kort 693).
  Venter på skisser og kort 682. Det er besluttet hvordan visningen skal virke (Ken,
  2026-11-01): brevet rendres usladdet med Gotenberg fra dataene, og hentes
  aldri fra Joark. `journalpostId` i eventen er derfor bare en kvittering,
  ikke en peker arbeidsgiver-API-et skal bruke.
- Varsling av arbeidsgiver: kort 645 (<https://trello.com/c/XZvrwKjG>).
- Vedtaksbrev til deltaker: «Journalfør vedtaksbrev til deltaker»
  (<https://trello.com/c/4qcIiWBB>).
- Utsending av brevene: «Send ut vedtaksbrev og tilsagnsbrev»
  (<https://trello.com/c/6l4XA2H5>).
- Endelig ordlyd i malene og klageavsnittet til arbeidsgiver. Fag
  kvalitetssikrer (`forelopig_vedtak_brev.md`).
- Endring av `tilskuddsbrev` og journalføringen av Arena-saker.
- Å la `FattVedtak`, `SettSoknadStatusVedtakFattet` og `BestillTilsagnIOebs`
  bruke de nye feltene i stedet for projeksjonen. Det kan gjøres senere.
- `SoknadDomainV1` og å bytte `DTO.Soknad` med den i eventene som har den i
  dag (eget kort). Det blir ingen `V1.tilV2()`: 949 av 1517 søknader i prod
  har bare fritekst i `kompetanse`, og den kan ikke oversettes til listene
  uten å gjette.
- Hvordan kode 6/7 skal se ut i saksbehandling (resten av `jRixpurm`).

## Tilnærming

### 1. Domain-typer og utvidet `VedtakFattet`

Nye filer i `event/domain/`:

```kotlin
@Serializable
data class SoknadDomainV2(
    val id: String,
    val virksomhet: Virksomhet,
    val ansatt: Ansatt,
    val ekspert: Ekspert,
    val behovForBistand: BehovForBistand,
    val nav: Nav,
    val opprettetAv: String,
    val opprettetTidspunkt: Instant,
    val status: Status,
) {
    @Suppress("EnumEntryName")
    @Serializable
    enum class Status { innsendt, godkjent, avlyst, avslatt }

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
        val kontaktperson: Kontaktperson,
        val beliggenhetsadresse: String?,
    )

    @Serializable
    data class Kontaktperson(val navn: String, val epost: String, val telefonnummer: String)

    @Serializable
    data class Ansatt(val fnr: String, val navn: String)

    @Serializable
    data class Ekspert(
        val navn: String,
        val virksomhet: String,
        val virksomhetNavn: String?,
        val virksomhetOrgnr: String?,
        val godkjentUtdanningEllerAutorisasjon: List<String>,
        val relevantKompetanse: List<String>,
    )

    @Serializable
    data class BehovForBistand(
        val begrunnelse: String,
        val behov: String,
        val estimertKostnad: String,
        val timer: String,
        val tilrettelegging: String,
        val startdato: LocalDate,
    )

    @Serializable
    data class Nav(val kontaktperson: String)
}

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
```

Oversettelsene ligger i samme pakke: `DTO.Soknad.tilDomain()`,
`ForelopigVedtakDTO.tilDomain()` og `VilkarsvurderingDTO.tilDomain()`.
`SakDomain` bygges fra saksraden. `DTO.Soknad.tilDomain()` feiler med
`IllegalStateException` når `id`, `opprettetAv` eller `opprettetTidspunkt`
mangler, eller når en av ekspertlistene er tom. Det første skjer bare med et
utkast, og `findSoknadById` gir aldri utkast (de ligger i `UtkastTable`). `opprettetTidspunkt` leses med `Instant.parse`,
siden `tilSoknadDTO` skriver den med `Instant.toString()`.

Enumene oversettes i samme pakke med `tilDomain()` på hver API-enum
(`SoknadStatus`, `Saksstatus`, `KildeTilBehandling`, `Adressebeskyttelse`,
`Vilkar`, `ForelopigUtfall`). Hver er en uttømmende `when` uten `else`:

```kotlin
fun SoknadStatus.tilDomain(): SoknadDomainV2.Status = when (this) {
    SoknadStatus.innsendt -> SoknadDomainV2.Status.innsendt
    SoknadStatus.godkjent -> SoknadDomainV2.Status.godkjent
    SoknadStatus.avlyst -> SoknadDomainV2.Status.avlyst
    SoknadStatus.avslatt -> SoknadDomainV2.Status.avslatt
    SoknadStatus.utkast -> error("Søknad med status utkast kan ikke bli SoknadDomainV2")
}
```

`VedtakDomain.Utfall.tilSaksstatus()` ligger også her, for `init` i
`VedtakFattet`. Den gir samme svar som `ForelopigUtfall.saksstatus`.

`VedtakFattet`:

```kotlin
data class VedtakFattet(
    val sakId: String,
    val soknadId: String,
    val forelopigVedtakVersjon: Int,
    val utfall: Saksstatus,
    val fattetAvIdent: String,
    val fattetAvNavn: String,
    val tidspunkt: Instant,
    val soknad: SoknadDomainV2,
    val sak: SakDomain,
    val vedtak: VedtakDomain,
) : EventData
```

- `sak.status` er statusen før vedtaket (`TIL_BESLUTNING`). Statusen etter står
  i `utfall`.
- `sak.behandlendeEnhetNavn` og `sak.saksbehandlerNavn` kan være null, som i
  forhåndsvisningen. Malen viser da det den gjør der.
- KDoc: nye felt, ny konsument, og at identer, navn, fødselsnummer og
  begrunnelser ikke skal logges utenfor teamLog.

### 2. `BeslutterApi` fyller ut eventen

I `/vedtak`-ruten, inne i `publiserBeslutning` etter sjekken av versjonen, i
samme transaksjon og under samme lås:

- `hentForelopigVedtak(sakId, forelopigVedtakVersjon)` i `saksbehandling/Db.kt`
  gir `VedtakDomain`.
- `hentSakDomain(sakId, behandlendeEnhetNavn)` i `event/domain/` leser saksraden
  og `hentVilkarsvurdering(sakId)`.
- `findSoknadById(soknadId, filterForArbeidsgiver = false)?.tilDomain()`.
- `principal.enhetsnavn(behandlendeEnhet)` leses før transaksjonen og sendes
  inn.
- Mangler det foreløpige vedtaket, saken, enheten eller søknaden, avvises
  beslutningen med `FORELOPIG_VEDTAK_ENDRET` (409), som når versjonen ikke
  stemmer. Ingen event publiseres.

### 3. Ny event

```kotlin
@Serializable
@SerialName("vedtaksbrevArbeidsgiverJournalfoert")
data class VedtaksbrevArbeidsgiverJournalfoert(
    val sakId: String,
    val soknadId: String,
    val utfall: VedtakDomain.Utfall,
    val virksomhetsnummer: String,
    val virksomhetsnavn: String,
    val journalpostId: String,
    val dokumentInfoId: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

- `journalpostId` og `dokumentInfoId` er `String`, slik Dokarkiv returnerer dem.
  Arena-eventene bruker `Int`.
- KDoc: publisert av `JournalfoerVedtaksbrevArbeidsgiver`, konsument kommer i
  kort 645. Ingen personopplysninger, bare organisasjonsdata.
- Inline no-op-handler `VedtaksbrevArbeidsgiverJournalfoertNoop`.

### 4. `JournalfoerVedtaksbrevArbeidsgiver`

Lytter på `VedtakFattet`. Registreres for alle miljøer, etter
`SettSoknadStatusVedtakFattet`. `id = "Journalfoer vedtaksbrev arbeidsgiver"`.

1. Guard på `vedtaksbrev_arbeidsgiver_journalfoert`. Er den satt, `success()`.
2. I transaksjon: `hentVedtakUtfallForEvent(event.id)`. Er den null, gir det
   `transientError` når `vedtakKanFattes(data)`, ellers `success()` uten
   journalføring.
3. Utenfor transaksjon, bare med data fra eventen:
   - `vedtaksbrevData(..., mottaker = ARBEIDSGIVER, dato = tidspunkt)` med
     `soknad`, `sak` og `vedtak` fra eventen, og sladding ved
     `sak.adressebeskyttelse`.
   - `dokumentService.genererVedtaksbrevPdf(...)`. Feil gir `transientError`.
   - `dokArkivClient.opprettOgFerdigstillJournalpost(...)` med
     `UTGAAENDE`, mottaker orgnr og fagsak fra `FagsakIdService`.
     Feil eller ikke ferdigstilt gir `transientError`.
4. I én transaksjon: publiser `VedtaksbrevArbeidsgiverJournalfoert` og
   `SakOppdatert`, og guard. Feil gir `rollback()` og `transientError`.

Ingen suspend-kall inne i `transaction {}`. Innholdet i brevet logges ikke.

### 5. Hjelpefunksjoner

- `event/domain/`: domain-typene, `tilDomain()`-oversettelsene og
  `hentSakDomain`.
- `saksbehandling/Db.kt`: `hentForelopigVedtak(sakId, versjon)`.
- `saksbehandling/Vedtaksbrev.kt`:
  - `vedtaksbrevData` tar deltakers navn, virksomhet og søknadssum som egne
    parametere i stedet for `DTO.Soknad`, så brevet ikke avhenger av
    API-DTO-en. Forhåndsvisningen henter dem fra `DTO.Soknad`, og handleren
    fra `SoknadDomainV2`.
  - `saksbehandlerForBrev(saksbehandlerIdent, saksbehandlerNavn, innstiltAvIdent)`
    med regelen fra `hentSaksbehandlerNavn`. Forhåndsvisningen og handleren
    bruker den.
  - `enhetsnavn`, flyttet fra `SakDokumentApi.kt`.
  - Sladdingen ved adressebeskyttelse.
- `dokarkiv/DokArkivClient.kt`: kommentar om at `"9999"` er automatisk
  journalføring.

### 6. Lokal kjøring

`LocalApplication` har mock for Dokarkiv. `LokaleSaker.kt` lager
`VedtakFattet` med de nye feltene.

### 7. Dokumentasjon

- KDoc på `VedtakFattet`: nye felt, og `JournalfoerVedtaksbrevArbeidsgiver`
  som konsument.
- `event-flow.md` genereres med `EventFlowDiagram.kt`.
- `varsle_arbeidsgiver_vedtak.md` (kort 645) oppdateres med eventnavnet når den
  spec-en tas opp igjen.

### 8. Tester

Med `testApplicationWithDatabase` og mønsteret fra `JournalfoerTilskuddsbrev`-
og `BestillTilsagnIOebs`-testene.

- `BeslutterApiTest`: `VedtakFattet` i køen har `vedtak` fra den versjonen av
  det foreløpige vedtaket som ble godkjent, `sak` med enhet og enhetsnavn,
  gradering og vilkårsvurderinger, og `soknad`. En nyere versjon gir 409 og
  ingen event, som i dag.
- `DTO.Soknad.tilDomain()`: en innsendt søknad fra `findSoknadById` oversettes
  med alle felt. Den feiler for en søknad uten `id`, `opprettetAv` eller
  `opprettetTidspunkt`, og for en søknad med fritekst i `kompetanse` og tomme
  lister.
- `SoknadStatus.utkast.tilDomain()` kaster `IllegalStateException`.
- Hvert av de tre utfallene gir riktig mal, `UTGAAENDE` til orgnr,
  `journalfoerendeEnhet` lik `9999` og
  én `VedtaksbrevArbeidsgiverJournalfoert` og én `SakOppdatert` i køen.
- Brevdato er `VedtakFattet.tidspunkt`.
- Kode 6 og 7: `deltaker.navn` er `***********` i dataene til malen for
  `STRENGT_FORTROLIG` og `FORTROLIG`. Det er ikke sladdet for en sak som bare
  er skjermet, eller uten gradering.
- Vedtak ikke lagret, men kan fattes: `transientError`. Kan ikke fattes:
  `success()` uten journalføring.
- Feil fra Gotenberg eller Dokarkiv: `transientError`, ingen eventer.
- Retry etter suksess: ingen nye kall og ingen nye eventer.
- `SakDokumentApiTest` og `VedtaksbrevTest` er grønne etter at
  `vedtaksbrevData` har fått nye parametere.
- Ny event og utvidet `VedtakFattet` i `TestEventData.allEventSamples` og
  `EventDataAggregateRootIdTest`.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `event/domain/` | Ny: `SoknadDomainV2`, `SakDomain`, `VedtakDomain` med egne enums, `tilDomain()` for typer og enums, `tilSaksstatus()` og `hentSakDomain` |
| `event/Events.kt` | Utvidet `VedtakFattet`, ny event, KDoc, registrering, no-op-handler |
| `saksbehandling/BeslutterApi.kt` | Fyller ut `soknad`, `sak` og `vedtak` i `VedtakFattet` |
| `event/handlers/JournalfoerVedtaksbrevArbeidsgiver.kt` | Ny |
| `saksbehandling/Db.kt` | `hentForelopigVedtak(sakId, versjon)` |
| `saksbehandling/Vedtaksbrev.kt` | `vedtaksbrevData` med egne parametere, `saksbehandlerForBrev`, `enhetsnavn` og sladding |
| `saksbehandling/SakDokumentApi.kt` | Bruker de flyttede funksjonene |
| `dokarkiv/DokArkivClient.kt` | Kommentar på `"9999"` |
| `event/event-flow.md` | Generert |
| `test/.../LokaleSaker.kt` | `VedtakFattet` med nye felt |
| `test/.../BeslutterApiTest.kt` | Nye felt i eventen |
| `test/.../FattVedtakTest.kt`, `SettSoknadStatusVedtakFattetTest.kt`, `SaksbehandlingsstatistikkProjectionTest.kt` | `VedtakFattet` med nye felt |
| `test/.../JournalfoerVedtaksbrevArbeidsgiverTest.kt` | Ny |
| `test/.../event/domain/SoknadDomainV2Test.kt` | Ny: `DTO.Soknad.tilDomain()` og `SoknadStatus.utkast` |
| `test/.../TestEventData.kt`, `EventDataAggregateRootIdTest.kt` | Ny event og utvidet `VedtakFattet` |

## Kanttilfeller og feilmodus

- **Retry etter journalføring, før eventen er publisert.** Dokarkiv gir 409 og
  samme journalpost på `eksternReferanseId`. Eventen publiseres én gang.
- **Gotenberg eller Dokarkiv nede.** `transientError` og retry. Ingen
  journalpost, ingen event og dermed ingen varsel.
- **Journalposten blir ikke ferdigstilt.** `transientError`. Et nytt forsøk får
  409 og samme journalpost, og kan ferdigstille.
- **Saksbehandler har byttet etter innstilling.** `sak.saksbehandlerIdent` er
  ikke `vedtak.innstiltAvIdent`, så saksbehandler i brevet blir null, og
  hilsenen viser bare enheten. Samme regel som forhåndsvisningen.
- **Saken eller søknaden endres etter vedtaket.** Brevet bygges fra eventen og
  viser det som gjaldt da vedtaket ble fattet. Det samme gjelder et nytt forsøk
  dager senere.
- **`DTO.Soknad` endres senere.** `tilDomain()` må oppdateres, men
  `SoknadDomainV2` og `VedtakFattet` endres ikke. Kompilatoren viser hvor.
- **En API-enum får en ny verdi.** Den uttømmende `when` i `tilDomain()`
  kompilerer ikke før verdien er oversatt eller kaster med vilje.
- **`tilDomain()` får et utkast.** Mangler `id`, `opprettetAv` eller
  `opprettetTidspunkt`, feiler oversettelsen. `BeslutterApi` henter bare
  innsendte søknader, så dette er en programmeringsfeil og gir 500, ikke 409.
- **Søknadssum som ikke er et tall.** `tolkSoknadssum` gir null, og malen
  viser det den gjør i forhåndsvisningen i dag.
- **Arena-saker.** `VedtakFattet` publiseres bare for kilde `EKSPERTBISTAND`.
- **Personopplysninger i logger.** Eventen har fødselsnummer og navn på
  deltaker, identer, notater fra vilkårsvurderingen og begrunnelser som kan ha
  helseopplysninger. De står i `event_log`, og deler av dem i PDF-en. De
  skrives ikke til applikasjonslogger eller saksloggen.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] `SoknadDomainV2`, `SakDomain` og `VedtakDomain` finnes i `event/domain/`, og `SoknadDomainV2` har påkrevde felt der en innsendt søknad alltid har en verdi
- [ ] Domain-typene bruker bare egne enums, og `tilDomain()` på API-enumene kaster for verdier uten motstykke
- [ ] `VedtakFattet` har `soknad`, `sak` (med vilkårsvurderinger) og `vedtak`, fylt ut av `BeslutterApi`
- [ ] Handleren bygger brevet bare fra eventen, uten å lese projeksjonene
- [ ] Vedtak gir journalført brev til arbeidsgiver for alle tre utfall, med riktig mal, dato, enhet og saksbehandler
- [ ] Journalposten er `UTGAAENDE`, i arbeidsgivermappa, på søknadens fagsak, med `journalfoerendeEnhet` `9999`
- [ ] `VedtaksbrevArbeidsgiverJournalfoert` publiseres med dataene kort 645 trenger, og en linje i saksloggen
- [ ] Deltakers navn er `***********` i det journalførte brevet ved kode 6 og 7
- [ ] Ingen dobbel journalpost eller dobbel event ved retry
- [ ] `event-flow.md` er generert, og KDoc på `VedtakFattet` er oppdatert
- [ ] `mvn -B package` er grønn
