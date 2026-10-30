# Saksbehandling frontend

Separat frontend-app for saksbehandling.

## Komme i gang

- Kjør `pnpm install` fra rotmappen
- Start appen med `pnpm dev:saksbehandling`

## UU-tester

Kjør `pnpm --filter ./frontend/saksbehandling e2e` fra rotmappen. Testene starter
Vite med mockdata på port 5174 og sjekker oversikt og saksvisning med axe.
Playwright-jobben i GitHub Actions kjører dem på push og pull request.
Hvis Chromium mangler lokalt, kjør
`pnpm --filter ./frontend/saksbehandling exec playwright install chromium` først.

## Kjøre mot LocalApplication med flere brukere

`LocalApplication.kt` i backend har fire saksbehandlere med tilgang til samme
enheter:

| Ident     | Navn                  | Roller                     |
| --------- | --------------------- | -------------------------- |
| `A123456` | Ola Beggedeler        | saksbehandler og beslutter |
| `B654321` | Kari Beggedeler       | saksbehandler og beslutter |
| `P123456` | Per Baresaksbehandler | saksbehandler              |
| `L654321` | Lisa Barebeslutter    | beslutter                  |

1. Start `LocalApplication.kt`.
2. Kjør `pnpm serve:saksbehandling:e2e` fra rotmappen og
   åpne http://localhost:4001.
3. Bytt bruker under «Bytt bruker (lokalt)» i brukermenyen øverst til høyre.

Valget lagres i cookien `lokal-bruker`. BFF-en sender da `faketoken-<ident>`
til backend i stedet for `faketoken`. Uten cookie er du Ola. Velgeren og
cookien virker bare når BFF-en kjører uten Azure og med `NODE_ENV` ulik
`production`. Nye brukere legges inn både i `lokaleAnsatte` i
`LocalApplication.kt` og i `src/lokal-bruker.ts` i saksbehandling-server.

Slik tester du topartskontrollen: Ola tildeler seg saken og sender foreløpig
vedtak. Bytt til Kari, som tildeler seg saken som beslutter. Ola får ikke
«Tildel meg» som beslutter på saken.

## Videre arbeid

- Legg inn routes i `src/App.tsx`
- Opprett domeneområder under `src/features`
- Koble mot egen BFF eller backend når API-kontraktene er klare
