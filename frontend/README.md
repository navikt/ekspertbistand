# Frontend

Enkel oversikt over hvordan du kjører frontend lokalt.

## Forutsetninger

- Node 18+ og `pnpm` installert.

## Komme i gang

- Installer avhengigheter (fra rotmappen): `pnpm install`
- Start utviklingsmiljø (kjører klient og BFF samtidig): `pnpm dev`

## Porter

- Klient (Vite/React): `http://localhost:5173`
- BFF/Server (Express): `http://localhost:4000`
- Klienten proxyer kall til `/ekspertbistand-backend` og `/oauth2` videre til BFF på port 4000.

## Bygg og kjør

- Bygg alle pakkene: `pnpm build`
- For å forhåndsvise bygget klient: gå til `frontend/client` og kjør `pnpm preview` (starter på port 4173 som standard).

## Nyttige kommandoer

- Lint: `pnpm lint`
- Format: `pnpm format`
- Typecheck: `pnpm typecheck`

## Lokalt med Docker Compose

- Kopier miljøvariabler: `cp .env.example .env` (rediger ved behov)
- Bygg og start: `docker compose up --build`
- Kjør i bakgrunnen: `docker compose up -d --build`
- Stopp og rydd: `docker compose down`

### Miljøvariabler

- `PORT`: standard `4000` (eksponeres som `http://localhost:PORT`)
- `BASE_PATH`: standard `/` (bruk f.eks. `/ekspertbistand` for sub-path, uten trailing slash)
- `VITE_APP_BASE_PATH`: settes lik `BASE_PATH`, må starte med `/` og kan ikke ha trailing slash; brukes av `BrowserRouter` og API-paths.
- `VITE_BASE_PATH`: **kun** nødvendig når bygget lastes til CDN – sett til full URL (f.eks. `https://cdn.nav.no/fager/ekspertbistand/<sha>`).
- `NODE_AUTH_TOKEN`: GitHub Packages token for å hente `@navikt`-pakker

### Verifisering

- App: `http://localhost:4000/` (eller `http://localhost:4000/ekspertbistand/` hvis `BASE_PATH` settes)
- Logger: `docker compose logs -f`

### Kjøre frontend og backend

`pnpm dev` alene bruker MSW-mockene. Mot `LocalApplication`:

1. Start database: `docker-compose -f backend/docker-compose.yml up`
2. Start backend: Kjør LocalApplication.kt
3. Start frontend på en av to måter:
   - Bygget klient servert av BFF-en: `pnpm serve:e2e`, og åpne http://localhost:4000
   - Med hot reload: `EKSPERTBISTAND_API_BASEURL=http://localhost:8080 pnpm --filter server run dev` og
     `VITE_ENABLE_MOCKS=false pnpm --filter client dev`, og åpne http://localhost:5173

Testbrukeren har tilgang til alle virksomhetene i `LokaleSaker.kt`. `pnpm serve:all:e2e` starter både arbeidsgiverfrontenden (http://localhost:4000) og saksbehandling (http://localhost:4001) mot `LocalApplication`. Testflyt:

1. Send inn en søknad som arbeidsgiver.
2. Behandle saken i saksbehandling (http://localhost:4001): vilkårsvurdering, foreløpig vedtak og godkjenning.
3. Send refusjonskrav og sluttrapport som arbeidsgiver fra kvitteringssiden til søknaden.

Testdataene har allerede tre godkjente søknader (én uten kontonummer, én med tilskuddsbrev), én avslått og én avlyst. «Se som arbeidsgiver» i saksbehandling åpner søknaden i arbeidsgiverfrontenden.

### Mock-miljøet i dev-gcp

`deploy-dev-mock.yml` bygger og deployer tre mock-apper mot `ekspertbistand-backend-mock` (samme oppsett som `LocalApplication`). Alle deler de samme dataene, og de nullstilles hver gang backend-mocken starter.

- https://ekspertbistand-saksbehandling-mock.ansatt.dev.nav.no kjører saksbehandling uten innlogging. Velg testbruker i headeren. Imaget bygges med `VITE_LOKAL_BRUKERBYTTE=true` og uten `VITE_ENABLE_MOCKS`, så MSW starter ikke, og BFF-en kjører med `LOKAL_BRUKERBYTTE=true`.
- https://ekspertbistand-arbeidsgiver-e2e-mock.ansatt.dev.nav.no/ekspertbistand (`ekspertbistand-frontend-e2e-mock`) kjører arbeidsgiverfrontenden uten ID-porten, innlogget som testbrukeren. Imaget bygges med `VITE_LOKAL_INNLOGGING=true`, og BFF-en kjører med `LOKAL_INNLOGGING=true`. BFF-en avslutter ved oppstart hvis både `LOKAL_INNLOGGING` og `TOKEN_X_ISSUER` er satt.

https://arbeidsgiver-dev.ansatt.dev.nav.no/ekspertbistand (`ekspertbistand-frontend-mock`) kjører arbeidsgiverfrontenden med MSW og uten backend. NKS bruker den. Den deployes av `deploy-dev-manual-frontend-mock.yml`.

## Teknologier

- Bygg/verktøy: `pnpm` workspaces, Vite, TypeScript, `tsx`
- Klient: React (med `@vitejs/plugin-react-swc`)
- Server (BFF): Express (TypeScript)
- Testing: Vitest, Testing Library, Playwright (e2e), MSW
- Kodekvalitet: ESLint og Prettier (husky + lint-staged)

## Ressurser

- NAVs «Golden Path» for frontend: https://aksel.nav.no/god-praksis/artikler/golden-path-frontend
