# Adressebeskyttelse og skjerming ved opprettelse av sak

Trello: https://trello.com/c/6L5KVmvN/661-ta-vare-p%C3%A5-informasjon-om-deltakers-adressebeskyttelse-og-skjermet-ansatt-i-nav-n%C3%A5r-saken-opprettes-eller-s%C3%B8knaden-sendes-inn
(kort 661, kort-ID `6ad77a468d2f2d287cd57e47`)

Henger sammen med `ruting_egne_ansatte_og_kommune.md` (kort 608 og 610) og
`saksbehandlingsstatistikk.md`. Begge har hooks som venter på dette kortet.

## Mål

Når `OpprettSak` oppretter en sak, slår vi opp deltakerens adressebeskyttelse i
PDL og skjerming (egen ansatt) i `skjermede-personer-pip`. Svarene lagres på
saken og på `SakOpprettet`. Da kan vi:

1. Merke saken med «Egen ansatt», «Fortrolig adresse» eller «Strengt fortrolig
   adresse» for saksbehandlere med tilgang (K255.1), også for kode 7.
2. Holde behandlinger der deltakeren hadde adressebeskyttelse ved opprettelsen
   utenfor statistikken vi deler, også når PDL senere sier ugradert.

Kortet gjelder tilgangsstyring og statistikk. Rutingen til enhet endres ikke.

## Bakgrunn

### Hooks som venter på 661

| Hook | Fil | I dag |
|------|-----|-------|
| `ResultRow.skjermetVedOpprettelse()` | `saksbehandling/SakApi.kt` | `false` |
| `ResultRow.adressebeskyttelseVedOpprettelse()` | `saksbehandling/SakApi.kt` | `STRENGT_FORTROLIG` for saker på Vikafossen (`2103`), ellers `null` |
| `EventData.SakOpprettet.adressebeskyttetVedOpprettelse()` | `statistikk/SaksbehandlingsstatistikkProjection.kt` | `false` |

Listen, frontend og statistikkprojeksjonen bruker allerede hookene. Detaljsiden
bruker bare adressebeskyttelse. Kortet fyller hookene med lagrede verdier og
gir detaljsiden `skjermet`.

### Adressebeskyttelse i dag

`JournalfoerInnsendtSoknad` slår opp adressebeskyttelse i PDL via
`BehandlendeEnhetUtleder` for å rute saken. Graderingen brukes bare til
rutingen og lagres ikke. `InnsendtSoknadJournalfoert.virksomhetKommunenummer`
er null for kode 6, men skal ikke brukes til å utlede adressebeskyttelse (se
klassedokumentasjonen i `Events.kt`).

Statistikkprojeksjonen slår opp gjeldende gradering i PDL før hver rad
(`PdlAdressebeskyttelseOppslag`). Den regner alt annet enn `UGRADERT` som
adressebeskyttelse, også ukjente graderinger.

### Hvor skjerming kommer fra

NOM registrerer skjerming (egne ansatte), og `skjermede-personer-pip` (team
nom) svarer på oppslag. Tilgangsmaskinen bruker det samme registeret.

- `POST /skjermet` med `{ "personident": "…" }` svarer `true` eller `false`.
  [Swagger](https://skjermede-personer-pip.intern.nav.no/swagger-ui/index.html#/skjerming-pip/isSkjermetPost)
- Appen kjører i GCP, namespace `nom`. Vi kaller den på
  `http://skjermede-personer-pip.nom`, slik `TilgangsmaskinClient` kaller
  Tilgangsmaskinen.
- Team nom la `ekspertbistand-backend` (namespace `fager`) til i
  inbound-reglene 2026-10-23.

Auth, lest i kildekoden til `navikt/skjerming` 2026-10-23 (dokumentasjonen
sier lite):

- Nais-manifestene for dev-gcp og prod-gcp har `azure.application.enabled:
  true` og `allowAllUsers: true`. Inbound-reglene har `cluster` satt på hver
  regel. `populasjonstilgangskontroll` (Tilgangsmaskinen) står blant dem.
- I GCP krever appen gyldig token på alle kall (`fullyAuthenticated`).
  `JwtUtils` sjekker signatur, utløp, issuer
  (`https://login.microsoftonline.com/<tenant>/v2.0`) og audience (egen
  client-ID). Den sjekker ikke `NAVident`, `roles` eller `azp`. Et
  client credentials-token fra oss blir derfor godtatt, så lenge Azure AD
  utsteder det. Azure AD gjør det bare når vi står i inbound-reglene.
- `POST /skjermet` svarer `false` for en ident som ikke finnes i cachen
  deres. Mangler `personident`, svarer den 400.

### Hvor OpprettSak kjører

`OpprettSak` er bare registrert utenfor prod (`Events.kt`). I prod lager
`SakProjection` Arena-saker fra event-loggen, og de får ikke feltene.
`JournalfoerInnsendtSoknad` kjører i prod.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 661, Ken, 2026-10-21 | Statistikkprojeksjonen har en hook på `SakOpprettet` som returnerer `false` til feltet finnes. Projeksjonen tar vare på verdien gjennom hele behandlingen. Er den satt, skjermes hele behandlingen uten oppslag i PDL. Ellers avgjør PDL som i dag. |
| Kort 661, Ken, 2026-10-21 | Når feltet er på plass: la hooken returnere feltet, og legg til en test i `SaksbehandlingsstatistikkProjectionTest` der behandlingen skjermes når feltet er satt, også når PDL sier ugradert. |
| Kort 661, Ken, 2026-10-21 | Arena-saker får ikke statistikk og trenger ikke feltet. |
| Kort 661, Ken, 2026-10-21 | Hooken i statistikken heter nå `adressebeskyttetVedOpprettelse()`. Skjerming betyr egne ansatte. Statistikken gjelder adressebeskyttelse (kode 6 og 7). Erstatter ordbruken i kommentaren fra tidligere samme dag. |
| Kort 661, Ken, 2026-10-22 | Skjerming slås opp i `skjermede-personer-pip` på intern service-URL. Team nom må legge oss til i inbound-reglene. |
| Ruting-spec, teamet, 2026-10-22 | Oppslaget i `skjermede-personer-pip` lagres på `SakOpprettet`, på samme måte som adressebeskyttelse. Kortet sa først «`SakOpprettet` eller `SoknadInnsendt`». Denne beslutningen velger `SakOpprettet`. |
| Kildekoden til `navikt/skjerming`, 2026-10-23 | Appen godtar client credentials-token. Target er `<cluster>:nom:skjermede-personer-pip`. Inbound-regelen i deres manifest er det eneste som mangler. |
| Ken, 2026-10-23 | Team nom har lagt til inbound-regelen. Vi venter ikke lenger på dem, og trenger ingen fallback. |
| Ruting-spec, Ken, 2026-10-23 (K255.1) | Med tilgang skal beskyttelsen merkes tydelig. Kode 7 merkes først når 661 lagrer graderingen. |
| Ken, 2026-10-23 | Detaljsiden viser «Egen ansatt» på samme måte som «Strengt fortrolig adresse», i linja øverst med navn og fnr. |

## Avgrensning

- Arena-saker (`SakProjection`). De får ikke feltene, og listen bruker
  Vikafossen-signalet for dem som i dag.
- Å skru på `SaksbehandlingsstatistikkProjection` i prod. Kortet fjerner
  adressebeskyttelse som grunn til å vente, men `OpprettSak` kjører ikke i
  prod ennå.
- Skjerming i statistikken. Statistikken gjelder adressebeskyttelse, se
  beslutningene.
- Endring i rutingen. Egne ansatte går til vanlig arbeidslivssenter, se
  `ruting_egne_ansatte_og_kommune.md`.
- Å oppdatere flaggene når adressebeskyttelse eller skjerming endres etter
  opprettelsen. Tilgangsmaskinen og PDL-oppslaget i statistikken fanger
  endringer. Flaggene sier hva som gjaldt ved opprettelsen.

## Tilnærming

🔴 Rød sone: personvern og tilgangsstyring. Feil verdi kan vise kode 7 som
ugradert i statistikken eller skjule en merking saksbehandleren trenger.

### 1. Oppslag i OpprettSak

`OpprettSak` slår opp begge verdiene før transaksjonen, og bare når saken skal
opprettes (ikke ved idempotent retry som allerede er guardet):

- **Adressebeskyttelse.** `hentAdressebeskyttelse` i PDL, høyeste gradering.
  - `STRENGT_FORTROLIG` og `STRENGT_FORTROLIG_UTLAND` gir
    `Adressebeskyttelse.STRENGT_FORTROLIG`.
  - `FORTROLIG` gir `Adressebeskyttelse.FORTROLIG`.
  - `UGRADERT`, ingen gradering og `NotFound` gir `null`. Ifølge
    PDL-dokumentasjonen betyr `UGRADERT` det samme som tom liste.
  - `__UNKNOWN_VALUE` gir `null`, som i rutingen. Den genererte klienten
    legger til verdien i alle enumer, men PDL-skjemaet har bare de fire
    verdiene over. Med `null` stemmer flagget med enheten saken rutes til.
- **Skjerming.** `POST /skjermet` mot `skjermede-personer-pip`.

Feiler et oppslag, returnerer handleren `transientError`. Saken opprettes ikke
før begge oppslagene har lyktes. Det gir ingen sak uten flagg, og ingen sak med
gjettet verdi.

Valget og alternativet: Graderingen fra rutingen i `JournalfoerInnsendtSoknad`
kunne vært sendt videre på `InnsendtSoknadJournalfoert`. Det sparer ett
PDL-kall. Men skjermingsoppslaget måtte da også ligget i journalføringen, som
kjører i prod. Journalføringen i prod ville da stoppet på et oppslag vi ennå
ikke har tilgang til. I `OpprettSak` ligger begge oppslagene på ett sted, i en
handler som ikke kjører i prod. Prisen er ett ekstra PDL-kall per sak.

Graderingslogikken (`hoyesteGradering`) flyttes fra `BehandlendeEnhetUtleder`
til en felles funksjon i `pdl/`, slik at rutingen og flagget rangerer likt.

### 2. Klient mot skjermede-personer-pip

Ny `skjerming/SkjermingKlient.kt`:

- `fun interface SkjermingOppslag { suspend fun erSkjermet(fnr: String): Boolean }`
- `SkjermedePersonerPipKlient(azureAdTokenProvider, defaultHttpClient)`:
  machine-to-machine-token (client credentials) med target
  `${NaisEnvironment.clusterName}:nom:skjermede-personer-pip`, som
  `PdlApiKlient`. Det gir `dev-gcp:nom:skjermede-personer-pip` og
  `prod-gcp:nom:skjermede-personer-pip`. Ingress med `basedOnEnv` som i `TilgangsmaskinClient`:
  `http://skjermede-personer-pip.nom` i dev og prod, mock-host ellers.
- Fnr sendes i request-body, aldri i URL eller logg. Alt annet enn 200 med
  `true` eller `false` kastes som feil.
- Metrikker med `HttpClientMetricsFeature`, timeout 15 sekunder.

Adressebeskyttelsen får tilsvarende `fun interface` i `pdl/`, slik at
`OpprettSak` kan testes uten PDL. `Application.kt` registrerer begge.

### 3. Event

`EventData.SakOpprettet` får to felter med default:

```kotlin
val adressebeskyttelse: Adressebeskyttelse? = null,
val skjermet: Boolean = false,
```

Defaultene gjør at eldre `SakOpprettet` i dev deserialiseres som før. I prod
finnes ingen `SakOpprettet`, fordi `OpprettSak` ikke kjører der.

`Adressebeskyttelse` (`FORTROLIG`, `STRENGT_FORTROLIG`) finnes i
`saksbehandling/SakApi.kt` og gjenbrukes. `Events.kt` importerer allerede typer
fra `saksbehandling`.

### 4. Lagring på saken

Migrering `V28__sak_adressebeskyttelse_og_skjerming.sql`:

```sql
SET lock_timeout = '3s';

ALTER TABLE sak ADD COLUMN IF NOT EXISTS adressebeskyttelse TEXT;
ALTER TABLE sak ADD COLUMN IF NOT EXISTS skjermet BOOLEAN NOT NULL DEFAULT false;
```

`SakTable` får kolonnene. `opprettSakForSoknad` får parameterne og setter dem
i `insertIgnore` og i `update` når enheten mangler, på samme måte som
`kommunenummer`. `OpprettSak` lagrer saken og publiserer `SakOpprettet` med
samme verdier i samme transaksjon.

### 5. Hooks

- `SakApi.skjermetVedOpprettelse()` returnerer `row[SakTable.skjermet]`.
- `SakApi.adressebeskyttelseVedOpprettelse()` returnerer den lagrede
  graderingen. Er kolonnen null og saken ligger på Vikafossen, returnerer den
  `STRENGT_FORTROLIG` som i dag. Det dekker Arena-saker og saker opprettet før
  migreringen.
- `adressebeskyttetVedOpprettelse()` i statistikken returnerer
  `adressebeskyttelse != null`.
- TODO-ene som peker på 661 fjernes.
- Kommentaren i `EventLogProjections.kt` som sier at statistikken venter på
  adressebeskyttelse ved opprettelse, oppdateres: den venter nå bare på at
  `OpprettSak` kjører i prod.

### 6. «Egen ansatt» på detaljsiden

- `SakDetaljer` får `skjermet: Boolean` fra `row.skjermetVedOpprettelse()`.
  Detaljen trenger ingen maskering: `GET /sak/{id}` svarer 403 før den
  returnerer saken hvis Tilgangsmaskinen avviser.
- I frontend flyttes `skjermet` fra `SakListeElement` til `SakInfo`, som
  `adressebeskyttelse`.
- Ny komponent `components/EgenAnsattTag.tsx` med samme `size`-prop som
  `AdressebeskyttelseTag`. Den erstatter `egenAnsattTag` i
  `OversiktPage.tsx`, så listen ser lik ut som i dag.
- `SakPage.tsx` viser `EgenAnsattTag` med `size="small"` rett etter
  `AdressebeskyttelseTag` i linja med navn og fnr.
- Mocken trenger ingen endring. `lagSakDetaljer` sprer listeelementet, som
  har `skjermet` fra før.

### 7. Nais

`nais/dev-gcp-backend.yaml` og `nais/prod-gcp-backend.yaml` får outbound-regel:

```yaml
- application: skjermede-personer-pip
  namespace: nom
```

Team nom har lagt `ekspertbistand-backend` i `fager` til i inbound-reglene
sine.

### 8. Tester

- `OpprettSakTest`: stubbede oppslag.
  - Kode 7 gir `sak.adressebeskyttelse = FORTROLIG` og samme verdi på
    `SakOpprettet`.
  - Kode 6 i utlandet gir `STRENGT_FORTROLIG`.
  - Ugradert og `NotFound` gir `null`.
  - Skjermet gir `sak.skjermet = true` og `SakOpprettet.skjermet = true`.
  - Feil i PDL eller skjermingsoppslaget gir `transientError` og ingen sak
    eller event.
- `SaksbehandlingsstatistikkProjectionTest`: behandlingen holdes utenfor
  viewet når `SakOpprettet.adressebeskyttelse` er satt, også når PDL sier
  ugradert (kortet, 2026-10-21).
- `SaksbehandlingSakApiTest`: sak med `skjermet = true` og innvilget tilgang
  har `skjermet = true` i listen og på detaljen. Sak med lagret `FORTROLIG` har
  `adressebeskyttelse = FORTROLIG` i listen og på detaljen. Testen for
  `skjermet = true` hører til 661 ifølge ruting-spec-en.
- `SkjermedePersonerPipKlient`: `true`, `false` og feilstatus mot `MockEngine`.
- `TestEventData` og `EventDataAggregateRootIdTest`: `SakOpprettet` har nye
  felter med default, så eksemplene kan stå. Ett eksempel får satte verdier,
  slik at serialiseringen testes.

### 9. Dokumentasjon

- `statistikk/README.md`: adressebeskyttelse ved opprettelse kommer fra
  `SakOpprettet`.
- `event/event-flow.md` genereres på nytt med `EventFlowDiagram.kt` hvis
  handlerne endrer seg.
- `ruting_egne_ansatte_og_kommune.md`: punktene om hooks som venter på 661
  oppdateres.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/.../skjerming/SkjermingKlient.kt` (ny) | `SkjermingOppslag` og `SkjermedePersonerPipKlient` |
| `backend/.../pdl/` (ny fil eller `PdlApiKlient.kt`) | Felles høyeste gradering og oppslag for adressebeskyttelse |
| `backend/.../norg/BehandlendeEnhetUtleder.kt` | Bruker felles gradering |
| `backend/.../event/Events.kt` | `adressebeskyttelse` og `skjermet` på `SakOpprettet` |
| `backend/.../event/handlers/OpprettSak.kt` | Oppslag, lagring og felter på eventen |
| `backend/.../saksbehandling/Db.kt` | `SakTable.adressebeskyttelse`, `SakTable.skjermet` |
| `backend/.../saksbehandling/SakApi.kt` | Hooks leser kolonnene, `SakDetaljer.skjermet` |
| `backend/.../statistikk/SaksbehandlingsstatistikkProjection.kt` | Hooken leser eventen |
| `backend/.../statistikk/README.md` | Kilde for adressebeskyttelse ved opprettelse |
| `backend/.../event/projections/EventLogProjections.kt` | Kommentar |
| `backend/.../Application.kt` | Registrerer oppslagene |
| `backend/src/main/resources/db/migration/V28__sak_adressebeskyttelse_og_skjerming.sql` (ny) | Kolonner på `sak` |
| `nais/dev-gcp-backend.yaml`, `nais/prod-gcp-backend.yaml` | Outbound til `skjermede-personer-pip` |
| `backend/src/test/.../OpprettSakTest.kt` | Nye tester |
| `backend/src/test/.../SaksbehandlingsstatistikkProjectionTest.kt` | Ny test |
| `backend/src/test/.../SaksbehandlingSakApiTest.kt` | `skjermet` og kode 7 fra lagret verdi |
| `backend/src/test/.../TestEventData.kt` | Eksempel med satte felter |
| `specifications/ruting_egne_ansatte_og_kommune.md` | Hooks er koblet |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | `skjermet` flyttes til `SakInfo` |
| `frontend/saksbehandling/src/components/EgenAnsattTag.tsx` (ny) | Felles tag for egen ansatt |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | Bruker `EgenAnsattTag` |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Viser `EgenAnsattTag` øverst |

## Kanttilfeller og feilmodus

- **PDL eller pip er nede, eller tokenet avvises.** `OpprettSak` gir
  `transientError`, og saken opprettes når kallet lykkes. Søknaden er
  journalført i mellomtiden.
- **Personen finnes ikke i PDL.** `adressebeskyttelse = null`, som i rutingen.
- **Personen finnes ikke i skjermingsregisteret.** Pip svarer `false`, og vi
  lagrer `skjermet = false`.
- **Kode 6 uten geografisk tilknytning.** Rutes til Vikafossen og lagres som
  `STRENGT_FORTROLIG`. Flagg og enhet stemmer.
- **Graderingen endres mellom ruting og opprettelse.** Sekunder mellom
  oppslagene. Flagget kan avvike fra rutingen. Tilgangsmaskinen avgjør tilgang
  uansett.
- **Adressebeskyttelse eller skjerming endres etter opprettelsen.** Flaggene
  står. Tilgangsmaskinen fanger det i listen og på detaljen.
  `Adressebeskyttelseskontroll` fanger ny adressebeskyttelse i statistikken.
  En deltaker som mister adressebeskyttelsen, holdes fortsatt utenfor viewet.
  Det er bevisst.
- **Saken finnes fra før** (`insertIgnore`). Flaggene settes bare sammen med
  enheten når enheten mangler, som `kommunenummer`.
- **Personopplysninger.** Fnr sendes til PDL og pip i request-body. Vi logger
  ikke fnr, gradering eller skjerming. Loggen fra `OpprettSak` har bare sak-ID
  og søknad-ID som i dag.

## Ferdig når

- [ ] `OpprettSak` slår opp adressebeskyttelse i PDL og skjerming i `skjermede-personer-pip`, og gir `transientError` ved feil
- [ ] `sak.adressebeskyttelse` og `sak.skjermet` lagres for nye saker
- [ ] `SakOpprettet` har `adressebeskyttelse` og `skjermet` med samme verdier
- [ ] Listen viser «Egen ansatt» og «Fortrolig adresse» fra lagrede verdier for saksbehandlere med tilgang
- [ ] Detaljsiden viser «Egen ansatt» ved siden av adressebeskyttelsen øverst
- [ ] Statistikken holder behandlingen utenfor viewet når `SakOpprettet.adressebeskyttelse` er satt, også når PDL sier ugradert
- [ ] TODO-ene som peker på 661 er fjernet
- [ ] Outbound-regel i dev og prod
- [ ] `OpprettSak` henter skjerming fra pip i dev etter deploy
- [ ] Testene i 8 finnes og er grønne
- [ ] `mvn -B package` er grønn, og frontend bygger og passerer lint
