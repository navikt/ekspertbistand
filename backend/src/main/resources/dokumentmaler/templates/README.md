# Dokumentmaler

Malene her rendres med Handlebars til HTML. Gotenberg (Chromium) gjør HTML-en om til PDF/A, og PDF-en journalføres i Joark. Mange av verdiene kommer fra fritekst som arbeidsgivere skriver, så behandle dem som upålitelig input. Malene er ren HTML. Markdown støttes ikke.

## Gjør ✅

- Bruk alltid `{{verdi}}`. Handlebars HTML-escaper verdien, og linjeskift blir `<br/>`.
- Plasser verdier bare som tekstinnhold i elementer: `<td>{{navn}}</td>`, `<p>{{begrunnelse}}</p>`.
- Skriv lenker som faste `https://`-URL-er direkte i malen.
- Bruk fonten som finnes i containeren (DejaVu Sans) og CSS-en i `formats/pdf/style.css`. Legg justeringer i `formats/pdf/chromium.css`.
- Legg bilder og logoer inn som inline `<svg>` eller `data:`-URI.
- Legg til eller oppdater `testdata/*.json` når du endrer en mal, med både utfylte og tomme valgfrie felt. Kjør `DokumentmalTest`.
- Nye felt i malen må også finnes i payloaden fra `DokumentService`. Testen stopper deg ellers.
- Se over snapshot-diffen i PR-en.

## Ikke gjør ❌

- `{{{verdi}}}` (triple-stash). Det skrur av escaping, og brukertekst kan da bli HTML. Stoppes av test.
- Verdier i attributter: `href="{{…}}"`, `src="{{…}}"`, `style="…{{…}}…"`, `class="{{…}}"`, `on*=`. Escaping beskytter ikke mot `javascript:`-URL-er eller CSS-injeksjon.
- Verdier i `<style>`, `<script>`, HTML-kommentarer eller `<svg>`.
- Elementene `<script>`, `<iframe>`, `<object>`, `<embed>`, `<link rel="stylesheet">`, `<base>` og `<meta http-equiv>`. JavaScript er slått av i Gotenberg, og testen avviser elementene.
- Eksterne ressurser: `http(s)://`- eller `file:`-URL-er i `src` eller CSS `url(…)`, og `@import`. Gotenberg har ikke nettverkstilgang, så de feiler, og de er en SSRF-risiko.
- Markdown (`# overskrift`, `[tekst](url)`, `**fet**`). Det rendres ikke og stoppes av test.
- `Handlebars.SafeString` eller nye helpers som returnerer uescapet HTML fra data.
- Personopplysninger i `testdata` som ikke er syntetiske. Bruk fiktive navn og fødselsnumre.
- Å endre `dokgen/content/**`. Den mappa er frosset og slettes.

## Ny mal: sjekkliste

1. Lag `templates/{navn}/template.hbs` og `templates/{navn}/testdata/default.json` (pluss varianter).
2. Legg til metode og payload-mapping i `DokumentService`.
3. Kjør `DokumentmalTest` og snapshot-oppdatering, og se over diffen.
4. Render lokalt med `RenderDokumentmaler` og se på PDF-en.
5. Be om review fra noen i teamet på både malen og payloaden.

## Hvor reglene håndheves

| Regel | Håndheves av |
|---|---|
| `{{verdi}}` escapes, linjeskift blir `<br/>` | `EscapingStrategy` (`HtmlEscapingStrategy`), `DokumentRendererSikkerhetTest` |
| Ingen `{{{` eller Markdown i `.hbs` | `DokumentmalTest` |
| Velformet XHTML, ingen uløste referanser | `DokumentmalTest` |
| Ingen `<script>`, `<iframe>`, `<object>`, `<embed>`, `<link rel="stylesheet">` | `DokumentmalTest` |
| Bare `data:`-URI-er eller font-filnavn i `src` og CSS `url(…)`, ingen `@import` | `DokumentmalTest` |
| `<a href>` bare absolutt `https://` | `DokumentmalTest` |
| Alle malfelt finnes i payloaden fra `DokumentService` | `DokumentmalTest` (strict mode + ekte payload) |
| JavaScript av, bare filer fra `/tmp` | Gotenberg-variablene (§4.2) |
| Ingen nettverkstilgang for Chromium | Nettverkspolicyen i nais (§4.3) |
| Syntetiske testdata | Manuell review |

Merk: dette er ikke en mal. `TemplateRepository` og `DokumentmalTest` finner maler via `templates/*/template.hbs`, så README-en på rotnivå tolkes ikke som en mal.
