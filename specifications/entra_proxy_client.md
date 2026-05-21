# EntraProxyClient — Hent enheter for innlogget saksbehandler

## Mål

Ny Ktor HTTP-klient mot entra-proxy for å hente alle NAV-enheter den innloggede saksbehandleren kan representere. Input er saksbehandlerens `navIdent`.

## API-endepunkt

**GET** `/api/v1/enhet/ansatt/{navIdent}`

Server: `https://entraproxy.intern.nav.no` (prod) / `https://entraproxy.intern.dev.nav.no` (dev)

OpenAPI-spec: `specifications/entra-proxy-openapi.json`

### Request

Headers:
- `Authorization: Bearer <token>` — Azure AD token (required, CC-flow)

Path:
- `navIdent` — saksbehandlerens NAV-ident (f.eks. `A123456`)

### Response

`200 OK` — `Set<Enhet>`:
```json
[
  { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" }
]
```

## Implementasjonsplan

### 1. EntraProxyClient.kt

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/entraproxy/EntraProxyClient.kt`

Følg mønsteret fra `ArenaClient`:
- Constructor: `EntraProxyClient(tokenProvider: AzureAdTokenProvider, defaultHttpClient: HttpClient)`
- Konfigurer httpClient med `ContentNegotiation`, `HttpClientMetricsFeature` (clientName: `entra-proxy.client`), `HttpTimeout` (15s)
- `companion object` med `basedOnEnv` for `ingress` og `targetAudience`
- En suspend-funksjon:

```
suspend fun hentEnheter(navIdent: String): List<Enhet>
```

### 2. DTOer

I samme fil, `@Serializable` data classes:

```
Enhet(enhetnummer: String, navn: String)
```

### 3. DI-registrering

I `Application.kt` — legg til `provide(EntraProxyClient::class)` i `dependencies`-blokken.

### 4. NAIS-konfigurasjon

Legg til `entraproxy` i `accessPolicy.outbound.external` i dev- og prod-backend YAML-filene.
Legg til Azure AD scope/audience for entra-proxy.

## Acceptance Criteria

- [ ] `EntraProxyClient` følger samme mønster som `ArenaClient` (constructor injection, httpClient config, companion object med basedOnEnv, bearer auth via AzureAdTokenProvider)
- [ ] Kaller `GET /api/v1/enhet/ansatt/{navIdent}` med bearer token
- [ ] Returnerer `List<Enhet>` med `enhetnummer` og `navn`
- [ ] DTOer er `@Serializable` og fungerer med `defaultJson` (ignoreUnknownKeys)
- [ ] Registrert i DI-containeren i `Application.kt`
- [ ] NAIS accessPolicy oppdatert for dev og prod
- [ ] Tester med prosjektets eksisterende testharness: `externalServices`-mock i `backend/src/test/kotlin/no/nav/ekspertbistand/mocks/` (se `TiltaksgjennomfoeringMock.kt` som referanse) og/eller Ktor `MockEngine` (se `DokgenClientTest.kt`). Ingen nye testbiblioteker. Verifiser happy path og feilhåndtering (401, 403)
