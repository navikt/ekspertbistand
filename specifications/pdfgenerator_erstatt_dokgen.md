# PdfGenerator — erstatt dokgen med innebygd PDF/A-generering

Branch: `rm_dokgen` (rebaset på `main` 2026-09-26)
Status: kjerne, adapter og maler er på plass. Det som gjenstår er å bygge og verifisere, bytte bibliotek,
herde sikkerheten, gjøre genereringen klar for drift og rydde opp. Se [Arbeid som gjenstår](#arbeid-som-gjenstår).

## Mål

Erstatte den separate `ekspertbistand-dokgen`-tjenesten med dokumentgenerering inne i `ekspertbistand-backend`.

Resultatet skal:

- produsere gyldig **PDF/A-2b** med samme innhold som dagens dokgen-output for `soknad`, `tilskuddsbrev` og `arenaNotat`,
- gjenbruke eksisterende Handlebars-maler, fonter og CSS,
- bare kunne kalles fra kode (`DokumentService` → `PdfGenerator`). Det skal **ikke** finnes noe HTTP-API for
  PDF-generering, og maler kan **ikke** sendes inn utenfra. De ligger på classpath,
- ikke la innhold fra brukere eller eksterne systemer trigge nettverkskall eller endre layouten i dokumentet,
- være trådsikker, begrenset i parallellitet og målt,
- holde et tydelig skille mellom en **generisk kjerne**, som kan kopieres til andre apper, og en **domeneadapter**,
- gjøre det mulig å fjerne dokgen-appen, dens nais-oppsett, workflows og access policies.

## Bakgrunn

`ekspertbistand-dokgen` er bygget på `ghcr.io/navikt/dokgen/dokgen` + `COPY content content` (`dokgen/Dockerfile`),
deployes som egen NAIS-app (`nais/{dev,prod}-gcp-dokgen.yaml`) og har egen CI (`.github/workflows/cicd-dokgen.yaml`,
`deploy-dev-manual-dokgen.yml`). dokgen vedlikeholdes ikke lenger. Internt er den en tynn Spring-wrapper rundt
Handlebars, Markdown og openhtmltopdf, så vi kan kjøre samme pipeline i backend.

> **Styrende krav:** PDF-ene journalføres i Joark via `DokArkivClient`
> (`JournalfoerInnsendtSoknad`, `JournalfoerTilskuddsbrev`, `JournalfoerTilskuddsbrevKildeAltinn`,
> `JournalfoerNotatArenaSakOpprettet`). Joark krever **PDF/A**. Det styrer valget av motor og at fonter og
> fargeprofil bygges inn.

### Beslutninger

| Beslutning | Begrunnelse |
|---|---|
| Rendering i backend, via kodekall | Ingen ekstra app å drifte. Uten HTTP-API har ingen utenfor backend tilgang til genereringen, og ingen kan sende inn egne maler. |
| Handlebars (jknack) + commonmark + openhtmltopdf | Samme pipeline som dokgen, så malene kan flyttes uendret. Gir PDF/A uten ekstra verktøy. Ingen headless Chromium. |
| `io.github.openhtmltopdf` (fork) | `com.openhtmltopdf` er arkivert (siste release 1.0.10, 2021). Forken vedlikeholdes og bruker PDFBox 3. |
| Maler bare på classpath | Maler i payload er bevisst valgt bort av sikkerhetshensyn. |
| `DokgenClient` omdøpt til `DokumentService` | Navnet sier hva klassen gjør, ikke hvilken tjeneste den kalte før. |

## Arkitektur

```
no.nav.ekspertbistand.dokument.pdf    <-- GENERISK KJERNE (kopierbar mellom apper)
  ├─ PdfGenerator.kt                  interface: renderPdf(...) / renderHtml(...)
  ├─ PdfGeneratorImpl.kt              limet: mal → Handlebars → Markdown → innpakning → PDF/A
  ├─ TemplateRepository.kt            laster maler, formats og fonter fra classpath
  ├─ HandlebarsConfig.kt              Handlebars-instans, helpers og (ny) escaping-strategi
  ├─ MarkdownRenderer.kt              Markdown → HTML (commonmark, rå HTML bevares)
  ├─ PdfaRenderer.kt                  HTML → PDF/A-2b (openhtmltopdf-pdfbox + Batik for SVG)
  ├─ PdfGenerationException.kt
  └─ README.md                        hvordan kjernen kopieres til en annen app

no.nav.ekspertbistand.dokument        <-- DOMENEADAPTER
  └─ DokumentService.kt               mapper DTO.Soknad / TilsagnData → JsonObject og kaller PdfGenerator
```

Regler for kjernen:

- Ingen importer fra `no.nav.ekspertbistand.*` utenfor egen pakke. Ingen Ktor, Micrometer, Arena, Kafka eller Exposed.
- All konfigurasjon via konstruktør (`resourcePrefix`, PDF/A-nivå).

### Generisk API

```kotlin
interface PdfGenerator {
    fun renderPdf(templateName: String, data: JsonObject, variation: String? = null): ByteArray  // PDF/A-2b
    fun renderHtml(templateName: String, data: JsonObject, variation: String? = null): String    // samme HTML som går inn i PDF
}
```

Funksjonene er synkrone (CPU-bundet). `DokumentService` eksponerer `suspend`-metoder og kjører renderingen i `Dispatchers.IO`.

### DokumentService (offentlig API, uendret fra `DokgenClient`)

| Metode | Kalles fra |
|---|---|
| `genererSoknadPdf(soknad: DTO.Soknad): ByteArray` | `JournalfoerInnsendtSoknad` |
| `genererTilskuddsbrevPdf(tilsagnData: TilsagnData): ByteArray` | `JournalfoerTilskuddsbrev`, `JournalfoerTilskuddsbrevKildeAltinn`, `tilsagndata/Api.kt` |
| `genererTilskuddsbrevHtml(tilsagnData: TilsagnData): String` | `tilsagndata/Api.kt` (forhåndsvisning i frontend) |
| `genererArenaNotatPdf(saksnummer, tiltaksgjennomfoeringId): ByteArray` | `JournalfoerNotatArenaSakOpprettet` |

DI: `provide { DokumentService() }` i `Application.kt`. I tester: `DokumentService(StubPdfGenerator(...))`.

### Ressurser

```
backend/src/main/resources/dokumentmaler/
  fonts/SourceSansPro-*.ttf                 (12 varianter, embeddes i PDF/A)
  formats/pdf/{style.css,header.html,footer.html}   (header har NAV-logoen som inline SVG)
  formats/html/style.css
  templates/{soknad,tilskuddsbrev,arenaNotat}/template.hbs
  templates/*/schema.json                   (dokumentasjon, valideres ikke)
  templates/*/testdata/*.json
```

### Pipeline

1. **Handlebars** rendrer `template.hbs` med `data`. Verdier escapes med escaping-strategien i §3.1.
2. **Markdown → HTML** (commonmark) på resultatet. Rå HTML i malen passerer uendret.
3. **Innpakning** i et komplett XHTML-dokument med header, footer og CSS (`formats/pdf/*` for PDF, `formats/html/style.css` for HTML).
4. **HTML → PDF/A-2b** med innebygde fonter, innebygd sRGB ICC-profil, Batik for SVG og sperre for eksterne ressurser (§3.2).

## Status

Implementert:

- Kjerne og adapter som beskrevet over, med de fire metodene til `DokumentService`.
- Maler, fonter og formats i `dokumentmaler/`. `soknad` er synket med `main`, inkludert listefeltene
  `ekspert.godkjentUtdanningEllerAutorisasjon` og `ekspert.relevantKompetanse`.
- `diff -r dokgen/content backend/src/main/resources/dokumentmaler` er tom. Hold det slik så lenge `dokgen/` finnes.
- Tester: `PdfGeneratorImplTest`, `DokumentServiceTest`, `StubPdfGenerator`. `LocalApplication`, `TilskuddsbrevHtmlApiTest`
  og de fire `Journalfoer*Test`-ene bruker `StubPdfGenerator` i stedet for HTTP-mock av dokgen.
- Rebase mot `main`: commiten som slutter å spore `.idea` er droppet fra branchen (den ligger i `chore/untrack-idea-db-state`).

Mangler:

| # | Mangel | Seksjon |
|---|--------|---------|
| M0 | Ikke bygget eller testet etter rebase og pakkeflytting | §1 |
| M1 | `com.openhtmltopdf:*:1.0.10` er arkivert | §2 |
| M2 | Markdown-tegn og tomme linjer i verdier tolkes etter Handlebars-escaping og kan gi `<img>`, lenker og overskrifter fra brukerinput, eller bryte tabeller | §3.1 |
| M3 | openhtmltopdf kan hente eksterne ressurser over http, https og file | §3.2 |
| M4 | `TemplateRepository.templates` er en `HashMap` som skrives fra flere tråder | §4.1 |
| M5 | Ingen grense på samtidige renderinger og ingen metrikk | §4.2, §4.3 |
| M6 | Uverifisert at AWT, ICC og Batik virker i Chainguard-JRE-imaget | §4.4 |
| M7 | Ingen PDF/A-validering i test (bare `%PDF`-header) | §5.1 |
| M8 | Ingen erstatning for dokgen sitt preview-UI | §6 |
| M9 | dokgen-appen, nais-filer, workflows og access policies finnes fortsatt | §7 |

## Utenfor scope

- Generisk HTTP-API for PDF-generering, eller maler i payload.
- Bytte til flexmark eller skrive om malene. Malene skal være uendret med mindre en test krever noe annet.
- Schema-validering mot `schema.json`. Payload bygges fra typede DTO-er.
- iText. Lisensen er AGPL.

---

## Arbeid som gjenstår

### 1. Bygg og verifiser

- Kjør `mvn -f backend/pom.xml verify` og rett eventuelle kompileringsfeil etter rebasen og pakkeflyttingen.
- `git grep -n -E "no\.nav\.dokument\.pdf|DokgenClient|ekspertbistand\.dokgen" -- backend` skal gi null treff.

### 2. Bytt til openhtmltopdf-forken

I `backend/pom.xml` erstatter du `com.openhtmltopdf:openhtmltopdf-pdfbox` og `openhtmltopdf-svg-support` (1.0.10) med:

```xml
<properties>
    <openhtmltopdf.version>1.1.37</openhtmltopdf.version> <!-- nyeste stabile per september 2026 -->
</properties>

<dependency>
    <groupId>io.github.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>${openhtmltopdf.version}</version>
</dependency>
<dependency>
    <groupId>io.github.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-svg-support</artifactId>
    <version>${openhtmltopdf.version}</version>
</dependency>
```

- Java-pakkenavnene er etter det vi vet fortsatt `com.openhtmltopdf.*`, så importene skal i utgangspunktet ikke endres. Verifiser med kompilering.
- Kjør `mvn dependency:tree`. Det skal bare være én `org.apache.pdfbox:pdfbox` på classpath (PDFBox 3).
- Oppdater avhengighetslisten i `dokument/pdf/README.md`.

### 3. Sikkerhet

> 🔴 **Rød sone.** Dokumentene journalføres i Joark og inneholder fritekst fra arbeidsgiver
> (`behovForBistand.begrunnelse/behov/tilrettelegging`, `ekspert.*`, `kontaktperson.*`). Les gjennom
> escaping og ressurs-sperre grundig før merge.

#### 3.1 Escaping av verdier

**Problem:** Handlebars HTML-escaper `{{verdi}}`, men lar Markdown-tegn være. Etter Markdown-steget blir `![x](http://…)`
til `<img>` og `[tekst](…)` til `<a>`, og `# ` eller `* ` i starten av en linje endrer formateringen. En verdi med tom linje
(`\n\n`) kan avslutte en rå HTML-blokk (for eksempel `<table>` i `soknad`), slik at resten av malen tolkes som Markdown.

**Krav:** Lag en egen `EscapingStrategy` i kjernen og registrer den som standard i `HandlebarsConfig`:

1. HTML-escape som i dag (`&`, `<`, `>`, `"`, `'`, `` ` ``, `=`).
2. Erstatt Markdown-signifikante tegn med **numeriske tegnreferanser** (ikke backslash-escaping):
   `\ * _ [ ] ( ) # + - ! | ~` → `&#92; &#42; &#95; &#91; &#93; &#40; &#41; &#35; &#43; &#45; &#33; &#124; &#126;`.
   Backslash-escaping virker ikke inne i rå HTML-blokker i CommonMark, så backslashen ville blitt stående synlig i tabellceller.
   Tegnreferanser dekodes riktig både i Markdown og i rå HTML, og er gyldig XML.
3. Normaliser linjeskift: `\r\n` og `\r` → `\n`, og deretter `\n` → `<br/>`. Brukerens linjeskift bevares, og verdien kan aldri
   inneholde en tom linje.

I tillegg:

- Triple-stash `{{{…}}}` er forbudt i maler. Legg til en test som feiler hvis en `.hbs` under `dokumentmaler/templates/` inneholder `{{{`.
- Helpers som returnerer tekst fra data (`dateFormat`, `norwegian-date`) skal returnere vanlig `String`, ikke `Handlebars.SafeString`,
  slik at de escapes. Verifiser i test.

#### 3.2 Sperr eksterne ressurser

**Problem:** openhtmltopdf løser `<img src>`, `<link href>`, CSS `url(…)` og `@import`, og henter dem over http, https og file.

**Krav** i `PdfaRenderer`:

- Tillat bare `data:`-URI-er. Alle andre skjemaer (`http`, `https`, `file`, `jar`, `ftp` og relative stier) avvises uten nettverks- eller filtilgang.
- Foretrukket: `builder.useExternalResourceAccessControl({ uri, _ -> uri.startsWith("data:") }, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)`
  og tilsvarende for `RUN_AFTER_RESOLVING_URI`. Finnes ikke API-et i valgt versjon, bruk `useUriResolver` som returnerer `null` for alt
  som ikke er `data:`, **og** `useProtocolsStreamImplementation` med en factory som kaster for `http`, `https`, `file` og `jar`.
- `baseUri` skal være tom streng.
- Fonter leveres via `FSSupplier` og berøres ikke av sperren. Den inline SVG-logoen i `header.html` er ikke en ekstern ressurs.
- Logg avviste URI-er på `warn`, uten dokumentinnhold.

#### 3.3 HTML-forhåndsvisning

`genererTilskuddsbrevHtml` returnerer HTML som vises i frontend. Escapingen i §3.1 dekker den, og testene i §5.2 kjøres også mot `renderHtml`.

### 4. Robusthet og drift

#### 4.1 Trådsikkerhet

- `TemplateRepository.templates`: bytt `HashMap` til `ConcurrentHashMap`.
- `HandlebarsConfig.cache` er allerede `ConcurrentHashMap`. `getOrPut` er ikke atomisk, men det er ufarlig fordi kompilering er idempotent.
- `PdfaRenderer` lager ny `PdfRendererBuilder` og `BatikSVGDrawer` per kall. Behold det.

#### 4.2 Begrens parallellitet

- I `DokumentService`: legg en `kotlinx.coroutines.sync.Semaphore` (standard 2 permits, konstruktørparameter) rundt hvert kall til `PdfGenerator`.
  Podden har `limits.memory: 1024Mi`.

#### 4.3 Metrikk

- Micrometer-`Timer` `dokument_render` med taggene `template`, `format` (`pdf`/`html`) og `outcome` (`success`/`error`).
- Registreres i `DokumentService` etter mønsteret i `AppMetrics.kt`. Kjernen skal ikke ha Micrometer-avhengighet.

#### 4.4 Kjøretidsmiljø (Chainguard JRE `nav.no/jre:openjdk-21`)

- Legg en lisensfri sRGB ICC-profil som ressurs (`dokumentmaler/color/sRGB.icc`) og les den derfra i stedet for
  `ICC_Profile.getInstance(CS_sRGB).data`. Da slipper vi avhengigheten til JDK-internt ICC og native lcms.
- Sett `-Djava.awt.headless=true` i `JDK_JAVA_OPTIONS` i `backend/Dockerfile`, i tillegg til dagens `System.setProperty`.
- Verifiser i dev: én journalføring av søknad og én tilskuddsbrev-forhåndsvisning, uten `UnsatisfiedLinkError`, `HeadlessException` eller fontvarsler i loggene.

#### 4.5 XML-robusthet

- `withHtmlContent` krever velformet XHTML. Legg til en test med `&nbsp;` og `<br>` (ikke selvlukkende) i en test-mal.
- Feiler den: parse med jsoup (`org.jsoup:jsoup`) og bruk `W3CDom().fromJsoup(doc)` med `builder.withW3cDocument(doc, "")`. Går den grønt, trengs ingen endring.

### 5. Testing

#### 5.1 PDF/A-validering

- Legg til `org.verapdf:validation-model` (nyeste stabile, `test`-scope).
- For hver mal og hver `testdata/*.json`: render og valider mot `PDFAFlavour.PDFA_2_B`. Ved feil skal testen liste bruddene.

#### 5.2 Sikkerhetstester (`PdfGeneratorSikkerhetTest`)

Kjør mot både `renderPdf` (ekstraher tekst med PDFBox) og `renderHtml`:

| Input i et fritekstfelt | Forventet |
|---|---|
| `![x](http://127.0.0.1:<port>/x.png)` | Teksten vises bokstavelig. Ingen `<img>` i HTML. En lokal `ServerSocket` på porten mottar ingen tilkobling. |
| `[klikk](javascript:alert(1))` | Bokstavelig tekst, ingen `<a>` |
| `# Overskrift` / `* punkt` / `1. punkt` | Bokstavelig tekst, ingen `<h1>` eller `<li>` |
| `<script>alert(1)</script>` / `<img src=x onerror=…>` | Escapet, vises som tekst |
| `linje1\n\nlinje2` i en tabellcelle i `soknad` | Begge linjer i samme celle, adskilt av `<br/>`. Resten av tabellen er intakt. |
| `Navn & Sønn AS`, `Ærlig «sitat»`, `a_b*c` | Vises korrekt uten synlige escape-tegn |
| Test-mal i `src/test/resources` med `<img src="http://127.0.0.1:<port>/x">` | Ingen tilkobling. Renderingen fullføres. |

I tillegg: testen fra §3.1 om at ingen `.hbs` inneholder `{{{`.

#### 5.3 Innhold og samtidighet

- Utvid `soknad`-testen i `PdfGeneratorImplTest` til å verifisere at `godkjentUtdanningEllerAutorisasjon` og `relevantKompetanse` rendres kommaseparert.
- Bekreft at NAV-logoen (SVG i `header.html`) kommer med i PDF-en.
- Parallelltest: render alle tre malene 20 ganger parallelt (`coroutineScope { repeat(20) { async(Dispatchers.IO) { … } } }`). Alle skal gi gyldig PDF.
- `DokumentServiceTest`, `Journalfoer*Test` og `TilskuddsbrevHtmlApiTest` skal være grønne.

### 6. Forhåndsvisning av maler lokalt

- Lag en kjørbar `main` i `backend/src/test/kotlin/no/nav/ekspertbistand/executables/RenderDokumentmaler.kt`
  (samme mønster som `executables/EventFlowDiagram.kt`).
- Den rendrer alle `templates/*/testdata/*.json` til `backend/target/dokumentmaler-preview/{mal}-{testdata}.pdf` og `.html`, og skriver stiene til stdout.
- Dokumenter kjøringen i `backend/README.md`, og fjern dokgen-instruksjonene derfra.

### 7. Opprydding: fjern dokgen-appen (egen PR, etter verifisering i dev)

- Slett `dokgen/`.
- Slett `nais/dev-gcp-dokgen.yaml` og `nais/prod-gcp-dokgen.yaml`.
- Slett `.github/workflows/cicd-dokgen.yaml` og `.github/workflows/deploy-dev-manual-dokgen.yml`.
- Fjern `ekspertbistand-dokgen` fra `accessPolicy.outbound.rules` i `nais/dev-gcp-backend.yaml` og `nais/prod-gcp-backend.yaml`.
- Fjern `dokgen`-tjenesten fra `backend/docker-compose.yml`.
- `git grep -n -i dokgen` skal bare treffe historiske kommentarer og specs.
- Slett NAIS-appen i dev og prod (`kubectl delete app ekspertbistand-dokgen -n fager`). Det gjøres manuelt av teamet.

## Akseptansekriterier

- [ ] `mvn -f backend/pom.xml verify` er grønn.
- [ ] Kjernen `no.nav.ekspertbistand.dokument.pdf` importerer ingenting fra resten av ekspertbistand, Ktor eller Micrometer.
- [ ] `DokumentService` har de fire metodene uendret. Handlers krever ingen endringer ut over navnet.
- [ ] Bare `io.github.openhtmltopdf` og én PDFBox-versjon ligger på classpath.
- [ ] Alle sikkerhetstestene i §5.2 er grønne. Ingen utgående tilkobling skjer under rendering.
- [ ] Ingen `.hbs`-mal bruker `{{{`.
- [ ] Alle maler og testdata validerer som PDF/A-2b med veraPDF. NAV-logoen vises.
- [ ] Parallelltesten er grønn.
- [ ] `dokument_render`-timer finnes med taggene `template`, `format` og `outcome`.
- [ ] Verifisert i dev: søknad journalføres, og tilskuddsbrev-forhåndsvisning vises, uten feil i loggene.
- [ ] `RenderDokumentmaler` produserer PDF og HTML lokalt.
- [ ] Ingen HTTP-kall til dokgen igjen i backend.
- [ ] (PR 2) dokgen-appen, nais-filer, workflows og access policies er fjernet.

## For implementøren (GitHub Copilot)

- Rekkefølge: §1 → §2 → §4.1 → §3.1 → §3.2 → §5 → §4.2–4.5 → §6. §7 er egen PR.
- Ikke legg til HTTP-endepunkter for PDF-generering.
- Ikke endre malinnhold, CSS eller fonter, med mindre en test i denne spec-en krever det.
- Hold kjerne og adapter i hver sin pakke som beskrevet. Kjernen skal kunne kopieres til andre apper.
- Følg kodestilen i eksisterende klienter: konstruktørinjeksjon og `kotlinx.serialization`. Testnavn skrives på norsk med backticks.
