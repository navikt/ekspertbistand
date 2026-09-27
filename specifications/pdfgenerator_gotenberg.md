# PdfGenerator med Gotenberg — alternativ til dokgen

Branch: `pdfgen_gotenberg` (basert på `main`, `b012e6c`)
Alternativ til: branchen `rm_dokgen` (`specifications/pdfgenerator_erstatt_dokgen.md`), der PDF/A lages i backend med openhtmltopdf.
Denne spec-en beskriver samme mål, men lar **Gotenberg** (headless Chromium + LibreOffice) gjøre HTML → PDF/A.
Den skal gi et konkret grunnlag for å velge mellom de to løsningene. Se [Sammenligning og valgkriterier](#sammenligning-og-valgkriterier).

## Mål

Erstatte `ekspertbistand-dokgen`, som ikke vedlikeholdes lenger, med:

- **Templating i backend:** Handlebars og innpakning. Malene flyttes fra `dokgen/content/` til backend og gjøres om til **ren HTML**. Markdown-steget fra dokgen fjernes.
- **Konvertering i Gotenberg:** HTML → PDF/A-2b i en egen NAIS-app, `ekspertbistand-gotenberg`, som bare backend kan nå.

Resultatet skal:

- produsere gyldig **PDF/A-2b** med samme innhold som dagens dokgen-output for `soknad`, `tilskuddsbrev` og `arenaNotat`,
- beholde det offentlige API-et til `DokgenClient` (fire metoder), men klassen døpes om til `DokumentService`,
- aldri sende maler utenfra. Maler ligger på classpath i backend, og Gotenberg får bare ferdig rendret HTML fra backend,
- ikke la innhold fra brukere eller eksterne systemer trigge nettverkskall, fillesing eller layoutendringer,
- ha retry, timeout, metrikk og en grense på samtidige kall,
- gjøre det mulig å fjerne dokgen-appen etter verifisering i dev.

> **Styrende krav:** PDF-ene journalføres i Joark via `DokArkivClient`. Joark krever **PDF/A**.

## Bakgrunn

- dokgen er Handlebars + Markdown + openhtmltopdf bak et Spring-API. Dagens flyt:
  `DokgenClient` POSTer JSON til `http://ekspertbistand-dokgen/template/{mal}/create-pdf|create-html`.
- Gotenberg er et aktivt vedlikeholdt, stateless HTTP-API for dokumentkonvertering (Go, Chromium, LibreOffice). Siste versjon er 8.37.0 (september 2026).
  NAV bruker det allerede, for eksempel i `navikt/sosialhjelp-konvertering-til-pdf` og `navikt/sosialhjelp-upload`.
- Gotenberg kan ikke rendre Handlebars. Templating må derfor skje i backend. Det gir to deler: en **templating-kjerne i backend** og en **tynn Gotenberg-klient**.

### Beslutninger

| Beslutning | Begrunnelse |
|---|---|
| Templating i backend, konvertering i Gotenberg | Gotenberg rendrer ikke maler. Malene skal ikke sendes utenfra. |
| Gjenbruk templating-kjernen fra `rm_dokgen` | `HandlebarsConfig` og `TemplateRepository` er de samme i begge alternativer. Da blir de to sammenlignbare, og lite kode skrives to ganger. |
| Ingen Markdown, malene er ren HTML + Handlebars | Bare `tilskuddsbrev` bruker Markdown. Uten Markdown-steget kan brukertekst ikke bli `<img>`, lenker eller overskrifter, og escaping blir vanlig HTML-escaping. Én avhengighet (`commonmark`) mindre. |
| Full Gotenberg-image (ikke `-chromium`-varianten) | PDF/A-konvertering gjøres av LibreOffice-motoren i Gotenberg. |
| Egen NAIS-app med thin Dockerfile | Gir Dependabot-oppdateringer av image-taggen og samme CI-mønster som dokgen har i dag. |
| Bare tilgang fra backend, ingen utgående trafikk | Gotenberg er eksponert for HTML. Nettverkspolicyen er den viktigste SSRF-sperren. |
| `DokgenClient` omdøpes til `DokumentService` | Samme navn og pakkestruktur som i `rm_dokgen`, så alternativene kan sammenlignes direkte. |

## Arkitektur

```
ekspertbistand-backend                                   ekspertbistand-gotenberg (NAIS, namespace fager)
┌──────────────────────────────────────────────┐         ┌──────────────────────────────────────┐
│ DokumentService                               │         │ gotenberg/gotenberg:8.37.0 (full)    │
│   ├─ DTO → JsonObject                         │         │                                      │
│   ├─ DokumentRenderer (templating-kjerne)     │         │ POST /forms/chromium/convert/html    │
│   │    Handlebars (HTML) → XHTML-dokument     │ ──────▶ │   index.html + fonter (*.ttf)        │
│   └─ GotenbergClient ─────────────────────────┤ multipart│   pdfa=PDF/A-2b                      │
│        (HTML → PDF/A via HTTP)                │ ◀────── │ → application/pdf                    │
└──────────────────────────────────────────────┘         └──────────────────────────────────────┘
```

### Pakker i backend

```
no.nav.ekspertbistand.dokument.pdf          <-- GENERISK KJERNE (kopierbar mellom apper)
  ├─ DokumentRenderer.kt                    renderHtml(templateName, data, format): String  (komplett XHTML)
  ├─ TemplateRepository.kt                  maler, formats og fonter fra classpath   (fra rm_dokgen)
  ├─ HandlebarsConfig.kt                    Handlebars, helpers og escaping-strategi  (fra rm_dokgen + §4.1)
  ├─ PdfKonverterer.kt                      interface: tilPdfA(html, assets): ByteArray
  ├─ PdfGenerationException.kt                                                        (fra rm_dokgen)
  └─ README.md

no.nav.ekspertbistand.dokument.gotenberg
  └─ GotenbergClient.kt                     implementerer PdfKonverterer via Ktor HttpClient (multipart)

no.nav.ekspertbistand.dokument
  └─ DokumentService.kt                     domeneadapter: de fire metodene fra DokgenClient
```

- Kjernen (`dokument.pdf`) skal ikke importere noe fra `no.nav.ekspertbistand.*` utenfor egen pakke, og heller ikke Ktor eller Micrometer.
- `GotenbergClient` kan bruke infrastrukturen i backend (`basedOnEnv`, `HttpClientMetricsFeature`, `defaultHttpClient`), etter mønsteret i dagens `DokgenClient`.
- `PdfKonverterer` er en abstraksjon. I tester brukes en stub. Senere kan openhtmltopdf-varianten fra `rm_dokgen` pluggses inn bak samme interface.

### DokumentService (offentlig API, uendret fra `DokgenClient`)

| Metode | Kalles fra | Hva skjer |
|---|---|---|
| `genererSoknadPdf(soknad: DTO.Soknad): ByteArray` | `JournalfoerInnsendtSoknad` | render `soknad` → Gotenberg |
| `genererTilskuddsbrevPdf(tilsagnData: TilsagnData): ByteArray` | `JournalfoerTilskuddsbrev`, `JournalfoerTilskuddsbrevKildeAltinn`, `tilsagndata/Api.kt` | render `tilskuddsbrev` → Gotenberg |
| `genererTilskuddsbrevHtml(tilsagnData: TilsagnData): String` | `tilsagndata/Api.kt` (forhåndsvisning) | bare render, **ingen** Gotenberg-kall |
| `genererArenaNotatPdf(saksnummer, tiltaksgjennomfoeringId): ByteArray` | `JournalfoerNotatArenaSakOpprettet` | render `arenaNotat` → Gotenberg |

Behold `SoknadRequest` og `from(dto)` fra dagens `DokgenClient` uendret, inkludert `godkjentUtdanningEllerAutorisasjon` og `relevantKompetanse`.
Payload-feltene til malene skal være identiske med dagens HTTP-body.

## Implementasjonsplan

### 1. Hent templating-kjernen fra `rm_dokgen`

```bash
git checkout rm_dokgen -- \
  backend/src/main/kotlin/no/nav/ekspertbistand/dokument/pdf/HandlebarsConfig.kt \
  backend/src/main/kotlin/no/nav/ekspertbistand/dokument/pdf/TemplateRepository.kt \
  backend/src/main/kotlin/no/nav/ekspertbistand/dokument/pdf/PdfGenerationException.kt \
  backend/src/main/resources/dokumentmaler \
  backend/src/test/kotlin/no/nav/ekspertbistand/mocks/StubPdfGenerator.kt
```

- **Ikke** ta med `MarkdownRenderer.kt`, `PdfaRenderer.kt`, `PdfGeneratorImpl.kt`, commonmark eller openhtmltopdf-avhengighetene.
- Legg til `com.github.jknack:handlebars` i `backend/pom.xml` (samme versjon som i `rm_dokgen`, eller nyeste stabile). Det er den **eneste** nye avhengigheten i produksjonskoden.
- Testavhengigheter, alle med `<scope>test</scope>` og nyeste stabile versjon: `org.jsoup:jsoup` (HTML-validering, §7.1), `org.verapdf:validation-model` (PDF/A-validering) og `org.apache.pdfbox:pdfbox` 3.x (tekstuttrekk).
  veraPDF og PDFBox skal **ikke** havne i runtime-classpath. Ikke legg til Testcontainers-avhengigheter for Gotenberg.
- Kopier malene fra `dokgen/content/templates` på `main` hvis de er nyere enn de i `rm_dokgen`. Fra nå er `backend/src/main/resources/dokumentmaler` kilden. `dokgen/content` fryses og brukes bare av den kjørende dokgen-appen til den fjernes i §8.
- `TemplateRepository.templates`: bruk `ConcurrentHashMap`, ikke `HashMap`.
- `StubPdfGenerator` gjøres om til `StubPdfKonverterer` (returnerer fast `%PDF-mock`).

### 1.1 Gjør malene om til ren HTML

Malene skal være HTML med Handlebars-uttrykk, uten Markdown. Status per mal:

| Mal | Markdown i dag | Tiltak |
|---|---|---|
| `arenaNotat` | Nei | Ingen |
| `soknad` | Nei | Rett feltnavnet `opprettetDato` (se under) |
| `tilskuddsbrev` | Ja (linje 17–54 og 70) | Konverter som beskrevet under |

Konvertering av `tilskuddsbrev/template.hbs`. Resultatet skal gi samme HTML som commonmark ga, så utseendet blir uendret:

- `# Dere har fått innvilget tilskudd til ekspertbistand` → `<h1>…</h1>`
- `### Hvordan kan dere få utbetalt pengene?` → `<h3>…</h3>`
- Hvert tekstavsnitt (tekst adskilt av blanke linjer) → `<p>…</p>`. Linjeskift inne i et avsnitt (linje 45–46) er mellomrom i samme `<p>`.
- Lenker `[tekst](url "tittel")` → `<a href="url" title="tittel">tekst</a>`. Det gjelder fire lenker: forskrift om ekspertbistand, skjema for refusjonskrav,
  «Send dokumenter til Nav om oppfølging» og «Tilskudd til ekspertbistand - nav.no». URL-ene er faste tekster i malen, ikke data.
- Løs tekst etter tabellen på linje 70 («Hvis dette er feil …») → `<p>…</p>`.
- Behold eksisterende `<br/>`, tabeller og inline-stiler uendret.
- Oppdater `testdata/*.json` bare hvis det trengs. Konverteringen skal ikke endre hvilke felt malen bruker.

**Eksisterende feil i `soknad`:** Malen leser `{{opprettetDato}}` (linje 96, og slik står det i `schema.json` og `testdata`), men `SoknadRequest` sender `opprettetTidspunkt`.
Dermed står det trolig bare «Sendt inn til Nav» uten dato på dagens søknader. Rett det ved å døpe om feltet i `SoknadRequest` til `opprettetDato` (verdien er allerede en dato, `yyyy-MM-dd`).
Vurder `{{norwegian-date opprettetDato}}` i malen for formatet `dd.MM.yyyy`. Dekk det med en test.

Legg til en test som feiler hvis en `.hbs` under `dokumentmaler/templates/` inneholder Markdown-overskrifter (`^\s*#{1,6}\s`) eller Markdown-lenker (`\]\(`).

### 1.2 README med sikkerhetsregler i malmappa

Lag `backend/src/main/resources/dokumentmaler/templates/README.md`. Den er for alle som endrer eller lager maler, også folk som ikke kjenner resten av løsningen.
Den skal være kort, handlingsrettet og skrevet på norsk. Den skal minst dekke dette:

**Innledning (2–4 setninger)**

- Malene rendres med Handlebars til HTML. Gotenberg (Chromium) gjør HTML-en om til PDF/A, og PDF-en journalføres i Joark.
- Verdiene i malene kommer blant annet fra fritekst skrevet av arbeidsgivere. De må behandles som **upålitelig input**.
- Malene er ren HTML. Markdown støttes ikke.

**Gjør (✅)**

- Bruk alltid `{{verdi}}`. Handlebars HTML-escaper verdien, og linjeskift blir `<br/>`.
- Plasser verdier bare som **tekstinnhold** i elementer (`<td>{{navn}}</td>`, `<p>{{begrunnelse}}</p>`).
- Skriv lenker som faste `https://`-URL-er direkte i malen.
- Bruk fontene som finnes (Source Sans Pro) og CSS-en i `formats/pdf/style.css`. Legg justeringer i `formats/pdf/chromium.css`.
- Bilder og logoer legges inn som inline `<svg>` eller `data:`-URI.
- Legg til eller oppdater `testdata/*.json` når du endrer en mal, med både utfylte og tomme valgfrie felt. Kjør `DokumentmalTest`.
- Nye felt i malen må også finnes i payloaden fra `DokumentService`. Testen stopper deg ellers.
- Se over snapshot-diffen i PR-en (`dokumentmaler-snapshots/`).

**Ikke gjør (❌), med kort begrunnelse for hver**

- ❌ `{{{verdi}}}` (triple-stash). Det skrur av escaping, og brukertekst kan da bli HTML. Stoppes av test.
- ❌ Verdier i attributter: `href="{{…}}"`, `src="{{…}}"`, `style="…{{…}}…"`, `class="{{…}}"`, `on*=`. Escaping beskytter ikke mot `javascript:`-URL-er eller CSS-injeksjon.
- ❌ Verdier i `<style>`, `<script>`, HTML-kommentarer eller `<svg>`.
- ❌ `<script>`, `<iframe>`, `<object>`, `<embed>`, `<link rel="stylesheet">`, `<base>` og `<meta http-equiv>`. JavaScript er slått av i Gotenberg, og testen avviser elementene.
- ❌ Eksterne ressurser: `http(s)://`- eller `file:`-URL-er i `src` eller CSS `url(…)`, og `@import`. Gotenberg har ikke nettverkstilgang, så de feiler, og de er en SSRF-risiko.
- ❌ Markdown (`# overskrift`, `[tekst](url)`, `**fet**`). Det rendres ikke og stoppes av test.
- ❌ `Handlebars.SafeString` eller nye helpers som returnerer uescapet HTML fra data.
- ❌ Personopplysninger i `testdata` som ikke er syntetiske. Bruk fiktive navn og fødselsnumre.
- ❌ Å endre `dokgen/content/**`. Den mappa er frosset og slettes.

**Ny mal: sjekkliste**

1. Lag `templates/{navn}/template.hbs` og `templates/{navn}/testdata/default.json` (pluss varianter).
2. Legg til metode og payload-mapping i `DokumentService`.
3. Kjør `DokumentmalTest` og snapshot-oppdatering, og se over diffen.
4. Render lokalt med `RenderDokumentmaler` (§7.2) og se på PDF-en.
5. Be om review fra noen i teamet på både malen og payloaden.

**Hvor reglene håndheves:** en tabell som kobler hver regel til testen eller konfigurasjonen som håndhever den
(`DokumentmalTest`, `DokumentRendererSikkerhetTest`, `EscapingStrategy`, Gotenberg-variablene i §4.2, nettverkspolicyen i §4.3).
Regler uten automatisk håndhevelse (for eksempel syntetiske testdata) merkes «manuell review».

I tillegg:

- Lenk til README-en fra `dokument/pdf/README.md` og fra `backend/README.md`.
- `TemplateRepository` og `DokumentmalTest` finner maler via `templates/*/template.hbs`. README-en på rotnivå skal derfor ikke forveksles med en mal. Dekk det med en test.
- README-en er dokumentasjon og skal gjerne ligge med i jar-en, men den skal ikke leses av koden.

### 2. DokumentRenderer

- `renderHtml(templateName, data, format: Format)` der `Format` er `PDF` eller `HTML`. Returnerer et komplett XHTML-dokument.
  - Pipeline: `TemplateRepository.loadTemplate` → `HandlebarsConfig.render` → innpakning. Ingen Markdown.
  - For `PDF`: `formats/pdf/style.css`, `header.html` og `footer.html` pakkes inn i `<body>` som i dag (NAV-logoen er inline SVG i `#header`).
    I tillegg kommer et `@font-face`-blokk som refererer fontene **bare med filnavn** (`src: url('SourceSansPro-Regular.ttf')`), ett per vekt og stil.
  - For `HTML`: `formats/html/style.css`, uten header og footer.
- `assetsForPdf(): List<Asset>` returnerer fontene fra `TemplateRepository.fonts` som `Asset(fileName, bytes, contentType = "font/ttf")`.

### 3. GotenbergClient

`POST {baseUrl}/forms/chromium/convert/html` som `multipart/form-data`:

| Del | Verdi |
|---|---|
| fil `index.html` | XHTML fra `DokumentRenderer` |
| filer `SourceSansPro-*.ttf` | fra `assetsForPdf()`. Refereres fra `@font-face` med filnavn. Gotenberg legger alle filer i samme katalog. |
| `paperWidth` / `paperHeight` | `8.27in` / `11.69in` (A4) |
| `marginTop` / `marginRight` / `marginBottom` / `marginLeft` | tilsvarende dagens `@page { margin: 64px 64px 74px 64px }`. Se §5 om enheter. |
| `preferCssPageSize` | `false` (A4 styres av skjemafeltene, ikke av `@page`) |
| `printBackground` | `true` |
| `pdfa` | `PDF/A-2b` |
| `failOnResourceLoadingFailed` | `true` |
| `failOnConsoleExceptions` | `true` |
| `skipNetworkIdleEvent` | `true` (standard) |

- Send header `Gotenberg-Trace: <callId/traceId>`, så logger kan korreleres.
- `baseUrl = basedOnEnv(prod = "http://ekspertbistand-gotenberg", dev = "http://ekspertbistand-gotenberg", other = "http://localhost:3003")`.
- `HttpTimeout`: `requestTimeoutMillis = 30_000`. Gotenberg har selv `API_TIMEOUT=30s`.
- `HttpClientMetricsFeature` med `clientName = "gotenberg.client"`.
- Retry: `HttpRequestRetry` på 503 og nettverksfeil, maks 3 forsøk med eksponentiell backoff. **Ikke** retry på 4xx.
- Svaret skal ha `Content-Type: application/pdf` og starte med `%PDF`. Ellers kastes `PdfGenerationException` med HTTP-status og malnavn, men **uten** dokumentinnhold.
- En `kotlinx.coroutines.sync.Semaphore` (standard 4) i `DokumentService` begrenser samtidige kall til Gotenberg.

### 4. Sikkerhet

> 🔴 **Rød sone.** Fritekst fra arbeidsgiver ender i HTML som rendres av Chromium. Les gjennom escaping, Gotenberg-flagg og nettverkspolicy grundig før merge.

#### 4.1 Escaping i backend

Uten Markdown er HTML-escapingen i Handlebars det eneste som står mellom brukertekst og HTML-en. Den må være på for alle verdier.

- Lag en egen `EscapingStrategy` i kjernen og registrer den som standard i `HandlebarsConfig`:
  1. HTML-escape (`&`, `<`, `>`, `"`, `'`, `` ` ``, `=`), som Handlebars sin standard.
  2. Linjeskift: `\r\n` og `\r` → `\n`, og deretter `\n` → `<br/>`. Brukerens linjeskift i fritekst (for eksempel `begrunnelse`) blir synlige i PDF-en.
     Dette skjer **etter** HTML-escaping, så `<br/>` er det eneste HTML-elementet en verdi kan gi.
- Triple-stash `{{{…}}}` er forbudt i maler. En test skal feile hvis en `.hbs` inneholder `{{{`.
- Helpers som returnerer tekst fra data (`dateFormat`, `norwegian-date`) skal returnere vanlig `String`, ikke `Handlebars.SafeString`, slik at de escapes.
- Verdier skal aldri brukes i attributter som `href`, `src` eller `style` i malene. URL-er i malene er faste tekster.

#### 4.2 Gotenberg-konfigurasjon (miljøvariabler i nais)

| Variabel | Verdi | Hvorfor |
|---|---|---|
| `CHROMIUM_DISABLE_JAVASCRIPT` | `true` | Malene trenger ikke JS. Fjerner en hel angrepsflate. |
| `CHROMIUM_ALLOW_LIST` | `^file:///tmp/.*` | Chromium får bare laste filene i forespørselen (Gotenberg legger dem i `/tmp`). |
| `CHROMIUM_DENY_LIST` | standard (`^file:(?!//\/tmp/).*`) | Beholdes i tillegg. |
| `API_DISABLE_DOWNLOAD_FROM` | `true` | Ingen nedlasting av filer fra URL-er. |
| `WEBHOOK_DISABLE` | `true` | Ingen callbacks ut av poden. |
| `LIBREOFFICE_DISABLE_ROUTES` | `true` | Office-konverteringsrutene trengs ikke. **Verifiser** at PDF/A fra Chromium-ruten fortsatt virker (LibreOffice-motoren brukes til PDF/A). Hvis ikke: la den stå `false`, og stol på nettverkspolicyen. |
| `CHROMIUM_MAX_CONCURRENCY` | `2` | Tilpasset memory-limit. |
| `CHROMIUM_RESTART_AFTER` | `100` (standard) | Begrenser minnelekkasjer. |
| `LOG_STD_FORMAT` | `json` | Loki. |
| `API_DISABLE_HEALTH_CHECK_ROUTE_TELEMETRY` | `true` (standard) | Mindre loggstøy. |

- Bruk alltid en patchet versjon. Pin versjonen, helst med digest. Kjente sårbarheter i eldre versjoner: permissiv deny-list før 8.1.0 (GHSA-rh2x-ccvw-q7r3) og SSRF via redirect på URL-ruten (GHSA-chwh-f6gm-r836).
- Backend bruker **aldri** `/forms/chromium/convert/url`.

#### 4.3 Nettverkspolicy (nais)

- `accessPolicy.inbound.rules`: bare `ekspertbistand-backend`.
- `accessPolicy.outbound`: bare `logging` i `nais-system` (som i dagens dokgen-yaml). **Ingen** `external`-regler.
  Dermed kan Chromium ikke nå internett eller andre tjenester, selv om escaping eller allow-list skulle svikte.
- Ingen `ingresses`.

### 5. Visuell likhet: kjente fallgruver

- **Enheter:** `style.css` har `@page { size: 595px 842px }`. Det er A4 målt i **punkter**, fordi openhtmltopdf i dokgen tolker px omtrent som pt.
  I Chromium er 1 px = 0,75 pt, så CSS-en ville gitt en for liten side. Derfor `preferCssPageSize=false` og A4 via skjemafeltene.
  Tekst og marger i px blir da relativt mindre enn i dagens PDF-er.
  - Start med `scale=1.333` (Gotenberg-feltet `scale`) og marger omregnet fra dagens verdier.
  - Alternativt: en egen `formats/pdf/chromium.css` som overstyrer px-verdier. Ikke endre `style.css`, som deles med `dokgen/` til den er fjernet.
  - Akseptkriteriet er visuell sammenligning med dagens dokgen-PDF for alle `testdata/*.json` (se §7.2).
- **Fonter:** Chromium bruker bare fonter som er sendt med og referert i `@font-face`. `* { font-family: "Source Sans Pro" … !important }` i `style.css` står fast.
- **PDF/A via LibreOffice:** LibreOffice rasteriserer tabellceller med bakgrunnsfarge. Sjekk at malene ikke har bakgrunnsfarge på celler, eller godta at de blir rasterisert.
- **Header og footer:** Dagens `header.html` og `footer.html` er vanlig innhold i `<body>`, ikke Chromium-header og -footer. Send dem **ikke** som `header.html`/`footer.html`-filer til Gotenberg.
  Chromium-header og -footer kan bare bruke systemfonter og inline data.

### 6. Drift: ny NAIS-app `ekspertbistand-gotenberg`

- `gotenberg/Dockerfile`:
  ```dockerfile
  FROM gotenberg/gotenberg:8.37.0
  ```
  Pin gjerne `@sha256:…`. Legg `gotenberg/` til i Dependabot (`.github/dependabot.yml`, `package-ecosystem: docker`).
- `nais/dev-gcp-gotenberg.yaml` og `nais/prod-gcp-gotenberg.yaml`, basert på `nais/*-gcp-dokgen.yaml`:
  - `port: 3000`, `liveness.path: /health`, `readiness.path: /health`
  - `resources.requests.memory: 1Gi`, `limits.memory: 2Gi`. Juster etter måling.
  - `replicas: { min: 1, max: 2 }` i dev og `{ min: 2, max: 4 }` i prod
  - miljøvariabler fra §4.2, access policy fra §4.3
  - `observability.logging.destinations: [loki]`
  - Gotenberg kjører som bruker `gotenberg` (uid 1001) og skriver til `/tmp`. **Verifiser** at poden starter med NAIS sin standard security context
    (read-only root filesystem, `/tmp` som emptyDir). Sett `HOME=/tmp` hvis Chromium eller LibreOffice trenger en skrivbar profilkatalog.
- `.github/workflows/cicd-gotenberg.yaml` og `deploy-dev-manual-gotenberg.yml`, som kopier av dokgen-workflowene med `image_suffix: gotenberg`.
- I `nais/{dev,prod}-gcp-backend.yaml`: legg `ekspertbistand-gotenberg` til `accessPolicy.outbound.rules`. Behold `ekspertbistand-dokgen` til §8.
- **Lokalt og i CI, på samme måte som Postgres.** Ikke bruk Testcontainers.
  - `backend/docker-compose.yml`: legg til tjenesten `gotenberg` ved siden av `postgres`. `dokgen` beholdes inntil videre.
    ```yaml
      gotenberg:
        image: gotenberg/gotenberg:8.37.0
        ports:
          - "3003:3000"
        environment:        # samme verdier som i nais (§4.2)
          CHROMIUM_DISABLE_JAVASCRIPT: "true"
          CHROMIUM_ALLOW_LIST: "^file:///tmp/.*"
          API_DISABLE_DOWNLOAD_FROM: "true"
          WEBHOOK_DISABLE: "true"
          LIBREOFFICE_DISABLE_ROUTES: "true"
          CHROMIUM_MAX_CONCURRENCY: "2"
    ```
  - **GitHub Actions (påkrevd for integrasjonstesten):** `.github/workflows/cicd-backend.yaml` kjører `mvn -B package` i jobben `build`, og den har bare `postgres` som service i dag.
    Uten Gotenberg som service vil integrasjonstesten feile i CI. Legg `gotenberg` til under `jobs.build.services` ved siden av `postgres`:
    ```yaml
    jobs:
      build:
        services:
          postgres:
            # … uendret …
          gotenberg:
            image: gotenberg/gotenberg:8.37.0
            env:
              CHROMIUM_DISABLE_JAVASCRIPT: "true"
              CHROMIUM_ALLOW_LIST: "^file:///tmp/.*"
              API_DISABLE_DOWNLOAD_FROM: "true"
              WEBHOOK_DISABLE: "true"
              LIBREOFFICE_DISABLE_ROUTES: "true"
              CHROMIUM_MAX_CONCURRENCY: "2"
            options: >-
              --health-cmd "curl -fsS http://localhost:3000/health || exit 1"
              --health-interval 10s
              --health-timeout 5s
              --health-retries 10
            ports:
              - 3003:3000
    ```
    - GitHub venter til helsesjekken er grønn før stegene starter. Gotenberg bruker noen sekunder på å starte Chromium og LibreOffice, derfor `health-retries: 10`.
    - Verifiser at `curl` finnes i imaget. Hvis ikke, bytt helsesjekken til et eget steg før `mvn`:
      `timeout 60 bash -c 'until curl -fsS http://localhost:3003/health; do sleep 2; done'`.
    - Per i dag er `cicd-backend.yaml` den eneste workflowen som kjører backend-testene. `codeql.yml` kjører bare `mvn compile` og trenger ikke Gotenberg. Kommer det nye workflows som kjører testene, må de få samme service.
    - Verifiser i en PR at jobben `build` kjører integrasjonstesten mot service-containeren. Den skal ikke hoppes over.
  - Tester når Gotenberg på `http://localhost:3003` (`GotenbergClient.baseUrl` for `other`), slik Postgres-testene når `localhost:5532`.
    Utviklere kjører `docker compose -f backend/docker-compose.yml up -d` før `mvn verify`, som i dag. Dokumenter det i `backend/README.md`.
  - Hold image-taggen lik i `gotenberg/Dockerfile`, `docker-compose.yml` og `cicd-backend.yaml`. Dependabot oppdaterer Dockerfile. De to andre oppdateres i samme PR, eventuelt med en test eller et skript som sjekker at taggene er like.

### 7. Testing

#### 7.1 Enhetstest av alle maler (`DokumentmalTest`, uten Gotenberg)

Målet er å fange feil i malene før de når Gotenberg eller Joark. Det gjelder felt som ikke finnes i dataene, referanser som ikke kan løses, og HTML som ikke er gyldig.
Testen kaller **ikke** Gotenberg. Den validerer HTML-en fra `DokumentRenderer.renderHtml(mal, data, Format.PDF)`, og for `tilskuddsbrev` også `Format.HTML`.

**Hvilke maler og data:**

- Testen er parametrisert (`@ParameterizedTest` + `@MethodSource`) og finner **alle** maler under `dokumentmaler/templates/*/template.hbs` automatisk.
  En ny mal blir dermed testet uten at noen må huske å legge den til.
- En mal uten minst én `testdata/*.json` gir feil i testen.
- Hver mal rendres med to typer data:
  1. **Alle `testdata/*.json`** for malen.
  2. **Ekte payload fra adapteren:** `DokumentService` med en `PdfKonverterer`-stub som fanger `JsonObject`, kjørt med eksempel-DTO-er
     (`DTO.Soknad`, `TilsagnData`, arenaNotat-parametere). Da testes feltnavnene backend faktisk sender, ikke bare testdata som kan ha drevet fra koden.
     Dette ville fanget `opprettetDato`-feilen i §1.1.
- For `soknad` skal det finnes testdata både **med** og **uten** valgfrie felt (`beliggenhetsadresse = null`, tomme lister), så begge greiner rendres.

**Handlebars-referanser skal kunne løses:**

- Testen bygger `HandlebarsConfig` i **strict mode** (`Handlebars#strictMode(true)` eller tilsvarende i valgt versjon). Da kaster rendering `HandlebarsException`
  når en variabel eller helper ikke finnes i dataene. Det gjelder også `{{#each}}`/`{{#if}}` på felt som mangler, og partials (`{{> …}}`) som ikke finnes.
- Tillatte `null`-verdier (for eksempel `beliggenhetsadresse`) skal ikke gi feil. Hvis strict mode ikke skiller mellom «felt mangler» og «felt er `null`»,
  lag en `ValueResolver`-wrapper i testen som registrerer stier som mangler helt og feiler på dem. `null` skal slippe gjennom.
- Produksjon kjører uten strict mode, så et uventet manglende felt gir tom tekst i stedet for en feilet journalføring.
  Testen er det som sikrer at det ikke skjer.
- Output skal ikke inneholde rester av Handlebars-syntaks (`{{` eller `}}`).

**HTML-validering av resultatet:**

- **Velformet XHTML:** Parse med JDKs `DocumentBuilderFactory` (namespace-aware). Slå av DTD og eksterne entiteter
  (`disallow-doctype-decl` er ikke mulig fordi dokumentet har `<!DOCTYPE html>`, så sett `load-external-dtd=false`, `external-general-entities=false`, `external-parameter-entities=false`).
  Parse-feil skal feile testen med linje og kolonne.
- **Ingen referanser som ikke kan løses.** Undersøk med jsoup (`org.jsoup:jsoup`, test-scope) eller XPath:

  | Hva | Krav |
  |---|---|
  | `src` / `href` på `img`, `link`, `script`, `iframe`, `frame`, `embed`, `object`, `source`, `video`, `audio`, `input`, `use` | Ikke tillatt, med unntak av `data:`-URI-er. `<script>`, `<iframe>`, `<object>`, `<embed>` og `<link rel="stylesheet">` skal ikke finnes i det hele tatt. |
  | CSS `url(…)` i `<style>` og `style`-attributter | Bare `data:`-URI-er eller filnavn som finnes i `DokumentRenderer.assetsForPdf()` (fontene). Relative stier, `/…`, `http(s):` og `file:` gir feil. |
  | CSS `@import` | Ikke tillatt |
  | `@font-face` | Hver `font-family`/`font-weight`/`font-style` som brukes, peker på et filnavn i `assetsForPdf()`, og hver font i `assetsForPdf()` er referert. |
  | `<a href>` | Bare absolutte `https://`-URL-er, og bare slike som står fast i malen. Ingen `javascript:`, `data:`, `file:` eller relative lenker. |
  | `id`-referanser (`href="#…"`, `xlink:href="#…"` i SVG) | Må peke på en `id` som finnes i dokumentet |

- **Innhold:** Nøkkelverdier fra data finnes i teksten (for eksempel virksomhetsnavn og saksnummer). Ingen tekstnode er bokstavelig `null`.
  NAV-logoen (`svg#nav_logo`) finnes i PDF-varianten.
- **Ingen Markdown og ingen triple-stash:** Ingen `.hbs` inneholder `{{{`, Markdown-overskrifter (`^\s*#{1,6}\s`) eller Markdown-lenker (`\]\(`). Se §1.1 og §4.1.

**Snapshot (valgfritt, anbefalt):** Lagre rendret HTML per mal og testdata som `src/test/resources/dokumentmaler-snapshots/{mal}-{testdata}.html`
og sammenlign i testen. Oppdater med en egen systemvariabel (`-DoppdaterSnapshots=true`). Da vises alle endringer i malene i PR-diffen.

#### 7.2 Øvrige tester

- **Enhetstester (uten Gotenberg):**
  - `DokumentServiceTest`: riktig mal og payload, med `StubPdfKonverterer`. Portert fra dagens `DokgenClientTest`, inkludert listefeltene.
  - `GotenbergClientTest` med Ktor `MockEngine`: multipart-delene (filnavn og skjemafelt fra §3), retry på 503, ingen retry på 400, feil ved svar uten `%PDF`.
  - Sikkerhet (`DokumentRendererSikkerhetTest`) mot `renderHtml`:

    | Input i et fritekstfelt | Forventet |
    |---|---|
    | `![x](http://127.0.0.1/x.png)` | Bokstavelig tekst, ingen `<img>` |
    | `[klikk](javascript:alert(1))` | Bokstavelig tekst, ingen `<a>` |
    | `# Overskrift` / `* punkt` | Bokstavelig tekst, ingen `<h1>` eller `<li>` |
    | `<script>alert(1)</script>` / `<iframe src="file:///etc/passwd">` | Escapet, vises som tekst |
    | `linje1\n\nlinje2` i en tabellcelle i `soknad` | `<br/>` i samme celle, tabellen er intakt |
    | `Navn & Sønn AS`, `a_b*c`, `«sitat»` | Vises korrekt uten synlige escape-tegn |

- **Integrasjonstest med ekte Gotenberg:** mot Gotenberg fra `docker-compose` lokalt og service-containeren i CI (§6). **Ikke Testcontainers.** Testen kjøres som en del av `mvn verify`, som Postgres-testene.
  - Alle maler og testdata → PDF. Valider som **PDF/A-2b** med veraPDF (`org.verapdf:validation-model`, test-scope).
  - Ekstraher tekst med PDFBox (`org.apache.pdfbox:pdfbox` 3.x, test-scope) og sjekk nøkkelverdier fra testdata.
  - Negativtest: send HTML med `<img src="http://example.com/x.png">` og `<iframe src="file:///etc/passwd">` direkte til `GotenbergClient`.
    Forventet: feil (på grunn av `failOnResourceLoadingFailed`) eller PDF uten innholdet. Ingen innhold fra `/etc/passwd` i PDF-en.
- **Visuell sammenligning:** Et kjørbart program `backend/src/test/kotlin/no/nav/ekspertbistand/executables/RenderDokumentmaler.kt` rendrer alle testdata via lokal Gotenberg
  (docker-compose) til `backend/target/dokumentmaler-preview/gotenberg/`. Legg dagens dokgen-PDF-er (fra lokal dokgen på port 9000) i
  `…/dokgen/` ved siden av, så de kan sammenlignes manuelt. Dokumenter kjøringen i `backend/README.md`.

### 8. Opprydding (egen PR, etter verifisering i dev)

- Slett `dokgen/`, `nais/*-gcp-dokgen.yaml`, `cicd-dokgen.yaml`, `deploy-dev-manual-dokgen.yml`, dokgen i `backend/docker-compose.yml`
  og `ekspertbistand-dokgen` i backendens outbound rules.
- Slett NAIS-appen `ekspertbistand-dokgen` i dev og prod manuelt.

## Sammenligning og valgkriterier

| | Gotenberg (denne branchen) | openhtmltopdf i backend (`rm_dokgen`) |
|---|---|---|
| Ekstra app å drifte | **Ja** (`ekspertbistand-gotenberg`, cirka 1,5 GB image, 1–2 Gi minne) | Nei |
| Nettverkshopp og retry | Ja | Nei |
| CSS-støtte | Moderne (flex, grid) | Omtrent CSS 2.1 |
| Lik output som i dag | Krever tilpasning (px/pt-skalering, §5) | Samme motor som dokgen, nesten lik |
| PDF/A | Via LibreOffice-motoren (kan rasterisere celler med bakgrunn) | Native i openhtmltopdf |
| Sikkerhetsflate | Chromium, men uten JS, med allow-list og uten utgående nett | openhtmltopdf, med URI-sperre i koden |
| Vedlikehold av motor | Gotenberg-teamet (aktivt, hyppige releaser) | openhtmltopdf-forken (aktiv, mindre miljø) |
| Kjøretidsrisiko i backend | Ingen AWT, Batik eller ICC i backend | AWT, ICC og Batik i Chainguard-JRE må verifiseres |

Kjør begge alternativene mot samme testdata før valget tas, og vurder:

1. veraPDF-godkjent PDF/A-2b for alle maler.
2. Visuell likhet med dagens dokgen-PDF-er.
3. Ressursbruk og ventetid per dokument (p95) i dev.
4. Driftskostnad: ekstra app, image-oppdateringer og alarmer.

## Utenfor scope

- Å bruke Gotenberg til noe annet enn HTML → PDF/A, for eksempel konvertering av vedlegg.
- Å endre innholdet i malene. Tillatt er konverteringen fra Markdown til HTML (§1.1), rettingen av `opprettetDato` og en eventuell `chromium.css` (§5).
- Schema-validering mot `schema.json`.
- Å gjøre Gotenberg tilgjengelig for andre apper.

## Akseptansekriterier

- [ ] `mvn -f backend/pom.xml verify` er grønn, inkludert Gotenberg-integrasjonstesten. Gotenberg kjører fra `docker-compose` lokalt og som service-container i `cicd-backend.yaml`. Testcontainers brukes ikke.
- [ ] Gotenberg er satt opp som service i alle GitHub-workflows som kjører backend-testene, med helsesjekk, og integrasjonstesten kjører grønt i CI.
- [ ] `org.verapdf:validation-model`, `org.apache.pdfbox:pdfbox` og `org.jsoup:jsoup` har `<scope>test</scope>`. `mvn dependency:tree -Dscope=runtime` viser ingen av dem.
- [ ] `DokumentService` har de fire metodene med uendrede signaturer. `DokgenClient` og pakken `no.nav.ekspertbistand.dokgen` er fjernet.
- [ ] Kjernen `dokument.pdf` importerer ingenting fra resten av ekspertbistand, Ktor eller Micrometer.
- [ ] Alle maler og testdata gir PDF/A-2b som godkjennes av veraPDF.
- [ ] `DokumentmalTest` (§7.1) er grønn for alle maler, med både testdata og ekte payload fra `DokumentService`, i strict mode og uten referanser som ikke kan løses.
- [ ] Sikkerhetstestene (§7.2) er grønne. Ingen `.hbs` bruker `{{{` eller Markdown.
- [ ] Ingen commonmark-avhengighet i `backend/pom.xml`. `tilskuddsbrev` er ren HTML med samme utseende som før.
- [ ] Søknads-PDF-en viser innsendingsdato («Sendt inn til Nav dd.MM.yyyy»).
- [ ] `dokumentmaler/templates/README.md` finnes med gjør/ikke gjør, sjekkliste for ny mal og tabell over håndhevelse (§1.2), og er lenket fra `dokument/pdf/README.md` og `backend/README.md`.
- [ ] Gotenberg kjører med miljøvariablene i §4.2 og nettverkspolicyen i §4.3.
- [ ] Visuell sammenligning mot dagens dokgen-PDF-er er gjort og godkjent av teamet.
- [ ] Verifisert i dev: søknad journalføres, og tilskuddsbrev-forhåndsvisning vises, uten feil.
- [ ] Metrikk for `gotenberg.client` vises i Grafana. Retry på 503 er verifisert.
- [ ] (PR 2) dokgen-appen, nais-filer, workflows og access policies er fjernet.

## For implementøren (GitHub Copilot)

- Rekkefølge: §1 → §2 → §7.1 (skriv maltesten, se at den feiler på `opprettetDato`) → §1.1 → §1.2 → §4.1 → §3 → §7.2 (enhetstester) → §6 → §7.2 (integrasjon og visuell sammenligning) → §5-justeringer. §8 er egen PR.
- Gjenbruk kode fra `rm_dokgen` med `git checkout rm_dokgen -- <sti>` i stedet for å skrive den på nytt.
- Følg mønsteret i dagens `DokgenClient` for HTTP-klient, `basedOnEnv`, metrikk og timeout.
- Ikke kall `/forms/chromium/convert/url`, og ikke legg til HTTP-endepunkter for PDF-generering i backend.
- Ikke endre `dokgen/content/**` eller `formats/pdf/style.css`. Malendringer (§1.1) gjøres bare i `backend/src/main/resources/dokumentmaler`. Visuelle justeringer gjøres i en egen `chromium.css`.
- Ikke legg til Markdown-støtte eller commonmark.
- Ikke bruk Testcontainers for Gotenberg. Bruk `docker-compose` lokalt og en service-container i CI, som for Postgres.
- veraPDF, PDFBox og jsoup skal bare være testavhengigheter.
- Hold `templates/README.md` i sync med testene. Blir en regel håndhevet av en ny test, oppdater tabellen over håndhevelse.
- Testnavn skrives på norsk med backticks.
