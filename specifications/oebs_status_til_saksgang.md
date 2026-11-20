# Status fra OeBS til saksgangen

Trello: https://trello.com/c/u2XIE5PJ/726-status-fra-oebs-til-saksgangen
Kort-ID: `6afe19e5e0ffae299042f0b4`

Bestilt av Ken 2026-11-18 på branchen `oebs-manuell-test-dev` (#259).

Henger sammen med:

- [integrasjon_oebs_tiltaksokonomi_bestilling.md](integrasjon_oebs_tiltaksokonomi_bestilling.md).
  A-4 holdt domene-eventene utenfor den leveransen. Denne spec-en lager eventene for veien inn.
- [sette_av_penger_oebs_vedtak.md](sette_av_penger_oebs_vedtak.md). `BestillTilsagnIOebs`
  bestiller tilsagnet på `VedtakFattet` og lagrer det i `sak_bestilling`.
- [test_oebs_integrasjon_dev.md](test_oebs_integrasjon_dev.md). Manuell test i dev med
  testnumrene `E-2026/900001` til `E-2026/900009`.

## Mål

Når vi får en status fra VALP på en bestilling eller faktura, publiserer `oebs/`-pakken en event på
EventQueue. Eventen har en egen domain-klasse og er knyttet til søknaden som aggregatrot. Da kan
saksgangen oppdatere saken med handlere, uten å lese `oebs`-tabellene og uten å kjenne VALP sin
kontrakt.

Eventene er grensesnittet ut av `oebs/`-pakken. Denne spec-en lager eventene og publiseringen, og
den første konsumenten: saksloggen. I dag skriver `BestillTilsagnIOebs` «Bestilling til OeBS
opprettet» når bestillingen legges i outboxen, før OeBS har sett den. Linjen flyttes til en handler
på statuseventen, så saksloggen viser det OeBS har bekreftet. Andre handlere som endrer saken,
kommer i egne kort.

## Bakgrunn

Fra før (`TiltaksokonomiConsumer.processRecord`):

1. Filtrerer på headeren `fagsystem=EKSPERTBISTAND`.
2. Deserialiserer til `OebsStatusMelding.Bestilling(BestillingStatus)` eller
   `OebsStatusMelding.Faktura(FakturaStatus)`. Brudd på kontrakten gir `teamLog.error`,
   `log.error` og unntak, så meldingen prøves på nytt.
3. `loggMottattStatus` skriver råmeldingen til `oebs_mottatt_status` i egen transaksjon.
   `insertIgnore` på topic, partition og offset gjør den idempotent.
4. `tolkStatus` og upsert av `oebs_bestilling_status` i ny transaksjon. Bestilling nøkles på
   bestillingsnummer, faktura på fakturanummer.
5. `FEILET` gir `teamLog.error` og `log.error`.

Ingenting går videre til saksgangen. Saksbehandler og arbeidsgiver ser ikke om tilsagnet er
aktivt, avvist eller annullert.

Andre fakta:

- `oebs/` importerer i dag bare fra `infrastruktur/`. Ingen fil i pakken kjenner `event/` eller
  `saksbehandling/`. Koblingen mellom bestilling og sak ligger i `sak_bestilling`
  (`saksbehandling/Bestilling.kt`), som `BestillTilsagnIOebs` skriver.
- Bestillingsnummer er `E-<saksnummer>-<løpenr>`. Fakturanummer er `<bestillingsnummer>-<løpenr>`.
  `saksnummer_seq` starter på 1 000 000, så testnumrene `2026/900001` til `2026/900009` kan ikke
  tilhøre en ekte sak.
- VALP sin kontrakt (`common/tiltaksokonomi-client` i navikt/mulighetsrommet, lest 2026-11-18):
  `BestillingStatus(bestillingsnummer, status, statusSistOppdatert)` og
  `FakturaStatus(fakturanummer, status, statusSistOppdatert)`. Vår speiling av `BestillingStatus`
  mangler `statusSistOppdatert`. `ignoreUnknownKeys` skjuler det. Ingen av dem har feilkode eller
  feilmelding fra OeBS ennå. Sondre legger dem til 2026-11-19.
- Faktura og annullering er ikke koblet til saksgangen ennå. Bare bestilling er det.
- Lokalt og i mock-backenden svarer `LokalOebs` med status gjennom den ekte consumeren.

## Beslutninger

| Kilde | Beslutning |
|-------|------------|
| Ken, 2026-11-18 | Status går ut av `oebs/`-pakken via EventQueue. |
| Ken, 2026-11-18 | Eventene har egne domain-klasser i `event/domain/`, ikke VALP-DTO-ene. |
| Ken, 2026-11-18 | Eventene skal gjøre det mulig å oppdatere aggregatet og saken. |
| Ken, 2026-11-18 | Saksloggen for bestillingen flyttes fra `BestillTilsagnIOebs` til en handler på statuseventen. Erstatter steget i `sette_av_penger_oebs_vedtak.md` som publiserer `SakOppdatert` ved bestilling. |
| Ken, 2026-11-18 | `SENDT` gir «Bestilling til OeBS sendt», `AKTIV` gir «opprettet» og `FEILET` gir «feilet». |
| Ken, 2026-11-18 | Andre statuser feiler i handleren, så eventen prøves på nytt til de er implementert. |
| Ken, 2026-11-18 | Status uten referanse kaster og blokkerer partisjonen. Det gjelder også status etter at saken er slettet, som ikke skal kunne skje. |
| Ken, 2026-11-18 | `OebsFakturaStatusMottatt` tas med nå. |
| Ken, 2026-11-18 | Ken spør VALP (Sondre) om de kan sende `feilKode` og `feilMelding` videre. Gjør de ikke det, ber vi VALP slå opp årsaken, eller spør OeBS med bestillingsnummeret. |
| Ken, 2026-11-19 | VALP (Sondre) legger til feilkode og feilmelding. Vi bygger på at de kommer som to nullable felt, og justerer når kontrakten er på plass. |
| Ken, 2026-11-19 | VALP sender feilen som `feil: OebsFeil?` med `kode: String?` og `melding: String?` på begge statusmeldingene (navikt/mulighetsrommet#8631). Domain-klassene beholder de flate feltene `feilKode` og `feilMelding`. |
| Memory (event sourcing) | Eventen har nok data til at handlere ikke leser projeksjoner. |
| Memory (event sourcing) | Domain-enums har egen `tilDomain()` med uttømmende `when` uten `else`. |
| Ken, tidligere i #259 | Meldinger vi ikke kan behandle, hoppes ikke over: logg, kast, prøv på nytt. |

### Valg i spec-en

Disse er mine forslag. Valgene om status uten referanse og faktura er bekreftet av Ken (se
tabellen over).

- **To eventer, én per status-topic.** `OebsBestillingStatusMottatt` og `OebsFakturaStatusMottatt`
  speiler det vi fikk, også `SENDT`. Handlerne bestemmer hva hver status betyr for saken.
  Forkastet: én event per forretningsfakta (`TilsagnAktivert`, `TilsagnAvvist`, `FakturaBetalt` …).
  Det flytter tolkningen inn i `oebs/` og gir ti eventtyper før vi vet hvilke saksgangen trenger.
  De kan lages senere av en handler som lytter på eventene her.
- **`oebs/` eier koblingen til saken.** `opprettBestilling` får `sakId` og `soknadId`, og pakken
  lagrer dem i en ny tabell `oebs_bestilling_referanse`. Consumeren leser den tabellen og
  publiserer eventen. `oebs/` avhenger da av `event/`, men ikke av `saksbehandling/`.
  Forkastet: slå opp i `sak_bestilling` fra consumeren. Det gir `oebs/` en avhengighet til
  `saksbehandling/` og binder pakken til én bestillingstype. Kostnaden er at koblingen ligger
  to steder. Migreringen fyller den nye tabellen fra `sak_bestilling`.
- **Én transaksjon per melding.** `loggMottattStatus`, upsert av `oebs_bestilling_status` og
  `publishEventQueue` skjer i samme transaksjon. Eventen publiseres bare når `loggMottattStatus`
  satte inn en ny rad. Samme Kafka-melding levert på nytt gir da ikke en ny event.
  Forkastet: beholde revisjonssporet i egen transaksjon. Da kan en melding få revisjonsrad uten
  event, og vi trenger en egen markør for hvilke rader som er publisert. Det vi taper: en melding
  som feiler før commit, havner ikke i `oebs_mottatt_status`. Den ligger fortsatt på topicen og i
  teamLogs, og prøves på nytt.
- **Testnumre under 1 000 000 gir ingen event.** Status uten referanse der løpenummeret i
  saksnummeret er under 1 000 000, er manuell testdata. Den lagres som i dag, med `log.info`, uten
  event. Alle andre statuser uten referanse er en feil hos oss og gir `teamLog.error`, `log.error`
  og unntak.
- **Sakslogg på `SENDT`, `AKTIV` og `FEILET`.** `SkrivSaksloggForOebsBestillingStatus` publiserer
  `SakOppdatert` (`SYSTEM`) med notat etter status (se steg 6). `tidspunkt` er
  `statusSistOppdatert`, altså når statusen ble satt, ikke når vi leste meldingen.
  `ANNULLERING_SENDT`, `ANNULLERT` og `OPPGJORT` gir `transientError`, så eventen prøves på nytt
  til saksloggen for dem er implementert. Samme mønster som `SkrivSaksloggForRefusjonskrav`.
- **No-op-handler** for `OebsFakturaStatusMottatt`, som `SoknadKopiDeltakerDistribuertNoop`, til en
  handler i saksgangen trenger den.

## Avgrensning

Ikke i denne spec-en:

- Handlere som endrer saken utover saksloggen: visning for saksbehandler eller arbeidsgiver,
  oppgave ved `FEILET`. Egne kort når vi vet hva saksgangen skal gjøre med statusene.
- Kobling av faktura, annullering og oppgjør til saksgangen.
- Ny sending etter `FEILET` (åpent spørsmål i `test_oebs_integrasjon_dev.md`).
- Å skru på polleren og consumeren i prod.
- Endring i hvordan Kafka-feil håndteres per partisjon (`Kafka.kt`).

## Tilnærming

```
VedtakFattet ─> BestillTilsagnIOebs           (publiserer ikke lenger SakOppdatert)
                  └─ opprettBestilling(sakId, soknadId, saksnummer)
                       ├─ oebs_bestilling_referanse (ny)
                       └─ oebs_outbox ─> poller ─> VALP ─> OeBS

VALP status-topic ─> TiltaksokonomiConsumer
                       └─ transaksjon
                            ├─ loggMottattStatus (ny rad?)
                            ├─ upsert oebs_bestilling_status
                            └─ ny rad og referanse finnes:
                                 publishEventQueue(...)

OebsBestillingStatusMottatt ─> SkrivSaksloggForOebsBestillingStatus
                                 └─ SENDT | AKTIV | FEILET: SakOppdatert ─> SkrivSakslogg
OebsFakturaStatusMottatt    ─> no-op
```

### 1. Kontrakten: `statusSistOppdatert`, feilkode og feilmelding

`BestillingStatus` i `OebsStatusMelding.kt` får `statusSistOppdatert: Instant`, som hos VALP og i
`FakturaStatus`. Feltet er påkrevd hos VALP. Melding uten feltet er brudd på kontrakten og
håndteres som i dag.

`BestillingStatus` og `FakturaStatus` får `feil: OebsFeil? = null`, der
`OebsFeil(kode: String?, melding: String?)` speiler navikt/mulighetsrommet#8631. VALP fyller den
bare ut for `FEILET`. Standardverdien `null` trengs for deserialiseringen: meldinger fra før VALP
la til feltet, mangler det. Det er DTO-felt, ikke parametere på en klient, så regelen om
eksplisitte argumenter gjelder ikke. Consumerens JSON har `ignoreUnknownKeys`, så VALP kan merge
før oss uten at meldinger feiler.

`OebsStatusMeldingTest` får `statusSistOppdatert` i eksempelmeldingen, og tester melding med og uten
`feil`.

### 2. Domain-klasser

Ny fil `event/domain/OebsStatusDomain.kt`:

```kotlin
/** Status på en bestilling (tilsagn) i OeBS, slik VALP meldte den. */
@Serializable
data class OebsBestillingStatusDomain(
    val bestillingsnummer: String,
    val status: Status,
    val statusSistOppdatert: Instant,
    val feilKode: String?,
    val feilMelding: String?,
    val kvittering: OebsKvitteringDomain,
) {
    @Serializable
    enum class Status { SENDT, AKTIV, ANNULLERING_SENDT, ANNULLERT, OPPGJORT, FEILET }
}

/** Status på en faktura (utbetaling) i OeBS, slik VALP meldte den. */
@Serializable
data class OebsFakturaStatusDomain(
    val fakturanummer: String,
    val bestillingsnummer: String,
    val status: Status,
    val statusSistOppdatert: Instant,
    val feilKode: String?,
    val feilMelding: String?,
    val kvittering: OebsKvitteringDomain,
) {
    @Serializable
    enum class Status { SENDT, IKKE_BETALT, DELVIS_BETALT, FULLT_BETALT, FEILET }
}

/** Hvor statusen kom fra. Peker på raden i revisjonssporet `oebs_mottatt_status`. */
@Serializable
data class OebsKvitteringDomain(
    val kafkaTopic: String,
    val kafkaPartition: Int,
    val kafkaOffset: Long,
    val mottattTidspunkt: Instant,
)
```

`TilDomain.kt` får `BestillingStatusType.tilDomain()` og `FakturaStatusType.tilDomain()` med
uttømmende `when` uten `else`. En ny verdi fra VALP feiler allerede i deserialiseringen.
KDoc-en på `tolkStatus` sier at en ny verdi «behandles som ikke-feilet». Det stemmer ikke, og
rettes.

### 3. Eventene

I `Events.kt`:

```kotlin
/**
 * VALP har meldt ny status på en bestilling i OeBS. Publiseres av TiltaksokonomiConsumer for hver
 * statusmelding på en bestilling som tilhører en sak. Statusen er det VALP meldte, ikke tolket.
 *
 * Søknaden er aggregatroten, så [aggregateRootId] er [soknadId].
 *
 * Konsument: [no.nav.ekspertbistand.event.handlers.SkrivSaksloggForOebsBestillingStatus].
 */
@Serializable
@SerialName("oebsBestillingStatusMottatt")
data class OebsBestillingStatusMottatt(
    val sakId: String,
    val soknadId: String,
    val bestilling: OebsBestillingStatusDomain,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}

@Serializable
@SerialName("oebsFakturaStatusMottatt")
data class OebsFakturaStatusMottatt(
    val sakId: String,
    val soknadId: String,
    val faktura: OebsFakturaStatusDomain,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

Ingen av eventene har personopplysninger.

### 4. Koblingen til saken

Ny tabell i `oebs/model/BestillingReferanse.kt` og migrering `V39__oebs_bestilling_referanse.sql`:

```sql
CREATE TABLE IF NOT EXISTS oebs_bestilling_referanse (
    bestillingsnummer TEXT        PRIMARY KEY,
    sak_id            UUID        NOT NULL,
    soknad_id         UUID        NOT NULL,
    opprettet         TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO oebs_bestilling_referanse (bestillingsnummer, sak_id, soknad_id, opprettet)
SELECT sb.bestillingsnummer, sb.sak_id, s.soknad_id, sb.opprettet
FROM sak_bestilling sb
JOIN sak s ON s.sak_id = sb.sak_id
ON CONFLICT (bestillingsnummer) DO NOTHING;
```

Ingen fremmednøkkel til `sak`. `oebs/` eier tabellen, og `AggregateRetention` sletter den med de
andre `oebs_`-tabellene på bestillingsnummer-prefikset.

`OebsKlient.opprettBestilling(sakId, soknadId, saksnummer, bygg)` skriver raden i samme transaksjon
som outbox-raden. Parameterne har ingen standardverdi. `BestillTilsagnIOebs` sender dem.

Faktura finner bestillingen med `bestillingsnummerForFaktura(fakturanummer)` i `Nummerserie.kt`.
Funksjonen tar alt før siste `-` og kaster hvis resten ikke er et løpenummer. Fakturaen får samme
referanse som bestillingen.

### 5. Consumeren

`processRecord` etter deserialiseringen, i én transaksjon:

1. `loggMottattStatus` returnerer `true` når den satte inn en ny rad.
2. Upsert av `oebs_bestilling_status` som i dag.
3. Er raden ny, slå opp referansen på bestillingsnummeret:
   - Finnes: `publishEventQueue` med eventen. `log.info` med bestillingsnummer og status.
   - Mangler, testnummer (løpenummer i saksnummeret under 1 000 000): `log.info` om at testdata
     ikke publiseres.
   - Mangler ellers: `teamLog.error` med `record`, `log.error` med topic, partition, offset og
     bestillingsnummer, og unntak. Transaksjonen rulles tilbake, og meldingen prøves på nytt.

`FEILET` logges som i dag, etter commit. `log.error` får også `feilKode`, som er en kode fra OeBS
(som `PO_PDOI_CHARGE_FAILED`). `feilMelding` står bare i `teamLog`, via `record`: den er fritekst fra
OeBS og kan inneholde leverandørinformasjon.

### 6. Saksloggen

Ny handler `event/handlers/SkrivSaksloggForOebsBestillingStatus.kt` på
`OebsBestillingStatusMottatt`, bygget som `SkrivSaksloggForRefusjonskrav`:

- Idempotensvakt og `publishEventQueue` i samme transaksjon.
- `SakOppdatert(sakId, soknadId, SYSTEM, null, notat, statusSistOppdatert)` med notat:

  | Status | Notat |
  |--------|-------|
  | `SENDT` | «Bestilling til OeBS sendt: $bestillingsnummer» |
  | `AKTIV` | «Bestilling til OeBS opprettet: $bestillingsnummer» |
  | `FEILET` | «Bestilling til OeBS feilet: $bestillingsnummer», med feilmelding og feilkode når vi har dem (se under) |
  | `ANNULLERING_SENDT`, `ANNULLERT`, `OPPGJORT` | Ikke implementert, `transientError` |

- `when` over `OebsBestillingStatusDomain.Status` er uttømmende uten `else`, så en ny status krever
  at vi tar stilling til saksloggen.
- Statusene uten sakslogg gir `transientError("Sakslogg for status $status er ikke implementert,
  bestilling $bestillingsnummer")`, og idempotensvakten settes ikke. `EventManager` logger
  `log.error` og prøver eventen på nytt til handleren er implementert. Ingen status blir borte
  fordi saksgangen ikke var klar. Ikke `TODO()`: `EventManager.routeToHandlers` fanger ikke unntak
  fra `handle`, så `NotImplementedError` stopper hele event-løkka, ikke bare denne eventen.

Notatet ved `FEILET` tar med det vi har fra OeBS:

| `feilMelding` | `feilKode` | Notat |
|---------------|------------|-------|
| finnes | finnes | «Bestilling til OeBS feilet: $bestillingsnummer. $feilMelding ($feilKode)» |
| finnes | `null` | «Bestilling til OeBS feilet: $bestillingsnummer. $feilMelding» |
| `null` | finnes | «Bestilling til OeBS feilet: $bestillingsnummer ($feilKode)» |
| `null` | `null` | «Bestilling til OeBS feilet: $bestillingsnummer» |

Eksempel: «Bestilling til OeBS feilet: E-2026/1000008-1. Feil: Generering av Kostnadskonto
mislyktes. (PO_PDOI_CHARGE_FAILED)». Feilmeldingen fra OeBS er ikke alltid beskrivende nok. Da
spør vi OeBS med bestillingsnummeret. Saksloggen vises bare for saksbehandlere
(`SaksbehandlerApi`), ikke for arbeidsgiver.

`BestillTilsagnIOebs` slutter å publisere `SakOppdatert`. KDoc-en, som sier at saksloggen skrives i
samme transaksjon som bestillingen, rettes.

Saker i ny saksbehandling finnes bare utenfor prod, og consumeren kjører bare i dev. I dev og
lokalt får saken linjene når statusene kommer. Melder VALP ingenting, står det ingenting om
bestillingen i saksloggen. Det er riktig: vi vet bare at den ligger i outboxen.

### 7. Registrering og dokumentasjon

- `SkrivSaksloggForOebsBestillingStatus` og `OebsFakturaStatusMottattNoop` i
  `configureEventHandlers`.
- `event/README.md`: navnetabellen og `aggregateRootId`-tabellen.
- `event/event-flow.md` genereres på nytt.
- `oebs/README.md`: Statusflyt-diagrammet får publiseringen, Struktur og Datamodell får den nye
  tabellen.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `oebs/integration/OebsStatusMelding.kt` | `statusSistOppdatert` på `BestillingStatus`, `OebsFeil` og `feil` på begge |
| `oebs/integration/TiltaksokonomiConsumer.kt` | Én transaksjon, oppslag og publisering, KDoc på `tolkStatus` |
| `oebs/model/Meldingslogg.kt` | `loggMottattStatus` returnerer om raden var ny |
| `oebs/model/BestillingReferanse.kt` (ny) | `oebs_bestilling_referanse` og oppslag |
| `oebs/model/Nummerserie.kt` | `bestillingsnummerForFaktura`, sjekk for testnummer |
| `oebs/Oebs.kt` | `opprettBestilling(sakId, soknadId, …)` |
| `resources/db/migration/V39__oebs_bestilling_referanse.sql` (ny) | Tabell og utfylling fra `sak_bestilling` |
| `event/domain/OebsStatusDomain.kt` (ny) | Domain-klassene |
| `event/domain/TilDomain.kt` | `tilDomain()` for begge status-enums |
| `event/Events.kt` | To eventer, registrering av handler og no-op-handler |
| `event/handlers/SkrivSaksloggForOebsBestillingStatus.kt` (ny) | Sakslogg ved `SENDT`, `AKTIV` og `FEILET` |
| `event/handlers/BestillTilsagnIOebs.kt` | Sender `sakId` og `soknadId`, publiserer ikke `SakOppdatert` |
| `soknad/AggregateRetention.kt` | Sletter `oebs_bestilling_referanse` |
| `event/README.md`, `event/event-flow.md`, `oebs/README.md` | Dokumentasjon |
| `TiltaksokonomiConsumerTest.kt`, `OebsStatusMeldingTest.kt`, `BestillTilsagnIOebsTest.kt`, `SkrivSaksloggForOebsBestillingStatusTest.kt` (ny) | Tester |

## Kanttilfeller og feilmodus

- **Samme melding levert på nytt** (rebalansering, offset satt tilbake): `loggMottattStatus` finner
  raden, ingen ny event. Upsert er idempotent.
- **Samme status to ganger med ulik offset**: to eventer. Eventen sier at vi fikk en melding, ikke
  at statusen endret seg. Handlere må tåle det. Får vi samme status to ganger, står linjen to
  ganger i saksloggen. Handleren leser ikke saksloggen for å stoppe det.
- **Saker bestilt før endringen** har allerede «opprettet»-linjen fra `BestillTilsagnIOebs`, skrevet
  da bestillingen ble lagt i outboxen. Statuser som kommer etter utrullingen, gir nye linjer.
  Gjelder bare dev og lokalt.
- **Rekkefølge**: Kafka holder rekkefølgen per partisjon, men handlere bør ikke anta at eventer
  for samme bestilling kommer i rekkefølge. `statusSistOppdatert` er med så de kan sammenligne, og
  saksloggen får det som tidspunkt, så «sendt» står før «opprettet» uansett når vi leste dem.
- **Statuser mottatt før denne endringen** har allerede revisjonsrad og gir ingen event, heller
  ikke om offset settes tilbake. Gjelder bare dev. I prod er consumeren ikke på.
- **Status på bestilling uten referanse** blokkerer partisjonen til feilen er rettet. Det er
  meningen: statusen skal ikke gå tapt for saksgangen. `log.error` gjør det synlig.
- **Saken er slettet** av `AggregateRetention`: skal ikke kunne skje. Aggregatet slettes først 10 år
  (`OPPBEVARINGSTID_AAR`) etter siste event, og ikke mens det finnes uferdige meldinger i outboxen.
  Skjer det likevel, mangler referansen, og consumeren kaster og blokkerer partisjonen som for
  annen status uten referanse.
- **Status som ikke er implementert** (`ANNULLERING_SENDT`, `ANNULLERT`, `OPPGJORT`): skal ikke
  komme for ekte saker før annullering og oppgjør er koblet til saksgangen. Kommer den likevel,
  ligger eventen i køen med `log.error` ved hvert forsøk, til handleren er implementert. Andre
  eventer behandles som vanlig. Testnumrene gir ingen event, så `OPPGJORT` fra den manuelle testen
  treffer ikke dette.
- **`FEILET` uten feilkode og feilmelding**: statuser fra før VALP la til feltene, og eventuelle
  feil der OeBS ikke gir noen. Notatet blir da bare «feilet», og vi spør VALP eller OeBS.
- **Feltene får andre navn eller typer** enn antatt: `ignoreUnknownKeys` gjør at de blir `null`
  uten feil. Vi oppdager det ved neste `FEILET` i dev, der notatet mangler feilmeldingen. Derfor
  sjekker vi kontrakten mot VALP før dette rulles ut (se Åpne spørsmål).
- **Lokalt og i mock**: `LokalOebs` gir eventene for saker som er bestilt gjennom
  `BestillTilsagnIOebs`. Arena-saker i `LokaleSaker` har ingen OeBS-bestilling.

## Åpne spørsmål

1. **Kontrakten for feilkode og feilmelding**: Avklart 2026-11-19, se beslutningene. Speilingen
   sjekkes mot `common/tiltaksokonomi-client` når navikt/mulighetsrommet#8631 er merget.

## 🔴 Rød sone

- [ ] Idempotens i consumeren: én transaksjon og publisering bare ved ny revisjonsrad.
- [ ] Regelen for status uten referanse, inkludert grensen for testnumre.
- [ ] `tilDomain()` for status-enums.

## Ferdig når

- [ ] Statusmelding på en bestilling med referanse gir én `OebsBestillingStatusMottatt` på
      søknaden, i samme transaksjon som revisjonsraden.
- [ ] Statusmelding på en faktura gir én `OebsFakturaStatusMottatt` med bestillingens referanse.
- [ ] Samme Kafka-melding to ganger gir én event.
- [ ] Testnummer uten referanse lagres uten event og uten feil.
- [ ] Annet nummer uten referanse gir `teamLog.error`, `log.error` og unntak, og ingenting lagres.
- [ ] `BestillTilsagnIOebs` lagrer referansen, og migreringen fyller den for eksisterende
      bestillinger.
- [ ] `AggregateRetention` sletter referansen.
- [ ] `SENDT`, `AKTIV` og `FEILET` gir «Bestilling til OeBS sendt/opprettet/feilet:
      $bestillingsnummer» i saksloggen med `statusSistOppdatert` som tidspunkt.
- [ ] `FEILET` tar med feilmelding og feilkode i notatet når VALP sender dem, og gir «feilet» uten
      dem når de mangler.
- [ ] `feil` er sjekket mot kontrakten i `common/tiltaksokonomi-client` når
      navikt/mulighetsrommet#8631 er merget.
- [ ] `ANNULLERING_SENDT`, `ANNULLERT` og `OPPGJORT` gir `transientError` uten idempotensvakt, og
      eventen prøves på nytt.
- [ ] `BestillTilsagnIOebs` skriver ikke lenger til saksloggen.
- [ ] Eventene er registrert, `event-flow.md` er generert, og README-ene er oppdatert.
- [ ] Testet i dev: ny bestilling på en ekte sak gir eventen i `event_log` når VALP melder status.
