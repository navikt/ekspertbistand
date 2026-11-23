# Kompakt oppsummering ved innsending av sluttrapport og refusjon

Trello: https://trello.com/c/W4cdyLLf/686-justeringer-p%C3%A5-innsending-av-sluttrapport
Kort-ID: `6ae47e62562cfcff2069b1f6`

## Mål

Arbeidsgiver skal se hvilken sak innsendingen gjelder uten å åpne et panel.
Både sluttrapportsiden og refusjonssiden i v2 får en kompakt oppsummering
under sidetittelen, med arbeidsgiver, ansatt, ekspert og tiltaksperiode.

Tiltaksperioden hentes fra vedtaket. Søknadens ønskede startdato skal ikke
brukes som erstatning for perioden Nav har innvilget.

## Bakgrunn

- Korttittelen og Figma gjelder sluttrapport, mens kortbeskrivelsen omtaler
  refusjon. Brukeren har avklart at endringen gjelder begge sidene.
- `v2/pages/SluttrapportPage.tsx` og `v2/pages/SokRefusjonPage.tsx` viser i dag
  et `FormSummary` med overskriften «Opplysninger om saken». Ingen av disse
  oppsummeringene er ekspanderbare i dagens kode.
- Begge viser arbeidsgiver, ansatt, ekspert og søknadens startdato.
- `v2/components/Nokkeldata.tsx` viser allerede nøkkeldata som en semantisk
  definisjonsliste på kvitterings- og vedtakssiden. Dagens variant har større
  tekst og uthever etikettene, mens skissen uthever verdiene.
- `v2/hooks/useVedtak.ts` henter vedtaket med SWR. Vedtakets `tiltaksperiode`
  inneholder `fra` og `til` som ferdig formaterte norske datotekster.
- V2 har egne ruter bak `EKSPERTBISTAND_V2`. Endringen skal ikke berøre
  v1-sidene.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI`, lest med Figma MCP 2026-11-20.

| Del | Ramme | Lenke |
|-----|-------|-------|
| Innsending av sluttrapport | `299:52832` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-52832> |
| Kompakt oppsummering | `299:52842` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-52842> |

Skissen viser tilbakelenke, sidetittel og fire rader før filopplastingen.
Etikettene står til venstre i vanlig skrift og verdiene til høyre i halvfet
skrift. Rekkefølgen er «Arbeidsgiver», «Ansatt», «Ekspert» og
«Tiltaksperiode». Oppsummeringen har ingen ramme, egen synlig overskrift,
åpneknapp eller skillelinjer.

Vi bruker dette oppsettet på begge sidene etter avklaringen med brukeren.
Filopplasting, sidetitler og innsendingsknapper endres ikke for å kopiere
andre detaljer i skissen. Ingen personopplysninger fra skissen er kopiert hit.

## Beslutninger

Kortet har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-20).

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Oppsummeringen øverst skal vises direkte, på samme måte som på søknadsoversikten. |
| Figma `299:52842` | Vis en kompakt liste med fire rader, vanlige etiketter og halvfete verdier. |
| Brukeren, avklaring 2026-11-20 | Endringen gjelder både sluttrapport og refusjon. |
| Brukeren, avklaring 2026-11-20 | Vis vedtakets tiltaksperiode, ikke dagens startdato fra søknaden. |

### Valg i spec-en

- Gjenbruk `Nokkeldata` med en valgfri kompakt variant. Dagens variant og
  eksisterende bruk på kvitterings- og vedtakssiden beholdes uendret.
- Bruk eksisterende `useVedtak` og SWR-nøkkel. Ingen ny API-rute eller
  endring av vedtaksdataene er nødvendig.
- Et oppslag som bare trengs for oppsummeringen skal ikke fjerne utfylte
  skjemafelt, valgte filer eller endre om arbeidsgiver kan sende inn.
  Lasting og feil vises ved oppsummeringen.

## Avgrensning

Ingen endringer i v1, backend, API-kontrakter eller tilgangskontroll.
Vi endrer ikke filgrenser, filtyper, beløpsvalidering, kontonummerkontroll
eller innsending og navigering etter innsending.

Kortene for allerede innsendt sluttrapport og refusjonskrav på vedtakssiden
beholdes som ekspansjonskort. Denne oppgaven gjelder bare oppsummeringen
før innsending.

## Tilnærming

### 1. Kompakt variant av nøkkeldata

Utvid eksisterende `Nokkeldata` med en kompakt variant for innsendingssidene.
Behold `dl`, `dt` og `dd`, slik at etiketter og verdier hører sammen også
for skjermlesere.

Varianten bruker Aksel `BodyShort` med `size="small"`, vanlige etiketter og
`weight="semibold"` på verdiene. Bruk eksisterende avstandstokens
`space-24` mellom kolonnene og `space-4` mellom radene. Ingen nye ikoner,
farger eller avhengigheter.

Appens versjonskrav er `@navikt/ds-react: ^8.16.1`.
Typografiprops og avstandstokens er slått opp i Aksel MCP. Dokumentasjonen
viser 8.23.0, altså samme hovedversjon. Props kontrolleres mot installerte
typer under implementeringen.

Verdikolonnen må kunne krympe og bryte lange navn uten avkorting eller
horisontal rulling ved 320 pikslers skjermbredde. Endringer som trengs
for dette avgrenses til den kompakte varianten.

### 2. Data og plassering på begge sider

Erstatt dagens `FormSummary` under sidetittelen med den kompakte varianten.
Ingen synlig «Opplysninger om saken»-overskrift skal stå igjen.

| Etikett | Datakilde |
|---------|-----------|
| Arbeidsgiver | `soknad.virksomhet.virksomhetsnavn` |
| Ansatt | `soknad.ansatt.navn` |
| Ekspert | `soknad.ekspert.navn` |
| Tiltaksperiode | `vedtak.tiltaksperiode.fra` og `vedtak.tiltaksperiode.til` |

De tre navnene kommer fra søknadsoppslaget sidene allerede bruker.
Perioden kommer fra `useVedtak(id)`. Gjenbruk cachen fra vedtakssiden og
hookens vanlige revalidering.

Datoene i vedtaksresponsen er allerede formatert av backend. Vis dem med
samme skilletegn som på vedtakssiden. Ikke send disse tekstene gjennom
`formatDate`, som forventer ISO-datoer. Vi beholder vedtakssidens datoformat
selv om skissen bruker kortere månedsnavn.

Tomme navn vises med dagens plassholder, ikke som bortfiltrerte rader.
Når vedtaket finnes, men perioden mangler, beholdes «Tiltaksperiode» med
plassholder. Ikke beregn en sluttdato eller bruk søknadens startdato.

### 3. Lasting og feil

Eksisterende laste- og feiltilstander for søknad, dokumentstatus,
maksbeløp og kontonummer beholdes.

Mens vedtaket lastes, vises en tilgjengelig lastetekst ved tiltaksperioden.
Når resten av skjemaet er klart, kan arbeidsgiver bruke det mens perioden
hentes. En revalidering skal ikke montere skjemaet på nytt.

Feil ved vedtaksoppslaget vises som en synlig feilmelding ved oppsummeringen,
med beskjed om å laste siden på nytt. En 404 gir `null` fra `useVedtak`;
vis da at vedtaket ikke ble funnet. Ingen av tilstandene skal fremstå
som en gyldig, tom periode eller erstattes med søknadens startdato.
Oppslagene endrer ikke backendens eksisterende regler for innsending.

### 4. Tester

Utvid de eksisterende sidetestene med vedtaksdata og kontroller begge sider.
Bruk en søknadsstart som er forskjellig fra vedtaksperioden, slik at testen
avslører bruk av feil datakilde.

Kontroller rekkefølge og sammenkobling av etiketter og verdier, at alle
opplysningene vises uten handling, og at «Startdato» og det gamle
oppsummeringspanelet er borte.

Dekk manglende navn, manglende periode, lasting, 404 og andre feil fra
vedtaksoppslaget. Kontroller at valgte filer og utfylte refusjonsfelt
beholdes når vedtaksoppslaget oppdateres.

Behold dekningen av filopplasting, innsendingsfeil og navigering etter
innsending. Kjør også eksisterende kvitterings- og vedtakstester for å
bekrefte at standardvarianten av `Nokkeldata` er uendret.

## Berørte filer

| Fil under `frontend/client/src/` | Endring |
|--------------------------------|---------|
| `v2/components/Nokkeldata.tsx` | Kompakt variant uten å endre standardvisningen. |
| `v2/components/Nokkeldata.module.css` | Variantavgrenset layout og linjebryting dersom CSS trengs. |
| `v2/pages/SluttrapportPage.tsx` | Kompakt oppsummering og periode fra eksisterende vedtakshook. |
| `v2/pages/SokRefusjonPage.tsx` | Samme oppsummering og datakilde som sluttrapportsiden. |
| `v2/pages/SluttrapportPage.test.tsx` | Oppsummering, datakilder og vedtaksoppslagets tilstander. |
| `v2/pages/SokRefusjonPage.test.tsx` | Tilsvarende dekning uten å endre refusjonsflyten. |

## Åpne spørsmål

Ingen uavklarte punkter fra kortet etter avklaringen med brukeren.
Den kompakte varianten og håndteringen av vedtaksoppslaget over inngår
i gjennomgangen før implementering.

## Ferdig når

- Begge v2-sidene viser en kompakt, alltid synlig oppsummering som i skissen.
- Oppsummeringen viser de fire avtalte radene i riktig rekkefølge.
- Tiltaksperioden kommer fra vedtaket, også når søknadens startdato avviker.
- Lange navn og datoer kan leses ved 320 pikslers skjermbredde uten
  horisontal rulling.
- Lasting, manglende vedtak og hentefeil vises uten å nullstille skjemaet.
- V1 og eksisterende nøkkeldata på kvitterings- og vedtakssiden er uendret.
- Filopplasting, validering og navigering etter innsending virker som før.
- Målrettede sidetester, typekontroll og v2-isolasjonstesten passerer.
- Visningen er kontrollert på smal skjerm og med skjermleserens leserekkefølge.
