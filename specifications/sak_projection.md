# Plan: SakProjection – populer `sak`-tabellen fra event-loggen

## Problem
Tabellen `sak` (V12, spesifisert i `specifications/sak-datamodell.md`) finnes, men ingenting skriver til den.
Vi lager en `EventLogProjectionBuilder` som bygger `sak` fra event-loggen for søknader som behandles i Arena.
Projeksjonen er kun for backfilling. Nye saker opprettes av handleren `OpprettSak` (på
`InnsendtSoknadJournalfoert`, se `specifications/opprett_sak.md`), som setter behandlende enhet med en gang. Projeksjonen oppretter derfor aldri
en sak for en søknad som allerede har en. Den beholdes for eldre søknader og for status-overganger, og skal
på sikt erstattes. Projeksjonen kan ha opprettet saken fra `SoknadInnsendt` før `OpprettSak` kjører, så
`OpprettSak` setter behandlende enhet også på en eksisterende sak uten enhet. Begge mapper Arena-nummeret i
`InnsendtSoknadJournalfoert` tilbake til Norg-nummeret, så de skriver samme enhet.

## Avklarte beslutninger
| Tema | Valg |
|------|------|
| Statusmapping | `SoknadInnsendt` → `OPPRETTET`, `SaksbehandlingStartetIArena` → `UNDER_BEHANDLING`, `TilskuddsbrevMottatt` → `INNVILGET`, `SoknadAvlystIArena` → `AVSLATT` (eksisterende `Saksstatus`) |
| Kilde | `kilde_til_behandling = ARENA` |
| Behandlende enhet | Fra `InnsendtSoknadJournalfoert.behandlendeEnhetId` |
| Arena-saksnummer | `arena_sak_id` fra `TiltaksgjennomforingOpprettet.saksnummer` |
| Rekkefølge | Terminalstatus (`INNVILGET`/`AVSLATT`) overskrives aldri; `UNDER_BEHANDLING` settes kun fra `OPPRETTET` |
| FK mot søknad | Ny migrering: `sak.soknad_id` → `ON DELETE CASCADE`. Projeksjonen hopper over events der søknaden ikke finnes lenger |
| Saksvilkår | Alle `Vilkar` opprettes som ikke vurdert når saken opprettes (fra `Sak-v3`, også for eksisterende saker) |
| Utenfor scope | `sakslogg`, beslutter, refusjon/sluttrapport. Tildeling av saksbehandler kom med [`tildel_meg_sak.md`](tildel_meg_sak.md) |

## Tilnærming

### 1. Migrering `V16__sak_soknad_fk_cascade.sql`
```sql
ALTER TABLE sak DROP CONSTRAINT sak_soknad_id_fkey;
ALTER TABLE sak ADD CONSTRAINT sak_soknad_id_fkey
    FOREIGN KEY (soknad_id) REFERENCES soknad(id) ON DELETE CASCADE;
```
(Verifiser constraint-navnet – V12 bruker inline `REFERENCES`, så Postgres-default er `sak_soknad_id_fkey`.)
Uten dette vil `slettGamleInnsendteSoknader` feile med FK-brudd når det finnes en sak.

### 2. Domenemodell – ny pakke `no.nav.ekspertbistand.sak` (`Db.kt`)
- `object SakTable : Table("sak")` med alle kolonnene fra V12. Fremmednøkler og CHECK-constraints
  håndheves av databasen.
- `enum class Saksstatus { OPPRETTET, UNDER_BEHANDLING, TIL_BESLUTNING, INNVILGET, AVSLATT, AVSLUTTET }`
- `enum class KildeTilBehandling { EKSPERTBISTAND, ARENA }`
- Lagres som TEXT (spesifikasjonen: "TEXT i DB, håndhevet i Kotlin").

### 3. `event/projections/SakProjection.kt`
`class SakProjection(database: Database) : EventLogProjectionBuilder(database)`, `name = "Sak-v3"`.

| Event | Handling |
|-------|----------|
| `SoknadInnsendt` | `insertIgnore` sak (`soknad_id`, `status=OPPRETTET`, `kilde=ARENA`, `opprettet=sist_endret=eventTimestamp`) – **kun hvis søknaden finnes** i `soknad` og **det ikke allerede finnes en sak** for søknaden (ellers logg info og hopp over) |
| `InnsendtSoknadJournalfoert` | `behandlende_enhet = BehandlendeEnhetService.arenaTilNorgEnhetNr(behandlendeEnhetId)`. Eventen inneholder enhetsnummeret som ble sendt til Arena (f.eks. 1891 → 1899), så mappingen reverseres |
| `TiltaksgjennomforingOpprettet` | `arena_sak_id = saksnummer` |
| `SaksbehandlingStartetIArena` | `status = UNDER_BEHANDLING` **WHERE status = OPPRETTET** |
| `TilskuddsbrevMottatt` | `status = INNVILGET` **WHERE status NOT IN (INNVILGET, AVSLATT)** |
| `SoknadAvlystIArena` | `status = AVSLATT` **WHERE status NOT IN (INNVILGET, AVSLATT)** |
| `SakTildeltSaksbehandler` | `tildelSak`: `saksbehandler_ident`, `saksbehandler_navn`, `tildeling_event_id = event.id`, og `status = UNDER_BEHANDLING` når status er `OPPRETTET` – **WHERE `tildeling_event_id` IS NULL OR < event.id**. Alle saker uansett kilde. Se [`tildel_meg_sak.md`](tildel_meg_sak.md) |
| `SakFrigjort` | `frigjoerSak`: nuller `saksbehandler_ident` og `saksbehandler_navn`, `tildeling_event_id = event.id` – **WHERE `saksbehandler_ident` = eventens ident AND (`tildeling_event_id` IS NULL OR < event.id)**. Status endres ikke |
| øvrige | ignoreres (inkl. `*KildeAltinn` – de har ingen søknad i vårt system) |

- Alle oppdateringer: `WHERE soknad_id = … AND kilde_til_behandling = 'ARENA'`, og setter `sist_endret = eventTimestamp`.
- Oppdateringer mot en sak som ikke finnes (søknad slettet) treffer 0 rader – ingen feil.
- Idempotent: eksplisitt sjekk på om saken finnes, pluss `insertIgnore` på UNIQUE `soknad_id` som vern mot
  samtidig opprettelse; oppdateringene gir samme resultat ved gjentakelse.
- Replay fra posisjon 0 fyller saker for all historikk ved første oppstart.
- **Re-kjøring:** bump versjonen i `name` (`Sak` → `Sak-v2` → …). Ny builder-rad i `projection_builder_state`
  starter på posisjon 0. Vi nullstiller ikke posisjonen med en migrasjon, fordi en gammel pod under rolling deploy
  da kan starte replay med gammel logikk. Den gamle raden (`Sak`) blir liggende og kan slettes manuelt.

### 4. Registrering
Legg `dependencies.create(SakProjection::class)` til i `configureProjectionBuilders` (`EventLogProjections.kt`).

### 5. Tester – `SakProjectionTest` (mønster fra `SoknadBehandletForsinkelseProjectionTest`)
- SoknadInnsendt oppretter sak med OPPRETTET/ARENA
- InnsendtSoknadJournalfoert setter behandlende_enhet; TiltaksgjennomforingOpprettet setter arena_sak_id
- Hele løpet → UNDER_BEHANDLING → INNVILGET; og → AVSLATT
- Rekkefølge: SaksbehandlingStartet etter INNVILGET endrer ikke status; AVSLATT etter INNVILGET endrer ikke
- Søknad som ikke finnes i `soknad` → ingen sak, projeksjonen går videre (ikke fast)
- Idempotens: samme event to ganger gir én sak
- FK cascade: sletting av søknad sletter saken
- Behandlende enhet mappes tilbake fra Arena (1899) til Norg (1891)
- Re-kjøring retter behandlende enhet på eksisterende sak uten å endre terminalstatus

### 6. Dokumentasjon
- Kjør `executables/EventFlowDiagram.kt` for å oppdatere `event/event-flow.md` (ny projeksjon).
- Nevn `SakProjection` i `event/projections/README.md`.

## Merknader
- `opprettet`/`sist_endret` settes fra eventens tidspunkt (ikke `now()`), slik at replay gir riktige tidspunkter.
- Ved deploy vil projeksjonen replaye hele event-loggen i batcher à 10 hvert poll – følg `lag`-metrikken per builder.
