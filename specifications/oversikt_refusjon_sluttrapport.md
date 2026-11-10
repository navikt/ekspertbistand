# Refusjon og sluttrapport i saksoversikten

## Mål

Når arbeidsgiver sender inn refusjonskrav eller sluttrapport på en innvilget sak,
skal saksbehandler se det som en oppgave i saksoversikten:

- Oppgavetypene «Refusjon» og «Sluttrapport» vises i oversikten, med merkene
  «R» og «SR».
- En sak får én rad per oppgave. Har saken både refusjonskrav og sluttrapport,
  står den på to rader.
- Oppgavene vises i «Oppgaver som venter» for saksbehandlere.
- Kolonnen «Søknad mottatt» blir «Mottatt» og viser når innsendingen kom.
  Listen sorteres på denne datoen.
- En innvilget sak med en slik oppgave kan tildeles og overtas.

Oversikten bygges uten nye hendelser. Oppgavene utledes av tabellene
`refusjonskrav` og `sluttrapport`, slik søknadsoppgavene utledes av saken.

## Bakgrunn

- `GET /api/saksbehandling/v1/saker` gir én rad per sak på innloggets enheter.
  `oppgavetype(status, harRetur)` gir `SOKNAD`, `SOKNAD_RETUR`,
  `SOKNAD_BESLUTTER` eller null. Null betyr at frontend viser saksstatusen.
  Se `listevisning.md`.
- `refusjonskrav` har `sak_id` (obligatorisk og unik fra V33 og V35),
  `status` og `opprettet`. `RefusjonDb.lagreRefusjonskrav` setter status til
  `MOTTATT`.
- `sluttrapport` har `sak_id` (obligatorisk fra V34), `status` (standard
  `MOTTATT`) og `opprettet`. En sak kan ha flere sluttrapporter.
  Saksdetaljen viser den nyeste.
- Innsendingene publiserer ingen hendelse og endrer ikke saksstatusen.
- `tildelingHindring` avviser innvilgede saker, fordi de ikke har noen oppgave
  for saksbehandler (`tildeling_detaljvisning.md`, Ken 2026-10-13).
- Tildelingen ligger på saken (`sak.saksbehandler_ident`). Saksbehandleren fra
  søknaden står fortsatt på saken etter vedtaket.
- Rutene for saksbehandling er stengt i prod.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Ken, 2026-11-10 | Oversikten kommer før hendelsene for innsendt refusjonskrav og sluttrapport, og skal ikke være avhengig av dem. |
| Ken, 2026-11-10 | Refusjonskrav og sluttrapport vises begge i oversikten, som hver sin oppgave. |
| Ken, 2026-11-10 | Oppgaven går til saksbehandleren som hadde søknaden. Saken frigjøres ikke når en innsending kommer. |
| Ken, 2026-11-10 | Bare saker fra ny løsning (`kilde_til_behandling = EKSPERTBISTAND`). Arena-saker får ikke oppgavene. |
| Ken, 2026-11-10 | «Mottatt» og sorteringen bruker tidspunktet innsendingen kom. |
| Ken, 2026-11-10 | Avslutning av oppgavene er ikke med nå. |
| Ken, 2026-11-10 | Merket for sluttrapport er «SR». |
| `tildeling_detaljvisning.md` (Ken, 2026-10-13) | Innvilgede saker kan tildeles og overtas når de har oppgavetypen «Refusjon». Det gjelder også «Sluttrapport». |

Valg i spesifikasjonen:

- Oppgavene utledes bare når innsendingen har `status = 'MOTTATT'`. Når
  refusjonsbehandlingen gir kravet en ny status, forsvinner oppgaven uten
  endring her.
- `tildelingHindring` får innsendingene fra databasen, aldri fra klienten.
- Saksdetaljen viser én oppgavetype: `REFUSJON` foran `SLUTTRAPPORT` foran
  søknadsoppgaven. Begge har rollen saksbehandler, så detaljen viser det samme.
- Tilgangsmaskinen får hvert fødselsnummer én gang, selv om saken står på to
  rader.

## Avgrensning

- Hendelser for innsendt refusjonskrav og sluttrapport. Det er neste kort.
- Avslutning av oppgavene og behandling av refusjonskravet.
- «Refusjon - beslutter».
- Sortering på kolonnene i frontend. API-et sorterer.
- Beløp, filnavn og andre detaljer om innsendingen i listen. De står i
  saksdetaljen.

## Tilnærming

```
GET /saker ──> hentSakerForSaksbehandling
                 ├─ sak JOIN soknad (som før)
                 ├─ hentMottatteInnsendinger(sakIder): refusjonskrav og sluttrapport med status MOTTATT
                 └─ oppgaver(...) per sak ──> én SakListeElement per oppgave, sortert på mottatt
POST /saker/{id}/tildeling ──> hentSakTilgangsgrunnlag ──> hentMottatteInnsendinger ──> tildelingHindring
```

### 1. Backend: oppgavetype

`saksbehandling/Oppgavetype.kt`:

```kotlin
enum class Oppgavetype { SOKNAD, SOKNAD_RETUR, SOKNAD_BESLUTTER, REFUSJON, SLUTTRAPPORT }

data class MottatteInnsendinger(val refusjonskrav: Instant?, val sluttrapport: Instant?)

fun oppgaver(
    status: Saksstatus,
    kilde: KildeTilBehandling,
    harRetur: Boolean,
    innsendinger: MottatteInnsendinger,
): List<Oppgave>
```

- `Oppgave` har `type: Oppgavetype` og `mottatt: Instant?`. Null betyr at
  søknaden gir datoen.
- Saker fra ny løsning med mottatt refusjonskrav eller sluttrapport får én
  oppgave per innsending, og ingen søknadsoppgave.
- Andre saker får søknadsoppgaven fra `oppgavetype(status, harRetur)` som før,
  eller en tom liste.
- `REFUSJON` og `SLUTTRAPPORT` har rollen `SAKSBEHANDLER`.

### 2. Backend: listen

`hentSakerForSaksbehandling`:

- Henter mottatte innsendinger for sak-ID-ene, med én spørring per tabell.
  For sluttrapport gjelder det nyeste `opprettet` per sak.
- Lager én `SakListeElement` per oppgave. Saker uten oppgave får én rad med
  `oppgavetype = null`, som før.
- Nytt felt `mottatt`: innsendingens `opprettet`, ellers søknadens
  `opprettetTidspunkt`. Listen sorteres på `mottatt` synkende.
- Ruten sender hvert fødselsnummer bare én gang til Tilgangsmaskinen.
- Rader for refusjon og sluttrapport får `beslutterIdent` og `beslutterNavn`
  lik null. Beslutteren på saken er søknadens, og innsendingene har ingen
  beslutter før «Refusjon - beslutter». Da tar heller ikke «Mine saker» med
  radene for søknadens beslutter.

### 3. Backend: tildeling

- Tildelingsruten henter innsendingene med `hentMottatteInnsendinger` etter
  tilgangssjekkene. `SakTilgangsgrunnlag` endres ikke, fordi de andre
  tilgangssjekkene ikke trenger innsendingene.
- `tildelingHindring(status, kilde, innsendinger, saksbehandlerIdent, principal)`
  bruker `oppgaver(...)`. Saken kan tildeles når en av oppgavene har rollen
  saksbehandler.
- `SakTildeltSaksbehandler` og `tildelSak` endres ikke. `tildelSak` lar
  statusen `INNVILGET` stå.

### 4. Backend: saksdetaljen

`hentSakForSaksbehandling` setter `oppgavetype` til den første oppgaven fra
`oppgaver(...)`, og `kanTildeleMeg` fra den nye `tildelingHindring`.

`SakDetaljer` får `innsendinger: { refusjonskrav, sluttrapport }`, med
tidspunktet for mottatt innsending eller null. Feltet kommer fra
`hentMottatteInnsendinger`, slik at oversikten og fanene i saken bygger på samme
spørring. En egen liste med alle oppgaver i detaljen er ikke tatt med, fordi
ingen visning trenger den.

### 5. Frontend

- `useSaker.ts`: `Oppgavetype` får `REFUSJON` og `SLUTTRAPPORT`, og
  `rolleForOppgave` gir dem `saksbehandler`. `SakListeElement` får `mottatt`.
- `OversiktPage.tsx`:
  - Merket følger oppgavetypen: «SØ» for søknad, «R» for refusjon og «SR» for
    sluttrapport. Søknad har fargen `warning`, refusjon `meta-purple` og
    sluttrapport `meta-lime`.
  - Teksten er «Refusjon» og «Sluttrapport».
  - Radnøkkelen er `sakId` + `oppgavetype`.
  - Kolonnen «Søknad mottatt» blir «Mottatt» og viser `mottatt`.
  - En rad for refusjon eller sluttrapport lenker til saken med
    `?fane=refusjon` eller `?fane=sluttrapport`.
- `SakPage.tsx`:
  - Fanene «Sluttrapport» og «Refusjon» vises bare når `innsendinger` har et
    tidspunkt.
  - Valgt fane står i `?fane=` og oppdateres når saksbehandler bytter fane.
    Uten gyldig fane vises «Søknad».
  - Refusjonskrav og sluttrapport hentes først når fanen er valgt.
- `mocks/handlers.ts` lager de samme radene og svarer med refusjonskrav og
  sluttrapport for saker som har oppgaven.

### 6. Testdata

`LokaleSaker.kt` får én innvilget sak fra ny løsning med refusjonskrav og
sluttrapport, satt inn direkte i tabellene som opplastingen gjør. Hver innsending
får et PDF-vedlegg, som ved ekte innsending. Saken får ingen Arena-hendelser.

## Tester

- `OppgavetypeTest`: `oppgaver(...)` for alle statuser, begge kildene, med og
  uten innsendinger, og rollene til de nye oppgavetypene.
- `TildelingHindringTest`:
  - En innvilget sak fra ny løsning kan tildeles med en mottatt innsending.
  - Uten innsending avvises den.
  - En Arena-sak med innsending avvises.
- `SaksbehandlingSakApiTest`:
  - Refusjon gir én rad, sluttrapport gir én rad, og begge gir to rader for
    samme sak.
  - En Arena-sak med innsending gir ingen ny rad.
  - `mottatt` og sorteringen følger innsendingen.
  - Rader for innsendinger har ingen beslutter. Rader for søknaden beholder den.
  - Tilgangsmaskinen får hvert fødselsnummer én gang.
  - `POST …/tildeling` på en innvilget sak med innsending gir 202, uten
    innsending 409.
- Frontend: typesjekk og lint. Playwright-a11y dekker oversikten.

## Åpne spørsmål

- **Topartskontroll på refusjon.** Den som var beslutter på søknaden, kan ha
  rollen saksbehandler og ta refusjonen. Vi stopper det ikke nå. Kontrollen
  kommer med «Refusjon - beslutter».
