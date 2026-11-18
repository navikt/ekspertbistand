# Manuell test av OeBS-integrasjonen i dev

## Mål

Vi bekrefter at meldinger fra `oebs_outbox` når OeBS T1 via tiltaksøkonomi (Team
VALP), og at statusen kommer tilbake til oss. Vi legger testmeldingene rett i
outboxen med SQL i stedet for å gå gjennom saksbehandlingen.

Testen går i to runder:

1. **Runde 1** tester `BESTILLING` og `ANNULLERING`. Godkjent 2026-11-13.
2. **Runde 2** tester `FAKTURA` og `GJOR_OPP_BESTILLING`. Godkjent 2026-11-16.
3. **Repetisjon** av hele løpet 2026-11-17 og 2026-11-18, etter ønske fra OeBS.

Når siste repetisjon er godkjent, sletter vi testdataene i dev. Deretter kobler vi de
testede meldingstypene til hendelser i saksgangen. Prod er uendret.

## Bakgrunn

- Outbox, poller, status-consumer og revisjonsspor finnes, se
  [`oebs/README.md`](../backend/src/main/kotlin/no/nav/ekspertbistand/oebs/README.md)
  og [`integrasjon_oebs_tiltaksokonomi_bestilling.md`](integrasjon_oebs_tiltaksokonomi_bestilling.md).
- `BestillTilsagnIOebs` legger en `BESTILLING` i outboxen når et vedtak blir
  innvilget, se [`sette_av_penger_oebs_vedtak.md`](sette_av_penger_oebs_vedtak.md).
  Handleren er registrert i alle miljøer. I dev kan det derfor ligge
  `PENDING`-rader fra saksbehandlingen.
- Til nå har ingen startet polleren og status-consumeren. Ingenting har gått ut
  til VALP.
- VALP har gitt oss lesetilgang til status-topicene i dev
  (navikt/mulighetsrommet#8572).

## Kodeendring

`Application.main()` kaller `startOebsProsessering` bare i dev:

```kotlin
basedOnEnvSuspending(
    dev = { startOebsProsessering(coroutineContext) },
    other = {},
)
```

Det starter to prosesser i `ekspertbistand-backend` i dev:

- `OebsOutboxPoller` publiserer `PENDING`-rader til `fager.ekspertbistand.bestillinger-v1`.
- `TiltaksokonomiConsumer` leser `team-mulighetsrommet.tiltaksokonomi.bestilling-status-v1`
  og `faktura-status-v1` (consumer group `fager.ekspertbistand.tiltaksokonomi-status`,
  `earliest`). Den beholder bare meldinger med header `fagsystem=EKSPERTBISTAND`.

Prod, lokal kjøring og mock-backenden er uendret. Lokalt og i mock-backenden
svarer `LokalOebs` som før. KDoc i `Oebs.kt` og `TiltaksokonomiConsumer.kt` og
`oebs/README.md` er oppdatert.

Vi trenger ingen endring i Nais-manifestet. `kafka.pool: nav-dev` gir
Kafka-credentials, og topic-manifestet gir oss skrivetilgang og VALP lesetilgang.

## Beslutninger

| Valg | Begrunnelse |
|------|-------------|
| Polleren startes bare i dev, i kode | Samme mønster som `basedOnEnvSuspending` ellers i `main()`. Når runde 1 er godkjent, er polleren allerede på i dev for neste steg (koble til saksgangen). Prod åpnes i et eget kort. |
| Faktura og oppgjør testes i en egen runde (OeBS, 2026-11-13) | OeBS er ikke klare for faktura og oppgjør. De gir beskjed når de er det. Bestilling og annullering testes først. |
| Testmeldingene legges rett i `oebs_outbox` | Vi tester integrasjonen uten å være avhengige av saksbehandlingsflyten. Polleren, revisjonssporet og status-consumeren kjører som vanlig. |
| Saksnummer `2026/900001` til `2026/900009` | Under starten på `saksnummer_seq` (1 000 000), så ingen ekte sak kan få nummeret. Arena-tilsagn i OeBS har ikke `E-`-prefiks. Testnumrene kolliderer derfor ikke med ekte bestillinger i VALP og OeBS T1, der testdataene blir liggende. `LIKE 'E-2026/90000_-%'` treffer bare disse ni numrene. |
| Arrangør `925236594`, kontonummer `10002427740`, identer `Z993433` og `Z990079` | Verdiene står i VALP sine eksempelmeldinger fra dev (`OebsBestillingMeldingContractTest`). Vi vet derfor at de går gjennom Brreg/Ereg-oppslaget hos VALP. Ingen personopplysninger. |
| Kostnadssted `0391` (1A), `1291` (1C), `0291` og `1891` (2026-11-17), `0391` og `1291` (2026-11-18 og erstatning) | Kostnadsstedet er `sak.behandlende_enhet`. Bare disse enhetene behandler ekspertbistand: 0291, 0391, 0491, 0691, 0891, 1091, 1191, 1291, 1591, 1891, 1991, 2103 og 5772. 2026-11-17 avviste OeBS T1 `E-2026/900006-1` fordi kostnadsstedet `1891` manglet der. Resten av repetisjonen bruker derfor bare `0391` og `1291`, som er bekreftet i T1. OeBS har senere bekreftet at bare `1891` og `1991` mangler i T1. Repetisjonen tester flyten. Hvilke kostnadssteder OeBS har, følger vi opp med OeBS (se *Kostnadssteder i OeBS*). |
| Repetisjonen bruker nye saksnumre (900005 til 900009) | Sender vi et brukt bestillingsnummer på nytt, oppretter ikke VALP noe nytt (1B). Annullering, faktura og oppgjør krever status `AKTIV`. 900003 og 900004 er holdt av til 1D og reservebestillingen i runde 2. 900009 erstatter bestillingen som ble avvist 2026-11-17. |
| Periode `[2026-11-16, 2026-12-16)` | Konteringen hos VALP gjelder fra 2026-11-01. Perioden spenner over to måneder, så fakturaen treffer en bestillingslinje. |

## Forutsetninger

Gå gjennom listen før deploy:

- [ ] **OeBS** har bekreftet at det funksjonelle og tekniske oppsettet mot T1 er
      klart for bestilling og annullering (runde 1), eller for faktura og
      oppgjør (runde 2).
- [ ] **VALP** har skrudd på `EkspertbistandBestillingConsumer` (topic
      `fager.ekspertbistand.bestillinger-v1`) i dev og følger med i loggene sine
      under testen.
- [ ] Teamet vet om testvinduet og fatter ikke innvilgede vedtak i dev mens testen pågår.
- [ ] Du har skrivetilgang til dev-databasen (se *Tilgang til databasen*).

## Tilgang til databasen

Vi bruker Cloud SQL Studio i GCP-konsollen (`fager-dev`, instans
`ekspertbistand-backend`, database `ekspertbistand`) med personlig IAM-bruker.
Lesetilgang er standard. Testen trenger skrivetilgang:

```shell
# Én gang, hvis du ikke har en IAM-databasebruker på instansen fra før
nais cloudsql grant --team fager --environment dev-gcp ekspertbistand-backend

# Skrivetilgang til tabellene
nais cloudsql prepare --team fager --environment dev-gcp --all-privileges ekspertbistand-backend
```

Trekk tilbake skrivetilgangen når testen og oppryddingen er ferdig:

```shell
nais cloudsql revoke --team fager --environment dev-gcp ekspertbistand-backend
```

## Fremgangsmåte

### 1. Sjekk outboxen før deploy

Polleren publiserer alle `PENDING`-rader når den starter, også rader fra
saksbehandlingen.

```sql
SELECT id, bestillingsnummer, meldingstype, created_at,
       melding_json -> 'payload' -> 'periode' AS periode
FROM oebs_outbox
WHERE status = 'PENDING'
ORDER BY id;
```

Finnes det rader, kopierer du resultatet til testloggen (Trello-kortet) og
sletter radene. Det gir en kontrollert test. Bestillinger med periode før
2026-11-01 ville uansett blitt avvist hos VALP (mangler kontering).

```sql
DELETE FROM oebs_outbox WHERE status = 'PENDING';
```

Raden i `sak_bestilling` blir stående for disse sakene. Bestillingen blir ikke
sendt. Det gjelder bare testdata i dev.

### 2. Deploy branchen til dev

```shell
gh workflow run deploy-dev-manual-backend.yml --ref <branch>
```

Sjekk i loggene at appen starter uten Kafka-feil. Den første meldingen fra polleren
kommer først når det finnes en rad.

Status-consumeren leser status-topicene fra starten (`earliest`). Den hopper over
meldinger fra andre fagsystemer og logger dem bare på `debug`.

### 3. Runde 1: bestilling og annullering

Hver `INSERT` henter `bestillingsnummer` (Kafka-nøkkelen) og `meldingstype` fra
JSON-en. Da kan ikke kolonnene og meldingen spriker. Polleren leser
`melding_json` med vår egen modell og serialiserer den på nytt. JSON-en må derfor
følge `OebsBestillingMelding`, ikke VALP sin modell (se *Kontraktsavvik*).

Rekkefølgen er viktig. VALP godtar annullering, faktura og oppgjør bare når
bestillingen har status `AKTIV`. Vent på forventet status før neste steg.

#### 1A: Bestilling `E-2026/900001-1`

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/900001-1",
    "regnskapskonto": "TILTAK_DRIFTSTILSKUDD",
    "artskonto": "EKSPERTBISTAND",
    "arrangor": { "type": "NORSK", "organisasjonsnummer": "925236594" },
    "kostnadssted": "0391",
    "avtalenummer": null,
    "belop": 1000,
    "periode": { "start": "2026-11-16", "slutt": "2026-12-16" },
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T12:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T12:05:00Z",
    "valuta": "NOK"
  }
}'::jsonb)) AS v(m);
```

Forventet: `PUBLISHED` i outboxen og bestillingsstatus `SENDT`, deretter `AKTIV`
når OeBS kvitterer.

#### 1B: Samme bestilling på nytt (idempotens)

Kjør `INSERT`-en fra 1A en gang til når statusen er `AKTIV`.

Forventet: en ny rad i `oebs_sendt_melding` og en ny statusmelding med `AKTIV` i
`oebs_mottatt_status`. VALP oppretter ingenting nytt.

#### 1C: Bestilling `E-2026/900002-1` og annullering

1C er uavhengig av 1A og 1B og kan kjøres samtidig. Send bestillingen:

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/900002-1",
    "regnskapskonto": "TILTAK_DRIFTSTILSKUDD",
    "artskonto": "EKSPERTBISTAND",
    "arrangor": { "type": "NORSK", "organisasjonsnummer": "925236594" },
    "kostnadssted": "1291",
    "avtalenummer": null,
    "belop": 500,
    "periode": { "start": "2026-11-16", "slutt": "2026-12-16" },
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T12:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T12:05:00Z",
    "valuta": "NOK"
  }
}'::jsonb)) AS v(m);
```

Når statusen er `AKTIV`, annullerer du:

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "ANNULLERING",
  "payload": {
    "bestillingsnummer": "E-2026/900002-1",
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T15:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T15:05:00Z"
  }
}'::jsonb)) AS v(m);
```

Forventet: `ANNULLERING_SENDT`, deretter `ANNULLERT`.

#### 1D (valgfri): Feilet bestilling

Vi har ingen kjent måte å få `FEILET` fra OeBS på uten å avtale det med OeBS. Et
ugyldig kostnadssted (for eksempel `9999` i en kopi av 1A med
`E-2026/900003-1`) gir sannsynligvis avvist kvittering. Kjør bare hvis OeBS og
VALP er enige.

Forventet: `trenger_manuell_oppfolging = true` i `oebs_bestilling_status` og
`log.error` «Status for E-2026/900003-1 er FEILET og krever manuell oppfølging».
Detaljene står i teamLogs.

### 4. Runde 2: faktura og oppgjør

Vent med denne runden til OeBS sier at de er klare for faktura og oppgjør. Sjekk
først at VALP fortsatt godtar `kid` (se *Kontraktsavvik*).

Runde 2 bruker bestillingen fra 1A. Oppryddingen etter runde 1 sletter den bare
hos oss, så den er fortsatt `AKTIV` hos VALP og i OeBS. Spør VALP om den ikke
er det. Da sender du en ny bestilling først (kopi av 1A med
`E-2026/900004-1`) og bruker det nummeret i 2A og 2B.

#### 2A: Faktura `E-2026/900001-1-1`

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "FAKTURA",
  "payload": {
    "fakturanummer": "E-2026/900001-1-1",
    "bestillingsnummer": "E-2026/900001-1",
    "betalingsinformasjon": { "type": "BBAN", "kontonummer": "10002427740", "kid": null },
    "belop": 400,
    "periode": { "start": "2026-11-16", "slutt": "2026-12-01" },
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T13:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T13:05:00Z",
    "gjorOppBestilling": false,
    "beskrivelse": "Ekspertbistand test E-2026/900001-1-1",
    "valuta": "NOK"
  }
}'::jsonb)) AS v(m);
```

`beskrivelse` må være satt. VALP gjør `"kid": null` om til
`MeldingTilMottaker` med `beskrivelse` som melding.

Forventet: fakturastatus `SENDT`, deretter `IKKE_BETALT` eller `FULLT_BETALT` når
OeBS kvitterer. Statusen lagres i `oebs_bestilling_status` på fakturanummeret.

#### 2B: Gjør opp `E-2026/900001-1`

Kjør når fakturaen i 2A har fått kvittering (ikke lenger `SENDT`).

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "GJOR_OPP_BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/900001-1",
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T14:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T14:05:00Z"
  }
}'::jsonb)) AS v(m);
```

Forventet: bestillingsstatus `OPPGJORT`. VALP sender oppgjøret som en faktura
`E-2026/900001-1-X` til OeBS, men publiserer ikke fakturastatus for den.

### 4b. Repetisjon (OeBS, 2026-11-17)

OeBS vil ha flere meldinger gjennom. Vi kjører hele løpet en gang
2026-11-17 og en gang 2026-11-18. Hver repetisjon bruker to nye bestillinger:

- **A** går gjennom bestilling, faktura og oppgjør.
- **B** går gjennom bestilling og annullering.

| Dag | A | B |
|-----|---|---|
| 2026-11-17 | `E-2026/900005-1`, kostnadssted `0291` | `E-2026/900006-1`, kostnadssted `1891` |
| 2026-11-17, erstatning | `E-2026/900009-1`, kostnadssted `0391` | |
| 2026-11-18 | `E-2026/900007-1`, kostnadssted `0391` | `E-2026/900008-1`, kostnadssted `1291` |

OeBS T1 avviste en av bestillingene 2026-11-17 fordi kostnadsstedet ikke finnes
der. Bestillingen `E-2026/900009-1` med kostnadssted `0391` tar rollen til den
avviste, enten den var A eller B. Kjør blokk 1 med bare én rad i `VALUES`:

- erstatter den A: `('E-2026/900009-1', '0391', 1000)`, og bruk `E-2026/900009-1`
  i blokk 3 og 4
- erstatter den B: `('E-2026/900009-1', '0391', 500)`, og bruk `E-2026/900009-1`
  i blokk 2

Den avviste bestillingen får ingen flere meldinger.

SQL-en under bygger samme JSON som 1A, 1C, 2A og 2B. Du endrer bare
`VALUES`-linja nederst i hver blokk. Rekkefølgen er den samme som før: vent på
`AKTIV` før annullering og faktura, og på kvittering for fakturaen før oppgjør.

**1. Bestill A og B.** Én `INSERT` gir to rader. Dagens verdier står i `VALUES`.

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT nr, 'BESTILLING', jsonb_build_object(
  'type', 'BESTILLING',
  'payload', jsonb_build_object(
    'bestillingsnummer', nr,
    'regnskapskonto', 'TILTAK_DRIFTSTILSKUDD',
    'artskonto', 'EKSPERTBISTAND',
    'arrangor', jsonb_build_object('type', 'NORSK', 'organisasjonsnummer', '925236594'),
    'kostnadssted', kostnadssted,
    'avtalenummer', NULL,
    'belop', belop,
    'periode', jsonb_build_object('start', '2026-11-16', 'slutt', '2026-12-16'),
    'behandletAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z993433'),
    'behandletTidspunkt', '2026-11-12T12:00:00Z',
    'besluttetAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z990079'),
    'besluttetTidspunkt', '2026-11-12T12:05:00Z',
    'valuta', 'NOK'
  )
)
FROM (VALUES ('E-2026/900005-1', '0291', 1000),
             ('E-2026/900006-1', '1891', 500)) AS p(nr, kostnadssted, belop);
```

**2. Annuller B** når B er `AKTIV`:

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT nr, 'ANNULLERING', jsonb_build_object(
  'type', 'ANNULLERING',
  'payload', jsonb_build_object(
    'bestillingsnummer', nr,
    'behandletAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z993433'),
    'behandletTidspunkt', '2026-11-12T15:00:00Z',
    'besluttetAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z990079'),
    'besluttetTidspunkt', '2026-11-12T15:05:00Z'
  )
)
FROM (VALUES ('E-2026/900006-1')) AS p(nr);
```

**3. Fakturer A** når A er `AKTIV`. Fakturanummeret blir `<A>-1`.

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT nr, 'FAKTURA', jsonb_build_object(
  'type', 'FAKTURA',
  'payload', jsonb_build_object(
    'fakturanummer', nr || '-1',
    'bestillingsnummer', nr,
    'betalingsinformasjon', jsonb_build_object('type', 'BBAN', 'kontonummer', '10002427740', 'kid', NULL),
    'belop', 400,
    'periode', jsonb_build_object('start', '2026-11-16', 'slutt', '2026-12-01'),
    'behandletAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z993433'),
    'behandletTidspunkt', '2026-11-12T13:00:00Z',
    'besluttetAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z990079'),
    'besluttetTidspunkt', '2026-11-12T13:05:00Z',
    'gjorOppBestilling', false,
    'beskrivelse', 'Ekspertbistand test ' || nr || '-1',
    'valuta', 'NOK'
  )
)
FROM (VALUES ('E-2026/900005-1')) AS p(nr);
```

**4. Gjør opp A** når fakturaen har fått kvittering (ikke lenger `SENDT`):

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT nr, 'GJOR_OPP_BESTILLING', jsonb_build_object(
  'type', 'GJOR_OPP_BESTILLING',
  'payload', jsonb_build_object(
    'bestillingsnummer', nr,
    'behandletAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z993433'),
    'behandletTidspunkt', '2026-11-12T14:00:00Z',
    'besluttetAv', jsonb_build_object('type', 'NAV_ANSATT', 'navIdent', 'Z990079'),
    'besluttetTidspunkt', '2026-11-12T14:05:00Z'
  )
)
FROM (VALUES ('E-2026/900005-1')) AS p(nr);
```

Forventet for hver repetisjon: A ender med `OPPGJORT` og fakturaen med
`IKKE_BETALT` eller `FULLT_BETALT`. B ender med `ANNULLERT`. Spørringene i
*Følg med* dekker de nye numrene. Vent med oppryddingen til siste repetisjon er
godkjent.

### 5. Følg med

Etter hver `INSERT`:

```sql
-- Utgående: forventer PUBLISHED innen noen sekunder
SELECT id, bestillingsnummer, meldingstype, status, attempts, updated_at
FROM oebs_outbox
WHERE bestillingsnummer LIKE 'E-2026/90000_-%'
ORDER BY id;

-- Revisjonsspor for det vi sendte, med Kafka-koordinater
SELECT bestillingsnummer, meldingstype, kafka_partition, kafka_offset, sendt_tidspunkt
FROM oebs_sendt_melding
WHERE bestillingsnummer LIKE 'E-2026/90000_-%'
ORDER BY id;

-- Alle svar fra VALP
SELECT bestillingsnummer, kafka_topic, raw_json, mottatt_tidspunkt
FROM oebs_mottatt_status
WHERE bestillingsnummer LIKE 'E-2026/90000_-%'
ORDER BY id;

-- Siste status per bestilling og faktura
SELECT bestillingsnummer, status, trenger_manuell_oppfolging, mottatt_tidspunkt
FROM oebs_bestilling_status
WHERE bestillingsnummer LIKE 'E-2026/90000_-%'
ORDER BY bestillingsnummer;
```

I loggene til `ekspertbistand-backend` (dev):

- `Publiserte melding til fager.ekspertbistand.bestillinger-v1` når en rad er sendt.
- `Publisering av outbox-melding feilet` betyr at raden blir liggende og prøves
  igjen hvert 30. sekund (se *Feilsituasjoner*).
- `Status-melding bryter kontrakten` (error) betyr at statuskontrakten ikke
  stemmer. Hele meldingen og feilen står i teamLogs under samme tekst. Consumeren
  står fast på meldingen og prøver på nytt hvert 5. sekund til speilingen er rettet.

OeBS kvitterer i faste intervaller på 10 minutter (12:15:00, 12:25:00 og 12:35:00
i runde 1). Regn med opptil 10 minutter fra `SENDT` til `AKTIV` eller `ANNULLERT`.

### 6. Rydd opp

Når siste repetisjon er godkjent, sletter vi testdataene i dev. Revisjonssporet er
append-only i prod, men i dev er dette testdata vi selv har lagt inn.

```sql
BEGIN;
DELETE FROM oebs_outbox            WHERE bestillingsnummer LIKE 'E-2026/90000_-%';
DELETE FROM oebs_sendt_melding     WHERE bestillingsnummer LIKE 'E-2026/90000_-%';
DELETE FROM oebs_mottatt_status    WHERE bestillingsnummer LIKE 'E-2026/90000_-%';
DELETE FROM oebs_bestilling_status WHERE bestillingsnummer LIKE 'E-2026/90000_-%';
COMMIT;
```

Testdataene blir liggende hos VALP og i OeBS T1. Gi VALP og OeBS beskjed om at
runden er ferdig og hvilke numre vi brukte. Til slutt trekker du tilbake
skrivetilgangen (se *Tilgang til databasen*).

## Resultat

### Runde 1 (2026-11-13): godkjent

Alle fire meldingene ble publisert på første forsøk (`attempts = 1`), på
partisjon 0 med offset 0–3. VALP svarte på alle, og `fagsystem`-filteret og
statuskontrakten virket.

| Test | Sendt (UTC) | Statuser tilbake | Siste status |
|------|-------------|------------------|--------------|
| 1A: bestilling `E-2026/900001-1` (0391) | 12:04:18 | `SENDT` 12:04:20, `AKTIV` 12:15:00 | `AKTIV` |
| 1B: samme bestilling på nytt | 12:17:16 | `AKTIV` med samme `statusSistOppdatert` (12:15:00) | `AKTIV` |
| 1C: bestilling `E-2026/900002-1` (1291) | 12:19:08 | `SENDT` 12:19:10, `AKTIV` 12:25:00 | `AKTIV` |
| 1C: annullering `E-2026/900002-1` | 12:25:54 | `ANNULLERING_SENDT` 12:25:54, `ANNULLERT` 12:35:00 | `ANNULLERT` |

Funn:

- VALP svarer med `SENDT` innen 2–3 sekunder. OeBS kvitterer i neste
  10-minuttersintervall.
- 1B bekrefter idempotensen. VALP publiserer gjeldende status på nytt uten å
  endre den, og vi lagrer svaret som en ny rad i `oebs_mottatt_status`.
- `trenger_manuell_oppfolging` er `false` for begge bestillingene. 1D (feilet
  bestilling) ble ikke kjørt.

### Runde 2 (2026-11-16): godkjent

Siste status i `oebs_bestilling_status` etter runden:

| Test | Referanse | Siste status | Mottatt (UTC) | Manuell oppfølging |
|------|-----------|--------------|---------------|--------------------|
| 2A: faktura | `E-2026/900001-1-1` | `IKKE_BETALT` | 11:45:21 | `false` |
| 2B: gjør opp | `E-2026/900001-1` | `OPPGJORT` | 11:47:33 | `false` |

Funn:

- Den første fakturastatusen fra VALP (`faktura-status-v1`, partisjon 1, offset
  290) ble ikke lagret. VALP endret feltet `fakturaStatusSistOppdatert` til
  `statusSistOppdatert` 2026-09-21 (mulighetsrommet 944a943), etter at vi speilet
  kontrakten. Speilingen i `OebsStatusMelding.kt` er rettet.
- Consumeren hoppet over meldingen og committet offset, og feilen havnet bare i
  team-logs. Det var ingen beslutning bak det. Det kom inn i en refaktorering
  (8b27ea9). Nå logger consumeren `log.error` med topic, partisjon, offset og
  unntakstype, og `teamLog.error` med hele meldingen og feilen. Så kaster den, og
  `CoroutineKafkaConsumer` committer ikke, men prøver samme offset på nytt hvert
  5. sekund. Et kontraktbrudd stopper da consumeren til speilingen er rettet, i
  stedet for at statusen går tapt.
- Etter deploy av fiksen kom fakturastatusen fram, og oppgjøret ga `OPPGJORT` på
  bestillingen. `trenger_manuell_oppfolging` er `false` for begge.

### Repetisjon 2026-11-17: pågår

Funn:

- OeBS T1 avviste `E-2026/900006-1` (`1891`) fordi kostnadsstedet ikke finnes i
  T1. `E-2026/900009-1` med `0391` erstatter B. 2026-11-18 bruker bare `0391`
  og `1291`.
- Avvisningen kom som `FEILET` på `bestilling-status-v1` dagen etter
  (2026-11-18). En trigger i OeBS T1 var ikke oppdatert for kilden `EKSPBIST`,
  og det forsinket kvitteringen (Hilde-Mari, Slack 2026-11-18). Vi mottok
  `FEILET` i dev. Dette dekker også 1D.

## Feilsituasjoner

- **Polleren står fast på en rad.** Polleren tar eldste `PENDING`-rad først.
  Kan den ikke lese JSON-en (feil felt eller type), feiler den hvert 30. sekund på
  samme rad, og radene bak venter. Slett raden og legg inn en rettet versjon:
  `DELETE FROM oebs_outbox WHERE id = <id> AND status = 'PENDING';`
- **VALP avviser meldingen.** Valideringsfeil hos VALP (manglende kontering,
  ukjent arrangør, feil status på bestillingen) gir ingen `FEILET` tilbake til oss.
  VALP logger feilen og prøver på nytt. Vi ser det bare som manglende status. Be
  VALP sjekke loggene og fjerne meldingen hos seg.
- **OeBS avviser bestillingen.** OeBS sender kvittering med feilkode til VALP,
  og VALP publiserer `FEILET`. Vi logger `log.error`. Se *Avvisning i OeBS*.
- **Ingen status kommer tilbake.** Sjekk at VALP har publisert (logger hos VALP),
  at headeren `fagsystem=EKSPERTBISTAND` er satt, og at consumeren vår har startet
  (ingen `Kafka`-feil ved oppstart).
- **Consumeren står fast på en status-melding.** Apploggen viser `Status-melding
  bryter kontrakten` hvert 5. sekund for samme partisjon og offset. Finn meldingen
  og feilen i teamLogs under samme tekst, og rett speilingen etter VALP sin
  kontrakt. Etter deploy leser consumeren meldingen på nytt.
- **Noen deployer `main` til dev under testen.** Før denne PR-en er merget, stopper
  det polleren og status-consumeren. Rader blir liggende som `PENDING`, og status
  blir liggende på topicet til branchen er deployet igjen. Ingenting går tapt.
- **Et innvilget vedtak blir fattet i dev under testen.** Bestillingen sendes som
  vanlig. Den ryddes ikke av SQL-en over, siden den har et ekte saksnummer.

## Kontraktsavvik å følge opp

VALP endret `OpprettFaktura.Betalingsinformasjon.BBan` i navikt/mulighetsrommet#8527
(2026-11-10). `kid: Kid?` er erstattet med `referanse: Betalingsreferanse`
(`KidNummer` eller `MeldingTilMottaker`). VALP oversetter det gamle formatet i
`decodeOkonomiBestillingMelding`, men har en TODO om å fjerne oversettelsen. Vår
speiling i `OebsBestillingMelding.kt` og `OebsBestillingMeldingContractTest` har
fortsatt `kid`. Runde 2 bruker det gamle formatet, fordi polleren leser outboxen
med vår modell. Har VALP fjernet oversettelsen før runde 2, må vi oppdatere
speilingen og JSON-en i 2A først. Uansett oppdaterer vi speilingen før vi kobler
faktura til saksgangen.

## Kostnadssteder i OeBS

Vi sender det firesifrede enhetsnummeret fra Norg som `kostnadssted`. VALP
sender det uendret videre:

- `NavEnhetNummer` krever nøyaktig fire sifre (`^\d{4}$`).
- `OebsMeldingMapper` setter `kontor = bestilling.kostnadssted.value`.
- KDoc på `OebsBestillingMelding.kontor`: enheten «mappes til et kostnadssted
  innad i OeBS», og fagsystemet og OeBS må være enige om hvilke kostnadssteder
  som brukes.

Avklart på Slack 2026-11-17:

- Hilde-Mari (OeBS): fire sifre er riktig. OeBS gjør dem om til et sekssifret
  kostnadssted. Ofte er de fire første sifrene enhetsnummeret, men ikke alltid.
  Lista hun sendte, er de 863 aktive kostnadsstedene i T1 med seks sifre. Hun
  sender en liste med fire sifre senere.
- Sondre (VALP): tiltaksadministrasjon sender også fire sifre, og avklarer hver
  nye enhet med OeBS før den tas i bruk.

Vi kan derfor ikke lese av lista med seks sifre hvilke av enhetene våre som
virker. `1891` står som `189100` i lista, men ble avvist. `1291` står ikke som
`129100`, men ble `AKTIV`.

Hilde-Mari sjekket enhetene våre i OeBS 2026-11-17:

| Enhet | T1 | Prod |
|-------|----|------|
| 0291, 0391, 0491, 0691, 0891, 1091, 1191, 1291, 1591, 2103, 5772 | Finnes | Ikke sjekket |
| 1891 | Mangler | Aktiv |
| 1991 | Mangler | Ikke aktiv, trolig avsluttet februar 2020 |

`1991` står på lista vår over enheter som behandler ekspertbistand, men er
avsluttet i OeBS prod. Vi må finne ut om enheten fortsatt er i bruk i Norg, og
om saker for den skal belastes en annen enhet.

### Kostnadssted eller behandlende enhet

Hos oss er kostnadsstedet alltid `sak.behandlende_enhet`
(`BestillTilsagnIOebs`). I tiltaksadministrasjon velger saksbehandleren det,
med en foreslått verdi:

- Gjennomføringen har en ansvarlig enhet, valgt fra hvitlista. Hjelpeteksten
  sier at den «brukes som standard kostnadssted»
  (`GjennomforingFormDetaljer.tsx`).
- Nytt tilsagn fylles ut med `tilsagn?.kostnadssted ?: gjennomforing.ansvarligEnhet`
  (`TilsagnRoutesBeregning.kt`).
- Saksbehandleren kan bytte til en annen enhet i hvitlista `kostnadssted` (269
  enheter fordelt på region, `R__kostnadssted_innhold.sql`) via
  `VelgKostnadssted`. Tilskuddsbehandling bruker samme komponent.
- `TilsagnValidator` krever kostnadssted. Beslutteren kan returnere tilsagnet med
  årsaken `FEIL_KOSTNADSSTED`.
- Kostnadsstedet styrer også tilgang: bare beslutter ved tilsagnets kostnadssted
  kan godkjenne (`AdminUtbetalingService`).
- Tiltaksøkonomi (VALP) velger ingenting, men sender videre det den får.

KDoc hos VALP kaller feltet «Nav-enhet som skal belastes for bestillingen». Om
det er enheten som behandler ekspertbistand-saken, er et spørsmål om økonomi og
budsjett, ikke om integrasjonen. Det må avklares før bestilling kobles til
saksgangen.

### Før bestilling kobles til saksgangen

1. Avklar at behandlende enhet er enheten som skal belastes.
2. Avklar `1991`: er enheten i bruk, og hvilket kostnadssted skal saker for den
   belastes?
3. Bestill kostnadssted for 1891 i T1, og for 1991 i T1 og prod hvis den skal
   brukes. Få bekreftet de 11 andre enhetene i prod.
4. Vurder en hvitliste som i tiltaksadministrasjon, slik at en sak med ukjent
   enhet stopper før bestillingen sendes.

## Avvisning i OeBS

OeBS T1 avviste `E-2026/900006-1` 2026-11-17. Kvitteringen kom først
2026-11-18, fordi en trigger i T1 ikke var oppdatert for `EKSPBIST`. Da kom den
fram til oss som `FEILET`.

Avklart med Hilde-Mari (OeBS) på Slack 2026-11-18:

- Feilhåndteringen er lik i alle OeBS-miljøer.
- Alle feil havner i kvitteringen til VALP, med feilkode og feilmelding fra
  OeBS. Eksempel fra `E-2026/900006-1`: `feilKode` `PO_PDOI_CHARGE_FAILED`,
  «Feil: Generering av Kostnadskonto mislyktes.».
- OeBS kan gi flere feilmeldinger for samme feil. Kvitteringen har bare én.
- Avhengig av feilen ber OeBS oss sende meldingen på nytt etter retting, eller
  OeBS retter selv. Kjersti Pedersen (OeBS) vet mer om hvor ofte hver av delene
  skjer.
- De vanligste feilene i prod gjelder kontering eller leverandørinformasjon.
  Hilde-Mari sendte eksempler på feilmeldinger som bilde på Slack.

Åpent:

- Når OeBS retter en feilet bestilling selv, kommer det en ny kvittering når den
  er opprettet? Hilde-Mari skal sjekke. Kommer den, overskriver consumeren
  statusen, og `trenger_manuell_oppfolging` blir `false`.
- Hvordan vi sender en bestilling på nytt etter `FEILET`, når OeBS ber om det.
  Samme bestillingsnummer gir trolig `PO_PDOI_DOC_NUM_UNIQUE` hvis OeBS allerede
  har dokumentet.

## Avgrensning

- Ingen endring i prod. Polleren og status-consumeren startes ikke der.
- Faktura, annullering og oppgjør kobles ikke til hendelser i saksgangen.
- Ingen visning av OeBS-status i saksbehandlingsløsningen.

## Personvern og sikkerhet

Testmeldingene har orgnr, Nav-enhet, kontonummer til en virksomhet og
test-identer. Det er ingen fødselsnumre eller navn. Ingen nye logglinjer. Vi
publiserer på vår egen topic med eksisterende ACL og leser VALP sine topics med
ACL fra navikt/mulighetsrommet#8572. Skrivetilgangen til databasen er personlig og
trekkes tilbake etter testen.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | Starter `startOebsProsessering` i dev |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/Oebs.kt` | KDoc |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/integration/TiltaksokonomiConsumer.kt` | KDoc. Ved kontraktbrudd logger den `log.error` og `teamLog.error` og kaster, så meldingen prøves på nytt |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/README.md` | Status for wiring og eksterne avhengigheter |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/integration/OebsStatusMelding.kt` | `FakturaStatus.statusSistOppdatert` (funn i runde 2) |
| `backend/src/test/kotlin/no/nav/ekspertbistand/oebs/OebsStatusMeldingTest.kt` | Kontraktstest med VALP sitt feltnavn |
| `specifications/test_oebs_integrasjon_dev.md` | Ny |

## Ferdig når

- [x] Polleren og status-consumeren kjører i dev og ikke i prod

Runde 1:

- [x] 1A: bestillingen er `PUBLISHED` og har status `AKTIV`
- [x] 1B: ny sending av samme bestilling gir ny `AKTIV`-status og ingen endring hos VALP
- [x] 1C: den andre bestillingen har status `ANNULLERT`
- [x] Hver sendt melding har en rad i `oebs_sendt_melding`, og hvert svar har en rad i `oebs_mottatt_status`

Runde 2:

- [x] 2A: fakturaen har fått kvittering fra OeBS
- [x] 2B: bestillingen har status `OPPGJORT`

Repetisjon:

- [ ] 2026-11-17: A er `OPPGJORT` og B er `ANNULLERT`, med `E-2026/900009-1` i stedet for den avviste bestillingen
- [ ] 2026-11-18: `E-2026/900007-1` er `OPPGJORT`, og `E-2026/900008-1` er `ANNULLERT`

Etter siste repetisjon:

- [ ] Testdataene er slettet i dev, og skrivetilgangen er trukket tilbake
- [ ] VALP og OeBS har fått beskjed om at testen er ferdig, og hvilke numre vi brukte

Før bestilling kobles til saksgangen:

- [ ] Avklart at behandlende enhet er enheten som skal belastes
- [x] OeBS har sjekket de 13 enhetene i T1: 1891 og 1991 mangler
- [x] OeBS har sjekket 1891 og 1991 i prod: 1891 er aktiv, 1991 er avsluttet
- [ ] Avklart om 1991 skal brukes, og hvilket kostnadssted saker for den belastes
- [ ] Kostnadssted for 1891 er bestilt i T1 (og 1991 i T1 og prod hvis den skal brukes)
- [ ] OeBS har bekreftet de 11 andre enhetene i prod
- [x] Avklart at OeBS sender `FEILET` når den avviser en bestilling (mottatt i dev 2026-11-18)
- [ ] Avklart om OeBS sender ny kvittering når den retter en feilet bestilling selv
- [ ] Avklart hvordan vi sender en bestilling på nytt etter `FEILET`
