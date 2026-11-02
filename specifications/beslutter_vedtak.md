# Beslutter fatter vedtak eller sender i retur

Trello: https://trello.com/c/4kkrg41a/631-beslutter-fatter-vedtak-p%C3%A5-s%C3%B8knaden-el-sender-tilbake
Kort-ID: `6acccc401a4c00755e9e2396`

## Mål

Beslutteren som har saken, skal kunne ta stilling til saksbehandlers foreløpige
vedtak på en søknad. Beslutteren har to valg:

- **Godkjenn:** vedtaket fattes, uten ekstra bekreftelse. Saken går til
  `INNVILGET` eller `AVSLATT`, søknaden til `godkjent` eller `avslatt`, og
  beslutter ser en kvittering «Vedtak fattet».
- **Send i retur til saksbehandler:** beslutter velger én eller flere årsaker og
  skriver en forklaring. Saken går tilbake til `UNDER_BEHANDLING` med
  oppgavetypen «Søknad - retur». Saksbehandler ser årsakene og forklaringen over
  skjemaet for foreløpig vedtak, som er forhåndsutfylt med forrige innstilling.

Topartskontrollen håndheves når vedtaket fattes: den som sendte det foreløpige
vedtaket, kan ikke fatte det.

Dette kortet sender ikke vedtaksbrev, journalfører ikke, varsler ikke og
bestiller ikke midler. Varslingen bygges i et eget kort før lansering. Se
*Avgrensning*.

## Bakgrunn

Fra før:

- `Saksstatus` har `TIL_BESLUTNING`, `INNVILGET` og `AVSLATT`. Flyten i
  `sak-datamodell.md`: `TIL_BESLUTNING` → beslutter godkjenner → `INNVILGET`/`AVSLATT`,
  eller beslutter returnerer → `UNDER_BEHANDLING` med rad i `sak_retur`.
  Ingenting setter `INNVILGET` eller `AVSLATT` for saker med kilde
  `EKSPERTBISTAND` i dag.
- `forelopig_vedtak` (V21, `forelopig_vedtak.md`) er versjonert per sak og
  har `innstilt_av_ident`. `lagreForelopigVedtak` oppdaterer bare fra
  `UNDER_BEHANDLING` og lager versjon `max + 1`. Ny innsending etter retur
  virker dermed uten endring.
- `sak.foreslatt_utfall` settes av `LagreForelopigVedtak` (`INNVILGET`/`AVSLATT`).
- `sak_retur` finnes fra V12, men ingen skriver til den, og den har ingen
  Exposed-tabell. Kolonnene er `retur_id`, `sak_id`, `returnert_av_ident`,
  `til_saksbehandler_ident`, `aarsak TEXT NOT NULL` (én verdi), `forklaring TEXT
  NOT NULL` (≤ 1000 tegn) og `opprettet`. Den har ikke `event_id`, så en handler
  kan ikke være idempotent mot den. `ReturArsak` (`FEIL_REGELFORSTAELSE`,
  `FEIL_FAKTA`, `ANNET`) er beskrevet i `sak-datamodell.md`, men finnes ikke i
  koden.
- Beslutter tildeler seg saken med «Tildel meg» (`listevisning.md`,
  `TildelBeslutter`). `tildelBeslutter` i `Db.kt` sjekker habilitet mot
  `innstilt_av_ident` på siste versjon. `beslutterTildelingHindring` i
  `SakApi.kt` gir 403 `BESLUTTER_INHABIL`. `sjekkErBeslutterPåSak` i
  `Tilgangsjekker.kt` finnes, men brukes ikke.
- `Oppgavetype` har `SOKNAD` og `SOKNAD_BESLUTTER`, utledet bare av status.
  `rolleForOppgave` i frontend speiler rollen.
- `SakPage` viser det foreløpige vedtaket låst (`ForelopigVedtakOppsummering`,
  `modus="sendt"`) når status er `TIL_BESLUTNING`, også for beslutter. Det
  finnes ingen knapper for beslutter.
- `soknad.status` har `utkast`, `innsendt`, `godkjent` og `avlyst`. For
  Arena-saker settes `godkjent` av `SettGodkjentSoknadStatus`
  (`TilskuddsbrevJournalfoert`) og `avlyst` av `SettAvlystSoknadStatus`
  (`SoknadAvlystIArena`, «avslått eller trukket»). Begge setter
  `slette_tidspunkt` til nå + `slettSøknadOm`. Ingenting setter dem for saker med
  kilde `EKSPERTBISTAND`.
- `hentGodkjentSakIdForUpdate` krever `godkjent`. Refusjon og vedlegg fra
  arbeidsgiver går via den (`RefusjonDb`, `VedleggDb`), og det samme gjør
  `RefusjonApi` og `VedleggApi`. `godkjent` åpner dermed for refusjon.
- Arbeidsgivers søknadsliste viser `godkjent` som «Godkjent» og `avlyst` som
  «Trukket eller avslått». `KvitteringPage` henter tilskuddsbrev fra
  Arena-tilsagn når status er `godkjent`, og viser «Søknad trukket eller
  avslått» for `avlyst`. `server-schemas.ts` validerer status med en lukket
  `z.enum`, så en ny verdi må legges inn der. `soknad.status` har ingen
  CHECK i databasen.
- Vilkårsvurderingen kan bare endres i `UNDER_BEHANDLING`
  (`PATCH …/vilkarsvurdering` gir 409 ellers). Den er dermed låst etter vedtak.
- ADR-utkastet på grenen `adr_vedtak_klage` (`adr/0002-vedtak-og-klagebehandling.md`)
  skiller behandling, foreløpig vedtak og vedtak (B1), sier at beslutter fatter og
  at `Vedtak.fattetAv` er beslutteren (B2), at returnotatet skal bevares (B2), og
  at innstillinger og returer versjoneres (B3).

Relatert, ikke del av dette kortet:

- «Vedtak til deltakere og tilsagn til arbeidsgiver» (`6a8d564c1012dc836b9e2a1b`):
  endelig vedtaksbrev og tilsagnsbrev.
- «Varsle deltaker om vedtak» (`6aab96635bae2266d1c1396f`): varsling og
  revarsling av deltaker.
- `forelopig_vedtak_brev.md`: forhåndsvisning av brevene. Beslutter kan allerede
  åpne dem (`SakDokumentApi`, rolle `SAKSBEHANDLER` eller `BESLUTTER`).
- `integrasjon_oebs_tiltaksokonomi_bestilling.md` (A-4): eventene som utløser
  bestilling er utenfor den specen.

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-14.

| Node | Lenke | Viser |
|------|-------|-------|
| `224:18355` | [beslutter, vedtak](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=224-18355) | `SakPage` for beslutter. Høyre kolonne har fanene «Vilkårsvurdering» og «Vedtak» (valgt). Grønt infokort «Vurdering: Innvilge» med tilskudd, deltaker, arbeidsgiver og tiltaksperiode. Låst «Deltaker - tilpass begrunnelse» med hjelpetekst. Lenkene «Forhandvis foreløpig vedtak - deltakere» og «… - arbeidsgiver». Knappene «Send i retur til saksbehandler» (sekundær) og «Godkjenn» (primær). |
| `58:1795` | [vedtak fattet](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=58-1795) | Helside. Grønn hake, «Vedtak fattet», «Arbeidsgiver og deltaker vil bli varslet om vedtaket.» Oppsummering: beslutt (Innvilge), tilskudd, arbeidsgiver, deltakere. Lenken «Tilbake til liste av saker». |
| `226:19357` | [send i retur](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=226-19357) | `Modal` «Send i retur til saksbehandler», «Saksbehandler vil få beskjed om årsaker og forklaring til returen.» `CheckboxGroup` «Årsak for retur»: «Feil i regelforståelsen», «Feil i fakta», «Annet». `Textarea` «Forklaring» med teller «1000 tegn igjen». Knappene «Avbryt» og «Send i retur». |
| `137:23033` | [saksbehandler etter retur](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=137-23033) | Fanen «Foreløpig vedtak» for saksbehandler. Blått infokort «I retur av beslutter» med «Årsak for retur», «Kommentar» og «Beslutter» (navn). Under: skjemaet for foreløpig vedtak forhåndsutfylt med forrige innstilling, og «Forhåndvis foreløpig vedtak». |

Skissene har realistiske testdata (navn, fødselsnummer, organisasjonsnummer). De er
ikke kopiert hit.

Avvik mellom kort og skisse:

- **Flere årsaker.** Skissen har avkrysningsbokser, altså én eller flere årsaker.
  `sak_retur.aarsak` tar én verdi. Vi følger skissen og endrer kolonnen (*0*),
  bekreftet av Ken 2026-10-14.
- **«Kommentar» og «Forklaring».** Modalen kaller feltet «Forklaring», infokortet
  kaller det «Kommentar». Vi bruker «Forklaring» begge steder.
- **«Arbeidsgiver og deltaker vil bli varslet om vedtaket.»** Ingen varsling
  bygges i dette kortet. Teksten vises likevel, fordi varslingen bygges i et
  eget kort før lansering (Ken, 2026-10-14).
- **Forhåndsvisningslenker.** Skissen har lenker. Vi bruker brev-«pills» fra
  `forelopig_vedtak_brev.md`, som allerede vises i `ForelopigVedtakOppsummering`.
- **Stavefeil i skissen.** «Forhandvis», «Forhåndvis». Vi skriver «Forhåndsvis».

## Beslutninger

Kortet har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-10-14).

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen; Figma `224:18355`, `58:1795` | Beslutter har to valg: godkjenne (fatte vedtak) eller sende i retur. Godkjenning gir en kvittering «Vedtak fattet». |
| Kortbeskrivelsen; Figma `226:19357` | Retur krever årsak og har fritekstfelt for forklaring. |
| Kortbeskrivelsen | Etter retur vises saken i «Oppgaver som venter» med oppgavetypen «Søknad - retur». En saksbehandler kan ta den, og opprinnelig saksbehandler står fortsatt på saken. |
| Kortbeskrivelsen; Figma `137:23033` | Saksbehandler ser beslutters notat sammen med opprinnelig vilkårsvurdering og foreløpig vedtak. |
| Ken, 2026-10-12 (`listevisning.md`) | «Søknad - retur» kommer med beslutterflyten, utledet av `sak_retur`, for `UNDER_BEHANDLING`. Retur nullstiller ikke `beslutter_ident`, så saken går tilbake til samme beslutter. Har beslutteren selv sendt det nye foreløpige vedtaket, får andre besluttere «Tildel meg», og beslutterkortet hindrer at hen fatter vedtaket. |
| Ken, 2026-10-08 (`tildel_meg_sak.md`, `sak-datamodell.md`) | Topartskontrollen: den som sendte saken til `TIL_BESLUTNING`, kan ikke beslutte den. |
| Ken, 2026-10-06 (minne) | API-et publiserer en event, og en handler gjør endringen. Saksloggen skrives via `SakOppdatert` og `SkrivSakslogg`. |
| Ken, 2026-10-10 (minne) | `SakProjection` backfiller bare Arena-saker i prod og får ikke de nye eventene. |
| Ken, 2026-10-09 (`forelopig_vedtak.md`) | Arena-saker saksbehandles ikke hos oss. Vedtak og retur gir 409 `SAK_UGYLDIG_KILDE`. |
| ADR-utkast B1–B3 | Vedtaket er et eget begrep, fattet av beslutter. Både innstillingen og returnotatet bevares. |
| Ken, 2026-10-14 | Retur kan ha flere årsaker, minst én. |
| Ken, 2026-10-14 | Forklaring er påkrevd ved retur, uansett årsak. |
| Ken, 2026-10-14 | Kvitteringen viser «Arbeidsgiver og deltaker vil bli varslet om vedtaket.» Varslingen bygges i et eget kort, før lansering. |
| Ken, 2026-10-14 | Dette kortet oppdaterer søknaden etter vedtak: en handler lytter på `VedtakFattet` og setter `soknad.status`. Se *4b*. |
| Ken, 2026-10-14 | Avslag gir ny søknadsstatus `avslatt`, ikke `avlyst`. `avlyst` fra Arena betyr enten avslått eller trukket av arbeidsgiver, og vi vet ikke hvilket. I ny løsning er avslått avslått. Trekking får eventuelt en egen status senere, lagt til eksplisitt. Se *4c*. |
| Ken, 2026-10-14; Figma `224:18355`, `58:1795` | «Godkjenn» fatter vedtaket direkte, uten bekreftelsessteg. |
| Ken, 2026-10-14 | Fanen heter «Vedtak» for tildelt beslutter og når vedtak er fattet, ellers «Foreløpig vedtak». |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Beslutter må være tildelt saken** (`sjekkErBeslutterPåSak`, 403
  `IKKE_TILDELT_SAK`). Det speiler saksbehandlers krav, og knappene vises bare for
  tildelt beslutter.
- **Egen tabell `vedtak`**, ikke kolonner på `forelopig_vedtak`. ADR-en skiller
  innstilling og vedtak (B1), og klagen skal ha vedtaket som objekt. Prisen er en
  tabell til. Kolonner på `forelopig_vedtak` ble forkastet fordi de blander
  saksbehandlers innstilling med beslutters avgjørelse.
- **Versjonssjekk.** Beslutter sender versjonen av det foreløpige vedtaket hen
  så. Er den ikke siste versjon, gir API-et 409 `FORELOPIG_VEDTAK_ENDRET`. Det
  hindrer at beslutter godkjenner en innstilling hen ikke har sett.
- **Maks sats sjekkes ikke på nytt.** Satsen velges etter tiltaksperiodens
  startdato, og en ny sats gjelder bare fra sin `gyldigFra` (`forelopig_vedtak.md`
  *2b*). Et sendt beløp kan derfor ikke bli ugyldig før vedtak.
- **`foreslatt_utfall` nullstilles ved retur.** Den beskriver en innstilling som
  ligger hos beslutter, og settes på nytt ved neste innsending.
- **Skjemaet forhåndsutfylles etter retur** med siste versjon: utfall, beløp,
  tiltaksperiode og begrunnelser.
- **Søknadsstatus settes i en egen handler**, ikke i `FattVedtak`. Handleren
  sjekker at `FattVedtak` lagret vedtaket for samme event (*4b*).

## Avgrensning

Ikke i dette kortet:

- Endelig vedtaksbrev og tilsagnsbrev, med beslutters navn og Arena-felter som
  tilsagnsnummer og refusjonsfrist. Det er «Vedtak til deltakere og tilsagn til
  arbeidsgiver» (<https://trello.com/c/3moZGD2U>).
- Journalføring av brevene: «Journalfør vedtaksbrev og tilsagnsbrev»
  (<https://trello.com/c/4qcIiWBB>).
- Utsending av brevene: «Send ut vedtaksbrev og tilsagnsbrev»
  (<https://trello.com/c/6l4XA2H5>).
- Varsling av deltaker og arbeidsgiver: «Varsle deltaker om vedtak»
  (<https://trello.com/c/QA5bXVrf>) og «Varsle arbeidsgiver om vedtak»
  (<https://trello.com/c/XZvrwKjG>). Begge bygges før lansering.
- Avsetting av penger i OEBS når vedtaket er innvilget: «Sette av penger i OEBS
  når vedtak er fattet» (<https://trello.com/c/abzeX7gO>) lytter på
  `VedtakFattet`. Integrasjonsdetaljene hører til «Integrere med OEBS for å
  sette av penger og utbetale» (<https://trello.com/c/m53Ud4QY>).
- Frysing av vilkårsvurderingen som egen kopi (ADR B5). Statusen låser den, men
  vi tar ikke et øyeblikksbilde.
- Vedtak på refusjon og klage (ADR B6, B7).
- Frigjøring av beslutter og «Tildel beslutter»: «Frigjør beslutter og tildel
  annen beslutter» (<https://trello.com/c/Nouutlp7>).
- Statistikk over antall returer (ADR B3): «Statistikk over antall returer»
  (<https://trello.com/c/NojzDsa9>). Dataene finnes i `sak_retur`.

## Tilnærming

```
POST …/vedtak ── publishEventQueue(VedtakFattet) ──> event_queue
                                                         │
FattVedtak ── fattVedtak(...) + publishEventQueue(SakOppdatert)
              ├─ habilitet + status + versjon sjekkes på nytt
              ├─ INSERT vedtak
              └─ UPDATE sak: status = INNVILGET | AVSLATT

SettSoknadStatusVedtakFattet (samme event) ── bare når vedtak-raden for eventen finnes
              └─ UPDATE soknad: status = godkjent | avslatt, slette_tidspunkt

POST …/retur ── publishEventQueue(ForelopigVedtakReturnert) ──> event_queue
                                                         │
ReturnerForelopigVedtak ── returnerForelopigVedtak(...) + publishEventQueue(SakOppdatert)
              ├─ status + versjon sjekkes på nytt
              ├─ INSERT sak_retur
              └─ UPDATE sak: status = UNDER_BEHANDLING, foreslatt_utfall = NULL
                             (saksbehandler_ident og beslutter_ident uendret)
                                                         │
SkrivSakslogg ── INSERT sakslogg
```

`SakProjection` får ingen av eventene.

### 0. Migrering

`V24__vedtak_og_retur.sql`:

```sql
CREATE TABLE IF NOT EXISTS vedtak (
    id                  BIGSERIAL PRIMARY KEY,
    sak_id              UUID        NOT NULL UNIQUE REFERENCES sak (sak_id),
    forelopig_vedtak_id BIGINT      NOT NULL UNIQUE REFERENCES forelopig_vedtak (id),
    utfall              TEXT        NOT NULL CHECK (utfall IN ('INNVILGET', 'AVSLATT')),
    fattet_av_ident     TEXT        NOT NULL,
    fattet_av_navn      TEXT        NOT NULL,
    fattet_tidspunkt    TIMESTAMPTZ NOT NULL,
    event_id            BIGINT      NOT NULL UNIQUE
);

ALTER TABLE sak_retur DROP COLUMN aarsak;
ALTER TABLE sak_retur
    ADD COLUMN aarsaker TEXT[] NOT NULL
        CHECK (cardinality(aarsaker) >= 1
           AND aarsaker <@ ARRAY ['FEIL_REGELFORSTAELSE', 'FEIL_FAKTA', 'ANNET']::TEXT[]),
    ADD COLUMN forelopig_vedtak_id BIGINT NOT NULL REFERENCES forelopig_vedtak (id),
    ADD COLUMN returnert_av_navn   TEXT   NOT NULL,
    ADD COLUMN event_id            BIGINT NOT NULL UNIQUE;
```

`sak_retur` er tom i alle miljøer, fordi ingen skriver til den. `NOT NULL` på nye
kolonner er derfor trygt. `sak_id UNIQUE` på `vedtak` gjelder søknadsvedtaket.
Refusjons- og klagevedtak (ADR B6, B7) må endre den når de kommer.

`til_saksbehandler_ident` settes til `sak.saksbehandler_ident` da returen ble
lagret. `opprettet` settes fra eventens tidspunkt.

Exposed-tabellene `VedtakTable` og `SakReturTable` legges i `saksbehandling/Db.kt`.
`aarsaker` blir `array<String>("aarsaker")`. Ingen migreringstest.

### 1. Nye eventer

I `Events.kt`, etter `ForelopigVedtakSendtTilBeslutter`:

```kotlin
@SerialName("vedtakFattet")
data class VedtakFattet(
    val sakId: String,
    val soknadId: String,
    val forelopigVedtakVersjon: Int,
    val utfall: Saksstatus, // INNVILGET eller AVSLATT, kontrollert i init
    val fattetAvIdent: String,
    val fattetAvNavn: String,
    val tidspunkt: Instant,
) : EventData

@SerialName("forelopigVedtakReturnert")
data class ForelopigVedtakReturnert(
    val sakId: String,
    val soknadId: String,
    val forelopigVedtakVersjon: Int,
    val aarsaker: Set<ReturArsak>,   // ikke tom, kontrollert i init
    val forklaring: String,          // trimmet, 1–1000 tegn
    val returnertAvIdent: String,
    val returnertAvNavn: String,
    val tidspunkt: Instant,
) : EventData
```

`aggregateRootId` er `soknadId`. Navnene kommer fra `principal.navn`, som ved
tildeling. Identer og navn logges bare i teamLog. Forklaringen logges ikke, den
kan inneholde helseopplysninger.

`ReturArsak` legges i `saksbehandling/Db.kt` ved siden av `ForelopigUtfall`.

### 2. Backend: API

Nye ruter i `BeslutterApi.kt` (ny fil i `saksbehandling/`), registrert ved siden
av `ForelopigVedtakApi`:

| Metode | Sti | Kropp | Svar |
|--------|-----|-------|------|
| `POST` | `/api/saksbehandling/v1/saker/{sakId}/vedtak` | `{ "forelopigVedtakVersjon": 2 }` | 202 |
| `POST` | `/api/saksbehandling/v1/saker/{sakId}/retur` | `{ "forelopigVedtakVersjon": 2, "aarsaker": ["FEIL_FAKTA"], "forklaring": "…" }` | 202 |

Rekkefølgen følger `ForelopigVedtakApi`:

1. `principalMedRolle(Role.BESLUTTER)`, ellers 403.
2. `sakIdParameter`, `hentSakTilgangsgrunnlag` (404), `sjekkTilgangTilEnhet`,
   `sjekkTilgangsmaskin` (`KJERNE`).
3. Kilde `ARENA` gir 409 `SAK_UGYLDIG_KILDE`.
4. `sjekkErBeslutterPåSak`, ellers 403 `IKKE_TILDELT_SAK`.
5. `call.receive<…>().also { valider(it) }`. Brudd gir 400.
   - Retur: `aarsaker` ikke tom, `forklaring` ikke blank, maks 1000 tegn etter trim.
6. I én transaksjon med `FOR UPDATE` på saken:
   - status ulik `TIL_BESLUTNING` gir 409 `SAK_UGYLDIG_STATUS`
   - `forelopigVedtakVersjon` ulik siste versjon gir 409 `FORELOPIG_VEDTAK_ENDRET`
   - **bare vedtak:** `innstiltAvIdent` på siste versjon lik innlogget, eller null,
     gir 403 `BESLUTTER_INHABIL`
   - `publishEventQueue(...)`
7. 202.

🔴 Rød sone: topartskontrollen. Sjekken er fail-closed (null `innstiltAvIdent`
avvises) og gjøres både i API og handler, fordi en ny innstilling kan komme mellom
dem. Retur sjekker ikke habilitet: å sende tilbake fatter ikke noe vedtak.

`SakDetaljer` får to nye felter:

```kotlin
val sisteRetur: ReturDTO?   // nyeste rad i sak_retur
val vedtak: VedtakDTO?      // raden i vedtak, når fattet

data class ReturDTO(
    val forelopigVedtakVersjon: Int,
    val aarsaker: List<ReturArsak>,
    val forklaring: String,
    val returnertAvNavn: String,
    val tidspunkt: Instant,
)

data class VedtakDTO(
    val utfall: Saksstatus,
    val forelopigVedtakVersjon: Int,
    val fattetAvNavn: String,
    val fattetTidspunkt: Instant,
)
```

`sisteRetur` vises i frontend bare når oppgavetypen er `SOKNAD_RETUR`. Eldre
returer ligger i saksloggen.

### 3. Backend: oppgavetype

```kotlin
enum class Oppgavetype { SOKNAD, SOKNAD_RETUR, SOKNAD_BESLUTTER }

fun oppgavetype(status: Saksstatus, harRetur: Boolean): Oppgavetype? = when (status) {
    OPPRETTET -> SOKNAD
    UNDER_BEHANDLING -> if (harRetur) SOKNAD_RETUR else SOKNAD
    TIL_BESLUTNING -> SOKNAD_BESLUTTER
    INNVILGET, AVSLATT, AVSLUTTET -> null
}
```

`SOKNAD_RETUR.rolle` er `SAKSBEHANDLER`. Saken kan bare være i `UNDER_BEHANDLING`
med rad i `sak_retur` etter en retur, så `EXISTS` holder. Listen henter
`harRetur` for alle saker i ett oppslag (`sak_id IN (…)`), som
`hentSisteForelopigVedtakSammendrag`. `beslutterTildelingHindring` og
`kanTildeleMegSomSaksbehandler` virker uendret, fordi de går via `rolle`.

### 4. Backend: handlere

`FattVedtak` på `VedtakFattet` og `ReturnerForelopigVedtak` på
`ForelopigVedtakReturnert`. Mønsteret er `LagreForelopigVedtak`:
`IdempotencyGuard`, én transaksjon, domenefunksjonen i `Db.kt`, så `SakOppdatert`
med `utfortAvRolle = BESLUTTER`. Feil gir rollback og `transientError`.

`fattVedtak(data, eventId): Boolean`, med `FOR UPDATE` på saken:

- status ulik `TIL_BESLUTNING`, versjon ulik siste versjon, eller
  `innstilt_av_ident` lik `fattetAvIdent`: ingen endring, `false`. Inhabil
  logges som `log.error` uten ident, fordi API-et skulle ha stoppet den.
- ellers `INSERT vedtak` (`insertIgnore` på `event_id`), `sak.status = utfall`,
  `sist_endret`. `true`.

`returnerForelopigVedtak(data, eventId): Boolean`:

- status ulik `TIL_BESLUTNING` eller versjon ulik siste versjon: ingen endring, `false`.
- ellers `INSERT sak_retur`, `sak.status = UNDER_BEHANDLING`,
  `foreslatt_utfall = NULL`, `sist_endret`. `saksbehandler_ident`,
  `beslutter_ident` og navnene endres ikke. `true`.

`SakOppdatert` publiseres bare ved `true`. Notat i saksloggen:

| Hendelse | Notat |
|----------|-------|
| Vedtak, innvilget | «Vedtak fattet: innvilget» |
| Vedtak, avslått | «Vedtak fattet: avslått» |
| Retur | «Sendt i retur til saksbehandler» |

Forklaringen står ikke i saksloggen.

Begge registreres i `configureEventHandlers` for alle miljøer.

### 4b. Backend: søknadsstatus etter vedtak

Ny handler `SettSoknadStatusVedtakFattet` på `VedtakFattet`, registrert etter
`FattVedtak` for alle miljøer. Mønsteret er `SettGodkjentSoknadStatus`, med én
transaksjon og `IdempotencyGuard`.

🔴 Rød sone: `godkjent` åpner for refusjon og vedlegg fra arbeidsgiver
(`hentGodkjentSakIdForUpdate`). Søknaden skal derfor bare oppdateres når
`FattVedtak` lagret vedtaket. Handlerne kjører hver for seg, og
`FattVedtak` kan avvise eventen (retur kom først, ny versjon, inhabil). Handleren
stoler derfor ikke på at den andre handleren har kjørt, og slår opp vedtaket:

1. `vedtak` med `event_id = event.id` finnes:
   `UPDATE soknad SET status = …, slette_tidspunkt = now + slettSøknadOm
   WHERE id = soknadId AND status = 'innsendt'`.
   - `INNVILGET` gir `godkjent`, `AVSLATT` gir `avslatt`.
   - 0 rader og søknaden finnes ikke: `unrecoverableError`, som
     `SettGodkjentSoknadStatus`.
   - 0 rader og søknaden har allerede en annen status enn `innsendt`: `success`
     uten endring. Det skjer ved ny kjøring av samme event.
2. Vedtaket finnes ikke, men `FattVedtak` ville fortsatt lagret eventen
   (`vedtakKanFattes`: `TIL_BESLUTNING`, samme versjon og habil beslutter):
   `FattVedtak` har ikke kjørt ferdig. `transientError`, og eventen prøves
   igjen. Habiliteten er med, ellers ville en inhabil event bli prøvd igjen i
   det uendelige.
3. Vedtaket finnes ikke, og `FattVedtak` ville avvist eventen: `success` uten
   endring.

Handleren publiserer ikke `SakOppdatert`. Saksloggen har allerede linjen fra
`FattVedtak`.

Alternativet var å oppdatere søknaden i samme transaksjon i `FattVedtak`. Det
er enklere, men blander saksbehandling og søknad i én handler. Ken valgte egen
handler (2026-10-14).

### 4c. Ny søknadsstatus `avslatt`

`avlyst` fra Arena kan bety både avslått og trukket. `avslatt` betyr bare at Nav
har avslått søknaden (Ken, 2026-10-14). `avlyst` beholdes uendret for
Arena-saker.

Backend:

- `SoknadStatus` i `soknad/Api.kt` får `avslatt`. Verdien lagres som tekst, og
  `soknad.status` har ingen CHECK, så det trengs ingen migrering.
- `godkjent`-sjekkene for refusjon og vedlegg er uendret. `avslatt` åpner ikke
  for refusjon.

Arbeidsgivers frontend (`frontend/client`):

- `server-schemas.ts`: `z.enum([..., "avslatt"])`. Uten dette feiler parsing av
  søknaden.
- `soknader.ts`, `resolveStatusTag`: `avslatt` gir «Avslått» med `variant: "error"`.
- `KvitteringPage.tsx`: egen boks for `avslatt` med overskriften «Søknad avslått»
  og teksten «Du får vedtaket med begrunnelse og informasjon om hvordan du kan
  klage på det.» Den eksisterende boksen for `avlyst` endres ikke.
  PR-beskrivelsen ber om gjennomgang av ordlyden.
- `mocks/handlers.ts`: en søknad med status `avslatt`.

Saksbehandlingsfrontend: typen `SoknadStatus` i `useSaker.ts` får `"avslatt"`.

### 5. Frontend: hook og typer

- `useSak.ts`: `SakDetaljer` får `sisteRetur` og `vedtak`, `ReturArsak`-typen.
- `useSaker.ts`: `Oppgavetype` får `"SOKNAD_RETUR"`, `rolleForOppgave` gir
  `"saksbehandler"` for den.
- `OversiktPage.tsx`: etiketten «Søknad - retur».
- Ny `useBeslutning.ts` med `fattVedtak(versjon)` og `returner(input)`, bygd som
  `useForelopigVedtak`: `isSaving`, `error`, feilmeldinger per 409-kode,
  revalidering av sak og sakslogg.
- `constants.ts`: `SAKSBEHANDLING_VEDTAK_URL` og `SAKSBEHANDLING_RETUR_URL`.

### 6. Frontend: beslutter

I `ForelopigVedtakFane` (`SakPage.tsx`), når status er `TIL_BESLUTNING` og
innlogget er tildelt beslutter (`sak.beslutterIdent`):

- `ForelopigVedtakOppsummering` som i dag, med knappene «Send i retur til
  saksbehandler» (`secondary`) og «Godkjenn» (`primary`) under.
- Kan ikke innlogget fatte vedtaket (`innstiltAvIdent` er innlogget), vises en
  `Alert` i stedet for «Godkjenn»: «Du sendte det foreløpige vedtaket og kan
  ikke fatte vedtaket.» «Send i retur» vises fortsatt.

Andre ser oppsummeringen uten knapper, som i dag.

Nye komponenter i `components/beslutning/`:

- `ReturModal.tsx`: Aksel `Modal` med `CheckboxGroup` «Årsak for retur» (påkrevd),
  `Textarea` «Forklaring» (`maxLength={1000}`, påkrevd), «Avbryt» og «Send i retur».
  Feilmeldinger fra validering og API vises i modalen.
- `VedtakKvittering.tsx`: helside som `ForelopigVedtakKvittering`. Grønn hake,
  «Vedtak fattet», «Arbeidsgiver og deltaker vil bli varslet om vedtaket.»,
  oppsummering med vedtak (innvilge/avslå), tilskudd, arbeidsgiver og deltakere,
  og «Tilbake til liste av saker».
- `ReturInfoKort.tsx`: blått infokort «I retur av beslutter» med årsakene,
  forklaringen og navnet på beslutter.

`SakPage` holder kvitteringstilstanden som for foreløpig vedtak, og skjuler
tildeling og sakslogg mens kvitteringen vises. Etter vedtak viser fanen
«Vedtak» oppsummeringen låst, med «Vedtak fattet av <navn> <dato>».

### 7. Frontend: saksbehandler etter retur

Når oppgavetypen er `SOKNAD_RETUR`:

- `ReturInfoKort` øverst i fanen «Foreløpig vedtak», for alle som ser saken.
- Skjemaet for tildelt saksbehandler starter fra siste versjon:
  `tilstandFraForelopigVedtak(sak.forelopigVedtak, …)` i `forelopigVedtak.ts`
  fyller utfall, beløp, tiltaksperiode og begrunnelser. Uten retur brukes
  `startTilstand` som før.
- Fanen «Foreløpig vedtak» velges som standard, slik at returen synes med en gang.

### 8. MSW-mock

`mocks/handlers.ts`:

- `POST …/vedtak` og `POST …/retur` oppdaterer mock-saken og gir 202.
- En mock-sak i `TIL_BESLUTNING` der mock-brukeren er beslutter, og en der
  mock-brukeren sendte det foreløpige vedtaket (inhabil).
- En mock-sak i `UNDER_BEHANDLING` med `sisteRetur`.
- `oppgavetype` i mocken gir `SOKNAD_RETUR`.

### 9. Dokumentasjon

- `sak-datamodell.md`: tabellen `vedtak`, endret `sak_retur`, `ReturArsak`,
  oppgavetypen `SOKNAD_RETUR`, flyten «Bygd».
- `listevisning.md` og `tildeling_detaljvisning.md`: «Søknad - retur» er bygd.
- `event/README.md`: de to eventene og handlerne.
- `event-flow.md`: generert med `EventFlowDiagram.kt`.

### 10. Tester

`kotlin.test`, `testApplicationWithDatabase`, `MockAzureAdIntrospector`.

`BeslutterApiTest.kt`:

- vedtak og retur gir 202 og publiserer riktig event, uten å endre `sak`
- rolle saksbehandler gir 403
- beslutter som ikke er tildelt gir 403 `IKKE_TILDELT_SAK`
- vedtak når innlogget sendte det foreløpige vedtaket gir 403 `BESLUTTER_INHABIL`;
  retur i samme situasjon gir 202
- status ulik `TIL_BESLUTNING` gir 409 `SAK_UGYLDIG_STATUS`
- kilde `ARENA` gir 409 `SAK_UGYLDIG_KILDE`
- gammel versjon gir 409 `FORELOPIG_VEDTAK_ENDRET`
- retur uten årsak, med blank eller for lang forklaring gir 400

`FattVedtakTest.kt` og `ReturnerForelopigVedtakTest.kt`:

- status, `vedtak`/`sak_retur` og `SakOppdatert` som beskrevet
- samme event to ganger gir én rad og én sakslogglinje
- utdatert status eller versjon gir ingen endring
- inhabil i handleren gir ingen endring
- retur beholder `saksbehandler_ident` og `beslutter_ident`
- retur, nytt foreløpig vedtak og vedtak gir versjon 2 og vedtak på versjon 2

`SettSoknadStatusVedtakFattetTest.kt`:

- innvilget gir `godkjent`, avslått gir `avslatt`, begge med `slette_tidspunkt`
- vedtak avvist av `FattVedtak` (retur først, inhabil) gir ingen endring på søknaden
- `FattVedtak` ikke kjørt, saken i `TIL_BESLUTNING`: `TransientError`, og etter
  `FattVedtak` gir ny kjøring `godkjent`
- søknad som ikke er `innsendt`, endres ikke

`OppgavetypeTest.kt`: `SOKNAD_RETUR` for `UNDER_BEHANDLING` med retur.
`SaksbehandlingSakApiTest.kt`: `sisteRetur`, `vedtak` og oppgavetypen i liste og
detalj. `TestEventData.allEventSamples` og `EventDataAggregateRootIdTest` får de
to eventene.

`frontend/client`: `schema.test.ts` får et tilfelle der `status: "avslatt"`
parses. Ny `soknader.test.ts` sjekker at `resolveStatusTag` gir «Avslått» for
`avslatt` og «Trukket eller avslått» for `avlyst`.

Frontend: `pnpm typecheck` og `pnpm lint` i begge frontender, `pnpm test:run` i
`frontend/client`, og manuell test mot MSW.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V24__vedtak_og_retur.sql` | Ny |
| `backend/.../saksbehandling/Db.kt` | `VedtakTable`, `SakReturTable`, `ReturArsak`, `fattVedtak`, `returnerForelopigVedtak`, oppslag for retur og vedtak |
| `backend/.../saksbehandling/BeslutterApi.kt` | Ny: `POST …/vedtak`, `POST …/retur` |
| `backend/.../saksbehandling/SakApi.kt` | `sisteRetur`, `vedtak`, `harRetur` til `oppgavetype` |
| `backend/.../saksbehandling/Oppgavetype.kt` | `SOKNAD_RETUR`, ny signatur |
| `backend/.../event/Events.kt` | `VedtakFattet`, `ForelopigVedtakReturnert`, registrering |
| `backend/.../event/handlers/FattVedtak.kt` | Ny |
| `backend/.../event/handlers/ReturnerForelopigVedtak.kt` | Ny |
| `backend/.../event/handlers/SettSoknadStatusVedtakFattet.kt` | Ny |
| `backend/.../soknad/Api.kt` | `SoknadStatus.avslatt` |
| `backend/.../event/README.md`, `event-flow.md` | Oppdatert / generert |
| `backend/src/test/.../saksbehandling/BeslutterApiTest.kt` | Ny |
| `backend/src/test/.../event/handlers/FattVedtakTest.kt`, `ReturnerForelopigVedtakTest.kt`, `SettSoknadStatusVedtakFattetTest.kt` | Nye |
| `backend/src/test/.../saksbehandling/OppgavetypeTest.kt`, `SaksbehandlingSakApiTest.kt` | Utvidet |
| `backend/src/test/.../event/TestEventData.kt`, `EventDataAggregateRootIdTest.kt` | Nye eksempler |
| `frontend/saksbehandling/src/hooks/useSak.ts`, `useSaker.ts` | Typer, `SOKNAD_RETUR`, `SoknadStatus` med `"avslatt"` |
| `frontend/client/src/features/soknad/server-schemas.ts` | `"avslatt"` i status-enum |
| `frontend/client/src/features/soknad/soknader.ts` | `resolveStatusTag`: «Avslått» |
| `frontend/client/src/pages/KvitteringPage.tsx` | Boks «Søknad avslått» |
| `frontend/client/src/mocks/handlers.ts` | Mock-søknad med `avslatt` |
| `frontend/client/src/features/soknad/schema.test.ts` | Parsing av `avslatt` |
| `frontend/client/src/features/soknad/soknader.test.ts` | Ny: statusetiketter |
| `frontend/saksbehandling/src/hooks/useBeslutning.ts` | Ny |
| `frontend/saksbehandling/src/utils/constants.ts` | URL-er |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Knapper, kvittering, fanenavn, retur |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | Etikett «Søknad - retur» |
| `frontend/saksbehandling/src/components/beslutning/*` | `ReturModal`, `VedtakKvittering`, `ReturInfoKort` |
| `frontend/saksbehandling/src/components/forelopigVedtak/forelopigVedtak.ts` | `tilstandFraForelopigVedtak` |
| `frontend/saksbehandling/src/mocks/handlers.ts` | POST-er, mock-saker |
| `specifications/sak-datamodell.md`, `listevisning.md`, `tildeling_detaljvisning.md` | Oppdatert |

## Kanttilfeller og feilmodus

- **Dobbeltklikk på «Godkjenn».** Knappen er `loading`. Kommer to kall, publiseres
  to eventer. Den første setter `INNVILGET`, den andre ser feil status og gjør
  ingenting. Én rad og én sakslogglinje.
- **Retur og godkjenning samtidig** (to faner). Den som behandles først, vinner.
  Den andre gjør ingenting, og frontend viser ny status etter revalidering.
- **Ny innstilling mellom visning og klikk.** Kan bare skje etter retur, og da er
  status `UNDER_BEHANDLING` (409). Versjonssjekken fanger resten.
- **Beslutter tar over en sak der hen selv sendte det nye foreløpige vedtaket.**
  `tildelBeslutter` avviser det. Står hen fortsatt som beslutter etter retur og
  egen innsending, gir «Godkjenn» 403 `BESLUTTER_INHABIL`, og frontend viser
  forklaringen. Andre besluttere kan ta saken (`listevisning.md`).
- **`innstilt_av_ident` mangler.** Skal ikke skje. Fail-closed: 403 i API,
  ingen endring i handler.
- **Saksbehandler frigjort før retur.** `saksbehandler_ident` er null, og saken
  ligger i «Oppgaver som venter» som «Søknad - retur» med «Tildel meg».
  `til_saksbehandler_ident` blir null.
- **En annen saksbehandler tar saken etter retur.** Vanlig «Overta som
  saksbehandler». Returen og skjemaet vises for den nye saksbehandleren.
- **Flere returer.** Hver retur får sin rad. Fanen viser bare siste, saksloggen
  viser alle.
- **Vilkår etter vedtak.** `PATCH …/vilkarsvurdering` gir 409. Etter retur kan
  vilkårene endres igjen.
- **Arena-saker.** 409 `SAK_UGYLDIG_KILDE`, knappene vises ikke.
- **Retur og godkjenning samtidig, søknaden.** Vinner returen, avviser
  `FattVedtak` eventen, og søknaden står fortsatt som `innsendt` (*4b*, punkt 3).
- **Arbeidsgiver etter innvilgelse.** Søknaden vises som «Godkjent», og
  arbeidsgiver kan sende refusjon. `KvitteringPage` henter tilskuddsbrev fra
  Arena-tilsagn og får en tom liste, fordi saken ikke har tilsagn i Arena.
  Tilsagnsbrevet kommer med kortet «Vedtak til deltakere og tilsagn til
  arbeidsgiver», før lansering.
- **Arbeidsgiver etter avslag.** Søknaden vises som «Avslått». Ingen
  varsel før «Varsle arbeidsgiver om vedtak» er bygd.
- **Arena-saker som avlyses.** Får fortsatt `avlyst` og «Trukket eller avslått».
- **Eldre frontend i nettleseren.** En arbeidsgiver med en gammel versjon av
  appen åpen får en parsingfeil på `avslatt` til siden lastes på nytt. Vedtak
  fattes bare fra saksbehandling, som bare er deployet i dev, så det rammer
  ingen i prod ennå.
- **Personopplysninger.** Forklaringen kan inneholde helseopplysninger. Den logges
  ikke og står ikke i saksloggen. Identer og navn logges bare i teamLog.

## Åpne spørsmål

Ingen. Spørsmålene er besvart av Ken 2026-10-14 og flyttet til *Beslutninger*,
*Avgrensning* og *4b*.

## Ferdig når

- [x] API-ene publiserer `VedtakFattet` og `ForelopigVedtakReturnert` og skriver ikke selv til `sak`, `vedtak` eller `sak_retur`
- [x] Bare tildelt beslutter med tilgang (enhet, Tilgangsmaskinen `KJERNE`) kan fatte vedtak eller returnere, bare fra `TIL_BESLUTNING`, bare på siste versjon, og bare på saker med kilde `EKSPERTBISTAND`
- [x] Den som sendte det foreløpige vedtaket kan ikke fatte det, sjekket i API og handler, fail-closed
- [x] Vedtak setter `INNVILGET`/`AVSLATT` og lagrer en rad i `vedtak` med beslutter og versjon, idempotent
- [x] `SettSoknadStatusVedtakFattet` setter søknaden til `godkjent`/`avslatt` bare når vedtaket er lagret for samme event, idempotent
- [x] `SoknadStatus.avslatt` finnes, og arbeidsgiver ser «Avslått» i listen og «Søknad avslått» i kvitteringen. `avlyst` er uendret
- [x] Retur setter `UNDER_BEHANDLING`, nullstiller `foreslatt_utfall`, beholder saksbehandler og beslutter, og lagrer årsaker og forklaring i `sak_retur`, idempotent
- [x] Oppgavetypen «Søknad - retur» vises i listen og på saken, og kan tas av saksbehandler
- [x] Beslutter ser «Godkjenn» (uten bekreftelsessteg) og «Send i retur til saksbehandler», returmodalen med påkrevd årsak (én eller flere) og forklaring, og kvitteringen «Vedtak fattet» med varslingsteksten
- [x] Saksbehandler ser «I retur av beslutter» og et skjema forhåndsutfylt med forrige innstilling, og kan sende på nytt
- [x] Vedtak og retur vises i saksloggen, uten forklaringen
- [x] Forklaringen logges ikke
- [x] `sak-datamodell.md`, `listevisning.md`, `tildeling_detaljvisning.md`, `event/README.md` og `event-flow.md` er oppdatert
- [x] `mvn -B package` er grønn
- [x] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
- [x] `pnpm typecheck`, `pnpm lint` og `pnpm test:run` i `frontend/client` er grønne
