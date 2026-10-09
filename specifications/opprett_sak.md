# Plan: OpprettSak – opprett sak fra event-flyten

## Problem
Saker i `sak`-tabellen opprettes i dag bare av `SakProjection` (se `specifications/sak_projection.md`).
Projeksjonen leser event-loggen i etterkant og setter behandlende enhet først når den leser
`InnsendtSoknadJournalfoert`. Vi vil at saken opprettes direkte i event-flyten, med behandlende enhet satt
fra start, slik at `SakProjection` på sikt kan fjernes.

## Avklarte beslutninger
| Tema | Valg |
|------|------|
| Trigger | Handleren `OpprettSak` lytter på `InnsendtSoknadJournalfoert`, på samme måte som `OpprettTiltaksgjennomfoeringForInnsendtSoknad`. |
| Behandlende enhet | Utledes før journalføring av `BehandlendeEnhetUtleder` (PDL, Ereg, Norg) i `JournalfoerInnsendtSoknad`. Eventen bærer Arena-enhetsnummeret, som `OpprettSak` mapper tilbake til Norg-nummer med `BehandlendeEnhetService.arenaTilNorgEnhetNr`. |
| Enhetsnummer på saken | `sak.behandlende_enhet` er alltid Norg-nummer. Arena bruker et annet nummer for noen enheter (Nordland: Norg `1891`, Arena `1899`). |
| Event for opprettet sak | Ingen egen `SakOpprettet`-event. En event uten handler gir `TransientError` i `EventManager`, og ingen trenger den ennå. |
| Sakslogg | `OpprettSak` publiserer `SakOppdatert(SYSTEM, null, "Sak opprettet")`, som `SkrivSakslogg` skriver til `sakslogg`. |
| `SakProjection` | Beholdes som backfill for eldre søknader og for statusoverganger. Ingen versjonsbump. |
| Søknad finnes ikke | `UnrecoverableError`. Det skal ikke skje i normal flyt, så det skal synes. |

## Event-flyt
```
SoknadInnsendt
  └─ JournalfoerInnsendtSoknad (utleder enhet, journalfører)
       └─ InnsendtSoknadJournalfoert (behandlendeEnhetId = Arena-nummer)
            ├─ OpprettSak → sak + vilkår, SakOppdatert → SkrivSakslogg
            └─ OpprettTiltaksgjennomfoeringForInnsendtSoknad → Arena
```

## Tilnærming

### 1. `norg/BehandlendeEnhetUtleder.kt`
- PDL-, Ereg- og Norg-logikken er flyttet hit fra `JournalfoerInnsendtSoknad`.
- `suspend fun utled(soknad: DTO.Soknad): String` returnerer Norg-enhetsnummer:
  - Strengt fortrolig adresse: geografisk tilknytning fra PDL, ellers `NAV_VIKAFOSSEN`.
  - Ellers, og når personen ikke finnes i PDL: kommunenummer fra virksomhetens forretningsadresse i Ereg.
- Kaster `ManglerDataForBehandlendeEnhetException` når virksomheten mangler kommunenummer.

### 2. `norg/BehandlendeEnhetService.kt`
- `hentBehandlendeEnhet` returnerer Norg-nummer, og mapper ikke lenger til Arena.
- `norgTilArenaEnhetNr` og `arenaTilNorgEnhetNr` gjør mappingen begge veier.

### 3. `event/handlers/JournalfoerInnsendtSoknad.kt`
- Lytter på `SoknadInnsendt` og utleder enheten med `BehandlendeEnhetUtleder`. Feil gir `TransientError`.
- Publiserer `InnsendtSoknadJournalfoert` med `behandlendeEnhetId = norgTilArenaEnhetNr(enhet)`.

### 4. `event/handlers/OpprettSak.kt`
- `EventHandler<InnsendtSoknadJournalfoert>`, id `"Opprett sak"`, med bare `Database` som avhengighet.
- Flyt:
  1. Idempotency guard (subtask `sak_opprettet`). Hvis eventen allerede er håndtert, returneres success.
  2. Ugyldig `soknad.id` gir `UnrecoverableError`.
  3. Enheten mappes fra Arena- til Norg-nummer.
  4. Én transaksjon gjør tre ting: kaller `opprettSakForSoknad`, publiserer `SakOppdatert` og setter guarden.
- `opprettSakForSoknad` (privat):
  - Returnerer null hvis søknaden ikke finnes.
  - `insertIgnore` på `sak` (`OPPRETTET`, kilde `ARENA`, enhet, `opprettet = sist_endret = nå`).
  - Setter enheten på en eksisterende sak bare hvis den mangler. Projeksjonen kan ha opprettet saken fra
    `SoknadInnsendt` uten enhet.
  - Oppretter alle vilkår med `opprettVilkarForSak`.
- `companion object { fun opprettVilkarForSak(sakId) }` legger inn én rad per `Vilkar` som ikke vurdert
  (`batchInsert` med `ignore = true`). `SakProjection` bruker den også.

### 5. Registrering
- `configureEventHandlers`: `OpprettSak` registreres etter `JournalfoerInnsendtSoknad`.
- DI: `provide(BehandlendeEnhetUtleder::class)` i `Application.kt` og `LocalApplication.kt`.

### 6. Tester
- `OpprettSakTest`:
  - Oppretter sak med enhet, status, kilde og vilkår, og publiserer én `SakOppdatert`.
  - Mapper Arena-nummer til Norg-nummer (`1899` → `1891`).
  - En eksisterende sak uten enhet får enhet.
  - Retry gir ikke ny sak eller nye events.
  - En søknad som ikke finnes gir `UnrecoverableError`.
- `JournalfoerInnsendtSoknadTest`: PDL-, Ereg- og Norg-scenarioene, pluss at Norg-nummer mappes til
  Arena-nummer i eventen.
- `BehandlendeEnhetTest`: `hentBehandlendeEnhet` returnerer Norg-nummer, og mappingen fungerer begge veier.

## Idempotens og samtidighet
- `sak.soknad_id` er UNIQUE, så `insertIgnore` gir maks én sak per søknad, også når `SakProjection` og
  `OpprettSak` kjører samtidig.
- Begge skriver samme enhet, fordi begge mapper Arena-nummeret fra samme event tilbake til Norg-nummer.
- `opprettVilkarForSak` er idempotent.

## Utrulling
- Søknader som er journalført før deploy, har allerede fått sak fra `SakProjection`. `OpprettSak` kjører
  bare på nye `InnsendtSoknadJournalfoert`-events, og kjøres ikke på historikk.
- Events i køen ved deploy går gjennom den nye flyten. Hvis journalføringen kjøres på nytt, er det trygt,
  fordi `DokArkivClient` behandler `409 Conflict` som suksess.

## Videre arbeid
- Når alle saker har behandlende enhet, og statusoverganger er flyttet til egne handlere, kan
  `SakProjection` fjernes.
- Enhetsnummeret i `InnsendtSoknadJournalfoert` gjelder bare saker med kilde `ARENA`. Saker som behandles i
  ekspertbistand, trenger egen håndtering når de kommer.
