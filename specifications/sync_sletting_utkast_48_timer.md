# Synk sletting av utkast med frontend-tekst (48 timer)

Trello: https://trello.com/c/TzQE05Ex/579-oppdater-misvisende-info-om-sletting-av-utkast
Kort-ID: `6aa0e22aacb8653e9235344a`

## Mål

Backend skal slette gamle utkast etter **48 timer**, slik at faktisk oppførsel
stemmer med teksten brukeren ser i frontend.

## Bakgrunn

Frontend forteller brukeren at utkast slettes etter 48 timer, tre steder:

- `frontend/client/src/components/SaveDraftModal.tsx:22` — «Hvis du ikke
  fortsetter innen 48 timer, blir utkastet slettet.»
- `frontend/client/src/pages/SoknadPage.tsx:190` — samme tekst.
- `frontend/client/src/pages/SoknaderPage.tsx:108` — «Utkast lagres i 48 timer
  innen vi sletter dem.»

Backend er derimot konfigurert til å slette utkast som er eldre enn **30 dager**
(`slettGamleUtkast(ttl: Duration = 30.days)` i
`backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt`). Utkast lever
altså langt lenger enn teksten lover — misvisende informasjon (kortet er merket
`Bug`).

## Beslutning

Fra kortkommentar (Ken Gullaksen, 2026-09-10): «ble enige om å oppdatere backend
til å gjøre som beskrivelsen sier.» Retningen er dermed avklart: **backend
tilpasses frontend-teksten** (48 timer), ikke omvendt. Frontend-tekstene endres
ikke.

## Endring

Én verdi endres, i
`backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt`:

```kotlin
fun slettGamleUtkast(
    ttl: Duration = 48.hours,   // var: 30.days
    clock: Clock = Clock.System,
) = transaction {
    UtkastTable.deleteWhere {
        UtkastTable.opprettetTidspunkt lessEq (clock.now() - ttl)
    }.let {
        log.info("Slettet $it gamle utkast eldre enn $ttl")
    }
}
```

Importen `kotlin.time.Duration.Companion.days` byttes/utvides til
`kotlin.time.Duration.Companion.hours` (linje 25 i samme fil). `days` beholdes
kun hvis den fortsatt brukes ellers i filen — ellers fjernes den.

Sletterutinen kjøres allerede hvert 10. minutt fra
`soknad/Routing.kt` (`slettGamleUtkast()` i løkke med `delay(10.minutes)`), så
ingen endring i schedulering trengs. TTL-en måles mot `opprettetTidspunkt`.

## Filer som berøres

- `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt` — `30.days` →
  `48.hours` + import.

Ingen frontend-endring. Ingen databasemigrasjon (TTL beregnes i kode, ikke i
skjema).

## Kanttilfeller og vurderinger

- **Data-tap ved deploy:** Utkast mellom 48 timer og 30 dager gamle blir slettet
  ved første kjøring etter deploy. Dette er tilsiktet — de skulle allerede vært
  borte ifølge frontend-teksten. Verdt å nevne for teamet før produksjonssetting.
- **Innsendte søknader** (`slettGamleInnsendteSoknader`, `slettSøknadOm = 455.days`)
  er urørt — gjelder kun utkast.
- **`opprettetTidspunkt` vs. sist oppdatert:** TTL måles fra opprettelse, ikke
  fra siste redigering. Frontend-teksten («hvis du ikke fortsetter innen 48
  timer») antyder inaktivitet fra siste handling. Dagens felt er
  `opprettetTidspunkt`. **Åpent spørsmål:** skal 48-timersfristen regnes fra
  opprettelse (som nå) eller fra siste oppdatering? Kortet spesifiserer ikke
  dette. Standardvalg her: behold `opprettetTidspunkt` (minste endring, matcher
  eksisterende felt). Bekreft med bruker.

## Ferdig når

- `slettGamleUtkast` bruker 48 timers TTL.
- Backend kompilerer (`./gradlew build` / eksisterende testoppsett).
- Faktisk slettevindu (48 timer) stemmer med frontend-tekst.
