# OeBS / tiltaksøkonomi-integrasjon

Integrasjon mot OeBS (Oracle e-Business Suite) for å **sette av penger (bestille), utbetale
(fakturere), annullere og gjøre opp** tilskudd for ekspertbistand.

Ekspertbistand er et **eget kildesystem** i OeBS med **egen nummerserie**, men snakker ikke direkte
med OeBS. All kommunikasjon går via Team VALP sin tiltaksøkonomi-tjeneste
([`mulighetsrommet-tiltaksokonomi`](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi)),
som eier kontrakten mot OeBS.

> Se den fulle spesifikasjonen i
> [`specifications/integrasjon_oebs_tiltaksokonomi_bestilling.md`](../../../../../../../../specifications/integrasjon_oebs_tiltaksokonomi_bestilling.md).

## Kontekst

Vi publiserer bestillinger på **vår egen** topic. VALP abonnerer (read-ACL), oversetter til OeBS, og
sender status tilbake på **sine** status-topics som vi lytter på.

```mermaid
flowchart LR
    subgraph fager["fager (oss)"]
        klient["TiltaksokonomiClient"]
        outbox[("oebs_outbox")]
        poller["OebsOutboxPoller 🔴"]
        status["BestillingStatusConsumer"]
        statusdb[("oebs_bestilling_status")]
    end

    subgraph valp["team-mulighetsrommet"]
        tokonomi["tiltaksokonomi"]
    end

    oebs["OeBS"]

    klient -- "leggIOutbox (samme txn)" --> outbox
    poller -- "poll" --> outbox
    poller -- "publiser" --> topic["fager.ekspertbistand.bestillinger-v1"]
    topic -- "read-ACL" --> tokonomi
    tokonomi <--> oebs
    tokonomi -- "bestilling-status-v1 / faktura-status-v1" --> status
    status --> statusdb
```

## Hvorfor outbox-mønster?

Kafka-produsenten er innkapslet i et **outbox-mønster** (jf. spec-beslutning 11): kallere skriver
meldingen til `oebs_outbox` i **samme databasetransaksjon** som sin egen forretningsendring, og en
bakgrunnspoller publiserer radene til Kafka etterpå.

Konsekvensen er at vi ikke har en hard avhengighet til Kafka-oppetid, og at publiseringsfeil ikke
blør innover i forretningslogikken — en feilet publisering blir liggende i outbox-en og prøves igjen.
Dette speiler mønsteret i [`event`-pakken](../event/README.md) (`FOR UPDATE SKIP LOCKED`).

### Skriveflyt (bestilling → OeBS)

```mermaid
sequenceDiagram
    participant K as Kaller (forretningslogikk)
    participant C as TiltaksokonomiClient
    participant N as Nummerserie 🔴
    participant O as oebs_outbox (DB)
    participant P as OebsOutboxPoller 🔴
    participant T as Kafka-topic
    participant V as Team VALP

    K->>C: transaction { opprettBestilling(sakId) { nr -> ... } }
    C->>N: nesteBestillingsnummer(sakId)
    N-->>C: E<sak><løpenr>
    C->>O: leggIOutbox(Bestilling) — samme txn som kaller
    Note over O: rad {status: PENDING}

    P->>O: poll (FOR UPDATE SKIP LOCKED)
    O-->>P: neste PENDING-rad
    P->>T: send(key=bestillingsnummer, value=JSON), acks=all
    T-->>P: ack
    P->>O: marker PUBLISHED (samme txn)
    T-->>V: konsumerer bestilling
```

### Statusflyt (OeBS → oss)

```mermaid
sequenceDiagram
    participant V as Team VALP
    participant S as BestillingStatusConsumer
    participant D as oebs_bestilling_status (DB)
    participant T as team-logs

    V->>S: status-melding (bestilling/faktura)
    S->>S: tolkStatus(raw) 🔴
    S->>D: upsert status + trenger_manuell_oppfolging
    alt feilet operasjon
        S->>T: teamLogger.warn (signal for manuell oppfølging)
    end
```

## Komponenter

| Fil | Ansvar | Sone |
|-----|--------|------|
| [`OkonomiBestillingMelding.kt`](OkonomiBestillingMelding.kt) | Lokalt speilet meldingsmodell + verdityper + `Json`-instans | 🟢 (wire-parity ⚠️) |
| [`TiltaksokonomiProducer.kt`](TiltaksokonomiProducer.kt) | Idempotent Kafka-produsent (`acks=all`, SSL fra `KAFKA_*`) | 🟢 |
| [`Outbox.kt`](Outbox.kt) | `oebs_outbox`-tabell + `JdbcTransaction.leggIOutbox` (skriveside) + poller-stub | 🟢 skrive / 🔴 poller |
| [`Nummerserie.kt`](Nummerserie.kt) | `oebs_lopenummer`-tabell + `FAGSYSTEM_KILDE` + nummergenerator | 🔴 |
| [`TiltaksokonomiClient.kt`](TiltaksokonomiClient.kt) | Internt API: bestille / fakturere / annullere / gjøre opp | 🟢 |
| [`BestillingStatusConsumer.kt`](BestillingStatusConsumer.kt) | Lytter på VALP sine status-topics, lagrer status | 🟢 skjelett / 🔴 tolkning |
| [`OebsProsessering.kt`](OebsProsessering.kt) | Oppstart av poller + status-konsument | 🟢 (ikke wiret inn ennå) |
| [`V12__oebs_tiltaksokonomi.sql`](../../../../../resources/db/migration/V12__oebs_tiltaksokonomi.sql) | Flyway: outbox-, løpenummer- og statustabeller | 🟢 |
| [`nais/{dev,prod}-gcp-topic-bestillinger.yaml`](../../../../../../../../nais) | Topic-manifest + ACL | 🟢 |

## Datamodell

| Tabell | Rolle |
|--------|-------|
| `oebs_outbox` | Utgående meldinger, drenert til Kafka av polleren |
| `oebs_lopenummer` | Neste ledige løpenummer per sak (én rad per sak) |
| `oebs_bestilling_status` | Siste status per bestilling, med flagg for manuell oppfølging |

## Meldingsmodell og kontrakt

`OkonomiBestillingMelding` er en `sealed class` med diskriminatorene `BESTILLING`, `ANNULLERING`,
`FAKTURA` og `GJOR_OPP_BESTILLING`. Modellen er **speilet lokalt** (ikke tatt inn som avhengighet,
jf. spec-beslutning 8) med samme `@SerialName` og feltnavn som VALP.

> ⚠️ **Kontrakt-parity:** Wire-formatet (feltnavn, `type`-diskriminator og serialisering av
> verdityper som `Periode` og `Organisasjonsnummer`) må matche VALP eksakt. Dette er ikke fullt
> verifisert ennå — se `OkonomiBestillingMeldingContractTest` (skjelett) og kanttilfellene i spec-en.

## 🔴 Rød sone — implementeres av teamet

Disse delene er bevisst lagt igjen som stubber (`TODO`) fordi de er økonomikritiske og bør forstås
grundig, ikke genereres:

- **Nummerserie-generering** (`nesteBestillingsnummer`) — transaksjonssikker les-og-inkrementer.
- **Outbox-poller** (`OebsOutboxPoller.startProcessing`) — `SKIP_LOCKED`-poll → publiser → marker
  `PUBLISHED` i én transaksjon, med retry/backoff (at-least-once).
- **Tolkning av avviste operasjoner** (`BestillingStatusConsumer.tolkStatus`) — avgjør hva som er
  feilet/avvist og når det krever manuell oppfølging.

Testskjeletter finnes i
[`src/test/.../oebs`](../../../../../../test/kotlin/no/nav/ekspertbistand/oebs) (`@Ignore` til de er
implementert).

> `OebsProsessering.startOebsProsessering` er **ikke** koblet inn i `Application.main()` ennå. Den
> kaster `TODO(...)` fra rød sone, så den skal først wires inn når logikken over er skrevet.

## Gjenstående eksterne avhengigheter

- **Fagsystembokstav/kilde**: bruker `E` midlertidig (`FAGSYSTEM_KILDE`), må bekreftes mot OeBS.
- **VALP** må legge inn `EKSPERTBISTAND` / `TILTAK_EKSPERTBISTAND` / ekspertbistand-kilde i sine
  enum-er og abonnere på `fager.ekspertbistand.bestillinger-v1` før meldinger godtas.
