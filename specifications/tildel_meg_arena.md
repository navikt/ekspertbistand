# Ingen «Tildel meg» på Arena-saker

Trello: https://trello.com/c/s2agmYgC/638-tildel-meg-skal-ikke-v%C3%A6re-tilgjengelig-i-gui-for-arena-saker
Kort-ID: `6acf325b57a2e40aa99e4499`

## Mål

GUI-et tilbyr ikke tildeling av saker med kilde `ARENA`:

- Listen viser ikke «Tildel meg» på Arena-saker.
- Saksdetaljen viser verken «Tildel meg» eller «Overta som saksbehandler» på
  Arena-saker.

Arena-saker behandles ferdig i Arena. Å tildele dem her gir ingen handlinger å
gjøre, og saken havner i «Mine saker» uten grunn.

## Bakgrunn

- `tildelingHindring` i `SakApi.kt` gir `kanTildeleMeg` i listen og
  detaljoppslaget. Regelen ser på rolle, status og nåværende saksbehandler, men
  ikke på `kildeTilBehandling`. Arena-saker i `OPPRETTET` og `UNDER_BEHANDLING`
  får derfor `kanTildeleMeg = true`.
- `OversiktPage.tsx` viser «Tildel meg» i kolonnen «Saksbehandler» når
  `kanTildeleMeg` er `true`.
- `components/Tildeling.tsx` viser «Tildel meg» når saken er ledig, og
  «Overta som saksbehandler» i «Meny» når en kollega har saken. Begge krever
  `kanTildeleMeg`.
- `SakPage.tsx` skjuler allerede vilkår, refusjon og sluttrapport for
  Arena-saker med `sak.kildeTilBehandling === "ARENA"`.
- `saksbehandler_ident` på en Arena-sak kommer bare fra tildeling i denne
  løsningen. Arena-saker som noen har tildelt seg før endringen, beholder
  saksbehandleren.

## Skisser

Kortet har ingen Figma-lenke.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Arena-saker kan tildeles i dag, selv om man ikke kan gjøre noe på dem. «Tildel meg»-knappen fjernes i GUI. |
| Ken, 2026-10-14 | Bare GUI nå. API-et og `tildelingHindring` endres ikke. |
| Ken, 2026-10-14 | Arena-saker blir liggende i «Oppgaver som venter» inntil videre. `oppgavetype` og `harOppgaveForMeg` endres ikke. |
| Ken (`SakProjection.kt`) | Arena-saker vises bare i saksbehandling: ingen vilkår, refusjon, sluttrapport, tildeling eller vedtak. De behandles ferdig i Arena. |

Kortet har ingen kommentarer, sjekklister eller vedlegg.

Valg i spec-en:

- **Én hjelpefunksjon for regelen.** `useSaker.ts` får
  `kanTildeleMegSomSaksbehandler(sak)`, som `OversiktPage` og `Tildeling`
  bruker. Regelen står da ett sted i frontend. Alternativet var å legge
  `kildeTilBehandling !== "ARENA"` inn i hver komponent.
- **«Frigjør oppgave» står for Arena-saker innlogget allerede har.** Da kan den
  som tildelte seg en Arena-sak før endringen, rydde den ut av «Mine saker».
- **Beslutter berøres ikke.** Arena-saker når aldri `TIL_BESLUTNING`, så
  `kanTildeleMegSomBeslutter` er alltid `false` for dem.

## Avgrensning

- Backend: `tildelingHindring`, `POST …/tildeling` og `kanTildeleMeg` i
  API-et endres ikke. API-et godtar fortsatt tildeling av Arena-saker.
- `oppgavetype` og `harOppgaveForMeg` for Arena-saker endres ikke.
- Menyvalget «Tildel saksbehandler» i handlingsmenyen i listen er en plassholder
  uten handling for alle saker. Det endres ikke her.

## Tilnærming

### 1. Frontend: `kanTildeleMegSomSaksbehandler`

`useSaker.ts` får:

```ts
/** Arena-saker behandles ferdig i Arena. API-et godtar tildeling, men GUI-et tilbyr det ikke. */
export const kanTildeleMegSomSaksbehandler = (
  sak: Pick<SakInfo, "kildeTilBehandling" | "kanTildeleMeg">
) => sak.kildeTilBehandling !== "ARENA" && sak.kanTildeleMeg;
```

### 2. Frontend: `OversiktPage` og `Tildeling`

- `OversiktPage` bruker `kanTildeleMegSomSaksbehandler(sak)` i stedet for
  `sak.kanTildeleMeg` i kolonnen «Saksbehandler».
- `Tildeling` bruker `kanTildeleMegSomSaksbehandler(sak)` når rollen er
  saksbehandler. Beslutter bruker `sak.kanTildeleMegSomBeslutter` som før.

Resultat for Arena-saker:

| Tilstand | Listen | Saksdetaljen |
|----------|--------|--------------|
| Ikke tildelt | «–» | Ingenting foran «Logg» |
| Tildelt en kollega | Navnet | «Saksbehandler {navn}», ingen «Meny» |
| Tildelt innlogget | Navnet | «Saksbehandler {navn}» og «Meny» med «Frigjør oppgave» |

`EKSPERTBISTAND`-saker ser ut som før.

### 3. MSW-mock

`mocks/handlers.ts` speiler backend, og `kanTildeles` endres ikke. Mocken gir
derfor `kanTildeleMeg = true` for ledige Arena-saker, slik backend gjør, og
viser at GUI-et skjuler knappen likevel.

To nye Arena-saker i `UNDER_BEHANDLING`, så alle radene i *2* kan sjekkes:

| Tilstand | Mock-sak |
|----------|----------|
| Ikke tildelt | `…2011` (`OPPRETTET`) |
| Tildelt en kollega | ny `…2015`, tildelt `kollega` |
| Tildelt innlogget | ny `…2016`, tildelt mock-brukeren |

### 4. Tester

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW av radene i *3*. Backend
endres ikke.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `frontend/saksbehandling/src/hooks/useSaker.ts` | `kanTildeleMegSomSaksbehandler` |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | Bruker hjelpefunksjonen |
| `frontend/saksbehandling/src/components/Tildeling.tsx` | Bruker hjelpefunksjonen for saksbehandler |
| `frontend/saksbehandling/src/mocks/handlers.ts` | To nye Arena-saker |

## Kanttilfeller og feilmodus

- **Arena-sak tildelt før endringen.** Står i «Mine saker» med navnet.
  Innlogget kan frigjøre den. En kollega kan ikke overta den i GUI-et.
- **Direkte kall mot API-et.** `POST …/tildeling` godtar fortsatt Arena-saker.
  Det er bevisst (Ken, 2026-10-14).
- **Optimistisk oppdatering.** `useTildeling` setter `kanTildeleMeg = false`
  etter tildeling. Det påvirker ikke Arena-saker, siden knappen ikke vises.
- **«Oppgaver som venter».** Ledige Arena-saker i `OPPRETTET` og
  `UNDER_BEHANDLING` står i fanen uten «Tildel meg». Det er bevisst (Ken,
  2026-10-14).
- **Personopplysninger.** Ingenting nytt logges eller vises.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene om backend og «Oppgaver som venter»
2026-10-14. Svarene står under *Beslutninger*.

## Ferdig når

- [ ] Listen viser ikke «Tildel meg» på Arena-saker
- [ ] Saksdetaljen viser verken «Tildel meg» eller «Overta som saksbehandler» på Arena-saker
- [ ] «Frigjør oppgave» vises fortsatt på Arena-saker innlogget har
- [ ] `EKSPERTBISTAND`-saker viser tildeling som før
- [ ] MSW-mocken dekker radene i *3*
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
