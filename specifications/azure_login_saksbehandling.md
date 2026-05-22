# Azure AD (Entra ID) pålogging for saksbehandling-frontend

## Mål

Aktivere Azure AD-innlogging via Wonderwall sidecar for `ekspertbistand-saksbehandling` i dev-gcp, slik at saksbehandlere må autentisere seg med Nav-kontoen sin for å bruke appen.

## Bakgrunn

Frontenden (`frontend/saksbehandling`) og BFF-serveren (`frontend/saksbehandling-server`) er allerede forberedt for Wonderwall:
- Frontenden bruker `/oauth2/session` og `/oauth2/login` (via `useSession` hook og `LOGIN_URL` konstant)
- BFF-serveren har TokenX OBO-middleware for å veksle brukertoken mot backend
- Det mangler kun Nais-konfigurasjon for å aktivere sidecaren

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

Aktiver TokenX for OBO-token mot backend:

```yaml
tokenx:
  enabled: true
```

Legg til env-variabel for TokenX audience:

```yaml
env:
  - name: EKSPERTBISTAND_API_AUDIENCE
    value: "dev-gcp:fager:ekspertbistand-backend"
```

Legg til accessPolicy for utgående trafikk til backend:

```yaml
accessPolicy:
  outbound:
    rules:
      - application: ekspertbistand-backend
```

Fjern `idporten`-blokken (irrelevant for saksbehandler-app).

### 2. Verifiser BFF-server

Serveren (`frontend/saksbehandling-server/src/index.ts`) håndterer allerede:
- Wonderwall setter `Authorization: Bearer <token>` på innkommende requests
- `tokenXMiddleware` bruker `getToken(req)` fra `@navikt/oasis` for å hente token
- OBO-token veksles via `requestTokenxOboToken` mot `EKSPERTBISTAND_API_AUDIENCE`
- Lokal mock av `/oauth2/session` kjører kun når `NODE_ENV !== "production"`

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
| TokenX | Aktivert | Bevarer brukerkontext mot backend (OBO) |

## Del 2: Ansatt-endepunkt (`/api/ansatte/meg`)

### Kontekst

Frontenden kaller allerede `/api/ansatte/meg` (se `TilgangProvider.tsx`), men dette er kun mocka via MSW. Etter Azure-innlogging må dette endepunktet returnere ekte data fra entra-proxy (`/api/v1/ansatt/{navIdent}`).

### Utfordring: Auth-flyt

Backend validerer i dag kun TokenX-tokens fra ID-porten (krever `pid` og `acr: idporten-loa-high`). For saksbehandler-flyten er tokenet fra Azure AD → TokenX, som har `NAVident` i stedet for `pid`.

### Anbefalt tilnærming: Alternativ A — Nytt backend-endepunkt med Azure AD-originated TokenX

**Flyt:**
1. Saksbehandler logger inn via Azure AD (Wonderwall sidecar)
2. BFF mottar Azure AD-token fra Wonderwall
3. BFF veksler via TokenX OBO → får TokenX-token for backend
4. Backend validerer TokenX-token med ny auth-provider som aksepterer Azure AD-claims (`NAVident`)
5. Backend kaller `EntraProxyClient.hentAnsatt(navIdent)` + `hentEnheter(navIdent)` med CC-flow
6. Returnerer `InnloggetAnsatt` til frontend

**Backend-endringer:**
- Ny auth-provider `AZURE_TOKENX_PROVIDER` som validerer TokenX-tokens med `NAVident`-claim (i stedet for `pid`)
- Nytt endepunkt `GET /api/saksbehandling/ansatte/meg` autentisert med ny provider
- Endepunktet leser `NAVident` fra token, kaller `EntraProxyClient`
- Returnerer data i format som matcher frontend-typen `InnloggetAnsatt`
- Nytt endepunkt `POST /api/saksbehandling/ansatte/enhet` for å bytte valgt enhet (kan være no-op/stateless i første iterasjon)

**BFF-endringer:**
- Legg til proxy for `/api/ansatte/*` → backend `/api/saksbehandling/ansatte/*` (med TokenX OBO, samme som `/ekspertbistand-backend`)

**Nais backend-endringer:**
- Legg til `accessPolicy.inbound.rules` for `ekspertbistand-saksbehandling` i `dev-gcp-backend.yaml`

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
- [ ] `nais/dev-gcp-saksbehandling.yaml` har `azure.application.enabled: true` med `allowAllUsers: true`
- [ ] Wonderwall sidecar er aktivert med `autoLogin: false`
- [ ] TokenX er aktivert (`tokenx.enabled: true`)
- [ ] `EKSPERTBISTAND_API_AUDIENCE` env-variabel er satt til `dev-gcp:fager:ekspertbistand-backend`
- [ ] `accessPolicy.outbound.rules` inneholder `ekspertbistand-backend`
- [ ] `idporten`-blokken er fjernet
- [ ] Appen deployer og starter uten feil i dev-gcp
- [ ] `/oauth2/session` returnerer gyldig sesjon etter innlogging

### Del 2: Ansatt-endepunkt
- [ ] `/api/ansatte/meg` returnerer ekte ansatt-data fra entra-proxy etter innlogging
- [ ] Responsformat matcher frontend-typen `InnloggetAnsatt`
- [ ] `NAVident` hentes fra autentisert token
- [ ] Entra-proxy kalles via `EntraProxyClient` (CC-flow)
- [ ] Vite proxy oppdatert for lokal utvikling
