# Vis innsendt sluttrapport til arbeidsgiver

Trello: https://trello.com/c/omYxyPPP/683-oppdatere-sluttrapport-visning-for-arbeidsgiver
Kort-ID: `6ae46cb01ce89316eb7fdd6e`

Bygger videre på kort 680, «Oppdatere kvitteringssiden for arbeidsgiver», og
[vis_vedtak_arbeidsgiver.md](vis_vedtak_arbeidsgiver.md).

## Mål

Når arbeidsgiver har sendt inn sluttrapport, viser vi «Sluttrapport sendt inn»
i et lukket ekspansjonskort over vedtaket og søknaden. Når arbeidsgiver åpner
kortet, ser hen filnavnet og datoen rapporten ble sendt inn til Nav.

Filnavnet er ren tekst. Arbeidsgiver skal verken kunne åpne, forhåndsvise
eller laste ned sluttrapporten. Rapporten kan inneholde sensitive
personopplysninger.

Knappen «Send inn sluttrapport fra eksperten» vises ikke når sluttrapporten
er mottatt. «Søk refusjon» og refusjonsfristen står som før.

## Bakgrunn

- `v2/pages/VedtakPage.tsx` viser vedtaket, vilkårene, knappene og kortene
  for vedtak og søknad. Den henter ikke sluttrapportstatus og viser alltid
  innsendingsknappen ved innvilgelse.
- `v2/pages/KvitteringPage.tsx` sender arbeidsgiver til vedtakssiden når
  vedtaket finnes. Kortet hører derfor hjemme på `VedtakPage`, selv om
  oppgaven omtaler kvitteringssiden.
- `v2/pages/SluttrapportPage.tsx` henter allerede metadata, viser filnavn
  uten lenke etter innsending og sender arbeidsgiver tilbake til
  kvitteringen når innsendingen lykkes.
- `v2/hooks/useSluttrapportStatus.ts` returnerer `sluttrapport`, `isLoading`,
  `error` og `mutate`. Metadataene er `filnavn`, `opprettet` og `storrelse`.
- `GET /api/soknad/v1/{id}/sluttrapport` returnerer metadata ved treff og
  204 når rapporten ikke finnes. Feil er ikke det samme som manglende rapport.
  Backend har bevisst ingen nedlastingsrute for arbeidsgiver
  (`vedlegg/VedleggApi.kt`).
- `v2/components/SoknadSendtInnKort.tsx` er mønsteret for et lite, lukket
  kort med dokumentikon, tittel og dato.
- `v2/isolation.test.ts` tillater bare `utils/api`, `utils/http` og
  `utils/env` som delte moduler utenfor v2. Nye komponenter legges i v2.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI`, lest med Figma MCP 2026-11-17.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Lukket sluttrapportkort | `299:44721` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-44721> |
| Åpent sluttrapportkort | `299:45040` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-45040> |

Begge viser en godkjent søknad med sluttrapportkortet over vedtakskortet
og søknadskortet. Knappen for innsending av sluttrapport er borte.

Kortets hode har dokumentikon, «Sluttrapport sendt inn» og dato.
Åpent kort viser «Vedlegg» med filnavnet som tekst, deretter «Sendt inn til Nav»
med dato. Skissen viser skillelinjer mellom hodet og innholdet og mellom
opplysningene.

Eksempeldatoene er forskjellige i de to rammene. Vi bruker samme
`opprettet` fra API-et i både hodet og innholdet.

## Beslutninger

Kort 683 har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-17).

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Innsendt sluttrapport vises i et ekspansjonskort. Bare filnavnet vises, ikke selve dokumentet. |
| Figma `299:44721` og `299:45040` | Kortet starter lukket, står over vedtaket og erstatter innsendingsknappen. Dato vises i hodet og under «Sendt inn til Nav». |
| Ken, kommentar på kort 680, 2026-10-30 | Kvitteringsendringene forutsetter kort 682: ny løsning på egen rute bak feature toggle. |

### Valg i spec-en

- Endringen gjelder v2 og både ordinær innvilgelse og innvilgelse med
  redusert beløp. Vi endrer ikke v1-visningen for Arena-saker.
- Et eget `SluttrapportSendtInnKort` følger mønsteret fra
  `SoknadSendtInnKort`. Metadata hentes på vedtakssiden med den eksisterende
  hooken, ikke ved å hente dokumentet.
- Dato formateres med `formatSubmittedDate`. Samme verdi brukes begge steder.
  Manglende eller ugyldig dato vises som eksisterende plassholder.
- Vi avklarer status før innsendingsknappen vises. Ved feil viser vi en
  feilmelding i stedet for å anta at rapporten mangler.

## Avgrensning

Ingen endring i filopplasting, filgrenser, innsending, API-kontrakt eller
tilgangskontroll. Ingen ny dokumentrute eller PDF-komponent.
Kort 686 om oppsummeringen på innsendingssiden inngår ikke.
Visningen av refusjonskrav endres heller ikke.

## Tilnærming

### 1. Sluttrapportkort

Lag `v2/components/SluttrapportSendtInnKort.tsx` med metadata som props.
Bruk Aksel `ExpansionCard` med `size="small"` og lukket starttilstand.
`ExpansionCard.Title` rendres som `h2`, og kortet får navnet
«Sluttrapport sendt inn» for hjelpemidler.

Bruk `FileTextIcon` med `aria-hidden` og samme hodeoppsett som
`SoknadSendtInnKort`. Innholdet bruker en semantisk definisjonsliste med
«Vedlegg» og «Sendt inn til Nav». La lange filnavn bryte uten å skape
horisontal rulling. Ingen lenke, knapp, blob-URL eller PDF-forhåndsvisning
knyttes til filnavnet.

Aksel-avhengigheten er `@navikt/ds-react` med versjonskrav `^8.16.1`.
`ExpansionCard` med underkomponenter og lukket standardtilstand er slått
opp via Aksel MCP, sammen med `FileTextIcon` og avstandstokenet `space-16`.
Vi følger eksisterende layout i søknadskortet og trenger ingen nye ikoner
eller egne fargeverdier.

### 2. Vedtakssiden

Hent status med `useSluttrapportStatus` når søknaden har et innvilget vedtak,
også ved redusert beløp. Hooken får `undefined` før dette er avklart og
ved avslag, slik at den ikke starter unødige kall.

| Status | Visning |
|--------|---------|
| Laster | Ventemarkering med tilgjengelig navn. Ingen innsendingsknapp ennå. |
| 200 med metadata | Sluttrapportkort øverst i kortlisten. Ingen innsendingsknapp. |
| 204 | Ingen sluttrapportkort. Vis eksisterende innsendingsknapp. |
| Feil | Synlig feilmelding med beskjed om å laste siden på nytt. Ingen innsendingsknapp mens status er ukjent. Resten av vedtaket er tilgjengelig. |

Bruk eksisterende SWR-nøkkel. Etter innsending oppdaterer
`SluttrapportPage` allerede status med `mutateStatus` før navigering.
Når kvitteringen sender videre til vedtakssiden, bruker siden samme cache.
Vi beholder SWRs revalidering ved senere besøk.

### 3. Tester

Utvid `VedtakPage.test.tsx` med MSW-responser for metadata, 204 og feil.
Kontroller lukket starttilstand, åpning med tastatur og plassering før
vedtakskortet. Test både ordinær innvilgelse og redusert beløp.

Kontroller at filnavnet er tekst uten dokumentlenke eller forhåndsvisning,
og at åpning av kortet ikke utløser noe dokumentkall.
Test at innsending av sluttrapport fortsatt fører tilbake til oversikten
over vedtaket, nå med sluttrapportkort og uten innsendingsknapp.
Ingen ny testinfrastruktur.

## Berørte filer

| Fil under `frontend/client/src/` | Endring |
|--------------------------------|---------|
| `v2/components/SluttrapportSendtInnKort.tsx` | Ny presentasjonskomponent for metadata. |
| `v2/pages/VedtakPage.tsx` | Hent status, vis kortet og styr innsendingsknappen. |
| `v2/pages/VedtakPage.test.tsx` | Dekk rapportstatus, kortinnhold og uendret vedtaksvisning. |
| Eksisterende testoppsett for innsending i v2 | Dekk overgangen fra vellykket innsending til sluttrapportkortet. |

## Kanttilfeller og feilmodus

- 403, 404 og serverfeil er feiltilstander, ikke en tom rapportstatus.
- Avslag viser fortsatt ingen innsendingsknapper eller sluttrapportkort.
- Statusfeil skal ikke skjule vedtaksbrevet, refusjonsknappen eller søknaden.
- Langt filnavn må fungere på smal skjerm. Filnavnet rendres som tekst,
  ikke HTML.
- Datoen i hodet og innholdet skal alltid stemme overens.
- Eksisterende visning av vedtaksbrev beholder sin PDF-forhåndsvisning.
  Forbudet mot forhåndsvisning gjelder sluttrapporten.

## Åpne spørsmål

Ingen som blokkerer utkastet. Avgrensningen til v2 og feiltilstandene over
inngår i gjennomgangen før implementering.

## Ferdig når

- Innsendt sluttrapport vises i et lukket kort før vedtaket og søknaden.
- Åpnet kort viser filnavn og innsendingsdato som i skissen.
- Sluttrapporten kan ikke åpnes, forhåndsvises eller lastes ned.
- Innsendingsknappen vises bare når status er avklart og rapporten mangler.
- Laste- og feiltilstander vises uten å fremstille dem som manglende rapport.
- Både innvilget og redusert beløp virker. Avslag og v1 er uendret.
- Målrettede tester, typekontroll og v2-isolasjonstesten passerer.
- Visningen er kontrollert på smal skjerm og med tastatur.
