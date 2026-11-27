# Notat i saken

Trello: https://trello.com/c/XhuWiTdx/736-saksbehandler-m%C3%A5-kunne-skrive-notat-i-saken
Kort-ID: `6b056e4c20e2ccf73267b8ab`

## Mål

Saksbehandler og beslutter skal kunne skrive interne arbeidsnotater på en sak i
saksdetaljen:

- «Notat» i linjen med «Tilbake til liste av saker» åpner et panel til høyre
  med alle notatene på saken, nyeste først. Knappen viser antallet notater i en
  blå sirkel.
- Hvert notat viser hvem som skrev det og når.
- Den som skrev et notat, kan endre og slette det. Ingen andre kan det.
- Alle med tilgang til saken ser notatene.
- Saksloggen får et innslag når et notat opprettes, endres eller slettes, men
  ikke selve teksten.
- «Logg» åpner et panel på samme måte i stedet for dagens popover.
- Arena-saker har ikke notater.

Notatene journalføres ikke i Joark. De ligger hos oss, fordi løsningen er
klassifisert som arkiv.

## Bakgrunn

Fra før:

- `SakPage.tsx` har `Tildeling` (tekst og «Meny») og `Sakslogg` («Logg») til
  høyre i linjen med tilbakelenken. Begge bruker `HeaderKnapp`, som har chevron
  og `aria-expanded`.
- `Sakslogg.tsx` viser loggen i en `Popover` under «Logg». Innholdet er en
  Aksel `Process` med ett `Process.Event` per innslag
  (`specifications/sakslogg.md`).
- `sakslogg.notat` er teksten til et logginnslag, for eksempel «Sak tildelt».
  Det er ikke et notat skrevet av en saksbehandler, og har ingenting med dette
  kortet å gjøre utover navnet.
- Saksbehandlingshandlinger følger samme mønster: API-et sjekker tilgang og
  publiserer en event på `EventQueue`, svarer 202, og en handler skriver
  tabellen API-et leser fra (`tildel_meg_sak.md`, `FrigjoerSak`).
- `GET …/saker/{sakId}/logg` sjekker rolle (`SAKSBEHANDLER` eller
  `BESLUTTER`), enhet og Tilgangsmaskinen. Notatene bruker de samme sjekkene.
- `useTildeling` oppdaterer SWR-cachen optimistisk og revaliderer etter
  500 ms, fordi handleren ikke har kjørt når API-et svarer 202.
- Siste migrering er `V42__sluttrapport_vurdering.sql`. Vår blir V43.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-11-27.

| Node | Lenke | Viser |
|------|-------|-------|
| `1281:33439` | [lukket](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1281-33439) | Saksdetaljen med begge paneler lukket. Til høyre i linjen med tilbakelenken: «Saksbehandler {navn}», «Meny» med chevron, «Logg» med klokkeikon og «Notat» med notatikon og en blå sirkel med tallet 2. «Logg» og «Notat» har ingen chevron. |
| `1281:31840` | [notat åpent](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1281-31840) | «Notat» er aktiv (understreket, lys bakgrunn). Et panel til høyre i full høyde under linjen skyver saksinnholdet til siden. Panelet har overskriften «Notater» med notatikon, en lilla boks «Internt arbeidsnotat» med øyeikon og teksten «Vises ikke til arbeidsgiver eller deltaker med mindre de ber om innsyn.», en blå knapp «Skriv nytt notat» med plussikon i full bredde, og to notater. Hvert notat har en rund sirkel med forbokstaven i navnet, navnet i fet skrift, tidspunktet («24. nov. 2026 kl. 10:42») og teksten. Det øverste notatet, skrevet av innlogget, har de små tertiærknappene «Endre» og «Slett» (rød). Det andre er skrevet av beslutteren på saken og har ingen knapper. |
| `1281:32536` | [logg åpen](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1281-32536) | «Logg» er aktiv. Samme panel med overskriften «Logg» med klokkeikon og saksloggen som i dag: tidslinje med tidspunkt, navn, rolle-tag og tekst. |

Skissene har ingen tilstand for å skrive eller endre et notat, ingen tom
tilstand og ingen feil. Ken godtok `Textarea` med «Lagre» og «Avbryt» (se
*Beslutninger*). Tom tilstand og feil følger mønstrene i repoet.

Avvik mellom kort og skisse:

- **«Meny».** Skissen viser «Meny» med et hamburgerikon. Kortet sier «Endre ikke
  hvordan Meny åpnes eller vises». Vi følger kortet, og `Tildeling` er uendret.
- **Hvem som skriver.** Kortet sier «Saksbehandler må kunne skrive notat».
  Skissen viser et notat fra beslutteren på saken. Begge rollene skal kunne
  skrive (Ken, 2026-11-27).

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Notatet journalføres ikke i Joark. Det ligger i vårt system, fordi vi er klassifisert som arkiv. |
| Kortbeskrivelsen | Notatene er synlige for alle med tilgang til saken i saksdetaljen. |
| Kortbeskrivelsen | Vi viser hvem som skrev notatet og tidspunktet. |
| Kortbeskrivelsen | Antallet notater vises i den blå sirkelen på «Notat». |
| Kortbeskrivelsen | Innholdet i fanene «Søknad», «Sluttrapport» og «Refusjon» endres ikke. Bare knappene i linjen med tilbakelenken og visningen av logg og notat endres. |
| Kortbeskrivelsen | «Meny» åpnes og vises som i dag. |
| Kortbeskrivelsen; Figma `1281:31840`, `1281:32536` | «Logg» og «Notat» åpner et panel som skyver innholdet til siden. |
| Kortbeskrivelsen | Man kan endre og slette notater man har skrevet selv. «Slett» sletter notatet. «Endre» åpner notatet i redigeringsmodus. |

| Ken, 2026-11-27 | Både saksbehandler og beslutter kan skrive notater. Eneste krav er tilgang til saken. Man trenger ikke være tildelt saken. |
| Ken, 2026-11-27 | Arena-saker får ikke notater, fordi de saksbehandles ferdig i Arena. Andre saker kan få notater uansett status. |
| Ken, 2026-11-27 | Slett fjerner raden fra `saksnotat`. At teksten finnes i `event_log`, er nok. |
| Ken, 2026-11-27 | Saksloggen får «Notat opprettet», «Notat endret» og «Notat slettet». Selve teksten skrives ikke til saksloggen. |
| Ken, 2026-11-27 | Nytt notat og redigering bruker `Textarea` med «Lagre» og «Avbryt» i panelet. Ved redigering er feltet fylt ut med teksten som var. |
| Ken, 2026-11-27 | «Slett» sletter uten bekreftelse. |
| Ken, 2026-11-27 | Et endret notat viser «· endret {tidspunkt}». |
| Ken, 2026-11-27 | Maks lengde er 1000 tegn. |

Kortet har ingen kommentarer.

Valg i specen som følger mønstre i repoet:

| Valg | Begrunnelse |
|------|-------------|
| API-et publiserer events, og handlere skriver tabellen `saksnotat`. API-et leser bare `saksnotat`. | Samme mønster som tildeling. `event_log` er masterdata for alt som skjer, og API-er slår ikke opp i `event_log`. |
| Teksten i notatet ligger i eventen. | `event_log` er masterdata, og fritekst med personopplysninger kan ligge der. Teksten skal aldri i applogger. |
| API-et lager `notatId` (UUID) og sender den i eventen og i svaret. | Handleren blir idempotent på `notatId`, og frontend kan vise notatet med en gang. |
| Navnet og rollen til den som skriver, endrer eller sletter, følger med i eventen. Navnet kommer fra `principal.navn`. | Handlerne trenger ikke lese `saksnotat` for å skrive saksloggen. Navnet kommer fra entra-proxy, aldri fra `name`-claimet, som for tildeling. |
| Teksten trimmes og kan ikke være tom. | Hindrer tomme notater. |

## Avgrensning

Ikke med:

- Journalføring av notater i Joark.
- Notater i saksoversikten.
- Innsyn for arbeidsgiver eller deltaker. Teksten «med mindre de ber om innsyn»
  handler om en manuell prosess utenfor løsningen.
- Historikk over endringer i UI. Tidligere versjoner ligger i `event_log`.
- Notater på Arena-saker.
- Endringer i «Meny», i fanene og i hvordan saksloggen viser et innslag.
- Notater i `LokaleSaker`. De lages i UI når vi tester lokalt.

## Tilnærming

```
POST/PUT/DELETE …/notater ── publishEventQueue(Saksnotat*) ──> event_queue
                                                                  │
OpprettSaksnotat / EndreSaksnotat / SlettSaksnotat ── INSERT/UPDATE/DELETE saksnotat
                                                   + publishEventQueue(SakOppdatert)
                                                                  │
SkrivSakslogg ── INSERT sakslogg («Notat opprettet» / «Notat endret» / «Notat slettet»)

GET …/notater ── SELECT saksnotat
```

### 0. Migrering

`V43__saksnotat.sql`:

```sql
CREATE TABLE IF NOT EXISTS saksnotat (
    notat_id          UUID PRIMARY KEY,
    sak_id            UUID        NOT NULL REFERENCES sak (sak_id) ON DELETE CASCADE,
    tekst             TEXT        NOT NULL,
    skrevet_av_ident  TEXT        NOT NULL,
    skrevet_av_navn   TEXT        NOT NULL,
    skrevet_av_rolle  TEXT        NOT NULL,
    opprettet_at      TIMESTAMPTZ NOT NULL,
    endret_at         TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_saksnotat_sak_id ON saksnotat (sak_id, opprettet_at DESC);
```

`skrevet_av_rolle` er `SAKSBEHANDLER` eller `BESLUTTER` (`AktorRolle` uten
`SYSTEM`). Den vises ikke i skissen, men gjør det mulig å skille rollene senere
uten å lese `event_log`.

`SaksnotatTable` legges i `saksbehandling/Db.kt`, ved siden av
`SaksloggTable`.

### 1. Events (`event/Events.kt`)

Tre nye `EventData`, alle med `aggregateRootId = soknadId`, som
`SakOppdatert`:

```kotlin
@SerialName("saksnotatOpprettet")
data class SaksnotatOpprettet(
    val notatId: String,
    val sakId: String,
    val soknadId: String,
    val tekst: String,
    val skrevetAvRolle: AktorRolle,
    val skrevetAvIdent: String,
    val skrevetAvNavn: String,
    val tidspunkt: Instant,
)

@SerialName("saksnotatEndret")
data class SaksnotatEndret(
    val notatId: String,
    val sakId: String,
    val soknadId: String,
    val tekst: String,
    val endretAvRolle: AktorRolle,
    val endretAvIdent: String,
    val tidspunkt: Instant,
)

@SerialName("saksnotatSlettet")
data class SaksnotatSlettet(
    val notatId: String,
    val sakId: String,
    val soknadId: String,
    val slettetAvRolle: AktorRolle,
    val slettetAvIdent: String,
    val tidspunkt: Instant,
)
```

`init` krever at `tekst` ikke er blank og har maks `SAKSNOTAT_MAKS_LENGDE`
(1000) tegn, at identene ikke er blanke, og at rollene ikke er `SYSTEM`. KDoc
sier at eventene har fritekst og identer og ikke skal logges.

Rollen er med på alle tre eventene, slik at handlerne kan publisere
`SakOppdatert` uten å lese `saksnotat`.

Feltene er primitiver og `AktorRolle`, ingen API-DTO-er.

### 2. Handlere (`event/handlers/`)

Én handler per event, registrert ved siden av `SkrivSakslogg`. Alle bruker
`idempotencyGuard` og logger bare `sakId` og `notatId`, aldri tekst eller
ident. Når raden faktisk endres, publiserer handleren `SakOppdatert` i samme
transaksjon, som `FrigjoerSak`. `utfortAvRolle` og `utfortAvIdent` kommer fra
eventen, og `notat` er en fast tekst, aldri teksten i notatet.

- `OpprettSaksnotat`: `INSERT … ON CONFLICT (notat_id) DO NOTHING`.
  `opprettet_at` er `tidspunkt` fra eventen. Saksloggen får «Notat opprettet».
- `EndreSaksnotat`: `UPDATE saksnotat SET tekst, endret_at WHERE notat_id = ?
  AND skrevet_av_ident = ?`. Saksloggen får «Notat endret». Treffer den ingen
  rad (slettet i mellomtiden), logges det på info, saksloggen får ikke noe
  innslag, og handleren svarer `success()`.
- `SlettSaksnotat`: `DELETE FROM saksnotat WHERE notat_id = ? AND
  skrevet_av_ident = ?`. Saksloggen får «Notat slettet». Ingen rad gir
  `success()` uten innslag.

`skrevet_av_ident` i `WHERE` er et ekstra vern. API-et har allerede sjekket
eierskapet.

🔴 Rød sone: eierskapssjekken i handlerne og i API-et (*3*). Gå gjennom denne
nøye.

### 3. API (`saksbehandling/SaksnotatApi.kt`)

Ny fil med `configureSaksnotatApiV1()`, registrert der
`configureSaksbehandlerApiV1()` registreres. Alle ruter krever
`principalMedRolle(SAKSBEHANDLER, BESLUTTER)`, slår opp
`hentSakTilgangsgrunnlag(sakId)` (404 «fant ikke sak») og kjører
`sjekkTilgangTilEnhet` og `sjekkTilgangsmaskin`, som `GET …/logg`.

| Rute | Svar |
|------|------|
| `GET /api/saksbehandling/v1/saker/{sakId}/notater` | 200 `SaksnotaterResponse` |
| `POST …/notater` med `{ "tekst": "…" }` | 202 `{ "notatId": "…" }`, publiserer `SaksnotatOpprettet` |
| `PUT …/notater/{notatId}` med `{ "tekst": "…" }` | 202, publiserer `SaksnotatEndret` |
| `DELETE …/notater/{notatId}` | 202, publiserer `SaksnotatSlettet` |

```kotlin
@Serializable
data class SaksnotaterResponse(val notater: List<Saksnotat>)

@Serializable
data class Saksnotat(
    val notatId: String,
    val tekst: String,
    val skrevetAvIdent: String,
    val skrevetAvNavn: String,
    val skrevetAvRolle: AktorRolle,
    val opprettet: String,
    val endret: String?,
)
```

Regler:

- `POST`, `PUT` og `DELETE` på en Arena-sak (`kildeTilBehandling = ARENA` i
  `SakTilgangsgrunnlag`) gir 409 med `kode = SAK_KILDE_ARENA`, uten event.
  `GET` på en Arena-sak gir en tom liste.
- `tekst` trimmes. Tom tekst eller mer enn 1000 tegn gir 400 med
  `kode = UGYLDIG_NOTAT`.
- Rollen i eventen (`skrevetAvRolle`, `endretAvRolle`, `slettetAvRolle`):
  `BESLUTTER` når innlogget er `beslutter_ident` på saken, ellers
  `SAKSBEHANDLER` hvis innlogget har den rollen, ellers `BESLUTTER`.
- `PUT` og `DELETE`: notatet må finnes på saken i URL-en, ellers 404 «fant ikke
  notat». Er `skrevet_av_ident` en annen enn `principal.navIdent`, svarer
  API-et 403 med `kode = IKKE_EGET_NOTAT`, uten event.
- `notatId` som ikke er en UUID, gir 400, som `sakIdParameter()`.
- Listen er sortert på `opprettet_at` synkende.

Ingen ny sporingslogg til ArcSight. Detaljoppslaget logger allerede at
saksbehandleren har sett saken, og notatpanelet vises bare der.

### 4. Frontend: knapper og panel

`SakPage.tsx` får tilstanden `sidepanel: "logg" | "notat" | null`.

- Ny komponent `PanelKnapp` (ved siden av `HeaderKnapp`): ikon, tekst,
  valgfritt antall, ingen chevron. Aktiv knapp er understreket og har lys
  bakgrunn, som i skissen. `aria-expanded` og `aria-controls` peker på
  panelet. Antallet er en blå sirkel med Aksel-tokens, og knappen får
  `aria-label` «Notat, {n} notater».
- «Logg» og «Notat» står etter `Tildeling`. Klikk på aktiv knapp lukker
  panelet. Klikk på den andre bytter panel. Bare ett panel er åpent om gangen.
- `HeaderKnapp` og `Tildeling` er uendret.
- Under linjen legges fanene og panelet i en `HStack`: fanene får
  `flex: 1; min-width: 0`, panelet en fast bredde (`20rem`) med venstrekant og
  full høyde. Overskriften i panelet («Logg» eller «Notater» med ikon) har samme
  høyde som `Tabs.List`.
- Under `md` legges panelet over fanene i full bredde, slik at innholdet ikke
  presses under minstebredden.
- Knappene og panelet skjules når kvitteringen vises, som i dag.
- På Arena-saker (`erArenaSak`) vises bare «Logg». «Notat» vises ikke, og
  notatene hentes ikke.

`Sakslogg.tsx` mister `Popover` og `HeaderKnapp` og eksporterer bare innholdet
(`SaksloggInnhold`). Loggen hentes fortsatt først når panelet åpnes.

### 5. Frontend: notatpanelet

Ny `components/Saksnotater.tsx` og `hooks/useSaksnotater.ts`.

- `useSaksnotater(sakId)` henter `GET …/notater` når `SakPage` lastes, fordi
  antallet vises på knappen også når panelet er lukket. For Arena-saker får
  hooken `null` og henter ingenting.
- `useSaksnotatHandlinger(sakId)` har `opprett`, `endre` og `slett`. Hver
  handling oppdaterer SWR-cachen optimistisk og revaliderer etter 500 ms, som
  `useTildeling`. Ved feil rulles cachen tilbake.
- Panelet viser, ovenfra og ned:
  1. Boksen «Internt arbeidsnotat» med `EyeClosedIcon` og teksten «Vises ikke
     til arbeidsgiver eller deltaker med mindre de ber om innsyn.»
     (`Box` med `meta-purple`-farger, som i skissen).
  2. «Skriv nytt notat» (`Button`, `variant="primary"`, `size="small"`,
     `PlusIcon`, full bredde).
  3. Notatene. Hvert notat er et `article` med en sirkel med forbokstaven i
     navnet (`aria-hidden`), navnet med `weight="semibold"`, tidspunktet i
     `<time>` («24. nov. 2026 kl. 10:42») og teksten med bevarte linjeskift.
     Er `skrevetAvIdent` lik innlogget, vises «Endre» (`tertiary`, `xsmall`)
     og «Slett» (`tertiary`, `xsmall`, `data-color="danger"`).
- «Skriv nytt notat» bytter knappen ut med et `Textarea` (label «Nytt notat»,
  `maxLength={1000}`, teller) og knappene «Lagre» og «Avbryt». Fokus går til
  feltet. «Lagre» med tom tekst viser feilen «Skriv et notat før du lagrer.» i
  feltet.
- «Endre» bytter teksten i notatet ut med et `Textarea` (label «Endre notat»,
  `maxLength={1000}`, teller) som er fylt ut med teksten som var, og «Lagre» og
  «Avbryt». Bare ett notat kan redigeres om gangen.
- «Slett» sletter notatet uten bekreftelse, slik kortet sier. Fokus flyttes til
  neste notat, eller til «Skriv nytt notat» hvis det var det siste.
- Er notatet endret, viser tidspunktet «… · endret {tidspunkt}».
- Ingen notater: «Ingen notater ennå.»
- Feil vises som `LocalAlert` i panelet: 403 med `tilgangAvvistTekst`,
  403 `IKKE_EGET_NOTAT` «Du kan bare endre og slette notater du har skrevet
  selv.», 409 `SAK_KILDE_ARENA` med meldingen fra API-et, 503 «Vi kunne ikke sjekke tilgangen din akkurat nå. Prøv igjen om
  litt.», ellers «Kunne ikke lagre notatet.» eller «Kunne ikke hente
  notatene.».

`utils/constants.ts` får `SAKSBEHANDLING_SAKSNOTATER_URL(sakId)` og
`SAKSBEHANDLING_SAKSNOTAT_URL(sakId, notatId)`.

### 6. MSW-mock

`mocks/handlers.ts` får de fire rutene med notater i minnet per sak. Saken som
er tildelt mock-brukeren, starter med to notater: ett fra mock-brukeren og ett
fra `mockBeslutter`. De andre sakene starter uten notater. `PUT` og `DELETE` på
et notat fra en annen gir 403 `IKKE_EGET_NOTAT`. Skriving på en Arena-sak gir
409 `SAK_KILDE_ARENA`, og `GET` gir en tom liste. Mocken legger også
«Notat opprettet», «Notat endret» og «Notat slettet» i saksloggen.

### 7. Dokumentasjon

- `sak-datamodell.md`: ny seksjon `saksnotat` under *Tabeller*, og raden i
  *ER-oversikt*.
- `sakslogg.md`: «Logg» er et panel, ikke en dropdown. Nye innslag «Notat
  opprettet», «Notat endret» og «Notat slettet». Henvisning hit.
- `event-flow.md` regenereres med `EventFlowDiagram`.

### 8. Tester

Backend følger testoppsettet som finnes: `kotlin.test`,
`testApplicationWithDatabase`, `MockAzureAdIntrospector` og `QueuedEvents`.

- Ny `SaksnotatApiTest`:
  - `GET` gir notatene på saken, nyeste først, og ikke notater fra andre saker.
  - `POST` publiserer `SaksnotatOpprettet` med ident, navn og rolle fra
    principal og trimmet tekst, og svarer 202 med `notatId`.
  - `POST` med tom tekst og med 1001 tegn gir 400 uten event. 1000 tegn går
    gjennom.
  - `POST`, `PUT` og `DELETE` på en Arena-sak gir 409 `SAK_KILDE_ARENA` uten
    event, og `GET` gir en tom liste.
  - `POST` uten rolle gir 403, uten enhetstilgang 403, og Tilgangsmaskinen som
    avviser gir 403. Ingen event.
  - `PUT` og `DELETE` på eget notat publiserer `SaksnotatEndret` og
    `SaksnotatSlettet`.
  - `PUT` og `DELETE` på en annens notat gir 403 `IKKE_EGET_NOTAT` uten event.
  - `PUT` og `DELETE` på ukjent notat og på notat fra en annen sak gir 404.
- Nye handlertester, `OpprettSaksnotatTest`, `EndreSaksnotatTest` og
  `SlettSaksnotatTest`: raden skrives, endres og slettes, og handleren
  publiserer `SakOppdatert` med riktig rolle, ident og fast tekst. Samme event
  to ganger gir samme resultat og ett innslag. Endre og slett på et slettet
  notat gir `success()` uten `SakOppdatert`.
- Eventene: `init` avviser blank tekst, for lang tekst og `SYSTEM`.

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW og `LocalApplication`
med skjermbilder i PR-en.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V43__saksnotat.sql` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | `SaksnotatTable` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SaksnotatApi.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | Registrerer `configureSaksnotatApiV1()` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | Tre events, tre handlere registrert |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/OpprettSaksnotat.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/EndreSaksnotat.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/SlettSaksnotat.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/event-flow.md` | Regenerert |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/SaksnotatApiTest.kt` | Ny |
| `backend/src/test/kotlin/no/nav/ekspertbistand/event/handlers/*SaksnotatTest.kt` | Nye |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Panel-tilstand, `PanelKnapp`, layout med panel |
| `frontend/saksbehandling/src/pages/SakPage.module.css` | Layout for panelet |
| `frontend/saksbehandling/src/components/PanelKnapp.tsx` (+ `.module.css`) | Ny |
| `frontend/saksbehandling/src/components/Sakslogg.tsx` | Bare innholdet, uten popover |
| `frontend/saksbehandling/src/components/Saksnotater.tsx` | Ny |
| `frontend/saksbehandling/src/hooks/useSaksnotater.ts` | Ny |
| `frontend/saksbehandling/src/utils/constants.ts` | URL-er |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Notat-rutene |
| `specifications/sak-datamodell.md`, `specifications/sakslogg.md` | Se *7* |

## Kanttilfeller og feilmodus

- **Notatet er ikke lagret når API-et svarer.** Svaret er 202 før handleren har
  kjørt. Frontend viser notatet optimistisk og revaliderer etter 500 ms. Er
  handleren tregere, kan revalideringen mangle notatet et øyeblikk. Neste
  revalidering retter det.
- **Endre eller slette et notat en kollega har slettet.** Kan ikke skje, fordi
  bare forfatteren kan slette. Sletter forfatteren i to faner, svarer den andre
  404, og panelet revaliderer.
- **Endre og slette raskt etter hverandre.** `EventQueue` henter eventene i
  rekkefølge, men med `SKIP LOCKED` og nye forsøk etter feil er rekkefølgen ikke
  garantert. `PUT` og `DELETE` krever at raden finnes, så `SaksnotatEndret` og
  `SaksnotatSlettet` publiseres aldri før `OpprettSaksnotat` har kjørt. Kommer
  endringen etter slettingen, treffer den ingen rad og gir `success()`.
- **Innlogget mister tilgangen til saken.** Alle rutene svarer 403, og panelet
  viser feilen.
- **Forfatteren slutter eller bytter navn.** Navnet er lagret da notatet ble
  skrevet og endres ikke.
- **Saken slettes.** `ON DELETE CASCADE` fjerner notatene. Saker slettes ikke i
  dag.
- **Replay av `event_log`.** Handlerne er idempotente på `notatId` og tåler å
  kjøre eventene på nytt. `idempotencyGuard` hindrer doble innslag i
  saksloggen.
- **Saken blir Arena-sak.** Kilden endres ikke etter at saken er opprettet, så
  en sak med notater blir aldri en Arena-sak.
- **Personopplysninger.** Teksten kan ha personopplysninger. Den ligger i
  `event_log`, `event_queue` og `saksnotat`, og vises bare i UI. Applogger har
  bare `sakId` og `notatId`. Identen logges ikke utenfor teamLog.
- **Lange notater.** Teksten brytes (`overflow-wrap: anywhere`), og panelet
  scroller.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene 2026-11-27. Svarene står under *Beslutninger*.

## Ferdig når

- [ ] «Notat» i linjen med tilbakelenken åpner notatpanelet og viser antallet notater i en blå sirkel
- [ ] «Logg» åpner loggen i samme panel, og bare ett panel er åpent om gangen
- [ ] Panelet skyver saksinnholdet til siden
- [ ] «Meny» og innholdet i fanene er uendret
- [ ] Notatpanelet viser infoboksen, «Skriv nytt notat» og notatene med navn og tidspunkt, nyeste først
- [ ] Saksbehandler og beslutter med tilgang til saken kan skrive et notat på maks 1000 tegn
- [ ] Forfatteren kan endre og slette sine egne notater, og ingen andre ser knappene
- [ ] «Endre» fyller feltet med teksten som var, og et endret notat viser «· endret {tidspunkt}»
- [ ] Arena-saker har ikke «Notat», og API-et avviser skriving med 409 `SAK_KILDE_ARENA`
- [ ] Saksloggen får «Notat opprettet», «Notat endret» og «Notat slettet», uten teksten
- [ ] API-et avviser endring og sletting av andres notater med 403 `IKKE_EGET_NOTAT`
- [ ] Notatene lagres via events og handlere i `saksnotat`, og API-et leser ikke `event_log`
- [ ] Teksten i notater logges ikke
- [ ] MSW-mocken har notater og dekker 403 på andres notater
- [ ] `sak-datamodell.md`, `sakslogg.md` og `event-flow.md` er oppdatert
- [ ] Backendtestene i *8* er grønne, og `pnpm typecheck` og `pnpm lint` går gjennom
