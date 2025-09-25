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
- Klienten proxyer kall til `/api` videre til BFF på port 4000.

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
- `BASE_PATH`: standard `/` (bruk f.eks. `/ekspertbistand` for sub-path)
- `NODE_AUTH_TOKEN`: GitHub Packages token for å hente `@navikt`-pakker under build (kun nødvendig ved første install)

### Verifisering

- App: `http://localhost:4000/` (eller `http://localhost:4000/ekspertbistand/` hvis `BASE_PATH` settes)
- Health: `curl -s http://localhost:4000/api/health` (eller `/ekspertbistand/api/health`)
- Logger: `docker compose logs -f`

## Teknologier

- Bygg/verktøy: `pnpm` workspaces, Vite, TypeScript, `tsx`
- Klient: React (med `@vitejs/plugin-react-swc`)
- Server (BFF): Express (TypeScript)
- Delte typer: `frontend/shared`-pakke
- Testing: Vitest, Testing Library, Playwright (e2e), MSW
- Kodekvalitet: ESLint og Prettier (husky + lint-staged)

## Ressurser

- NAVs «Golden Path» for frontend: https://aksel.nav.no/god-praksis/artikler/golden-path-frontend
