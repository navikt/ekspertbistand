# Saksbehandling-mock mot mocka backend

Trello: https://trello.com/c/sUrtiwWE
Kort-ID: `6ad5d052b75d488ff859eea7`

## Mål

Lage en mock av saksbehandlingsløsningen i dev-gcp som oppfører seg som når vi
kjører `LocalApplication` og saksbehandling-frontend lokalt: ekte backend og
database, mockede eksterne tjenester og de fire lokale testbrukerne med
brukerbytte. Databasen slettes og fylles med testdataene fra `LocalApplication`
hver gang backend starter.

## Bakgrunn

`ekspertbistand-saksbehandling-mock` kjører i dag bare frontend med MSW. Mockene i
nettleseren gir faste svar og har ingen tilstand på tvers av kall, så flyter som
tildeling, vilkårsvurdering, foreløpig vedtak og beslutning kan ikke testes
helt. Lokalt får vi det fulle bildet ved å kjøre `LocalApplication` mot Postgres
og saksbehandling-frontend med brukerbytte. Det oppsettet flytter vi til dev-gcp.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortet | Ny app med backend og testbrukere, satt opp likt som `LocalApplication` + frontend lokalt. |
| Kortet | «Ha noe leasing på testbrukere?» Anders, 2026-10-26: utenfor scope. Alle deler de fire testbrukerne. Mulig videre arbeid. |
| Anders, 2026-10-26 | Bare saksbehandling. `ekspertbistand-frontend-mock` (arbeidsgiver) beholdes med MSW. |
| Anders, 2026-10-26 | Dagens `dev-gcp-saksbehandling-mock.yaml` og workflow overskrives/erstattes. |
| Anders, 2026-10-26 | Egen Cloud SQL-instans. Flyway `clean` + `migrate` og testdata ved hver oppstart. Det er greit at en pod-restart også nullstiller. |
| Anders, 2026-10-26 | Mock-koden blir i `src/test`. Eget image bygges fra test-classpath; prod-jaren endres ikke. |
| Anders, 2026-10-26 | Tilgang som i dag: åpen ingress på `ansatt.dev.nav.no` uten innlogging. Backend-mocken nås bare fra mock-BFF-en. |
| Anders, 2026-10-26 | Deploy ved push til `main` og ved `workflow_dispatch`. Backend og frontend i samme workflow. |

## Tilnærming

### 1. Felles oppstart for lokal og mock

Innmaten i `LocalApplication.main` flyttes til en funksjon i samme fil:

```kotlin
fun startLokalApplikasjon(dbConfig: DbConfig) { ... }
```

Funksjonen tar imot en ferdig migrert database. Resten er som i dag: mockede
klienter, `ktorServer { ... }`, `initTestData` og `behandleLokaleSaker`.
`initTestData` tar `Database` i stedet for `TestDatabase`.

- `LocalApplication.main` lager `TestDatabase().cleanMigrate()` og kaller
  `startLokalApplikasjon(testDb.config)`. Lokal kjøring virker som før.
- Ny `backend/src/test/kotlin/no/nav/ekspertbistand/MockApplication.kt` har en
  egen `main`:
  1. lager `DbConfig.nais()` fra `DB_JDBC_URL`
  2. setter `cleanDisabled(false)` og kjører `clean()` og `migrate()`
  3. kaller `startLokalApplikasjon(dbConfig)`

`flyway clean` kan dermed bare kjøres fra test-kildene. `Application.kt` og
`DbConfig` i `src/main` endres ikke.

PDF går gjennom `LokalPdfKonverterer` som lokalt. `GotenbergClient.baseUrl` peker
til `ekspertbistand-gotenberg` i dev-gcp. Svarer ikke Gotenberg, viser
fallback-PDF-en at den mangler.

### 2. Image fra test-classpath

Maven-profilen `mock` i `backend/pom.xml` legger til:

- `maven-jar-plugin:test-jar`, som gir `app-tests.jar` med test-klassene
- en ekstra `maven-assembly-plugin`-kjøring med `assembly-mock.xml`. Den legger
  `app.jar` og `app-tests.jar` i `target/ekspertbistand-backend-mock/` og alle
  avhengigheter (også test-scope) i `lib/`

`backend/Dockerfile.mock` bruker samme JRE-image som `backend/Dockerfile`:

```dockerfile
COPY target/ekspertbistand-backend-mock/ ./
CMD ["-cp", "app.jar:app-tests.jar:lib/*", "no.nav.ekspertbistand.MockApplicationKt"]
```

Bygges med `mvn -B -Pmock -DskipTests package`. Testene kjører allerede i
`cicd-backend.yaml`.

### 3. Nais: backend-mock

Ny `nais/dev-gcp-backend-mock.yaml`, `ekspertbistand-backend-mock`:

- 1 replika (min = max = 1), port 8080
- liveness `/internal/isalive`, readiness `/internal/isready`
- egen `sqlInstances`: `ekspertbistand-backend-mock`, `POSTGRES_18`, `db-f1-micro`,
  database `ekspertbistand`, `envVarPrefix: DB`
- ingen `tokenx`, `azure`, `kafka` eller `bigQueryDatasets`
- `accessPolicy.inbound`: bare `ekspertbistand-saksbehandling-mock`
- `accessPolicy.outbound`: `ekspertbistand-gotenberg` og `logging` (nais-system)

`nais/dev-gcp-gotenberg.yaml` får inbound fra `ekspertbistand-backend-mock`.

Backend-mocken godtar `faketoken`. Den har derfor ingen ingress, og bare
mock-BFF-en kan nå den.

### 4. BFF: brukerbytte i mock-imaget

`frontend/saksbehandling-server/src/index.ts` får env-flagget
`LOKAL_BRUKERBYTTE`. Når `LOKAL_BRUKERBYTTE=true`:

- `/oauth2/session` og `/lokal/brukere` er aktive også med
  `NODE_ENV=production`
- cookien `lokal-bruker` velger bruker, og `faketoken-<navIdent>` sendes til
  backend
- fail-open-sjekken tillater oppstart uten `AZURE_APP_CLIENT_ID`

Flagget erstatter dagens unntak for `VITE_ENABLE_MOCKS` i BFF-en. Er både
`LOKAL_BRUKERBYTTE` og `AZURE_APP_CLIENT_ID` satt, avslutter BFF-en ved oppstart,
så flagget aldri kan skru av Azure i den ekte appen.

CSP er uendret: SPA-en snakker bare med egen origin.

### 5. SPA: brukervelger utenfor localhost

`isLokalBrukerbytteMulig()` i `frontend/saksbehandling/src/utils/constants.ts`
blir sann når `VITE_LOKAL_BRUKERBYTTE=true` er satt ved bygg, i tillegg til
dagens localhost-regel. Mock-imaget bygges uten `VITE_ENABLE_MOCKS`, så MSW
ikke starter.

MSW-mockene beholdes. De brukes ved lokal kjøring uten backend og i e2e-testene.

### 6. Nais: saksbehandling-mock

`nais/dev-gcp-saksbehandling-mock.yaml` overskrives:

- env `EKSPERTBISTAND_API_BASEURL=http://ekspertbistand-backend-mock` og
  `LOKAL_BRUKERBYTTE=true`
- `accessPolicy.outbound`: `ekspertbistand-backend-mock`
- ingress, `tokenx`/`idporten` av og ressurser som i dag

### 7. Workflow

`.github/workflows/deploy-dev-manual-saksbehandling-mock.yml` erstattes av
`.github/workflows/deploy-dev-mock.yml`:

- Trigger: `workflow_dispatch` og push til `main` med endringer i `backend/**`,
  `frontend/saksbehandling/**`, `frontend/saksbehandling-server/**`,
  `nais/dev-gcp-backend-mock.yaml`, `nais/dev-gcp-saksbehandling-mock.yaml` eller
  selve workflowen.
- Jobb `build-backend-mock`: Java 21, `mvn -B -Pmock -DskipTests package`,
  `nais/docker-build-push` med `image_suffix: backend-mock` og
  `dockerfile: backend/Dockerfile.mock`.
- Jobb `build-saksbehandling-mock`: som dagens jobb, men med
  `VITE_LOKAL_BRUKERBYTTE=true` som build-arg og uten `VITE_ENABLE_MOCKS`.
- Jobb `deploy`: deployer backend-mock og deretter saksbehandling-mock til dev-gcp.
- Eksplisitte, minimale `permissions` per jobb, som i dagens workflows.

`frontend/saksbehandling-server/Dockerfile` tar imot `VITE_LOKAL_BRUKERBYTTE` som
`ARG`/`ENV` i byggesteget.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt` | `startLokalApplikasjon(dbConfig)` trekkes ut; `main` bruker `TestDatabase` som før |
| `backend/src/test/kotlin/no/nav/ekspertbistand/MockApplication.kt` | Ny: `main` med `DbConfig.nais()`, clean + migrate |
| `backend/pom.xml` | Ny profil `mock` (test-jar + test-scope-avhengigheter) |
| `backend/assembly-mock.xml` | Ny: assembly for `target/ekspertbistand-backend-mock/` |
| `backend/Dockerfile.mock` | Ny |
| `nais/dev-gcp-backend-mock.yaml` | Ny |
| `nais/dev-gcp-gotenberg.yaml` | Inbound fra `ekspertbistand-backend-mock` |
| `nais/dev-gcp-saksbehandling-mock.yaml` | Overskrives: peker mot backend-mock, `LOKAL_BRUKERBYTTE=true` |
| `frontend/saksbehandling-server/src/index.ts` | Flagget `LOKAL_BRUKERBYTTE` |
| `frontend/saksbehandling-server/Dockerfile` | Build-arg `VITE_LOKAL_BRUKERBYTTE` |
| `frontend/saksbehandling/src/utils/constants.ts` | `isLokalBrukerbytteMulig()` respekterer `VITE_LOKAL_BRUKERBYTTE` |
| `.github/workflows/deploy-dev-mock.yml` | Ny |
| `.github/workflows/deploy-dev-manual-saksbehandling-mock.yml` | Fjernet |
| `frontend/README.md`, `backend/README.md` | Beskriv mocken, testbrukerne og nullstilling ved oppstart |

## Kanttilfeller og feilmodus

- **Pod-restart.** Databasen nullstilles og fylles på nytt. Det er akseptert.
  Data testere har lagt inn, forsvinner.
- **Flere testere samtidig.** Alle deler de samme dataene og de fire brukerne.
  Leasing er videre arbeid.
- **Første deploy.** Nais oppretter Cloud SQL-instansen, og det tar noen
  minutter. Backend-mocken er ikke klar før databasen er det. BFF-en svarer 502
  på API-kall i mellomtiden.
- **Migrering som feiler.** `clean` + `migrate` feiler ved oppstart, poden
  krasjer og deployen blir rød. Samme migrering feiler også i `cicd-backend`.
- **Migrering endret etter at den har kjørt.** Ingen checksum-feil, siden
  databasen alltid er tom før `migrate`.
- **Gotenberg nede.** PDF-forhåndsvisning viser fallback-PDF-en; resten virker.
- **Bakgrunnsjobber.** Utkast-sletting, retention og event-handlere kjører som
  lokalt. Eksterne kall går til mockene i `LocalApplication`. Kafka-konsumenter
  startes ikke.
- **Feilkonfigurasjon i ekte app.** `LOKAL_BRUKERBYTTE` sammen med Azure stopper
  BFF-en ved oppstart. Backend-mocken har ingen ingress og bare én inbound-regel.
- **Test-avhengigheter i imaget.** `ktor-server-test-host`, `pdfbox` og
  lignende følger med. Det er akseptert for en dev-mock, og prod-imaget berøres
  ikke.

## Utenfor scope

- Arbeidsgiver-mocken (`ekspertbistand-frontend-mock`)
- Leasing av testbrukere
- Innlogging foran mocken

## Ferdig når

- [ ] `LocalApplication` virker lokalt som før
- [ ] `mvn -B -Pmock -DskipTests package` gir `target/ekspertbistand-backend-mock/`,
      og `Dockerfile.mock` starter `MockApplicationKt`
- [ ] `ekspertbistand-backend-mock` kjører i dev-gcp med egen Cloud SQL og er
      bare nåbar fra `ekspertbistand-saksbehandling-mock`
- [ ] Databasen er nullstilt og har testdataene fra `LocalApplication` etter hver
      oppstart
- [ ] `https://ekspertbistand-saksbehandling-mock.ansatt.dev.nav.no` viser sakene
      fra backend-mocken, og brukervelgeren bytter mellom de fire testbrukerne
- [ ] MSW starter ikke i mock-imaget, men virker fortsatt lokalt og i e2e
- [ ] BFF-en nekter å starte med både `LOKAL_BRUKERBYTTE` og Azure
- [ ] `deploy-dev-mock.yml` bygger og deployer begge apper ved push til `main` og
      ved `workflow_dispatch`; den gamle workflowen er fjernet
- [ ] README-ene beskriver mocken
