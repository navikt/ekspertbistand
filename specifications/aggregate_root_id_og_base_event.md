# aggregateRootId og base-event i event-modellen

## Mål

Innføre et felles `aggregateRootId` på alle events, slik at hver hendelse i `event_queue` og `event_log` kan knyttes
til aggregatroten den handler om — uten å måtte grave i `event_json` per event-type.

Konkret vil vi:

1. Utvide base-eventet (`EventData`) med et påkrevd `aggregateRootId`, slik at kompilatoren tvinger hvert nytt event til
   å svare på «hvilket aggregat hører dette til?».
2. Legge til en denormalisert kolonne `aggregate_root_id` på `event_queue` og `event_log`.
3. Backfille alle eksisterende rader ved å derivere verdien fra `event_json`.
4. Ende opp med `NOT NULL` + indeks, uten å risikere en hengende Flyway-migrering i prod.

Dette er en forutsetning for å legge til flere events og flere aggregater uten at hver ny spørring må kjenne
payload-strukturen til hver enkelt event-type.

## Bakgrunn

### Dagens tilstand

Køen og loggen er beskrevet i [ADR.md](../ADR.md) og
[backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md](../backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md).

* `event_queue` — arbeidskø. Rad slettes ved `finalize`.
* `event_log` — terminal logg. `event_log.id` er **samme id** som køraden hadde (ikke en ny sekvens).
* `event_json JSON NOT NULL` — serialisert `EventData` med kotlinx-diskriminator `type` (f.eks. `"soknadInnsendt"`).
* `event_handler_states`, `idempotency_guard_records` — nøklet på `event_id`, berøres **ikke** av denne endringen.
* Projeksjoner (`EventLogProjectionBuilder`) leser `event_log` sekvensielt på `id` og plukker søknadsid ut av
  payload per event-type.

Domenemodellen har i praksis **én** aggregatrot i dag: søknaden (`soknad.id`, uuid). Unntaket er tilsagn som kommer
fra Altinn-perioden, der vi ikke har noen søknad — der brukes tilsagnsnummeret som rot.

### Problemet

Uten `aggregateRootId` må all korrelasjon skje via payload-spesifikke uttrykk. Hver ny event-type og hvert nytt
spørrebehov (tidslinje per søknad, «hva skjedde med denne saken?», saksbehandler-API, feilsøking) må gjenta
kunnskapen om hvor søknadsiden ligger i akkurat den payloaden. Det skalerer ikke, og det er den typen kunnskap som
lekker ut i spørringer og projeksjoner.

### Hvorfor migreringen ikke kan gå automatisk på deploy

Flyway kjøres i dag ved oppstart, inne i `provide<Database>` i
[Application.kt](../backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt):

```kotlin
provide<Database> {
    dbConfig.flywayAction { migrate() }
    dbConfig.jdbcDatabase
}
```

En tung `UPDATE` over hele `event_log` i en Flyway-migrering gir tre problemer samtidig:

1. **Full tabell-rewrite** under `ACCESS EXCLUSIVE`-lås → køpolleren og API-et blokkeres så lenge det tar.
2. **Timeout / drept pod** — readiness-proben er ikke i gang under migrering, og k8s eller deploy-jobben kan drepe
   poden midt i transaksjonen. Flyway etterlater da en rad i `flyway_schema_history` med `success = false`, og
   **alle senere deploys feiler** til noen rydder manuelt. Dette er «stuck migration»-tilstanden vi skal unngå.
3. **Flere pods samtidig** — alle prøver å migrere; Flyway tar en advisory lock, så øvrige pods står og venter på en
   migrering som kanskje ikke fullfører.

### Styrende regel for hele planen

> **Flyway får kun utføre DDL som er O(1) (katalog-endringer). Alt som må lese eller skrive rader gjøres av en
> restartbar bakgrunnsjobb i applikasjonen.**

Alle fasene under er utformet etter denne regelen.

## Designbeslutninger

### D1 — `aggregateRootId` som derivert medlem på base-eventet

`EventData` er base-eventet. Det utvides med ett abstrakt medlem:

```kotlin
@Serializable
sealed interface EventData {
    /**
     * Id til aggregatroten hendelsen tilhører.
     *
     * Deriveres fra payload — den lagres bevisst IKKE i event_json, kun i kolonnen
     * aggregate_root_id, slik at vi ikke får to sannheter i samme rad.
     */
    val aggregateRootId: String
}
```

Hver implementasjon overstyrer med en **getter** (ikke konstruktør-parameter):

```kotlin
@Serializable
@SerialName("soknadInnsendt")
data class SoknadInnsendt(
    val soknad: DTO.Soknad,
) : EventData {
    override val aggregateRootId: String
        get() = soknad.aggregateRootId
}
```

Konsekvenser, og hvorfor dette er valgt:

* **Ingen endring i serialisert form.** kotlinx.serialization serialiserer bare konstruktør-properties, så en
  property med custom getter havner ikke i `event_json`. Vi trenger dermed *ingen* payload-migrering, ingen
  schema-versjonering av eksisterende events, og gamle og nye rader er byte-identiske.
* **Kompilator-tvang.** Et nytt event kompilerer ikke uten å svare på spørsmålet. Det var hele poenget.
* **Én sannhet.** Verdien er alltid derivert fra payload; kolonnen er en ren denormalisering for spørring og indeks.

Hjelpere for de to rot-variantene, plassert sammen med typene de gjelder:

```kotlin
// soknad/Api.kt
val DTO.Soknad.aggregateRootId: String
    get() = id ?: error("Soknad mangler id — event kan ikke publiseres før søknaden er persistert")

// tilsagndata/Db.kt (concat() finnes allerede)
val TilsagnData.aggregateRootId: String
    get() = tilsagnNummer.concat()
```

### D2 — Kolonnetype `TEXT`, ikke `uuid`

Søknadsroten er en uuid, men Altinn-sporet bruker tilsagnsnummer på formen `"2026:123:1"` (`TilsagnNummer.concat()`).
Begge skal ligge i samme kolonne, og da kan kolonnen ikke være `uuid`.

Formatene er disjunkte (36 tegn med bindestreker vs. tre kolon-separerte heltall), så det er ingen kollisjonsfare og
vi trenger ingen prefiks. Konvensjonen dokumenteres i kode og i tabellen under [Mapping per event-type](#mapping-per-event-type).

**Vurdert og utsatt:** egen kolonne `aggregate_type` (`SOKNAD` / `TILSAGN`), og prefiksede id-er
(`soknad:<uuid>` / `tilsagn:<nummer>`). Begge er billigere å legge til senere enn å fjerne, og med én reell
aggregatrot i dag er de foreløpig kun støy. Tas opp igjen når aggregat nummer to faktisk dukker opp — se
[P7](#p7--oppfølging).

**Vurdert og utsatt:** `@JvmInline value class AggregateRootId(val value: String)` med navngitte fabrikker.
Gir bedre typesikkerhet og et naturlig sted for konvensjonen, men koster en `.transform()` på Exposed-kolonnen og
touch på alle testene. Vurder som eget, isolert PR etter at denne planen er landet.

### D3 — Denormalisert kolonne på både `event_queue` og `event_log`

Begge tabellene får kolonnen. `finalize` kopierer verdien fra kø til logg, på samme måte som `created_at` og
`attempts` kopieres i dag.

### D4 — `NULL` først, `NOT NULL` til slutt

Kolonnen må være nullbar i selve DDL-steget fordi historiske rader ikke har verdi ennå. Den er **ikke** nullbar som
domeneegenskap: `EventData.aggregateRootId` er `String` (ikke `String?`), så alle *nye* skrivinger har alltid verdi.
Nullbarheten i databasen er utelukkende et migreringsartefakt og fjernes i [P6](#p6--stramme-inn-til-not-null).

Alle historiske event-typer er deriverbare fra payload (se mapping-tabellen), så vi forventer 0 gjenstående
`NULL`-rader etter backfill. `foo`/`bar` finnes kun i tester.

### D5 — DDL i Flyway, backfill i applikasjonen

Se [styrende regel](#styrende-regel-for-hele-planen). Backfill kjøres som en bakgrunnsjobb i backend, etter samme
mønster som `EventLogProjectionBuilder`: `FOR UPDATE SKIP LOCKED` på en state-rad gjør at kun én pod jobber om
gangen, og jobben er restartbar og selv-avsluttende.

### D6 — To eksplisitte publiseringsmetoder, og `EventQueue` som eneste vei inn i køen

I dag settes `event_json` fra seks steder: `EventQueue.publish` pluss fem direkte `QueuedEvents.insert { ... }`
(soknad/Api.kt, JournalfoerInnsendtSoknad, JournalfoerTilskuddsbrev, JournalfoerTilskuddsbrevKildeAltinn,
LagreTilsagnsData). Etter denne endringen må *alle* sette to kolonner riktig, og da er seks kopier av den kunnskapen
fem for mange.

Men problemet er større enn duplisering, og det er verdt å ta først — bokstavelig talt: dette landes som
[P1](#p1--forutsetning-eventqueue-grensesnittet), før noe rører databasen.

#### Dagens `publish` har én signatur og to helt ulike betydninger

`EventQueue.publish` åpner `transaction { }` uten argumenter. Kalt utenfra en transaksjon åpner den sin egen; kalt
*innenfra* en transaksjon blir den med i den (nøstede transaksjoner er av som standard i Exposed). Samme kall, to
vidt forskjellige atomisitets-garantier, avgjort av kontekst kalleren ikke ser.

Kallstedene i dag deler seg nøyaktig i to:

| Kallsted                                        | Kontekst                      | Trenger |
|-------------------------------------------------|-------------------------------|---------|
| `ArenaTilsagnsbrevProcessor`                    | inne i `transaction(database)` | kallerens transaksjon |
| `ArenaTiltaksgjennomforingEndretProcessor`      | inne i `transaction(database)` | kallerens transaksjon |
| `ArenaTiltakssakEndretProcessor`                | inne i `transaction(database)` | kallerens transaksjon |
| `TilsagnDataApi` (4 steder)                     | utenfor transaksjon            | egen transaksjon |
| de fem direkte `QueuedEvents.insert`            | inne i `transaction(database)` | kallerens transaksjon |

De tre Arena-prosessorene er det viktige funnet: alle tre kjører `markerXSomBehandlet(...)` og `publish(...)` i samme
`transaction(database) { }`, og **idempotensen deres avhenger av at de to er atomiske**. Skulle publiseringen havne i
sin egen transaksjon, ville vi kunne markere en Kafka-melding som behandlet uten å ha lagt eventet i køen — eller
motsatt, publisere to ganger. Den garantien hviler i dag utelukkende på udokumentert Exposed-semantikk som ingen i
koden nevner.

Merk hva dette betyr for planen: alternativet er å *dokumentere og teste* at Exposed joiner nøstede kall, og deretter
leve med at garantien er usynlig på kallstedet. Å dele `publish` i to gjør spørsmålet irrelevant i stedet for å måtte
besvares.

#### Løsning: to metoder, der den ene ikke kompilerer i feil kontekst

```kotlin
object EventQueue {
    /**
     * Publiserer eventet i en NY transaksjon.
     *
     * Bruk denne kun når kalleren ikke selv har en transaksjon — typisk fra route-handlere som
     * ikke skriver noe annet. Trenger publiseringen å være atomisk med annet arbeid, er det en
     * feil å bruke denne: bruk [publishInCurrentTransaction].
     */
    fun publishInNewTransaction(ev: EventData): QueuedEvent {
        require(TransactionManager.currentOrNull() == null) {
            "publishInNewTransaction() ble kalt inne i en pågående transaksjon. " +
                "Bruk publishInCurrentTransaction() hvis publiseringen skal være atomisk med kallerens arbeid."
        }
        return transaction { publishInCurrentTransaction(ev) }
    }
}

/**
 * Publiserer eventet i kallerens pågående transaksjon — commiter og rulles tilbake med den.
 *
 * Receiveren er ikke dekorasjon: den er håndhevelsen. Funksjonen finnes ikke utenfor en
 * transaction { }-blokk, så det er umulig å kalle den og tro at man har fått atomisitet.
 */
fun JdbcTransaction.publishInCurrentTransaction(ev: EventData): QueuedEvent =
    QueuedEvents.insertReturning {
        it[eventData] = ev
        it[aggregateRootId] = ev.aggregateRootId
    }.first().tilQueuedEvent()
```

Egenskapene som gjør dette verdt å gjøre nå, mens vi likevel er inne i alle kallstedene:

* **Feil kontekst kompilerer ikke.** `publishInCurrentTransaction` krever en `JdbcTransaction` som receiver. Utenfor
  en `transaction { }`-blokk finnes ingen slik receiver, og kallet er en kompileringsfeil — ikke en subtil
  atomisitets-bug.
* **Motsatt feil fanges ved kjøring.** `require` i `publishInNewTransaction` gjør det umulig å ved uhell få
  «egen transaksjon»-semantikk der man faktisk er inne i en. Dette er bevisst en hard feil framfor en stille: den
  slår ut i første test som treffer kallveien, og alle publiseringsveiene har testdekning.
* **Én implementasjon av selve inserten.** `publishInNewTransaction` delegerer til
  `publishInCurrentTransaction`, så det finnes ett sted som setter `event_json` og `aggregate_root_id`.
* **Kallstedet dokumenterer intensjonen.** Navnet sier hvilken garanti man får, i stedet for at leseren må vite hva
  Exposed gjør med nøstede kall.

Merk: `JdbcTransaction` er typen på `transaction { }`-blokkens receiver i Exposed 1.0 (den het `Transaction` i 0.x).
Eksakt navn og pakke bekreftes mot `1.0.0-rc-2` når koden skrives — det endrer ikke designet, bare importen.

**Vurdert og forkastet:** å kalle begge `publish` (én member, én extension). Overload-oppløsning ville avhengt av
hvilke importer som var i scope, og det er den typen tvetydighet vi nettopp forsøker å bli kvitt.

**Vurdert og utsatt:** `context(tx: JdbcTransaction)` i stedet for receiver. Semantisk mer presist, men
context-parametre er ferske i Kotlin og gir ingen praktisk gevinst her.

#### Migrering av kallstedene

Åtte kall til `publishInCurrentTransaction` (tre Arena-prosessorer + de fem tidligere direkte innsettingene) og fire
til `publishInNewTransaction` (`TilsagnDataApi`). Ingen av dem endrer oppførsel: alle åtte kjørte allerede i
kallerens transaksjon, og alle fire i sin egen. Endringen gjør bare det som skjedde implisitt, eksplisitt — men fra
og med nå kan ingen av dem havne i feil kategori uten at kompilatoren eller en `require` sier fra.

#### Hvordan «eneste vei inn» håndheves

Tre lag, i økende styrke:

1. **KDoc på `QueuedEvents`:** «skriv aldri til denne tabellen direkte — bruk `EventQueue`». Tabellen forblir
   offentlig fordi `AppMetrics` og `EventManager.cleanupFinalizedEvents` leser fra den.
2. **Test som skanner kildekoden:** feiler hvis `QueuedEvents.insert`, `.insertReturning` eller `.upsert` finnes i
   `src/main` utenfor `EventQueue.kt`. Krever ingen nye avhengigheter, og fanger en ny bypass i PR-en som innfører
   den — ikke i prod.
3. **Databasen:** `NOT NULL` på `aggregate_root_id` gjør at *enhver* skrivevei som ikke setter aggregatroten feiler.
   Dette er den egentlige garantien: invarianten «hvert event har en aggregatrot» håndheves av databasen, ikke av
   konvensjon.

To presiseringer om lag 3:

* **Det gjelder først fra [P6](#p6--stramme-inn-til-not-null) for køen.** `V8`-constrainten legges bare på
  `event_log` (se advarselen i [P3](#p3--modell-og-invariant-for-nye-rader) om `UPDATE` mot legacy
  kø-rader), og `event_log` skrives kun av `finalize`. I vinduet P3–P6 er det altså lag 1 og 2 som bærer — og de
  er allerede på plass, siden [P1](#p1--forutsetning-eventqueue-grensesnittet) kommer først.
* **Det håndhever ikke hvilken metode som brukes**, bare at raden er riktig — en bypass som setter begge kolonnene
  korrekt passerer. Det er akseptabelt: det er radens innhold som betyr noe. Atomisiteten er det receiveren og
  `require` tar seg av.

#### Vurdert: full innkapsling av tabellen

Det finnes en sterkere variant: gjør `QueuedEvents` til et **fil-privat** objekt i `EventQueue.kt` og eksponer
lesebehovene som funksjoner på `EventQueue` (`sizeByStatus()`, `retriesByEventType()`, `processingByAgeBucket()`,
`idsInQueue()`). Da er det fysisk umulig å skrive til tabellen utenfra, ikke bare frarådet.

Kostnad: tre spørringer flyttes ut av `AppMetrics`, én ut av `EventManager.cleanupFinalizedEvents`, og ~80
test-referanser må gå via nye hjelpere (testene trenger fortsatt å kunne sette `created_at`, `status` og `attempts`
direkte for å rigge opp aldersbøtter — det krever en `internal fun insertRaw(...)`, som er synlig fra testkilden
siden Kotlin behandler test-sourceset som friend module).

Kotlin har ingen package-private synlighet, så det finnes ingen mellomvariant: enten fil-privat eller offentlig.

**Anbefaling:** lag 1 og 2 i [P1](#p1--forutsetning-eventqueue-grensesnittet), lag 3 følger av `V8`/`V9`. Full
innkapsling som eget, isolert PR i [P7](#p7--oppfølging) — den kjøper ryddighet, ikke sikkerhet, og bør ikke ligge i
samme PR som en datamigrering.

### D7 — Ingen endring i payload i denne runden

Vi *slanker ikke* payloadene nå (f.eks. fjerne innbakt `DTO.Soknad` og heller slå opp søknaden via
`aggregateRootId`). Det er et naturlig neste steg, men det er en semantisk endring av event-loggen og hører hjemme i
en egen leveranse — se [P7](#p7--oppfølging).

## Mapping per event-type

`event_json->>'type'` er kotlinx-diskriminatoren, altså `@SerialName`-verdien.

| `type`                              | Aggregatrot          | Kotlin-derivering                         | SQL-derivering fra `event_json`                         |
|-------------------------------------|----------------------|-------------------------------------------|---------------------------------------------------------|
| `soknadInnsendt`                    | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `innsendtSoknadJournalfoert`        | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `tiltaksgjennomforingOpprettet`     | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `tilskuddsbrevMottatt`              | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `tilskuddsbrevJournalfoert`         | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `soknadAvlystIArena`                | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `saksbehandlingStartetIArena`       | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `TilsagnsdataLagret`                | søknad               | `soknad.aggregateRootId`                  | `->'soknad'->>'id'`                                     |
| `tilskuddsbrevMottattKildeAltinn`   | tilsagn              | `tilsagnData.aggregateRootId`             | `concat_ws(':', aar, loepenrSak, loepenrTilsagn)`       |
| `tilskuddsbrevJournalfoertKildeAltinn` | tilsagn           | `tilsagnData.aggregateRootId`             | `concat_ws(':', aar, loepenrSak, loepenrTilsagn)`       |
| `tilskuddsbrevVist`                 | søknad, ellers tilsagn | `soknad?.aggregateRootId ?: tilsagnNummer` | `coalesce(->'soknad'->>'id', ->>'tilsagnNummer')`     |
| `foo`, `bar`                        | kun test             | `"test:$fooName"` / `"test:$barName"`     | ingen — forventes ikke i prod                           |

Merk to fallgruver:

* `TilsagnsdataLagret` har **stor forbokstav** i `@SerialName`, i motsetning til de øvrige. Ikke skriv
  `type = 'tilsagnsdataLagret'` i noen spørring.
* `tilskuddsbrevMottatt` (ikke-Altinn) har **både** `soknad` og `tilsagnData`. Søknaden er roten, så `coalesce`
  må prioritere `soknad.id`. Det gjør uttrykket under.

### Felles SQL-uttrykk

Ett `coalesce` dekker alle typer, i riktig prioritet:

```sql
coalesce(
    event_json -> 'soknad' ->> 'id',
    nullif(concat_ws(':',
        event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'aar',
        event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrSak',
        event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrTilsagn'
    ), ''),
    event_json ->> 'tilsagnNummer'
)
```

* Kolonnen er `JSON`, ikke `JSONB`. `->` og `->>` fungerer likt på begge; det er kun operatorene `@>`, `?` og
  GIN-indekser vi ikke har. Vi trenger ingen av dem her, så vi lar kolonnetypen ligge (en `JSONB`-konvertering er en
  full rewrite og hører ikke inn i denne leveransen).
* `concat_ws` hopper over `NULL`, så en payload uten `tilsagnData` gir `''` — derfor `nullif(..., '')`, ellers ville
  tom streng «vunnet» over neste ledd i `coalesce`.
* `event_json -> 'soknad'` på en payload der `soknad` er JSON `null` gir JSON-null, og `->> 'id'` på den gir SQL
  `NULL`. `tilskuddsbrevVist` uten søknad faller derfor korrekt gjennom til `tilsagnNummer`.

## Faseplan

Hver fase er én PR og én deploy, og hver fase er trygg å stoppe på. **P1 er en forutsetning for resten** — den
inneholder ingen databaseendring og bør landes og deployes for seg selv før P2 starter.

| Fase | Innhold                                            | Flyway            | Kjøretid    | Reversibel |
|------|----------------------------------------------------|-------------------|-------------|------------|
| P0   | Måle datamengde og velge batch-parametre            | –                 | minutter    | n/a        |
| P1   | **Forutsetning:** EventQueue-grensesnittet — to publiseringsmetoder | –        | ren refaktorering | ja |
| P2   | `ADD COLUMN` + state-tabell                        | `V7`              | O(1)        | ja         |
| P3   | Modell + `finalize` + `CHECK NOT VALID`            | `V8`              | O(1)        | ja         |
| P4   | Backfill-jobb (kjører til ferdig)                  | –                 | minutter–timer | ja      |
| P5   | Verifisering                                       | –                 | minutter    | n/a        |
| P6   | `VALIDATE` (jobb) + `SET NOT NULL` + indeks        | `V9`              | O(1) i Flyway | ja       |
| P7   | Oppfølging: spørringer, projeksjoner, payload-slanking | –              | –           | –          |

### P0 — Måling

Kjør mot **prod** (via cloud-sql-proxy eller `psql` fra en pod) før noe kode skrives. Tallene bestemmer batch-størrelse
og om vi trenger backfill-indeksen i P4.

```sql
SELECT
    (SELECT count(*) FROM event_log)                          AS log_rows,
    (SELECT count(*) FROM event_queue)                        AS queue_rows,
    pg_size_pretty(pg_total_relation_size('event_log'))       AS log_size,
    (SELECT max(id) FROM event_log)                           AS log_max_id;

-- fordeling per event-type, og hvor mange som IKKE er deriverbare
SELECT
    event_json ->> 'type' AS type,
    count(*)              AS rows,
    count(*) FILTER (WHERE coalesce(
        event_json -> 'soknad' ->> 'id',
        nullif(concat_ws(':',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'aar',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrSak',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrTilsagn'
        ), ''),
        event_json ->> 'tilsagnNummer'
    ) IS NULL)            AS uderiverbare
FROM event_log
GROUP BY 1
ORDER BY 2 DESC;
```

**Beslutningspunkt:** `uderiverbare` skal være 0 for alle typer. Er den ikke det, må mapping-tabellen utvides før
vi går videre — ikke kompensér i jobben.

Grove holdepunkter for batch-parametre: under ~100k rader er 1 000 rader per batch og 100 ms pause rikelig
(ferdig på under et minutt). Over ~1M rader: 500 rader per batch, 250 ms pause, og vurder backfill-indeksen i P4.

### P1 — Forutsetning: EventQueue-grensesnittet

**Denne fasen kommer først, før noe som helst rører databasen.** Den inneholder ingen skjemaendring, ingen
migrering og ingen ny kolonne — bare refaktoreringen fra [D6](#d6--to-eksplisitte-publiseringsmetoder-og-eventqueue-som-eneste-vei-inn-i-køen).

#### Hvorfor først

1. **Den er verdifull alene.** Atomisitets-problemet i de tre Arena-prosessorene finnes i dag, uavhengig av
   `aggregateRootId`. Å rette det bør ikke vente på en datamigrering, og bør ikke kunne bli avsporet av den.
2. **Den gjør resten av planen liten.** Etter P1 finnes det **ett** sted i kodebasen som skriver et event til køen.
   Modellendringen i [P3](#p3--modell-og-invariant-for-nye-rader) blir da én ny linje på ett innsettingssted, i
   stedet for seks. Uten P1 først må hver av de seks skriveveiene endres samtidig som kolonnen innføres — flere
   filer, flere sjanser for å glemme én, og en PR som blander refaktorering med migrering.
3. **Den er trygg å reviewe.** Rent Kotlin, ingen SQL, ingen prod-tilstand involvert. Diffen er mekanisk og
   oppførselen skal være uendret — det gjør review til en kontroll av at hvert kallsted havnet i riktig kategori,
   ikke en vurdering av migreringsrisiko.
4. **Den er trygg å rulle tilbake.** Deploy forrige image; ingen tilstand er endret noe sted.

#### Innhold

1. Splitt `EventQueue.publish` i `JdbcTransaction.publishInCurrentTransaction(ev)` og
   `EventQueue.publishInNewTransaction(ev)`, der sistnevnte delegerer til førstnevnte og har `require`-vakten.
   I denne fasen setter innsettingen fortsatt bare `event_json` — kolonnen finnes ikke ennå.
2. Flytt de tolv kallstedene til riktig variant (åtte i kallerens transaksjon, fire i egen), og fjern de fem direkte
   `QueuedEvents.insert { ... }`.
3. KDoc på `QueuedEvents`: skriv aldri til denne tabellen direkte.
4. Legg til den kildekode-skannende testen (håndhevingslag 2).
5. Legg til atomisitets-testene: `publishInCurrentTransaction` rulles tilbake med kallerens transaksjon,
   `publishInNewTransaction` gjør det ikke, `require`-en slår ut ved feil bruk, og rollback av
   `markerXSomBehandlet` ruller også tilbake publiseringen i Arena-prosessorene.
6. Oppdater `event/README.md` med det nye grensesnittet.

#### Akseptansekriterier

* Ingen `QueuedEvents.insert` / `.insertReturning` / `.upsert` i `src/main` utenfor `EventQueue.kt`.
* Alle eksisterende tester grønne uten funksjonelle endringer — oppførselen skal være identisk før og etter.
* De fire nye atomisitets-testene grønne.
* Deployet til dev og prod før P2 starter. P1 og P2 skal ikke ligge i samme PR.

### P2 — DDL (Flyway `V7`)

Kun katalog-endringer. `ADD COLUMN` av en nullbar kolonne **uten** `DEFAULT` er metadata-only i Postgres, altså O(1)
uavhengig av tabellstørrelse. Den tar en kortvarig `ACCESS EXCLUSIVE`-lås — derfor `lock_timeout`, så migreringen
feiler raskt og rent i stedet for å stå i lås-kø bak en langvarig transaksjon.

`backend/src/main/resources/db/migration/V7__aggregate_root_id.sql`:

```sql
SET lock_timeout = '3s';

ALTER TABLE event_queue ADD COLUMN IF NOT EXISTS aggregate_root_id TEXT NULL;
ALTER TABLE event_log   ADD COLUMN IF NOT EXISTS aggregate_root_id TEXT NULL;

-- Generisk state-tabell for restartbare backfill-jobber.
CREATE TABLE IF NOT EXISTS backfill_state (
    job_name     TEXT PRIMARY KEY,
    cursor_pos   BIGINT    NOT NULL DEFAULT 0,
    scanned      BIGINT    NOT NULL DEFAULT 0,
    updated      BIGINT    NOT NULL DEFAULT 0,
    skipped      BIGINT    NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Ingen indeks her — indeksbygging leser rader og hører derfor til P4/P6.

Feiler migreringen på `lock_timeout`: finn den blokkerende transaksjonen (`pg_stat_activity` /
`pg_locks`), og kjør deploy på nytt. Migreringen er idempotent (`IF NOT EXISTS`) og har ikke skrevet noe.

### P3 — Modell og invariant for nye rader

Kodeendringer:

1. `EventData` får abstrakt `val aggregateRootId: String`; alle 13 subklasser implementerer den med getter etter
   mapping-tabellen.
2. `DTO.Soknad.aggregateRootId` og `TilsagnData.aggregateRootId` som extension properties.
3. `QueuedEvents` og `EventLog` får `val aggregateRootId = text("aggregate_root_id").nullable()`.
4. `publishInCurrentTransaction` får **én ny linje** — `it[aggregateRootId] = ev.aggregateRootId`. Dette er hele
   skriveveis-endringen, fordi [P1](#p1--forutsetning-eventqueue-grensesnittet) allerede har gjort dette til det
   eneste innsettingsstedet.
5. `EventQueue.finalize` kopierer verdien til loggen, med derivering som fallback for rader som ble lagt i køen
   *før* denne deployen:

   ```kotlin
   it[EventLog.aggregateRootId] =
       event[QueuedEvents.aggregateRootId] ?: event[QueuedEvents.eventData].aggregateRootId
   ```

   Dette er viktigere enn det ser ut: `event_log.id` gjenbruker køens id, så en gammel kørad som finaliseres
   *etter* at backfill-markøren har passert den id-en, ville ellers dukke opp i loggen med `NULL` bak markøren.
   Fallbacken gjør at loggen aldri får nye `NULL`-rader etter denne deployen.
6. `QueuedEvent` og `LoggedEvent` får feltet `aggregateRootId: String?` lest fra kolonnen (ikke fra payload — for
   backfillede rader er kolonnen det vi faktisk har lagret).
7. `Event<T>` får `val aggregateRootId get() = data.aggregateRootId` som bekvemmelighet for handlers.
8. Tester som setter inn events direkte (`AppMetricsTest`) oppdateres; `foo`/`bar` får sin derivering.

`V8__aggregate_root_id_not_null_check.sql` — låser invarianten for nye logg-rader umiddelbart, uten å bry seg om
historiske rader:

```sql
SET lock_timeout = '3s';

ALTER TABLE event_log
    ADD CONSTRAINT event_log_aggregate_root_id_nn
    CHECK (aggregate_root_id IS NOT NULL) NOT VALID;
```

`NOT VALID` betyr «ikke sjekk eksisterende rader» — men constrainten **håndheves for alle nye og oppdaterte rader**
fra dette øyeblikket. Den er O(1) å legge til. Dermed kan vi ikke regressere: skulle noen glemme å sette kolonnen i en
ny skrivevei, feiler inserten i test og dev lenge før prod.

> **Viktig: `event_queue` får ikke denne constrainten nå.**
> En `NOT VALID`-check håndheves ved `UPDATE`, og `EventQueue.poll` oppdaterer nettopp `status`, `attempts` og
> `updated_at` på eksisterende kø-rader. Med constrainten på plass ville polling av en gammel `NULL`-rad feile og
> blokkere køen. `event_log` er insert-only og har ikke dette problemet. Kø-constrainten legges derfor i
> [P6](#p6--stramme-inn-til-not-null), etter at køen er backfillet.

### P4 — Backfill-jobb

En bakgrunnsjobb i backend, startet fra `Application.kt` etter samme mønster som `configureProjectionBuilders()`.
Den er **selv-avsluttende**: når begge tabellene er ferdige, markeres jobben `completed_at` og påfølgende oppstarter
gjør ett billig oppslag og avslutter. Den koster altså ingenting på senere deploys.

#### Egenskaper

| Egenskap            | Hvordan                                                                                  |
|---------------------|------------------------------------------------------------------------------------------|
| Restartbar          | Markør i `backfill_state.cursor_pos`, oppdatert per batch i samme transaksjon som UPDATE  |
| Én pod om gangen    | `SELECT ... FOR UPDATE SKIP LOCKED` på state-raden — samme mønster som projeksjonene      |
| Idempotent          | Setter kun rader der `aggregate_root_id IS NULL`; å kjøre den to ganger er et no-op       |
| Skånsom             | Batch på N rader, `delay` mellom batcher, `lock_timeout` + `statement_timeout` per batch  |
| Observerbar         | Logg per batch + gauge på gjenstående `NULL`-rader                                        |
| Avskrubar           | `enabled = false` i `AggregateRootIdBackfillConfig` (config i kode) + deploy               |
| Selv-avsluttende    | `completed_at` satt → jobben returnerer umiddelbart ved oppstart                          |

Rekkefølge: **`event_queue` først** (liten, og fjerner kilden til nye `NULL`-rader i loggen), deretter `event_log`.

#### Batch-spørringen

Én statement per batch. Den flytter markøren og gjør oppdateringen atomisk, og returnerer tellere:

```sql
WITH batch AS (
    SELECT id,
           coalesce(
               event_json -> 'soknad' ->> 'id',
               nullif(concat_ws(':',
                   event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'aar',
                   event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrSak',
                   event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrTilsagn'
               ), ''),
               event_json ->> 'tilsagnNummer'
           ) AS arid
    FROM event_log
    WHERE id > :cursor
    ORDER BY id
    LIMIT :batchSize
),
upd AS (
    UPDATE event_log e
       SET aggregate_root_id = b.arid
      FROM batch b
     WHERE e.id = b.id
       AND e.aggregate_root_id IS NULL
       AND b.arid IS NOT NULL
    RETURNING e.id
)
SELECT (SELECT max(id)   FROM batch) AS new_cursor,
       (SELECT count(*)  FROM batch) AS scanned,
       (SELECT count(*)  FROM upd)   AS updated;
```

Noter:

* Markøren går på **primærnøkkelen**, ikke på `aggregate_root_id IS NULL`. Det gir en monoton, indeksert scan og
  ingen risiko for evig loop på rader som ikke kan deriveres (det finnes ingen slike, men jobben skal ikke kunne
  henge om det viser seg feil).
* `new_cursor IS NULL` (tom batch) betyr at markøren har nådd enden → gå til sveip-fasen.
* `scanned - updated` teller rader som allerede hadde verdi *eller* ikke kunne deriveres. Disse skilles i
  sveip-fasen.

#### Sveip-fase

Når markøren er i enden kjøres én avsluttende kontroll:

```sql
SELECT count(*) FROM event_log WHERE aggregate_root_id IS NULL;
```

* `0` → sett `completed_at`, logg på `INFO`, ferdig.
* `> 0` → rader har dukket opp bak markøren (eller er uderiverbare). Nullstill `cursor_pos = 0` og kjør en runde til.
  Andre runde er billig fordi nesten alt allerede har verdi. Blir tallet ikke 0 etter to runder, logg på `ERROR`
  med de 20 første id-ene og la jobben stoppe — dette er en mapping-feil som skal fikses i kode, ikke maskeres.

Med `finalize`-fallbacken fra P3 på plass forventer vi at sveipet er 0 på første forsøk.

**Valgfritt for store tabeller (fra P0):** en midlertidig partiell indeks gjør sveipet O(antall NULL) i stedet for
en seq scan:

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS tmp_event_log_arid_null
    ON event_log (id) WHERE aggregate_root_id IS NULL;
-- droppes av jobben når completed_at settes
```

#### Skisse

```kotlin
class AggregateRootIdBackfill(
    private val database: Database,
    private val batchSize: Int = 1000,
    private val pause: Duration = 100.milliseconds,
) {
    private val log = logger()

    /** Kjører til begge tabellene er ferdige, deretter returnerer den. */
    suspend fun run() = withContext(Dispatchers.IO) {
        listOf(EVENT_QUEUE, EVENT_LOG).forEach { table ->
            val job = "aggregate_root_id:$table"
            if (isCompleted(job)) {
                log.info("Backfill $job allerede fullført, hopper over")
                return@forEach
            }
            while (isActiveAndNotTerminating) {
                // claimBatch: FOR UPDATE SKIP LOCKED på state-raden, kjør batch-spørringen,
                //             oppdater cursor_pos/scanned/updated i samme transaksjon.
                //             null = en annen pod holder låsen.
                val progress = claimAndRunBatch(job, table) ?: break
                if (progress.newCursor == null) {          // markør i enden
                    if (finishOrRewind(job, table)) break  // sveip 0 → completed_at satt
                } else {
                    log.info("Backfill $job: markør ${progress.newCursor}, oppdaterte ${progress.updated}")
                }
                delay(pause)
            }
        }
    }
}
```

#### Konfigurasjon

Konfigurasjon settes i kode, etter mønsteret fra `EventManagerConfig` — ingen env-vars, ingen `env:`-blokk i
nais-manifestet:

```kotlin
data class AggregateRootIdBackfillConfig(
    val enabled: Boolean = true,
    val batchSize: Int = basedOnEnv(other = 1000, prod = 1000, dev = 200),
    val pause: Duration = 100.milliseconds,
    val lockTimeout: Duration = 2.seconds,
    val statementTimeout: Duration = 30.seconds,
)
```

Registrering i `Application.kt`, ved siden av de øvrige bakgrunnsløkkene:

```kotlin
launch { dependencies.create(AggregateRootIdBackfill::class).run() }
```

Jobben avslutter selv når `completed_at` er satt, og `enabled = false` gjør `run()` til et no-op. Å skru den av
krever altså en kodeendring og en deploy — som i praksis er samme operasjon som å endre en env-var i nais-manifestet,
og med den fordelen at avgjørelsen ligger i git-historikken.

Hver batch settes med `SET LOCAL lock_timeout = '2s'; SET LOCAL statement_timeout = '30s';`. Timeout på en batch er
ikke en feil — logg på `WARN`, `delay`, prøv igjen. Markøren står stille, så ingenting går tapt.

#### Metrikk og alarm

Ny gauge i `AppMetrics`, i tråd med de eksisterende `MultiGauge`-ene:

```kotlin
val aggregateRootIdMissingGauge: MultiGauge = MultiGauge.builder("event.aggregaterootid.missing")
    .description("Antall rader uten aggregate_root_id, per tabell")
    .register(meterRegistry)
```

Tagget på `table` (`event_queue` / `event_log`). Denne er nyttig **etter** migreringen også: den skal ligge flatt på 0,
og et hopp over 0 betyr at noen har innført en skrivevei som omgår `EventQueue`. Legg en Grafana-alarm på
`> 0 i 15 min` — men først etter at P6 er landet, ellers fyrer den under selve backfillen.

### P5 — Verifisering

Kjøres i dev først, deretter prod. Ingen kodeendring.

```sql
-- 1. Ingen gjenstående NULL
SELECT count(*) FROM event_queue WHERE aggregate_root_id IS NULL;   -- forventet 0
SELECT count(*) FROM event_log   WHERE aggregate_root_id IS NULL;   -- forventet 0

-- 2. Kolonnen stemmer med payload for hele tabellen (ikke bare stikkprøve)
SELECT count(*) AS avvik
FROM event_log
WHERE aggregate_root_id IS DISTINCT FROM coalesce(
        event_json -> 'soknad' ->> 'id',
        nullif(concat_ws(':',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'aar',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrSak',
            event_json -> 'tilsagnData' -> 'tilsagnNummer' ->> 'loepenrTilsagn'
        ), ''),
        event_json ->> 'tilsagnNummer');                             -- forventet 0

-- 3. Alle søknadsrøtter peker på en søknad som finnes
SELECT count(*) FROM event_log e
WHERE e.aggregate_root_id ~ '^[0-9a-f-]{36}$'
  AND NOT EXISTS (SELECT 1 FROM soknad s WHERE s.id::text = e.aggregate_root_id);
-- > 0 er ikke nødvendigvis feil: slettede søknader (slette_tidspunkt) er forventet. Kontrollér mot
--   antall slettede før konklusjon.

-- 4. Stikkprøve: tidslinje for én søknad skal se komplett ut
SELECT id, event_json ->> 'type' AS type, created_at
FROM event_log WHERE aggregate_root_id = '<soknad-uuid>' ORDER BY id;
```

**Beslutningspunkt:** kontroll 1 og 2 må være 0 i prod før P6 deployes.

### P6 — Stramme inn til `NOT NULL`

Todelt: jobben tar det som skanner rader, Flyway tar katalog-endringene.

**Steg 1 — i backfill-jobben** (kjøres når `completed_at` settes, eller som eget kall):

```sql
-- Full seq scan, men kun SHARE UPDATE EXCLUSIVE: blokkerer ikke lesing eller skriving.
ALTER TABLE event_log VALIDATE CONSTRAINT event_log_aggregate_root_id_nn;

-- Indeks for oppslag «alle events for ett aggregat, i rekkefølge».
CREATE INDEX CONCURRENTLY IF NOT EXISTS event_log_aggregate_root_id_idx
    ON event_log (aggregate_root_id, id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS event_queue_aggregate_root_id_idx
    ON event_queue (aggregate_root_id, id);
```

`CREATE INDEX CONCURRENTLY` kan ikke kjøre inne i en transaksjon, og må derfor kjøres på en connection med
autoCommit — enda en grunn til at dette hører i jobben og ikke i Flyway. Feiler den, etterlater den en `indisvalid = false`
indeks; jobben skal derfor sjekke `pg_index.indisvalid` for indeksnavnet, droppe en ugyldig indeks og prøve på nytt.

**Steg 2 — Flyway `V9`**, etter at steg 1 er bekreftet ferdig:

```sql
SET lock_timeout = '3s';

-- Postgres 12+ bruker den validerte CHECK-constrainten og hopper over tabell-scanen: O(1).
ALTER TABLE event_log ALTER COLUMN aggregate_root_id SET NOT NULL;
ALTER TABLE event_log DROP CONSTRAINT event_log_aggregate_root_id_nn;

-- Køen er nå tom for NULL-rader, og alle nye rader settes av EventQueue.
ALTER TABLE event_queue ALTER COLUMN aggregate_root_id SET NOT NULL;
```

Merk at `event_queue` går rett på `SET NOT NULL` uten CHECK-omveien: tabellen er liten og drenert (rader slettes ved
`finalize`), så scanen er trivielt kort. Er køen uventet stor på migreringstidspunktet, bruk samme
`CHECK NOT VALID` → `VALIDATE` → `SET NOT NULL`-sekvens som for loggen.

Til slutt: oppdater Exposed-definisjonene fra `.nullable()` til ikke-nullbar, og `QueuedEvent`/`LoggedEvent` fra
`String?` til `String`.

### P7 — Oppfølging

Egne leveranser, ikke del av denne:

1. **Spørrings-API:** `fun hentEventerForAggregat(aggregateRootId: String): List<LoggedEvent>` i event-modulen, og
   en tidslinje-visning på saksbehandler-API-et. Dette er den egentlige gevinsten.
2. **Projeksjoner:** `SoknadBehandletForsinkelseProjection` og `TilskuddsbrevVistProjection` kan bruke
   `aggregateRootId` i stedet for å plukke søknadsid ut av payload per type.
3. **Slankere payload:** vurder å fjerne innbakt `DTO.Soknad` fra events og heller slå opp søknaden via
   `aggregateRootId` i handlerne. Reduserer duplisering og gjør loggen mindre, men er en semantisk endring av
   event-loggen (loggen mister øyeblikksbildet av søknaden slik den var) — krever egen vurdering.
4. **`aggregate_type` og/eller prefiksede id-er:** når aggregat nummer to kommer (se [D2](#d2--kolonnetype-text-ikke-uuid)).
5. **Full innkapsling av `QueuedEvents`:** fil-privat tabell-objekt og lese-API på `EventQueue`, slik at det er
   fysisk umulig å skrive til køen utenom de to publiseringsmetodene — se
   [D6](#d6--to-eksplisitte-publiseringsmetoder-og-eventqueue-som-eneste-vei-inn-i-køen) for kostnadsbildet.
6. **Flere base-felt:** `correlationId` / `causationId` for sporing gjennom kjeder av events, og `schemaVersion`.
   Disse *må* ligge i payload (de kan ikke deriveres), så de krever en reell payload-migrering — bevisst holdt utenfor
   denne runden.

## Verifisert lokalt

SQL-uttrykkene og låse-strategien er kjørt mot Postgres før denne planen ble skrevet, med syntetiske payloads for
alle event-typene i mapping-tabellen.

**Deriveringen** ga korrekt verdi for alle typer, inkludert de tre fallgruvene: `tilskuddsbrevVist` med
`"soknad": null` faller korrekt gjennom til `tilsagnNummer`, `tilskuddsbrevMottatt` med både `soknad` og
`tilsagnData` velger søknaden, og `foo` gir `NULL` som forventet.

**`NOT VALID`-constraint på `event_queue` ble bekreftet å være farlig** — dette er ikke teori:

```
INSERT INTO event_queue (aggregate_root_id) VALUES (NULL);
ALTER TABLE event_queue ADD CONSTRAINT q_nn CHECK (aggregate_root_id IS NOT NULL) NOT VALID;
UPDATE event_queue SET status = 1, attempts = attempts + 1 WHERE id = 1;
ERROR:  new row for relation "event_queue" violates check constraint "q_nn"
```

Det vil si: hadde vi lagt kø-constrainten i `V8`, ville `EventQueue.poll` feilet på hver gjenstående legacy-rad og
køen stått. Derfor ligger den i `V9`.

**`CHECK` + `VALIDATE` → `SET NOT NULL` på 3 mill. rader** (lokal disk, PG 16):

| Statement                              | Tid      | Lås                       |
|----------------------------------------|----------|---------------------------|
| `ADD COLUMN ... TEXT NULL`             | 3 ms     | ACCESS EXCLUSIVE, kortvarig |
| `ADD CONSTRAINT ... CHECK ... NOT VALID` | 0,6 ms | ACCESS EXCLUSIVE, kortvarig |
| `VALIDATE CONSTRAINT`                  | 98 ms    | **SHARE UPDATE EXCLUSIVE** — blokkerer ikke |
| `SET NOT NULL` (etter VALIDATE)        | 0,4 ms   | ACCESS EXCLUSIVE, kortvarig |
| `SET NOT NULL` (uten VALIDATE, referanse) | 103 ms | ACCESS EXCLUSIVE **hele scanen** |

Nyansen dette avdekker: selve scanen er *rask* — det er ikke skannetiden som er faren. Faren er at
`ACCESS EXCLUSIVE` holdes gjennom scanen, og at låsen står i kø bak en langvarig transaksjon mens den selv blokkerer
alt bak seg. `CHECK`/`VALIDATE`-omveien flytter scanen til en lås som ikke blokkerer, og reduserer den blokkerende
låsen til under et millisekund. Det er derfor omveien er verdt de to ekstra migreringene, selv om tallene ser små ut.

Tilsvarende for `UPDATE`-backfillen: den er ikke farlig fordi den er treg, men fordi en enkelt stor `UPDATE` holder
radlåser og oppblåser tabellen (dead tuples) i én transaksjon. Batching løser begge.

## Testplan

Sortert på fase, slik at hver PR har sin egen liste.

| Fase | Nivå        | Test                                                                                                   |
|------|-------------|--------------------------------------------------------------------------------------------------------|
| P1   | Statisk     | Kildekode-skann: `QueuedEvents.insert` / `.insertReturning` / `.upsert` finnes ikke i `src/main` utenfor `EventQueue.kt` |
| P1   | Integrasjon | `publishInCurrentTransaction` rulles tilbake med kallerens transaksjon — publiser i en `transaction` som rulles tilbake, verifiser at raden ikke finnes |
| P1   | Integrasjon | `publishInNewTransaction` overlever kallerens rollback — dvs. den *er* en egen transaksjon               |
| P1   | Integrasjon | `publishInNewTransaction` kalt inne i en transaksjon feiler på `require` med forklarende melding         |
| P1   | Integrasjon | Arena-prosessorene: rollback av `markerXSomBehandlet` ruller også tilbake publiseringen (atomisiteten de hviler på) |
| P2   | Migrering   | `V7` kjører rent på tom base og på base med eksisterende rader; kolonnen er nullbar                     |
| P3   | Enhet       | For hver `EventData`-subklasse: derivering gir forventet verdi, og aldri blank                          |
| P3   | Enhet       | Refleksjonstest over `EventData::class.sealedSubclasses`: ingen subklasse mangler override eller returnerer blank |
| P3   | Integrasjon | Publisering setter `aggregate_root_id`; `finalize` kopierer den til `event_log`                          |
| P3   | Integrasjon | `finalize` av en kørad med `NULL` (simulert legacy) deriverer verdien i stedet for å skrive `NULL`       |
| P3   | Migrering   | Bypass-forsøk: insert i `event_log` uten `aggregate_root_id` feiler på `V8`-constrainten                 |
| P4   | Integrasjon | **SQL-mot-Kotlin-konsistens** — se under                                                                |
| P4   | Integrasjon | Backfill-jobben: kjør på seedet tabell, verifiser at alle rader får riktig verdi og at `completed_at` settes |
| P4   | Integrasjon | Backfill-jobben er idempotent: to kjøringer gir samme resultat, andre kjøring oppdaterer 0 rader         |
| P4   | Integrasjon | Backfill-jobben er restartbar: avbryt midt i, start på nytt, verifiser at den fullfører fra markøren     |
| P4   | Integrasjon | To samtidige jobb-instanser: den andre får ikke låsen og gjør ikke dobbeltarbeid                         |
| P6   | Migrering   | `V9` kjører rent; insert uten `aggregate_root_id` feiler nå også mot `event_queue`                       |

### SQL-mot-Kotlin-konsistens

Den viktigste testen, fordi den er det eneste som fanger drift mellom backfill-SQL-en og Kotlin-deriveringen:

1. For hver `EventData`-subklasse: konstruér et realistisk eksemplar og sett det inn med `aggregate_root_id = NULL`.
2. Kjør backfill-jobben.
3. Assert at kolonneverdien er lik `eventData.aggregateRootId` for hver rad.

Dette validerer SQL-uttrykket mot **faktisk serialisert payload**, ikke mot en antagelse om feltnavn — inkludert
fallgruvene i mapping-tabellen (`TilsagnsdataLagret` med stor T, `tilskuddsbrevMottatt` som har både `soknad` og
`tilsagnData`, `tilskuddsbrevVist` uten søknad).

Testen bør feile hvis en ny subklasse legges til uten at eksempel-listen utvides — bruk `sealedSubclasses` og
assert at listen dekker alle.

## Rollback

| Fase | Rollback                                                                                                          |
|------|-------------------------------------------------------------------------------------------------------------------|
| P1   | Ren refaktorering — deploy forrige image. Ingen tilstand er endret.                                                |
| P2   | Kolonnen er nullbar og additiv — ingenting å rulle tilbake. `DROP COLUMN` om ønskelig, men unødvendig.             |
| P3   | Deploy forrige image. Nye rader slutter å få verdi; gamle beholder sin. `V8`-constrainten må da droppes manuelt.    |
| P4   | Sett `enabled = false` i configen og deploy. Markøren står; jobben fortsetter der den var når den skrus på.        |
| P6   | `ALTER TABLE ... ALTER COLUMN aggregate_root_id DROP NOT NULL` — O(1). Indeksene kan droppes `CONCURRENTLY`.        |

Ingen fase gjør destruktive endringer på `event_json`, så event-loggen er aldri i fare.

## Sjekkliste

- [ ] P0: kjørt måle-spørringene i prod, `uderiverbare = 0` for alle typer, batch-parametre valgt
- [ ] P1: de to publiseringsmetodene, tolv kallsteder flyttet, kildekode-skann + atomisitets-tester grønne, deployet dev → prod
- [ ] P2: `V7` deployet til dev, så prod
- [ ] P3: modell + `finalize`-fallback + `V8`, alle tester grønne, deployet dev → prod
- [ ] P4: backfill kjørt ferdig i dev, verifisert, deretter prod; logg viser `completed_at`
- [ ] P5: verifiseringsspørring 1 og 2 gir 0 i prod
- [ ] P6: `VALIDATE` + indekser ferdig, `V9` deployet, Exposed-definisjoner strammet til
- [ ] Grafana-alarm på `event.aggregaterootid.missing > 0` aktivert
- [ ] `event/README.md` oppdatert — nytt publiserings-grensesnitt (P1) og aggregateRootId-konvensjonen med mapping-tabellen (P3)

## Åpne spørsmål

1. **Skal `V7`/`V8`/`V9` genereres med `CreateMigration.kt`, eller skrives for hånd?** Exposed-generatoren produserer
   ikke `lock_timeout`, `NOT VALID`-constraints eller `CONCURRENTLY`, så disse tre bør skrives for hånd. Verdt å
   notere i `CreateMigration.kt` at generatoren er for enkle skjema-endringer.
2. **Trenger vi `aggregateRootId` på `event_handler_states` og `idempotency_guard_records`?** Antatt nei — de er
   nøklet på `event_id` og har alltid en rad i køen/loggen å slå opp via. Bekreft.
3. **Hvor lenge skal backfill-jobben ligge i kodebasen etter at den er ferdig?** Forslag: fjern den i egen PR når
   P6 er landet i prod og `completed_at` er satt i begge miljøer — men behold `backfill_state`-tabellen, den er
   generisk og neste backfill kommer.
