# Forhåndsvisning av vedtaksbrev på foreløpig vedtak

Trello: https://trello.com/c/gh8l45zx/635-bruk-pdf-pill-p%C3%A5-%C3%A5-fatte-forel%C3%B8pig-vedtak-p%C3%A5-s%C3%B8knad
Kort-ID: `6ace19c389523a1695295f92`

## Mål

Saksbehandler skal se vedtaksbrevene før det foreløpige vedtaket sendes til
beslutter. Fanen «Foreløpig vedtak» viser to PDF-er med
`PdfForhandsvisning`: én til deltaker og én til arbeidsgiver. Brevene lages
fra det saksbehandler har fylt ut, og knappene «Forrige» og «Send til
beslutter» flyttes under dem.

Etter innsending viser fanen de samme to brevene, laget fra det lagrede
foreløpige vedtaket. Beslutter ser dem også.

Kortet lager seks nye brevmaler, én per utfall og mottaker. Bare teksten til
deltaker ved innvilgelse er klar. De andre er forslag som fag kvalitetssikrer
før prodsetting.

## Bakgrunn

Fra før:

- `forelopig_vedtak.md` (Ken, 2026-10-09) holdt forhåndsvisning av brev
  utenfor. Hanna skulle lage et forslag med «pills» i stedet for lenker. Det
  forslaget er skissen i dette kortet.
- `pdf_forhandsvisning.md` laget `PdfForhandsvisning` og
  `PdfForhandsvisningListe` i `frontend/saksbehandling/src/components/pdf/`.
  De brukes i dag for refusjonsvedlegg og sluttrapport. `PdfFil` har `url`,
  `filnavn` og `storrelse`, og pdf.js henter `url` selv.
- `SakDokumentApi.kt` gir PDF-er til saksbehandler og beslutter med
  `hentTilgjengeligSak` (rolle, enhet, Tilgangsmaskinen) og `svarMedPdf`
  (`no-store`, trygt filnavn, `?visning=forhandsvisning`).
- `DokumentService` rendrer Handlebars-maler under
  `dokumentmaler/templates/` og konverterer til PDF/A med Gotenberg. En
  semafor tillater fire samtidige konverteringer. `DokumentmalTest` krever
  testdata per mal og at alle malfelt finnes i payloaden fra
  `DokumentService`.
- Malen `tilskuddsbrev` bygger på `TilsagnData` fra Arena:
  tilsagnsnummer, kontonummer, refusjonsfrist, beslutter og saksbehandler.
  Den brukes når Arena-tilsagn journalføres.
- `ForelopigVedtakOppsummering` viser steg 2 (`forhandsvisning`) og vedtaket
  etter innsending (`sendt`). `ForelopigVedtakFane` i `SakPage` viser `sendt`
  for alle som åpner en sak i `TIL_BESLUTNING`.
- Navnet på behandlende enhet finnes ikke på saken. Innlogget ansatt har
  `principal.enheter` med nummer og navn fra Entra-proxyen, og
  `sjekkTilgangTilEnhet` krever at sakens enhet er blant dem.
- `LocalApplication` bruker `StubPdfKonverterer` og registrerer ikke
  `configureSakDokumentApiV1`.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-13.

| Node | Lenke | Viser |
|------|-------|-------|
| `853:29352` | [knapper nederst](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=853-29352) | Fanen «Foreløpig vedtak», steg 2 for innvilge. Grønt infokort «Vurdering: Innvilge» med tilskudd, deltaker, arbeidsgiver og tiltaksperiode. Låst «Deltaker - tilpass begrunnelse». Under står to PDF-miniatyrer over hverandre (cirka 465×659 px), hver med lenken «Forhandvis foreløpig vedtak - deltakere» og ekstern-ikon under. Nederst «Forrige» (sekundær) og «Send til beslutter» (primær). |

Miniatyrene i skissen er plassholdere: et tilfeldig juridisk brev, ikke et
utkast til vedtaksbrev. Skissen har realistiske testdata (navn,
fødselsnummer). De er ikke kopiert hit.

Ingen andre frames i fila har «pills» for foreløpig vedtak. Redusert beløp,
avslag og visningen etter innsending (`414:18714`, `38:1255`, `723:25524`,
`723:25766`, `513:28774`) har fortsatt lenkene fra før.

Avvik mellom kort og skisse:

- **Begge lenkene heter «… - deltakere».** Kortet sier at vedtaket til
  arbeidsgiver og til deltaker vises hver for seg. Den andre er til
  arbeidsgiver (Ken, 2026-10-13).
- **Lenketekst og ikon.** Skissen har ekstern-ikon og «Forhåndsvis …».
  `PdfForhandsvisning` viser PDF-ikon og filnavnet som nedlastingslenke. Vi
  bruker komponenten som den er, med filnavn som sier hvem brevet er til.
- **Stavefeil i skissen.** «Forhandvis», «deltakere». Vi skriver
  «Foreløpig vedtak - deltaker».

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen; Figma `853:29352` | Forhåndsvisningen bruker PDF-komponenten fra `pdf_forhandsvisning.md`. Vedtaket til deltaker og til arbeidsgiver vises hver for seg. |
| Kortbeskrivelsen | Arbeidsgiver har mal for innvilgelse (tilskuddsbrev). Det trengs nye maler for arbeidsgiver ved redusert beløp og avslag, og tre nye maler for deltaker. |
| Kortbeskrivelsen | Teksten til deltaker ved innvilgelse er klar og står i kortet. De andre malene får forslag som fag kvalitetssikrer før prodsetting. |
| Kommentar fra Hanna, 2026-09-24, på kortet «Vedtak til deltakere og tilsagn til arbeidsgiver» (`6a8d564c…`) | Samme tekst til deltaker, uten `{begrunnelseTilDeltaker}`. Kortet vårt er nyere og plasserer begrunnelsen etter hjemmelen. |
| Ken, 2026-10-09 (`forelopig_vedtak.md`) | Forhåndsvisning med «pills» kommer i et eget kort. Det er dette. |
| Ken, 2026-10-12 (`forelopig_vedtak.md`) | Begrunnelse til deltaker gjelder alle utfall. Begrunnelse til arbeidsgiver finnes bare ved redusert beløp og avslag. |
| Ken, 2026-10-13 | Den andre lenken i skissen er brevet til arbeidsgiver. Deltaker vises først, arbeidsgiver under. |
| Ken, 2026-10-13 | Innvilgelse til arbeidsgiver får en ny mal med samme tekst som `tilskuddsbrev`. `tilskuddsbrev` endres ikke. |
| Ken, 2026-10-13 | Fristen for refusjonskrav er seks måneder etter sluttdatoen for tiltaket. Perioden er én konstant i koden, slik at den kan endres hvis regelverket endres. |
| Ken, 2026-10-13 | Forhåndsvisningen ser ut akkurat som brevet som sendes. Ingen utkast-merking. |
| Ken, 2026-10-13 | Klagerett og klagefrist for arbeidsgiver avklares med fag senere. Forslaget med klageavsnitt brukes inntil videre. |
| Ken, 2026-10-06 (minne) | API-et publiserer en event, og en handler gjør endringen. Forhåndsvisning endrer ingen tilstand, så den trenger ingen event. |
| Ken, 2026-10-10 (`forelopig_vedtak.md`) | Backend bruker testoppsettet som finnes, uten ny testteknologi. |

Kortet har ingen kommentarer, sjekklister eller vedlegg.

Valg i spec-en:

- **Ny mal også for innvilgelse til arbeidsgiver.** `tilskuddsbrev` krever
  Arena-felt vi ikke har før beslutter har godkjent: tilsagnsnummer,
  kontonummer, beslutter og refusjonsfrist fra Arena. Vi lager
  `vedtakArbeidsgiverInnvilget` med teksten fra `tilskuddsbrev`, uten
  tilsagnsnummer, kontonummer og beslutter, og med refusjonsfristen vi regner
  ut selv. `tilskuddsbrev` endres ikke, fordi Arena-journalføringen bruker den.
  Ulempen er to maler med nesten lik tekst.
- **Brevet lages på forespørsel og lagres ikke.** Brevet som journalføres og
  sendes, lages i beslutterkortet.
- **To ruter.** `POST` lager brev fra skjemaet før innsending. `GET` lager brev
  fra det lagrede vedtaket etter innsending. Beslutter skal se det som er
  lagret, ikke det en nettleser sender inn.
- **Ingen utkast-merking.** Forhåndsvisningen ser ut akkurat som brevet som
  sendes. Ulempen er at en nedlastet forhåndsvisning kan forveksles med et
  sendt vedtak. Filnavnet sier «forelopig-vedtak».
- **Refusjonsfristen ligger i `refusjon`-pakken.** Den gjelder refusjonskravet,
  ikke brevet, og refusjonskortet kan bruke den samme konstanten.
- **«Nav», ikke «NAV».** Teksten i kortet skriver «NAV». Malene følger Navs
  skrivemåte. Lenkene skrives som `https://www.nav.no/…`, fordi malene bare
  tillater absolutte `https://`-lenker.

## Avgrensning

Ikke i dette kortet:

- Journalføring og utsending av vedtaksbrevene. Det kommer med beslutterkortet
  og «Varsle deltaker om vedtak» (`6aab96635bae2266d1c1396f`).
- Beslutterens side utover at fanen viser de lagrede brevene.
- Endelig ordlyd i forslagene. Fag kvalitetssikrer før prodsetting.
- Endringer i `tilskuddsbrev` og Arena-journalføringen.
- Forhåndsvisning i steg 1 (skjemaet). Brevene vises i steg 2 og etter
  innsending.
- Visning av tilsagnsbrev på Arena-saker.

## Tilnærming

```
Steg 2 (forhandsvisning)                         Etter innsending (sendt)
POST …/forelopig-vedtak/forhandsvisning          GET …/forelopig-vedtak/brev/{mottaker}
  body: ForelopigVedtakRequest                     leser siste forelopig_vedtak
  ?mottaker=deltaker|arbeidsgiver
              │                                               │
              └──────────── vedtaksbrevData(...) ─────────────┘
                                    │
          DokumentService.genererVedtaksbrevPdf(utfall, mottaker, data)
                                    │
                    mal per (utfall, mottaker) ── Gotenberg ── PDF
```

### 1. Maler

Seks nye mapper under `dokumentmaler/templates/`, hver med `template.hbs`,
`schema.json` og `testdata/`:

| Mal | Utfall | Mottaker | Tekst |
|-----|--------|----------|-------|
| `vedtakDeltakerInnvilget` | `INNVILGE` | deltaker | Fra kortet |
| `vedtakDeltakerInnvilgetRedusert` | `INNVILGE_REDUSERT` | deltaker | Forslag |
| `vedtakDeltakerAvslag` | `AVSLAA` | deltaker | Forslag |
| `vedtakArbeidsgiverInnvilget` | `INNVILGE` | arbeidsgiver | Fra `tilskuddsbrev`, uten Arena-felt |
| `vedtakArbeidsgiverInnvilgetRedusert` | `INNVILGE_REDUSERT` | arbeidsgiver | Forslag |
| `vedtakArbeidsgiverAvslag` | `AVSLAA` | arbeidsgiver | Forslag |

Alle malene:

- starter med «Unntatt offentlighet, jf. offl. § 13», som `tilskuddsbrev`,
  fordi begrunnelsen kan inneholde helseopplysninger,
- har dato øverst til høyre,
- bruker bare `{{verdi}}` og faste `https://`-lenker, etter
  `templates/README.md`.

Malene er ren tekst med Handlebars. Utkastene under viser innholdet, ikke
endelig HTML.

**`vedtakDeltakerInnvilget`** (fra kortet):

> **Du får tilskudd til ekspertbistand**
>
> Du, {arbeidsgiver.navn} og Nav har blitt enige om at ekspertbistand kan
> bidra til å bedre din arbeidssituasjon. Tilskuddet utbetales til
> arbeidsgiveren din. Du kan lese mer om ekspertbistand på nav.no.
>
> Vedtaket er gjort etter folketrygdloven § 8-7a femte ledd og forskrift om
> tilskudd til ekspertbistand.
>
> {begrunnelse}
>
> *Felles avslutning til deltaker*

**`vedtakDeltakerInnvilgetRedusert`** (forslag):

> **Du får tilskudd til ekspertbistand**
>
> Du, {arbeidsgiver.navn} og Nav har blitt enige om at ekspertbistand kan
> bidra til å bedre din arbeidssituasjon. Arbeidsgiveren din søkte om
> {soknadssum} kroner. Nav har innvilget {innvilgetBelop} kroner. Tilskuddet
> utbetales til arbeidsgiveren din. Du kan lese mer om ekspertbistand på
> nav.no.
>
> Vedtaket er gjort etter folketrygdloven § 8-7a femte ledd og forskrift om
> tilskudd til ekspertbistand.
>
> {begrunnelse}
>
> *Felles avslutning til deltaker*

**`vedtakDeltakerAvslag`** (forslag):

> **Du får ikke tilskudd til ekspertbistand**
>
> {arbeidsgiver.navn} søkte om tilskudd til ekspertbistand for deg. Nav har
> avslått søknaden.
>
> Vedtaket er gjort etter folketrygdloven § 8-7a femte ledd og forskrift om
> tilskudd til ekspertbistand.
>
> {begrunnelse}
>
> *Felles avslutning til deltaker*

*Felles avslutning til deltaker* (fra kortet) legges som partial
`templates/felles/vedtakDeltakerAvslutning.hbs` og tas inn med
`{{> felles/vedtakDeltakerAvslutning}}`:

> **Du har rett til å klage**
> Hvis du mener vedtaket er feil, kan du klage innen seks uker fra den datoen
> du mottok vedtaket. Klagen skal være skriftlig. Du finner skjema og
> informasjon på nav.no/klage.
>
> **Du har rett til innsyn**
> Du har rett til å se dokumentene i saken din. Dette kan Nav-kontoret hjelpe
> deg med.
>
> **Har du spørsmål?**
> Du kan kontakte oss på nav.no/kontakt eller på telefon 55 55 33 36.

Mappen `felles/` har ingen `template.hbs` og regnes ikke som mal. Brevhode,
hjemmel, utbetaling, klage og hilsen til arbeidsgiver er også partials i
`felles/`, slik at de seks malene deler teksten.

Strict-modus i `HandlebarsConfig` (brukt i test) kastet for alle Map-kontekster
når en partial ble tatt inn, fordi Handlebars slår opp `size` og `empty` i
konteksten. Modellen pakkes nå i en egen `Datamodell`-type, og bare den
sjekkes strengt. Mapper Handlebars lager selv, og oppslagene `size` og
`empty`, slipper gjennom.

**`vedtakArbeidsgiverInnvilget`** (fra `tilskuddsbrev`):

> Til {arbeidsgiver.navn}
>
> **Dere har fått innvilget tilskudd til ekspertbistand**
>
> Nav og dere har blitt enige om dette:
>
> | | |
> |-|-|
> | Tiltaket | Ekspertbistand |
> | Deltakeren | {deltaker.navn} |
> | Tiltaksperioden | {tiltaksperiode.fra} - {tiltaksperiode.til} |
> | Støtten fra Nav | Opptil {innvilgetBelop} kroner |
>
> Avsnittene om betingelser, refusjonskrav, ekspertens rapport og kontroll fra
> `tilskuddsbrev`. Setningen om frist blir «Dere må rapportere de faktiske
> utgiftene innen {refusjonsfrist}.», der fristen er seks måneder etter
> {tiltaksperiode.til}. Tabellen «Følgende informasjon er registrert hos Nav»
> får bare organisasjonsnummer. Kontonummeret oppgir arbeidsgiver i
> refusjonskravet.
>
> Hilsen
> {navEnhet}
> {saksbehandler}

**`vedtakArbeidsgiverInnvilgetRedusert`** (forslag): som
`vedtakArbeidsgiverInnvilget`, men med overskriften «Dere har fått innvilget
lavere tilskudd til ekspertbistand enn dere søkte om», raden «Dere søkte om
{soknadssum} kroner» over «Støtten fra Nav», {begrunnelse} under tabellen og
avsnittet om klage fra avslutningen til deltaker, med «dere» i stedet for
«du». Refusjonsfristen står som ved innvilgelse.

**`vedtakArbeidsgiverAvslag`** (forslag):

> Til {arbeidsgiver.navn}
>
> **Dere får ikke tilskudd til ekspertbistand**
>
> Nav har avslått søknaden om tilskudd til ekspertbistand for
> {deltaker.navn}.
>
> Vedtaket er gjort etter folketrygdloven § 8-7a femte ledd og forskrift om
> tilskudd til ekspertbistand.
>
> {begrunnelse}
>
> Avsnittet om klage, med «dere». Hilsen, enhet og saksbehandler som over.

Brevene til arbeidsgiver har deltakers navn, men ikke fødselsnummer.
Arbeidsgiver vet hvem den ansatte er, og vi tar ikke med mer enn vi trenger.

### 2. Backend: payload og `DokumentService`

🔴 Rød sone: payloaden bestemmer hvilke personopplysninger som havner i
brevet. Gå gjennom denne nøye.

Ny fil `refusjon/Refusjonsfrist.kt`:

```kotlin
val REFUSJONSFRIST_ETTER_SLUTTDATO: Period = Period.ofMonths(6)

fun refusjonsfrist(sluttdato: LocalDate): LocalDate =
    sluttdato.plus(REFUSJONSFRIST_ETTER_SLUTTDATO)
```

`plus(Period.ofMonths(6))` gir siste dag i måneden når datoen ikke finnes,
for eksempel 31. august gir 28. eller 29. februar.

Ny fil `saksbehandling/Vedtaksbrev.kt`:

```kotlin
enum class Mottaker { DELTAKER, ARBEIDSGIVER }

@Serializable
data class VedtaksbrevData(
    val dato: String,                  // «13. oktober 2026»
    val deltaker: Deltaker,
    val arbeidsgiver: Arbeidsgiver,
    val soknadssum: String?,           // «22 000»
    val innvilgetBelop: String?,
    val tiltaksperiode: Periode?,      // «22. november 2026»
    val refusjonsfrist: String?,       // null ved avslag
    val begrunnelse: String?,          // til mottakeren
    val navEnhet: String?,
    val saksbehandler: String?,
) {
    @Serializable data class Deltaker(val navn: String)
    @Serializable data class Arbeidsgiver(val navn: String, val orgnr: String)
    @Serializable data class Periode(val fra: String, val til: String)
}

fun vedtaksbrevMal(utfall: ForelopigUtfall, mottaker: Mottaker): String
fun vedtaksbrevData(
    soknad: DTO.Soknad,
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
): VedtaksbrevData
```

`vedtaksbrevData` tar feltene hver for seg, slik at `POST` kan sende inn fra
`ForelopigVedtakRequest` og `GET` fra `ForelopigVedtakDTO`.

- `begrunnelse` er `begrunnelseDeltaker` eller `begrunnelseArbeidsgiver` etter
  mottaker. Den andre begrunnelsen kommer aldri med i payloaden.
- `innvilgetBelop` er søknadssummen ved `INNVILGE`, som i
  `publiserForelopigVedtak`. Søknadssummen tolkes med samme funksjon som der.
- `refusjonsfrist` er `refusjonsfrist(tiltaksperiodeTil)` når perioden finnes.
  `validerFelter` krever perioden ved `INNVILGE` og `INNVILGE_REDUSERT`, og
  forbyr den ved `AVSLAA`.
- Beløp formateres med mellomrom som tusenskille, og datoer med norske
  månedsnavn. Formateringen ligger i `Vedtaksbrev.kt`, slik at malene ikke
  trenger helpers.
- Fødselsnummer, kontaktperson og ekspert tas ikke med.

`DokumentService` får:

```kotlin
suspend fun genererVedtaksbrevPdf(
    utfall: ForelopigUtfall,
    mottaker: Mottaker,
    data: VedtaksbrevData,
): ByteArray
```

Den kaller `renderPdf(vedtaksbrevMal(utfall, mottaker), …)` og bruker samme
semafor.

### 3. Backend: ruter

🔴 Rød sone: tilgang til brev med personopplysninger. Gå gjennom denne nøye.

Begge rutene legges i `SakDokumentApi.kt`, slik at de bruker
`hentTilgjengeligSak`, `inlineVisning`, `svarMedPdf` og `dokumentHentet`.
`DokumentService` hentes med `dependencies.resolve()`.

**`POST /api/saksbehandling/v1/saker/{sakId}/forelopig-vedtak/forhandsvisning?mottaker=deltaker|arbeidsgiver`**

Body: `ForelopigVedtakRequest`, som ved innsending. Rekkefølge, som
`POST …/forelopig-vedtak`:

1. `principalMedRolle(Role.SAKSBEHANDLER)`, `sakIdParameter()`, ukjent
   `mottaker` gir 400.
2. `hentSakTilgangsgrunnlag` (404), `sjekkTilgangTilEnhet` (403),
   `sjekkTilgangsmaskin` (403/503), kilde `ARENA` gir 409
   `SAK_UGYLDIG_KILDE`, `sjekkErSaksbehandlerPåSak` (403).
3. Status må være `UNDER_BEHANDLING`, ellers 409 `SAK_UGYLDIG_STATUS`.
4. `call.receive`, `valider(request)` og `validerFelter(request)` (400).
5. Henter søknaden, enhetsnavnet fra `principal.enheter` og navnet til
   saksbehandler på saken. Lager PDF med dagens dato.
6. Svarer med `svarMedPdf(filnavn, bytes, inline = true)`.

Ingen event og ingen skriving til databasen. Sjekkene som krever databasen
ved innsending (alle vilkår vurdert, beløp under søknadssum, beløp over sats
ved innvilgelse), gjøres ikke her. Innsendingen stopper dem.

**`GET /api/saksbehandling/v1/saker/{sakId}/forelopig-vedtak/brev/{mottaker}`**

1. `hentTilgjengeligSak`: rolle `SAKSBEHANDLER` eller `BESLUTTER`, enhet og
   Tilgangsmaskinen. Beslutter skal se brevet uten å ha saken.
2. Ukjent `mottaker` gir 400. Ingen lagret versjon gir 404.
3. Lager PDF fra `hentSisteForelopigVedtak(sakId)` (`Db.kt`), med
   `sendt_tidspunkt` som dato. Saksbehandler i brevet er `innstilt_av_ident`
   sitt navn, hvis vi har det.
4. `?visning=forhandsvisning` gir `inline`, ellers nedlasting, som de andre
   filrutene.

Filnavn: `forelopig-vedtak-deltaker.pdf` og
`forelopig-vedtak-arbeidsgiver.pdf`. `svarMedPdf` ville uansett byttet ut
«ø».

Gotenberg-feil gir 503 med `{ "message": "kunne ikke lage brev" }`. Feilen
logges uten innhold fra brevet.

### 4. Frontend: `PdfFil` med innhold

En forhåndsvisning fra `POST` har ingen URL pdf.js kan hente. `PdfFil` får et
valgfritt felt:

```ts
export type PdfFil = {
  url: string;
  filnavn: string;
  storrelse?: number;
  /** Når satt, gjengis PDF-en fra innholdet, og `url` brukes bare til nedlasting. */
  innhold?: Blob;
};
```

`PdfMiniatyr` og `PdfSider` gir `react-pdf` `fil.innhold ?? fil.url`.
`react-pdf` leser en `Blob` med `FileReader` og henter ingenting, så CSP-en
trenger ikke `blob:` i `connect-src`. Nedlastingslenken bruker
`URL.createObjectURL(innhold)` som `url`. Eksisterende bruk endres ikke.

Valget: `Blob` på `PdfFil` i stedet for `blob:`-URL til pdf.js. Vi slipper å
åpne CSP-en, mot ett nytt felt i komponenten.

### 5. Frontend: hook

`hooks/useVedtaksbrev.ts`:

- `useVedtaksbrevForhandsvisning(sakId, input)` gir `{ deltaker, arbeidsgiver,
  isLoading, error }`. Den POSTer `input` én gang per mottaker med SWR, med
  nøkkel `[url, mottaker, JSON.stringify(input)]` og uten revalidering. Svaret
  blir en `Blob` og en object URL. Object URL-ene frigjøres med
  `URL.revokeObjectURL` når input endres eller komponenten forsvinner.
- `useLagredeVedtaksbrev(sakId, versjon)` henter `GET …/brev/{mottaker}` med
  `?visning=forhandsvisning` som `Blob` på samme måte. `versjon` er med i
  SWR-nøkkelen. Innsendingen svarer 202 før `LagreForelopigVedtak` har lagret
  vedtaket, og fanen bytter til `sendt` med en gang. GET kan derfor gi 404 de
  første hundre millisekundene. Hooken prøver på nytt ved 404, opptil fem
  ganger med dobbel ventetid fra 300 ms, før den gir opp.

Nye konstanter i `utils/constants.ts`:
`SAKSBEHANDLING_FORELOPIG_VEDTAK_FORHANDSVISNING_URL` og
`SAKSBEHANDLING_FORELOPIG_VEDTAK_BREV_URL`.

### 6. Frontend: `ForelopigVedtakOppsummering`

Ny komponent `components/forelopigVedtak/Vedtaksbrev.tsx` viser brevene over
hverandre i en `VStack`, deltaker først:

| Filnavn (lenketekst) | Kilde |
|----------------------|-------|
| «Foreløpig vedtak - deltaker.pdf» | `forhandsvisning`: `useVedtaksbrevForhandsvisning`. `sendt`: `useLagredeVedtaksbrev` |
| «Foreløpig vedtak - arbeidsgiver.pdf» | Som over, med `arbeidsgiver` |

- Bredde 360 px. Skissen har cirka 465 px i en kolonne på 586 px. Vi går ned
  for å få plass på smalere skjermer.
- Mens brevene hentes, vises `Skeleton` i samme størrelse.
- Feiler henting av brevene, vises `Alert variant="warning" size="small"` «Kunne ikke lage
  forhåndsvisning av brevene.» «Send til beslutter» virker fortsatt, slik at
  Gotenberg-feil ikke stopper saksbehandlingen.

`ForelopigVedtakOppsummering` viser `Vedtaksbrev` etter begrunnelsene i begge
modusene. I `forhandsvisning` står feilmeldingen og knappene «Forrige» og
«Send til beslutter» under brevene, som i skissen.

`DEMO_VEDLEGG` i `SakPage` blir stående. Demoen fjernes i kortet som kobler på
vedlegg.

### 7. MSW-mock

`mocks/handlers.ts`:

- `POST …/forelopig-vedtak/forhandsvisning` henter `public/mock/eksempel.pdf`
  og svarer med den som `application/pdf`.
- `GET …/forelopig-vedtak/brev/:mottaker` svarer med samme fil. Nedlasting
  med `<a download>` treffer ikke MSW (se `pdf_forhandsvisning.md`) og gir
  404 i mock. Forhåndsvisningen virker.

### 8. `LocalApplication`

`LocalApplication` registrerer `configureSakDokumentApiV1()`, slik at rutene
finnes lokalt. `StubPdfKonverterer` gir fortsatt en stub-PDF. Malene sjekkes
visuelt med `RenderDokumentmaler` mot Gotenberg fra `docker-compose`.

### 9. Tester

Backend følger testoppsettet som finnes: `kotlin.test`,
`testApplicationWithDatabase`, `MockAzureAdIntrospector` og
`StubPdfKonverterer`.

- `DokumentmalTest`: de seks malene rendres fra testdata. Testen «rendrer
  gyldig HTML fra ekte payload via DokumentService» utvides med
  `genererVedtaksbrevPdf` for hver kombinasjon av utfall og mottaker.
- `VedtaksbrevTest` (ren enhetstest):
  - `vedtaksbrevMal` gir riktig mal for alle seks kombinasjoner.
  - Payloaden til deltaker har bare `begrunnelseDeltaker`, og payloaden til
    arbeidsgiver har bare `begrunnelseArbeidsgiver`.
  - `INNVILGE` får søknadssummen som beløp.
  - Beløp og datoer formateres som «22 000» og «22. november 2026».
  - Payloaden har ikke fødselsnummer.
  - `refusjonsfrist` er seks måneder etter sluttdato ved `INNVILGE` og
    `INNVILGE_REDUSERT`, og null ved `AVSLAA`.
- `RefusjonsfristTest` (ren enhetstest): 30. juni gir 30. desember, og
  31. august gir 28. februar (29. i skuddår).
- `SakDokumentApiTest`, `POST …/forhandsvisning`:
  - 200 `application/pdf` med `Cache-Control: no-store` for begge mottakere.
  - 403 uten rolle, uten enhet, avslått i Tilgangsmaskinen og ikke tildelt.
  - 404 ukjent sak. 400 ukjent mottaker og ugyldig body.
  - 409 for `ARENA` og for status som ikke er `UNDER_BEHANDLING`.
  - Ingen ny rad i `event_queue`.
- `SakDokumentApiTest`, `GET …/brev/{mottaker}`:
  - 200 for saksbehandler og for beslutter som ikke har saken.
  - 404 når saken ikke har foreløpig vedtak.
  - 403 uten enhet og avslått i Tilgangsmaskinen.
  - Bruker siste versjon når saken har flere.

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW for alle tre utfall i
steg 2 og etter innsending.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/dokumentmaler/templates/vedtak*/` | Seks nye maler med `schema.json` og `testdata/` |
| `backend/src/main/resources/dokumentmaler/templates/felles/` | Ny: partials som malene deler |
| `backend/src/main/kotlin/no/nav/ekspertbistand/dokument/pdf/HandlebarsConfig.kt` | Strict-modus tåler partials (`Datamodell`) |
| `backend/src/main/kotlin/no/nav/ekspertbistand/refusjon/Refusjonsfrist.kt` | Ny: `REFUSJONSFRIST_ETTER_SLUTTDATO`, `refusjonsfrist` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/refusjon/RefusjonsfristTest.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Vedtaksbrev.kt` | Ny: `Mottaker`, `VedtaksbrevData`, `vedtaksbrevMal`, `vedtaksbrevData` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/dokument/DokumentService.kt` | `genererVedtaksbrevPdf` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakDokumentApi.kt` | `POST …/forhandsvisning`, `GET …/brev/{mottaker}` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt` | `configureSakDokumentApiV1()` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/dokument/DokumentmalTest.kt` | Nye maler |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/VedtaksbrevTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SakDokumentApiTest.kt` | Nye ruter |
| `frontend/saksbehandling/src/components/pdf/types.ts`, `PdfMiniatyr.tsx`, `PdfSider.tsx`, `PdfForhandsvisning.tsx` | `innhold?: Blob`. `PdfMiniatyr` tar `fil` i stedet for `url` |
| `frontend/saksbehandling/src/hooks/useVedtaksbrev.ts` | Ny |
| `frontend/saksbehandling/src/utils/constants.ts` | To nye URL-er |
| `frontend/saksbehandling/src/components/forelopigVedtak/Vedtaksbrev.tsx` | Ny |
| `frontend/saksbehandling/src/components/forelopigVedtak/ForelopigVedtakOppsummering.tsx` | Viser brevene, knappene nederst |
| `frontend/saksbehandling/src/mocks/handlers.ts` | To nye handlere |
| `specifications/forelopig_vedtak.md` | *Avgrensning* peker hit |

## Kanttilfeller og feilmodus

- **Gotenberg er nede eller treg.** `POST` og `GET` gir 503. Fanen viser
  advarselen, og «Send til beslutter» virker. Semaforen begrenser til fire
  samtidige konverteringer. To brev per visning gir to kall.
- **Saksbehandler går fram og tilbake mellom steg 1 og 2.** Samme input gir
  samme SWR-nøkkel, og brevene hentes ikke på nytt. Endret input gir nye
  kall og frigjør de gamle object URL-ene.
- **Saken endrer status mens steg 2 er åpent.** For eksempel tar en kollega
  saken. `POST` gir 403 eller 409, og fanen viser advarselen. Innsendingen
  avvises på samme måte.
- **Søknadssum som ikke er et tall.** Ved `INNVILGE` kan beløpet ikke settes.
  `POST` gir 409 `UGYLDIG_SOKNADSSUM`, som innsendingen.
- **Enheten finnes ikke i `principal.enheter`.** Kan ikke skje etter
  `sjekkTilgangTilEnhet`. Blir `navEnhet` likevel null, står bare «Nav» i
  hilsenen.
- **Beslutter ser brevet med saksbehandlers navn.** Brevet etter innsending
  viser den som sendte. Beslutterens navn kommer med i beslutterkortet.
- **Nedlastet forhåndsvisning.** PDF-en har ingen utkast-merking og ser ut som
  et sendt vedtak. Den lagres ikke, journalføres ikke og sendes ikke. Det
  endelige brevet lages i beslutterkortet.
- **Refusjonsfrist på slutten av måneden.** `plus(Period)` flytter til siste
  gyldige dag, så fristen faller aldri på en dato som ikke finnes.
- **Lange begrunnelser.** Maks 10 000 tegn fra `valider`. Brevet får flere
  sider, og miniatyren viser bare den første.
- **HTML i begrunnelsen.** `{{begrunnelse}}` escapes, og linjeskift blir
  `<br/>`. `DokumentRendererSikkerhetTest` dekker dette.
- **Personopplysninger.** Brevene har navn og begrunnelser som kan inneholde
  helseopplysninger. Vi logger ikke innhold, mottakernavn eller begrunnelser.
  Svarene har `Cache-Control: no-store`. Object URL-ene lever bare i fanen og
  frigjøres.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene om lenke og rekkefølge, mal for arbeidsgiver,
refusjonsfrist, utkast-merking og klage 2026-10-13. Svarene står under
*Beslutninger*. Klagerett og klagefrist for arbeidsgiver avklares med fag før
prodsetting, sammen med de andre forslagene til brevtekst.

## Ferdig når

- [ ] Steg 2 i fanen «Foreløpig vedtak» viser brevet til deltaker og brevet til arbeidsgiver med `PdfForhandsvisning`, og knappene står under brevene
- [x] Brevene lages fra skjemaet med `POST …/forhandsvisning` uten at noe lagres eller publiseres
- [ ] Etter innsending viser fanen brevene fra det lagrede vedtaket for saksbehandler og beslutter
- [x] Seks maler finnes, én per utfall og mottaker, og deltaker ved innvilgelse har teksten fra kortet
- [x] Brevet til en mottaker har bare begrunnelsen til den mottakeren, og ingen brev har fødselsnummer
- [x] Tilgang følger `SakDokumentApi` for `GET` og innsendingen for `POST`
- [x] Gotenberg-feil stopper ikke «Send til beslutter»
- [x] CSP-en er uendret
- [x] `tilskuddsbrev` og Arena-journalføringen er uendret
- [x] Brevene til arbeidsgiver ved innvilgelse og redusert beløp har refusjonsfrist seks måneder etter sluttdato, fra én konstant
- [x] Forhåndsvisningen har ingen utkast-merking
- [x] PR-beskrivelsen ber fag gå gjennom forslagene til brevtekst, og klagerett for arbeidsgiver, før prodsetting
- [x] `mvn -B package` er grønn
- [x] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
