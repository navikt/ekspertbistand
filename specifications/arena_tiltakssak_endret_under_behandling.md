# Arena TiltakssakEndret — marker sak som «under behandling i Arena»

## Mål

Lytte på Kafka-topicet `aapen-arena-tiltakssakendret-v1` for å oppdage at en saksbehandler har begynt å behandle en
ekspertbistandsak i Arena, og markere den tilhørende søknaden i vårt system som «under behandling i Arena».

Markeringen skal eksponeres på saksbehandler-API-et slik at vår egen saksbehandlingsløsning kan **advare** — ikke nekte
— når en saksbehandler åpner en sak som allerede er tatt til behandling i Arena.

## Bakgrunn

Vi bygger saksbehandling av ekspertbistand i vår egen app, som på sikt skal erstatte saksbehandling i Arena. For å unngå
en big-bang-overgang skal begge løsningene leve side om side i en periode. Risikoen er at samme sak behandles i begge
apper. Denne endringen gir oss signalet vi trenger for å advare saksbehandler.

### Deteksjonsregel — beskrivelse fra Arena-teamet

> Et tegn på at noen har begynt å behandle en ekspertbistandsak i Arena er at tiltaksansvarlig oppdateres. Når
> sak/tiltaksgjennomføring/oppgave opprettes via Tiltaksgjennomfoering API, så settes behandlende enhet som
> tiltaksansvarlig. Verdien lagres i databasekolonnen `sak.brukerid_ansvarlig`. Ved behandling av tiltaksgjennomføringen
> oppdaterer tiltaksansvarlig denne til egen brukerident. Ettersom tiltaksansvarlig lagres på tiltakssaken og ikke
> tiltaksgjennomføringen, så vil oppdatering av denne publiseres på `teamarenanais.aapen-arena-tiltakssakendret-v1`, og
> ikke `teamarenanais.aapen-arena-tiltakgjennomforingendret-v1`.

Dette er grunnen til at eksisterende `ArenaTiltaksgjennomforingEndretProcessor` **ikke** kan brukes: den meldingen
inneholder kun `TILTAKSTATUSKODE`, ikke tiltaksansvarlig.

### Meldingsformat

Dokumentert av Team Arena:
[Arena - Tjeneste Kafka - Tiltakssak](https://confluence.adeo.no/spaces/ARENA/pages/478256186/Arena+-+Tjeneste+Kafka+-+Tiltakssak)
(krever innlogging).

Topicet er en Golden Gate CDC-strøm fra Arena-tabellen `SIAMO.SAK`, med samme konvoluttstruktur som de to
Arena-topicene vi allerede konsumerer.

**Konvolutt:**

| felt         | type              | kommentar                                                       |
|--------------|-------------------|-----------------------------------------------------------------|
| `table`      | string            | Alltid `SIAMO.SAK`                                                |
| `op_type`    | string            | **Påkrevd.** Databaseoperasjon: `I` (insert), `U` (update), `D` (delete) |
| `op_ts`      | date-time uten T  | Tidsstempel for databaseoperasjonen (commit)                      |
| `current_ts` | date-time uten T  | Tidsstempel for Golden Gate-prosessering                          |
| `pos`        | string            | Sekvensnummer + RBA i Golden Gate trail-fil                       |
| `tokens`     | object            | Key-value par fra trail-filen                                     |
| `before`     | object `tiltakssak` | Radinnhold før operasjonen. Ikke relevant for `I`               |
| `after`      | object `tiltakssak` | Radinnhold etter operasjonen. Ikke relevant for `D`             |

**`tiltakssak`-objektet** — kolonnenavnene er UPPERCASE, som i `TiltaksgjennomforingEndret`:

| felt                   | type    | påkrevd | kommentar                                                            |
|------------------------|---------|---------|----------------------------------------------------------------------|
| `SAK_ID`               | integer | ja      | Unik identifikator for saken. Vår idempotensnøkkel                    |
| `SAKSKODE`             | string  | ja      | Alltid `TILT` for tiltakssaker                                        |
| `AAR`                  | integer | ja      | Årstall i saksnummeret                                                |
| `LOPENRSAK`            | integer | ja      | Løpenummer i saksnummeret innenfor året                               |
| `SAKSTATUSKODE`        | string  | ja      | `AKTIV`, `AVSLU` (lukket), `HIST` (historisert), `INAKT`, `OPRTV`     |
| `BRUKERID_ANSVARLIG`   | string  |         | **Arena-ident til saksbehandler som er ansvarlig for saken**          |
| `AETATENHET_ANSVARLIG` | string  |         | **Nav-enhet som er ansvarlig for saken**                              |
| `TABELLNAVNALIAS`      | string  | ja      | Hovedaktør i saken: `ARBGIV`, `PERS`, `SAK`, `SAMH`                   |
| `OBJEKT_ID`            | integer |         | Objektid for hovedaktøren                                             |
| `REG_DATO` / `REG_USER`| string  |         | Opprettet når / av hvem                                               |
| `MOD_DATO` / `MOD_USER`| string  |         | Sist endret når / av hvem                                             |
| `DATO_AVSLUTTET`       | date    |         | Dato saken ble avsluttet                                              |
| `STATUS_ENDRET`        | date-time |       | Tidspunkt for siste statusendring                                     |
| `ER_UTLAND`            | string  | ja      | `J`/`N`                                                               |
| `ARKIVNOKKEL`, `AETATENHET_ARKIV`, `ARKIVHENVISNING`, `PARTISJON` | | | Ikke relevant for oss |
| `OBJEKT_KODE`          | string  |         | Ikke i bruk                                                           |

Merk at `tiltakssak` **ikke** har noen tiltakskode — `SAKSKODE` er alltid `TILT` og sier bare at det er en tiltakssak.
Det er derfor ikke mulig å filtrere ut ekspertbistand fra meldingen alene; korrelasjon mot vår `arena_sak`-tabell er
eneste vei (se regelen under).

### Eksempelmelding — `after`-blokken

```json
{
  "SAK_ID": 13769058,
  "SAKSKODE": "TILT",
  "REG_DATO": "07.01.2026 10.07.01",
  "REG_USER": "ARENA_AP",
  "MOD_DATO": "07.01.2026 10.07.01",
  "MOD_USER": "ARENA_AP",
  "TABELLNAVNALIAS": "SAK",
  "OBJEKT_ID": 13769058,
  "AAR": 2026,
  "LOPENRSAK": 202,
  "SAKSTATUSKODE": "AKTIV",
"BRUKERID_ANSVARLIG": "1899",
  "AETATENHET_ANSVARLIG": "1899",
  "STATUS_ENDRET": "07.01.2026 10.07.01",
  "ER_UTLAND": "N"
}
```

Dette er en melding **før** saken er tatt til behandling (`BRUKERID_ANSVARLIG == AETATENHET_ANSVARLIG == "1899"`), og
skal ignoreres av regelen under.

Saksnummer utledes som `asSaksnummer(aar = 2026, loepenrSak = 202)` → `"2026202"` (se `arena/ArenaClient.kt`).

### Konkret regel som skal implementeres

En sak regnes som tatt til behandling i Arena når **alle** disse holder:

1. `op_type == "U"` — kun oppdateringer er interessante. `I` er saksopprettelsen (der er `BRUKERID_ANSVARLIG` per
   definisjon fortsatt behandlende enhet), og `D` har ingen `after`.
2. `after.BRUKERID_ANSVARLIG` er satt **og** forskjellig fra `after.AETATENHET_ANSVARLIG`.
3. Saksnummeret (`AAR` + `LOPENRSAK`) matcher en rad i vår `arena_sak`-tabell — altså at det er *vi* som har opprettet
   saken.

**Hvorfor regel 2 er formulert som en tilstandssammenligning og ikke som en `before`/`after`-diff.** Feltene betyr to
forskjellige ting: `BRUKERID_ANSVARLIG` er *saksbehandlerens Arena-ident*, `AETATENHET_ANSVARLIG` er *Nav-enheten*. At
begge er `"1899"` ved opprettelse er en konsekvens av at Tiltaksgjennomfoering API setter behandlende enhet som
tiltaksansvarlig. Så snart en person eier saken, divergerer feltene.

En `before.BRUKERID_ANSVARLIG != after.BRUKERID_ANSVARLIG`-diff ville vært mer presis for *øyeblikket* endringen skjer,
men den fanger kun selve overgangen. Tilstandsregelen fanger i tillegg saker som allerede var tatt før vi begynte å
konsumere, ved neste vilkårlige oppdatering av saken — og den fungerer under backfill (se punkt 7). Vi bruker derfor
tilstandsregelen som beslutningsgrunnlag.

`before` skal likevel leses og **logges** når `op_type == "U"`: logg `before.BRUKERID_ANSVARLIG` sammen med
`after.BRUKERID_ANSVARLIG` og et avledet flagg `brukeridEndret`. Det gir oss data til å bekrefte at
tilstandsregelen og den faktiske feltendringen sammenfaller i praksis, og åpner for å stramme inn senere.

**Sekundært signal (kun logging, ikke beslutningsgrunnlag):** `BRUKERID_ANSVARLIG` som ser ut som en Arena-ident
framfor et enhetsnummer.

Merk at dette er *Arena-identer*, ikke NAV-identer (`A123456`). Observerte verdier i dev: `KG0219`, `KGB0219` — altså
varierende antall bokstaver etterfulgt av siffer. Ikke anta ett bestemt antall av noen av delene.

Den underliggende forskjellen er enklere enn formatet: en Nav-enhet er **rent numerisk** (`1899`), mens en saksbehandlers
Arena-ident **inneholder bokstaver**. Bruk derfor et mønster som kun krever bokstaver etterfulgt av siffer, og logg
verdien selv sammen med flagget slik at vi kan se faktiske formater i produksjon.

## Referanser

- [Arena - Tjeneste Kafka - Tiltakssak](https://confluence.adeo.no/spaces/ARENA/pages/478256186/Arena+-+Tjeneste+Kafka+-+Tiltakssak)
  — **feltdokumentasjon for topicet fra Team Arena.** Kilden til feltbeskrivelsene over
- `backend/src/main/kotlin/no/nav/ekspertbistand/arena/ArenaTiltaksgjennomforingEndretProcessor.kt` — nærmeste mønster å
  kopiere
- `backend/src/main/kotlin/no/nav/ekspertbistand/arena/ArenaTilsagnsbrevProcessor.kt` — idempotens + `EventQueue.publish`
  i samme transaksjon
- `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Kafka.kt` — `CoroutineKafkaConsumer`,
  `ConsumerRecordProcessor`
- `backend/src/main/kotlin/no/nav/ekspertbistand/arena/Db.kt` — `ArenaSakTable`, `ArenaMeldingIdempotencyTable`
- `adr/0001-asynkron-prosessering-med-event-ko.md` — event-kø og event-prosessorer
- `backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md`

## ⚠️ Eksterne avhengigheter

1. **ACL på topicet.** Aiven-topics fra Arena styres i `navikt/arena-iac`. `ekspertbistand-backend` må legges til som
   konsument på topicet der. Bestill hos Team Arena. Ingen endring i våre NAIS-manifester er nødvendig utover
   `kafka.pool`, som allerede er satt (`nav-dev` / `nav-prod`).

Meldingsformat og topic-navn er avklart: formatet er dokumentert i Confluence-siden over, og topic-navnene følger samme
konvensjon som `ArenaTiltaksgjennomforingEndretProcessor.TOPIC` — `-v1-q2` i dev, `-v1-p` i prod.

## Implementasjonsplan

### 1. Indeks på `arena_sak.saksnummer` — påkrevd, ikke valgfritt

`arena_sak` har i dag **ingen indekser i det hele tatt** — ingen primærnøkkel, ingen sekundærindekser (se
`V1__initial_setup.sql` linje 12). Det har vært uproblematisk så lenge tabellen kun leses ved sjeldne
tilsagnsbrev-/tiltaksgjennomføring-meldinger.

Denne consumeren endrer forutsetningen fundamentalt: `aapen-arena-tiltakssakendret-v1` bærer **alle** tiltakssaker i
Arena, ikke bare ekspertbistand. Hver melding som passerer forfiltrene fører til et oppslag på `saksnummer`, og uten
indeks blir hvert oppslag en full table scan. I tillegg replayes hele topicet ved første oppstart (se punkt 7), så
belastningen kommer i en konsentrert bolk.

Indeksen skal derfor inn i samme migrasjon som resten av endringen, og consumeren skal ikke settes i drift uten den.
`saksnummer` er unik per sak i Arena, så en unik indeks er riktig — den dokumenterer invarianten og gir samme
oppslagsytelse:

```kotlin
// arena/Db.kt
object ArenaSakTable : Table("arena_sak") {
    val saksnummer = text("saksnummer").uniqueIndex()
    // ...
}
```

Kjør en `SELECT saksnummer, count(*) FROM arena_sak GROUP BY saksnummer HAVING count(*) > 1` mot dev og prod før
migrasjonen kjøres. Hvis det finnes duplikater, fall tilbake til `.index()` (ikke-unik) og opprett en egen sak på å
rydde dataene — migrasjonen må ikke feile på deploy.

### 2. Databasemigrasjon — `V4__arena_sak_under_behandling.sql`

**Migrasjoner genereres, ikke håndskrives.** Se `backend/src/test/kotlin/no/nav/ekspertbistand/CreateMigration.kt`:
oppdater `main()` til å peke på de nye/endrede tabellene og `scriptName = "V4__arena_sak_under_behandling"`, kjør den,
og commit det genererte scriptet. Verifiser innholdet manuelt etterpå — spesielt at indeksen fra punkt 1 faktisk er med.

Migrasjonen skal inneholde:

**a) Indeksen på `arena_sak.saksnummer`** fra punkt 1.

**b) Ny tabell `arena_sak_under_behandling`** som holder tilstanden vi eksponerer:

| kolonne                | type      | kommentar                                            |
|------------------------|-----------|------------------------------------------------------|
| `sak_id`               | INT       | PRIMARY KEY. Arena `sak_id` fra meldingen             |
| `saksnummer`           | TEXT      | `aar` + `lopenrsak`, matcher `arena_sak.saksnummer`   |
| `soknad_id`            | UUID      | vår søknad                                            |
| `brukerid_ansvarlig`   | TEXT      | saksbehandlerens brukerident i Arena                  |
| `aetatenhet_ansvarlig` | TEXT      | nullable                                              |
| `sakstatuskode`        | TEXT      | nullable                                              |
| `observert_at`         | TIMESTAMP | når vi først registrerte at saken var tatt            |

### 3. Utvid `arena/Db.kt`

```kotlin
object ArenaSakUnderBehandlingTable : Table("arena_sak_under_behandling") {
    val sakId = integer("sak_id")
    val saksnummer = text("saksnummer")
    val soknadId = uuid("soknad_id")
    val brukeridAnsvarlig = text("brukerid_ansvarlig")
    val aetatenhetAnsvarlig = text("aetatenhet_ansvarlig").nullable()
    val sakstatuskode = text("sakstatuskode").nullable()
    val observertAt = timestamp("observert_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(sakId)
}
```

Indeksen på `ArenaSakTable.saksnummer` (punkt 1) hører hjemme i denne filen — legg den på kolonnedefinisjonen slik at
migrasjonsgeneratoren plukker den opp.

Nye funksjoner i samme fil, etter mønster av de eksisterende:

```kotlin
fun markerArenaSakUnderBehandling(
    sakId: Int,
    saksnummer: Saksnummer,
    soknadId: UUID,
    brukeridAnsvarlig: String,
    aetatenhetAnsvarlig: String?,
    sakstatuskode: String?,
): Boolean   // insertIgnore, returnerer insertedCount > 0

fun erArenaSakUnderBehandling(soknadId: UUID): ArenaBehandlingStatus?
```

Utvid `ArenaMeldingType`-enumet med `TILTAKSSAK_ENDRET`, og legg til:

```kotlin
fun markerTiltakssakEndretMeldingSomBehandlet(sakId: Int) =
    markerArenaMeldingSomBehandlet(ArenaMeldingType.TILTAKSSAK_ENDRET, sakId)
```

**Merk om idempotens:** nøkkelen er `sak_id`, som betyr at vi kun reagerer på den *første* observerte
ansvarlig-endringen per sak. Senere omfordelinger mellom saksbehandlere i Arena ignoreres. Det er tilstrekkelig for
formålet — vi trenger kun å vite *at* saken behandles i Arena, ikke av hvem til enhver tid. Dokumenter dette i en
KDoc-kommentar.

### 4. Nytt event i `event/Events.kt`

```kotlin
@Serializable
@SerialName("saksbehandlingStartetIArena")
data class SaksbehandlingStartetIArena(
    val soknad: DTO.Soknad,
    val tiltakssakEndret: TiltakssakEndret,
) : EventData
```

### 5. Ny prosessor — `arena/ArenaTiltakssakEndretProcessor.kt`

Følg strukturen i `ArenaTiltaksgjennomforingEndretProcessor` nøye: samme konstruktørsignatur
(`database: Database, startProcessingAt: Instant`), samme `startProcessingAt`-sjekk, samme tombstone-håndtering, samme
`logger()` / `teamLogger()`-bruk (personidentifiserende data og hele records kun til `teamLog`), samme
`companion object` med `TOPIC`, `kafkaConfig` og `consumer by lazy`.

Topic-navnene følger nøyaktig samme konvensjon som `ArenaTiltaksgjennomforingEndretProcessor.TOPIC` — `-q2` i dev,
`-p` i prod:

```kotlin
companion object {
    /**
     * Feltdokumentasjon:
     * https://confluence.adeo.no/spaces/ARENA/pages/478256186/Arena+-+Tjeneste+Kafka+-+Tiltakssak
     *
     * Topic:
     * https://github.com/navikt/arena-iac/tree/main/kafka-aiven/aapen-arena-tiltakssakendret-v1
     */
    val TOPIC = basedOnEnv(
        dev = "teamarenanais.aapen-arena-tiltakssakendret-v1-q2",
        other = "teamarenanais.aapen-arena-tiltakssakendret-v1-p",
    )

    val kafkaConfig = KafkaConsumerConfig(
        groupId = "fager.ekspertbistand.tiltakssakendret",
        topics = setOf(TOPIC),
        // midlertidig — se punkt 7. Settes til NONE når consumeren er i drift i prod.
        autoOffsetReset = AutoOffsetReset.EARLIEST,
    )

    val consumer by lazy { CoroutineKafkaConsumer(kafkaConfig) }
}
```

#### DTO-er

Formatet er dokumentert (se «Meldingsformat» over) og er identisk i struktur med
`TiltaksgjennomforingEndretKafkaMelding` / `TiltaksgjennomforingEndret`. Modeller det på samme måte — konvolutt +
radobjekt, `@SerialName` med UPPERCASE kolonnenavn, `Json { ignoreUnknownKeys = true }`. Ingen fallback til andre
formater er nødvendig.

```kotlin
@Serializable
data class TiltakssakEndretKafkaMelding(
    @SerialName("op_type")
    val opType: String? = null,
    val table: String? = null,
    @SerialName("op_ts")
    val opTs: String? = null,
    val before: TiltakssakEndret? = null,
    val after: TiltakssakEndret? = null,
) {
    val erOppdatering: Boolean get() = opType == "U"
}

@Serializable
data class TiltakssakEndret(
    @SerialName("SAK_ID")
    val sakId: Int,
    @SerialName("SAKSKODE")
    val sakskode: String? = null,
    @SerialName("AAR")
    val aar: Int,
    @SerialName("LOPENRSAK")
    val lopenrsak: Int,
    @SerialName("SAKSTATUSKODE")
    val sakstatuskode: Sakstatuskode? = null,
    @SerialName("BRUKERID_ANSVARLIG")
    val brukeridAnsvarlig: String? = null,
    @SerialName("AETATENHET_ANSVARLIG")
    val aetatenhetAnsvarlig: String? = null,
    @SerialName("MOD_USER")
    val modUser: String? = null,
    @SerialName("MOD_DATO")
    val modDato: String? = null,
) {
    val saksnummer: Saksnummer get() = asSaksnummer(aar = aar, loepenrSak = lopenrsak)

    /** SAKSKODE er alltid TILT på dette topicet, men vi verifiserer for sikkerhets skyld. */
    val erTiltakssak: Boolean get() = sakskode == "TILT"

    /**
     * BRUKERID_ANSVARLIG er saksbehandlerens Arena-ident, AETATENHET_ANSVARLIG er Nav-enheten.
     * Ved opprettelse via Tiltaksgjennomfoering API settes behandlende enhet som tiltaksansvarlig,
     * slik at begge feltene har samme verdi. Når en saksbehandler tar saken settes
     * BRUKERID_ANSVARLIG til vedkommendes brukerident, og feltene divergerer.
     */
    val erTattAvSaksbehandler: Boolean
        get() = !brukeridAnsvarlig.isNullOrBlank() &&
                brukeridAnsvarlig != aetatenhetAnsvarlig

    /**
     * Kun for logging/verifisering — ikke beslutningsgrunnlag.
     *
     * Arena-identer, ikke NAV-identer. Observerte verdier i dev: KG0219, KGB0219 — varierende antall
     * bokstaver etterfulgt av siffer. Ikke lås antallet i noen av delene.
     *
     * Det som faktisk skiller er at en Nav-enhet er rent numerisk (1899) mens en Arena-ident
     * inneholder bokstaver.
     */
    val brukeridLiknerSaksbehandlerIdent: Boolean
        get() = brukeridAnsvarlig?.matches(Regex("^[A-ZÆØÅ]+\\d+$", RegexOption.IGNORE_CASE)) == true

    enum class Sakstatuskode {
        AKTIV,   // Aktiv
        AVSLU,   // Lukket
        HIST,    // Historisert
        INAKT,   // Inaktiv
        OPRTV,   // Opprettet (RTV)
    }
}
```

`Sakstatuskode` modelleres som enum siden verdisettet er dokumentert. Bruk
`Json { ignoreUnknownKeys = true; coerceInputValues = true }` eller gjør feltet til `String?` hvis en ukjent verdi ellers
ville felt hele consumeren — en uventet statuskode skal ikke stoppe prosesseringen, siden vi ikke tar beslutninger på
feltet.

Vi filtrerer **ikke** på `SAKSTATUSKODE`. En sak som er `AVSLU`/`HIST` er ferdigbehandlet i Arena, og det er fortsatt
relevant å advare om.

#### Parsing

Følg `ArenaTiltaksgjennomforingEndretProcessor` direkte:

```
1. Dekod value til TiltakssakEndretKafkaMelding.
2. Ved parsefeil: teamLog.error med hele recorden, og kast Exception med record.key() i meldingen
   (dette stopper consumeren og forhindrer tap av meldinger).
3. after == null -> log.info med op_type og return (gjelder op_type == "D").
```

#### Prosesseringsflyt

```
1. record.timestamp() < startProcessingAt   -> return
2. record.value() == null (tombstone)       -> return
3. parse melding                             -> ved feil: kast
4. !melding.erOppdatering (op_type != "U")   -> return   (billig filter, ingen db-treff)
5. after == null                             -> return
6. !after.erTiltakssak                       -> return   (billig filter, ingen db-treff)
7. !after.erTattAvSaksbehandler              -> return   (billig filter, ingen db-treff)
8. slå opp arena_sak på after.saksnummer:
     hentArenaSakBySaksnummer(after.saksnummer) { Json.decodeFromString<DTO.Soknad>(this[ArenaSakTable.soknad]) }
   - null  -> log.info (sak i Arena vi ikke er kilde til — Altinn-æra eller opprettet direkte i Arena). return.
   - ellers fortsett.
9. i én transaction(database):
     val ikkeTidligereBehandlet = markerTiltakssakEndretMeldingSomBehandlet(after.sakId)
     if (ikkeTidligereBehandlet) EventQueue.publish(EventData.SaksbehandlingStartetIArena(soknad, after))
     else log.info("... allerede behandlet, hopper over")
10. I dev-gcp (NaisEnvironment.clusterName == "dev-gcp"): log.info hele meldingen,
    samme mønster som ArenaTiltaksgjennomforingEndretProcessor.
```

Steg 4, 6 og 7 er avgjørende for ytelse: topicet bærer alle tiltakssaker i Arena, og de aller fleste meldinger skal
forkastes uten databaseoppslag. `op_type`-sjekken i steg 4 er den billigste og filtrerer bort alle innsettinger og
slettinger.

**Logging for verifisering.** Rett før steg 8, når saken passerer forfiltrene, logg til `teamLog`:
`before?.brukeridAnsvarlig`, `after.brukeridAnsvarlig`, `after.aetatenhetAnsvarlig`,
`brukeridEndret = before?.brukeridAnsvarlig != after.brukeridAnsvarlig` og `after.brukeridLiknerSaksbehandlerIdent`.
Dette lar
oss i ettertid bekrefte at tilstandsregelen sammenfaller med en reell endring av feltet, og vurdere å bytte til en ren
`before`/`after`-diff senere.

### 6. Registrer consumeren i `arena/ArenaConsumers.kt`

Legg til en tredje blokk etter samme mønster som de to eksisterende. Merk at denne consumeren **ikke** skal bruke den
delte `startKafkaProsesseringAt`-konstanten — se begrunnelse i punkt 7:

```kotlin
// Arena TiltakssakEndret Processor
CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
    ArenaTiltakssakEndretProcessor(
        dependencies.resolve(),
        startTiltakssakProsesseringAt
    ).startProcessing()
}
```

I samme fil, ved siden av `startKafkaProsesseringAt`:

```kotlin
/**
 * Denne consumeren skal lese hele topicets historikk ved første oppstart, slik at saker som allerede er
 * tatt til behandling i Arena blir markert (backfill). Idempotens på sak_id gjør replay trygt.
 */
val startTiltakssakProsesseringAt: Instant = Instant.EPOCH
```

### 7. Offset-håndtering — `EARLIEST` nå, `NONE` etter produksjonssetting

`CoroutineKafkaConsumer` hardkoder i dag `AUTO_OFFSET_RESET_CONFIG = "none"` for alle consumere. Det er riktig for de
to eksisterende — de har committede offsets, og `none` sikrer at vi feiler høylytt i stedet for å hoppe over meldinger
— men det gjør at en helt ny consumer group ikke kan starte i det hele tatt. Se KDoc-en øverst i
`infrastruktur/Kafka.kt`, som beskriver nettopp dette.

Løsningen er å gjøre innstillingen konfigurerbar per consumer, kjøre den nye med `earliest` frem til den er etablert i
prod, og deretter sette den til `none` som de andre.

**a) Utvid `infrastruktur/Kafka.kt`:**

```kotlin
enum class AutoOffsetReset(val value: String) {
    EARLIEST("earliest"),
    NONE("none"),
}

data class KafkaConsumerConfig(
    val topics: Set<String>,
    val groupId: String,
    val autoOffsetReset: AutoOffsetReset = AutoOffsetReset.NONE,
)
```

I `properties`-blokken: `put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetReset.value)`.

Defaultverdien `NONE` gjør endringen kildekompatibel — `ArenaTilsagnsbrevProcessor` og
`ArenaTiltaksgjennomforingEndretProcessor` beholder dagens oppførsel uten å røres.

**b) Etterarbeid etter produksjonssetting.** Når consumeren har kjørt i prod og har committet offsets, endres
`ArenaTiltakssakEndretProcessor.kafkaConfig` til `AutoOffsetReset.NONE` og `startTiltakssakProsesseringAt` vurderes satt
til deploy-tidspunktet. Opprett en oppfølgingssak på dette som del av PR-en — `earliest` skal ikke bli permanent, siden
det betyr at consumeren stille hopper til starten av topicet hvis offsets forsvinner, i stedet for å feile.

**Konsekvens av `earliest` + `Instant.EPOCH`:** ved første oppstart leses hele retention-vinduet. Det er ønsket
oppførsel her — det gir oss backfill av saker som allerede er tatt til behandling i Arena, og det er nettopp de sakene
som har høyest risiko for dobbeltbehandling akkurat nå. Idempotensnøkkelen på `sak_id` gjør replay trygt, og forfiltrene
i punkt 5 gjør at de aller fleste meldingene forkastes uten databaseoppslag. Indeksen fra punkt 1 **må** være på plass
før dette kjøres.

### 8. Ny event handler — `event/handlers/MarkerSakUnderBehandlingIArena.kt`

Følg `SettAvlystSoknadStatus` som mønster.

```kotlin
class MarkerSakUnderBehandlingIArena(
    private val database: Database,
) : EventHandler<EventData.SaksbehandlingStartetIArena> {
    override val id = "Marker sak under behandling i Arena"
    override val eventType = EventData.SaksbehandlingStartetIArena::class

    override suspend fun handle(event: Event<EventData.SaksbehandlingStartetIArena>): EventHandledResult {
        // soknad.id null -> unrecoverableError
        // transaction: markerArenaSakUnderBehandling(...)
        // insertIgnore -> allerede markert er OK, returner success()
        // exception -> rollback() + transientError(...)
    }
}
```

**Viktig:** søknadens `status` (`SoknadStatus`: `utkast`, `innsendt`, `godkjent`, `avlyst`) skal **ikke** endres. Dette
er en advarsel som lever ved siden av statusen, ikke en tilstandsovergang i vår saksflyt.

Registrer handleren i `configureEventHandlers()` i `event/Events.kt`:

```kotlin
register(dependencies.create(MarkerSakUnderBehandlingIArena::class))
```

### 9. Eksponer på saksbehandler-API-et

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlerApi.kt`

Nytt endepunkt under den eksisterende `authenticate(AZURE_AD_PROVIDER)` /
`route("/api/saksbehandling/v1")`-blokken:

**GET** `/api/saksbehandling/v1/soknad/{soknadId}/arena-behandling`

`200 OK`:

```json
{
  "underBehandlingIArena": true,
  "brukeridAnsvarlig": "K123456",
  "aetatenhetAnsvarlig": "1899",
  "observertAt": "2026-08-05T09:14:22"
}
```

Når saken ikke er markert:

```json
{ "underBehandlingIArena": false }
```

`400 Bad Request` ved ugyldig UUID. `401 Unauthorized` uten gyldig token.

```kotlin
@Serializable
data class ArenaBehandlingStatus(
    val underBehandlingIArena: Boolean,
    val brukeridAnsvarlig: String? = null,
    val aetatenhetAnsvarlig: String? = null,
    val observertAt: String? = null,
)
```

Legg i tillegg til feltet `underBehandlingIArena: Boolean = false` på `OversiktRad`, slik at listevisningen kan merke
radene. `/oversikt` returnerer i dag stubbede data (`stubbedOversikt`) — sett feltet på stub-radene og legg igjen en
`TODO`-kommentar om å fylle det fra `arena_sak_under_behandling` når oversikten kobles mot ekte data.

### 10. Metrikk

Legg til en teller i `AppMetrics.kt`-stil, eller registrer direkte mot `Metrics.meterRegistry` i handleren:
`ekspertbistand_arena_sak_under_behandling_total`. Det gjør det mulig å se hvor ofte dobbeltbehandling faktisk oppstår
under utfasingen — nyttig styringsinformasjon for når Arena kan skrus av.

### 11. Tester

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/arena/ArenaTiltakssakEndretProcessorTest.kt`,
`backend/src/test/kotlin/no/nav/ekspertbistand/event/handlers/MarkerSakUnderBehandlingIArenaTest.kt` og en liten test
for Kafka-konfigurasjonen.

Bruk eksisterende testharness — `ArenaTiltaksgjennomforingEndretProcessorTest` og `ArenaMeldingIdempotencyTest` er
direkte forbilder. `TestDatabase` for databasetester. Ingen nye testbiblioteker.

Bruk eksempelmeldingen fra «Meldingsformat» som utgangspunkt for testfixtures, pakket i konvolutten.

Prosessor-tester:

1. `op_type = "U"`, `after.BRUKERID_ANSVARLIG == after.AETATENHET_ANSVARLIG` (`"1899"`/`"1899"`) → ingen event
2. `op_type = "U"`, feltene divergerer (`"K123456"`/`"1899"`), saksnummer finnes i `arena_sak` →
   `SaksbehandlingStartetIArena` publisert
3. Som 2, men saksnummer finnes **ikke** i `arena_sak` → ingen event, ingen exception
4. `op_type = "I"` med divergerende felter → ingen event (kun oppdateringer er interessante)
5. `op_type = "D"` (`after: null`) → ingen event, ingen exception
6. `SAKSKODE != "TILT"` → ingen event
7. Samme `SAK_ID` to ganger → kun ett event (idempotens)
8. Tombstone (`value == null`) → ingen event, ingen exception
9. `record.timestamp()` før `startProcessingAt` → ingen event
10. Konvolutt med UPPERCASE kolonnenavn parses korrekt til `TiltakssakEndret`, inkl. `SAKSTATUSKODE` → enum
11. Ukjent `SAKSTATUSKODE`-verdi feller ikke prosesseringen
12. Ukjente felter i `after` (f.eks. `PARTISJON`, `ARKIVNOKKEL`) ignoreres
13. `before` er tilgjengelig og logges ved `op_type = "U"`
14. Ugyldig JSON → kaster exception (slik at consumeren ikke committer offset)
14b. `brukeridLiknerSaksbehandlerIdent` er `true` for `KG0219` og `KGB0219` (ekte verdier fra dev), og `false` for
     `1899` og `null`. Merk at flagget kun er logging — regelen som faktisk avgjør er
     `BRUKERID_ANSVARLIG != AETATENHET_ANSVARLIG`, og en Arena-ident i et uventet format skal derfor **ikke** hindre
     at saken markeres. Legg til en test som bekrefter nettopp det: divergerende felter der
     `BRUKERID_ANSVARLIG = "XYZ"` (matcher ikke mønsteret) → event publiseres likevel

Kafka-konfigurasjon:

15. `KafkaConsumerConfig` uten `autoOffsetReset` gir `none` (verifiserer at eksisterende consumere ikke endrer
    oppførsel), og `AutoOffsetReset.EARLIEST` gir `earliest`

Handler-tester:

16. Happy path skriver rad i `arena_sak_under_behandling`
17. Kall to ganger for samme `sak_id` → `success()`, én rad
18. `soknad.id == null` → `unrecoverableError`
19. Søknadens `status` er uendret etter håndtering

API-test i `saksbehandling/SaksbehandlerRoutingTest.kt`:

20. Markert sak → `underBehandlingIArena: true` med felter satt
21. Umarkert sak → `underBehandlingIArena: false`
22. Uten token → 401

## Filstruktur — nye/endrede filer

```
backend/src/main/kotlin/no/nav/ekspertbistand/
├── arena/
│   ├── ArenaTiltakssakEndretProcessor.kt     (ny)
│   ├── ArenaConsumers.kt                     (endret — start ny consumer + startTiltakssakProsesseringAt)
│   └── Db.kt                                 (endret — ny tabell, unik index på saksnummer,
│                                              ArenaMeldingType.TILTAKSSAK_ENDRET, hjelpefunksjoner)
├── infrastruktur/
│   └── Kafka.kt                              (endret — AutoOffsetReset enum + felt på KafkaConsumerConfig,
│                                              default NONE så eksisterende consumere er uendret)
├── event/
│   ├── Events.kt                             (endret — SaksbehandlingStartetIArena + register handler)
│   └── handlers/
│       └── MarkerSakUnderBehandlingIArena.kt (ny)
├── saksbehandling/
│   └── SaksbehandlerApi.kt                   (endret — nytt endepunkt + felt på OversiktRad)

backend/src/main/resources/db/migration/
└── V4__arena_sak_under_behandling.sql        (ny — generert via CreateMigration.kt)

backend/src/test/kotlin/no/nav/ekspertbistand/
├── CreateMigration.kt                        (endret — peker på V4)
├── arena/ArenaTiltakssakEndretProcessorTest.kt        (ny)
├── event/handlers/MarkerSakUnderBehandlingIArenaTest.kt (ny)
├── infrastruktur/KafkaConsumerConfigTest.kt   (ny)
└── saksbehandling/SaksbehandlerRoutingTest.kt (endret)
```

Ingen endringer i `nais/*.yaml` — `kafka.pool` er allerede satt. ACL bestilles i `navikt/arena-iac`.

## Avgrensninger

- Saker som finnes i Arena men ikke i vår `arena_sak`-tabell (sendt inn via Altinn 2, eller opprettet direkte i Arena)
  ignoreres. Vi har ingen søknad å markere.
- Vi reagerer kun på første observerte ansvarlig-endring per sak. Omfordeling mellom saksbehandlere i Arena gir ingen
  ny hendelse.
- Ingen «avmarkering». Når en sak først er markert som under behandling i Arena, forblir den det.
- Ingen frontend-endringer. Advarselen i `frontend/saksbehandling` spesifiseres separat, og bygger på endepunktet i
  punkt 9.
- Ingen blokkering. Vår app skal fortsatt tillate saksbehandling — kun advare.

## Acceptance Criteria

- [ ] `ArenaTiltakssakEndretProcessor` implementerer `ConsumerRecordProcessor` og følger samme struktur som
      `ArenaTiltaksgjennomforingEndretProcessor` (konstruktør, `startProcessingAt`-sjekk, tombstone, `logger()`/
      `teamLogger()`, `companion object` med `TOPIC`/`kafkaConfig`/`consumer by lazy`)
- [ ] Topic satt via `basedOnEnv` med `-v1-q2` i dev og `-v1-p` i prod, samme konvensjon som
      `ArenaTiltaksgjennomforingEndretProcessor.TOPIC`
- [ ] `groupId = "fager.ekspertbistand.tiltakssakendret"`
- [ ] `TiltakssakEndretKafkaMelding` / `TiltakssakEndret` modellert etter dokumentasjonen med `@SerialName` og UPPERCASE
      kolonnenavn, på samme form som `TiltaksgjennomforingEndretKafkaMelding` / `TiltaksgjennomforingEndret`
- [ ] `SAKSTATUSKODE` modellert som enum (`AKTIV`, `AVSLU`, `HIST`, `INAKT`, `OPRTV`), og en ukjent verdi feller ikke
      consumeren
- [ ] Ukjente felter ignoreres (`ignoreUnknownKeys = true`)
- [ ] `after: null` (`op_type = "D"`) og tombstone gir ingen event og ingen exception
- [ ] Ugyldig JSON kaster exception slik at offset ikke committes
- [ ] Meldinger filtreres på `op_type == "U"`, `SAKSKODE == "TILT"` og
      `BRUKERID_ANSVARLIG != AETATENHET_ANSVARLIG` **før** databaseoppslag
- [ ] Det filtreres **ikke** på `SAKSTATUSKODE` — avsluttede saker skal også markeres
- [ ] `before.BRUKERID_ANSVARLIG`, `after.BRUKERID_ANSVARLIG` og avledet `brukeridEndret` logges til `teamLog` for
      saker som passerer forfiltrene
- [ ] `brukeridLiknerSaksbehandlerIdent` bruker et mønster uten fast antall bokstaver eller siffer
      (`^[A-ZÆØÅ]+\d+$`, ignore case) — verifisert mot `KG0219` og `KGB0219`. Flagget er **kun logging** og påvirker
      ikke om saken markeres
- [ ] Korrelasjon skjer på `asSaksnummer(AAR, LOPENRSAK)` mot `ArenaSakTable.saksnummer`
- [ ] Saker som ikke finnes i `arena_sak` logges og ignoreres uten feil
- [ ] Idempotens via `ArenaMeldingIdempotencyTable` med ny `ArenaMeldingType.TILTAKSSAK_ENDRET` og `SAK_ID` som
      `ekstern_id`, i samme transaksjon som `EventQueue.publish`
- [ ] `EventData.SaksbehandlingStartetIArena` er `@Serializable` med `@SerialName("saksbehandlingStartetIArena")`
- [ ] `MarkerSakUnderBehandlingIArena` registrert i `configureEventHandlers()`
- [ ] Handleren skriver til `arena_sak_under_behandling` og endrer **ikke** `SoknadTable.status`
- [ ] Migrasjon `V4` generert via `CreateMigration.kt`, inneholder både indeks på `arena_sak.saksnummer` og den nye
      tabellen
- [ ] `uniqueIndex()` lagt på `ArenaSakTable.saksnummer` i Exposed-modellen (eller `index()` hvis duplikatsjekken mot
      dev/prod viser duplikater — da med egen oppfølgingssak)
- [ ] Duplikatsjekk på `arena_sak.saksnummer` kjørt mot dev og prod før migrasjonen deployes
- [ ] `AutoOffsetReset`-enum lagt til i `infrastruktur/Kafka.kt`, og `KafkaConsumerConfig.autoOffsetReset` har default
      `NONE` slik at de to eksisterende consumerne er uendret
- [ ] Ny consumer kjører med `AutoOffsetReset.EARLIEST` og `startTiltakssakProsesseringAt = Instant.EPOCH`
- [ ] Oppfølgingssak opprettet for å sette consumeren til `AutoOffsetReset.NONE` etter produksjonssetting
- [ ] `GET /api/saksbehandling/v1/soknad/{soknadId}/arena-behandling` returnerer `ArenaBehandlingStatus`, beskyttet med
      `authenticate(AZURE_AD_PROVIDER)`
- [ ] `OversiktRad` utvidet med `underBehandlingIArena: Boolean = false` og `TODO` for ekte data
- [ ] Metrikk `ekspertbistand_arena_sak_under_behandling_total`
- [ ] Consumeren startet i `startKafkaConsumers()`
- [ ] Alle 22 testene i punkt 11 implementert med eksisterende testharness, ingen nye testbiblioteker
- [ ] Confluence-lenken til feltdokumentasjonen er med som KDoc på `ArenaTiltakssakEndretProcessor`, etter mønster fra
      `ArenaTilsagnsbrevProcessor`
- [ ] Personidentifiserende data og hele Kafka-records logges kun via `teamLogger()`
