# Hent roller for innlogget bruker via entra-proxy

Trello: https://trello.com/c/HY9i8MkL/602-hente-roller-for-innlogget-bruker-vha-entra-proxy (kort-ID `6ab21225e41a2992e300da45`)

## Mål

Tilgangsstyre saksbehandler/beslutter på Entra-roller uten å bruke `groups`-claimet i token. Fordi vi må sette `allowAllUsers: true` i Azure-oppsettet (multi-tenant-problem i dev gjør `groups`-claimet ikke kan brukes uten at det ødelegger for M2M kall), skal `bearer(AZURE_AD_PROVIDER)` i `Auth.kt` i stedet slå opp gruppene til den innloggede saksbehandleren via entra-proxy og legge de relevante gruppene i `AzureAdPrincipal`. Oppslaget skjer lazily per request, på samme måte som `AzureAdTokenIntrospector` allerede kalles per request.

## Bakgrunn

I dag leser `bearer(AZURE_AD_PROVIDER)` (i `Auth.kt`) `groups`-claimet fra introspeksjonssvaret og fyller `AzureAdPrincipal.groups`. `Role.fromGroups` matcher så gruppenes **objectId** mot `Role.groupId`.

Dette slutter å virke når vi setter `allowAllUsers: true`: claimet kan mangle eller være ufullstendig i dev pga. multi-tenant. Løsningen er å hente gruppene fra entra-proxy med saksbehandlerens `navIdent` i stedet, og matche på gruppe-**navn** (displayName). Da trenger vi ikke lenger forholde oss til objectId i koden.

## API-endepunkt (entra-proxy)

**GET** `/api/v1/ansatt/tilganger/{navIdent}` — krever CC-flow (`@OAuth2RequireCCF`).

Server: `https://entraproxy.intern.nav.no` (prod) / `https://entraproxy.intern.dev.nav.no` (dev).

Kilde: `navikt/entra-proxy` → `EntraController.grupperForAnsatt` (`@GetMapping("/ansatt/tilganger/{navIdent}")`).

### Request

- Header: `Authorization: Bearer <token>` — Azure AD CC-flow-token (audience = `EntraProxyClient.targetAudience`)
- Path: `navIdent` — saksbehandlerens NAV-ident (f.eks. `A123456`)

### Response

`200 OK` — `Set<EntraGruppe>`, der `EntraGruppe(val rolle: String)`:

```json
[
  { "rolle": "0000-CA-Ekspertbistand_Saksbehandler" },
  { "rolle": "0000-CA-Ekspertbistand_Beslutter" }
]
```

`rolle` er gruppens displayName, ikke objectId.

### Gruppenavn (dev, opprettet)

```
0000-CA-Ekspertbistand_Saksbehandler   objectId: ef346e01-fe04-4da6-9f4a-458ceb33052d
0000-CA-Ekspertbistand_Beslutter        objectId: 883651d8-207d-452c-a7ed-4507420e99c9
```

I prod venter vi på klarsignal fra identitet-teamet, men vi antar samme navn. objectId brukes ikke lenger i koden.

## Implementasjonsplan

### 1. `EntraProxyClient.kt` — ny metode + DTO

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/entraproxy/EntraProxyClient.kt`.

- Ny konstant i `companion object`: `const val GRUPPER_API_PATH = "/api/v1/ansatt/tilganger"`
- Ny suspend-funksjon (følger samme mønster som `hentEnheter`/`hentAnsatt`, CC-flow via `tokenProvider`):

  ```
  suspend fun hentGrupper(navIdent: String): List<EntraGruppe>
  ```

- Ny `@Serializable data class EntraGruppe(val rolle: String)` i samme fil.

### 2. `Auth.kt` — gruppeoppslag i `bearer(AZURE_AD_PROVIDER)`

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Auth.kt`.

I `authenticate`-blokken for `AZURE_AD_PROVIDER`, etter at `navIdent` er lest fra introspeksjonssvaret:

- Fjern lesing av `groups` fra `other["groups"]`.
- Resolve `EntraProxyClient` fra `application.dependencies` (lazily, som `AzureAdTokenIntrospector`).
- Kall `hentGrupper(navIdent)` og map `.rolle` inn i `AzureAdPrincipal.groups`.
- **Behold kun gruppene vi bryr oss om** — filtrer bort grupper som ikke tilsvarer en kjent `Role`, slik at `AzureAdPrincipal.groups` ikke inneholder alt entra-proxy returnerer (f.eks. `grupper.map { it.rolle }.filter { navn -> Role.entries.any { it.groupId == navn } }`).

`AzureAdPrincipal.groups` beholder typen `List<String>`, men inneholder nå de relevante gruppe-**navnene** i stedet for objectId. `SaksbehandlerApi` og `Role.fromGroups` er uendret i signatur.

**Feilhåndtering (Q2 – avklart):** La kall-feil mot entra-proxy boble opp slik at requesten avvises (401). Ved feil skal det logges med både `log.error` (uten PII) og `teamLog.error` (kan inneholde `navIdent`) for å trigge alerts. Følg logg-standarden: `logger()` for vanlig logg, `teamLogger()` for team-logg (se `infrastruktur/Logging.kt`).

### 3. `Roles.kt` — match på gruppenavn

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Roles.kt`.

Endre `Role.groupId` fra objectId til gruppenavn (samme navn i prod og dev):

```
SAKSBEHANDLER              → "0000-CA-Ekspertbistand_Saksbehandler"
BESLUTTER                  → "0000-CA-Ekspertbistand_Beslutter"
FORTROLIG_ADRESSE          → "0000-GA-Fortrolig_Adresse"
STRENGT_FORTROLIG_ADRESSE  → "0000-GA-Strengt_Fortrolig_Adresse"
```

`other`-verdiene (brukt i test) oppdateres tilsvarende til navnebaserte testverdier. `fromGroups` er uendret. Siden prod og dev bruker samme navn kan `basedOnEnv` forenkles til én verdi per rolle (eller beholdes med lik verdi for prod/dev).

### 4. NAIS-konfigurasjon

- `nais/dev-gcp-backend.yaml`: sett `azure.application.allowAllUsers: true`.
- `nais/prod-gcp-backend.yaml`: sett `azure.application.allowAllUsers: true`, og fjern den utkommenterte `claims.groups`-blokken (gruppene hentes nå fra entra-proxy).
- `accessPolicy.outbound.external` har allerede `entraproxy.intern.*.nav.no` i begge miljøer — ingen endring.

### 5. Tester

- `EntraProxyMock.kt`: ny `mockEntraProxyGrupper` (og evt. utvid `mockEntraProxyFull` med gruppe-provider) som svarer på `GET ${GRUPPER_API_PATH}/{navIdent}`.
- `EntraProxyClientTest.kt`: ny test `henter grupper for saksbehandler` (happy path), pluss ukjente felter og token-feil, i samme stil som eksisterende tester.
- `SaksbehandlerRoutingTest.kt`: gruppene kommer nå fra entra-proxy-mocken, ikke fra `withGroups(...)` på introspeksjonssvaret. Oppdater `happy path`, `token uten groups gir tom roller-liste` osv. slik at rollene styres av gruppe-mocken. Behold verifisering av at `roller` mappes korrekt.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/.../entraproxy/EntraProxyClient.kt` | `hentGrupper` + `EntraGruppe` + `GRUPPER_API_PATH` |
| `backend/.../infrastruktur/Auth.kt` | Gruppeoppslag via entra-proxy i `AZURE_AD_PROVIDER` |
| `backend/.../saksbehandling/Roles.kt` | Match på gruppenavn i stedet for objectId |
| `nais/dev-gcp-backend.yaml` | `allowAllUsers: true` |
| `nais/prod-gcp-backend.yaml` | Fjern død `claims.groups`-blokk (+ evt. `allowAllUsers`) |
| `backend/src/test/.../mocks/EntraProxyMock.kt` | `mockEntraProxyGrupper` |
| `backend/src/test/.../entraproxy/EntraProxyClientTest.kt` | Test for `hentGrupper` |
| `backend/src/test/.../saksbehandling/SaksbehandlerRoutingTest.kt` | Grupper fra entra-proxy-mock |

## Edge cases og feilmodus

- **Tomt gruppesvar (`[]`)**: `AzureAdPrincipal.groups` blir tom → `Role.fromGroups` gir tomt sett → 403 på beskyttede endepunkt. Forventet.
- **Ukjente grupper i svaret**: filtreres bort allerede i `Auth.kt` (vi beholder kun grupper som tilsvarer en kjent `Role`).
- **entra-proxy nede / 401 / 403**: kall-feil bobler → 401. Logges med `log.error` + `teamLog.error` for å trigge alerts.
- **Ytelse**: ett ekstra HTTP-kall til entra-proxy per autentisert request (i tillegg til introspeksjon). Ingen klient-side caching — entra-proxy cacher selv server-side.
- **PII**: `navIdent` og gruppenavn logges kun via `teamLog` (team-logg), aldri via vanlig `log`. Følg eksisterende loggmønster.

## Beslutninger

| Beslutning | Valg | Begrunnelse |
|-----------|------|-------------|
| Kilde for grupper | entra-proxy (`/ansatt/tilganger/{navIdent}`) | `groups`-claim upålitelig med `allowAllUsers` i multi-tenant dev |
| Matching | Gruppe-**navn** (displayName) | entra-proxy returnerer navn; objectId trengs ikke lenger |
| Oppslagstidspunkt | Per request i `bearer(AZURE_AD_PROVIDER)` | Lazy, samme mønster som introspeksjon |
| Auth-flow mot entra-proxy | CC-flow (`AzureAdTokenProvider`) | Endepunktet krever `@OAuth2RequireCCF` |
| Grupper som beholdes | Kun grupper som matcher en kjent `Role` | Vi tar ikke vare på alt entra-proxy returnerer |
| Feilhåndtering | Kall-feil bobler → 401, logges med `log.error` + `teamLog.error` | Trigger alerts; sikkerhet framfor stille degradering |
| Caching | Ingen klient-side caching | entra-proxy har selv kontroll på caching |
| Prod-rollout | Samme gruppenavn og `allowAllUsers: true` som dev nå | Bekreftet av teamet |

## Avklaringer

- **Q1 – prod:** Prod bruker samme gruppenavn som dev nå, og `allowAllUsers: true` settes også i prod.
- **Q2 – feilhåndtering:** Kall-feil under gruppeoppslag bobler → requesten avvises (401). Logg med `log.error` og `teamLog.error`.
- **Q3 – caching:** Ingen klient-side caching; ett kall per request er godt nok.

## Definisjon av ferdig

- [ ] `EntraProxyClient.hentGrupper(navIdent)` kaller `GET /api/v1/ansatt/tilganger/{navIdent}` med CC-flow og returnerer `List<EntraGruppe>`
- [ ] `bearer(AZURE_AD_PROVIDER)` fyller `AzureAdPrincipal.groups` fra entra-proxy, filtrert til kun kjente `Role`-grupper, ikke fra token-claim
- [ ] Kall-feil mot entra-proxy bobler → 401, logget med `log.error` + `teamLog.error`
- [ ] `Role` matcher på gruppenavn (inkl. Fortrolig/Strengt fortrolig); objectId er fjernet fra koden
- [ ] `allowAllUsers: true` satt i både dev og prod; død `claims.groups`-blokk fjernet i prod
- [ ] Ingen klient-side caching av entra-proxy-kall
- [ ] Tester dekker happy path (roller utledet fra entra-proxy), tomt gruppesvar og feilhåndtering (401), med prosjektets eksisterende testharness (ingen nye testbibliotek)
- [ ] `SaksbehandlerRoutingTest` oppdatert til å styre roller via entra-proxy-mock
