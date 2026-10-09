# Tildel meg sak

Trello: https://trello.com/c/wu5GnoXw/623-tildel-meg-sak
Kort-ID: `6ac39ed37f2924d04ff865a8`

## Mål

En saksbehandler skal kunne tildele seg en sak, overta en sak fra en kollega og
frigjøre en sak hen har selv. Tildelingen lagres i `sak.saksbehandler_ident`
og `sak.saksbehandler_navn`. Listen og saksdetaljen viser navnet.
Knappene «Tildel meg» i saksoversikten og menyen på saksdetaljen er i dag bare
statisk UI.

## Bakgrunn

`sak.saksbehandler_ident` finnes fra V12, og både listen og detaljoppslaget
returnerer `saksbehandlerIdent`. Ingenting skriver til feltet. `sak` har ingen
kolonne for navnet.

Frontend har allerede:

- knappen «Tildel meg» i `OversiktPage` for saker uten saksbehandler, uten
  `onClick`
- fanen «Mine saker», som filtrerer på `saksbehandlerIdent === innloggetAnsatt.id`
- ingen visning av tildeling på `SakPage`

`sak-datamodell.md` sier at gjeldende saksbehandler ligger på
`sak.saksbehandler_ident`, at omtildeling logges i `sakslogg`, og at alle
saksbehandlerhandlinger skal logges der. Den beskriver også en habilitetssjekk
der beslutter aldri kan ha vært saksbehandler på saken, og CHECK-en
`chk_saksbehandler_ulik_beslutter` på `sak`. Det endrer vi i dette kortet:
begrensningen gjelder vedtaket, ikke saken (se *Beslutninger*).

Saksloggen er bygd (`specifications/sakslogg.md`, PR #170):

- `EventData.SakOppdatert` med `sakId`, `soknadId`, `utfortAvRolle`,
  `utfortAvIdent`, `notat` og `tidspunkt`
- handleren `SkrivSakslogg`, som skriver eventen til `sakslogg`
- `SaksloggTable` og `AktorRolle` i `saksbehandling/Db.kt`
- «Logg»-dropdownen (`components/Sakslogg.tsx`) til høyre i linjen med
  tilbakelenken på `SakPage`

Ingen publiserer `SakOppdatert` ennå. Tildeling blir første bruker.

Vilkårsvurderingen er bygd (PR #175 og #176). Den gir dette kortet:

- `saksbehandling/Tilgangsjekker.kt` med felles tilgangssjekker:
  `principalMedRolle`, `hentEnheterForPrincipal`, `SakTilgangsgrunnlag` og
  `hentSakTilgangsgrunnlag`, `sjekkTilgangTilEnhet`, `sjekkTilgangsmaskin` og
  `sjekkErSaksbehandlerPåSak` (403 `IKKE_TILDELT_SAK`). Detaljoppslaget bruker
  dem allerede. Tildelingen bruker de samme.
- `sakIdParameter()` i `saksbehandling/Parametre.kt`.
- `AzureAdPrincipal.harRolle` og `AzureAdPrincipal.harTilgangTilEnhet`.
- `PATCH …/vilkarsvurdering` krever at innlogget er `saksbehandler_ident` på
  saken og at saken er `UNDER_BEHANDLING`. Tildelingen er altså det som gjør
  at en saksbehandler kan vurdere vilkår.
- Migrering `V19__saksvilkar_rad_per_vilkar.sql`. Vår migrering blir V20.
- `SakProjection` heter nå `Sak-v3`.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-07.

| Node | Lenke | Viser |
|------|-------|-------|
| `695:23735` | [tildel i listen](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=695-23735) | Saksoversikten. Kolonnen «Saksbehandler» viser en liten sekundærknapp «Tildel meg» på saker uten saksbehandler, og **navnet** på saksbehandleren på tildelte saker. |
| `660:22383` | [tildelt meg](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=660-22383) | Saksdetaljen. Til høyre i linjen med «Tilbake til liste av saker»: teksten «Saken er tildelt meg», så «Meny» med chevron, så «Logg». «Meny» er tegnet med samme header-mønster som «Logg» (`LoggHeader`). |
| `695:23477` | [meny åpen](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=695-23477) | «Meny» åpen, med to grupper. «Legg behandlingen tilbake» har ett valg: «Frigjør oppgave». «Avslutt behandling» har «Avvis søknad» og «Trekk søknad». Gruppene har chevron og ser sammenleggbare ut. |
| `691:22948` | [tildelt en annen](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=691-22948) | Saksdetaljen: «Saken er tildelt <navn>» og en liten sekundærknapp «Tildel meg», så «Logg». Ingen «Meny». |
| `694:23201` | [ikke tildelt](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=694-23201) | Saksdetaljen: bare «Tildel meg», så «Logg». Ingen tekst og ingen «Meny». |

Alle detaljskissene viser en sak med vedtak «Innvilget». Det stemmer med
beslutningen om at `INNVILGET`-saker kan tildeles og overtas.

Avvik mellom kort og skisse:

- **Menyvalgene.** Kortet nevner «legge tilbake saken i sin egen liste» og
  «frigjøre saken» som to valg. Skissen har ett valg, «Frigjør oppgave», under
  gruppen «Legg behandlingen tilbake». Det andre valget ble fjernet etter
  designreview. Vi følger skissen.
- **Teksten.** Kortet sier «Tildelt meg». Skissen sier «Saken er tildelt meg» og
  «Saken er tildelt <navn>». Vi bruker skissens tekst.
- **Navn i listen.** Skissen viser navn i kolonnen «Saksbehandler». Kortet sier
  ikke noe om det. Vi viser navnet, lagret ved tildeling.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Sak uten saksbehandler viser «Tildel meg». Sak tildelt en annen viser navnet på saksbehandleren og «Tildel meg». Sak tildelt meg viser en tekst og en `ActionMenu` «Meny». Tekst og meny følger skissen, se under. |
| Figma `660:22383`, `691:22948`; Ken, 2026-10-07 | Teksten er «Saken er tildelt meg» og «Saken er tildelt <navn>». Dette og «Frigjør oppgave» er bare UI-tekst. Domenet sier fortsatt «sak»: `SakFrigjort`, `frigjoer()`, notatet «Sak frigjort». |
| Figma `695:23477`; Ken, 2026-10-07 | «Meny» har bare «Frigjør oppgave». Valget «legge tilbake saken i sin egen liste» fra kortet ble fjernet etter designreview og skal ikke bygges. |
| Kortbeskrivelsen; Figma `695:23477`; Ken, 2026-10-07 | «Trekk søknad» og «Avvis søknad» (gruppen «Avslutt behandling» i menyen) kommer i et senere kort. |
| Ken, 2026-10-06 | «Tildel meg» vises bare når innlogget faktisk kan tildele seg saken. Listen viser den ikke når saken er tildelt en annen. Saksdetaljen gjør det, slik at en kollega kan ta over saken (bus factor). «Meny» vises bare på saksdetaljen når saken er tildelt innlogget. |
| Ken, 2026-10-06 | Listen kaller ikke Tilgangsmaskinen nå. Kommer det senere, blir det en vask av hele listen for visning, ikke en sjekk bare for tildeling. |
| Ken, 2026-10-06 og 2026-10-07 | API-kallet skriver ikke til databasen, men publiserer en event på `EventQueue`. En event-handler gjør endringen live. `SakProjection` håndterer de samme eventene, slik at tildelingene kommer med når `sak` bygges fra loggen. |
| Ken, 2026-10-06 | `sakslogg` finnes på main. Vi publiserer `SakOppdatert` og lar `SkrivSakslogg` skrive loggen. |
| Ken, 2026-10-06 | Tildeler to seg samme sak samtidig, vinner den siste (last writer wins). Ingen 409 for samtidighet. |
| Ken, 2026-10-07 | Ugyldig status gir `409 kode = SAK_UGYLDIG_STATUS`. |
| Ken, 2026-10-07 | `INNVILGET`-saker kan tildeles og overtas. En innvilget sak blir stående hos saksbehandleren mens hen venter på refusjonskravet, og arbeidsgiveren kan avbryte den når som helst. Sammen med bus factor betyr det at en kollega må kunne ta over. |
| Ken, 2026-10-07 | Tildeling setter status til `UNDER_BEHANDLING`. Frigjøring endrer ikke status, og koden som frigjør har en kommentar om hvorfor. |
| Ken, 2026-10-07 | Navnet til saksbehandleren følger med på `SakTildeltSaksbehandler` og vises i listen og på saksdetaljen. Det er ikke nullable og kommer alltid fra entra-proxy, aldri fra `name`-claimet. `AZURE_AD_PROVIDER` henter navn og enheter og legger dem på `AzureAdPrincipal`. Detaljoppslaget slår ikke opp navnet. |
| Ken, 2026-10-08 | `AzureAdPrincipal` får også `epost` og `gjeldendeEnhet`, slik at `/meg` ikke gjør egne oppslag mot entra-proxy. |
| Ken, 2026-10-07 | Feiler entra-proxy, svarer backend 503, ikke 401. En feil hos en avhengighet er ikke en klientfeil, og 401 sender brukeren inn i en innloggingsløkke. Gjelder også gruppene, som i dag gir 401. |
| Ken, 2026-10-08 | Data fra entra-proxy (grupper, navn, e-post, gjeldende enhet, enheter) caches per token i `ENTRA_PROXY_CACHE_TTL` = 5 minutter, slik at vi ikke belaster entra-proxy for hver request, og slik at kortvarige feil der ikke når klientene. Berikelsen har `updatedAt` for når den ble hentet, til feilsøking, og `/meg` returnerer den. |
| Ken, 2026-10-08 | Frigjøring av en sak innlogget ikke har, bruker `sjekkErSaksbehandlerPåSak` og svarer 403 `IKKE_TILDELT_SAK`, som vilkårsvurderingen. |
| Ken, 2026-10-08 | Feilen i `tilTilgangsgrunnlag()` (`beslutterIdent = saksbehandlerIdent`) rettes i dette kortet. |
| Ken, 2026-10-08 | Tildeling har ingen beslutter-sjekk. Topartskontrollen gjelder vedtaket: beslutter kan ikke være den som sendte saken til `TIL_BESLUTNING`. Hvem som har vært tildelt saken spiller ingen rolle. Ellers kan en sak bli stående fordi eneste beslutter på jobb har vært innom den, og noen kan omgå kontrollen ved å tildele saken frem og tilbake. Regelen bygges sammen med vedtak, ikke i dette kortet. |

Kortet har ingen kommentarer.

## Tilnærming

```
API ── publishEventQueue(SakTildeltSaksbehandler) ──> event_queue
                                                          │
TildelSaksbehandler ── tildelSak(...) + publishEventQueue(SakOppdatert)
                                                          │
SkrivSakslogg ── INSERT sakslogg

event_log ── SakProjection ── tildelSak(...)   (treffer ikke live, brukes ved replay)
```

### 0. Migrering

`V20__sak_tildeling.sql`:

```sql
ALTER TABLE sak ADD COLUMN IF NOT EXISTS saksbehandler_navn TEXT NULL;
ALTER TABLE sak ADD COLUMN IF NOT EXISTS tildeling_event_id BIGINT NULL;
ALTER TABLE sak ADD CONSTRAINT chk_saksbehandler_navn_med_ident
    CHECK ((saksbehandler_ident IS NULL) = (saksbehandler_navn IS NULL));
ALTER TABLE sak DROP CONSTRAINT IF EXISTS chk_saksbehandler_ulik_beslutter;
```

`chk_saksbehandler_ulik_beslutter` fjernes. Den hindrer at gjeldende
saksbehandler og beslutter er samme person, og er en begrensning på saken. Etter
beslutningen 2026-10-08 skal begrensningen ligge på vedtaket. Med CHECK-en på
plass ville en tildeling til den som står som beslutter feile i handleren og
stoppe `SakProjection` ved replay. Ingenting skriver `beslutter_ident` i dag, så
ingen rader påvirkes.

`saksbehandler_navn` er `NULL` bare når saken ikke er tildelt. CHECK-en sikrer
at en tildelt sak alltid har navn. `SakTable` får
`saksbehandlerNavn = text("saksbehandler_navn").nullable()` og
`tildelingEventId = long("tildeling_event_id").nullable()`.
Ingen backfill: ingenting skriver `saksbehandler_ident` i dag, så CHECK-en
holder for alle eksisterende rader. Finnes det likevel en rad med ident,
feiler migreringen, og vi ser det før deploy går gjennom.

`tildeling_event_id` er id-en til siste tildelings- eller frigjøringsevent som
er brukt på saken. Den gjør at handleren og `SakProjection` kan bruke samme
event uten å skrive over en nyere tildeling (se steg 3 og 3b).

### 1. Nye events

To nye `EventData` i `Events.kt`, navngitt etter reglene i `event/README.md`:

```kotlin
@Serializable
@SerialName("sakTildeltSaksbehandler")
data class SakTildeltSaksbehandler(
    val sakId: String,
    val soknadId: String,
    val saksbehandlerIdent: String,
    val saksbehandlerNavn: String,
    val tidspunkt: Instant,
) : EventData {
    init {
        require(saksbehandlerNavn.isNotBlank()) { "saksbehandlerNavn kan ikke være blank" }
    }

    override val aggregateRootId: String get() = soknadId
}

@Serializable
@SerialName("sakFrigjort")
data class SakFrigjort(
    val sakId: String,
    val soknadId: String,
    val saksbehandlerIdent: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

- `aggregateRootId` er `soknadId`. Sak og søknad er 1:1, og README sier at
  aggregatroten i praksis er søknaden. Da ligger alle hendelser for en sak i samme
  strøm som søknadens.
- Payload har bare id-er, NAV-identen og navnet til saksbehandleren. Ikke hele
  `DTO.Soknad`, som inneholder fnr.
- `saksbehandlerIdent` og `saksbehandlerNavn` gjelder en Nav-ansatt og logges
  ikke utenfor teamLog, som for `SaksbehandlingStartetIArena`.
- `saksbehandlerNavn` kommer fra `AzureAdPrincipal.navn`, som er hentet fra
  entra-proxy (se steg 1b). Vi bruker ikke `name`-claimet i tokenet.
- Overtakelse fra en kollega er samme event som tildeling av en ledig sak.
- `tidspunkt` settes av API-et da saksbehandleren klikket, og følger med til
  `SakOppdatert`. Saksloggen viser da klikket, ikke når handleren kjørte.

### 1b. Backend: `AzureAdPrincipal` med ansattdata og enheter fra entra-proxy

I dag henter `AZURE_AD_PROVIDER` grupper fra entra-proxy, tar navnet fra
`name`-claimet og henter enheter først når en rute kaller
`principal.enheter()`. Det endres til:

```kotlin
data class AzureAdPrincipal(
    val navIdent: String,
    val navn: String,
    val epost: String?,
    val gjeldendeEnhet: Enhet,
    val groups: List<String>,
    val enheter: List<Enhet>,
    val berikelseUpdatedAt: Instant,
    val subjectToken: String,
)
```

- `AZURE_AD_PROVIDER` henter grupper (`hentGrupper`), ansatt (`hentAnsatt`) og
  enheter (`hentEnheter`) parallelt med `coroutineScope { async { … } }`.
  Resultatet caches per token, se *Cache per token* under. Kallene gjøres
  derfor én gang per token og 5 minutter, ikke per request.
- `navn` er `visningNavn`, ellers fornavn og etternavn. Regelen samles i
  `UtvidetAnsatt.visningsnavn(): String?` i `EntraProxyClient.kt`, og brukes
  også av `/meg` i `SaksbehandlerApi.kt` og `slaaOppNavn` i `Sakslogg.kt`, som
  i dag har hver sin kopi.
- `epost` og `gjeldendeEnhet` kommer fra samme `hentAnsatt`-svar (`epost` og
  `enhet`). `epost` er nullable fordi den er det i `UtvidetAnsatt`.
- `berikelseUpdatedAt` er `EntraBerikelse.updatedAt`: når dataene ble hentet
  fra entra-proxy, ikke når requesten kom. Den viser hvor gamle gruppene og
  enhetene er, for eksempel når en saksbehandler melder at ny tilgang ikke
  har slått inn. Den logges ikke for hver request, men `/meg` returnerer den
  som `updatedAt`.
- Feiler et av kallene, kaster `authenticate` en ny
  `EntraProxyUtilgjengeligException`. `StatusPages` i `Application.kt` gjør den
  om til 503 med `{"message": "tilgangskontroll er ikke tilgjengelig"}`, samme
  svar som enhetene gir i dag. Requesten slipper ikke gjennom (fail-closed),
  men brukeren får ikke 401 og sendes ikke til innlogging.
- Dette endrer gruppene: i dag returnerer `authenticate` `null` når
  `hentGrupper` feiler, og brukeren får 401. Det blir 503.
- Gir entra-proxy en ansatt uten navn, kaster `authenticate`
  `IllegalStateException`, og `StatusPages` svarer 500. Det er en feil i data
  fra Entra, ikke noe brukeren kan rette ved å logge inn på nytt eller prøve
  igjen.
- 401 brukes bare når tokenet er ugyldig eller mangler `NAVident`, som i dag.
- `name`-claimet leses ikke lenger.
- Logging: som i dag, `authLog` uten ident og `authTeamLog` med ident.

**Cache per token.** Grupper, ansattdata og enheter fra entra-proxy caches i
backend, med tokenet som nøkkel. Frontend sender flere kall parallelt når en
side lastes, og hvert av dem ville ellers gitt tre kall mot entra-proxy. Med
cachen treffer en kortvarig feil hos entra-proxy bare den første requesten med
et nytt token, ikke alle.

```kotlin
data class EntraBerikelse(
    val groups: List<String>,
    val navn: String,
    val epost: String?,
    val gjeldendeEnhet: Enhet,
    val enheter: List<Enhet>,
    val updatedAt: Instant,
)

val ENTRA_PROXY_CACHE_TTL: Duration = 5.minutes

class EntraBerikelseCache(
    entraProxyClient: EntraProxyClient,
    ttl: Duration = ENTRA_PROXY_CACHE_TTL,
    ticker: Ticker = Ticker.systemTicker(),
    clock: Clock = Clock.System,
) {
    suspend fun hent(token: String, navIdent: String): EntraBerikelse
}
```

- Ny klasse i `entraproxy/EntraBerikelseCache.kt`, levert med
  `dependencies { provide(EntraBerikelseCache::class) }` i `Application.kt` og
  `LocalApplication.kt`. Tester kan dermed levere sin egen.
- Bygget på Caffeine (`com.github.ben-manes.caffeine:caffeine`, ny avhengighet
  i `pom.xml`) som `AsyncCache`. Samtidige requester med samme token deler ett
  oppslag i stedet for å gjøre tre kall hver. Et oppslag som feiler, fjernes fra
  cachen av Caffeine, så neste request prøver på nytt. Feil caches aldri.
- Nøkkelen er SHA-256 av tokenet, ikke tokenet selv. Cachen holder da ikke på
  tokenet lenger enn requesten.
- Hver rad lever i `ENTRA_PROXY_CACHE_TTL` (5 minutter) etter at den ble
  hentet, med `expireAfterWrite`. Konstanten ligger i `EntraBerikelseCache.kt`
  og er standardverdi for `ttl`, så tester og `LocalApplication.kt` kan sette
  en annen. `exp` i tokenet leses ikke. Et token som har utløpt, stoppes av
  introspeksjonen før cachen brukes, og et nytt token får en ny nøkkel.
- `updatedAt` settes fra `clock` når oppslaget mot entra-proxy er ferdig, og
  følger raden. Et cachetreff gir samme `updatedAt` som da raden ble hentet.
  Caffeine sin `Ticker` måler bare tid og gir ikke et tidspunkt, derfor egen
  `Clock`. `Instant` og `Clock` er fra `kotlin.time`, som ellers i backend
  (`soknad/Api.kt`).
- Maks 10 000 rader. Det er langt over antall saksbehandlere, og hindrer at
  minnet vokser uten grense.
- Introspeksjonen av tokenet kjører fortsatt for hver request. Et utløpt eller
  ugyldig token gir 401 før cachen brukes.
- Metrikk: Caffeine sin `recordStats()` registreres i `Metrics.meterRegistry`
  med `CaffeineCacheMetrics`, navn `entra_berikelse`, slik at treffraten
  synes i Grafana.

Kostnaden er at endringer i Entra slår inn med opptil 5 minutters
forsinkelse. Mister en saksbehandler en gruppe eller en enhet, beholder hen
tilgangen til raden utløper eller hen får nytt token. Tilgangsmaskinen kalles
fortsatt for hver sak og er ikke cachet.

🔴 Rød sone: autentisering. Alle saksbehandlerruter går gjennom denne blokken.

Følger av endringen:

- `hentEnheterForPrincipal` i `Tilgangsjekker.kt` fjernes. Listen leser
  `principal.enheter` direkte. `sjekkTilgangTilEnhet` mister try/catch-en for
  entra-proxy, og `AzureAdPrincipal.harTilgangTilEnhet` blir en vanlig
  funksjon over `enheter`, ikke `suspend`. Testene `liste gir 503 når
  entra-proxy feiler for enheter` og `detalj gir 503 når entra-proxy feiler for
  enheter` beholder 503. Nå kommer svaret fra `AZURE_AD_PROVIDER`, ikke fra
  ruten.
- `/meg` bygger svaret fra principal alene: `navn`, `epost ?: ""`,
  `gjeldendeEnhet`, `enheter` og `updatedAt`. Den kaller ikke lenger
  `hentAnsatt`, og try/catch-en som gir 500 «Kunne ikke hente ansattdata.»
  fjernes. Feiler entra-proxy, svarer `AZURE_AD_PROVIDER` 503 før ruten
  kjører. `EntraProxyClient` brukes fortsatt av sakslogg-ruten
  (`slaaOppNavn`).
- `InnloggetAnsattResponse` får `updatedAt: Instant` (fra
  `principal.berikelseUpdatedAt`), slik at man ser i nettverksfanen hvor gamle
  dataene er. Frontend viser den ikke. `InnloggetAnsatt` i
  `frontend/saksbehandling/src/mock/ansatt.ts` får `updatedAt: string`, og
  `mockInnloggetAnsatt` får en verdi.
- Testoppsett som bygger `AzureAdPrincipal` eller mocker entra-proxy
  (`mockEntraProxyFull`), må svare på `/api/v1/ansatt/{navIdent}` med et navn.
  `VilkarsvurderingApiTest` svarer i dag `ansattProvider = { "{}" }` og må
  endres, ellers gir alle testene der 500.

### 2. Backend: API

Nye ruter i `configureSaksbehandlingSakApiV1` (`SakApi.kt`), under
`/api/saksbehandling/v1/saker/{sakId}/tildeling`:

| Metode | Publiserer | Body |
|--------|-----------|------|
| `POST` | `SakTildeltSaksbehandler` med innlogget ident og `principal.navn` | ingen |
| `DELETE` | `SakFrigjort` med innlogget ident | ingen |

Ruten leser saken, sjekker tilgang og forutsetninger, og publiserer eventen i en
egen transaksjon: `transaction(database) { publishEventQueue(...) }`. Ruten
skriver ikke til `sak` eller `sakslogg`. Begge svarer `202 Accepted`, fordi
endringen skjer når handleren har kjørt.

**Tilgang.** Samme sjekker som detaljoppslaget, i samme rekkefølge, med
funksjonene fra `Tilgangsjekker.kt`:

1. `principalMedRolle(Role.SAKSBEHANDLER)` (ikke `BESLUTTER` alene) → ellers 403.
2. `sakIdParameter()` → ugyldig id gir 400.
3. `hentSakTilgangsgrunnlag(sakId)` → saken finnes ikke gir 404.
4. `sjekkTilgangTilEnhet` → 403 `IKKE_TILGANG_ENHET`.
5. `sjekkTilgangsmaskin` med `Regelsett.KJERNE` på den ansattes fnr → avvist
   gir 403, feil gir 503.
6. Bare `DELETE`: `sjekkErSaksbehandlerPåSak` → 403 `IKKE_TILDELT_SAK`.

Vi lager ingen egne tilgangssjekker for tildeling. Tildelingen viser ingen
personopplysninger og sporingslogges ikke til ArcSight. Detaljoppslaget logger
som før.

`SakTilgangsgrunnlag` får feltet `status: Saksstatus`, og
`hentSakTilgangsgrunnlag` leser det. Da trenger tildelingen ett oppslag mot
`sak`. I samme fil retter vi `SakDetaljer.tilTilgangsgrunnlag()`, som i dag
setter `beslutterIdent = saksbehandlerIdent`. Ingen bruker feltet ennå, men
`sjekkErBeslutterPåSak` ville gitt feil svar.

🔴 Rød sone: tilgangssjekken må være identisk med detaljoppslaget. Uten steg 5
kan en saksbehandler tildele seg en sak med kode 6/7 eller egen ansatt.

**Forutsetninger** (leses fra `SakTilgangsgrunnlag` før publisering):

| Sjekk | Svar                                       |
|-------|--------------------------------------------|
| Status er `AVSLATT` eller `AVSLUTTET` | `409`, `kode = SAK_UGYLDIG_STATUS`         |

Tildeler innlogget seg en sak hen allerede har, publiserer ruten ingenting og
svarer `202`.

Ingen sjekk mot `beslutter_ident`. Den som står som beslutter, kan tildele seg
saken. Topartskontrollen håndheves når vedtaket fattes (se *Beslutninger*).

**Én regel for tildeling.** Reglene for `POST` samles i én funksjon i
`SakApi.kt`:

```kotlin
fun tildelingHindring(status: Saksstatus, saksbehandlerIdent: String?, principal: AzureAdPrincipal): TildelingHindring?
```

Parameterne er felter, ikke `SakTilgangsgrunnlag`, fordi listen ikke har fnr og
ikke kan bygge et tilgangsgrunnlag. Rollen sjekkes med `principal.harRolle`.

Den returnerer `null` når innlogget kan tildele seg saken, ellers første
hindring: mangler rollen `SAKSBEHANDLER`, saken har status `AVSLATT` eller
`AVSLUTTET`, eller innlogget har saken allerede. `POST` mapper hindringen til
svarkoden over. «Har saken allerede» gir `202` uten event. Listen og
detaljoppslaget bruker samme funksjon til
feltet `kanTildeleMeg` (se steg 4). Knappen og API-et kan da ikke vise ulike
svar. Tilgangsmaskinen og enhetstilgangen sjekkes utenfor funksjonen, fordi
listen og detaljoppslaget gjør det på hver sin måte. Listen legger i tillegg
på kravet om at saken ikke er tildelt (se steg 4).

**Logging.** Logg `sakId` og utfallet. Aldri ident eller fnr.

### 3. Backend: handlere

To nye handlere i `event/handlers/`, registrert i `configureEventHandlers`.
Uten handler blir eventen liggende i køen med `TransientError`
(«No handlers registered»).

**Felles skrivefunksjoner.** Handlerne og `SakProjection` (steg 3b) gjør samme
endring på `sak`. Den ligger i to funksjoner i `saksbehandling/Db.kt`, så de to
skriveveiene ikke kan gli fra hverandre:

```kotlin
fun JdbcTransaction.tildelSak(
    soknadId: UUID, ident: String, navn: String, eventId: Long, tidspunkt: Instant,
): Boolean

fun JdbcTransaction.frigjoerSak(
    soknadId: UUID, ident: String, eventId: Long, tidspunkt: Instant,
): Boolean
```

Begge returnerer `true` når `UPDATE` traff en rad.

- Saken finnes via `soknad_id`, ikke `sak_id`. `sak_id` genereres av databasen,
  så en sak som bygges på nytt fra loggen får ny `sak_id`. `soknad_id` er den
  samme, og sak og søknad er 1:1.
- `tildelSak`:
  ```sql
  UPDATE sak
  SET saksbehandler_ident = :ident,
      saksbehandler_navn  = :navn,
      status = CASE WHEN status = 'OPPRETTET' THEN 'UNDER_BEHANDLING' ELSE status END,
      tildeling_event_id  = :eventId,
      sist_endret = :tidspunkt
  WHERE soknad_id = :soknadId
    AND (tildeling_event_id IS NULL OR tildeling_event_id < :eventId)
  ```
  Status går bare fra `OPPRETTET` til `UNDER_BEHANDLING`, samme overgang og
  vilkår som `SakProjection` bruker for `SaksbehandlingStartetIArena`. Overtar en
  kollega en sak som er `UNDER_BEHANDLING`, `TIL_BESLUTNING` eller `INNVILGET`,
  står statusen.
- `frigjoerSak`:
  ```sql
  UPDATE sak
  SET saksbehandler_ident = NULL,
      saksbehandler_navn  = NULL,
      tildeling_event_id  = :eventId,
      sist_endret = :tidspunkt
  WHERE soknad_id = :soknadId
    AND saksbehandler_ident = :ident
    AND (tildeling_event_id IS NULL OR tildeling_event_id < :eventId)
  ```
  Har en kollega tatt saken i mellomtiden, frigjør vi ikke kollegaens
  tildeling. Status endres ikke. Funksjonen får en kommentar om hvorfor: en
  frigjort sak blir stående som `UNDER_BEHANDLING` uten saksbehandler, fordi
  behandlingen er startet, og vi går ikke tilbake til `OPPRETTET`.
- **Last writer wins etter event-id.** Vilkåret på `tildeling_event_id` gjør at
  en eldre event aldri skriver over en nyere. Det gjelder både når to pods
  kjører eventer i motsatt rekkefølge, og når projeksjonen kommer etter
  handleren med samme event.
- `sist_endret` settes fra eventens `tidspunkt`, som i `SakProjection`. Da gir
  replay samme verdi som live.

**`TildelSaksbehandler`** (`SakTildeltSaksbehandler`), i én transaksjon:

- Sjekker at saken finnes.
- `tildelSak(...)` med `event.id`.
- `publishEventQueue(SakOppdatert(...))` med `utfortAvRolle = SAKSBEHANDLER`,
  `utfortAvIdent = :ident`, `notat = "Sak tildelt"` og `tidspunkt` fra eventen.
  «Sak tildelt» er eksempelteksten i `sakslogg.md`. Publiseres også når
  `tildelSak` ikke traff fordi en nyere tildeling allerede er brukt.
  Saksbehandleren klikket, og saksloggen skal vise begge tildelingene.

**`FrigjoerSak`** (`SakFrigjort`), i én transaksjon:

- `frigjoerSak(...)` med `event.id`.
- `SakOppdatert` med `notat = "Sak frigjort"`, bare når `frigjoerSak` traff.

Handlerne publiserer `SakOppdatert` i stedet for å skrive til `sakslogg` selv.
Da har saksloggen én skrivevei (`SkrivSakslogg`). Vi publiserer fra handleren og
ikke fra API-et, slik at loggen bare viser endringer som faktisk ble gjort. En
frigjøring som ikke traff, gir ingen loggpost.

Felles for begge:

- Finnes ikke saken, gir handleren `unrecoverableError`, som i
  `SettGodkjentSoknadStatus`.
- Andre databasefeil gir `transientError` med `rollback()`, som i
  `SettGodkjentSoknadStatus`.
- **Idempotens.** Krasjer poden etter commit, men før EventManager har lagret
  handler-state, kjører handleren igjen. `tildeling_event_id` hindrer dobbel
  skriving til `sak`, men uten vern ville handleren publisert `SakOppdatert` to
  ganger og gitt to loggposter. Handlerne bruker derfor
  `IdempotencyGuard`, som `SkrivSakslogg` og `JournalfoerInnsendtSoknad`:
  `isGuarded` først, og `UPDATE`, `publishEventQueue` og `guard` i samme
  transaksjon.

`saksbehandling/Db.kt`: `SakTable` får `saksbehandlerNavn` og
`tildelingEventId`, og filen får `tildelSak` og `frigjoerSak`. `SaksloggTable`
og `AktorRolle` finnes.

### 3b. Backend: `SakProjection`

`SakProjection` håndterer de nye eventene, slik at tildelinger kommer med når
`sak` bygges fra loggen:

| Event | Effekt på `sak` |
|-------|-----------------|
| `SakTildeltSaksbehandler` | `tildelSak(soknadId, ident, navn, event.id, tidspunkt)` |
| `SakFrigjort` | `frigjoerSak(soknadId, ident, event.id, tidspunkt)` |

- Projeksjonen kjører også live. Den leser `event_log`, og eventen havner der
  først når handleren er ferdig. Da er `tildeling_event_id` allerede satt til
  samme id, og `UPDATE` treffer ikke. Projeksjonen skriver altså ikke over
  handleren, og en eldre event skriver ikke over en nyere.
- Ved replay mot en tom `sak` er `tildeling_event_id` `NULL`, og eventene brukes
  i rekkefølge.
- Ingen filter på `kilde_til_behandling = ARENA` for disse eventene. Tildeling
  gjelder alle saker.
- Projeksjonen publiserer ikke `SakOppdatert`. Saksloggen skrives bare live.
- Ingen versjonsbump i `name`. Ingen tildelingseventer finnes før denne
  featuren, så det er ingenting å spille av på nytt.
- KDoc i `SakProjection.kt`, tabellen i `event/projections/README.md` og
  `specifications/sak_projection.md` oppdateres med de to eventene.

### 4. Backend: `kanTildeleMeg` og navnet på saksbehandleren

`SakListeElement` og `SakDetaljer` får feltet `kanTildeleMeg: Boolean`. Frontend
viser «Tildel meg» bare når feltet er `true`, og regner ikke ut reglene selv.

**Detaljoppslaget.** Svaret sendes bare når enhetssjekken og Tilgangsmaskinen
har gitt tilgang. Da er `kanTildeleMeg = tildelingHindring(...) == null`, også
når saken er tildelt en annen. En kollega kan da ta over saken hvis den som har
den, er borte.

**Listen.** Listen viser bare saker på enheter saksbehandleren har tilgang
til. Der er `kanTildeleMeg = saksbehandlerIdent == null && tildelingHindring(...) == null`.
Listen viser altså ikke «Tildel meg» på saker som er tildelt en annen. Å ta
over en kollegas sak gjør saksbehandleren fra saksdetaljen, der hen ser hvem
som har saken. Listen kaller ikke Tilgangsmaskinen og henter ikke fnr.

Det betyr at listen kan vise «Tildel meg» på en sak der Tilgangsmaskinen
avviser den ansatte, for eksempel kode 6/7 eller egen ansatt. Da svarer `POST`
med 403, og ingen event publiseres. Tilgangen er fortsatt sikret i `POST`, men
knappen stemmer ikke alltid i listen. Vi godtar det til listen eventuelt får en
egen vask mot Tilgangsmaskinen. Den vasken skal i så fall gjelde hvilke saker
listen viser, ikke bare tildeling.

**Navn.** `SakListeElement` og `SakDetaljer` får feltet
`saksbehandlerNavn: String?`, lest fra `sak.saksbehandler_navn`. Feltet er
`null` bare når saken ikke er tildelt. Ingen oppslag i entra-proxy, verken i
listen eller i detaljoppslaget.

### 5. Frontend: felles hook

Ny hook `useTildeling(sakId)` i `hooks/`, etter mønsteret fra
`useVilkårsvurdering`: `tildelMeg()`, `frigjoer()`, `isSaving`, `error`.

Svaret er `202`, og handleren kjører vanligvis innen et par hundre millisekunder
(EventManager poller hvert 100. ms). Hooken oppdaterer derfor SWR-cachen for
listen og saken optimistisk med ny `saksbehandlerIdent`,
`saksbehandlerNavn = innloggetAnsatt.navn`, `kanTildeleMeg = false` og
`status = UNDER_BEHANDLING` når status var `OPPRETTET`, og
revaliderer etterpå. Ved frigjøring settes ident og navn til `null`, og status
står.
Den revaliderer også `SAKSBEHANDLING_SAKSLOGG_URL(sakId)`, slik at «Logg» viser
den nye posten. Feiler kallet, ruller hooken tilbake cachen og viser feilen.

### 6. Frontend: saksoversikten

`OversiktPage.tsx`:

- Kolonnen «Saksbehandler» viser «Tildel meg» (`Button size="xsmall" variant="secondary"`,
  som i skissen `695:23735`) når `sak.kanTildeleMeg` er `true`.
  Ellers viser den `saksbehandlerNavn`, eller «–» når
  saken ikke er tildelt.
- «Tildel meg» kaller `tildelMeg()`.
- Knappen stopper `onClick` fra å boble opp til raden, ellers åpner klikket
  saken.
- Knappen viser `loading` mens kallet går.
- Feil vises i en `Alert` over tabellen. Etter `409` henter vi listen på nytt,
  slik at `kanTildeleMeg` blir oppdatert. Ved `403` sier meldingen at du ikke
  har tilgang til å behandle saken.
- Menyvalget «Tildel saksbehandler» (tildele en annen) er utenfor scope og står
  urørt.
- Fanene endres ikke. `ferdigeSaksstatuser` regner fortsatt `INNVILGET` som
  avsluttet, så innvilgede saker ligger under «Avsluttet». En ledig innvilget
  sak får likevel «Tildel meg» der, fordi knappen følger `kanTildeleMeg`.

### 7. Frontend: saksdetaljen

`SakPage.tsx`: linjen med «Tilbake til liste av saker» er allerede en
`HStack justify="space-between"` med `<Sakslogg />` til høyre. Tildelingen
legges i en `HStack` til høyre, foran «Logg», slik skissene `660:22383`,
`691:22948` og `694:23201` viser.

| Tilstand | Viser |
|----------|-------|
| `saksbehandlerIdent === innloggetAnsatt.id` | «Saken er tildelt meg» + «Meny» |
| Tildelt en annen | «Saken er tildelt {`saksbehandlerNavn`}», + «Tildel meg» når `kanTildeleMeg`. Ingen «Meny». |
| Ikke tildelt | «Tildel meg» når `kanTildeleMeg`, ellers ingenting. Ingen «Meny». |

- «Tildel meg» er `Button size="small" variant="secondary"`.
- «Meny» er en Aksel `ActionMenu`. Triggeren ser ut som «Logg»-knappen i
  `Sakslogg.tsx`: `Button size="small"` med chevron ned/opp. Skissen tegner de
  to med samme header-mønster.
- Menyen har én `ActionMenu.Group` med label «Legg behandlingen tilbake» og
  valget «Frigjør oppgave». Aksel-gruppene kan ikke legges sammen, så vi
  dropper chevronene på gruppene i skissen.
- Gruppen «Avslutt behandling» («Avvis søknad», «Trekk søknad») bygges ikke nå.
- «Frigjør oppgave» kaller `frigjoer()` og navigerer til oversikten. «Oppgave»
  er bare UI-teksten fra skissen. Kode, eventer og saksloggen sier «sak».
- Feil vises i en liten `Alert` under linjen.

Typene `SakInfo` (`useSaker.ts`) og `SakDetaljer` (`useSak.ts`) får
`kanTildeleMeg: boolean` og `saksbehandlerNavn: string | null`. `null` betyr
ikke tildelt, som for `saksbehandlerIdent`.

### 8. MSW-mock

`mocks/handlers.ts` får `POST` og `DELETE` for
`/api/saksbehandling/v1/saker/:sakId/tildeling`. De oppdaterer
`saksbehandlerIdent` og `saksbehandlerNavn` i den samme `MockSak`-listen som
mocken for vilkårsvurdering leser, og svarer `202`. `POST` setter også status
`OPPRETTET` til `UNDER_BEHANDLING`. Da kan man tildele seg en sak og vurdere
vilkår mot mocken. `DELETE` på en sak innlogget ikke har, svarer 403
`IKKE_TILDELT_SAK`, som mocken for vilkårsvurdering.
Mocken setter
`kanTildeleMeg` ut fra status og om saken er tildelt
mock-saksbehandleren. I listen er feltet også `false` når saken er tildelt en
annen. Mocken har minst én sak der feltet er `false`, én sak tildelt en annen
der detaljen har `kanTildeleMeg = true`. Alle tildelte saker har navn.
Mocken bruker oppdiktede navn.

### 9. Dokumentasjon

- `event/README.md`: de nye eventene i raden `SakOppdatert`,
  `VilkarsvurderingOppdatert` → `soknadId` i tabellen over `aggregateRootId`.
  Verifiserings-SQL-en får `event_json ->> 'soknadId'` i `coalesce`. Den
  mangler i dag, så `SakOppdatert` og `VilkarsvurderingOppdatert` gir allerede
  avvik der.
- `event/event-flow.md`: generer på nytt med `EventFlowDiagram.kt`.
- `specifications/sak-datamodell.md`: kolonnene `saksbehandler_navn` og
  `tildeling_event_id` i tabellen over `sak`. Avsnittet om
  `chk_saksbehandler_ulik_beslutter` fjernes. Habilitetsavsnittet under
  `sakslogg` skrives om: beslutter kan ikke være den som sendte saken til
  `TIL_BESLUTNING`, regelen gjelder vedtaket og bygges sammen med vedtak.
  Kravet om at alle saksbehandlerhandlinger logges står, fordi loggen er
  sporet for saken.
- `event/projections/README.md` og `specifications/sak_projection.md`: de to nye
  eventene i tabellen over effekter på `sak`.

### 10. Tester

`SaksbehandlingSakApiTest`:

- `POST` publiserer `SakTildeltSaksbehandler` med innlogget ident og navnet fra
  entra-proxy → 202, og `sak` er uendret
- `POST` på sak tildelt en annen publiserer også (overtakelse) → 202
- `POST` på sak innlogget allerede har → 202 uten event
- `DELETE` på egen sak publiserer `SakFrigjort` → 202
- `DELETE` på sak tildelt en annen → 403 `IKKE_TILDELT_SAK`, ingen event
- sak med status `AVSLATT` eller `AVSLUTTET` → 409 `SAK_UGYLDIG_STATUS`, ingen event
- `POST` på `INNVILGET` sak tildelt en annen publiserer (overtakelse) → 202
- innlogget er beslutter på saken → 202 og event (ingen beslutter-sjekk ved
  tildeling)
- bare `BESLUTTER`-rolle → 403
- sak på annen enhet → 403 uten kall mot Tilgangsmaskinen
- Tilgangsmaskinen avviser → 403, ingen event
- Tilgangsmaskinen feiler → 503, ingen event
- liste og detaljoppslag gir `saksbehandlerNavn` fra `sak.saksbehandler_navn`,
  uten kall mot entra-proxy for navnet til saksbehandleren på saken
- liste og detalj gir fortsatt 503 når entra-proxy feiler for enheter (dagens
  tester, uendret forventning)
- `tilTilgangsgrunnlag()` gir `beslutterIdent` fra saken, ikke
  `saksbehandlerIdent`

Autentisering (`AZURE_AD_PROVIDER`), i `SaksbehandlerRoutingTest`, som dekker
auth i dag:

- principal får `navn` fra `visningNavn`, ellers fornavn og etternavn
- principal får `enheter`, `epost` og `gjeldendeEnhet` fra entra-proxy
- `/meg` svarer med data fra principal og gjør ingen egne kall mot
  entra-proxy (happy path-testen sjekker at `hentAnsatt` kalles én gang per
  token, ikke én gang i auth og én i ruten)
- `/meg` returnerer `updatedAt` fra berikelsen
- `name`-claimet i tokenet brukes ikke, heller ikke når det er satt
- 503 når `hentGrupper`, `hentAnsatt` eller `hentEnheter` feiler, aldri 401
- 500 når entra-proxy gir en ansatt uten navn
- 401 når tokenet er ugyldig eller mangler `NAVident`, som i dag

`EntraBerikelseCacheTest` i `entraproxy/`, med en `Ticker` og en `Clock` som
testen styrer:

- to oppslag med samme token gir ett sett kall mot entra-proxy
- samtidige oppslag med samme token gir ett sett kall
- ulike tokener gir hvert sitt oppslag
- etter `ttl` hentes dataene på nytt, før `ttl` gjør de ikke det
- `updatedAt` er tidspunktet oppslaget ble gjort: uendret ved cachetreff, nytt
  etter `ttl`
- et oppslag som feilet, caches ikke: neste oppslag kaller entra-proxy igjen

Migreringen, i `SakProjectionTest` eller `TildelSaksbehandlerTest`:

- CHECK `chk_saksbehandler_navn_med_ident` avviser ident uten navn og navn
  uten ident
- `kanTildeleMeg` i detaljoppslaget: `true` for ledig sak, sak tildelt en
  annen, `INNVILGET` sak og sak der innlogget er beslutter; `false` når status
  er `AVSLATT` eller `AVSLUTTET`, innlogget har saken eller mangler rollen
  `SAKSBEHANDLER`
- `kanTildeleMeg` i listen følger `tildelingHindring`, er `false` for saker
  tildelt en annen, og listen kaller ikke Tilgangsmaskinen

`TildelSaksbehandlerTest` og `FrigjoerSakTest` i `event/handlers/`, etter
mønsteret fra `SkrivSaksloggTest`, med testdata fra `sak/SakTestData.kt`:

- tildeling setter `saksbehandler_ident` og `saksbehandler_navn`, setter
  status `OPPRETTET` til `UNDER_BEHANDLING`, og publiserer én `SakOppdatert` med
  rolle `SAKSBEHANDLER`, identen, «Sak tildelt» og `tidspunkt` fra eventen
- tildeling av sak med status `TIL_BESLUTNING` eller `INNVILGET` endrer ikke
  status
- frigjøring endrer ikke status
- to tildelinger etter hverandre: den siste vinner, også for navnet
- samme event to ganger gir én `SakOppdatert`
- frigjøring nuller ident og navn og publiserer «Sak frigjort»
- frigjøring når en annen har saken endrer ingenting og publiserer ikke
- en eldre event etter en nyere (lavere `event.id`) endrer ikke `sak`, men
  tildeling publiserer likevel `SakOppdatert`
- ukjent sak gir `unrecoverableError`
- tildeling til den som er `beslutter_ident` på saken, setter ident og navn
  (CHECK-en er fjernet)

`SakProjectionTest`:

- `SakTildeltSaksbehandler` setter ident, navn, `tildeling_event_id` og
  `OPPRETTET` → `UNDER_BEHANDLING`, også for saker med kilde `EKSPERTBISTAND`
- `SakFrigjort` nuller ident og navn, og endrer ikke status
- samme event etter at handleren har brukt den, endrer ingenting
- replay mot en tom `sak` (`SoknadInnsendt`, tildeling, overtakelse, frigjøring)
  gir samme sluttilstand som live
- tildeling der identen er beslutter på saken, kaster ikke og tildeler saken
- tildeling for en søknad uten sak gjør ingenting

Frontend har ikke testoppsett i `saksbehandling`. Vi verifiserer med
`pnpm typecheck`, `pnpm lint` og manuelt mot MSW-mocken.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V20__sak_tildeling.sql` | Ny: kolonnene `saksbehandler_navn` og `tildeling_event_id`, CHECK `chk_saksbehandler_navn_med_ident`, fjerner `chk_saksbehandler_ulik_beslutter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Auth.kt` | `AzureAdPrincipal` med `navn`, `epost`, `gjeldendeEnhet` og `enheter`, hentet parallelt fra entra-proxy. `name`-claimet fjernes. Feil hos entra-proxy kaster `EntraProxyUtilgjengeligException` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | `StatusPages` gjør `EntraProxyUtilgjengeligException` om til 503. `provide(EntraBerikelseCache::class)` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/entraproxy/EntraBerikelseCache.kt` | Ny: cache per token for grupper, ansattdata og enheter |
| `backend/pom.xml` | Ny avhengighet: Caffeine |
| `backend/src/test/kotlin/no/nav/ekspertbistand/entraproxy/EntraBerikelseCacheTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt` | `provide(EntraBerikelseCache::class)` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/entraproxy/EntraProxyClient.kt` | `UtvidetAnsatt.visningsnavn()` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlerApi.kt` | `/meg` bygger svaret fra principal, uten `hentAnsatt` og try/catch. `InnloggetAnsattResponse.updatedAt` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Sakslogg.kt` | `slaaOppNavn` bruker `visningsnavn()` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlerRoutingTest.kt` | Auth-testene over |
| `backend/src/test/kotlin/no/nav/ekspertbistand/mocks/EntraProxyMock.kt` | Ansatt med navn som standard der testene trenger det |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | `SakTable.saksbehandlerNavn`, `SakTable.tildelingEventId`, `tildelSak`, `frigjoerSak` (med kommentar om status) |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/projections/SakProjection.kt` | Håndterer `SakTildeltSaksbehandler` og `SakFrigjort`, KDoc |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/projections/README.md` | Tabellen for `SakProjection` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | `SakTildeltSaksbehandler`, `SakFrigjort`, registrering av handlerne |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/TildelSaksbehandler.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/FrigjoerSak.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `POST`/`DELETE …/tildeling` med sjekkene fra `Tilgangsjekker.kt`, `tildelingHindring`, `kanTildeleMeg` og `saksbehandlerNavn` i liste og detalj. Listen leser `principal.enheter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Tilgangsjekker.kt` | `SakTilgangsgrunnlag.status`, `hentEnheterForPrincipal` fjernes, `sjekkTilgangTilEnhet` uten try/catch, `tilTilgangsgrunnlag()` setter `beslutterIdent` riktig |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/VilkarsvurderingApiTest.kt` | Entra-proxy-mocken svarer med navn |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/TestEventData.kt` | Eksempler på `SakTildeltSaksbehandler` og `SakFrigjort` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/EventDataAggregateRootIdTest.kt` | De to eventene gir `soknadId` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md` | `aggregateRootId`-tabell og verifiserings-SQL |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/event-flow.md` | Generert på nytt |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlingSakApiTest.kt` | API-testene over |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/handlers/TildelSaksbehandlerTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/handlers/FrigjoerSakTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/projections/SakProjectionTest.kt` | Testene for de nye eventene |
| `frontend/saksbehandling/src/hooks/useTildeling.ts` | Ny |
| `frontend/saksbehandling/src/hooks/useSak.ts` | `saksbehandlerNavn`, `kanTildeleMeg` |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | `kanTildeleMeg`, `saksbehandlerNavn` på `SakInfo` |
| `frontend/saksbehandling/src/utils/constants.ts` | `SAKSBEHANDLING_TILDELING_URL` |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | «Tildel meg» kobles til API |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Tildeling foran «Logg» i linjen med tilbakelenken |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Mock for tildeling |
| `frontend/saksbehandling/src/mock/ansatt.ts` | `InnloggetAnsatt.updatedAt` og verdi i `mockInnloggetAnsatt` |
| `specifications/sak-datamodell.md` | Kolonnene `saksbehandler_navn` og `tildeling_event_id` |
| `specifications/sak_projection.md` | De to nye eventene |

Én ny migrering, V20. `sak.saksbehandler_ident` finnes fra V12, `sakslogg` er
oppdatert i V18, og `saksvilkar` er bygd om i V19.

## Kanttilfeller og feilmodus

- **To trykker «Tildel meg» samtidig.** Begge får `202`. Eventen med høyest id
  vinner, også når to pods kjører dem i motsatt rekkefølge, fordi
  `tildeling_event_id` hindrer at en eldre event skriver over en nyere. Begge
  tildelingene står i saksloggen.
- **Frontend viser gammel tilstand.** Revalideringen kan komme før handleren har
  kjørt. Den optimistiske cachen dekker de første millisekundene. Laster brukeren
  siden på nytt med en gang, kan hen se forrige tildeling et øyeblikk.
- **Vilkår vurderes rett etter tildeling.** `PATCH …/vilkarsvurdering` leser
  `sak` direkte. Rekker saksbehandleren å vurdere et vilkår før handleren har
  kjørt, svarer API-et 403 `IKKE_TILDELT_SAK` eller 409 (status er fortsatt
  `OPPRETTET`). Vinduet er et par hundre millisekunder, og et nytt forsøk går
  gjennom. Vi godtar det.
- **Frigjort sak.** Saken står som `UNDER_BEHANDLING` uten saksbehandler, og
  `PATCH …/vilkarsvurdering` svarer 403 `IKKE_TILDELT_SAK` til noen tildeler
  seg saken igjen.
- **Køen står fast.** Henger EventManager, blir tildelingen liggende i
  `event_queue` til den kjører. Eksisterende metrikk for kødybde fanger dette.
- **Saksbehandler mister tilgang til enheten etter tildeling.** Saken blir
  usynlig i listen, men `saksbehandler_ident` står. Vi rydder ikke opp i dette
  kortet.
- **Entra-proxy nede.** Requester med et token som allerede er i cachen, går
  som før. Requester med et nytt token svarer 503, fordi
  `AZURE_AD_PROVIDER` ikke kan hente grupper, navn og enheter. Brukeren sendes
  ikke til innlogging. `SakPage` viser allerede en egen melding for 503. I dag
  gir en feil på gruppene 401. Det retter vi i dette kortet.
- **Tilgang fjernes i Entra.** Saksbehandleren beholder gruppene og enhetene
  fra cachen i opptil 5 minutter. Vi godtar det, se *Cache per token*.
- **Entra-proxy gir ikke noe navn.** Requesten avvises med 500. En tildelt sak
  har derfor alltid navn.
- **Navnet endres i Entra.** `saksbehandler_navn` er navnet da saken ble
  tildelt. Det oppdateres ved neste tildeling. Vi godtar det.
- **Tilgangsmaskinen avviser fra listen.** Listen viser «Tildel meg» på en sak
  med kode 6/7 eller egen ansatt. `POST` svarer 403, ingen event publiseres, og
  frontend ruller tilbake den optimistiske oppdateringen og viser feilen.
- **Tilgangsmaskinen nede.** Listen vises som før. Tildeling og detaljoppslag
  svarer 503.
- **Listen er gammel.** `kanTildeleMeg` gjelder da listen ble hentet. Har
  saken fått status `AVSLATT` eller `AVSLUTTET` i mellomtiden, svarer
  `POST` med 409, og frontend viser feilen og henter listen på nytt. Har en
  kollega tildelt seg saken i mellomtiden, tar `POST` over saken (last writer
  wins). Begge tildelingene står i saksloggen.
- **Beslutter tildeler seg saken.** Det er lov. Saken kan da ha samme person
  som saksbehandler og beslutter. Topartskontrollen sjekker ved vedtak at
  beslutter ikke sendte saken til `TIL_BESLUTNING`. Den regelen finnes ikke
  ennå, men `beslutter_ident` settes heller ikke av noe i dag.
- **Arena-saker.** Alle saker har i dag `kilde_til_behandling = ARENA`.
  Arena-eventene i `SakProjection` endrer status, enhet og `arena_sak_id`, ikke
  `saksbehandler_ident`, `saksbehandler_navn` eller `tildeling_event_id`.
- **Status.** Tildeling setter `OPPRETTET` til `UNDER_BEHANDLING`, som
  `sak-datamodell.md` beskriver. Andre statuser står.
- **Frigjøring endrer ikke status.** En frigjort sak blir stående som
  `UNDER_BEHANDLING` uten saksbehandler. Behandlingen er startet, og vi går
  ikke tilbake til `OPPRETTET`. Kommentaren står i `frigjoerSak`.
- **Arena og tildeling setter samme status.** `SaksbehandlingStartetIArena`
  (via `SakProjection`) og tildeling setter begge `OPPRETTET` →
  `UNDER_BEHANDLING` med samme vilkår. Den som kommer sist, gjør ingenting.
- **Replay av `SakProjection`.** Tildelinger, navn og status fra tildeling
  kommer med. Saksloggen bygges ikke på nytt av projeksjonen.
- **Eventen feiler i handleren.** Får eventen `COMPLETED_WITH_ERRORS`, leser
  projeksjonen den ikke (den leser bare `COMPLETED`). Da blir den heller ikke
  brukt ved replay, som live.
- **Vilkårsvurdering.** Kortet sier ikke at bare tildelt saksbehandler kan
  vurdere vilkår. Vi låser ikke vurderingen nå.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] API-et publiserer `SakTildeltSaksbehandler`/`SakFrigjort` og skriver ikke selv til `sak` eller `sakslogg`
- [ ] Handlerne oppdaterer `sak.saksbehandler_ident` og `sak.saksbehandler_navn` og publiserer `SakOppdatert`, idempotent
- [ ] Tildeling setter `OPPRETTET` til `UNDER_BEHANDLING`. Frigjøring endrer ikke status, og `frigjoerSak` har en kommentar om hvorfor
- [ ] `SakProjection` bruker `tildelSak`/`frigjoerSak` for de nye eventene, og replay gir samme tildeling som live
- [ ] Listen og saksdetaljen viser navnet fra tildelingen. En tildelt sak har alltid navn (CHECK). Ingen navneoppslag i entra-proxy ved visning
- [ ] `AzureAdPrincipal` har `navn`, `epost`, `gjeldendeEnhet` og `enheter` fra entra-proxy, hentet i `AZURE_AD_PROVIDER`. `name`-claimet brukes ikke
- [ ] Feil hos entra-proxy gir 503 på alle saksbehandlerruter, også for gruppene. Aldri 401
- [ ] `/meg` gjør ingen egne kall mot entra-proxy, og returnerer `updatedAt` for berikelsen
- [ ] Grupper, ansattdata og enheter caches per token i `ENTRA_PROXY_CACHE_TTL` (5 minutter), med `updatedAt` for når de ble hentet. Feil caches ikke
- [ ] «Tildel meg» vises bare når `kanTildeleMeg` er `true`, i listen og på saksdetaljen
- [ ] Listen viser ikke «Tildel meg» på saker tildelt en annen. Saksdetaljen gjør det
- [ ] «Meny» vises bare på saker tildelt innlogget
- [ ] `kanTildeleMeg` og `POST` bruker samme `tildelingHindring`. Listen kaller ikke Tilgangsmaskinen
- [ ] Tildeling og frigjøring vises i «Logg» på `SakPage`
- [ ] «Tildel meg» i listen tildeler saken og oppdaterer raden
- [ ] `SakPage` viser riktig tilstand for tildelt meg, tildelt annen og ikke tildelt
- [ ] «Frigjør oppgave» fjerner tildelingen
- [ ] Tildeling krever samme tilgang som detaljoppslaget, inkludert Tilgangsmaskinen, med sjekkene fra `Tilgangsjekker.kt`
- [ ] Tildeling har ingen beslutter-sjekk, og `chk_saksbehandler_ulik_beslutter` er fjernet. `sak-datamodell.md` sier at topartskontrollen gjelder vedtaket
- [ ] `event/README.md` og `event-flow.md` er oppdatert
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
