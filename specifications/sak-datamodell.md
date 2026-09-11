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
| Refusjon | **1:1**, kun på sak (`sak.refusjon_id`) | Refusjon hører til behandlingen, ikke søknaden. `refusjonskrav.soknad_id` fjernes. |
| Sluttrapport | Ny tabell, **1:1** på sak (`sak.sluttrapport_id`) | Kan ha flere vedlegg; egen tabell samler metadata. |
| Saksvilkår | **1:1**, deler PK med sak | Ren utvidelse av sak uten egen teknisk nøkkel. |
| Sakslogg | **1:N** fra sak | Hendelseslogg / audit trail. |
| Enum-verdier | `TEXT` i DB, håndhevet i Kotlin | Nye verdier krever ingen DB-migrering (samme mønster som `soknad.status`). |
| Aktør i logg | `utfort_av_type` = `BRUKER`/`SYSTEM` | Både saksbehandlere og systemet skriver til loggen. |

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
| `kilde_til_behandling` | TEXT | NOT NULL | Hva som utløste behandlingen. Se `KildeTilBehandling`. |
| `behandlende_enhet` | TEXT | NULL | NAV-enheten som behandler saken — NORG-enhetsnummer (4 siffer, ledende nuller bevart). Null til enhet er satt. |
| `ansvarlig_ident` | TEXT | NULL | NAV-ident til overordnet ansvarlig for saken. |
| `saksbehandler_ident` | TEXT | NULL | NAV-ident til saksbehandler som utreder. Null før tildeling. |
| `beslutter_ident` | TEXT | NULL | NAV-ident til beslutter (to-trinns kontroll). Null før beslutning. |
| `arena_sak_id` | TEXT | NULL | Saksnummer i Arena når saken er speilet dit. Null hvis ikke i Arena. |
| `refusjon_id` | UUID | UNIQUE, FK → `refusjonskrav(id)` | Refusjonskravet for saken (1:1). Null til krav mottas. |
| `sluttrapport_id` | UUID | UNIQUE, FK → `sluttrapport(sluttrapport_id)` | Sluttrapporten for saken (1:1). Null til rapport mottas. |
| `opprettet` | TIMESTAMPTZ | NOT NULL, default `now()` | Når saken ble opprettet. |
| `sist_endret` | TIMESTAMPTZ | NOT NULL, default `now()` | Sist endret. Oppdateres av applikasjonen ved skriv. |

Indekser: `idx_sak_status(status)`, `idx_sak_saksbehandler_ident(saksbehandler_ident)`.

### `saksvilkar`

Vilkårsvurderingen for en sak (1:1, deler primærnøkkel med `sak`). Alle vurderingsfelter er
nullbare og betyr «ikke vurdert ennå».

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sak_id` | UUID | PK, FK → `sak(sak_id)` ON DELETE CASCADE | Deler nøkkel med `sak`. Slettes med saken. |
| `har_arbeidsforhold` | BOOLEAN | NULL | Om den ansatte har aktivt arbeidsforhold. |
| `fylles_ut_i_samrad_godkjent` | BOOLEAN | NULL | Om vilkåret «fylt ut i samråd» er bekreftet/godkjent. |
| `provd_tilrettelegging` | BOOLEAN | NULL | Om ordinær tilrettelegging er prøvd før ekspertbistand. |
| `provd_tilrettelegging_notat` | TEXT | NULL | Saksbehandlers begrunnelse til vurderingen over. |
| `har_sykefravaershistorikk` | BOOLEAN | NULL | Om det finnes relevant sykefraværshistorikk. |
| `har_sykefravaershistorikk_notat` | TEXT | NULL | Notat til sykefraværsvurderingen. |
| `ekspert_har_kompetanse` | BOOLEAN | NULL | Om eksperten har nødvendig/relevant kompetanse. |
| `ekspert_har_kompetanse_notat` | TEXT | NULL | Notat til kompetansevurderingen. |

### `sakslogg`

Hendelseslogg / audit trail for en sak (1:N). Både saksbehandlere (`BRUKER`) og systemet
(`SYSTEM`) skriver til loggen.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sakslogg_id` | UUID | PK, default `gen_random_uuid()` | Teknisk id for loggposten. |
| `sak_id` | UUID | NOT NULL, FK → `sak(sak_id)` ON DELETE CASCADE | Saken hendelsen gjelder. |
| `utfort_av_type` | TEXT | NOT NULL | Om handlingen ble utført av en `BRUKER` eller `SYSTEM`. Se `AktorType`. |
| `utfort_av_ident` | TEXT | NULL | NAV-ident til den som utførte handlingen. Null når `utfort_av_type = SYSTEM`. |
| `loggtype` | TEXT | NOT NULL | Type hendelse. Se `Loggtype`. |
| `fra_status` | TEXT | NULL | Status før endring (kun ved `STATUS_ENDRET`). |
| `til_status` | TEXT | NULL | Status etter endring (kun ved `STATUS_ENDRET`). |
| `notat` | TEXT | NULL | Valgfri fritekst (f.eks. ved `NOTAT`). |
| `utfort_at` | TIMESTAMPTZ | NOT NULL, default `now()` | Tidspunkt for hendelsen. |

Indeks: `idx_sakslogg_sak_id(sak_id)`.

### `sluttrapport`

Sluttrapportering for en sak (1:1 fra sak). Selve dokumentene ligger i `vedlegg` og kobles via
`vedlegg.sluttrapport_id` — det kan være flere vedlegg per rapport.

| Kolonne | Type | Constraints | Beskrivelse |
|---------|------|-------------|-------------|
| `sluttrapport_id` | UUID | PK, default `gen_random_uuid()` | Teknisk id. Refereres av `sak.sluttrapport_id` og `vedlegg.sluttrapport_id`. |
| `status` | TEXT | NOT NULL, default `MOTTATT` | Rapportens status. |
| `opprettet` | TIMESTAMPTZ | NOT NULL, default `now()` | Når rapporten ble registrert. |

## Endringer i eksisterende tabeller

### `refusjonskrav`

- **Fjernes:** `soknad_id` (og indeks `idx_refusjonskrav_soknad_id`). Refusjon kobles nå til sak
  via `sak.refusjon_id`, ikke til søknad.
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
| `VENTER` | Avventer informasjon/dokumentasjon. |
| `INNVILGET` | Vedtak: bistand innvilget. |
| `AVSLATT` | Vedtak: avslag. |
| `UNDER_GJENNOMFORING` | Tiltaket pågår. |
| `AVSLUTTET` | Sluttrapport mottatt / refusjon utbetalt — saken lukket. |
| `HENLAGT` | Avbrutt uten realitetsvedtak. |

### `KildeTilBehandling` (`sak.kilde_til_behandling`)

| Verdi | Betydning |
|-------|-----------|
| `ALTINN` | Innsendt via Altinn / selvbetjening. |
| `ARENA` | Opprettet fra Arena-integrasjon. |
| `MANUELL` | Manuelt opprettet av saksbehandler. |

### `AktorType` (`sakslogg.utfort_av_type`)

| Verdi | Betydning |
|-------|-----------|
| `BRUKER` | Handling utført av en saksbehandler (har `utfort_av_ident`). |
| `SYSTEM` | Handling utført automatisk av systemet (`utfort_av_ident` er null). |

### `Loggtype` (`sakslogg.loggtype`)

Bevisst slank. Statusoverganger dekkes av `STATUS_ENDRET` sammen med `fra_status`/`til_status`,
slik at enumen ikke må utvides for hver nye status.

| Verdi | Betydning |
|-------|-----------|
| `SAK_OPPRETTET` | Saken ble opprettet. |
| `STATUS_ENDRET` | Statusovergang — se `fra_status` → `til_status`. |
| `VILKAR_VURDERT` | Vilkår ble vurdert/oppdatert. |
| `NOTAT` | Fritt saksbehandlernotat (`notat`). |

### `Refusjonsstatus` (`refusjonskrav.status`)

Uendret. Default `MOTTATT`.

### `Sluttrapportstatus` (`sluttrapport.status`)

Default `MOTTATT`.

## ER-oversikt

```
soknad 1 ──── 1 sak 1 ──── 1 saksvilkar
                 │ 1
                 ├──── N sakslogg
                 │ 1
                 ├──── 0..1 refusjonskrav      (via sak.refusjon_id)
                 │ 1
                 └──── 0..1 sluttrapport        (via sak.sluttrapport_id)
                                │ 1
                                └──── N vedlegg (via vedlegg.sluttrapport_id)
```

## Migrasjon

- Implementeres som Flyway-migrasjon `V12__legg_til_sak.sql` (neste ledige versjon etter `V11`).
- **Rent nybygg** — ingen produksjonsdata i `refusjonskrav`, så `DROP COLUMN soknad_id` er trygt.
- **Kodeendring kreves:** `RefusjonDb.lagreRefusjonskrav` setter i dag `RefusjonskravTable.soknadId`.
  Denne må oppdateres til å koble refusjon mot sak i stedet for søknad.

### Rollback

Ingen datatap siden tabellene er tomme:

```sql
DROP TABLE IF EXISTS sakslogg, saksvilkar CASCADE;
ALTER TABLE sak DROP CONSTRAINT IF EXISTS fk_sak_sluttrapport;
DROP TABLE IF EXISTS sak CASCADE;
DROP TABLE IF EXISTS sluttrapport CASCADE;
ALTER TABLE vedlegg DROP COLUMN IF EXISTS sluttrapport_id;
ALTER TABLE refusjonskrav ADD COLUMN soknad_id UUID;
```
