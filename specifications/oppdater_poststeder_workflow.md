# Oppdater poststeder via månedlig workflow

Trello: https://trello.com/c/dDVUf4qg
Kort-ID: `6ab61f0fc2d5b6bdd5e282e1`

## Mål

Holde `backend/src/main/resources/poststeder.json` oppdatert mot Bring uten
manuelt arbeid, og uten at bygget avhenger av at Bring er tilgjengelig. En
GitHub Actions-workflow kjører månedlig, laster ned registeret og oppretter en PR
hvis filen har endret seg.

## Bakgrunn

Ereg returnerer bare poststed for utenlandske adresser. `EregService` fyller
derfor inn poststed fra et lokalt register når det mangler
(`orFromMapping(postnummer)` i
`backend/src/main/kotlin/no/nav/ekspertbistand/ereg/EregService.kt`). Registeret
ble generert én gang og lå etter det urørt til noen oppdaget avvik.

Første forsøk i denne leveransen var en unit-test som sammenlignet
`poststeder.json` med Bring på hvert bygg. Team mulighetsrommet har en bedre
løsning: en månedlig CI-jobb som laster ned registeret og lager en PR ved
endringer (navikt/mulighetsrommet#8264). Vi tar i bruk samme løsning og fjerner
unit-testen.

Testen hadde to svakheter workflowen unngår:

- Den gikk mot bring.no på hvert bygg. Var Bring nede eller endret, slo det inn
  på urelaterte bygg.
- Den kunne fortelle at listen var utdatert, men ikke oppdatere den. Noen måtte
  kjøre en fetcher manuelt.

Workflowen skiller oppdatering fra bygg: bygget rører aldri Bring, og
oppdateringen kommer som en vanlig PR som kan reviewes og merges.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Ken, 2026-09-29 | Erstatt unit-testen med samme løsning som mulighetsrommet: en cron-jobb på GitHub Actions. |
| navikt/mulighetsrommet#8264 | Shell-script laster ned og konverterer registeret; workflow oppretter PR ved endringer. |

## Tilnærming

### 1. Shell-script

`backend/scripts/update-poststeder.sh` laster ned registeret fra Bring, dekoder
fra Windows-1252 til UTF-8, plukker ut postnummer og poststed, og skriver
`postnummer → poststed` som JSON til `src/main/resources/poststeder.json`.

Scriptet er kilden til filens format: `jq --sort-keys` gir sorterte nøkler og
2-mellomrom innrykk. Det er selvstendig (curl, iconv, awk, jq), så CI ikke
trenger å bygge backend for å oppdatere én fil.

Vi bruker den korte, stabile lenken
`https://www.bring.no/postnummerregister-ansi.txt` framfor den hashede
nedlastings-URL-en, slik at scriptet ikke må endres når Bring publiserer en ny
fil.

### 2. Workflow

`.github/workflows/update-poststeder.yaml`:

- `schedule: "0 12 1 * *"` (den 1. i måneden) og `workflow_dispatch` for manuell
  kjøring.
- Kjører scriptet, og hvis `poststeder.json` er endret: lager branchen
  `update-poststeder`, committer, force-pusher og åpner en PR mot `main`.
  Finnes PR-en allerede, gjenbrukes den.
- Toppnivå `permissions: contents: read`; jobben hever selv til
  `contents: write` og `pull-requests: write`. `timeout-minutes: 10`.
  `actions/checkout` pinnes til SHA.

PR-en går gjennom vanlig review og backend-CI før merge, så en endring i
registeret får samme kvalitetssikring som annen kode.

### 3. Rydd bort unit-test-varianten

`PoststederTest.kt` og den Kotlin-baserte `PoststedFetcher.kt` fjernes.
Workflowen dekker behovet, og å ha to mekanismer som gjør det samme inviterer til
at de gror fra hverandre.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/scripts/update-poststeder.sh` | Ny: last ned fra Bring, skriv `poststeder.json` |
| `.github/workflows/update-poststeder.yaml` | Ny: månedlig kjøring + PR ved endringer |
| `backend/src/main/resources/poststeder.json` | Regenerert i scriptets format (sortert, 2-space); samme innhold + tre nye postnummer |
| `backend/src/test/kotlin/no/nav/ekspertbistand/ereg/PoststederTest.kt` | Fjernet |
| `backend/src/test/kotlin/no/nav/ekspertbistand/executables/PoststedFetcher.kt` | Fjernet |

## Kanttilfeller og feilmodus

- **Bring nede eller treg ved kjøring.** Scriptet feiler (`curl -f`), workflowen
  blir rød og ingen PR lages. Neste månedlige kjøring, eller en manuell
  `workflow_dispatch`, prøver på nytt. Bygg og deploy påvirkes ikke.
- **Bring bytter URL/format.** `curl -f` feiler ved 404. Endrer kolonnerekkefølgen
  seg, gir `awk NF >= 2` fortsatt to felter, men mulig feil innhold. Endringen
  fanges i PR-review før merge.
- **Ingen endringer.** `git status --porcelain` er tom, workflowen avslutter uten
  PR.
- **PR ligger allerede åpen.** Branchen force-pushes og eksisterende PR
  gjenbrukes, så det hoper seg ikke opp PR-er.
- **Format-drift.** Fordi den committede filen er generert av scriptet, gir
  første kjøring ingen støy fra ren omformatering. Endrer noen filen for hånd i et
  annet format, vil neste kjøring foreslå å normalisere den tilbake.
- **Tegnkoding.** Registeret er Windows-1252; `iconv` konverterer til UTF-8.
  Poststeder med æøå (f.eks. `SØGNE`, `BÅTSFJORD`) er verifisert i filen.

## Ferdig når

- [ ] `backend/scripts/update-poststeder.sh` laster ned fra Bring og skriver
      `poststeder.json` i scriptets format
- [ ] `.github/workflows/update-poststeder.yaml` kjører månedlig og ved
      `workflow_dispatch`, og oppretter/oppdaterer en PR kun ved endringer
- [ ] Workflowen har eksplisitte, minimale `permissions`, `timeout-minutes` og
      SHA-pinnet `actions/checkout`
- [ ] `poststeder.json` er regenerert av scriptet, med samme innhold
- [ ] `PoststederTest.kt` og `PoststedFetcher.kt` er fjernet
- [ ] `mvn -B package` er grønn (backend laster registeret som før)
