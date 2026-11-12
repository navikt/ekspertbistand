# Manuell test av OeBS-integrasjonen i dev

## Mål

Vi bekrefter at meldinger fra `oebs_outbox` når OeBS T1 via tiltaksøkonomi (Team
VALP), og at statusen kommer tilbake til oss. Vi legger testmeldingene rett i
outboxen med SQL i stedet for å gå gjennom saksbehandlingen. Testen dekker alle
fire meldingstypene: `BESTILLING`, `FAKTURA`, `GJOR_OPP_BESTILLING` og
`ANNULLERING`.

Når testen er godkjent, sletter vi testdataene i dev. Deretter kobler vi faktura,
annullering og oppgjør til hendelser i saksgangen. Prod er uendret.

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
| Polleren startes bare i dev, i kode | Samme mønster som `basedOnEnvSuspending` ellers i `main()`. Når testen er godkjent, er polleren allerede på i dev for neste steg (koble til saksgangen). Prod åpnes i et eget kort. |
| Testmeldingene legges rett i `oebs_outbox` | Vi tester integrasjonen uten å være avhengige av saksbehandlingsflyten. Polleren, revisjonssporet og status-consumeren kjører som vanlig. |
| Saksnummer `2026/9000001` og oppover | `saksnummer_seq` starter på 1 000 000 og når aldri 9 000 000. Testnumrene kolliderer ikke med ekte saker i VALP og OeBS T1, der testdataene blir liggende. |
| Arrangør `925236594`, kostnadssted `0315`, kontonummer `10002427740`, identer `Z993433` og `Z990079` | Verdiene står i VALP sine eksempelmeldinger fra dev (`OebsBestillingMeldingContractTest`). Vi vet derfor at de går gjennom Brreg/Ereg-oppslaget og konteringen hos VALP. Ingen personopplysninger. |
| Periode `[2026-11-16, 2026-12-16)` | Konteringen hos VALP gjelder fra 2026-11-01. Perioden spenner over to måneder, så fakturaen treffer en bestillingslinje. |

## Forutsetninger

Gå gjennom listen før deploy:

- [ ] **OeBS** har bekreftet at det funksjonelle og tekniske oppsettet mot T1 er klart.
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

### 3. Send testmeldingene

Hver `INSERT` henter `bestillingsnummer` (Kafka-nøkkelen) og `meldingstype` fra
JSON-en. Da kan ikke kolonnene og meldingen spriker. Polleren leser
`melding_json` med vår egen modell og serialiserer den på nytt. JSON-en må derfor
følge `OebsBestillingMelding`, ikke VALP sin modell (se *Kontraktsavvik*).

Rekkefølgen er viktig. VALP godtar faktura, oppgjør og annullering bare når
bestillingen har status `AKTIV`. Vent på forventet status før neste steg.

#### T1: Bestilling `E-2026/9000001-1`

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/9000001-1",
    "regnskapskonto": "TILTAK_DRIFTSTILSKUDD",
    "artskonto": "EKSPERTBISTAND",
    "arrangor": { "type": "NORSK", "organisasjonsnummer": "925236594" },
    "kostnadssted": "0315",
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

#### T2: Samme bestilling på nytt (idempotens)

Kjør `INSERT`-en fra T1 en gang til når statusen er `AKTIV`.

Forventet: en ny rad i `oebs_sendt_melding` og en ny statusmelding med `AKTIV` i
`oebs_mottatt_status`. VALP oppretter ingenting nytt.

#### T3: Faktura `E-2026/9000001-1-1`

Kjør når T1 har status `AKTIV`.

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "FAKTURA",
  "payload": {
    "fakturanummer": "E-2026/9000001-1-1",
    "bestillingsnummer": "E-2026/9000001-1",
    "betalingsinformasjon": { "type": "BBAN", "kontonummer": "10002427740", "kid": null },
    "belop": 400,
    "periode": { "start": "2026-11-16", "slutt": "2026-12-01" },
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T13:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T13:05:00Z",
    "gjorOppBestilling": false,
    "beskrivelse": "Ekspertbistand test E-2026/9000001-1-1",
    "valuta": "NOK"
  }
}'::jsonb)) AS v(m);
```

`beskrivelse` må være satt. VALP gjør `"kid": null` om til
`MeldingTilMottaker` med `beskrivelse` som melding.

Forventet: fakturastatus `SENDT`, deretter `IKKE_BETALT` eller `FULLT_BETALT` når
OeBS kvitterer. Statusen lagres i `oebs_bestilling_status` på fakturanummeret.

#### T4: Gjør opp `E-2026/9000001-1`

Kjør når fakturaen i T3 har fått kvittering (ikke lenger `SENDT`).

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "GJOR_OPP_BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/9000001-1",
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T14:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T14:05:00Z"
  }
}'::jsonb)) AS v(m);
```

Forventet: bestillingsstatus `OPPGJORT`. VALP sender oppgjøret som en faktura
`E-2026/9000001-1-X` til OeBS, men publiserer ikke fakturastatus for den.

#### T5: Bestilling `E-2026/9000002-1` og annullering

T5 er uavhengig av T1–T4 og kan kjøres samtidig. Send bestillingen:

```sql
INSERT INTO oebs_outbox (bestillingsnummer, meldingstype, melding_json)
SELECT m -> 'payload' ->> 'bestillingsnummer', m ->> 'type', m
FROM (VALUES ('{
  "type": "BESTILLING",
  "payload": {
    "bestillingsnummer": "E-2026/9000002-1",
    "regnskapskonto": "TILTAK_DRIFTSTILSKUDD",
    "artskonto": "EKSPERTBISTAND",
    "arrangor": { "type": "NORSK", "organisasjonsnummer": "925236594" },
    "kostnadssted": "0315",
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
    "bestillingsnummer": "E-2026/9000002-1",
    "behandletAv": { "type": "NAV_ANSATT", "navIdent": "Z993433" },
    "behandletTidspunkt": "2026-11-12T15:00:00Z",
    "besluttetAv": { "type": "NAV_ANSATT", "navIdent": "Z990079" },
    "besluttetTidspunkt": "2026-11-12T15:05:00Z"
  }
}'::jsonb)) AS v(m);
```

Forventet: `ANNULLERING_SENDT`, deretter `ANNULLERT`.

#### T6 (valgfri): Feilet bestilling

Vi har ingen kjent måte å få `FEILET` fra OeBS på uten å avtale det med OeBS. Et
ugyldig kostnadssted (for eksempel `9999` i en kopi av T1 med
`E-2026/9000003-1`) gir sannsynligvis avvist kvittering. Kjør bare hvis OeBS og
VALP er enige.

Forventet: `trenger_manuell_oppfolging = true` i `oebs_bestilling_status` og
`log.error` «Status for E-2026/9000003-1 er FEILET og krever manuell oppfølging».
Detaljene står i teamLogs.

### 4. Følg med

Etter hver `INSERT`:

```sql
-- Utgående: forventer PUBLISHED innen noen sekunder
SELECT id, bestillingsnummer, meldingstype, status, attempts, updated_at
FROM oebs_outbox
WHERE bestillingsnummer LIKE 'E-2026/900000%'
ORDER BY id;

-- Revisjonsspor for det vi sendte, med Kafka-koordinater
SELECT bestillingsnummer, meldingstype, kafka_partition, kafka_offset, sendt_tidspunkt
FROM oebs_sendt_melding
WHERE bestillingsnummer LIKE 'E-2026/900000%'
ORDER BY id;

-- Alle svar fra VALP
SELECT bestillingsnummer, kafka_topic, raw_json, mottatt_tidspunkt
FROM oebs_mottatt_status
WHERE bestillingsnummer LIKE 'E-2026/900000%'
ORDER BY id;

-- Siste status per bestilling og faktura
SELECT bestillingsnummer, status, trenger_manuell_oppfolging, mottatt_tidspunkt
FROM oebs_bestilling_status
WHERE bestillingsnummer LIKE 'E-2026/900000%'
ORDER BY bestillingsnummer;
```

I loggene til `ekspertbistand-backend` (dev):

- `Publiserte melding til fager.ekspertbistand.bestillinger-v1` når en rad er sendt.
- `Publisering av outbox-melding feilet` betyr at raden blir liggende og prøves
  igjen hvert 30. sekund (se *Feilsituasjoner*).
- `Ukjent/ugyldig status-melding` i teamLogs betyr at statuskontrakten ikke stemmer.

Kvitteringen fra OeBS kan ta tid. Avtal med OeBS hvor lenge vi venter før vi spør.

### 5. Rydd opp

Når testen er godkjent, sletter vi testdataene i dev. Revisjonssporet er
append-only i prod, men i dev er dette testdata vi selv har lagt inn.

```sql
BEGIN;
DELETE FROM oebs_outbox            WHERE bestillingsnummer LIKE 'E-2026/900000%';
DELETE FROM oebs_sendt_melding     WHERE bestillingsnummer LIKE 'E-2026/900000%';
DELETE FROM oebs_mottatt_status    WHERE bestillingsnummer LIKE 'E-2026/900000%';
DELETE FROM oebs_bestilling_status WHERE bestillingsnummer LIKE 'E-2026/900000%';
COMMIT;
```

Testdataene blir liggende hos VALP og i OeBS T1. Gi VALP og OeBS beskjed om at
testen er ferdig og hvilke numre vi brukte. Til slutt trekker du tilbake
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
fortsatt `kid`. Vi oppdaterer speilingen før vi kobler faktura til saksgangen.
Denne testen bruker det gamle formatet, fordi polleren leser outboxen med vår
modell.

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
- [ ] T1: bestillingen er `PUBLISHED` og har status `AKTIV`
- [ ] T2: ny sending av samme bestilling gir ny `AKTIV`-status og ingen endring hos VALP
- [ ] T3: fakturaen har fått kvittering fra OeBS
- [ ] T4: bestillingen har status `OPPGJORT`
- [ ] T5: den andre bestillingen har status `ANNULLERT`
- [ ] Hver sendt melding har en rad i `oebs_sendt_melding`, og hvert svar har en rad i `oebs_mottatt_status`
- [ ] Testdataene er slettet i dev, og skrivetilgangen er trukket tilbake
- [ ] VALP og OeBS har fått beskjed om at testen er ferdig
