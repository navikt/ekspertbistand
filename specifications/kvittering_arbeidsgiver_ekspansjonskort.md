# Kvitteringen til arbeidsgiver i ekspansjonskort

Trello: https://trello.com/c/RZqSZ8p3/680-oppdatere-kvitteringssiden-for-arbeidsgiver
(kort 680, kort-ID `6ae45e35f30333e3308b51b8`)

Henger sammen med:

- Kort 682, [Legge ny søknad og funksjoner på egen rute bak feature toggle](https://trello.com/c/cEJ38zjf)
  (`6ae468163d6e9f6c3876ef9f`). Ligger i DONE.
- Kort 693 og spec-en [vis_vedtak_arbeidsgiver.md](vis_vedtak_arbeidsgiver.md).
  Vedtakssiden har allerede kvitteringen i et ekspansjonskort.

## Mål

Kvitteringen for innsendt søknad vises i dag som et åpent sammendrag
(`KvitteringSummary`). Den skal ligge i et lukket ekspansjonskort, som på
vedtakssiden. Arbeidsgiver skal etter hvert se flere elementer på siden, og
kortene holder siden oversiktlig.

Innholdet i kvitteringen er uendret. Bare innpakningen endres.

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

Figma-fil `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). **Ikke lest med Figma MCP.**
MCP-serveren virket ikke 2026-11-11, og Ken bestemte at vi går videre uten
skissene (se Beslutninger). Oppbygningen under følger kortbeskrivelsen og
vedtakssiden.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Siden åpnes, kortet er lukket | `1043:46991` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1043-46991> |
| Kortet er åpnet | `299:46960` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=299-46960> |

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Innholdet i kvitteringen er likt som i dag, men flyttes inn i et ekspansjonskort. Kortet er lukket når siden åpnes, og arbeidsgiver åpner det ved å klikke. |
| Ken, kommentar 2026-10-30 | Kortet forutsetter kort 682 (ny rute bak feature toggle). Det er løst. |
| Ken, 2026-11-11 | Vi går videre uten Figma, fordi MCP-serveren feiler. Vi vet nok fra kortet og vedtakssiden. |
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

## Avgrensning

- Statusbannere, snarveier, tilbakelenker og Arena-tilskuddsbrevene på
  kvitteringssiden endres ikke.
- Sidetittel og piktogram endres ikke. Kvitteringen har fortsatt «Søknad om
  tilskudd til ekspertbistand» som `h1`.
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

### 2. `VedtakPage`

Kortet erstattes med `<SoknadSendtInnKort ... />`. Ingen endring i det
arbeidsgiver ser.

### 3. `KvitteringPage`

`<KvitteringSummary data={formData} saksnummer={id} innsendtTekst={innsendtTekst} />`
erstattes med `<SoknadSendtInnKort soknad={formData} saksnummer={id} innsendtTekst={innsendtTekst} />`,
under samme vilkår som i dag (`!isLoading && !errorMessage && formData`).
Importen av `KvitteringSummary` fjernes.

### 4. Tester

`v2/pages/KvitteringPage.test.tsx`, med MSW:

- Innsendt søknad: kortet «Søknad sendt inn» vises lukket, og
  sammendraget (overskriften «Søknad») vises først når kortet åpnes.
- Godkjent søknad uten vedtak: både tilskuddsbrevkortet og kortet «Søknad
  sendt inn» vises.

`VedtakPage.test.tsx` og `v2/isolation.test.ts` skal være grønne uten endring.
Playwright-testene i `e2e/v2.spec.ts` sjekker ikke innholdet i sammendraget, og
skal være grønne uten endring.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `frontend/client/src/v2/components/SoknadSendtInnKort.tsx` | Ny, flyttet fra `VedtakPage` |
| `frontend/client/src/v2/pages/VedtakPage.tsx` | Bruker `SoknadSendtInnKort` |
| `frontend/client/src/v2/pages/KvitteringPage.tsx` | Bruker `SoknadSendtInnKort` i stedet for åpent sammendrag |
| `frontend/client/src/v2/pages/KvitteringPage.test.tsx` | Nye tester |

## Kanttilfeller og feilmodus

- **Mangler innsendingstidspunkt.** Kortet har ingen beskrivelse. Sammendraget
  viser «—» for innsendt, som i dag.
- **Søknaden hentes fortsatt.** `Loader` vises, og ikke kortet, som i dag.
- **Feil ved henting av søknaden.** `Alert` vises, og ikke kortet, som i dag.
- **Godkjent Arena-sak.** Tilskuddsbrevkortene vises over kortet «Søknad sendt
  inn». Feiler tilskuddsbrevet, vises varselet, og kvitteringskortet virker.
- **Avlyst eller avslått uten vedtak.** Statusbanneret vises over kortet, som i
  dag.
- **Saken får vedtak i ny løsning.** Kvitteringen sender videre til
  vedtakssiden, som før.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Kvitteringssiden i v2 viser søknaden i et lukket ekspansjonskort «Søknad sendt inn» med innsendingsdato
- [ ] Innholdet i kortet er likt dagens sammendrag
- [ ] Vedtakssiden og kvitteringssiden bruker samme `SoknadSendtInnKort`, og vedtakssiden ser lik ut som før
- [ ] v1 er uendret
- [ ] `pnpm --filter client typecheck`, `lint` og `test:run` er grønne
