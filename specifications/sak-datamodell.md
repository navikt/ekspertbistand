# Datamodell: sak, saksvilkår, sakslogg, sluttrapport

Spesifikasjon for saksbehandlingsdomenet i ekspertbistand. Beskriver nye tabeller
(`sak`, `saksvilkar`, `sakslogg`, `sluttrapport`) og endringer i eksisterende tabeller
(`refusjonskrav`, `vedlegg`).

Målet er å skille **søknad** (det arbeidsgiver sender inn) fra **sak** (Navs behandling av
søknaden). Refusjon og sluttrapport flyttes fra søknad til sak, siden de hører til
behandlingen — ikke innsendingen.

## Beslutninger

| Tema | Valg | Begrunnelse |
|------|------|-------------|
| Kardinalitet sak ↔ søknad | **1:1** (`sak.soknad_id` UNIQUE) | Én sak per innvilget/behandlet søknad. |
| Refusjon | **1:1**, kun på sak (`sak.refusjon_id`) | Refusjon hører til behandlingen, ikke søknaden. `refusjonskrav.soknad_id` fjernes senere (expand/contract, se Migrasjon). |
| Sluttrapport | Ny tabell, **1:1** på sak (`sak.sluttrapport_id`) | Kan ha flere vedlegg; egen tabell samler metadata. |
| Saksvilkår | **1:N**, én rad per vilkår, PK `(sak_id, vilkar_id)` | Hvert vilkår har sin egen vurdering, sitt eget notat og sitt eget vurdert-av/tidspunkt. Nye vilkår krever ikke nye kolonner. |
| Sakslogg | **1:N** fra sak | Hendelseslogg / audit trail. |
| Enum-verdier | `TEXT` i DB, håndhevet i Kotlin | Nye verdier krever ingen DB-migrering (samme mønster som `soknad.status`). |
| Aktør i logg | `utfort_av_rolle` = `SAKSBEHANDLER`/`BESLUTTER`/`SYSTEM` | Saksbehandlere, besluttere og systemet skriver til loggen. `utfort_av_type` er fjernet i V17. |
| To-trinns kontroll | Egen `sak_retur`-tabell (1:N) + `foreslatt_utfall` på sak | Strukturert returårsak + full historikk. Habilitet gjelder vedtaket: beslutter kan ikke være den som sendte saken til `TIL_BESLUTNING`. |
| Omtildeling | Logges i `sakslogg` (ingen egen tabell) | Gjeldende saksbehandler ligger på `sak.saksbehandler_ident` og `sak.saksbehandler_navn`; historikk dekkes av loggen. Se [`tildel_meg_sak.md`](tildel_meg_sak.md). |

## Personvern

- Alle `*_ident`-kolonner inneholder **NAV-ident** (ansatt-ID), **aldri fødselsnummer**.
- Ansatt-fnr finnes kun i `soknad.ansatt_fnr` (uendret).
- Logg `sak_id` — aldri ident eller andre personopplysninger.

## Tabeller

### `sak`

Saksbehandlingsdomenet for en behandlet søknad.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sak_id` | UUID | PK, default `gen_random_uuid()` | Intern, teknisk identifikator for saken. |
| `soknad_id` | UUID | NOT NULL, UNIQUE, FK → `soknad(id)` | Søknaden saken behandler. UNIQUE håndhever én sak per søknad. |
| `status` | TEXT | NOT NULL, default `OPPRETTET` | Sakens tilstand i livsløpet. Se `Saksstatus`. |
| `kilde_til_behandling` | TEXT | NOT NULL | Hva som utløste behandlingen. Se `KildeTilBehandling`. Default ARENA |
| `behandlende_enhet` | TEXT | NULL | NAV-enheten som behandler saken — NORG-enhetsnummer (4 siffer, ledende nuller bevart). Null til enhet er satt. |
| `saksbehandler_ident` | TEXT | NULL | NAV-ident til saksbehandler som utreder. Null før tildeling og etter frigjøring. |
| `saksbehandler_navn` | TEXT | NULL | Navnet til saksbehandleren fra entra-proxy da saken ble tildelt. Satt når og bare når `saksbehandler_ident` er satt (CHECK `chk_saksbehandler_navn_med_ident`). |
| `beslutter_ident` | TEXT | NULL | NAV-ident til beslutter (to-trinns kontroll). Null før beslutning. |
| `foreslatt_utfall` | TEXT | NULL | Saksbehandlers foreløpige vedtak sendt til beslutning (`INNVILGET`/`AVSLATT`). Se `Saksstatus`. Null før innsending. |
| `arena_sak_id` | TEXT | NULL | Saksnummer i Arena når saken er speilet dit. Null hvis ikke i Arena. |
| `refusjon_id` | UUID | UNIQUE, FK → `refusjonskrav(id)` | Refusjonskravet for saken (1:1). Null til krav mottas. |
| `sluttrapport_id` | UUID | UNIQUE, FK → `sluttrapport(sluttrapport_id)` | Sluttrapporten for saken (1:1). Null til rapport mottas. |
| `tildeling_event_id` | BIGINT | NULL | Id-en til eventen som sist tildelte eller frigjorde saken. En eldre event skriver aldri over en nyere. Null før første tildeling. |
| `opprettet` | TIMESTAMPTZ | NOT NULL, default `now()` | Når saken ble opprettet. |
| `sist_endret` | TIMESTAMPTZ | NOT NULL, default `now()` | Sist endret. Oppdateres av applikasjonen ved skriv. |

Indekser: `idx_sak_status(status)`, `idx_sak_saksbehandler_ident(saksbehandler_ident)`,
`idx_sak_behandlende_enhet(behandlende_enhet)` (saker listes per enhet).

Constraint — en tildelt sak har alltid navn (V20):

```sql
ALTER TABLE sak ADD CONSTRAINT chk_saksbehandler_navn_med_ident
  CHECK ((saksbehandler_ident IS NULL) = (saksbehandler_navn IS NULL));
```

`chk_saksbehandler_ulik_beslutter` fantes fra V12, men er fjernet i V20. En beslutter kan tildele
seg saken. Habiliteten sjekkes ved vedtak, se `sakslogg` under.

### `saksvilkar`

Vilkårsvurderingen for en sak (1:N, én rad per vilkår). Alle vilkår opprettes som ikke vurdert
når saken opprettes (`opprettVilkarForSak`). Vurderingsfeltene er nullbare til vilkåret er vurdert.
Tabellen hadde én kolonne per vilkår i V12, og ble bygget om til én rad per vilkår i V19.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sak_id` | UUID | PK, FK → `sak(sak_id)` ON DELETE CASCADE | Saken vilkåret gjelder. Slettes med saken. |
| `vilkar_id` | TEXT | PK | Vilkåret, se `Vilkar`. |
| `godkjent` | BOOLEAN | NULL | Om vilkåret er oppfylt. `NULL` betyr «ikke vurdert ennå». |
| `notat` | TEXT | NULL | Saksbehandlers begrunnelse for vurderingen. |
| `vurdert_tidspunkt` | TIMESTAMPTZ | NULL | Når vilkåret sist ble vurdert. |
| `vurdert_av_ident` | TEXT | NULL | NAV-identen til den som vurderte vilkåret. Null når systemet vurderte det eller vilkåret ikke er vurdert. |

### `sakslogg`

Hendelseslogg / audit trail for en sak (1:N). Både saksbehandlere, besluttere og systemet skriver
til loggen. Se også [`sakslogg.md`](sakslogg.md).

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sakslogg_id` | UUID | PK, default `gen_random_uuid()` | Teknisk id for loggposten. |
| `sak_id` | UUID | NOT NULL, FK → `sak(sak_id)` ON DELETE CASCADE | Saken hendelsen gjelder. |
| `utfort_av_rolle` | TEXT | NOT NULL | Rollen til den som utførte handlingen (`SAKSBEHANDLER` / `BESLUTTER` / `SYSTEM`). Se `AktorRolle`. |
| `utfort_av_ident` | TEXT | NULL | NAV-ident til den som utførte handlingen. Null når `utfort_av_rolle = SYSTEM`. |
| `notat` | TEXT | NULL | Teksten som vises i saksloggen, eventuelt med fritekst. |
| `utfort_at` | TIMESTAMPTZ | NOT NULL, default `now()` | Tidspunkt for hendelsen. |

`utfort_av_type` fantes i V12, men er fjernet i V17. Rollen `SYSTEM` erstatter den.

Indeks: `idx_sakslogg_sak_id(sak_id)`, `idx_sakslogg_sak_ident(sak_id, utfort_av_ident)`.

**Habilitet — beslutter kan ikke fatte vedtak på et foreløpig vedtak hen selv sendte til
beslutning.** Regelen gjelder vedtaket, ikke saken. Den som sendte saken til `TIL_BESLUTNING`, kan
ikke beslutte den. Andre som har vært saksbehandler på saken, kan. Slik blir ikke en sak stående
fordi den eneste beslutteren på jobb har vært innom den, og ingen kan omgå topartskontrollen ved å
få saken tildelt fram og tilbake. Regelen bygges sammen med vedtak, og implementeres i Kotlin
(sikkerhetskritisk).

**Alle saksbehandler-handlinger skal logges** med `utfort_av_rolle='SAKSBEHANDLER'` og
`utfort_av_ident`, fordi loggen er sporet for saken og viser hvem som sendte saken til beslutning.

### `sluttrapport`

Sluttrapportering for en sak (1:1 fra sak). Selve dokumentene ligger i `vedlegg` og kobles via
`vedlegg.sluttrapport_id` — det kan være flere vedlegg per rapport.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sluttrapport_id` | UUID | PK, default `gen_random_uuid()` | Teknisk id. Refereres av `sak.sluttrapport_id` og `vedlegg.sluttrapport_id`. |
| `status` | TEXT | NOT NULL, default `MOTTATT` | Rapportens status. |
| `opprettet` | TIMESTAMPTZ | NOT NULL, default `now()` | Når rapporten ble registrert. |

### `sak_retur`

Retur fra beslutter til saksbehandler i to-trinns kontroll (1:N). En sak kan returneres flere
ganger — hver retur er en egen rad, slik at hele historikken bevares.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `retur_id` | UUID | PK, default `gen_random_uuid()` | Teknisk id for returen. |
| `sak_id` | UUID | NOT NULL, FK → `sak(sak_id)` ON DELETE CASCADE | Saken som ble returnert. |
| `returnert_av_ident` | TEXT | NOT NULL | NAV-ident til beslutter som returnerte. |
| `til_saksbehandler_ident` | TEXT | NULL | Saksbehandler saken gikk tilbake til. Null hvis usatt. |
| `aarsak` | TEXT | NOT NULL | Returårsak. Se `ReturArsak`. |
| `forklaring` | TEXT | NOT NULL, CHECK `char_length(forklaring) <= 1000` | Beslutters begrunnelse. Maks 1000 tegn. |
| `opprettet` | TIMESTAMPTZ | NOT NULL, default `now()` | Tidspunkt for returen. |

Indeks: `idx_sak_retur_sak_id(sak_id)`.

## Endringer i eksisterende tabeller

### `refusjonskrav`

- **Utsatt fjerning:** `soknad_id` (og indeks `idx_refusjonskrav_soknad_id`) skal på sikt fjernes,
  siden refusjon kobles til sak via `sak.refusjon_id`. Men kolonnen er fortsatt i aktiv bruk i
  `RefusjonDb` (`lagreRefusjonskrav` + `finnRefusjonskravStatus`). Vi følger derfor
  **expand/contract**: `V12` beholder kolonnen (additiv migrasjon), og den droppes først i en
  senere contract-migrasjon når koden kobler refusjon via sak. Å droppe den nå ville brukket
  refusjon i prod.
- Tabellnavnet beholdes (`refusjonskrav`) for å unngå kodeendringer i `RefusjonskravTable`.

### `vedlegg`

- **Legges til:** `sluttrapport_id UUID` (FK → `sluttrapport(sluttrapport_id)` ON DELETE
  CASCADE), med indeks `idx_vedlegg_sluttrapport_id`.

## Enums

Alle lagres som `TEXT` i databasen og valideres i Kotlin.

### `Saksstatus` (`sak.status`)

| Verdi | Betydning |
|-------|-----------|
| `OPPRETTET` | Sak dannet fra innvilget søknad. |
| `UNDER_BEHANDLING` | Saksbehandler jobber med saken. |
| `TIL_BESLUTNING` | Sendt til beslutter for godkjenning (foreløpig vedtak i `foreslatt_utfall`). |
| `INNVILGET` | Vedtak: bistand innvilget (godkjent av beslutter). |
| `AVSLATT` | Vedtak: avslag (godkjent av beslutter). |
| `AVSLUTTET` | Sluttrapport mottatt / refusjon utbetalt — saken lukket. |

Flyt: `OPPRETTET` → (saksbehandler tilordner seg) `UNDER_BEHANDLING` → (sender til godkjenning)
`TIL_BESLUTNING` → beslutter godkjenner → `INNVILGET`/`AVSLATT`, **eller** beslutter returnerer →
`UNDER_BEHANDLING` (+ rad i `sak_retur`). `INNVILGET`/`AVSLATT` → `AVSLUTTET`.

### `KildeTilBehandling` (`sak.kilde_til_behandling`)

| Verdi | Betydning |
|-------|-----------|
| `EKSPERTBISTAND` | Vårt saksbehandlingssystem |
| `ARENA` | Opprettet fra Arena-integrasjon. |

### `AktorRolle` (`sakslogg.utfort_av_rolle`)

Rollen aktøren hadde ved handlingen. Alltid satt.

| Verdi | Betydning |
|-------|-----------|
| `SAKSBEHANDLER` | Saksbehandler som utreder saken. |
| `BESLUTTER` | Beslutter som fatter/godkjenner vedtak. |
| `SYSTEM` | Handling utført automatisk av systemet (`utfort_av_ident` er null). |

### `Vilkar` (`saksvilkar.vilkar_id`)

Nye verdier krever at `SakProjection` kjøres på nytt (bump versjonen), slik at eksisterende saker får rader for dem.

| Verdi | Betydning |
|-------|-----------|
| `DELTAKER_HAR_ARBEIDSFORHOLD` | Den ansatte har aktivt arbeidsforhold hos arbeidsgiveren. |
| `FYLLES_UT_I_SAMRAD_GODKJENT` | Søknaden er fylt ut i samråd med den ansatte. |
| `ARBEIDSGIVER_HAR_PROVD_TILRETTELEGGING` | Ordinær tilrettelegging er prøvd før ekspertbistand. |
| `DELTAKER_HAR_SYKEFRAVAERSHISTORIKK` | Det finnes relevant sykefraværshistorikk. |
| `EKSPERT_HAR_KOMPETANSE` | Eksperten har nødvendig og relevant kompetanse. |

### `ReturArsak` (`sak_retur.aarsak`)

| Verdi | Betydning |
|-------|-----------|
| `FEIL_REGELFORSTAELSE` | Feil i regelforståelsen. |
| `FEIL_FAKTA` | Feil i faktagrunnlaget. |
| `ANNET` | Annen årsak — se `forklaring`. |

### `Refusjonsstatus` (`refusjonskrav.status`)

Uendret. Default `MOTTATT`.

### `Sluttrapportstatus` (`sluttrapport.status`)

Default `MOTTATT`.

## ER-oversikt

```
soknad 1 ──── 1 sak 1 ──── N saksvilkar
                 │ 1
                 ├──── N sakslogg
                 │ 1
                 ├──── N sak_retur              (via sak_retur.sak_id)
                 │ 1
                 ├──── 0..1 refusjonskrav      (via sak.refusjon_id)
                 │ 1
                 └──── 0..1 sluttrapport        (via sak.sluttrapport_id)
                                │ 1
                                └──── N vedlegg (via vedlegg.sluttrapport_id)
```

## Migrasjon

- Implementeres som Flyway-migrasjon `V12__legg_til_sak.sql` (neste ledige versjon etter `V11`).
- **Additiv (expand-steg):** oppretter kun nye, tomme tabeller (`sluttrapport`, `sak`,
  `saksvilkar`, `sakslogg`, `sak_retur`) og legger til nullbar `vedlegg.sluttrapport_id`. Ingen
  eksisterende data endres eller slettes, ingen kolonner droppes — trygt i prod.
- **Utsatt (contract-steg):** `refusjonskrav.soknad_id` droppes IKKE i `V12` (se `refusjonskrav`
  over). Krever kodeomlegging i `RefusjonDb` først, deretter egen contract-migrasjon.
- `V19__saksvilkar_rad_per_vilkar.sql` sletter `saksvilkar` og oppretter den på nytt med én rad
  per vilkår. Tabellen var tom fordi ingen kode skrev til den. Vilkårsradene opprettes av
  `SakProjection` (`Sak-v3`), som kjøres på nytt og også gir vilkår til eksisterende saker.

### Rollback

Trygt fordi de **nye** tabellene (`sak`, `saksvilkar`, `sakslogg`, `sak_retur`, `sluttrapport`)
er tomme ved rollback, og endringene på eksisterende tabeller berører kun nye/tomme kolonner:

- `vedlegg.sluttrapport_id` — kolonnen ble innført i denne migrasjonen, så `DROP COLUMN` fjerner
  bare nye data. Øvrige `vedlegg`-kolonner er urørt.
- `refusjonskrav.soknad_id` — gjenopprettes som tom kolonne. Trygt kun fordi `refusjonskrav`
  ikke har produksjonsdata (jf. Migrasjon over); ved eksisterende data ville de gamle
  `soknad_id`-koblingene gått tapt.

⚠️ `soknad` og andre eksisterende tabeller røres ikke av denne rollbacken.

```sql
DROP TABLE IF EXISTS sakslogg, saksvilkar, sak_retur CASCADE;
ALTER TABLE sak DROP CONSTRAINT IF EXISTS fk_sak_sluttrapport;
DROP TABLE IF EXISTS sak CASCADE;
DROP TABLE IF EXISTS sluttrapport CASCADE;
ALTER TABLE vedlegg DROP COLUMN IF EXISTS sluttrapport_id;
ALTER TABLE refusjonskrav ADD COLUMN soknad_id UUID;
```
