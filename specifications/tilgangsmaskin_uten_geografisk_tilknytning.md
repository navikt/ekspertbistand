# Tilgangsmaskinen skal ignorere geografisk tilknytning

Trello: https://trello.com/c/6RJ8pn4X
Kort-ID: `6ac389c7ffd5856e513ad99b`

## Mål

Saksbehandlere skal få se en sak selv om deltakeren (den ansatte) har geografisk
tilknytning utenfor enhetene saksbehandleren har tilgang til. Vi bytter
detaljoppslaget fra komplett regelsett til kjerneregelsett i Tilgangsmaskinen.

## Bakgrunn

`GET /api/saksbehandling/v1/saker/{sakId}` sjekker tilgang i to steg
(`backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt`):

1. Saksbehandleren må ha tilgang til sakens `behandlendeEnhet` (fra Entra-proxy).
2. Tilgangsmaskinen evaluerer den ansattes fnr med `Regelsett.KOMPLETT`.

Steg 1 er den geografiske tilgangsstyringen vår. I steg 2 sjekker
Tilgangsmaskinen i tillegg geografisk tilgang: saksbehandleren må ha nasjonal
tilgang, tilgang til den ansattes kommune/bydel eller tilgang til enheten den
ansatte følges opp ved. For ekspertbistand er den ansattes bosted irrelevant:
saken hører til enheten som behandler arbeidsgiverens søknad. Regelen avviser
derfor saksbehandlere som har tilgang til enheten.

## Hva Tilgangsmaskinen støtter

Kilde: Confluence-siden til team Sikkerhetstjenesten om Tilgangsmaskinen.

Tilgangsmaskinen tilbyr to regelsett:

- **Kjerneregler** beskytter brukergrupper med særlig krav på beskyttelse og
  hindrer åpenbare brudd på habilitet: kode 6, §19, kode 7, egen ansatt
  (skjerming), egne data, egen familie og verge. De kan ikke overstyres.
- **Komplett regelsett** er kjerneregler pluss overstyrbare regler, som
  begrenser hvilke «vanlige» brukere en ansatt har tilgang til. Geografisk
  tilgangskontroll og tilgang til avdøde brukere er overstyrbare.

Det finnes ikke et regelsett som utelater bare den geografiske regelen.

Siden anbefaler kjerneregler for blant annet «relaterte parter» og
flerpartssaker, fordi komplett regelsett på andre enn hovedparten «kan gi
uventede resultater som at man avviser tilgang til en bruker fordi han/hun har
familie bosatt i en annen landsdel». I ekspertbistand er arbeidsgiveren søker.
Den ansatte er en relatert part, og geografien vår følger behandlende enhet.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Geografisk tilknytning for deltaker/ansatt skal ikke inngå i tilgangssjekken. Tilgang styres geografisk via enheten saksbehandleren har tilgang til. |
| Ken, 2026-10-05 | Bruk `KJERNE`. |

### Forkastet

- **`KOMPLETT` og behandle `AVVIST_GEOGRAFISK` som innvilget.** Vi vet ikke om
  de andre overstyrbare reglene er sjekket når svaret er geografisk avvisning.
- **Overstyring/enkelttilgang per sak.** Manuell, tidsbegrenset (standard tre
  dager) og ment for unntak.
- **Gi saksbehandlerne `0000-GA-GEO_NASJONAL`.** Gir nasjonal tilgang i alle
  systemer som bruker Tilgangsmaskinen, ikke bare ekspertbistand.

## Tilnærming

### 1. `TilgangsmaskinClient`

Klienten beholder `Regelsett` med både `KJERNE` og `KOMPLETT`, slik at den kan
kopieres til andre apper. `regelsett` på `evaluer` og `evaluerBulk` blir et
påkrevd parameter uten default. Et default-argument kan snike inn feil
regelsett, så kalleren må velge eksplisitt.

Den utdaterte linjen «Klienten er foreløpig ikke koblet inn i noen rute.» i KDoc
fjernes.

### 2. `SakApi`

Detaljoppslaget kaller `tilgangsmaskinClient.evaluer(...)` med
`Regelsett.KJERNE` i stedet for `Regelsett.KOMPLETT`. Kallet går da mot
`POST /api/v1/kjerne`. Resten av flyten er uendret: innvilget gir sporingslogg
til ArcSight, avvist gir 403, feil gir 503.

KDoc-en øverst i ruten oppdateres: den sier at Tilgangsmaskinen sjekker
kjerneregler, og hvorfor (geografi styres via behandlende enhet).

### 3. Tester

`SaksbehandlingSakApiTest`:

- Mocken for Tilgangsmaskinen lytter på `POST /api/v1/kjerne` i stedet for
  `/api/v1/komplett`. Kaller koden feil endepunkt, får den 404 fra mocken og
  testene for innvilget og avvist feiler.
- Eksisterende tester for innvilget, avvist (`AVVIST_STRENGT_FORTROLIG_ADRESSE`)
  og 503 skal bestå uendret ellers.

`TilgangsmaskinClientTest`: bulk-testen kaller `evaluerBulk` uten regelsett og
må sende `Regelsett.KJERNE` eksplisitt. Enkeltoppslag-testene sender allerede
regelsett.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/kotlin/no/nav/ekspertbistand/tilgangsmaskin/TilgangsmaskinClient.kt` | `regelsett` påkrevd uten default, fjerner utdatert KDoc-linje |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `Regelsett.KOMPLETT` → `Regelsett.KJERNE`, oppdatert KDoc |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SaksbehandlingSakApiTest.kt` | Mock på `/api/v1/kjerne` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/tilgangsmaskin/TilgangsmaskinClientTest.kt` | Bulk-testen sender `Regelsett.KJERNE` eksplisitt |

## Kanttilfeller og feilmodus

- **Ansatt som har vært død i mer enn 12 måneder.** Avvises ikke, fordi regelen
  er overstyrbar. Saker gjelder ansatte i et arbeidsforhold, så vi regner det
  som uaktuelt.
- **Endringer i kjernereglene.** Nye eller endrede kjerneregler gjelder
  automatisk. Ingen endring hos oss.
- **Saksliste.** `GET /saker` kaller ikke Tilgangsmaskinen og påvirkes ikke.

## Ferdig når

- [ ] Detaljoppslaget kaller `POST /api/v1/kjerne`
- [ ] En saksbehandler med tilgang til behandlende enhet ser saken selv om den
      ansatte bor utenfor saksbehandlerens geografi
- [ ] Kjerneavvisninger (f.eks. kode 6) gir fortsatt 403 uten sporingslogg
- [ ] `regelsett` er påkrevd på `evaluer` og `evaluerBulk`, uten default
- [ ] KDoc i `SakApi` forklarer valget av kjerneregelsett
- [ ] `mvn -B package` er grønn
