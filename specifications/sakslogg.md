# Plan: Sakslogg – event, handler, API og «Logg»-dropdown

## Problem
Tabellen `sakslogg` (V12, spesifisert i `specifications/sak-datamodell.md`) finnes, men ingenting skriver til
eller leser fra den. Vi bygger infrastrukturen for å vise saksloggen til saksbehandlere:

- eventet `SakOppdatert`
- en handler som skriver eventet til `sakslogg`
- et API som leser loggen for en sak
- en «Logg»-dropdown øverst til høyre på saksiden

**Ingen kode publiserer `SakOppdatert` ennå.** Det kommer når saksbehandlingshandlingene (tildeling,
vurdering, vedtak, refusjon) bygges.

Trello-kort: ikke koblet, fordi Trello CLI mangler auth. Lenken legges til her før commit.

## Avklarte beslutninger
| Tema | Valg |
|------|------|
| Visningstekst | Teksten som vises i loggen («Sak tildelt», «Vedtak fattet: Søknad innvilget (22 000 kr)») lagres i eksisterende `notat`-kolonne. |
| Aktør | `utfort_av_type` fjernes. `utfort_av_rolle` er påkrevd og er `SAKSBEHANDLER`, `BESLUTTER` eller `SYSTEM`. `utfort_av_ident` er satt for saksbehandler og beslutter, og null for system. |
| Migrering | Ny Flyway-migrering fjerner `utfort_av_type` og gjør `utfort_av_rolle` NOT NULL. Tabellen er tom, så endringen er trygg. |
| Navn på aktør | Backend slår opp navn med `EntraProxyClient.hentAnsatt(ident)`. Hvis oppslaget feiler, vises identen. `SYSTEM` vises som «System». |
| `aggregateRootId` | `soknadId`, som for de andre eventene. Søknaden er fortsatt eneste aggregatrot. |
| Utenfor scope | Publisering av `SakOppdatert`, tilgangskontroll per sak og habilitetssjekk mot `sakslogg` |

## Tilnærming

### 1. Migrering `V17__sakslogg_fjern_utfort_av_type.sql`
```sql
ALTER TABLE sakslogg DROP COLUMN utfort_av_type;
ALTER TABLE sakslogg ALTER COLUMN utfort_av_rolle SET NOT NULL;
```
`DROP COLUMN` er en ren katalogendring. `SET NOT NULL` skanner tabellen, men den er tom fordi ingenting
skriver til den ennå. Indeksene på `sakslogg` berøres ikke.

### 2. Domenemodell – `sak/Db.kt`
- `object SaksloggTable : Table("sakslogg")`:
  `saksloggId` (db-generert), `sakId`, `utfortAvRolle`, `utfortAvIdent` (nullable),
  `notat` (nullable), `utfortAt`.
- `enum class AktorRolle { SAKSBEHANDLER, BESLUTTER, SYSTEM }`, lagret som TEXT.

### 3. Event – `EventData.SakOppdatert` (`event/Events.kt`)
```kotlin
@Serializable
@SerialName("sakOppdatert")
data class SakOppdatert(
    val sakId: String,
    val soknadId: String,
    val utfortAvRolle: AktorRolle,
    val utfortAvIdent: String?,
    val notat: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```
- KDoc i samme stil som de andre eventene, med `SkrivSakslogg` som konsument.
- `init` validerer aktøren:
  - `SAKSBEHANDLER` og `BESLUTTER` krever `utfortAvIdent`
  - `SYSTEM` krever at `utfortAvIdent` er null
- `notat` kan ikke være blank.
- Merk: `utfortAvIdent` er en NAV-ident. Eventet skal ikke logges utenfor teamLog.

### 4. Handler – `event/handlers/SkrivSakslogg.kt`
`class SkrivSakslogg(database: Database) : EventHandler<EventData.SakOppdatert>`, id `"Skriv sakslogg"`.

| Situasjon | Resultat |
|-----------|----------|
| Eventet er allerede behandlet (`IdempotencyGuard.isGuarded`) | `success()` uten ny rad |
| Saken finnes | Én rad i `sakslogg` (`utfort_at = tidspunkt`) og guard-rad, i samme transaksjon → `success()` |
| Saken finnes ikke (FK-brudd) | `unrecoverableError` |
| Annen databasefeil | `rollback()` → `transientError` |

- Insert og guard-rad skrives i samme `transaction(database)`, slik at en retry aldri gir dobbel loggpost.
- Registreres i `configureEventHandlers` (`Events.kt`).

### 5. API – `GET /api/saksbehandling/v1/saker/{sakId}/logg`
I `SaksbehandlerApi.kt`, bak `AZURE_AD_PROVIDER`.

- `sakId` som ikke er en UUID gir 400.
- Leser `sakslogg` for saken, sortert på `utfort_at` synkende. En sak uten loggposter, eller en sak som
  ikke finnes, gir tom liste.
- Slår opp navn for hver unike ident parallelt med `EntraProxyClient.hentAnsatt`. Bruker `visningNavn`,
  ellers fornavn + etternavn. Feiler oppslaget, logges en advarsel (uten ident), og identen brukes som navn.
- Svar:

```kotlin
@Serializable
data class SaksloggResponse(val innslag: List<SaksloggInnslag>)

@Serializable
data class SaksloggInnslag(
    val id: String,
    val tidspunkt: Instant,
    val utfortAvRolle: AktorRolle,
    val utfortAvIdent: String?,
    val utfortAvNavn: String,   // «System» for SYSTEM
    val notat: String?,
)
```

### 6. Frontend – `frontend/saksbehandling`
- `SAKSBEHANDLING_SAKSLOGG_URL(sakId)` i `utils/constants.ts`.
- `hooks/useSakslogg.ts`: SWR, samme mønster som `useSak`. Henter først når dropdownen åpnes
  (`null`-nøkkel ellers).
- `components/Sakslogg.tsx`:
  - Knapp «Logg» med historikk-ikon og chevron som peker opp når loggen er åpen. Komponent
    (`Popover` eller `ActionMenu`) og ikonnavn bekreftes mot Aksel-dokumentasjonen.
  - Vertikal tidslinje med prikk per innslag. Hvert innslag viser:
    - tidspunkt («17. jun. 2026, 09.01»)
    - navn (uthevet)
    - `Tag` for rolle: Saksbehandler, Beslutter eller System, med ulik farge per rolle
    - notat
  - Laster: `Loader`. Feil: «Kunne ikke hente saksloggen.» Tom logg: «Ingen hendelser ennå.»
  - Bare Aksel-komponenter, layout-primitiver og tokens, ingen hardkodede farger eller px.
  - Tilgjengelighet: knappen har `aria-expanded`, og tidslinjen er en ordnet liste (`<ol>`).
- `SakPage.tsx`: Linja med «Tilbake til liste av saker» blir en `HStack justify="space-between"` med
  `<Sakslogg sakId={sakId} />` til høyre.
- `mocks/handlers.ts`: mock for `/saker/:sakId/logg` med innslagene fra skissen.

### 7. Tester
- `SkrivSaksloggTest` (mønster fra `SettAvlystSoknadStatusTest`):
  - `SAKSBEHANDLER`-event gir rad med rolle, ident, notat og `utfort_at = tidspunkt`
  - `SYSTEM`-event gir rad med rolle `SYSTEM` og uten ident
  - Samme event to ganger gir én rad
  - Ukjent `sakId` gir `unrecoverableError`
- `SakOppdatert`: `init` avviser `SAKSBEHANDLER`/`BESLUTTER` uten ident og `SYSTEM` med ident.
- API-test for `/saker/{sakId}/logg`: sortering, navneoppslag, fallback til ident, «System» for
  `SYSTEM` og 400 ved ugyldig id.
- Frontend: `typecheck` og `lint` for `saksbehandling`.

### 8. Dokumentasjon
- Kjør `executables/EventFlowDiagram.kt` for å oppdatere `event/event-flow.md`.
- `sak-datamodell.md` er oppdatert: `utfort_av_type` og `AktorType` er fjernet, `utfort_av_rolle` er
  NOT NULL med `SYSTEM`, og habilitetssjekken matcher på rolle og ident.

## Merknader
- `notat` brukes både til visningstekst og eventuell fritekst. Skal vi senere filtrere på hendelsestype
  (for eksempel i habilitetssjekken), trenger vi en egen kolonne og en migrering.
- `sak-datamodell.md` krever at alle saksbehandlerhandlinger logges, fordi habilitetssjekken bygger på
  `sakslogg`. Det blir ansvaret til koden som publiserer `SakOppdatert`.
- `sakId` i saksbehandlingsfrontenden er i dag en mock-id (`sak-1001`). Det ekte API-et forventer UUID,
  så dropdownen viser ekte data først når `/saker/{sakId}` er koblet mot `sak`-tabellen.
- Navneoppslaget gir ett kall til EntraProxy per unike ident når loggen åpnes. Det er akseptabelt for
  noen få aktører per sak, men kan trenge caching senere.
