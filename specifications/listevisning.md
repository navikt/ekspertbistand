# Listevisning for saksbehandler og beslutter

Trello: https://trello.com/c/5Yw04ZwB/630-fikse-listevisning-av-saker-som-saksbehandlere-og-beslutter-jobber-med-i-saksbehandslingsystemet
Kort-ID: `6acc8fd40ead46225972f216`

## Mål

Saksoversikten skal vise hva innlogget kan gjøre nå, enten som saksbehandler
eller som beslutter:

- Første fane, «Oppgaver som venter», viser saker med en oppgave for en rolle
  innlogget har, også saker kolleger jobber med. Listen åpner på denne fanen.
- Ny kolonne «Beslutter» viser navnet på beslutter. Er saken klar for
  beslutter og ingen har den, viser kolonnen «Tildel meg» til en beslutter som
  ikke selv sendte det foreløpige vedtaket.
- Kolonnen «Status» heter «Oppgavetype» og viser hva som skal gjøres i saken:
  «Søknad» eller «Søknad - beslutter». Oppgavetypen utledes av saken og er
  ingen ny datamodell.

Beslutter kan dermed plukke saker som er sendt til beslutning. Det svarer på
det åpne punktet «Hvordan beslutter plukker saker» i `forelopig_vedtak.md`.

Dette kortet bygger ikke beslutterens side av saken (godkjenne, returnere),
oppgavetypen «Søknad - retur», refusjon eller sluttrapport. Se *Avgrensning*.

## Bakgrunn

Fra før:

- `OversiktPage.tsx` har fanene «Til godkjenning», «Mine saker», «Pågående»,
  «Avsluttet» og «Alle», og åpner på «Alle». Fanene filtrerer i frontend.
  «Til godkjenning» er saker uten saksbehandler som ikke er avsluttet. «Mine
  saker» er saker der innlogget er saksbehandler.
- Kolonnene er Saksbehandler, Status, Arbeidsgiver, Deltaker, Startdato og
  Søknad mottatt, og til slutt handlingsmenyen. Status viser
  `saksstatusTekst`.
- `GET /api/saksbehandling/v1/saker` krever rolle `SAKSBEHANDLER` eller
  `BESLUTTER` og gir sakene på innloggets enheter. Listen kaller ikke
  Tilgangsmaskinen. `kanTildeleMeg` følger `tildelingHindring`, og er false i
  listen for saker som er tildelt en annen.
- Frontend kjenner ikke rollene til innlogget. `InnloggetAnsatt` har id, navn,
  e-post og enheter.
- `sak.beslutter_ident` finnes fra V12. Ingen skriver til den. Det finnes ingen
  kolonne for navnet på beslutter.
- `forelopig_vedtak.innstilt_av_ident` lagrer hvem som sendte saken til
  `TIL_BESLUTNING` (kort 621). `sak-datamodell.md`: den som sendte det
  foreløpige vedtaket, kan ikke beslutte det. Andre som har vært saksbehandler
  på saken, kan.
- `sak_retur` finnes fra V12, men ingen skriver til den, og den har ingen
  Exposed-tabell. Ifølge `sak-datamodell.md` setter retur saken tilbake til
  `UNDER_BEHANDLING` og legger en rad i `sak_retur`.
- `sak.refusjon_id` og `sak.sluttrapport_id` finnes, men ingen skriver til dem.
- Tildeling av saksbehandler (`tildel_meg_sak.md`): `POST …/tildeling`
  publiserer `SakTildeltSaksbehandler`, `TildelSaksbehandler` kaller `tildelSak`
  og publiserer `SakOppdatert`. `tildeling_event_id` hindrer at en eldre event
  skriver over en nyere.
- `SakProjection` kjører bare i prod og backfiller bare Arena-saker. Arena-saker
  får aldri foreløpig vedtak.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-12.

| Node | Lenke | Viser |
|------|-------|-------|
| `695:23735` | [beslutter og saksbehandler i hver sin kolonne](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=695-23735) | `InternalHeader` med enhetsvelger. Fanene «Oppgaver som venter» (valgt), «Mine saker», «Pågående», «Avsluttet» og «Alle», med antall. Kolonnene Saksbehandler (sorterbar), Beslutter, Oppgavetype, Arbeidsgiver, Deltakere, Tiltaksperiode (fra og til, sorterbar), Søknad mottatt (sorterbar) og handlingsmeny. «Tildel meg» (`xsmall`, sekundær) står i saksbehandlerkolonnen på saker uten saksbehandler, og i beslutterkolonnen på en sak med «Refusjon - beslutter». Oppgavetypen har et lite merke foran teksten: «SØ» for søknad og «R» for refusjon. Radene har «Søknad», «Søknad - beslutter», «Søknad - retur», «Refusjon» og «Refusjon - beslutter». |

Skissen har realistiske testdata (navn). De er ikke kopiert hit.

Avvik mellom kort og skisse:

- **Saker i første fane.** Kortet sier at fanen viser sakene innlogget jobber
  med eller kan plukke. Skissen har også saker andre saksbehandlere har. Vi
  følger skissen, fordi kolleger må kunne se og overta hverandres saker
  (trikkefaktor, Ken 2026-10-12).
- **Retur, refusjon og sluttrapport.** Skissen har «Søknad - retur»,
  «Refusjon» og «Refusjon - beslutter». Det finnes ingen flyt for retur eller
  refusjon på saken ennå, så vi bygger bare «Søknad» og «Søknad - beslutter».
  Se *Avgrensning*.
- **«Deltakere».** Saken har én deltaker. Kolonnen beholder navnet «Deltaker».
- **Sortering.** Skissen har sorteringspiler. Dagens kolonner har `sortable`
  uten sortering. Sortering er ikke med.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Første fane viser saker innlogget jobber med eller kan plukke som saksbehandler eller beslutter. En ansatt kan ha begge rollene, men topartskontrollen gjelder. Utvidet av Ken 2026-10-12 til også å ta med saker andre har, se under. |
| Kortbeskrivelsen | Kolonnen «Beslutter» viser beslutter når den er satt, og «Tildel meg» når saken er klar for beslutter, innlogget har rollen beslutter og ikke sendte det foreløpige vedtaket. Etter retur viser kolonnen fortsatt navnet på beslutter. |
| Kortbeskrivelsen | «Status» blir «Oppgavetype». Oppgavetypen er en forklaring i GUI-et. Datamodellen er fortsatt saken. |
| Kortbeskrivelsen | Oppgavetypene «Refusjon», «Refusjon - beslutter» og «Sluttrapport» hører til refusjon og sluttrapport. Detaljene om forhåndsvarsel ved avslag på refusjonskrav er ikke avklart. |
| `sak-datamodell.md` (Ken, 2026-10-08) | Habilitet gjelder vedtaket: den som sendte saken til `TIL_BESLUTNING`, kan ikke beslutte den. Tildelingen sjekker dette, og beslutterkortet må sjekke det igjen når vedtaket fattes. |
| Ken, 2026-10-06 (minne, `tildel_meg_sak.md`) | API-et publiserer en event, og en handler gjør endringen. Saksloggen skrives via `SakOppdatert` og `SkrivSakslogg`. |
| Ken, 2026-10-10 (`forelopig_vedtak.md`) | `SakProjection` backfiller bare Arena-saker og får ingen funksjonalitet for nye saker. |
| Ken, 2026-10-10 (`forelopig_vedtak.md`) | Backend bruker testoppsettet som finnes, uten ny testteknologi. |
| Ken, 2026-10-12 | Listen åpner på første fane, «Oppgaver som venter». |
| Ken, 2026-10-12 | «Oppgaver som venter» tar med saker andre har, fordi kolleger må kunne se og overta hverandres saker (trikkefaktor). |
| Ken, 2026-10-12 | «Søknad - retur» venter til beslutterflyten bygges. |
| Ken, 2026-10-12 | «Tildel meg» som beslutter er med i dette kortet. |
| Ken, 2026-10-12 | «Tiltaksperiode» viser perioden fra det foreløpige vedtaket, ellers søknadens startdato. |
| Ken, 2026-10-12 | «Oppgavetype» viser saksstatusen («Innvilget», «Avslått», «Avsluttet») når saken ikke har noen oppgave. |
| Ken, 2026-10-13 | «Tildel meg» i listen åpner saksdetaljen når tildelingen får et OK-svar. |

Valg i spec-en:

- Oppgavetypen utledes i backend, med samme mønster som `kanTildeleMeg`, så
  listen og senere API-er bruker én regel.
- Backend regner også ut om saken har en oppgave for en rolle innlogget har
  (`harOppgaveForMeg`), fordi frontend ikke kjenner rollene.
- Har saken ingen periode i et foreløpig vedtak (ingen vedtak, eller avslag),
  viser «Tiltaksperiode» søknadens startdato som fra-dato og «–» som til-dato.

## Avgrensning

- Beslutterens side av saken: godkjenne, returnere og fatte vedtak. Det er
  beslutterkortet. Det kortet skal ikke nullstille `beslutter_ident` ved retur,
  slik at saken går tilbake til samme beslutter.
- Frigjøring av beslutter og «Tildel beslutter» (tildele en annen).
- Visning av beslutter og «Tildel meg» som beslutter på `SakPage`.
- Oppgavetypen «Søknad - retur». Den kommer med beslutterflyten, utledet av
  `sak_retur` (Ken, 2026-10-12).
- Oppgavetypene «Refusjon», «Refusjon - beslutter» og «Sluttrapport». De kommer
  med kortene for refusjon og sluttrapport. `Oppgavetype` utvides da.
- Sortering på kolonnene.
- Handlingsmenyen endres ikke.

## Tilnærming

```
GET /saker ──> hentSakerForSaksbehandling ──> oppgavetype, harOppgaveForMeg, beslutter
                     │
                     └── forelopig_vedtak (siste versjon: innstilt_av_ident, tiltaksperiode)

POST /saker/{sakId}/tildeling/beslutter
   └── publishEventQueue(SakTildeltBeslutter) ──> TildelBeslutter ──> sak.beslutter_*
                                                        └── SakOppdatert ──> SkrivSakslogg
```

### 0. Migrering

`V23__sak_beslutter_tildeling.sql`:

```sql
ALTER TABLE sak ADD COLUMN IF NOT EXISTS beslutter_navn TEXT NULL;
ALTER TABLE sak ADD COLUMN IF NOT EXISTS beslutter_tildeling_event_id BIGINT NULL;

ALTER TABLE sak DROP CONSTRAINT IF EXISTS chk_beslutter_navn_med_ident;
ALTER TABLE sak ADD CONSTRAINT chk_beslutter_navn_med_ident
    CHECK ((beslutter_ident IS NULL) = (beslutter_navn IS NULL));
```

Ingen skriver `beslutter_ident` i dag, så CHECK-en holder for alle rader.
Saksbehandling finnes bare i dev. Rollback: dropp constraint og kolonner.

`beslutter_tildeling_event_id` er egen kolonne, fordi tildeling av
saksbehandler og beslutter er uavhengige. Én felles kolonne ville latt en ny
saksbehandlertildeling hindre en eldre, men ikke brukt, beslutter-event.

### 1. Ny event

`EventData.SakTildeltBeslutter(sakId, soknadId, beslutterIdent, beslutterNavn,
tidspunkt)`, med `aggregateRootId = soknadId` og `require` på at
`beslutterNavn` ikke er blank, som `SakTildeltSaksbehandler`.

### 2. Backend: oppgavetype

Nytt enum i `saksbehandling/Oppgavetype.kt`:

```kotlin
@Serializable
enum class Oppgavetype { SOKNAD, SOKNAD_BESLUTTER }

fun oppgavetype(status: Saksstatus): Oppgavetype? = when (status) {
    Saksstatus.OPPRETTET, Saksstatus.UNDER_BEHANDLING -> Oppgavetype.SOKNAD
    Saksstatus.TIL_BESLUTNING -> Oppgavetype.SOKNAD_BESLUTTER
    Saksstatus.INNVILGET, Saksstatus.AVSLATT, Saksstatus.AVSLUTTET -> null
}
```

- `null` betyr at saken ikke har noen oppgave. Frontend viser da
  `saksstatusTekst` («Innvilget», «Avslått», «Avsluttet»).
- Arena-saker følger samme regel. De er aldri i `TIL_BESLUTNING`.
- Beslutterflyten legger til `SOKNAD_RETUR` for `UNDER_BEHANDLING` med rad i
  `sak_retur`.

### 3. Backend: tildeling av beslutter

🔴 Rød sone: habilitetssjekken er en del av topartskontrollen.

Regelen, i `SakApi.kt` ved siden av `tildelingHindring`:

```kotlin
enum class BeslutterTildelingHindring {
    MANGLER_ROLLE, UGYLDIG_STATUS, INHABIL, HAR_SAKEN_ALLEREDE,
}

fun beslutterTildelingHindring(
    status: Saksstatus,
    beslutterIdent: String?,
    innstiltAvIdent: String?,
    principal: AzureAdPrincipal,
): BeslutterTildelingHindring? = when {
    !principal.harRolle(Role.BESLUTTER) -> MANGLER_ROLLE
    status != Saksstatus.TIL_BESLUTNING -> UGYLDIG_STATUS
    innstiltAvIdent == null || innstiltAvIdent == principal.navIdent -> INHABIL
    beslutterIdent == principal.navIdent -> HAR_SAKEN_ALLEREDE
    else -> null
}
```

`innstiltAvIdent` er fra siste versjon i `forelopig_vedtak`. Mangler den, er
saken i en tilstand vi ikke forventer, og vi avviser (fail-closed).

`POST /api/saksbehandling/v1/saker/{sakId}/tildeling/beslutter`:

- Krever rolle `BESLUTTER`, og samme tilgangssjekker som tildeling av
  saksbehandler: `hentSakForTildeling` (enhet og Tilgangsmaskinen `KJERNE`).
- `null` gir 202 og publiserer `SakTildeltBeslutter` med ident og navn fra
  principal.
- `HAR_SAKEN_ALLEREDE` gir 202 uten event.
- `UGYLDIG_STATUS` gir 409 `SAK_UGYLDIG_STATUS`.
- `INHABIL` gir 403 `TilgangAvvistResponse(kode = "BESLUTTER_INHABIL",
  begrunnelse = "Du sendte det foreløpige vedtaket og kan ikke være beslutter på saken")`.
- `MANGLER_ROLLE` gir 403 «krever rolle beslutter».
- En sak med en annen beslutter kan tas over via API-et, som for
  saksbehandler. Listen viser bare knappen når saken mangler habil beslutter,
  se *4*.

`SakTilgangsgrunnlag` får `innstiltAvIdent`, hentet i `hentSakTilgangsgrunnlag`
fra siste versjon i `forelopig_vedtak`.

Handleren `TildelBeslutter` følger `TildelSaksbehandler`: idempotency guard,
`tildelBeslutter(...)` i `Db.kt`, `SakOppdatert` med
`utfortAvRolle = BESLUTTER` og notatet «Beslutter tildelt», alt i én
transaksjon. `tildelBeslutter` oppdaterer bare når:

- status er `TIL_BESLUTNING`,
- `beslutter_tildeling_event_id` er null eller eldre enn eventen,
- og siste `forelopig_vedtak.innstilt_av_ident` ikke er beslutteren.

Sjekken av habilitet ligger dermed både i API-et og i handleren. Status kan
ikke endres mellom API og handler i dag, men handleren skal ikke stole på at
API-et har sjekket. Saksloggen får posten bare når saken ble oppdatert.

Handleren registreres for alle miljøer i `configureEventHandlers`.
`SakProjection` får ikke eventen.

### 4. Backend: listen

`SakListeElement` og `SakDetaljer` får:

| Felt | Type | Innhold |
|------|------|---------|
| `beslutterNavn` | `String?` | Fra `sak.beslutter_navn`. |
| `oppgavetype` | `Oppgavetype?` | Se *2*. |
| `kanTildeleMegSomBeslutter` | `Boolean` | Se under. |

`SakListeElement` får i tillegg:

| Felt | Type | Innhold |
|------|------|---------|
| `harOppgaveForMeg` | `Boolean` | Se under. |
| `tiltaksperiodeFra`, `tiltaksperiodeTil` | `LocalDate?` | Fra siste foreløpige vedtak. Null uten vedtak og ved avslag. |

`hentSakerForSaksbehandling` henter siste versjon av `forelopig_vedtak`
(`innstilt_av_ident`, tiltaksperiode) for alle sakene i listen. Det er én
ekstra spørring med `sak_id IN (…)`, ikke én per sak.

`kanTildeleMegSomBeslutter`:

- I listen: `beslutterTildelingHindring(...) == null` og `beslutterIdent` er
  null eller lik `innstiltAvIdent`. Siste ledd gjør at en sak ikke blir stående
  hvis beslutteren senere sender et nytt foreløpig vedtak selv. Det kan skje
  etter retur, når beslutterflyten er bygd.
- I detaljoppslaget: `beslutterTildelingHindring(...) == null`.

`harOppgaveForMeg` er true når ett av disse gjelder, uansett hvem som har
saken (trikkefaktor, Ken 2026-10-12):

- Innlogget har rollen saksbehandler, og oppgavetypen er `SOKNAD`.
- Innlogget har rollen beslutter, og oppgavetypen er `SOKNAD_BESLUTTER`.

En saksbehandler uten rollen beslutter ser dermed ikke saker som venter på
beslutter i fanen. De ligger under «Pågående». Avsluttede saker har ingen
oppgavetype og er aldri med. Arena-saker er med som «Søknad», fordi de kan
tildeles (kort 621). En beslutter ser også saker hen selv sendte, men får ikke
«Tildel meg» på dem.

### 5. Frontend: hook

- `useSaker.ts`: `Oppgavetype`, og de nye feltene på `SakInfo` og
  `SakListeElement`.
- `useTildeling.ts` får valget `rolle: "saksbehandler" | "beslutter"`
  (standard `saksbehandler`). `tildelMeg` for beslutter kaller
  `SAKSBEHANDLING_BESLUTTER_TILDELING_URL(sakId)` og oppdaterer
  `beslutterIdent`, `beslutterNavn` og `kanTildeleMegSomBeslutter` optimistisk.
- `feilmelding` viser `begrunnelse` fra 403, slik at «Du sendte det foreløpige
  vedtaket …» kommer fram.

### 6. Frontend: saksoversikten

`OversiktPage.tsx`:

- Fanene er «Oppgaver som venter» (`harOppgaveForMeg`), «Mine saker» (innlogget er
  saksbehandler eller beslutter), «Pågående», «Avsluttet» og «Alle».
  `Tabs defaultValue` er «Oppgaver som venter».
- Kolonnene er Saksbehandler, Beslutter, Oppgavetype, Arbeidsgiver, Deltaker,
  Tiltaksperiode, Søknad mottatt og handlingsmenyen. Startdato går ut.
- Beslutter viser «Tildel meg» når `kanTildeleMegSomBeslutter` er true, ellers
  `beslutterNavn` eller «–».
- Oppgavetype viser et `Tag size="xsmall"` med «SØ» (`aria-hidden`) og teksten:
  «Søknad» eller «Søknad - beslutter». Uten oppgavetype vises
  `saksstatusTekst` uten merke.
- Tiltaksperiode viser `tiltaksperiodeFra` og `tiltaksperiodeTil`. Uten periode
  vises søknadens startdato og «–».
- `TildelMegKnapp` får rollen som prop. Klikk stopper som før at raden åpnes.
- Når tildelingen får et OK-svar, går listen til saksdetaljen. Feiler den, blir
  listen stående og viser feilmeldingen.

### 7. MSW-mock

`handlers.ts` regner ut `oppgavetype`, `harOppgaveForMeg`,
`kanTildeleMegSomBeslutter`, `beslutterNavn` og tiltaksperioden for mock-sakene.
Mock-brukeren har begge rollene. Ny rute
`POST /api/saksbehandling/v1/saker/:sakId/tildeling/beslutter`. Mock-sakene
dekker:

- en sak i `TIL_BESLUTNING` uten beslutter, sendt av en annen,
- en sak i `TIL_BESLUTNING` sendt av mock-brukeren, uten «Tildel meg»,
- en sak med beslutter satt,
- en sak i `UNDER_BEHANDLING` som en kollega har.

### 8. Dokumentasjon

- `sak-datamodell.md`: `beslutter_navn`, `beslutter_tildeling_event_id`, CHECK,
  og at oppgavetypen utledes av status.
- `forelopig_vedtak.md`: punktet «Hvordan beslutter plukker saker» i
  *Avgrensning* peker hit.
- `event/README.md` og `event/event-flow.md`: ny event og handler.

### 9. Tester

Backend følger testoppsettet som finnes, uten ny teknologi: `kotlin.test`,
`testApplicationWithDatabase`, `MockAzureAdIntrospector` og `QueuedEvents`.

- `OppgavetypeTest` (ren enhetstest): hver status.
- `BeslutterTildelingHindringTest` (ren enhetstest): hver hindring, og at
  `INHABIL` slår til også når innlogget har begge rollene.
- `SaksbehandlingSakApiTest`:
  - `POST …/tildeling/beslutter`: 202 og publisert event. 202 uten event når
    innlogget har saken. 403 uten rolle, uten enhet, avslått i
    Tilgangsmaskinen og inhabil (`BESLUTTER_INHABIL`). 409 for hver status
    som ikke er `TIL_BESLUTNING`. 404 ukjent sak. 503 når Tilgangsmaskinen
    feiler.
  - Listen: `oppgavetype`, `beslutterNavn`, tiltaksperioden,
    `kanTildeleMegSomBeslutter` (ledig, sendt av innlogget, tildelt annen,
    tildelt inhabil beslutter) og `harOppgaveForMeg` for saksbehandler,
    beslutter og begge rollene, også når en kollega har saken.
  - `lagreSak` setter `beslutter_navn` når `beslutterIdent` er satt (CHECK).
- `TildelBeslutterTest`, etter `TildelSaksbehandlerTest`: setter ident og navn
  og publiserer `SakOppdatert`. Samme event to ganger gir én oppdatering. Eldre
  event skriver ikke over nyere. Ingen endring når status ikke er
  `TIL_BESLUTNING` eller beslutteren sendte vedtaket. Ukjent sak gir
  `unrecoverableError`.

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V23__sak_beslutter_tildeling.sql` | Ny: kolonner og CHECK |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | `beslutterNavn`, `beslutterTildelingEventId`, `tildelBeslutter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Oppgavetype.kt` | Ny |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | Ny rute, `beslutterTildelingHindring`, nye felt i listen og detaljen |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Tilgangsjekker.kt` | `innstiltAvIdent` i `SakTilgangsgrunnlag`, `BESLUTTER_INHABIL` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | `SakTildeltBeslutter`, registrering av handler |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/TildelBeslutter.kt` | Ny handler |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md`, `event-flow.md` | Ny event og handler |
| `backend/src/test/kotlin/…` | Tester over |
| `frontend/saksbehandling/src/utils/constants.ts` | `SAKSBEHANDLING_BESLUTTER_TILDELING_URL` |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | `Oppgavetype`, nye felt |
| `frontend/saksbehandling/src/hooks/useSak.ts` | Nye felt på `SakDetaljer` via `SakInfo` |
| `frontend/saksbehandling/src/hooks/useTildeling.ts` | Rolle |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | Faner, kolonner, «Tildel meg» som beslutter |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Nye felt, ny rute, mock-saker |
| `specifications/sak-datamodell.md`, `specifications/forelopig_vedtak.md` | Se *8* |

## Kanttilfeller og feilmodus

- **Innlogget har begge rollene.** Saken vises én gang. Innlogget kan ikke
  tildele seg som beslutter på et foreløpig vedtak hen sendte selv. Har hen
  bare vært saksbehandler tidligere på saken, går det.
- **To besluttere klikker samtidig.** Begge får 202. Handleren behandler
  eventene i rekkefølge, så den siste vinner, og saksloggen får to linjer.
  Listen revalideres og viser den som har saken.
- **Beslutter er satt, og saksbehandler tildeler seg saken.** Tildeling av
  saksbehandler endrer ikke beslutter, og omvendt.
- **Retur og nytt foreløpig vedtak (når beslutterflyten er bygd).** Beslutter
  blir stående. Har beslutteren selv sendt det nye vedtaket, viser listen
  «Tildel meg» til andre besluttere (*4*), og beslutterkortet hindrer at hen
  fatter vedtaket.
- **Sak uten foreløpig vedtak i `TIL_BESLUTNING`.** Skal ikke skje.
  `innstiltAvIdent` er null, og tildeling avvises som `INHABIL`.
- **Avsluttede saker.** Ingen oppgavetype, ikke i «Oppgaver som venter».
- **Ingen saker venter.** Fanen viser en tom tabell, som de andre fanene i dag.
- **Listen kaller ikke Tilgangsmaskinen.** Som før. «Tildel meg» kan derfor gi
  403 fra Tilgangsmaskinen ved klikk. Feilen vises over tabellen.
- **Personopplysninger.** Navnet på beslutter logges ikke, bare ident i
  saksloggen, som for saksbehandler.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene om første fane, retur, tildeling av beslutter,
tiltaksperiode og saksstatus i «Oppgavetype» 2026-10-12. Svarene står under
*Beslutninger*.

## Ferdig når

- [ ] Listen åpner på «Oppgaver som venter», som viser sakene der `harOppgaveForMeg` er true, også saker kolleger har
- [ ] «Mine saker» viser saker der innlogget er saksbehandler eller beslutter
- [ ] Kolonnen «Oppgavetype» viser oppgavetypen fra backend, eller saksstatusen når saken ikke har noen oppgave
- [ ] Kolonnen «Beslutter» viser navnet, eller «Tildel meg» når `kanTildeleMegSomBeslutter` er true
- [ ] «Tildel meg» i listen åpner saksdetaljen etter et OK-svar og blir stående i listen ved feil
- [ ] Kolonnen «Tiltaksperiode» erstatter «Startdato»
- [ ] API-et publiserer `SakTildeltBeslutter` og skriver ikke selv til `sak` eller `sakslogg`
- [ ] Tildeling som beslutter krever rolle `BESLUTTER`, enhet, Tilgangsmaskinen `KJERNE` og `TIL_BESLUTNING`
- [ ] Den som sendte det foreløpige vedtaket, kan ikke tildele seg som beslutter, sjekket i både API og handler
- [ ] Handleren setter `beslutter_ident` og `beslutter_navn` og publiserer `SakOppdatert` med rolle `BESLUTTER`, idempotent
- [ ] `kanTildeleMegSomBeslutter` og `POST` bruker samme `beslutterTildelingHindring`
- [ ] Listen henter siste foreløpige vedtak med én spørring, ikke én per sak
- [ ] `sak-datamodell.md`, `forelopig_vedtak.md`, `event/README.md` og `event-flow.md` er oppdatert
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
