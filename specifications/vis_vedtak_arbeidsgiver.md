# Vis vedtak til arbeidsgiver

Trello: https://trello.com/c/mmcwqp5V/693-vise-vedtak-til-arbeidsgiver
Kort-ID: `6ae863e8564cb2efd722cf47`

## Mål

Når beslutter har fattet vedtak på en søknad i ny saksbehandling, ser
arbeidsgiver vedtaket på en egen side i v2 av søknadsløsningen. Det gjelder alle
tre utfall:

- innvilget («Søknad godkjent»)
- innvilget med redusert beløp («Søknad godkjent med redusert beløp»)
- avslått («Søknad avslått»)

Siden viser utfallet, beløpet, nøkkeldata om tiltaket og vedtaksbrevet.
Arbeidsgiver ser brevet i en PDF-forhåndsvisning og kan laste det ned.

En ny handler lytter på `VedtaksbrevArbeidsgiverJournalfoert` og lagrer
vedtaket med brevdataene i tabellen `vedtaksbrev`. API-et finner raden
via søknadens sak, og slår aldri opp i `event_log`.
Backend rendrer brevet usladdet med Gotenberg fra de lagrede dataene, og henter
det aldri fra Joark. Hver gang arbeidsgiver henter brevet, publiserer backend
`VedtaksbrevVist`.

## Bakgrunn

Fra før:

- **Frontend v2.** Rutene ligger i `frontend/client/src/App.tsx` under `/v2`
  og vises bare når `EKSPERTBISTAND_V2` er på (lokalt og dev). I prod går en
  `/v2`-URL til `*` og videre til `/`. Koden i `src/v2/` kan bare importere
  `utils/api`, `utils/http` og `utils/env` utenfor v2
  (`v2/isolation.test.ts`). Andre moduler kopieres inn i v2.
- **Kvitteringen.** I v2-oversikten lenker alle innsendte søknader til
  `/v2/skjema/:id/kvittering` (`v2/features/soknad/soknader.ts:96`).
  `v2/pages/KvitteringPage.tsx` viser et statusbanner og en oppsummering av
  søknaden. Når statusen er `godkjent`, viser den Arena-tilskuddsbrev i et
  `ExpansionCard` med «Last ned PDF». Den har ingen vedtaksdata, og frontend
  kan ikke skille Arena-saker fra saker i ny løsning.
- **Sluttrapport og refusjon.** `/v2/skjema/:id/sluttrapport` og
  `/v2/skjema/:id/refusjon` finnes bak `EKSPERTBISTAND_SAKSBEHANDLING_BETA`.
  Begge har «Tilbake til avtalen», som lenker til kvitteringen.
- **`PdfForhandsvisning`** ligger i saksbehandlingsappen
  (`frontend/saksbehandling/src/components/pdf/`). Den viser en miniatyr av
  første side, filnavnet som nedlastingslenke og en modal med hele dokumentet.
  Den bruker `react-pdf` og `pdfjs-dist`, som `client` ikke har, og
  `utils/formatBytes.ts`, som bare den bruker. Saksbehandling bruker den i
  `forelopigVedtak/Vedtaksbrev.tsx`, `SluttrapportVisning.tsx`,
  `RefusjonskravVisning.tsx` og `SakPage.tsx`, og lager `PdfFil` fra en blob og
  en object URL (`hooks/useVedtaksbrev.ts`). `pdf_forhandsvisning.md` sier at
  komponenten bør flyttes til `frontend/shared` når `client` skal bruke den.
  `saksbehandling-server` tillater `worker-src 'self' blob:` for pdf.js-workeren.
  CSP-en i `frontend/server` har ingen `workerSrc`.
- **Søknads-API-et.** `/api/soknad/v1` er beskyttet med TokenX. Én søknad hentes
  med `findSoknadById(id, filterForArbeidsgiver = true)`, som skjuler søknader
  der `arbeidsgiverTilgangTil` er passert, og tilgang sjekkes med
  `altinnTilgangerClient.hentTilganger(...).harTilgang(orgnr)`.
  `hentMaksBelop` i `refusjon/RefusjonApi.kt` er mønsteret: søknad, så Altinn,
  så saken, slik at svarkodene ikke avslører noe om virksomheter brukeren ikke
  har tilgang til.
- **`VedtaksbrevArbeidsgiverJournalfoert`** (kort 691,
  `journalfor_vedtaksbrev_arbeidsgiver.md`) publiseres av
  `JournalfoerVedtaksbrevArbeidsgiver` når brevet er journalført i
  arbeidsgivermappa. Eventen har `sakId`, `soknadId`, `utfall:
  VedtakDomain.Utfall`, `virksomhetsnummer`, `virksomhetsnavn`, `tidspunkt` og
  brevet usladdet som `vedtaksbrev: VedtaksbrevDomain`. Verdiene i
  `VedtaksbrevDomain` er tekst, formatert slik de står i brevet («40 000»,
  «1. mars 2027»). `tilVedtaksbrevData()` gjør dem om til malens
  `VedtaksbrevData`. Eventen har bare en no-op-handler, som venter på
  varslingen i kort 645. `VedtakFattet` publiseres, og dermed denne eventen,
  bare for saker med kilde `EKSPERTBISTAND`.
- **Tabellen `vedtak` finnes fra før.** Den hører til saksbehandling
  (`VedtakTable` i `saksbehandling/Db.kt`), så den nye tabellen må ha et annet
  navn.
- **`vedlegg/`** er mønsteret for en arbeidsgiverfunksjon: `VedleggDb` med
  tabellene, `VedleggApi` med `RoutingContext`-funksjonene og
  `VedleggRouting.kt` med `configureVedleggApiV1()`. `Application.kt` kaller
  den bare utenfor prod. `LagreTilsagnsData` og `tilsagndata/Db.kt` er
  mønsteret for en handler som lagrer data fra en event til visning, med en
  `json<T>`-kolonne.
- **Sletting.** `AggregateRetention` sletter søknaden, og alt som henger på
  `sak` med `ON DELETE CASCADE` forsvinner med den.
- **`DokumentService.genererVedtaksbrevPdf(utfall, mottaker, data)`** rendrer
  brevet med Gotenberg. Ingen PDF lagres. Alt rendres per kall.
- **`TilskuddsbrevVist`** publiseres av `TilsagnDataApi` ved hvert oppslag på
  Arena-tilskuddsbrevet. Den har en no-op-handler og brukes av
  `TilskuddsbrevVistProjection` til bruksmetrikk.
- **`svarMedPdf`** i `saksbehandling/SakDokumentApi.kt` setter
  `Content-Disposition` og `utenMellomlagring()`, men er privat.
- **Refusjonsfristen** er `refusjonsfrist(tiltaksperiodeTil)`, seks måneder
  etter sluttdatoen (`refusjon/Refusjonsfrist.kt`). `vedtaksbrevData` setter
  den for innvilgelse og redusert beløp, og den står i `VedtaksbrevDomain`.
- **Søknaden** hentes allerede av kvitteringen med `GET /api/soknad/v1/{id}`.
  Den har eksperten, innsendingstidspunktet og alt `KvitteringSummary` viser.
- **Lokale testdata.** `LokaleSaker.kt` har vedtak med `INNVILGE` (Live Lager,
  Hanne Hovel, Tor Tilskudd, Knut Kvalitet) og `AVSLAA` (Snorre Snekker), men
  ingen med `INNVILGE_REDUSERT`. MSW-mockene i `frontend/client/src/mocks/handlers.ts`
  har ingen vedtak.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand), siden «Arbeidgisver -
søknad, sluttrapoort + refusjon». Lest med Figma MCP 2026-11-10.

| Utfall | Ramme | Lenke |
|--------|-------|-------|
| Innvilget | `1059:48284` «søknad godkjent» | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1059-48284> |
| Redusert beløp | `1058:47416` «søknad godkjent - redusert beløp» | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1058-47416> |
| Avslått | `1058:47850` «søknad avslått» | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1058-47850> |

Alle tre rammene har samme oppbygning, ovenfra og ned:

1. Sidetittel «Tilskudd til ekspertbistand» med piktogram.
2. `Link` «Tilbake til oversikt».
3. Statusbanner: grønt «Søknad godkjent» og «Søknad godkjent med redusert
   beløp», rødt «Søknad avslått».
4. «{beløp} kr satt av til ekspertbistand» (ikke ved avslag).
5. Nøkkeldata: Tiltaksperiode, Deltaker, Ekspert, Arbeidsgiver. «Søkt beløp»
   vises ved redusert beløp og avslag, ikke ved innvilget.
6. «Vilkår og informasjon om utbetaling» med teksten «Du må sende inn
   sluttrapport og dokumentere faktiske utgifter knyttet til ekspertbistand
   innen fristen.» (ikke ved avslag).
7. `Button` (secondary) «Send inn sluttrapport fra eksperten» og «Søk refusjon»,
   og `Tag` (warning, outline, klokkeikon) «Frist {dato}» (ikke ved avslag).
8. To `ExpansionCard` (small), lukket, nyeste først:
   - «Vedtak om godkjent søknad», «Vedtak om godkjent søknad med redusert
     beløp» eller «Vedtak om avslag på søknad», med vedtaksdatoen og en
     pil-ikon.
   - «Søknad sendt inn», med innsendingsdatoen og et dokumentikon.

Innholdet i kortene er ikke tegnet. Komponenten peker på en plassholder
(`30:1461`). Vedtakskortet får `PdfForhandsvisning`, se Beslutninger.

Datoer og beløp i skissene er eksempeldata. Skissen skriver «faktiskte». Teksten
her bruker «faktiske».

## Beslutninger

Kortet har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-10).
Beslutningene kommer fra kortbeskrivelsen, fra kort 691 og 682, som kortet
avhenger av, og fra Kens svar på første utkast av denne spec-en.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Arbeidsgiver ser vedtaksbrevet i søknadsløsningen for de tre utfallene. Brevet rendres med Gotenberg fra våre egne data, aldri fra Joark. Arbeidsgiver kan laste det ned. Avhenger av kort 682 (ny rute bak feature toggle) og 691 (journalføring og event). |
| Ken, 2026-11-01 (`journalfor_vedtaksbrev_arbeidsgiver.md`) | Arbeidsgiver får brevet usladdet, rendret med Gotenberg som forhåndsvisningen. PDF-er hentes aldri fra Joark til arbeidsgiver, og vi bruker ikke Altinn-meldinger. En blob på saken kan vurderes. `journalpostId` er en kvittering, ikke en peker for arbeidsgiver-API-et. |
| Ken (frontend-ruter) | Ny arbeidsgiverfunksjonalitet lages som nye sider på egen rute bak feature toggle. Eksisterende sider beholdes uendret for Arena-saker. |
| Kort 682 | v2 ligger på `/v2/...` bak `EKSPERTBISTAND_V2`, og koden kan videreutvikles uavhengig av v1. |
| ROS 2106 - 28079 (via kort 691) | Sladding ved kode 6 og 7 gjelder bare kopien i arbeidsgivermappa. Arbeidsgiver vet hvem søknaden gjelder, og ser brevet usladdet. |
| Ken, 2026-11-10 | Vedtakskortet gjenbruker PDF-forhåndsvisningen `PdfForhandsvisning`. |
| Ken, 2026-11-10 | Komponenten kopieres inn i v2 og flyttes ikke til `frontend/shared`. Visningen i saksbehandling og søknad kan fort trenge å skille seg, så vi venter med en delt komponent til det er tre brukere («rule of three»). Dette erstatter forslaget i `pdf_forhandsvisning.md`. |
| Ken, 2026-11-10 | Refusjonsfristen følger koden (`refusjonsfrist`). Skissen har ikke nødvendigvis reelle data. |
| Ken, 2026-11-10 | Vi publiserer eventen `VedtaksbrevVist` når arbeidsgiver ser vedtaksbrevet. |
| Ken, 2026-11-10 | `VedtaksbrevVist` har fødselsnummeret til personen som så brevet. |
| Ken, 2026-11-10 | Vi slår ikke opp i `event_log`. `VedtaksbrevArbeidsgiverJournalfoert` har nok data til å lage brevet. En handler lytter på eventen og lagrer vedtaket til visning. API-et ligger i `vedtaksbrev/` (`VedtaksbrevDb`, `VedtaksbrevApi`) etter mønsteret til `configureVedleggApiV1`. Pakken, klassene og tabellen heter `vedtaksbrev`, som eventene `VedtaksbrevArbeidsgiverJournalfoert` og `VedtaksbrevVist`. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Ny tabell `vedtaksbrev`, én rad per sak.** Kolonnene er `sak_id`
  (primærnøkkel, `REFERENCES sak ON DELETE CASCADE`), `utfall`, `vedtaksbrev`
  (JSONB), `journalfoert_tidspunkt` og `event_id`. Navnet skiller den fra
  saksbehandlingens `vedtak`. Raden slettes med søknaden, så `AggregateRetention`
  trenger ingen ny kode.
- **`vedtaksbrev` lagres som `VedtaksbrevDomain`.** Tabellen speiler eventen,
  og eventer i prod endres aldri, så formatet ligger fast. Brevet rendres med
  `tilVedtaksbrevData()`. Alternativet var en kolonne per felt i
  `VedtaksbrevDomain`, eller å lagre malens `VedtaksbrevData`. Det første gir
  mer kode uten gevinst. Det andre ble forkastet fordi `VedtaksbrevData` kan
  endres med malene.
- **`utfall` lagres som navnet på `VedtakDomain.Utfall`**, av samme grunn.
- **Lagringen er idempotent.** Handleren gjør `insertIgnore` på `sak_id`. Kjører
  eventen på nytt, blir raden stående, og handleren svarer `success`.
- **Handleren erstatter no-op-handleren** `VedtaksbrevArbeidsgiverJournalfoertNoop`.
  Den publiserer ingen ny event. Varslingen i kort 645 kan lytte på
  `VedtaksbrevArbeidsgiverJournalfoert` selv.
- **Ingen lagret PDF.** Brevet rendres per kall fra raden, som tilskuddsbrevet.
- **`PdfForhandsvisning` kopieres inn i v2.** Filene i
  `saksbehandling/src/components/pdf/` kopieres til
  `client/src/v2/components/pdf/`, uten `PdfForhandsvisningListe`, som v2 ikke
  trenger. `formatBytes` kopieres til `v2/utils/`. Bare importstiene endres ved
  kopieringen. Saksbehandling endres ikke. `client` får `react-pdf` og
  `pdfjs-dist` i samme versjoner som saksbehandling.
- **Bare PDF, ingen HTML-versjon av brevet.** Forhåndsvisningen viser PDF-en,
  så API-et trenger ikke et HTML-endepunkt.
- **`VedtaksbrevVist` publiseres når API-et har rendret PDF-en**, rett før
  svaret sendes. Feiler Gotenberg, publiseres ingen event. Hvert oppslag gir en
  event, som `TilskuddsbrevVist`. Eventen har en no-op-handler. Metrikk og
  projeksjon kommer eventuelt i et eget kort.
- **Fødselsnummeret i `VedtaksbrevVist` er `pid` fra TokenX** (`innloggetBruker`),
  som `opprettetAv` på søknader. Feltet heter `vistTil`. Det står i eventen, men
  aldri i applogger.
- **Ny side `/v2/skjema/:id/vedtak`** (`v2/pages/VedtakPage.tsx`). Siden er ny
  fordi oppbygningen er en annen enn kvitteringens. v1 endres ikke.
- **Kvitteringen i v2 sender videre til vedtakssiden.** Når statusen er
  `godkjent` eller `avslatt`, henter `KvitteringPage` vedtaket. Svarer API-et
  200, går siden til `/v2/skjema/:id/vedtak` med `replace`. Ved 404 vises
  kvitteringen som i dag. Da lander oversikten, «Tilbake til avtalen» fra
  sluttrapport og refusjon og gamle lenker riktig, uten at frontend må kjenne
  kilden. Alternativet var å lenke direkte fra oversikten. Det ble forkastet:
  oversikten vet ikke om saken er fra Arena, og de andre lenkene til
  kvitteringen måtte også endres.
- **Vedtakssiden sender tilbake til kvitteringen ved 404**, med `replace`. Det
  skjer bare ved en direkte URL til en sak uten vedtak i ny løsning. Ingen
  løkke: kvitteringen sender bare videre ved 200.
- **Brevet hentes først når vedtakskortet åpnes.** Da gir `VedtaksbrevVist`
  et mål på hvem som faktisk åpnet brevet, ikke hvem som åpnet siden.
- **«Søknad sendt inn» viser oppsummeringen av søknaden.** `KvitteringSummary`
  flyttes til en egen fil i `v2/components/`, så begge sidene bruker den.
- **Siden viser verdiene slik de står i brevet.** `VedtaksbrevDto` har utfallet,
  vedtaksdatoen, beløpene, perioden, fristen og navnene som tekst fra
  `VedtaksbrevDomain`. Frontend legger bare til «kr». Da er siden og brevet
  alltid like. Eksperten, innsendingsdatoen og oppsummeringen kommer fra
  søknaden, som siden henter med `GET /api/soknad/v1/{id}`, som kvitteringen.
- **Egen API-enum `VedtaksbrevUtfall`** (`INNVILGET`, `INNVILGET_REDUSERT`,
  `AVSLATT`) i `vedtaksbrev/`. API-et bruker ikke `VedtakDomain.Utfall`, så eventen
  kan endres uten å endre API-et. Oversettelsen er en uttømmende `when`.
- **Tilgang som `hentMaksBelop`.** Søknaden hentes med
  `filterForArbeidsgiver = true` (404), så sjekkes Altinn (403), og først
  deretter vedtaket (404). Arena-saker får aldri en rad, og gir 404.
- **Vedtaket vises når brevet er journalført.** Før det finnes ingen rad, og
  kvitteringen vises. Varselet til arbeidsgiver (kort 645) kommer også etter
  journalføringen, så siden er klar når arbeidsgiver får varselet.
- **Rutene registreres bare utenfor prod**, i samme blokk som
  `configureVedleggApiV1()` i `Application.kt`. Handleren registreres i alle
  miljøer, som `JournalfoerVedtaksbrevArbeidsgiver`.
- **Knappene for sluttrapport og refusjon** lenker til de eksisterende v2-sidene.
  Rutene krever `EKSPERTBISTAND_SAKSBEHANDLING_BETA`, som er på i de samme
  miljøene som v2. v2 kan ikke lese flaggene, så siden viser knappene uten å
  sjekke flagget.

## Avgrensning

Ikke i dette kortet:

- Varsel til arbeidsgiver og lenken i varselet: kort 645
  (<https://trello.com/c/XZvrwKjG>).
- Metrikk og projeksjon for `VedtaksbrevVist`.
- Tilstandene etter vedtaket: sluttrapport sendt, refusjonskrav sendt og
  refusjon godkjent. De har egne rammer i Figma («slutrapport sendt inn»,
  «Nav behandler refusjonskravet», «refusjonskrav godkjent»).
- Endringer i v1, i oversikten og i Arena-tilskuddsbrevet.
- Endringer i eksisterende eventer og i `JournalfoerVedtaksbrevArbeidsgiver`.
- Endringer i `PdfForhandsvisning` i saksbehandlingsappen.
- Vedtaksbrev til deltaker.
- Endelig ordlyd i brevmalene. Fag kvalitetssikrer dem.
- Å åpne v2 i prod.

## Tilnærming

```
KvitteringPage (godkjent/avslatt) ──GET /{id}/vedtak──> 200 ──> /v2/skjema/:id/vedtak
                                                         404 ──> kvitteringen som i dag

VedtakPage ──GET /{id}/vedtak──────────> VedtaksbrevDto
           ──GET /{id}/vedtak/brev-pdf─> application/pdf   (når vedtakskortet åpnes)
                                          └─> VedtaksbrevVist

VedtaksbrevArbeidsgiverJournalfoert ─> LagreVedtaksbrev ─> vedtaksbrev

API: findSoknadById(filter) ─> Altinn ─> vedtaksbrev for søknadens sak
```

### 1. Backend: tabellen og `VedtaksbrevDb`

Migrering `V36__vedtaksbrev.sql`:

```sql
CREATE TABLE vedtaksbrev (
    sak_id                 UUID        PRIMARY KEY REFERENCES sak (sak_id) ON DELETE CASCADE,
    utfall                 TEXT        NOT NULL,
    brevdata               JSONB       NOT NULL,
    journalfoert_tidspunkt TIMESTAMPTZ NOT NULL,
    event_id               BIGINT      NOT NULL
);
```

Ny pakke `vedtaksbrev/`, fil `VedtaksbrevDb.kt`:

```kotlin
object VedtaksbrevTable : Table("vedtaksbrev") {
    val sakId = javaUUID("sak_id")
    val utfall = text("utfall")
    val brevdata = json<VedtaksbrevDomain>("brevdata", Json)
    val journalfoertTidspunkt = timestamp("journalfoert_tidspunkt")
    val eventId = long("event_id")

    override val primaryKey = PrimaryKey(sakId)
}

class VedtaksbrevDb(private val database: Database) {
    fun lagre(eventId: Long, data: EventData.VedtaksbrevArbeidsgiverJournalfoert)
    fun hentForSoknad(soknadId: UUID): Vedtaksbrev?
}

data class Vedtaksbrev(
    val sakId: UUID,
    val utfall: VedtakDomain.Utfall,
    val brevdata: VedtaksbrevDomain,
    val journalfoertTidspunkt: Instant,
)
```

- `lagre` gjør `insertIgnore`, så samme event to ganger gir én rad.
- `hentForSoknad` joiner med `SakTable` på `soknad_id`. Null når saken eller
  raden mangler.
- `utfall` leses med `VedtakDomain.Utfall.valueOf`.
- `AggregateRetention`: `vedtaksbrev` inn i kommentaren om hva
  `DELETE FROM soknad` sletter med cascade.

### 2. Backend: handleren `LagreVedtaksbrev`

I `event/handlers/`, etter mønsteret til `LagreTilsagnsData`:

```kotlin
class LagreVedtaksbrev(database: Database) :
    EventHandler<EventData.VedtaksbrevArbeidsgiverJournalfoert> {
    override val id = "LagreVedtaksbrev"
    ...
}
```

- Kaller `VedtaksbrevDb(database).lagre(event.id, event.data)` og svarer `success`.
- Feil i databasen gir `transientError`.
- Logger bare `sakId`. Brevdataene har navn og begrunnelse og logges ikke.
- Registreres i `configureEventHandlers` i stedet for
  `VedtaksbrevArbeidsgiverJournalfoertNoop`.
- KDoc på `VedtaksbrevArbeidsgiverJournalfoert`: «Konsument:
  [LagreVedtaksbrev]. Varslingen av arbeidsgiver kommer i kort 645.»
- `event-flow.md` regenereres med `EventFlowDiagram`.

### 3. Backend: eventen `VedtaksbrevVist`

I `event/Events.kt`:

```kotlin
/**
 * Arbeidsgiver har hentet vedtaksbrevet som PDF.
 *
 * Publiseres av [no.nav.ekspertbistand.vedtaksbrev.VedtaksbrevApi] ved hvert oppslag, og kan derfor
 * forekomme mange ganger for samme vedtak. Har per nå bare en no-op-handler.
 *
 * [vistTil] er fødselsnummeret til personen som hentet brevet (`pid` fra TokenX).
 */
@Serializable
@SerialName("vedtaksbrevVist")
data class VedtaksbrevVist(
    val sakId: String,
    val soknadId: String,
    val utfall: VedtakDomain.Utfall,
    val virksomhetsnummer: String,
    val vistTil: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

- `vistTil` er `innloggetBruker`. `sakId` og `utfall` kommer fra raden,
  `virksomhetsnummer` fra søknaden.
- No-op-handler `VedtaksbrevVistNoop` i `configureEventHandlers`.
- `event/README.md`: `VedtaksbrevVist` inn i raden for `soknadId`.

### 4. Backend: API

`vedtaksbrev/VedtaksbrevApi.kt` og `vedtaksbrev/VedtaksbrevRouting.kt` som `vedlegg/`.
`configureVedtaksbrevApiV1()` kalles fra `Application.kt` i blokken utenfor prod,
ved siden av `configureVedleggApiV1()`. Nye ruter under
`/api/soknad/v1/{id}`, bak `TOKENX_PROVIDER`:

| Metode | Sti | Svar |
|--------|-----|------|
| GET | `/vedtak` | `VedtaksbrevDto` |
| GET | `/vedtak/brev-pdf` | PDF med `Content-Disposition: attachment; filename="Vedtaksbrev ekspertbistand.pdf"`. Publiserer `VedtaksbrevVist`. |

Begge:

1. `findSoknadById(id, filterForArbeidsgiver = true)`. Null gir 404.
2. `harTilgang(virksomhetsnummer)`. Nei gir 403.
3. `vedtaksbrevDb.hentForSoknad(id)`. Null gir 404 «vedtak ikke funnet».

`brev-pdf` rendrer med
`genererVedtaksbrevPdf(vedtaksbrev.utfall.tilForelopigUtfall(), Mottaker.ARBEIDSGIVER, vedtaksbrev.brevdata.tilVedtaksbrevData())`,
uten sladding. Den publiserer `VedtaksbrevVist` i en egen transaksjon og svarer
med PDF-en.

```kotlin
@Serializable
data class VedtaksbrevDto(
    val utfall: VedtaksbrevUtfall,
    val vedtaksdato: String,
    val innvilgetBelop: String?,
    val soknadssum: String?,
    val tiltaksperiode: Periode?,
    val refusjonsfrist: String?,
    val deltakerNavn: String,
    val virksomhetsnavn: String,
) {
    @Serializable
    data class Periode(val fra: String, val til: String)
}

@Serializable
enum class VedtaksbrevUtfall { INNVILGET, INNVILGET_REDUSERT, AVSLATT }
```

- Verdiene kommer fra `VedtaksbrevDomain`: `dato`, `innvilgetBelop`,
  `soknadssum`, `tiltaksperiode`, `refusjonsfrist`, `deltaker.navn` og
  `arbeidsgiver.navn`. `begrunnelse`, `navEnhet` og `saksbehandler` står bare i
  brevet.
- `svarMedPdf` flyttes fra `SakDokumentApi.kt` til `dokument/` og brukes av
  begge.
- Svarene har `utenMellomlagring()`.
- Ingen logging av fødselsnummer, navn, begrunnelser eller brevinnhold.
  Logglinjer har bare `soknadId`.

### 5. Frontend: PDF-forhåndsvisning i v2

- Kopier `PdfForhandsvisning.tsx`, `PdfMiniatyr.tsx`, `PdfModal.tsx`,
  `PdfSider.tsx`, `pdfjs.ts`, `types.ts`, `Pdf.module.css` og `index.ts` fra
  `saksbehandling/src/components/pdf/` til `client/src/v2/components/pdf/`.
  `formatBytes` kopieres til `v2/utils/`. `index.ts` eksporterer ikke
  `PdfForhandsvisningListe`.
- `pnpm --filter client add react-pdf pdfjs-dist` med samme versjoner som
  saksbehandling.
- `pdf_forhandsvisning.md`: en merknad under åpent spørsmål 3 om at
  komponenten er kopiert til v2 i kort 693, ikke flyttet til `frontend/shared`.
- `frontend/server/src/index.ts`: `workerSrc: ["'self'", "blob:"]`, som i
  `saksbehandling-server`. `blob:` trengs når MSW styrer siden i Firefox.

### 6. Frontend: data

I `src/v2/`:

- `features/vedtak/vedtak.ts`: zod-skjema for `VedtaksbrevDto` og
  `fetchVedtaksbrevPdf(id)` med `fetchBlob`.
- `hooks/useVedtak.ts`: `useSWR` på `${EKSPERTBISTAND_API_PATH}/${id}/vedtak`.
  404 gir `null`, ikke feil.
- Søknaden hentes med den samme `useSWR`-nøkkelen som kvitteringen bruker,
  `${EKSPERTBISTAND_API_PATH}/${id}`, og gjøres om med `draftDtoToInputs`.
- `hooks/useVedtaksbrev.ts`: henter PDF-en med `useSWR` bare når kortet er
  åpent, uten revalidering ved fokus. Lager `PdfFil` med blob og object URL, og
  frigjør URL-en når komponenten forsvinner, som `useObjectUrl` i
  saksbehandling.

### 7. Frontend: `VedtakPage`

Ny rute `skjema/:id/vedtak` under `/v2` i `App.tsx`, lazy-importert som de
andre v2-sidene.

Oppbygning etter skissene, med Aksel og komponentene i `v2/components/`:

- `DecoratedPage` med tittelen «Tilskudd til ekspertbistand» og piktogrammet.
- `BackLink` «Tilbake til oversikt» til `SOKNADER_PATH`.
- Statusbanner som i kvitteringen: `Box` med `success-moderate` eller
  `danger-moderate` og `Heading` nivå 2.
- `Heading` «{innvilgetBelop} kr satt av til ekspertbistand» ved innvilget og
  redusert beløp.
- Nøkkeldata i en beskrivelsesliste (`<dl>`): Tiltaksperiode, Deltaker, Ekspert,
  Arbeidsgiver, og Søkt beløp ved redusert beløp og avslag. Eksperten kommer
  fra søknaden, resten fra `VedtaksbrevDto`. Mangler perioden eller søkt beløp,
  vises ikke raden.
- «Vilkår og informasjon om utbetaling» med teksten fra skissen ved innvilget
  og redusert beløp.
- Knappene «Send inn sluttrapport fra eksperten» og «Søk refusjon» som
  `Button as={RouterLink}` (secondary) til `/v2/skjema/:id/sluttrapport` og
  `/v2/skjema/:id/refusjon`, og `Tag` «Frist {refusjonsfrist}» (warning,
  outline, `ClockIcon`). Ikke ved avslag.
- `ExpansionCard` (small, kontrollert) for vedtaket med tittelen for utfallet
  og `vedtaksdato`. Åpent kort viser `PdfForhandsvisning` med brevet. Mens PDF-en
  hentes, vises `Skeleton` i A4-format. Feiler hentingen, vises en `Alert`.
- `ExpansionCard` (small) «Søknad sendt inn» med innsendingsdatoen fra
  søknaden og `KvitteringSummary`.
- Beløp, datoer og perioden vises som teksten fra API-et, med «kr» etter
  beløpene.
- Ved 404 navigerer siden til `/v2/skjema/:id/kvittering` med `replace`. Ved
  andre feil viser den en `Alert` som kvitteringen.

### 8. Frontend: kvitteringen

`v2/pages/KvitteringPage.tsx`:

- Henter `useVedtak(id)` når statusen er `godkjent` eller `avslatt`.
- Mens vedtaket hentes, vises `Loader` i stedet for innholdet, så
  arbeidsgiver ikke ser kvitteringen blinke før omdirigeringen.
- Vedtak finnes: `<Navigate to={`/v2/skjema/${id}/vedtak`} replace />`.
- `KvitteringSummary` flyttes til `v2/components/KvitteringSummary.tsx`.

### 9. Mocks og lokale testdata

- `mocks/handlers.ts`: `GET .../:id/vedtak` og `.../vedtak/brev-pdf` for én
  godkjent søknad per utfall. De andre gir 404.
- `LokaleSaker.kt`: én ny sak med `INNVILGE_REDUSERT` og `Beslutning.GODKJENN`,
  uten Arena-eventer. Vedtakssiden for lokale saker vises når
  `JournalfoerVedtaksbrevArbeidsgiver` og `LagreVedtaksbrev` har
  kjørt.

### 10. Tester

Backend, med `testApplicationWithDatabase` og mønsteret fra
`RefusjonStatusApiTest` og `LagreTilsagnDataTest`:

- `LagreVedtaksbrevTest`:
  - Eventen gir én rad med utfall, brevdata, tidspunkt og `event_id`.
  - Samme event to ganger gir én rad og `success` begge ganger.
- `VedtaksbrevApiTest`, med rader lagret via `VedtaksbrevDb.lagre`:
  - Hvert av de tre utfallene gir riktig `VedtaksbrevDto`. `refusjonsfrist` og
    `innvilgetBelop` er null ved avslag.
  - Ukjent søknad og utløpt `arbeidsgiverTilgangTil` gir 404.
  - Uten Altinn-tilgang gir 403, også når søknaden ikke har vedtak.
  - Søknad uten rad i `vedtaksbrev` gir 404.
  - `brev-pdf` gir PDF med `Content-Disposition: attachment` og publiserer én
    `VedtaksbrevVist` med riktig sak, søknad, utfall, virksomhet og `vistTil`
    lik `pid` i tokenet.
  - `/vedtak`, 403 og 404 publiserer ingen `VedtaksbrevVist`.
  - Eventen har `adressebeskyttelse` kode 6: brevet har deltakers navn, ikke
    `***********`.
- `RetentionTest`: raden i `vedtaksbrev` slettes med søknaden.
- `TestEventData` og `EventDataAggregateRootIdTest` får `VedtaksbrevVist`.

Frontend, med Vitest og MSW:

- `VedtakPage.test.tsx`:
  - Innvilget viser beløpet, knappene og fristen, og ikke «Søkt beløp».
  - Redusert beløp viser «Søkt beløp».
  - Avslått viser verken beløp, vilkår, knapper eller frist.
  - Brevet hentes først når vedtakskortet åpnes, og vises med filnavnet som
    nedlastingslenke.
  - 404 navigerer til kvitteringen.
- `KvitteringPage.test.tsx`: godkjent med vedtak går til vedtakssiden. Godkjent
  uten vedtak viser kvitteringen.
- `v2/isolation.test.ts` er grønn.
- Playwright: vedtakssiden er med i `e2e/a11y.spec.ts` for de tre utfallene,
  også med åpent vedtakskort.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V36__vedtaksbrev.sql` | Ny tabell |
| `backend/.../vedtaksbrev/VedtaksbrevDb.kt` | Ny: `VedtaksbrevTable`, `VedtaksbrevDb`, `Vedtaksbrev` |
| `backend/.../vedtaksbrev/VedtaksbrevApi.kt` | Ny: rutefunksjonene, `VedtaksbrevDto` og `VedtaksbrevUtfall` |
| `backend/.../vedtaksbrev/VedtaksbrevRouting.kt` | Ny: `configureVedtaksbrevApiV1()` |
| `backend/.../Application.kt` | Kaller `configureVedtaksbrevApiV1()` utenfor prod |
| `backend/.../event/handlers/LagreVedtaksbrev.kt` | Ny handler |
| `backend/.../event/Events.kt` | `VedtaksbrevVist`, no-op-handler, ny handler i stedet for no-op, KDoc |
| `backend/.../event/README.md` | `VedtaksbrevVist` i tabellen over aggregatrøtter |
| `backend/.../event/event-flow.md` | Regenerert |
| `backend/.../soknad/AggregateRetention.kt` | Kommentaren om cascade |
| `backend/.../dokument/` (ny fil) | `svarMedPdf`, flyttet fra `SakDokumentApi.kt` |
| `backend/.../saksbehandling/SakDokumentApi.kt` | Bruker den flyttede `svarMedPdf` |
| `backend/src/test/.../vedtaksbrev/VedtaksbrevApiTest.kt` | Ny |
| `backend/src/test/.../event/handlers/LagreVedtaksbrevTest.kt` | Ny |
| `backend/src/test/.../soknad/RetentionTest.kt` | Sletting av raden |
| `backend/src/test/.../event/TestEventData.kt` | `VedtaksbrevVist` |
| `backend/src/test/.../event/EventDataAggregateRootIdTest.kt` | `VedtaksbrevVist` |
| `backend/src/test/.../LokaleSaker.kt` | Sak med redusert beløp |
| `frontend/server/src/index.ts` | `workerSrc` |
| `frontend/client/package.json`, `pnpm-lock.yaml` | `react-pdf`, `pdfjs-dist` |
| `frontend/client/src/v2/components/pdf/` | Ny, kopiert fra saksbehandling |
| `frontend/client/src/v2/utils/formatBytes.ts` | Ny, kopiert fra saksbehandling |
| `specifications/pdf_forhandsvisning.md` | Merknad om kopien |
| `frontend/client/src/App.tsx` | Rute `/v2/skjema/:id/vedtak` |
| `frontend/client/src/v2/pages/VedtakPage.tsx` | Ny |
| `frontend/client/src/v2/pages/VedtakPage.test.tsx` | Ny |
| `frontend/client/src/v2/pages/KvitteringPage.tsx` | Omdirigering, `KvitteringSummary` flyttet |
| `frontend/client/src/v2/pages/KvitteringPage.test.tsx` | Ny |
| `frontend/client/src/v2/components/KvitteringSummary.tsx` | Ny, flyttet fra kvitteringen |
| `frontend/client/src/v2/features/vedtak/vedtak.ts` | Ny |
| `frontend/client/src/v2/hooks/useVedtak.ts` | Ny |
| `frontend/client/src/v2/hooks/useVedtaksbrev.ts` | Ny |
| `frontend/client/src/mocks/handlers.ts` | Vedtak for tre søknader |
| `frontend/client/e2e/a11y.spec.ts` | Vedtakssiden |

## Kanttilfeller og feilmodus

- **Vedtaket er fattet, men brevet er ikke journalført ennå.** Det finnes ingen
  rad, så API-et gir 404, og kvitteringen vises. Feiler journalføringen,
  prøver `JournalfoerVedtaksbrevArbeidsgiver` igjen, og kvitteringen vises til
  den lykkes.
- **Kode 6 eller 7.** Arbeidsgiver ser deltakers navn. Raden har brevet
  usladdet. Bare kopien i arbeidsgivermappa er sladdet.
- **Saksbehandler eller enhet endres etter vedtaket.** Brevet bygges fra raden
  og er likt det som ble journalført.
- **Eventen behandles på nytt.** `insertIgnore` lar raden stå, og handleren
  svarer `success`.
- **Arena-sak.** Ingen `VedtaksbrevArbeidsgiverJournalfoert`, og dermed ingen
  rad: 404, og kvitteringen med tilskuddsbrevet vises som i dag.
- **Søknaden er avlyst.** Kvitteringen henter ikke vedtaket.
- **Arbeidsgivers tilgang er utløpt.** `filterForArbeidsgiver` gir 404, som for
  resten av søknaden.
- **Gotenberg er nede.** `brev-pdf` gir 500, og ingen `VedtaksbrevVist`
  publiseres. Vedtakskortet viser en feilmelding. Resten av siden virker.
- **pdf.js klarer ikke å vise PDF-en.** `PdfForhandsvisning` viser «Kunne ikke
  vise forhåndsvisning.», og nedlastingslenken virker fortsatt.
- **Arbeidsgiver åpner kortet mange ganger.** Hvert oppslag gir en
  `VedtaksbrevVist`. Mens siden er åpen, gjenbruker SWR svaret, så det blir
  ikke nye oppslag ved fokus.
- **Søknaden slettes etter oppbevaringstiden.** Raden forsvinner med `sak`
  (`ON DELETE CASCADE`).
- **Søknadssum som ikke er et tall.** `soknadssum` er null, og «Søkt beløp»
  vises ikke.
- **v2 i prod.** Rutene finnes ikke, og API-et brukes ikke. API-et er likevel
  beskyttet med TokenX og Altinn.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Arbeidsgiver ser vedtaket på `/v2/skjema/:id/vedtak` for innvilget, redusert beløp og avslått, som i skissene
- [ ] Kvitteringen i v2 sender videre til vedtakssiden når saken har vedtak i ny løsning, og er uendret ellers
- [ ] Vedtakskortet viser brevet med `PdfForhandsvisning`, rendret usladdet fra `vedtaksbrev`, og brevet kan lastes ned
- [ ] `LagreVedtaksbrev` lagrer vedtaket fra `VedtaksbrevArbeidsgiverJournalfoert` idempotent, og API-et slår aldri opp i `event_log`
- [ ] Hvert oppslag på brevet publiserer `VedtaksbrevVist` med fødselsnummeret til den som så det, og `event-flow.md` er regenerert
- [ ] `PdfForhandsvisning` er kopiert til `client/src/v2/components/pdf/`, og saksbehandling er uendret
- [ ] Ingen kall til Joark fra arbeidsgiver-API-et
- [ ] API-et gir 404 uten søknad eller vedtak, og 403 uten Altinn-tilgang
- [ ] Lokale testdata og MSW-mocks har alle tre utfall
- [ ] `mvn -B package`, `pnpm --filter client typecheck`, `lint`, `test:run` og a11y-testen er grønne
