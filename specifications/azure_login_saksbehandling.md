# Azure AD (Entra ID) pålogging for saksbehandling-frontend

## Mål

Aktivere Azure AD-innlogging via Wonderwall sidecar for `ekspertbistand-saksbehandling` i dev-gcp, slik at saksbehandlere må autentisere seg med Nav-kontoen sin for å bruke appen.

## Bakgrunn

Frontenden (`frontend/saksbehandling`) og BFF-serveren (`frontend/saksbehandling-server`) er allerede forberedt for Wonderwall:
- Frontenden bruker `/oauth2/session` og `/oauth2/login` (via `useSession` hook og `LOGIN_URL` konstant)
- BFF-serveren bruker Azure AD OBO-middleware for å veksle brukertoken mot backend
- Nais-konfigurasjon er på plass med Azure AD sidecar

## Dokumentasjon

- https://docs.nais.io/auth/entra-id/
- https://docs.nais.io/auth/entra-id/how-to/login/

## Implementasjonsplan

### 1. Oppdater `nais/dev-gcp-saksbehandling.yaml`

Legg til Azure AD med Wonderwall sidecar:

```yaml
azure:
  application:
    enabled: true
    allowAllUsers: true
  sidecar:
    enabled: true
    autoLogin: false
```

Legg til env-variabel for Azure OBO audience:

```yaml
env:
  - name: EKSPERTBISTAND_API_AUDIENCE
    value: "api://dev-gcp.fager.ekspertbistand-backend/.default"
```

Legg til accessPolicy for utgående trafikk til backend:

```yaml
accessPolicy:
  outbound:
    rules:
      - application: ekspertbistand-backend
```

Fjern `idporten`-blokken og `tokenx`-blokken (irrelevant for saksbehandler-app).

### 2. Verifiser BFF-server

Serveren (`frontend/saksbehandling-server/src/index.ts`) håndterer allerede:
- Wonderwall setter `Authorization: Bearer <token>` på innkommende requests
- `azureOboMiddleware` bruker `getToken(req)` fra `@navikt/oasis` for å hente token
- OBO-token veksles via `requestAzureOboToken` mot `EKSPERTBISTAND_API_AUDIENCE`
- Lokal mock av `/oauth2/session` kjører kun når `NODE_ENV !== "production"`
- Proxy-mapping: `/api/ansatte/*` → `/api/saksbehandling/v1/*` og `/api/saksbehandling/oversikt` → `/api/saksbehandling/v1/oversikt`

Ingen kodeendringer nødvendig i BFF.

### 3. Verifiser frontend

Frontenden har allerede:
- `useSession()` hook som kaller `/oauth2/session`
- `LOGIN_URL = /oauth2/login?redirect=/oversikt`
- `RequireAuth`-komponent som sjekker innloggingsstatus

Ingen kodeendringer nødvendig i frontend.

## Beslutninger

| Beslutning | Valg | Begrunnelse |
|-----------|------|-------------|
| Auth-mekanisme | Azure AD + Wonderwall | Saksbehandler-app → Entra ID er riktig |
| allowAllUsers | `true` | Alle Nav-ansatte kan logge inn i dev |
| autoLogin | `false` | Frontenden styrer login-flyten selv |
| OBO-mekanisme | Azure AD OBO | Saksbehandlertoken → backend med brukerkontext |
| TokenX | Fjernet | Ikke relevant — ingen borgerflyt i saksbehandling |

## Del 2: Ansatt-endepunkt (`/api/ansatte/meg`)

### Kontekst

Frontenden kaller allerede `/api/ansatte/meg` (se `TilgangProvider.tsx`), men dette er kun mocka via MSW. Etter Azure-innlogging må dette endepunktet returnere ekte data fra entra-proxy (`/api/v1/ansatt/{navIdent}`).

### Auth-flyt (implementert)

**Flyt:**
1. Saksbehandler logger inn via Azure AD (Wonderwall sidecar)
2. BFF mottar Azure AD-token fra Wonderwall
3. BFF veksler via Azure AD OBO (`requestAzureOboToken`) → får nytt Azure AD-token for backend
4. Backend validerer Azure AD-token med `AZURE_AD_PROVIDER`
5. Backend leser `NAVident` fra token, kaller `EntraProxyClient.hentAnsatt(navIdent)` + `hentEnheter(navIdent)` med CC-flow
6. Returnerer `InnloggetAnsatt` til frontend

**Merk:** Backend har allerede `azure.application.enabled: true` i Nais-config.

**Backend-ruter (implementert i `/api/saksbehandling/v1`):**
- `GET /api/saksbehandling/v1/meg` — autentisert med `AZURE_AD_PROVIDER`
- `POST /api/saksbehandling/v1/enhet` — no-op/stateless
- `GET /api/saksbehandling/v1/oversikt` — saksoversikt

**BFF proxy-mapping:**
- `/api/ansatte/*` → `/api/saksbehandling/v1/*` med Azure AD OBO
- `/api/saksbehandling/oversikt` → `/api/saksbehandling/v1/oversikt` med Azure AD OBO

### Responsformat (matcher frontend-typen `InnloggetAnsatt`)

```json
{
  "id": "A123456",
  "navn": "Tore Tang",
  "epost": "tore.tang@nav.no",
  "enheter": [
    { "id": "uuid", "nummer": "1234", "navn": "Nav Avdeling Sydpolen" }
  ],
  "gjeldendeEnhet": { "id": "uuid", "nummer": "1234", "navn": "Nav Avdeling Sydpolen" }
}
```

Mapping fra `UtvidetAnsatt` (entra-proxy) → `InnloggetAnsatt` (frontend):
- `id` ← `navIdent`
- `navn` ← `visningNavn`
- `epost` ← `epost`
- `enheter` ← `hentEnheter(navIdent)` (med `enhetnummer` → `nummer`)
- `gjeldendeEnhet` ← første enhet (eller fra brukerpreferanse)

### Vite proxy-oppdatering

Legg til `/api` i vite proxy for lokal utvikling:

```typescript
"/api": {
  target: "http://localhost:4000",
  changeOrigin: true,
},
```

## Acceptance Criteria

### Del 1: Azure-innlogging (Nais-config)
- [x] `nais/dev-gcp-saksbehandling.yaml` har `azure.application.enabled: true` med `allowAllUsers: true`
- [x] Wonderwall sidecar er aktivert med `autoLogin: false`
- [x] TokenX er fjernet (ikke relevant for saksbehandler-app)
- [x] `EKSPERTBISTAND_API_AUDIENCE` env-variabel er satt til `api://dev-gcp.fager.ekspertbistand-backend/.default`
- [x] `accessPolicy.outbound.rules` inneholder `ekspertbistand-backend`
- [x] `idporten`-blokken er fjernet
- [ ] Appen deployer og starter uten feil i dev-gcp
- [ ] `/oauth2/session` returnerer gyldig sesjon etter innlogging

### Del 2: Ansatt-endepunkt
- [ ] `/api/ansatte/meg` returnerer ekte ansatt-data fra entra-proxy etter innlogging
- [ ] Responsformat matcher frontend-typen `InnloggetAnsatt`
- [ ] `NAVident` hentes fra autentisert token
- [ ] Entra-proxy kalles via `EntraProxyClient` (CC-flow)
- [x] BFF proxy-mapping fikset (`/api/ansatte` → `/api/saksbehandling/v1`)
- [ ] Vite proxy oppdatert for lokal utvikling
