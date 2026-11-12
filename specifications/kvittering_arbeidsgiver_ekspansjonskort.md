# Kvitteringen til arbeidsgiver i ekspansjonskort

Trello: https://trello.com/c/RZqSZ8p3/680-oppdatere-kvitteringssiden-for-arbeidsgiver
(kort 680, kort-ID `6ae45e35f30333e3308b51b8`)

Henger sammen med:

- Kort 682, [Legge ny søknad og funksjoner på egen rute bak feature toggle](https://trello.com/c/cEJ38zjf)
  (`6ae468163d6e9f6c3876ef9f`). Ligger i DONE.
- Kort 693 og spec-en [vis_vedtak_arbeidsgiver.md](vis_vedtak_arbeidsgiver.md).
  Vedtakssiden har allerede kvitteringen i et ekspansjonskort.

## Mål

Kvitteringssiden i v2 skal følge skissen og ha samme oppbygning som
vedtakssiden:

1. Piktogram og `h1` «Tilskudd til ekspertbistand».
2. «Tilbake til oversikt».
3. Statusboksen «Til behandling».
4. «Nav har mottatt søknaden» med teksten om saksbehandlingstid.
5. Nøkkeldata: startdato, deltaker, ekspert og arbeidsgiver.
6. Søknaden i et lukket ekspansjonskort «Søknad sendt inn».

Kvitteringen for innsendt søknad vises i dag som et åpent sammendrag
(`KvitteringSummary`). Arbeidsgiver skal etter hvert se flere elementer på
siden, og kortene holder siden oversiktlig. Innholdet i sammendraget er
uendret.

## Bakgrunn

- **Vedtakssiden** (`v2/pages/VedtakPage.tsx`) viser «Søknad sendt inn» i et
  `ExpansionCard` (small) med `FileTextIcon`, tittel som `h2`,
  innsendingsdatoen som beskrivelse og `KvitteringSummary` med
  `headingLevel="3"` i innholdet. Kortet er lukket når siden åpnes.
- **Kvitteringssiden** (`v2/pages/KvitteringPage.tsx`) vises før vedtak, og for
  Arena-saker. Den viser statusbanner, Arena-tilskuddsbrev i ekspansjonskort når
  statusen er `godkjent`, og deretter `KvitteringSummary` åpent med
  `headingLevel="2"`.
- **v1** (`pages/KvitteringPage.tsx`) brukes i prod og er utenfor dette kortet.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand), lest med Figma MCP
2026-11-11.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Siden åpnes, kortet er lukket | `1043:46991` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1043-46991> |
| Kortet er åpnet | `299:46960` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-46960> |

Skissen viser en innsendt søknad uten vedtak, i tekstbredde (576 px):

| Element i skissen | I koden |
|-------------------|---------|
| Piktogram og «Tilskudd til ekspertbistand» (Heading xlarge) | `ApplicationPictogram` og `h1`, som på vedtakssiden |
| Lenke «Tilbake til oversikt» | `BackLink` under `h1`. Ingen tilbakelenke nederst |
| Blå boks «Til behandling» (`#e3eff7`, padding 24) | `Box background="info-moderate" padding="space-24"` med `h2` (large) |
| «Nav har mottatt søknaden» (Heading small) og brødtekst | `h3` (small) og `BodyLong`, under boksen |
| Tiltaksperiode, deltaker, ekspert, arbeidsgiver | `Nokkeldata` med startdato, deltaker, ekspert og arbeidsgiver |
| ExpansionCard (small) «Søknad sendt inn» med `FileText` og dato | `SoknadSendtInnKort` |
| Åpnet kort: sammendraget av søknaden | `KvitteringSummary`, uendret |

Avstanden mellom elementene under tilbakelenken er 24 px (`space-24`).

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Innholdet i kvitteringen er likt som i dag, men flyttes inn i et ekspansjonskort. Kortet er lukket når siden åpnes, og arbeidsgiver åpner det ved å klikke. |
| Ken, kommentar 2026-10-30 | Kortet forutsetter kort 682 (ny rute bak feature toggle). Det er løst. |
| Ken, 2026-11-11 | Vi gikk først videre uten Figma, fordi MCP-serveren feilet. Samme kveld virket den, og siden er rettet etter skissen. |
| Ken, 2026-11-11 | På vedtakssiden ligger kvitteringen allerede i ekspansjonskort (kort 693). Det som gjenstår, er kvitteringssiden før vedtak. |
| Ken (frontend-ruter) | Ny arbeidsgiverfunksjonalitet lages i v2. Sider i v1 beholdes uendret. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Felles komponent i v2.** Kortet på vedtakssiden flyttes ut til
  `v2/components/SoknadSendtInnKort.tsx`, og begge sidene bruker den. Da ser
  kortet likt ut på begge sidene. «Rule of three» gjelder deling mellom appene
  (`client` og `saksbehandling`), ikke gjenbruk innenfor v2. Alternativet er en
  kopi på ca. 25 linjer i `KvitteringPage`.
- **Plassering.** Kortet erstatter sammendraget der det står i dag, under
  Arena-tilskuddsbrevene. Det gir nyeste først, som på vedtakssiden.
- **Overskriftsnivå.** Tittelen «Søknad sendt inn» er `h2`, som på vedtakssiden,
  og sammendraget i kortet får `headingLevel="3"`. Tilskuddsbrevkortene beholder
  `h3`.
- **Startdato i stedet for tiltaksperiode.** Skissen viser «Tiltaksperiode:
  15 jan. 2026 – 15 apr. 2026». Søknaden har bare startdato. Tiltaksperioden
  settes i vedtaket. Kvitteringen viser derfor «Startdato».
- **Felles `Nokkeldata`.** Nøkkeldata-listen på vedtakssiden flyttes ut til
  `v2/components/Nokkeldata.tsx` og tar inn radene. Rader uten verdi vises ikke.
- **Sideramme for alle statuser.** Piktogram, `h1`, tekstbredde og nøkkeldata
  gjelder også for godkjent, avslått og avlyst uten vedtak (Arena-saker).
  Skissen viser bare innsendt søknad, så statusboksene for de andre statusene
  beholder tekst og stil.

## Avgrensning

- Statusboksene for godkjent, avslått og avlyst, snarveiene og
  Arena-tilskuddsbrevene endres ikke.
- Titlene på vedtakskortet (kort 681) endres ikke. Koden har «Vedtak om godkjent
  søknad (med redusert beløp)». Kortet foreslår «Dere har fått innvilget
  søknaden». Vi endrer bare hvis fag vil ha kortets tekst.
- v1 (`pages/KvitteringPage.tsx`).

## Tilnærming

🔴 Rød sone: ingen. Ren endring i visningen, uten nye data eller endret tilgang.

### 1. `SoknadSendtInnKort`

Ny fil `frontend/client/src/v2/components/SoknadSendtInnKort.tsx`, flyttet fra
`VedtakPage`:

```tsx
type Props = {
  soknad: SoknadInputs;
  saksnummer?: string;
  innsendtTekst: string | null;
};
```

- `ExpansionCard` (small) med `aria-label="Søknad sendt inn"`, lukket som
  standard.
- Header: `FileTextIcon` (2rem), tittel «Søknad sendt inn» som `h2` (small) og
  `innsendtTekst` som beskrivelse når den finnes.
- Innhold: `KvitteringSummary` med `headingLevel="3"`.

`SoknadInputs` kommer fra `v2/features/soknad/schema`, som i `KvitteringSummary`.

### 2. `Nokkeldata`

Ny fil `frontend/client/src/v2/components/Nokkeldata.tsx`, flyttet fra
`VedtakPage`. Tar inn `rader: [label, verdi][]` og viser en `dl` i to
kolonner. Rader uten verdi vises ikke.

### 3. `VedtakPage`

Kortet erstattes med `<SoknadSendtInnKort ... />`, og nøkkeldata bygges med
`Nokkeldata`. Ingen endring i det arbeidsgiver ser.

### 4. `KvitteringPage`

- `DecoratedPage` får `blockProps={{ width: "text" }}`.
- Piktogram og `h1` «Tilskudd til ekspertbistand» øverst, som på vedtakssiden.
- `BackLink` (eller snarveiene ved godkjent) under `h1`. Tilbakelenken
  nederst fjernes.
- `Loader` og feilvarsel vises under tilbakelenken.
- Resten ligger i en `VStack` med `gap="space-24"`:
  - Innsendt: boksen «Til behandling» (`info-moderate`, `space-24`, `h2`
    large), deretter `h3` «Nav har mottatt søknaden» og teksten om
    saksbehandlingstid.
  - Godkjent, avslått og avlyst: statusboksene som før.
  - `Nokkeldata` med startdato, deltaker, ekspert og arbeidsgiver fra søknaden.
  - Arena-tilskuddsbrevene som før.
  - `<SoknadSendtInnKort soknad={formData} saksnummer={id} innsendtTekst={innsendtTekst} />`
    under samme vilkår som sammendraget før
    (`!isLoading && !errorMessage && formData`).

### 5. Tester

`v2/pages/KvitteringPage.test.tsx`, med MSW:

- Innsendt søknad: `h2` «Til behandling», `h3` «Nav har mottatt søknaden» og
  nøkkeldata vises. Kortet «Søknad sendt inn» vises lukket, og sammendraget
  (overskriften «Søknad») vises først når kortet åpnes.
- Godkjent søknad uten vedtak: `h1` «Tilskudd til ekspertbistand»,
  tilskuddsbrevkortet og kortet «Søknad sendt inn» vises.
- Godkjent søknad med vedtak: testen venter på vedtakssidens `h2` før den sjekker
  `h1`, fordi begge sidene nå har samme `h1`.

`VedtakPage.test.tsx` og `v2/isolation.test.ts` skal være grønne uten endring.
`e2e/v2.spec.ts` finner overskriften «Nav har mottatt søknaden» uten å sjekke
nivået, og skal være grønn uten endring.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `frontend/client/src/v2/components/SoknadSendtInnKort.tsx` | Ny, flyttet fra `VedtakPage` |
| `frontend/client/src/v2/components/Nokkeldata.tsx` | Ny, flyttet fra `VedtakPage` |
| `frontend/client/src/v2/pages/VedtakPage.tsx` | Bruker `SoknadSendtInnKort` og `Nokkeldata` |
| `frontend/client/src/v2/pages/KvitteringPage.tsx` | Bygd om etter skissen |
| `frontend/client/src/v2/pages/KvitteringPage.test.tsx` | Nye og endrede tester |

## Kanttilfeller og feilmodus

- **Mangler innsendingstidspunkt.** Kortet har ingen beskrivelse. Sammendraget
  viser «—» for innsendt, som i dag.
- **Mangler startdato, ekspert eller andre nøkkeldata.** Raden vises ikke.
- **Søknaden hentes fortsatt.** `Loader` vises, og ikke nøkkeldata eller kortet.
- **Feil ved henting av søknaden.** `Alert` vises, og ikke nøkkeldata eller
  kortet.
- **Godkjent Arena-sak.** Tilskuddsbrevkortene vises over kortet «Søknad sendt
  inn». Feiler tilskuddsbrevet, vises varselet, og kvitteringskortet virker.
- **Avlyst eller avslått uten vedtak.** Statusboksen vises over nøkkeldata og
  kortet.
- **Saken får vedtak i ny løsning.** Kvitteringen sender videre til
  vedtakssiden, som før.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Kvitteringssiden i v2 følger skissen `1043:46991` for innsendt søknad
- [ ] Søknaden ligger i et lukket ekspansjonskort «Søknad sendt inn» med innsendingsdato
- [ ] Innholdet i kortet er likt dagens sammendrag
- [ ] Vedtakssiden og kvitteringssiden bruker samme `SoknadSendtInnKort` og `Nokkeldata`, og vedtakssiden ser lik ut som før
- [ ] v1 er uendret
- [ ] `pnpm --filter client typecheck`, `lint` og `test:run` er grønne
