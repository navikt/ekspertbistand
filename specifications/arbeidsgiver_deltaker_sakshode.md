# Arbeidsgiver og deltaker i sakshodet

Trello: https://trello.com/c/ulp1r0W4
(kort 653, kort-ID `6ad5cb5aac2acb3857decc5d`)

## Mål

Saksbehandler skal se hvilken arbeidsgiver og deltaker saken gjelder uten å
åpne søknaden. Linjen øverst på saksbildet skal vise arbeidsgiverens navn og
organisasjonsnummer, fulgt av deltakerens navn og fødselsnummer.

Begge navnene og numrene skal kunne kopieres hver for seg. Deltakeren merkes
med fortrolig eller strengt fortrolig adresse når saken har adressebeskyttelse.
Dagens merking av egne ansatte beholdes.

## Bakgrunn

- `SakPage.tsx` viser allerede deltakerens navn og fødselsnummer med
  kopierknapper, `AdressebeskyttelseTag` og `EgenAnsattTag`.
- `sak.soknad.virksomhet` inneholder `virksomhetsnavn` og `virksomhetsnummer`.
  Arbeidsgiver kan derfor legges til uten nye API-felt eller oppslag.
- Linjen ligger utenfor fanene og kvitteringene på `SakPage`. Endringen skal
  gjelde alle disse visningene, både for Arena-saker og nye saker.
- Kortet har ingen kommentarer eller sjekklister. Det har to bildevedlegg
  og en lenke til Figma-skissen nedenfor.

## Skisser

[Figma, node `981:40346`](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=981-40346).
Lest med Figma MCP 2026-11-16.

Skissen viser en lys grå linje med to grupper:

- Arbeidsgiver med bygningsikon, virksomhetsnavn, kopierknapp, skråstrek,
  organisasjonsnummer og kopierknapp.
- Deltaker med personikon, navn, kopierknapp, skråstrek, fødselsnummer og
  kopierknapp. En vertikal linje skiller arbeidsgiver fra deltaker.

Skissen har varianter uten merking, med «Strengt fortrolig adresse» og med
«Reservert i KRR». Adressebeskyttelsen vises som en liten tag med omriss.
Organisasjonsnummeret grupperes som tre grupper med tre sifre. Fødselsnummeret
grupperes som seks og fem sifre.

Avvik fra skissen:

- Alder i parentes utelates etter avklaring i samtalen 2026-11-16.
- KRR-varianten bygges ikke. Kortbeskrivelsen avgrenser KRR til en egen oppgave.
- Dagens merking av egne ansatte og den lokale lenken «Se som arbeidsgiver»
  beholdes, selv om de ikke er med i denne skissen.

Ingen personopplysninger fra skissen er kopiert hit.

## Beslutninger og innspill

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Vis arbeidsgiverens navn og organisasjonsnummer, deltakerens navn og fødselsnummer, og merking ved fortrolig eller strengt fortrolig adresse. |
| Kortbeskrivelsen | KRR-reservasjon og eventuell postsending løses i en egen oppgave. |
| Figma `981:40346` | Arbeidsgiver står før deltaker. Begge navnene og numrene har kopierknapp. Gruppene har hvert sitt ikon og en skillelinje mellom seg. |
| Avklart i samtalen, 2026-11-16 | Alder utelates. Den siste avklaringen «Dropp alder» erstatter det tidligere ønsket om alder som i Figma. |

## Avgrensning

- Ingen alder, fødselsdatoberegning eller nye PDL-oppslag.
- Ingen endringer i API-kontrakter, database eller eventer.
- Ingen endringer i tilgangskontrollen eller hvordan adressebeskyttelse og
  skjerming fastsettes.
- Ingen KRR-integrasjon, ny navigasjon eller endring av sakslisten.
- Ingen nye avhengigheter eller delte komponenter mellom frontend-appene.

## Tilnærming

### Data og plassering

Utvid den eksisterende linjen øverst i `SakPage.tsx`. Bruk data fra samme
`SakDetaljer` som resten av siden:

| Vises | Datakilde |
|-------|-----------|
| Virksomhetsnavn | `sak.soknad.virksomhet.virksomhetsnavn` |
| Organisasjonsnummer | `sak.soknad.virksomhet.virksomhetsnummer` |
| Deltakernavn | `sak.soknad.ansatt.navn` |
| Fødselsnummer | `sak.soknad.ansatt.fnr` |
| Adressebeskyttelse | `sak.adressebeskyttelse` |
| Egne ansatte | `sak.skjermet` |

Det trengs ikke en egen breadcrumb-komponent. Linjen viser sakens parter,
ikke en klikkbar navigasjonssti. Deltakerens navn beholdes som sidens
`h1`, mens arbeidsgiverens navn vises som uthevet tekst.

Arbeidsgiver og deltaker grupperes slik at kopierknappene står ved verdien de
tilhører. På smale skjermer kan gruppene og lange navn bryte over flere linjer.
Den vertikale skillelinjen skjules når gruppene stables. Ingen verdier eller
kopierknapper skal kuttes bort ved 320 pikslers skjermbredde.

### Aksel

Appen har `@navikt/ds-react` og `@navikt/aksel-icons` i Aksel 8.
Installert `ds-react` er 8.22.1. MCP-dokumentasjonen for `CopyButton` er
8.23.0, altså samme hovedversjon. Relevante props kontrolleres mot de
installerte typene under implementasjonen.

Komponenter, ikoner og tokens er slått opp i Aksel MCP:

| Del | Valg |
|-----|------|
| Linje og grupper | Eksisterende `Box` og `HStack`, med `align="center"` og linjebryting |
| Tekst | Eksisterende `Heading` og `BodyShort` |
| Kopiering | `CopyButton`, `size="xsmall"`, med `copyText`, beskrivende `title` og `activeText` |
| Arbeidsgiverikon | `Buildings2Icon` |
| Deltakerikon | `FigureOutwardIcon` |
| Adressebeskyttelse | Gjenbruk `AdressebeskyttelseTag`, med `Tag`, `size="small"` og `variant="outline"` i sakshodet |
| Gradering | Eksisterende `warning` for fortrolig og `danger` for strengt fortrolig |
| Bakgrunn, avstand og skillelinje | `bg-neutral-soft`, `space-8` og `border-neutral-subtle` |

`AdressebeskyttelseTag` får en valgfri variant-prop. Standardverdien forblir
`strong`, slik at sakslisten ikke endrer utseende. Sakshodet sender
`outline` eksplisitt. Tekstene og koblingen mellom gradering og farge endres
ikke. `EgenAnsattTag` endres ikke.

Ikonene og skilletegnene er dekorative og skjules for skjermlesere.
Kopierknappene får navnene «Kopier virksomhetsnavn»,
«Kopier organisasjonsnummer», «Kopier deltakernavn» og
«Kopier fødselsnummer». Knappenes navn og bekreftelser skal ikke inneholde
personopplysninger.

### Nummerformat og kopiering

Numrene behandles som strenger, slik at ledende nuller beholdes.
Vis organisasjonsnummer med mellomrom etter tredje og sjette siffer,
og fødselsnummer med mellomrom etter sjette siffer.

Kopier navnene uendret og numrene uten visningsmellomrom. Bruk Aksels
`CopyButton`, ikke egen kode for utklippstavlen.

### Kanttilfeller og feilmodus

- Lange navn og smal skjerm gir linjebryting, ikke avkortede verdier eller
  horisontal rulling fra sakshodet.
- Ingen adressebeskyttelse gir ingen graderingstag. Fortrolig og strengt
  fortrolig gir hver sin eksisterende tekst og farge.
- Skjermede deltakere beholder «Egen ansatt»-merkingen. Den skal kunne vises
  sammen med adressebeskyttelsen.
- Under lasting og ved avvist tilgang eller andre hentefeil vises fortsatt
  dagens laste- eller feilvisning, ikke sakshodet.
- Ingen av verdiene sendes til logger, analyseverktøy, sidetittel eller URL.
- Sakshodet blir stående ved fanebytte og på vedtakskvitteringene.

### Tester

Bruk eksisterende Playwright-oppsett i saksbehandlerappen:

- Kontroller at navnene og de formaterte numrene vises i riktig rekkefølge.
- Kontroller at alle fire kopierknappene kopierer riktig verdi. Numrene
  skal kopieres uten mellomrom, også når de har ledende null.
- Kontroller ingen adressebeskyttelse, fortrolig og strengt fortrolig,
  samt at egne ansatte fortsatt merkes.
- Kontroller at en Arena-sak og en ny sak får samme sakshode, og at
  fanebytte ikke fjerner opplysningene.
- Kontroller lange navn ved 320 pikslers bredde og ved vanlig desktopbredde.
  Alle verdier og kopierknapper skal være tilgjengelige, uten at sakshodet
  lager horisontal rulling.
- Behold ett `h1`, tydelige tilgjengelige navn på kopierknappene og
  tastaturbetjening. Kjør eksisterende axe-sjekk på saksbildet.
- Kontroller at feil ved henting av saken ikke viser sakens parter.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Arbeidsgivergruppe, ikoner, nummerformat og tilgjengelige kopierknapper i eksisterende sakshode |
| `frontend/saksbehandling/src/pages/SakPage.module.css` | Gruppering, skillelinje og tilpasning til smale skjermer |
| `frontend/saksbehandling/src/components/AdressebeskyttelseTag.tsx` | Valgfri variant, med uendret standard for eksisterende brukere |
| `frontend/saksbehandling/e2e/sakshode.spec.ts` | Tester av visning, kopiering, merking og smal skjerm |
| `frontend/saksbehandling/e2e/a11y.spec.ts` | Behold dekningen av saksbildet med det utvidede sakshodet |

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Arbeidsgiverens navn og organisasjonsnummer står før deltakerens navn og fødselsnummer
- [ ] Hvert navn og nummer kan kopieres separat, og numrene kopieres uten mellomrom
- [ ] Adressebeskyttelse vises med riktig tekst og farge, og egne ansatte merkes som før
- [ ] Alder og KRR-merking er ikke med
- [ ] Sakshodet fungerer på alle saksfaner, på kvitteringene og for Arena-saker
- [ ] Lange navn og 320 pikslers bredde gir ingen tap av innhold eller horisontal rulling fra sakshodet
- [ ] Tastaturbetjening, tilgjengelige knappnavn og sidens `h1` er ivaretatt
- [ ] Tilgangskontroll, API og resten av sakslisten er uendret
- [ ] Appens typesjekk, lint og relevante Playwright-tester er grønne
