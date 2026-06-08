# `no.nav.ekspertbistand.dokument.pdf` — gjenbrukbar PDF/A-generering

Domeneuavhengig kjerne for å rendre Handlebars-maler til **PDF/A-2b** (eller HTML).
Kjernen kjenner ingenting til resten av ekspertbistand, Ktor, Arena eller Joark og er ment å
kopieres tilnærmet uendret inn i andre apper som i dag bruker `dokgen`. Pakken skal derfor ikke
importere noe fra `no.nav.ekspertbistand.*` utenfor `no.nav.ekspertbistand.dokument.pdf`.

Domeneadapteren for ekspertbistand er `no.nav.ekspertbistand.dokument.DokumentService`.

## Bruk

```kotlin
val pdfGenerator: PdfGenerator = PdfGeneratorImpl(resourcePrefix = "dokumentmaler")

val pdf: ByteArray = pdfGenerator.renderPdf("soknad", dataAsJsonObject)
val html: String   = pdfGenerator.renderHtml("tilskuddsbrev", dataAsJsonObject)
```

`data` er en `kotlinx.serialization.json.JsonObject`. Adapteren i din app serialiserer
sin egen DTO til `JsonObject` før kall (ingen refleksjon, ingen app-kobling i kjernen).

## Pipeline (speiler dokgen)

1. **Handlebars** kompilerer og rendrer `template.hbs` med data.
2. **Markdown → HTML** (commonmark) på resultatet. Rå HTML i malen bevares.
3. **Innpakning** i komplett HTML-dokument med `formats/pdf/{header,footer}.html` + CSS.
4. **HTML → PDF/A** (openhtmltopdf-pdfbox) med embeddede fonter og SVG-støtte (Batik).

## Kopiere til en annen app

1. Kopier hele pakken `no.nav.ekspertbistand.dokument.pdf` og gi den et pakkenavn som passer i målappen.
2. Kopier ressursene til `src/main/resources/{resourcePrefix}/` med strukturen:
   ```
   {resourcePrefix}/
     fonts/{font}.ttf
     formats/pdf/{style.css,header.html,footer.html}
     formats/html/style.css            (valgfri)
     templates/{name}/template.hbs
   ```
3. Sett ønsket `resourcePrefix` i `PdfGeneratorImpl(...)`.
4. Legg til avhengighetene: `com.github.jknack:handlebars`,
   `org.commonmark:commonmark`, `com.openhtmltopdf:openhtmltopdf-pdfbox`,
   `com.openhtmltopdf:openhtmltopdf-svg-support`.

## Krav / merknader

- PDF/A-2b krever innebygde fonter og innebygd ICC-fargeprofil — begge håndteres av kjernen.
- `.ttf`-filer må kopieres som binærressurser (ikke filtreres av byggeverktøyet).
- Standard NAV-helpers (`dateFormat`, `norwegian-date`, `add`, `eq`) er registrert slik at
  maler fra andre apper også virker.

