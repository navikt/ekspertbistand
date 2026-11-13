# Manuell test av OeBS-integrasjonen i dev

## Mål

Vi bekrefter at meldinger fra `oebs_outbox` når OeBS T1 via tiltaksøkonomi (Team
VALP), og at statusen kommer tilbake til oss. Vi legger testmeldingene rett i
outboxen med SQL i stedet for å gå gjennom saksbehandlingen.

Testen går i to runder:

1. **Runde 1** tester `BESTILLING` og `ANNULLERING`. Den kjøres nå.
2. **Runde 2** tester `FAKTURA` og `GJOR_OPP_BESTILLING`. Den venter til OeBS
   sier at de er klare.

Når en runde er godkjent, sletter vi testdataene i dev. Deretter kobler vi de
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
| Kostnadssted `0391` (1A) og `1291` (1C) | Kostnadsstedet er `sak.behandlende_enhet`. Bare disse enhetene behandler ekspertbistand: 0291, 0391, 0491, 0691, 0891, 1091, 1191, 1291, 1591, 1891, 1991, 2103 og 5772. Med to ulike enheter ser vi at OeBS tar imot mer enn én. |
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
- `Ukjent/ugyldig status-melding` i teamLogs betyr at statuskontrakten ikke stemmer.

Kvitteringen fra OeBS kan ta tid. Avtal med OeBS hvor lenge vi venter før vi spør.

### 6. Rydd opp

Når en runde er godkjent, sletter vi testdataene i dev. Revisjonssporet er
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

## Feilsituasjoner

- **Polleren står fast på en rad.** Polleren tar eldste `PENDING`-rad først.
  Kan den ikke lese JSON-en (feil felt eller type), feiler den hvert 30. sekund på
  samme rad, og radene bak venter. Slett raden og legg inn en rettet versjon:
  `DELETE FROM oebs_outbox WHERE id = <id> AND status = 'PENDING';`
- **VALP avviser meldingen.** Valideringsfeil hos VALP (manglende kontering,
  ukjent arrangør, feil status på bestillingen) gir ingen `FEILET` tilbake til oss.
  VALP logger feilen og prøver på nytt. Vi ser det bare som manglende status. Be
  VALP sjekke loggene og fjerne meldingen hos seg.
- **Ingen status kommer tilbake.** Sjekk at VALP har publisert (logger hos VALP),
  at headeren `fagsystem=EKSPERTBISTAND` er satt, og at consumeren vår har startet
  (ingen `Kafka`-feil ved oppstart).
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
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/integration/TiltaksokonomiConsumer.kt` | KDoc |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/README.md` | Status for wiring og eksterne avhengigheter |
| `specifications/test_oebs_integrasjon_dev.md` | Ny |

## Ferdig når

- [ ] Polleren og status-consumeren kjører i dev og ikke i prod

Runde 1:

- [ ] 1A: bestillingen er `PUBLISHED` og har status `AKTIV`
- [ ] 1B: ny sending av samme bestilling gir ny `AKTIV`-status og ingen endring hos VALP
- [ ] 1C: den andre bestillingen har status `ANNULLERT`
- [ ] Hver sendt melding har en rad i `oebs_sendt_melding`, og hvert svar har en rad i `oebs_mottatt_status`
- [ ] Testdataene er slettet i dev, og skrivetilgangen er trukket tilbake
- [ ] VALP og OeBS har fått beskjed om at runden er ferdig

Runde 2 (når OeBS er klare):

- [ ] 2A: fakturaen har fått kvittering fra OeBS
- [ ] 2B: bestillingen har status `OPPGJORT`
- [ ] Testdataene er slettet i dev, og skrivetilgangen er trukket tilbake
- [ ] VALP og OeBS har fått beskjed om at runden er ferdig
