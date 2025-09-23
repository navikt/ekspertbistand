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

## Teknologier

- Bygg/verktøy: `pnpm` workspaces, Vite, TypeScript, `tsx`
- Klient: React (med `@vitejs/plugin-react-swc`)
- Server (BFF): Express (TypeScript)
- Delte typer: `frontend/shared`-pakke
- Testing: Vitest, Testing Library, Playwright (e2e), MSW
- Kodekvalitet: ESLint og Prettier (husky + lint-staged)
