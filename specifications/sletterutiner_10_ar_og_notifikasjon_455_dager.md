# Sletterutiner: 10 år i saksbehandlingsløsningen, 455 dager i notifikasjonsplattformen

Trello: _mangler_

## Mål

- Saksbehandlingsløsningen skal ta vare på alle data om en søknad og sak i
  **10 år etter siste oppdatering**. Det gjelder søknad, sak, vedtak, sakslogg,
  vedlegg, refusjonskrav, sluttrapport, event-logg og tilknyttede data om
  tilsagn og OeBS. Etter 10 år slettes alt.
- Notifikasjonsplattformen skal **hard-slette saken 455 dager etter siste
  oppdatering** av saken.
- Arbeidsgiver skal **miste tilgangen** til søknaden i søknadsløsningen
  455 dager etter siste oppdatering, selv om dataene ligger igjen hos oss.

## Bakgrunn

I dag styrer én konstant to ulike ting:

```kotlin
// backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Db.kt
val slettSøknadOm = 455.days
```

1. **Lokal sletting.** `soknad.slette_tidspunkt` settes til
   `now + slettSøknadOm`. Kolonnen flyttes fram når søknaden blir godkjent
   (`SettGodkjentSoknadStatus`), avlyst (`SettAvlystSoknadStatus`) eller får
   vedtak (`SettSoknadStatusVedtakFattet`). Den daglige jobben
   `slettGamleInnsendteSoknader()` (startes i `soknad/Routing.kt`) sletter
   søknaden når fristen er passert. Via `ON DELETE CASCADE` forsvinner også sak,
   saksvilkår, sakslogg, sak_retur, refusjonskrav, sluttrapport og vedlegg.
2. **Notifikasjonsplattformen.** `ProdusentApiKlient` sender
   `hardDelete = FutureTemporalInput(om = 455d)` i `nySak` og
   `HardDeleteUpdateInput(strategi = OVERSKRIV, om = 455d)` i `nyStatusSak`.
   Plattformen sletter dermed saken 455 dager etter siste statusoppdatering.

Vi har funnet disse svakhetene:

- Søknaden slettes etter 455 dager. Det er for tidlig for saksbehandlingen.
- `event_log`, `event_queue`, `event_handler_states`,
  `idempotency_guard_records`, `tilsagndata`, OeBS-tabellene og
  tilstandstabellene slettes aldri. Personopplysninger blir altså liggende i
  `event_json` etter at søknaden er slettet.
- `forelopig_vedtak.sak_id`, `vedtak.sak_id`, `vedtak.forelopig_vedtak_id` og
  `sak_retur.forelopig_vedtak_id` mangler `ON DELETE CASCADE`. Sletting av en
  søknad som har vedtak, feiler derfor i dag.
- Arbeidsgiverrutene (TokenX) filtrerer ikke på tid. Tilgangen forsvinner bare
  fordi raden slettes.
- Frontend (`frontend/client/src/pages/SoknadPage.tsx:154`) sier at tilgangen
  fjernes «12 måneder etter at tiltaket …». Det stemmer ikke med noen av fristene.

## Beslutninger

1. Arbeidsgiver mister tilgangen 455 dager etter siste oppdatering, samme frist
   som i notifikasjonsplattformen. Dataene ligger igjen i databasen.
2. Vi implementerer sletting etter 10 år nå, ikke bare stans av dagens sletting.
3. 10 år regnes fra siste oppdatering av søknaden eller saken.
4. Slettingen etter 10 år gjelder alt som kan knyttes til søknaden eller saken.

## Begreper

| Begrep | Betydning |
|---|---|
| Aggregat | Alt som hører til én `aggregate_root_id` i `event_log`. Det er søknad-id (UUID) for vanlige søknader og tilsagnsnummeret (`"2026:123:1"`) for tilsagn fra Altinn uten søknad. |
| Siste oppdatering (lokalt) | `max(event_log.created_at)` for aggregatet. Alle endringer på søknad og sak går gjennom events. |
| Siste oppdatering (notifikasjon) | Siste kall til `nySak` eller `nyStatusSak` for saken. |
| 10 år | 10 kalenderår (`now - 10 år` i UTC, med skuddår), ikke `3650.days`. |

## Endring

### A. Notifikasjonsplattformen: hard-delete etter 455 dager

- Lag en egen konstant i `notifikasjon`-pakken:

  ```kotlin
  val hardDeleteINotifikasjonsplattformenOm = 455.days
  ```

- `ProdusentApiKlient` slutter å importere `slettSøknadOm`.
- `opprettNySak`: `hardDelete = FutureTemporalInput(om = …)`. Oppførselen er uendret.
- `nyStatusSak`: `HardDeleteUpdateInput(strategi = OVERSKRIV, om = …)`. Oppførselen
  er uendret: hver statusoppdatering flytter fristen.
- Beskjeder (`opprettNyBeskjed`) med samme grupperingsid slettes sammen med saken
  hos plattformen. De får ingen egen hardDelete.
- Vi kaller ikke `hardDeleteSak` selv. Plattformen eier slettingen.
  `hardDeleteSak` beholdes som før.

### B. Arbeidsgivers tilgang: 455 dager

- Migrering (expand/contract): legg til `soknad.arbeidsgiver_tilgang_til`, kopier
  verdien fra `slette_tidspunkt` og sett standardverdien til
  `now + 455 dager`. `slette_tidspunkt` blir stående til neste release, så gamle
  pods fungerer under utrullingen. Standardverdien settes til `9999-12-31`, så
  en rollback til gammel kode ikke sletter nye søknader. Kolonnen fjernes i en
  senere migrering.
- Erstatt `slettSøknadOm` med `arbeidsgiverTilgangVarighet = 455.days` i
  `soknad/Db.kt`. `SoknadTable` bruker den nye kolonnen,
  `arbeidsgiverTilgangTil`.
- Fristen settes ved innsending og flyttes fram på de samme stedene som i dag:
  `SettGodkjentSoknadStatus`, `SettAvlystSoknadStatus` og
  `SettSoknadStatusVedtakFattet`.
- Alle TokenX-ruter som leser eller endrer en innsendt søknad, filtrerer på
  `arbeidsgiver_tilgang_til > now()`. Utløpt søknad oppfører seg som om den ikke
  finnes (404, eller den er utelatt fra listen).

  | Rute | Fil |
  |---|---|
  | `GET /api/soknad/v1?status=…` | `soknad/Routing.kt`, `soknad/Api.kt` |
  | `GET`, `PATCH`, `DELETE`, `PUT /api/soknad/v1/{id}` | `soknad/Routing.kt`, `soknad/Api.kt` |
  | `POST`, `GET /api/soknad/v1/{id}/refusjon`, `GET …/refusjon/vedlegg/{vedleggId}` | `refusjon/RefusjonRouting.kt` |
  | `POST`, `GET /api/soknad/v1/{id}/sluttrapport` | `vedlegg/VedleggRouting.kt` |
  | `GET /api/tilsagndata/v1/soknad/{id}/tilskuddsbrev-html` og `-pdf` | `tilsagndata/Routing.kt` |
  | `GET /api/tilsagndata/v1/tilskuddsbrev/{tilsagnNummer}/tilskuddsbrev-html` og `-pdf` | `tilsagndata/Routing.kt` |

- Tilskuddsbrev som slås opp på tilsagnsnummer: hvis tilsagnet har en
  `soknad_id`, gjelder søknadens frist. Tilsagn fra Altinn uten søknad får sin
  egen kolonne, `tilsagndata.arbeidsgiver_tilgang_til`, som settes til
  `now + 455 dager` ved innsetting. Eksisterende rader fylles ut med
  `max(event_log.created_at) + 455 dager` for tilsagnsnummeret, og ellers med
  `CURRENT_TIMESTAMP + 455 dager`.
- Utkast (`utkast`-tabellen) berøres ikke. 48-timersregelen gjelder fortsatt.
- Saksbehandler-API-et (Azure AD) filtrerer ikke på `arbeidsgiver_tilgang_til`.

### C. Saksbehandlingsløsningen: sletting etter 10 år

`slettGamleInnsendteSoknader()` fjernes. En ny klasse,
`AggregateRetention.slettGamleAggregater(clock, batchSize)`, kjøres daglig fra
`soknad/Routing.kt`.

**Kandidater.** Et aggregat kan slettes når alt dette er sant:

1. `max(event_log.created_at) < now - 10 år`.
2. Alle event-id-er for aggregatet er `≤ min(projection_builder_state.position)`.
   Dermed har alle projeksjoner lest eventene før vi sletter dem. Hvis tabellen
   er tom, slettes ingenting.
3. Aggregatet har ingen rader i `event_queue`.
4. Aggregatet har ingen uferdige rader i `oebs_outbox`.

**Gjennomføring.** Én transaksjon per aggregat, i batcher (standard 100):

1. Sjekk kandidatkriteriene på nytt i transaksjonen.
2. Slett i denne rekkefølgen:
   1. OeBS: `oebs_outbox`, `oebs_sendt_melding`, `oebs_mottatt_status`,
      `oebs_bestilling_status` (bestillingsnummer `E-<sakId>-*`),
      `oebs_lopenummer` og `oebs_faktura_lopenummer`.
   2. `tilskuddsbrev_vist_state`, `tilsagndata`,
      `soknad_behandlet_forsinkelse_state` og `fagsak_id_mapping`.
   3. `soknad`. Cascade tar sak, saksvilkår, sakslogg, sak_retur,
      forelopig_vedtak, vedtak, refusjonskrav, sluttrapport og vedlegg.
   4. `event_handler_states` og `idempotency_guard_records` for aggregatets
      event-id-er.
   5. `event_log`.
3. Tell opp i metrikkene `aggregat_slettet_total`,
   `aggregat_sletting_utsatt_total` og `aggregat_sletting_feil_total`. Logg bare
   antall og aggregat-id, ingen personopplysninger.

**Kobling til OeBS.** `opprettBestilling(sakId)` har ingen kallere ennå.
Spec-en forutsetter at `sakId` blir `sak.arena_sak_id`. Hvis det endres, må
slettejobben endres også.

**Fremmednøkler.** Migreringen legger `ON DELETE CASCADE` på
`forelopig_vedtak_sak_id_fkey`, `vedtak_sak_id_fkey`,
`vedtak_forelopig_vedtak_id_fkey` og `sak_retur_forelopig_vedtak_id_fkey`.

**Indekser.** `event_log(aggregate_root_id, created_at)` og
`event_queue(aggregate_root_id)`.

### D. Migrering

Én ny Flyway-migrering, `V26__oppbevaring_og_sletterutiner.sql`, med
`SET lock_timeout = '3s'`:

1. `soknad.arbeidsgiver_tilgang_til`: legg til, kopier fra `slette_tidspunkt`,
   sett `NOT NULL` med standardverdi. Sett standardverdien på `slette_tidspunkt`
   til `9999-12-31`.
2. `tilsagndata.arbeidsgiver_tilgang_til`: legg til, fyll ut og sett `NOT NULL`
   med standardverdi.
3. Gjenopprett de fire fremmednøklene med `ON DELETE CASCADE`.
4. Indekser på `event_log` og `event_queue`. Tabellene er små nok til vanlig
   `CREATE INDEX`. Hvis de har vokst, flytt indeksene til en egen migrering med
   `CONCURRENTLY`.

## Filer som berøres

- `../backend/src/main/resources/db/migration/V26__oppbevaring_og_sletterutiner.sql` (ny)
- `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Db.kt`, `Api.kt`, `Routing.kt`
- `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/AggregateRetention.kt` (ny)
- `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/SettGodkjentSoknadStatus.kt`,
  `SettAvlystSoknadStatus.kt`, `SettSoknadStatusVedtakFattet.kt`
- `backend/src/main/kotlin/no/nav/ekspertbistand/notifikasjon/ProdusentApiKlient.kt`
- `backend/src/main/kotlin/no/nav/ekspertbistand/refusjon/`, `vedlegg/`,
  `tilsagndata/` (tilgangsfilter)
- Tester under `backend/src/test/kotlin/no/nav/ekspertbistand/`

Frontend-teksten endres ikke før innholdet er godkjent (se åpne punkter).

## Kanttilfeller og vurderinger

- **Data som allerede er slettet.** Søknader som er slettet etter 455 dager,
  kan ikke hentes tilbake.
- **Utrulling.** Første deploy stopper dagens sletting etter 455 dager.
  Slettingen etter 10 år slår ikke til på mange år. Vi tester den med en
  injisert `Clock`.
- **Projeksjoner som står fast.** Hvis en projeksjon ikke flytter posisjonen sin,
  slettes ingenting. Det er tilsiktet: vi sletter heller for sent enn at en
  projeksjon mister events.
- **Sene events.** Vi sperrer ikke for nye events på et slettet aggregat. Det
  krever at noe publiserer et event på et aggregat som har stått urørt i
  10 år, og det er så lite sannsynlig at vi ikke bygger egen beskyttelse mot
  det.
- **Notifikasjonsplattformen og lokale oppdateringer.** Sakslogg-hendelser som ikke
  sender `nyStatusSak`, flytter ikke fristen hos plattformen. Arbeidsgiver kan
  derfor miste saken i plattformen før lokale data er utløpt. Det er akseptert.
- **Andre systemer.** Joark/dokarkiv og Arena har egne regler for oppbevaring.
  De er utenfor denne endringen.
- **Arena-tabellene.** `arena_sak`, `arena_sak_under_behandling` og
  `arena_melding_idempotency` slettes ikke av jobben. Vi skal være ute av Arena
  lenge før de første aggregatene når 10 år, og tabellene ryddes da for seg.

## Tester

- `ProdusentApiKlientTest`: `hardDelete` er 455 dager i både `nySak` og `nyStatusSak`.
- Arbeidsgiver-API: søknad med `arbeidsgiver_tilgang_til` i fortiden vises ikke
  i listen. Hent, oppdater, refusjon, sluttrapport og tilskuddsbrev gir 404.
- Handler-testene for godkjent, avlyst og vedtak fattet bruker ny kolonne og
  konstant.
- `AggregateRetention`:
  - sletter alt for et aggregat der siste event er eldre enn 10 år, også når
    saken har foreløpig vedtak og vedtak
  - rører ikke et aggregat med nyere event
  - rører ikke et aggregat med rader i `event_queue` eller uferdig OeBS-outbox
  - respekterer projeksjonsposisjonen
  - regner 10 kalenderår riktig over skuddår
  - avviser nye events for et slettet aggregat

## Åpne punkter

- Jurist bekrefter hjemmel for 10 års oppbevaring og for sletting av OeBS-data.
- Ny frontend-tekst om arbeidsgivers tilgang (jurist og innholdsdesigner).
  Forslag: «… fjerner vi tilgangen din til søknaden 15 måneder etter siste
  oppdatering.»
- Personvernerklæring og PVK/behandlingsgrunnlag oppdateres av teamet.
- Trello-kort og lenke.

## Ferdig når

- Ingen søknad slettes lokalt før 10 år etter siste oppdatering.
- Arbeidsgiver ser ikke søknader eller tilskuddsbrev etter 455 dager.
- Notifikasjonsplattformen får `hardDelete` på 455 dager i alle kall.
- Etter 10 år finnes ingen rader for aggregatet i noen tabell.
- Testene over er grønne.
