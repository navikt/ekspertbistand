# AaregClient — Sjekk deltakers ansettelsesforhold

## Mål

Ny Ktor HTTP-klient mot Aa-registeret (AAREG) for å verifisere at en deltaker (ansatt) har et aktivt arbeidsforhold hos den oppgitte virksomheten. Input hentes fra søknaden: `ansatt.fnr` og `virksomhet.virksomhetsnummer`.

## API-endepunkt

**GET** `/api/v2/arbeidstaker/arbeidsforhold`

Server: `https://aareg-services.intern.nav.no` (prod) / `https://aareg-services.intern.dev.nav.no` (dev)

OpenAPI-spec: https://aareg-services.intern.dev.nav.no/v3/api-docs/aareg.api.v2
Swagger: https://aareg-services.intern.dev.nav.no/swagger-ui/index.html?urls.primaryName=aareg.api.v2

### Request

Headers:
- `Authorization: Bearer <token>` — Azure AD token (required)
- `Nav-Personident: <fnr>` — deltakerens fødselsnummer (required)
- `Nav-Arbeidsstedident: <orgnr>` — virksomhetens organisasjonsnummer (optional, brukes som filter)

Query:
- `arbeidsforholdstatus=AKTIV` — kun aktive arbeidsforhold

### Response

`200 OK` — `List<Arbeidsforhold>`, der hvert element inneholder bl.a.:
- `arbeidssted.identer[]` — med `type=ORGANISASJONSNUMMER` og `ident`
- `ansettelsesperiode.startdato` / `sluttdato`

Feilkoder: 400, 401, 403, 404, 500 — alle returnerer `TjenestefeilResponse { meldinger: List<String> }`.

## Implementasjonsplan

### 1. AaregClient.kt

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/aareg/AaregClient.kt`

Følg mønsteret fra `ArenaClient`:
- Constructor: `AaregClient(tokenProvider: AzureAdTokenProvider, defaultHttpClient: HttpClient)`
- Konfigurer httpClient med `ContentNegotiation`, `HttpClientMetricsFeature` (clientName: `aareg.client`), `HttpTimeout` (15s)
- `companion object` med `basedOnEnv` for `ingress` og `targetAudience`
- En suspend-funksjon som kaller endepunktet og returnerer liste av arbeidsforhold

```
suspend fun hentArbeidsforhold(fnr: String, orgnr: String): List<Arbeidsforhold>
```

Sett headere `Nav-Personident` og `Nav-Arbeidsstedident`. Filtrer på `arbeidsforholdstatus=AKTIV`.

### 2. DTOer

I samme fil, `@Serializable` data classes — kun feltene vi trenger:

```
Arbeidsforhold(arbeidssted: Arbeidssted?, ansettelsesperiode: Ansettelsesperiode?)
Arbeidssted(type: String?, identer: List<Ident>?)
Ansettelsesperiode(startdato: String?, sluttdato: String?)
Ident(type: String?, ident: String?, gjeldende: Boolean?)
```

Bruk `ignoreUnknownKeys = true` (allerede i `defaultJson`) slik at vi slipper å modellere hele responsen.

### 3. DI-registrering

I `Application.kt` — legg til `provide(AaregClient::class)` i `dependencies`-blokken.

### 4. NAIS-konfigurasjon

Legg til `aareg-services` i `accessPolicy.outbound.external` i dev- og prod-backend YAML-filene.
Legg til Azure AD scope/audience for aareg-services.

## Acceptance Criteria

- [ ] `AaregClient` følger samme mønster som `ArenaClient` (constructor injection, httpClient config, companion object med basedOnEnv, bearer auth via AzureAdTokenProvider)
- [ ] Kaller `GET /api/v2/arbeidstaker/arbeidsforhold` med `Nav-Personident`, `Nav-Arbeidsstedident`, og `arbeidsforholdstatus=AKTIV`
- [ ] Returnerer `List<Arbeidsforhold>` med kun de feltene vi trenger (arbeidssted, ansettelsesperiode)
- [ ] DTOer er `@Serializable` og fungerer med `defaultJson` (ignoreUnknownKeys)
- [ ] Registrert i DI-containeren i `Application.kt`
- [ ] NAIS accessPolicy oppdatert for dev og prod
- [ ] Tester med prosjektets eksisterende testharness: `externalServices`-mock i `backend/src/test/kotlin/no/nav/ekspertbistand/mocks/` (se `TiltaksgjennomfoeringMock.kt` som referanse) og/eller Ktor `MockEngine` (se `DokgenClientTest.kt`). Ingen nye testbiblioteker. Verifiser happy path og feilhåndtering (401, 403, 404)
