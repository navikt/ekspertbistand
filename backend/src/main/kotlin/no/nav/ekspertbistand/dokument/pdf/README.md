# `no.nav.ekspertbistand.dokument.pdf` — templating-kjerne for dokumenter

Domeneuavhengig kjerne som rendrer Handlebars-maler til komplett XHTML. Kjernen konverterer ikke selv til PDF: den lager HTML-en som [`GotenbergClient`](../gotenberg/GotenbergClient.kt) sender til Gotenberg for å få PDF/A-2b.

Kjernen kjenner ingenting til resten av ekspertbistand, Ktor, Micrometer, Arena eller Joark. Pakken importerer derfor ikke noe fra `no.nav.ekspertbistand.*` utenfor `no.nav.ekspertbistand.dokument.pdf`. Domeneadapteren er `no.nav.ekspertbistand.dokument.DokumentService`.

## Bruk

```kotlin
val renderer = DokumentRenderer(resourcePrefix = "dokumentmaler")

val pdfHtml: String  = renderer.renderHtml("soknad", data, Format.PDF)
val htmlOnly: String = renderer.renderHtml("tilskuddsbrev", data, Format.HTML)
```

`data` er en `kotlinx.serialization.json.JsonObject`. Adapteren serialiserer sin egen DTO til `JsonObject` før kall.

## Pipeline

1. `TemplateRepository.loadTemplate` leser `template.hbs` fra classpath.
2. `HandlebarsConfig.render` kompilerer og rendrer malen med data. `HtmlEscapingStrategy` HTML-escaper alle `{{verdi}}` og gjør linjeskift om til `<br/>`. Ingen Markdown.
3. Innpakning i et komplett XHTML-dokument.
   - `Format.PDF`: `formats/pdf/style.css`, `header.html` og `footer.html` i `<body>`. Malene bruker DejaVu Sans, som ligger i Gotenberg-containeren.
   - `Format.HTML`: `formats/html/style.css`, uten header og footer.
4. `GotenbergClient` sender HTML-en til Gotenberg, som gjør HTML → PDF/A-2b.

## Escaping og strict mode

`HtmlEscapingStrategy` er sikkerhetsgrensa mot fritekst fra arbeidsgivere. Alle malverdier escapes, og maler skal aldri bruke `{{{` eller verdier i attributter. Se reglene i [`dokumentmaler/templates/README.md`](../../../../../../resources/dokumentmaler/templates/README.md).

`HandlebarsConfig(strict = true)` kaster `PdfGenerationException` når en mal refererer et felt som mangler helt i dataene. `null`-verdier slipper gjennom. Produksjon kjører uten strict, så et uventet manglende felt gir tom tekst i stedet for en feilet journalføring. `DokumentmalTest` kjører strict og fanger feilene før de når produksjon.

## Fonter

Malene bruker DejaVu Sans via `* { font-family: "DejaVu Sans", … }` i `style.css`. Fonten ligger allerede i Gotenberg-containeren, så vi sender ingen font-filer. Gotenberg lager PDF/A gjennom LibreOffice, som legger om teksten og bytter til en systemfont hvis malfonten mangler. En slik substitusjon endrer tekstbredden og bryter høyremargen. DejaVu Sans finnes for både Chromium og LibreOffice, så begge motorene måler teksten likt.

## Krav

- Standard NAV-helpers (`dateFormat`, `norwegian-date`, `add`, `eq`) er registrert.
