# Arbeidsgiver-mock mot mocka backend

Trello: https://trello.com/c/oy57Uh7U
Kort-ID: `6ae31c85e866fcb36c2d73b0`

## Mål

Teste hele flyten ende til ende, fra søknad via saksbehandling og vedtak til
refusjonskrav og sluttrapport, både lokalt og i mock-miljøet i dev-gcp.
Arbeidsgiverfrontenden (søknad) skal da gå mot den samme mocka backenden som
saksbehandling: `LocalApplication` lokalt og `ekspertbistand-backend-mock` i
dev-gcp.

## Bakgrunn

Saksbehandling-frontend går i dag mot `LocalApplication` lokalt og mot
`ekspertbistand-backend-mock` i dev-gcp, se
`specifications/saksbehandling_mock_mot_mocka_backend.md`. Arbeidsgiverfrontenden
gjør ikke det:

- `ekspertbistand-frontend-mock` kjører med MSW. Den har ingen tilstand på tvers
  av kall og ser ikke sakene som behandles i saksbehandling-mocken.
- `LocalApplication` registrerer ikke vedlegg-, refusjon- eller
  kontoregister-API-et. Refusjonskrav og sluttrapport svarer 404 lokalt og i
  backend-mocken.
- Testbrukeren (pid `42`) har Altinn-tilgang bare til `123456789` og
  `123456780`. Av de lokale søknadene ligger bare én på disse orgnumrene, og den
  er ikke behandlet. De to søknadene med fattet vedtak ligger på orgnumre
  arbeidsgiveren ikke har tilgang til. Refusjon og sluttrapport krever en
  søknad med status `godkjent`.
- I `vite dev` starter MSW alltid (`import.meta.env.DEV`), så klienten kan ikke
  kjøres med hot reload mot `LocalApplication`.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortet | Arbeidsgiverflyten skal kunne testes mot mocka backend, lokalt og i mock-miljøet. |
| Ken, 2026-10-29 | `ekspertbistand-frontend-mock` overskrives og går mot backend-mocken. Ingen har harde koblinger til MSW-mocken, men NKS bruker den, så de samme tilstandene skal vises. |
| Ken, 2026-10-29 | Testbrukeren får tilgang til alle lokale virksomheter. Tre innvilgede saker holder. |
| Ken, 2026-10-29 | Én workflow (`deploy-dev-mock.yml`). Saksbehandling får «Se som arbeidsgiver» der brukervelgeren vises. |
| Spesifikasjonen | Flagget `LOKAL_INNLOGGING` i BFF-en, som `LOKAL_BRUKERBYTTE`, erstatter unntaket for `VITE_ENABLE_MOCKS`. Én testbruker (pid `42`). |
| Forrige mock | Mock-koden blir i `src/test`, databasen nullstilles ved oppstart, og backend-mocken nås bare fra mock-BFF-ene. |
| Ken, 2026-10-30 | Erstatter beslutningen fra 2026-10-29 om å overskrive `ekspertbistand-frontend-mock`. Den går tilbake til MSW, fordi backend-mocken ikke kan vise tilskuddsbrev før eventene finnes i ny saksbehandlingsflyt. Mocken mot backend-mocken blir en ny app, `ekspertbistand-frontend-e2e-mock`, med egen ingress. BFF-en beholder derfor unntaket for `VITE_ENABLE_MOCKS` ved siden av `LOKAL_INNLOGGING`. |

## Tilnærming

### 1. Backend: refusjon, sluttrapport og kontoregister i `startLokalApplikasjon`

`startLokalApplikasjon` i `LocalApplication.kt` registrerer det samme som
`Application.kt` gjør utenfor prod:

- `configureVedleggApiV1()` (sluttrapport)
- `configureRefusjonApiV1()`
- `configureKontoregisterApiV1()`, som i dag er kommentert ut

Avhengighetene som mangler, mockes med `MockEngine` på samme måte som Altinn og
Ereg:

- **`ClamAvClient`.** Svarer `OK` for alle filer. Lokalt hopper klienten over
  skanningen (`endpointUrl` er `null` utenfor Nais), men i dev-gcp poster den til
  `clamav.nais-system`. Mocken gjør at backend-mocken ikke trenger
  outbound-regel til ClamAV.
- **`KontoregisterClient`.** Svarer med et fiktivt kontonummer for alle lokale
  virksomheter, unntatt én som svarer 404. Da kan både «kontonummer finnes» og
  «kontonummer mangler» testes. `tokenProvider` er
  `successAzureAdTokenProvider`.

Refusjon og sluttrapport publiserer ingen eventer. De skriver bare til
databasen, så ingen nye handlere eller mocker trengs.

### 2. Backend: testdata arbeidsgiveren ser

- Altinn-mocken bygges fra `lokaleVirksomheter`: hovedenheten `123456789` med
  alle lokale virksomheter som underenheter, alle med
  `nav_tiltak_ekspertbistand`. Pid `42` ser da alle de lokale søknadene, også
  de med fattet vedtak.
- `lokaleSaker` får to nye saker med innvilget og godkjent vedtak, til sammen
  tre. Refusjonskrav og sluttrapport kan sendes én gang per søknad, så én sak
  holder ikke lenge når flere tester samtidig.

Søknadene får status `godkjent` gjennom `SettSoknadStatusVedtakFattet`, som i
dag. Tiltaksperioden slutter 31. desember inneværende år, så refusjonsfristen
er ikke nådd.

Testdataene dekker tilstandene MSW-mocken viser i dag
(`frontend/client/src/mocks/handlers.ts`), fordi NKS bruker mocken:

| Tilstand i MSW | Slik oppstår den i backend-mocken |
|----------------|-----------------------------------|
| Godkjent, kontonummer finnes | En av de tre innvilgede sakene, på en virksomhet med kontonummer |
| Godkjent, kontonummer mangler | En av de tre innvilgede sakene, på virksomheten der Kontoregister-mocken svarer 404 |
| Avslått | Dagens sak med avslag og godkjent vedtak |
| Avlyst | Ny innsendt søknad. `LocalApplication` publiserer `SoknadAvlystIArena` for den, slik Arena-konsumenten gjør, og `SettAvlystSoknadStatus` setter status `avlyst` |
| Tilskuddsbrev på godkjent søknad | `LocalApplication` publiserer `TilskuddsbrevMottatt` for én godkjent søknad, slik Arena-konsumenten gjør. Eventkjeden journalfører mot DokArkiv-mocken og lagrer tilsagnsdataene, og kvitteringssiden viser brevet |
| Utkast | Opprettes av brukeren, som i MSW. Ingen testdata |

Ingen nye eventtyper trengs. Eventene publiseres fra `src/test`, på samme måte
som `behandleLokaleSaker` publiserer saksbehandlingseventene.

### 3. BFF: `LOKAL_INNLOGGING` i arbeidsgiver-BFF-en

`frontend/server/src/index.ts` får env-flagget `LOKAL_INNLOGGING`. Når
`LOKAL_INNLOGGING=true`:

- `/oauth2/session` svarer også med `NODE_ENV=production`
- TokenX er av, og `faketoken` (eller `LOCAL_SUBJECT_TOKEN`) sendes til backend,
  som i dag lokalt
- fail-open-sjekken tillater oppstart uten `TOKEN_X_ISSUER`

Unntaket for `VITE_ENABLE_MOCKS` blir stående for MSW-mocken
(`ekspertbistand-frontend-mock`), som setter flagget i runner-steget i
`Dockerfile`. Er både
`LOKAL_INNLOGGING` og `TOKEN_X_ISSUER` satt, avslutter BFF-en ved oppstart, så
flagget aldri kan skru av TokenX i den ekte appen.

CSP er uendret: SPA-en snakker bare med egen origin.

### 4. SPA: uten MSW og uten Wonderwall

I `frontend/client/src/utils/constants.ts`:

- `isMockEnabled()` returnerer `false` når `VITE_ENABLE_MOCKS=false`, også i
  `vite dev`. Det er samme regel som i saksbehandling. Uten flagget starter MSW
  i `vite dev` som før, så e2e-testene er uendret.
- Ny `isLokalInnlogging()`, sann når `VITE_LOKAL_INNLOGGING=true` er satt ved
  bygg. `LOGIN_URL` og `MIN_SIDE_URL` behandler lokal innlogging likt som MSW i
  dev: rett til `/soknader` uten `/oauth2/login`, og lenke til
  `arbeidsgiver-dev-like`.

`frontend/client/vite.config.ts` proxyer også `/oauth2` til BFF-en på port
4000, så `vite dev` får sesjonen fra BFF-en.

MSW-mockene beholdes. De brukes ved lokal kjøring uten backend og i
e2e-testene.

### 5. Saksbehandling: «Se som arbeidsgiver»

Saksdetaljene i saksbehandling får knappen «Se som arbeidsgiver». Den vises
under samme betingelse som brukervelgeren, `isLokalBrukerbytteMulig()`: lokalt
mot `LocalApplication` og i `ekspertbistand-saksbehandling-mock`. I den ekte
appen og med MSW vises den ikke.

- Plassering: til høyre i headeren med navn og fødselsnummer, øverst i
  `SakPage.tsx`.
- Aksel `Button` som lenke (`as="a"`), `variant="secondary"`, `size="small"`,
  `ExternalLinkIcon` til høyre. Åpnes i ny fane (`target="_blank"`,
  `rel="noopener noreferrer"`), så saksbehandlervisningen blir stående.
- Lenken går til kvitteringssiden for søknaden i arbeidsgiverfrontenden,
  `/skjema/<soknadId>/kvittering`. Derfra kommer man videre til refusjonskrav
  og sluttrapport.
- Ny funksjon `arbeidsgiverSoknadUrl(soknadId)` i
  `frontend/saksbehandling/src/utils/constants.ts` velger base-URL:
  - localhost: `http://localhost:4000`, arbeidsgiver-BFF-en (saksbehandling-BFF-en
    bruker 4001)
  - ellers: `https://ekspertbistand-arbeidsgiver-e2e-mock.ansatt.dev.nav.no/ekspertbistand`,
    som er `ekspertbistand-frontend-e2e-mock`

Lenken bærer ingen innlogging. Arbeidsgiverfrontenden logger alltid inn som pid
`42`, som har tilgang til alle lokale virksomheter, se punkt 2.

### 6. Nais

Ny `nais/dev-gcp-frontend-e2e-mock.yaml` for `ekspertbistand-frontend-e2e-mock`:

- ingress `https://ekspertbistand-arbeidsgiver-e2e-mock.ansatt.dev.nav.no/ekspertbistand`
- env `EKSPERTBISTAND_API_BASEURL=http://ekspertbistand-backend-mock` og
  `LOKAL_INNLOGGING=true`
- `accessPolicy.outbound`: `ekspertbistand-backend-mock`
- `BASE_PATH`, `tokenx`/`idporten` av og ressurser som i `ekspertbistand-frontend-mock`

`nais/dev-gcp-frontend-mock.yaml` er uendret og kjører med MSW.
`nais/dev-gcp-backend-mock.yaml` får inbound fra `ekspertbistand-frontend-e2e-mock`.

### 7. Workflow og image

`.github/workflows/deploy-dev-mock.yml` bygger og deployer også
`ekspertbistand-frontend-e2e-mock`:

- Ny jobb `build-frontend-e2e-mock`: som jobben i
  `deploy-dev-manual-frontend-mock.yml`, med CDN-opplasting til
  `fager/ekspertbistand-e2e-mock/<sha>`, men med `VITE_LOKAL_INNLOGGING=true` og
  uten `VITE_ENABLE_MOCKS`. Egen CDN-sti, så den ikke overskriver MSW-bygget
  for samme commit.
- Jobb `deploy`: deployer backend-mock først, deretter begge frontend-mockene.
- Trigger: dagens stier, pluss `frontend/client/**`, `frontend/server/**`,
  `frontend/shared/**`, `Dockerfile` og `nais/dev-gcp-frontend-e2e-mock.yaml`.
- `deploy-dev-manual-frontend-mock.yml` beholdes og deployer MSW-mocken.

Rot-`Dockerfile` tar imot `VITE_LOKAL_INNLOGGING` som `ARG`/`ENV` i byggesteget.

### 8. Lokal kjøring

`frontend/README.md` beskriver to måter å kjøre arbeidsgiverfrontenden mot
`LocalApplication`:

1. Bygget klient servert av BFF-en (som i dag). README-en bruker i dag
   `EKSPERTBISTAND_API_BASE_URL`, men BFF-en leser `EKSPERTBISTAND_API_BASEURL`.
   Det rettes.
2. Med hot reload: BFF-en på port 4000 og
   `VITE_ENABLE_MOCKS=false pnpm --filter client dev`.

README-en beskriver også testflyten: søk som arbeidsgiver, behandle i
saksbehandling, send refusjonskrav og sluttrapport som arbeidsgiver.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt` | Registrerer vedlegg-, refusjon- og kontoregister-API; mock av `ClamAvClient` og `KontoregisterClient`; Altinn-mock fra `lokaleVirksomheter` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/LokaleSaker.kt` | To nye saker med innvilget vedtak; virksomheten uten kontonummer; avlyst søknad og tilskuddsbrev via `SoknadAvlystIArena` og `TilskuddsbrevMottatt` |
| `frontend/server/src/index.ts` | Nytt flagg `LOKAL_INNLOGGING` ved siden av unntaket for `VITE_ENABLE_MOCKS` |
| `frontend/client/src/utils/constants.ts` | `isMockEnabled()` respekterer `VITE_ENABLE_MOCKS=false`; ny `isLokalInnlogging()` |
| `frontend/client/vite.config.ts` | Proxy for `/oauth2` |
| `frontend/saksbehandling/src/utils/constants.ts` | Ny `arbeidsgiverSoknadUrl(soknadId)` |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Knappen «Se som arbeidsgiver» når `isLokalBrukerbytteMulig()` |
| `Dockerfile` | Build-arg `VITE_LOKAL_INNLOGGING`; `VITE_ENABLE_MOCKS` også i runner-steget |
| `nais/dev-gcp-frontend-e2e-mock.yaml` | Ny app som peker mot backend-mock, `LOKAL_INNLOGGING=true` |
| `nais/dev-gcp-backend-mock.yaml` | Inbound fra `ekspertbistand-frontend-e2e-mock` |
| `.github/workflows/deploy-dev-mock.yml` | Bygger og deployer også `ekspertbistand-frontend-e2e-mock` |
| `frontend/README.md`, `backend/README.md` | Lokal kjøring, mock-miljøet og testflyten |

## Kanttilfeller og feilmodus

- **Felles data.** Arbeidsgiver- og saksbehandling-mocken deler databasen. En
  søknad sendt i arbeidsgiver-mocken dukker opp i saksbehandling-mocken. Alle
  testere deler pid `42`.
- **Testere samtidig.** Alle som bruker e2e-mocken deler de samme dataene.
  Tilstandene i tabellen i punkt 2 er tilbake etter hver omstart av
  backend-mocken. NKS bruker MSW-mocken og påvirkes ikke.
- **Testdata brukt opp.** Et refusjonskrav eller en sluttrapport kan sendes én
  gang per søknad. Når de tre sakene er brukt, må testeren behandle en ny søknad
  i saksbehandling-mocken, eller vente på neste omstart.
- **Pod-restart i backend-mocken.** Databasen nullstilles. Innsendte søknader,
  refusjonskrav og sluttrapporter forsvinner.
- **Backend-mocken utilgjengelig.** BFF-en svarer 502 på API-kall, og klienten
  viser feilsiden. MSW slår ikke inn.
- **Søknader med kode 6, kode 7 og egne ansatte.** Arbeidsgiveren ser nå også
  disse lokale søknadene. Det er riktig: arbeidsgiveren sendte dem, og
  beskyttelsen gjelder saksbehandling.
- **Opplastede filer.** Backend validerer at filen er en PDF. ClamAV-mocken
  svarer alltid `OK`, så virusfunn kan ikke testes i mocken.
- **Feilkonfigurasjon i ekte app.** `LOKAL_INNLOGGING` sammen med
  `TOKEN_X_ISSUER` stopper BFF-en ved oppstart. Backend-mocken har ingen ingress
  og bare inbound fra de to mock-BFF-ene.
- **Lenker fra mocken.** `MIN_SIDE_URL` peker til `arbeidsgiver-dev-like`, som
  i dag.
- **«Se som arbeidsgiver» lokalt uten arbeidsgiver-BFF.** Kjører ikke BFF-en på
  port 4000, får fanen tilkoblingsfeil. README-en beskriver hvordan den
  startes. Kjøres arbeidsgiverklienten med `vite dev` på 5173, går lenken
  likevel til 4000, som serverer det bygde klientbundlet.
- **«Se som arbeidsgiver» for Arena-saker.** Søknader fra Arena finnes ikke i
  arbeidsgiverfrontenden. Knappen skjules når `kildeTilBehandling` er `ARENA`.
  Det skjer ikke i dag: `SakProjection`, som backfiller Arena-saker, kjører bare
  i prod.

## Utenfor scope

- Brukerbytte for arbeidsgivere (flere pid-er eller Altinn-profiler)
- Leasing av testdata
- Virusfunn i ClamAV-mocken
- Visning av refusjonskrav og sluttrapport i saksbehandling
- Innlogging foran mocken

## Ferdig når

- [ ] Lokalt: med `LocalApplication`, BFF-en og klienten (bygget eller
      `vite dev` med `VITE_ENABLE_MOCKS=false`) kan pid `42` sende en søknad, se
      den behandlet i saksbehandling, og sende refusjonskrav og sluttrapport
- [ ] Lokalt: en sak med fattet vedtak er klar til refusjon og sluttrapport ved
      oppstart
- [ ] `kontonummer-finnes` svarer både `true` og `false` for lokale virksomheter
- [ ] Ved oppstart finnes søknader i alle tilstandene MSW-mocken viser: godkjent
      med og uten kontonummer, avslått, avlyst og godkjent med tilskuddsbrev
- [ ] `https://ekspertbistand-arbeidsgiver-e2e-mock.ansatt.dev.nav.no/ekspertbistand`
      går mot `ekspertbistand-backend-mock` uten MSW, og samme flyt virker der
- [ ] `https://arbeidsgiver-dev.ansatt.dev.nav.no/ekspertbistand` kjører med MSW
      som før
- [ ] MSW virker fortsatt i `vite dev` uten flagg og i e2e-testene
- [ ] Saksdetaljene viser «Se som arbeidsgiver» lokalt og i
      saksbehandling-mocken, ikke ellers. Lenken åpner søknaden i riktig
      arbeidsgiverfrontend
- [ ] BFF-en nekter å starte med både `LOKAL_INNLOGGING` og `TOKEN_X_ISSUER`
- [ ] Backend-mocken er bare nåbar fra de to mock-BFF-ene
- [ ] `deploy-dev-mock.yml` bygger og deployer `ekspertbistand-frontend-e2e-mock` ved push til
      `main` og ved `workflow_dispatch`
- [ ] README-ene beskriver lokal kjøring, mock-miljøet og testflyten
