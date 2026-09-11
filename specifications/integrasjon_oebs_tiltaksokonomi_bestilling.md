# Integrere med OeBS for å sette av penger og utbetale (via Tiltaksøkonomi)

Trello: https://trello.com/c/m53Ud4QY/565-integrere-med-oebs-for-%C3%A5-sette-av-penger-og-utbetale
Kort-ID: `6a8d585e6291d224000393bd`

## Mål

Ekspertbistand skal kunne **sette av penger (bestilling/tilsagn)**, **utbetale (faktura)**,
og **annullere / gjøre opp** bestillinger hos OeBS. Dette gjøres ikke direkte mot OeBS, men
via Team VALP sin tjeneste [`mulighetsrommet-tiltaksokonomi`](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi),
som er et Anti-Corruption Layer mot OeBS PO/AP.

Ekspertbistand skal stå som **eget kildesystem** i OeBS, med **egen nummerserie**, slik at
tilsagn og utbetalinger fra oss er sporbare tilbake til vårt fagsystem.

Leveransen er en **selvstendig OeBS-/tiltaksøkonomi-klient** i backend med et internt API som
lar resten av applikasjonen: bestille (sette av penger), utbetale (faktura), motta status, og
annullere / gjøre opp. Klienten **produserer** meldinger på vår egen Kafka-topic og **lytter**
på VALP sine status-topics. Selve domene-**eventene** som utløser en bestilling/utbetaling, og
koblingen til saksbehandlingsflyten, er **utenfor scope** i denne omgang (se «Avgrensning»).

## Kildegrunnlag

Kortet har **ingen kommentarer og ingen sjekklister** (verifisert 2026-09-11 via
`card:comments` / `card:checklists`). Grunnlaget er derfor kortbeskrivelsen, supplert med
**avklaringer gitt av Ken Gullaksen 2026-09-11** (svar på de opprinnelige åpne spørsmålene —
se «Avklaringer»). Der noe fortsatt er uavklart, er det markert eksplisitt i stedet for å
gjettes.

## Bakgrunn

### Dagens tilstand i ekspertbistand

- Ekspertbistand oppretter i dag sak og tiltaksgjennomføring i **Arena**, og *lytter* på
  Arena sine Kafka-topics for tilsagnsbrev og endringer
  (`backend/src/main/kotlin/no/nav/ekspertbistand/arena/ArenaTilsagnsbrevProcessor.kt`,
  topic `teamarenanais.aapen-arena-tilsagnsbrevgodkjent-v1[-q2]`). Selve
  økonomien (avsetning/utbetaling) håndteres altså i dag av Arena, ikke av oss.
- Det finnes **kun Kafka-consumere**, ingen Kafka-*producer* i kodebasen. Consumer-oppsettet
  ligger i `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Kafka.kt`
  (`CoroutineKafkaConsumer`, `StringDeserializer`, SSL via `KAFKA_*`-env). En producer må
  bygges fra bunnen.
- Vi har et **DB-basert event-system (outbox-lignende)**: `event_queue` → handler →
  `event_log`. `EventData` er en sealed interface med `aggregateRootId`
  (`backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt`). Handlere registreres i
  `configureEventHandlers()` og kjøres av `EventManager` med at-least-once + idempotens.
  Sideeffekter mot eksterne systemer gjøres i handlere (se f.eks.
  `event/handlers/LagreTilsagnsData.kt`).
- Domenemodellen har allerede `TilsagnData` og lagrede tilsagn
  (`backend/src/main/kotlin/no/nav/ekspertbistand/tilsagndata/`), men **ingen** referanse til
  OeBS, VALP, tiltaksøkonomi eller «bestilling».
- App: `ekspertbistand-backend`, namespace `fager`, kafka-pool `nav-dev` / `nav-prod`
  (`nais/dev-gcp-backend.yaml`, `nais/prod-gcp-backend.yaml`).

### Team VALP sin løsning (verifisert kontrakt)

`mulighetsrommet-tiltaksokonomi` konsumerer bestillinger/fakturaer fra én felles Kafka-topic,
sender dem videre til OeBS via HTTP/JSON, og publiserer status tilbake på egne status-topics.

- **VALP sin inn-topic (fagsystem → tiltaksøkonomi):**
  `team-mulighetsrommet.tiltaksokonomi.bestillinger-v1` (dev og prod).
  Kilde: `mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/ApplicationConfig{Dev,Prod}.kt`
  og topic-manifest `iac/kafka-topics/{dev,prod}/tiltaksokonomi.bestillinger-v1.yaml`.
- **Status-topics tilbake (tiltaksøkonomi → fagsystem):**
  `team-mulighetsrommet.tiltaksokonomi.bestilling-status-v1` og
  `…faktura-status-v1`.
- **Meldingsformat** (kotlinx.serialization JSON), fra
  [`OkonomiBestillingMelding.kt`](https://github.com/navikt/mulighetsrommet/blob/main/common/tiltaksokonomi-client/src/main/kotlin/no/nav/tiltak/okonomi/OkonomiBestillingMelding.kt):

  ```kotlin
  sealed class OkonomiBestillingMelding {         // diskriminator: "BESTILLING" / "ANNULLERING" / "FAKTURA" / "GJOR_OPP_BESTILLING"
      data class Bestilling(val payload: OpprettBestilling) : OkonomiBestillingMelding()
      // Annullering, Faktura, GjorOppBestilling …
  }

  data class OpprettBestilling(
      val bestillingsnummer: String,          // <-- vår nummerserie havner her
      val tilskuddstype: Tilskuddstype,       // enum – se under
      val tiltakskode: Tiltakskode,           // enum – se under
      val arrangor: Arrangor,                 // Norsk(orgnr) | Utenlandsk(orgnr, navn, adresse …)
      val kostnadssted: NavEnhetNummer,
      val avtalenummer: String?,
      val belop: Int,
      val periode: Periode,                   // [fom, tom)
      val behandletAv: OkonomiPart,           // NavAnsatt(navIdent) | System(kilde)
      val behandletTidspunkt: Instant,
      val besluttetAv: OkonomiPart,
      val besluttetTidspunkt: Instant,
      val valuta: Valuta,
  )
  ```

- **Enum-verdiene finnes IKKE i dag** (verifisert mot main):
  - `Tilskuddstype` = `TILTAK_DRIFTSTILSKUDD`, `TILTAK_INVESTERINGER`, `TILTAK_OPPLAERING_TILSKUDD`.
    **`TILTAK_EKSPERTBISTAND` mangler.**
  - `Tiltakskode` (`common/domain/.../Tiltakskoder.kt`) inneholder ikke `EKSPERTBISTAND`.
  - `OkonomiSystem` (kilde i `OkonomiPart.System`) har kun `TILTAKSADMINISTRASJON` — **ingen
    kilde for ekspertbistand.**

  Dette bekrefter kortets punkt om at Team VALP «vil legge i sitt system» tiltakskode
  `EKSPERTBISTAND`, tilskuddstype `TILTAK_EKSPERTBISTAND`, periode og kontoene. **Vi kan ikke
  publisere en gyldig melding før VALP har lagt inn disse verdiene** — hard avhengighet.

### Verdier som skal til Team VALP (fra kortet)

```json
{
  "tiltakskode": "EKSPERTBISTAND",
  "tilskuddstype": "TILTAK_EKSPERTBISTAND",
  "periode": "[2026-11-01,2099-01-01)",
  "statlig_regnskapskonto": "265076100000",
  "statlig_artskonto": "874007734610"
}
```

## Beslutninger

Fra kortbeskrivelsen (1–4) og avklaringer 2026-09-11 (5–10):

1. **Integrasjon via VALP, ikke direkte mot OeBS.** Vi bygger ikke egen OeBS-klient mot OeBS;
   vi publiserer meldinger på Kafka i formatet `OkonomiBestillingMelding`, og VALP snakker med
   OeBS.
2. **Egen skrive-topic i `fager`-namespace.** Vi skriver **ikke** direkte til VALP sin
   `tiltaksokonomi.bestillinger-v1`. Vi eier en egen topic og publiserer dit. Meldingsformatet
   er identisk med VALP sitt (`OkonomiBestillingMelding`).
3. **Ekspertbistand som eget kildesystem med egen nummerserie.** Generelt format:
   `<fagsystembokstav>-<fagsystem sak-id>-<inkrementerende løpenummer per tilsagn per sak>`.
4. **VALP legger inn** tiltakskode `EKSPERTBISTAND`, tilskuddstype `TILTAK_EKSPERTBISTAND`,
   periode og de statlige kontoene i sitt system.
5. **Fagsystembokstav = `E` midlertidig, som kodekonstant.** Endelig bokstav/kildenavn venter
   fortsatt på svar fra OeBs-teamet; vi bruker `E` inntil videre, definert som en konstant i
   koden (ikke ekstern config). Vi deployer hyppig, så en kodeendring er billigere enn å bære
   verdien i config; ekstern config forbeholdes plattform-gitte verdier og hemmeligheter.
6. **Selvstendig klient med internt API.** Vi lager en lokal tiltaksøkonomi-klient som
   eksponerer operasjonene bestille / utbetale / annullere / gjøre opp / motta status. De
   domene-eventene som utløser operasjonene er **ikke** en del av denne leveransen.
7. **VALP abonnerer på vår topic.** Team VALP setter opp konsum av vår topic og får `read`-ACL.
8. **Ingen gjenbruk av `tiltaksokonomi-client`.** Vi speiler meldingsmodellen lokalt, men kan
   bruke VALP sin kode som inspirasjon.
9. **Alle operasjoner i denne fasen**, inkludert faktura/utbetaling og statuslytting. Kun
   eventene og koblingen til saksbehandlingsflyten er utenfor scope.
10. **Feilede utbetalinger/bestillinger skal gi tydelig signal** som kan formidles til manuell
    oppfølging (se Endring §6 og Kanttilfeller).
11. **Kafka-producer implementeres med outbox-mønster, innkapslet i klienten** (i scope nå).
    Kallere skriver til DB-outbox i egen transaksjon; en bakgrunnspoller publiserer til Kafka.
    Ingen hard avhengighet til Kafka-oppetid, og Kafka-feilhåndtering lekker ikke innover.

## Avgrensning

- **I scope (denne leveransen):** en selvstendig tiltaksøkonomi-klient med internt API for
  alle operasjoner — bestilling (sette av penger), faktura (utbetaling), annullering, gjøre
  opp — som **produserer** på vår egen topic; **konsum** av VALP sine status-topics
  (`bestilling-status-v1`, `faktura-status-v1`); nummerserie-generering; nais-oppsett (topic +
  ACL for VALP + kafka-producer/consumer); pålitelig publisering; og tydelig signalering av
  feilede operasjoner for manuell oppfølging.
- **Utenfor scope:** domene-**eventene** som utløser bestilling/utbetaling, og koblingen mot
  saksbehandlingsflyten. Klienten skal ha et API som saksbehandlingsflyten *senere* kaller,
  men selve utløsende hendelser og forretningslogikk rundt når/hvor mye/for hvem lages ikke nå.

## Endring

> Kontrakten mot VALP er nå avklart. Eneste gjenstående eksterne verdi er endelig
> fagsystembokstav/kildenavn fra OeBs; inntil videre brukes `E`, parametrisert (§4), så
> implementasjon kan starte uten å blokkeres.

### 1. Kafka-topic vi eier (`fager`-namespace)

Nytt topic-manifest, deployes på samme måte som app-manifestene via
`nais/deploy/actions/deploy@v2` (jf. `.github/workflows/cicd-backend.yaml`, `RESOURCE:`).
Ny fil, f.eks. `nais/dev-gcp-topic-bestillinger.yaml` (+ prod):

```yaml
apiVersion: kafka.nais.io/v1
kind: Topic
metadata:
  name: ekspertbistand.bestillinger-v1   # fullt navn: fager.ekspertbistand.bestillinger-v1
  namespace: fager
  labels:
    team: fager
spec:
  pool: nav-dev            # nav-prod i prod-fila
  config:
    cleanupPolicy: delete
    minimumInSyncReplicas: 2
    partitions: 1
    replication: 3
    retentionHours: 2160   # 90 dager, som VALP sin topic
  acl:
    - team: fager
      application: ekspertbistand-backend
      access: write
    - team: team-mulighetsrommet
      application: tiltaksokonomi
      access: read         # VALP abonnerer på vår topic
```

Topicen bærer **alle** utgående meldinger (bestilling, faktura, annullering, gjør opp), på
samme måte som VALP sin `bestillinger-v1` bærer hele `OkonomiBestillingMelding`-hierarkiet.
Deploy-steg for topic-manifestene må legges til i `cicd-backend.yaml` (og evt. manuell
deploy-workflow).

### 2. Kafka-producer med outbox (ny)

Producer i `infrastruktur/` (analogt med eksisterende consumer-oppsett), med
`KafkaProducer<String, String>`, `StringSerializer`, `acks=all`, `enable.idempotence=true`,
og SSL fra samme `KAFKA_KEYSTORE_PATH` / `KAFKA_TRUSTSTORE_PATH` / `KAFKA_CREDSTORE_PASSWORD`
/ `KAFKA_BROKERS`-env som consumeren bruker. Key = `bestillingsnummer` (stabil nøkkel per
tilsagn, gir ordering per bestilling i én partisjon; faktura/annullering bruker samme key som
sin bestilling).

Producer-en kalles **aldri direkte** av klient-API-et. All publisering går via en **outbox**
(se §5): kalleren skriver meldingen til en DB-tabell i samme transaksjon som sin egen
tilstandsendring, og en bakgrunnspoller drenerer outboxen til Kafka. Dette fjerner den harde
avhengigheten til Kafka-oppetid — hvis Kafka er nede, ligger meldingen trygt i outboxen og
publiseres når Kafka er tilbake. Kafka-feilhåndtering (retry, backoff) holdes dermed **inne i
poller-en** og lekker ikke ut til kalleren.

### 3. Meldingsmodell (speiles lokalt)

Vi speiler `OkonomiBestillingMelding`-hierarkiet lokalt i ny pakke (ikke avhengighet til VALP,
jf. beslutning 8), med `@SerialName`-diskriminatorer som matcher VALP eksakt: `BESTILLING`,
`FAKTURA`, `ANNULLERING`, `GJOR_OPP_BESTILLING`, og payload-klassene `OpprettBestilling`,
`OpprettFaktura`, `AnnullerBestilling`, `GjorOppBestilling` med feltnavn/typer identisk med
[VALP sin definisjon](https://github.com/navikt/mulighetsrommet/blob/main/common/tiltaksokonomi-client/src/main/kotlin/no/nav/tiltak/okonomi/OkonomiBestillingMelding.kt).
Kilde-verdien (`OkonomiPart.System(kilde)`) settes til ekspertbistand-kilden (avventer OeBs,
§4). En kontrakttest bør verifisere at vår serialiserte JSON matcher VALP sitt skjema.

### 4. Nummerserie

Egen tabell + generator som gir `<fagsystembokstav>-<sak-id>-<løpenr per tilsagn per sak>`.
Fagsystembokstaven er **`E` midlertidig** og defineres som en **kodekonstant** (ikke ekstern
config — vi deployer ofte, og config forbeholdes plattform-verdier og hemmeligheter); endelig
verdi fra OeBs settes med en kodeendring. Løpenummeret må være **transaksjonelt unikt og
monotont per sak**, og genereringen må være idempotent slik at en retry ikke lager to
bestillingsnumre for samme tilsagn. 🔴 Rød sone.

### 5. Klient med internt API og innkapslet outbox

Ny klient (f.eks. `oebs/TiltaksokonomiClient`) som eksponerer operasjonene mot resten av
appen — konseptuelt:

```kotlin
interface TiltaksokonomiClient {
    // Skriver melding til outbox i den medsendte transaksjonen — ingen Kafka-kall her.
    fun JdbcTransaction.opprettBestilling(...): Bestillingsnummer   // sette av penger
    fun JdbcTransaction.opprettFaktura(...)                          // utbetale
    fun JdbcTransaction.annuller(bestillingsnummer: Bestillingsnummer)
    fun JdbcTransaction.gjorOpp(bestillingsnummer: Bestillingsnummer)
}
```

**Outbox-mønsteret er innkapslet i klienten** og er i scope nå:

1. **Skriv (transaksjonelt):** Et klient-kall serialiserer riktig `OkonomiBestillingMelding`
   og skriver den til en **outbox-tabell** i *samme* DB-transaksjon som kalleren allerede er i.
   Enten atomisk eller ingenting — ingen Kafka-kall på denne veien, så en kaller kan aldri bli
   blokkert eller feile pga. Kafka.
2. **Publiser (asynkront):** En bakgrunnspoller i klienten leser upubliserte outbox-rader
   (`FOR UPDATE SKIP LOCKED`, jf. eksisterende `event_queue`-mønster), publiserer til vår topic
   via producer §2, og markerer raden som publisert først etter `acks=all`. At-least-once;
   idempotens sikres av stabilt `bestillingsnummer` som key + OeBS sin duplikatsjekk.
3. **Feil bæres ikke innover:** Retry/backoff mot Kafka håndteres i poller-en. Kafka nede =
   meldinger hoper seg opp i outboxen og drenerer når Kafka er tilbake; kallerens flyt påvirkes
   ikke.

Vi kan gjenbruke det eksisterende kø-mønsteret (`event_queue` → poller → `event_log` med
`SKIP LOCKED`) som forbilde, men outboxen for OeBS-meldinger bør være **klientens egen tabell**
slik at ansvaret er innkapslet i `oebs/`-pakken, ikke blandet med domene-eventene.

**De domene-eventene som kaller dette API-et er utenfor scope** (beslutning 6/9) — API-et er
inngangen saksbehandlingsflyten kobles på senere.

### 6. Statuslytting + feilhåndtering

Ny consumer (gjenbruker `CoroutineKafkaConsumer`) på VALP sine status-topics
`team-mulighetsrommet.tiltaksokonomi.bestilling-status-v1` og `…faktura-status-v1`. Status
lagres på vår side og eksponeres via API-et. **Feilede bestillinger/utbetalinger må gi et
tydelig, synlig signal** (egen status/tabellflagg + metrikk + logg uten PII) som kan plukkes
opp for **manuell oppfølging** — ikke svelges stille. Dette krever read-ACL fra VALP på
status-topicene (koordineres med Team VALP).

### 7. AccessPolicy

`nais/{dev,prod}-gcp-backend.yaml`: å produsere til egen Aiven-topic krever ikke ny
`accessPolicy`-regel (Aiven-ACL styres av topic-manifestet). For å **lese** VALP sine
status-topics må `tiltaksokonomi`/VALP gi `ekspertbistand-backend` read-ACL på disse.

## Filer som berøres (foreløpig)

- `nais/dev-gcp-topic-bestillinger.yaml`, `nais/prod-gcp-topic-bestillinger.yaml` — nye
  Topic-manifest.
- `.github/workflows/cicd-backend.yaml` — deploy-steg for topic-manifestene.
- `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Kafka.kt` (eller ny
  `KafkaProducer.kt`) — producer-oppsett.
- Ny pakke, f.eks. `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/` — speilet
  meldingsmodell, nummerserie, `TiltaksokonomiClient` (API), **outbox + poller**,
  status-consumer og feilhåndtering/-signalering.
- `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` /
  `configureEventHandlers()` — oppstart av outbox-poller og status-consumer, DI-registrering.
- Ny Flyway-migrering under `backend/src/main/resources/db/migration/` — nummerserie-tabell,
  **outbox-tabell** og status/feil-tabell.

## Kanttilfeller og feilmodeller

- **Feilede bestillinger/utbetalinger (eksplisitt krav):** OeBS kan avvise en operasjon (feil
  konfig, ugyldig tilsagnsår `PO_PDOI_INVALID_PROJ_INFO`, duplikat `DUPLICATE INVOICE NUMBER`,
  m.m., jf. VALP-README). Slike statuser fra status-topicene må gi et **tydelig, synlig signal
  for manuell oppfølging** (status-flagg + metrikk + logg uten PII) — aldri svelges stille.
- **Duplikate bestillinger:** OeBS avviser duplikat-nummer. Nummerserie + publisering må være
  idempotent per tilsagn.
- **VALP mangler enum-verdiene:** publisering før VALP har lagt inn `EKSPERTBISTAND` /
  `TILTAK_EKSPERTBISTAND` / ekspertbistand-kilde vil bli avvist. Rekkefølge-avhengighet mellom
  teamene — koordineres.
- **Melding må ikke inneholde PII i logg:** `bestillingsnummer`, orgnr og beløp kan logges;
  ikke fnr. `behandletAv`/`besluttetAv` kan være NavIdent og skal ikke logges utenfor teamLog,
  jf. eksisterende praksis for saksbehandlerident.
- **Ordering:** OeBS krever at faktura ikke prosesseres før bestilling er kvittert OK. Faktura
  må derfor vente på bestilling-status; key per `bestillingsnummer` gir ordering per bestilling.
- **Feil/utilgjengelig VALP eller Kafka:** klient-API-et skriver til outbox i kallerens
  transaksjon og berøres ikke av Kafka-status. Outbox-poller-en gjør retry med backoff;
  meldinger ligger trygt til Kafka er tilbake. At-least-once — ingen melding går tapt.

## Avklaringer (besvart 2026-09-11)

- **A-1 (var blokkerende):** Fagsystembokstav/kildenavn venter fortsatt på OeBs, men vi bruker
  **`E`** midlertidig som **kodekonstant** (Endring §4). Ikke lenger blokkerende for
  implementasjon; kun endelig prod-verdi gjenstår, og settes med en kodeendring.
- **A-2 — topic-navn:** Vår utgående topic: **`fager.ekspertbistand.bestillinger-v1`**
  (bekreftet).
- **A-3:** Team VALP abonnerer på vår topic og får `read`-ACL. Koordineres med VALP.
- **A-4:** Domene-eventene som trigger bestilling/utbetaling er **utenfor scope**. Vi lager en
  lokal klient med API (bestille / motta status / utbetale / annullere / gjøre opp) som
  produserer til vår topic og lytter på VALP sine status-topics.
- **A-5:** Ingen gjenbruk av `tiltaksokonomi-client`; modellen speiles lokalt, VALP-koden
  brukes som inspirasjon.
- **A-6:** Alle operasjoner er med i denne fasen. Utenfor scope er eventene og koblingen til
  saksbehandlingsflyten.
- **A-7:** Ja — vi lytter på VALP sine status-topics, men produserer på vår egen.
- **A-8 (nytt krav):** Feilede utbetalinger må gi tydelig signal for manuell oppfølging (se
  Kanttilfeller + Endring §6).
- **A-9 (nytt krav):** Kafka-producer implementeres med outbox-mønster innkapslet i klienten,
  i scope nå — ingen hard avhengighet til Kafka-oppetid, feilhåndtering lekker ikke innover
  (se Endring §2 og §5).

### Gjenstående å bekrefte
- Endelig fagsystembokstav/kilde fra OeBs (A-1) — kun for prod.
- Read-ACL fra VALP på status-topicene + at VALP abonnerer på vår topic (A-3/A-7) —
  tverr-team-koordinering.

## 🔴 Rød sone — skriv selv (med begrunnelse)

- **Nummerserie-generering** — økonomikritisk, må være unik/idempotent/monoton per sak; feil
  her gir dupliserte eller kolliderende tilsagn i OeBS.
- **Outbox + poller (transaksjonell skriving og at-least-once-publisering)** — pengeflyt;
  konsekvens ved feil er tapt eller dobbel avsetning/utbetaling. Transaksjonsgrenser og
  markering av publiserte rader må være korrekt.
- **Feilhåndtering av avviste OeBS-operasjoner** — må fange og signalere korrekt til manuell
  oppfølging.

## 🟢 Grønn sone — kan genereres (les gjennom før merge)

- Topic-manifest og deploy-steg (verifiser ACL og retention).
- Kafka-producer- og status-consumer-oppsett (verifiser SSL/idempotence-config).
- Meldingsmodell/DTO-speiling (verifiser at diskriminator og feltnavn matcher VALP eksakt).
- Flyway-migreringer for nummerserie- og status/feil-tabell (verifiser at de er trygge).

## Ferdig når

- Vi eier en Kafka-topic i `fager`-namespace som deployes via CI, med read-ACL til VALP.
- Klienten eksponerer API for bestilling, faktura, annullering og gjør-opp, som skriver til
  outbox i kallerens transaksjon og produserer gyldige `OkonomiBestillingMelding` (riktig
  diskriminator, felt og enum-verdier) med korrekt bestillingsnummer på egen nummerserie
  (`E-…` midlertidig).
- Outbox + poller gir at-least-once-publisering uten hard avhengighet til Kafka-oppetid;
  Kafka-feil håndteres i poller-en og lekker ikke ut til kalleren.
- Vi konsumerer VALP sine status-topics og oppdaterer egen tilstand.
- Feilede operasjoner gir tydelig signal for manuell oppfølging.
- Ingen PII i logg.
- (Ekstern avhengighet: VALP har lagt inn `EKSPERTBISTAND` / `TILTAK_EKSPERTBISTAND` /
  ekspertbistand-kilde og abonnerer på vår topic.)
