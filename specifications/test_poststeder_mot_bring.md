# Test som sjekker at poststed-listen ikke er utdatert

Trello: https://trello.com/c/dDVUf4qg
Kort-ID: `6ab61f0fc2d5b6bdd5e282e1`

## Mål

En test som sammenligner den lokale poststed-listen
(`backend/src/main/resources/poststeder.json`) med kilden, og feiler hvis de er
ulike. Feilmeldingen skal fortelle utvikleren nøyaktig hva som er endret og hva
som må gjøres for å oppdatere listen.

## Bakgrunn

Ereg returnerer bare poststed for utenlandske adresser. `EregService` fyller
derfor inn poststed fra et lokalt register når det mangler
(`orFromMapping(postnummer)` i
`backend/src/main/kotlin/no/nav/ekspertbistand/ereg/EregService.kt`). Registeret
ble generert én gang (commit `f4f08aa`, 2026-02-12) av
`backend/src/test/kotlin/no/nav/ekspertbistand/executables/PoststedFetcher.kt`,
som laster ned Bring sin postnummertabell (tabulatorseparert, ISO-8859-1) og
skriver `postnummer → poststed` som JSON.

Ingenting varsler når Bring endrer tabellen. Listen er allerede utdatert: en
sammenligning 2026-09-28 viser tre nye postnummer hos Bring som mangler lokalt
(`1426 SOLBERG`, `5245 FANA`, `9653 HELLEFJORD`). Ingen er fjernet eller endret.

## Beslutninger fra kortet

Kortet har ingen kommentarer, sjekklister eller aktivitet. Beskrivelsen er
eneste kilde:

> Skriv en unit test som sjekker poststed listen ikke har endret seg og feiler
> dersom det er forskjell. Feilen bør gi beskjed om hva utvikler skal gjøre for å
> oppdatere listen.

Kilden til poststeder er Bring.

## Tilnærming

### 1. Trekk ut parsing fra `PoststedFetcher`

Parsingen av Bring-filen flyttes til en egen funksjon, slik at fetcheren og
testen bruker samme kode og ikke kan gli fra hverandre:

```kotlin
class PoststedFetcher(private val client: HttpClient = defaultHttpClient()) {
    suspend fun hentFraBring(): Map<String, String>   // last ned + parse
    suspend fun fetchAndWriteToFile()                  // hentFraBring() + skriv JSON
    companion object {
        const val URL = "…"                            // uendret URL
        fun parse(tsv: String): Map<String, String>    // dagens lines/split-logikk
    }
}
```

`main()` virker som før.

`resourceDir` er i dag `File("backend/src/main/resources")`, altså relativ til
repo-roten. Fetcheren skriver da til feil sted hvis den kjøres med `backend/`
som arbeidskatalog (standard for Maven og ofte for IntelliJ). Vi løser stien
slik at begge fungerer: bruk `src/main/resources` hvis den finnes, ellers
`backend/src/main/resources`. Liten endring, og den gjør instruksjonen i
feilmeldingen pålitelig.

### 2. Ny test

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/ereg/PoststederTest.kt`,
ved siden av de andre Ereg-testene. `kotlin.test` + JUnit 5, som resten av
prosjektet.

- Leser `/poststeder.json` fra klassestien, samme ressurs som `EregService`
  bruker.
- Henter og parser Bring-tabellen via `PoststedFetcher().hentFraBring()`.
- Sammenligner de to mappene. Ved avvik feiler testen med en melding på denne
  formen:

```
poststeder.json er utdatert mot Bring sin postnummertabell.

Nye postnummer (3):
  1426 SOLBERG
  5245 FANA
  9653 HELLEFJORD
Fjernet (0):
Endret poststed (0):

Slik oppdaterer du listen:
  1. Kjør main() i backend/src/test/kotlin/no/nav/ekspertbistand/executables/PoststedFetcher.kt
  2. Se over endringene i backend/src/main/resources/poststeder.json
  3. Commit filen
```

Listene i meldingen kappes til de 20 første per kategori, med «… og N til», så
en stor endring ikke gir en uleselig testrapport.

Testen feiler bare i to tilfeller (Q1). Alle andre feil ved henting gir en
`log.error` (via `logger()` fra `infrastruktur`), og testen passerer:

| Utfall ved henting fra Bring | Resultat |
|------------------------------|----------|
| `200` og listene er like | Passerer |
| `200` og listene er ulike | **Feiler** med avviksmeldingen over |
| `404` | **Feiler** med beskjed om å hente ny lenke fra https://www.bring.no/tjenester/adressetjenester/postnummer og oppdatere `URL` i `PoststedFetcher` |
| Tidsavbrudd, tilkoblingsfeil, TLS-feil, DNS-feil | `log.error`, passerer |
| Annen HTTP-status enn `200` og `404` (for eksempel `5xx`) | `log.error`, passerer |

`defaultHttpClient()` prøver allerede på nytt tre ganger ved `5xx` og
tidsavbrudd, så en feil som havner i loggen er ikke et enkeltstående
nettverkshikk. Testen setter `expectSuccess = false` og leser statuskoden selv,
slik at `404` skilles fra andre feil.

### 3. Enhetstest av parsing

En liten test av `PoststedFetcher.parse` med et fast utdrag i Bring-formatet
(inkludert æøå, tom linje på slutten og `\r\n`). Den kjører uten nettverk og
sikrer at parsingen er riktig uavhengig av Bring.

### 4. Oppdater `poststeder.json`

Første kjøring av testen feiler, fordi listen allerede er utdatert. Vi kjører
fetcheren og committer den oppdaterte filen i samme leveranse, slik at bygget er
grønt når testen merges.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/test/kotlin/no/nav/ekspertbistand/executables/PoststedFetcher.kt` | Trekk ut `parse` og `hentFraBring`, robust `resourceDir` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/ereg/PoststederTest.kt` | Ny: sammenligning mot Bring + enhetstest av parsing |
| `backend/src/main/resources/poststeder.json` | Oppdatert fra Bring (3 nye postnummer per 2026-09-28) |

Ingen produksjonskode endres utover dataene i `poststeder.json`.

## Kanttilfeller og feilmodus

- **Testen går mot internett.** Den kjører i `mvn -B package` i
  `cicd-backend.yaml`, altså på hvert bygg. Når Bring publiserer endringer,
  feiler bygget for alle til noen oppdaterer listen. Det er intensjonen i
  kortet, men det blokkerer også deploy av urelaterte endringer. Er Bring nede
  eller treg, gir testen bare `log.error` og bygget går videre (Q1).
- **Bring bytter nedlastings-URL.** URL-en inneholder en innholds-hash. Hvis
  Bring publiserer en ny fil under ny URL, kan den gamle enten gi 404 eller
  fortsette å levere den gamle filen. 404 får testen til å feile (se tabellen
  over). Hvis den gamle URL-en fortsetter å svare, oppdager testen ikke
  endringer. Det kan vi ikke løse uten å skrape Bring-siden, og det holder vi
  utenfor scope.
- **Nettverksfeil blir liggende usett.** En `log.error` i en grønn test blir
  lett oversett i CI-loggen. Varer feilen over tid, oppdager testen ikke lenger
  endringer. Det er en bevisst avveining (Q1): bygget skal ikke stoppe fordi
  Bring er utilgjengelig.
- **Tegnkoding.** Filen er ISO-8859-1. `parse` får allerede dekodet tekst, og
  enhetstesten dekker æøå.
- **Tom eller avkortet respons.** En `200` med få linjer viser seg i testen som
  mange fjernede postnummer, og testen feiler som «utdatert». Det holder oss
  innenfor de to feiltilfellene i Q1, og meldingen gjør feilen tydelig.
  Fetcheren nekter å skrive filen hvis responsen har under 4 000 postnummer, så
  en utvikler ikke committer en avkortet liste ved et uhell.
- **Sortering.** `poststeder.json` skrives i samme rekkefølge som Bring-filen.
  Sammenligningen gjøres på mapper, så rekkefølge påvirker ikke resultatet.

## Avklaringer

- **Q1 – nettverk i bygget:** ✅ Testen kjører på hvert bygg og feiler **kun**
  ved `404` eller utdatert liste. Nettverksproblemer gir bare `log.error` (Ken,
  review 2026-09-28). Andre HTTP-statuser enn `200` og `404` regnes som
  nettverksproblemer.

## Ferdig når

- [ ] `PoststederTest` sammenligner `poststeder.json` med Bring og feiler ved
      avvik med oversikt over nye, fjernede og endrede postnummer, pluss
      instruksjon for oppdatering
- [ ] Parsing er delt mellom fetcher og test, og dekket av en enhetstest uten
      nettverk
- [ ] `PoststedFetcher` skriver til riktig fil fra både repo-roten og `backend/`
- [ ] `poststeder.json` er oppdatert, og `mvn -B package` er grønn
- [ ] Testen feiler ved `404` med beskjed om å oppdatere `URL`
- [ ] Nettverksfeil og andre HTTP-statuser gir `log.error`, og testen passerer
