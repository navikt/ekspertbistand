# Gjenbrukbar forhåndsvisning av PDF («pills»)

Trello: https://trello.com/c/a0TVG7Ww/629-pills-av-pdf
Kort-ID: `6acc8f3ecb88ab1f27a98b9b`

## Mål

Saksbehandler skal kunne se PDF-er direkte i saksbildet, uten å laste dem ned
først. Vi lager én komponent som viser et miniatyrbilde av første side. Klikker
man på miniatyren, åpnes hele PDF-en i en modal med mulighet for å laste ned.

Komponenten skal kunne brukes overalt der vi viser PDF-er i
saksbehandlingsløsningen: vedlegg til refusjonskrav, sluttrapport og senere
forhåndsvisning av vedtaksbrev og tilskuddsbrev.

## Bakgrunn

- Kortbeskrivelsen: «Vi kommer ha forhåndsvisning av PDF:en flere steder. Lag en
  komponent som vi kan bruke på flere steder.» Kortet har ingen kommentarer
  eller sjekklister.
- I `forelopig_vedtak.md` (Ken, 2026-10-09) er forhåndsvisning av brev holdt
  utenfor. Der står det at Hanna lager et forslag med forhåndsvisnings-«pills»
  i stedet for lenker. Dette kortet lager komponenten som de «pills»-ene skal
  bygge på.
- Aksel (v8, slått opp med Aksel MCP 2026-10-12) har ingen komponent for
  PDF-visning. Det nærmeste er `FileUpload`, som viser filer under opplasting.
  Vi bruker derfor `react-pdf` (pdf.js) til å gjengi PDF-en, og Aksel til alt
  rundt.
- `frontend/saksbehandling` har i dag verken vedlegg, et endepunkt for vedlegg
  eller en Refusjon-fane. Det eneste endepunktet for vedlegg er
  `GET /api/soknad/v1/{id}/refusjon/vedlegg/{vedleggId}`, bak TokenX og for
  `client`.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-12.

| Node | Lenke | Viser |
|------|-------|-------|
| `506:27626` | [refusjonskrav, send til utbetaling](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=506-27626) | Saksbildet med fanen «Refusjon». Midtkolonnen har beløp, tiltaksperiode, kontonummer, utgifter, beløp for refusjonskravet og «Sendt inn til Nav». Under står «Vedlegg» med to miniatyrbilder av første side (cirka 364×514 px), side om side, uten tekst. |
| `506:27687` | [vedlegg, lenkeliste](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=506-27687) | Overskriften «Vedlegg» og to kolonner med PDF-ikon, filnavn som lenke og filstørrelse under («256kb»). Laget ligger bak miniatyrbildene (`506:27757`, `506:27758`), så de dekker det i den samlede skissen. |

Skissen har realistiske testdata (navn, fødselsnummer). De er ikke kopiert hit.

Avvik mellom kort og skisse:

- **Miniatyr og lenkeliste overlapper.** Skissen har begge deler i samme
  område. Vi viser miniatyren med filnavn og størrelse rett under, slik at
  miniatyren og lenken blir ett element. Se *Åpne spørsmål*.
- **Skissen viser ingen modal.** Klikk åpner hele PDF-en i en Aksel `Modal`
  (beslutning under). Utformingen av modalen følger Aksel-standarden.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Anders, 2026-10-12 | Komponenten viser miniatyr av første side. Klikk åpner hele PDF-en i en Aksel `Modal`. |
| Anders, 2026-10-12 | PDF-en gjengis med `react-pdf` (pdf.js). Vi bruker ikke nettleserens innebygde visning (`<iframe>`/`<object>`), fordi miniatyren må se lik ut i alle nettlesere og ikke vise verktøylinjen. |
| Anders, 2026-10-12 | Komponenten ligger bare i `frontend/saksbehandling` (`src/components/pdf/`). Vi deler den ikke med `client` nå, og `frontend/shared` er ikke satt opp som pakke. |
| Anders, 2026-10-12 | Kortet dekker komponenten og en demo med MSW. Endepunkt for vedlegg til saksbehandler (Azure) og fanen «Refusjon» i `SakPage` kommer i et eget kort. |
| Aksel MCP, 2026-10-12 | Ingen PDF-komponent i Aksel. Vi bruker `Modal`, `Skeleton`, `Button`, `BodyShort`, `Link`, `VStack`/`HStack` og `FilePdfIcon` fra `@navikt/aksel-icons`. |

## Avgrensning

Ikke i dette kortet:

- Backend-endepunkt for vedlegg til saksbehandler, og tilgangskontroll på det.
- Fanen «Refusjon» i `SakPage` og resten av innholdet i skissen (kontroll av
  refusjonskrav, «Send til utbetaling»).
- Forhåndsvisning av vedtaksbrev og tilskuddsbrev. Komponenten skal kunne brukes
  til det, men koblingen gjøres i brevkortet.
- Bruk i `client` (`SokRefusjonPage`, `KvitteringPage`).
- Søk, zoom, rotasjon og utskrift i modalen. Nettleserens egen visning er
  tilgjengelig ved å laste ned.

## Tilnærming

### 1. Avhengighet

`pnpm --filter saksbehandling add react-pdf pdfjs-dist@<samme versjon som react-pdf>`.
`pdfjs-dist` legges til som direkte avhengighet, fordi pnpm ellers ikke lar Vite
løse opp `pdfjs-dist/build/pdf.worker.min.mjs` fra appen. Versjonen må holdes lik
den `react-pdf` bruker.

`src/components/pdf/pdfjs.ts` setter workeren slik at Vite bundler den fra samme
opphav, og eksporterer `Document` og `Page` videre. Alle komponentene importerer
fra denne modulen, slik at workeren alltid er satt:

```ts
import { pdfjs } from "react-pdf";

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  "pdfjs-dist/build/pdf.worker.min.mjs",
  import.meta.url,
).toString();
```

Firefox starter ikke en modul-worker direkte fra URL når MSW sin service worker
kontrollerer siden. pdf.js venter da på workeren for alltid, uten feilmelding, og
miniatyren blir blank. Når en service worker kontrollerer siden, starter
`pdfjs.ts` derfor workeren via en blob som importerer pdf.js-workeren, og gir den
til pdf.js som `GlobalWorkerOptions.workerPort`. I prod finnes ingen service
worker, så der brukes `workerSrc` som vanlig, og CSP-en trenger ikke `blob:`.

pdf.js er stort. Komponentene som bruker `react-pdf`, lastes med `React.lazy`,
slik at sider uten PDF ikke laster det.

Tekst- og annotasjonslag slås av (`renderTextLayer={false}`,
`renderAnnotationLayer={false}`) i miniatyren. I modalen slås tekstlaget på, slik
at tekst kan markeres og kopieres. CSS-en til lagene importeres fra `react-pdf`.

### 2. Komponenter

```ts
export type PdfFil = {
  url: string;
  filnavn: string;
  storrelse?: number; // bytes
};
```

| Komponent | Ansvar |
|-----------|--------|
| `PdfForhandsvisning` | Offentlig API for én fil. Miniatyr, filnavn og størrelse, og modalen. |
| `PdfForhandsvisningListe` | Offentlig API for flere filer. `HStack` med `wrap`, én `PdfForhandsvisning` per fil. Tom liste gir `BodyShort` «–», som i `client`. |
| `PdfMiniatyr` | `Document` + `Page pageNumber={1}` med fast bredde. Ligger i en `<button>`. |
| `PdfModal` | Aksel `Modal`. Laster `PdfSider` lazy når den åpnes. |
| `PdfSider` | `Document` med alle sider i målt bredde. |

`PdfMiniatyr`:

- `<button type="button" aria-label="Forhåndsvis {filnavn}">` rundt
  `Page`, med ramme og skygge fra Aksel-tokens og synlig fokusmarkering.
- Bredde 240 px som standard (prop `bredde`). Høyden følger siden.
- Under knappen: `FilePdfIcon` og filnavnet som Aksel `Link` med `download`, og
  størrelsen som `BodyShort size="small" textColor="subtle"`, som i
  `506:27687`.

`PdfModal`:

- `Modal` med `header={{ heading: filnavn, icon: <FilePdfIcon aria-hidden /> }}`,
  `width="medium"` eller fast bredde, og `closeOnBackdropClick`.
- `Modal.Body` viser alle sider etter hverandre (`numPages` fra
  `onLoadSuccess`) i modalens bredde.
- `Modal.Footer`: `Button` «Last ned» (som lenke med `download`) og `Button
  variant="secondary"` «Lukk».
- `Document` lastes først når modalen åpnes.

Tilstander, i begge:

- **Laster:** `Skeleton variant="rectangle"` i samme størrelse som siden.
- **Feil:** `FilePdfIcon` og filnavnet som nedlastingslenke, og `BodyShort
  textColor="subtle"` «Kunne ikke vise forhåndsvisning.» Klikk på miniatyren er
  da slått av.

Hjelpefunksjonen `formatBytes` legges i `src/utils/` med samme oppførsel som i
`client`.

### 3. MSW-mock og demo

- Én syntetisk eksempel-PDF på to sider, uten persondata, i
  `public/mock/eksempel.pdf`. Vite serverer den i dev og kopierer den til
  `dist`, så den virker likt i dev og i mock-bygget.
- Vi bruker ikke en MSW-handler for filen. MSW slipper gjennom
  navigasjonsforespørsler, så nedlasting med `<a download>` ville ikke truffet
  handleren.
- `SakPage` viser `PdfForhandsvisningListe` med dette ene vedlegget bak
  `isMockEnabled()`, under søknaden i midtkolonnen, slik at demoen også vises i
  mock-deployet i dev. Demoen fjernes når den ekte datakilden kobles på.

### 4. CSP

`saksbehandling-server` tillater workers fra samme opphav. Den tillater også
`blob:` i `worker-src`, fordi Firefox trenger blob-workeren når en service
worker kontrollerer siden. Tillatelsen gjelder bare workers, ikke andre skript.
pdf.js kan bruke WebAssembly for noen bildeformater (JPX, ICC). Dukker det opp
CSP-brudd for disse formatene, må vi vurdere en avgrenset utvidelse.

### 5. Tester

`saksbehandling` har ikke noe testoppsett, og vi legger ikke til et i dette kortet.
Vi verifiserer med `pnpm --filter saksbehandling build`, `typecheck` og `lint`,
og manuelt i dev med MSW:

- Miniatyren viser første side for begge demofilene.
- Feilfilen viser feiltilstanden med nedlastingslenke.
- Klikk eller Enter/mellomrom på miniatyren åpner modalen med alle sider.
- Esc og «Lukk» lukker modalen, og fokus går tilbake til miniatyren.
- «Last ned» laster ned filen med riktig filnavn.
- Ingen CSP-brudd i konsollen.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `frontend/saksbehandling/package.json`, `pnpm-lock.yaml` | `react-pdf`, `pdfjs-dist` |
| `frontend/saksbehandling/src/components/pdf/pdfjs.ts` | Ny: oppsett av worker |
| `frontend/saksbehandling/src/components/pdf/PdfMiniatyr.tsx` | Ny |
| `frontend/saksbehandling/src/components/pdf/PdfModal.tsx` | Ny |
| `frontend/saksbehandling/src/components/pdf/PdfSider.tsx` | Ny |
| `frontend/saksbehandling/src/components/pdf/types.ts` | Ny: `PdfFil`, A4-forhold |
| `frontend/saksbehandling/src/components/pdf/PdfForhandsvisning.tsx` | Ny |
| `frontend/saksbehandling/src/components/pdf/PdfForhandsvisningListe.tsx` | Ny |
| `frontend/saksbehandling/src/components/pdf/index.ts` | Ny: eksporter `PdfForhandsvisning`, `PdfForhandsvisningListe` og `PdfFil`, lazy |
| `frontend/saksbehandling/src/components/pdf/Pdf.module.css` | Ny: ramme, skygge og fokus med Aksel-tokens |
| `frontend/saksbehandling/src/utils/formatBytes.ts` | Ny |
| `frontend/saksbehandling/public/mock/eksempel.pdf` | Ny: syntetisk PDF for demoen |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Demo bak `isMockEnabled()` |
| `frontend/saksbehandling-server/src/index.ts` | Bare ved CSP-brudd |

## Kanttilfeller og feilmodus

- **Ikke en PDF, korrupt eller kryptert fil.** `Document` kaller `onLoadError`,
  og vi viser feiltilstanden med nedlastingslenke.
- **401/403/404 fra URL-en.** Samme feiltilstand. Vi skiller ikke på årsak i
  dette kortet.
- **Store filer.** Opplasting er begrenset til 10 MB. Miniatyren gjengir bare
  side 1. Modalen gjengir alle sider, og vi måler med en fil på 10 MB i dev.
  Blir det tregt, gjengir vi sidene etter hvert som de blir synlige.
- **Mange vedlegg.** Hver miniatyr laster hele filen. Komponenten laster først
  når den blir synlig (`IntersectionObserver`), slik at en lang liste ikke
  henter alt med én gang.
- **Lange filnavn.** Brytes over flere linjer under miniatyren, og hele navnet
  vises i modaloverskriften.
- **Auth.** Saksbehandling-frontenden kaller backenden med cookie via proxy.
  pdf.js henter med `fetch` fra samme opphav, så det skal virke. Vi bekrefter
  det når den ekte datakilden kobles på.
- **Personopplysninger.** Vedleggene kan inneholde helseopplysninger. Vi logger
  ikke URL-er, filnavn eller innhold i frontend, og feil til Faro inneholder
  ikke filnavn.

## Åpne spørsmål

1. **Lenkeliste eller bare miniatyr?** Figma har både miniatyrer og en lenkeliste
   med ikon, filnavn og størrelse i samme område. Spec-en viser filnavn og
   størrelse under hver miniatyr. Er det riktig, eller skal lenkelisten stå
   for seg selv i tillegg? Bør avklares med Hanna.
2. **Hvor demoen skal ligge.** Spec-en legger den i `SakPage` bak
   `isMockEnabled()`. Alternativet er en egen rute, for eksempel
   `/dev/pdf`, som ikke rører saksbildet.
3. **Deling med `client`.** Skal komponenten brukes i `client` senere? I så fall
   bør den flyttes til `frontend/shared`, og det bør bli et eget kort.

## Ferdig når

- [ ] `PdfForhandsvisning` og `PdfForhandsvisningListe` er eksportert fra `src/components/pdf`, og pdf.js lastes bare når komponenten brukes
- [ ] Miniatyren viser første side med filnavn og størrelse, og kan åpnes med mus og tastatur
- [ ] Modalen viser alle sider, har «Last ned» og «Lukk», og fokus går tilbake til miniatyren
- [ ] Laste- og feiltilstand bruker Aksel `Skeleton` og en nedlastingslenke
- [ ] Bare Aksel-komponenter, -tokens og -ikoner i UI-et rundt PDF-en
- [ ] Demoen med MSW viser to PDF-er og én feilfil i dev
- [ ] Ingen CSP-brudd, eller CSP-en er oppdatert med begrunnelse
- [ ] `pnpm --filter saksbehandling build`, `typecheck` og `lint` er grønne
