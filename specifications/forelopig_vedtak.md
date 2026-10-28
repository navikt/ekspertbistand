# Fatte foreløpig vedtak og sende til beslutter

Trello: https://trello.com/c/M6Ei4UAR/621-saksbehandler-skal-kunne-fatte-forel%C3%B8pig-vedtak-og-sende-til-beslutter
Kort-ID: `6ac34fb776f5fd66b1412f37`

## Mål

Når alle vilkår er vurdert, skal saksbehandleren som har saken kunne fatte et
foreløpig vedtak og sende saken til beslutter. Utfallet er ett av tre:

- **Innvilge:** tiltaksperiode og begrunnelse til deltaker.
- **Innvilge med redusert beløp:** tiltaksperiode, nytt beløp og begrunnelse til
  deltaker og arbeidsgiver.
- **Avslå:** begrunnelse til deltaker og arbeidsgiver.

Saken går til `TIL_BESLUTNING`. Det foreløpige vedtaket lagres med hvem som
sendte det, slik at beslutterkortet kan håndheve topartskontrollen. Fanen
«Foreløpig vedtak» på `SakPage` er i dag en plassholder.

Dette kortet bygger ikke beslutterens side (godkjenne, returnere), vedtaksbrev
eller tilskuddsbrev. Se *Avgrensning*.

## Bakgrunn

Fra før:

- `Saksstatus` har `UNDER_BEHANDLING`, `TIL_BESLUTNING`, `INNVILGET` og
  `AVSLATT`. Flyten i `sak-datamodell.md`: `UNDER_BEHANDLING` → (sender til
  godkjenning) `TIL_BESLUTNING` → beslutter godkjenner eller returnerer.
  Ingenting setter `TIL_BESLUTNING` i dag.
- `sak.foreslatt_utfall` (TEXT, `INNVILGET`/`AVSLATT`) og `sak.beslutter_ident`
  finnes fra V12. Ingen skriver til dem. `sak_retur` finnes, men brukes ikke.
- `sak` har ingen kolonner for tiltaksperiode, innvilget beløp eller
  begrunnelser.
- `sak-datamodell.md` sier at beslutter ikke kan fatte vedtak på et foreløpig
  vedtak hen selv sendte til beslutning, og at regelen bygges sammen med vedtak
  (avklart i `tildel_meg_sak.md`, Ken 2026-10-08). Da må vi vite hvem som sendte.
- Vilkårsvurderingen (PR #175, #176): fem vilkår i `saksvilkar`, `godkjent = null`
  betyr ikke vurdert. `PATCH …/vilkarsvurdering` krever tildelt saksbehandler og
  `UNDER_BEHANDLING`, ellers 409. Når saken går til `TIL_BESLUTNING`, er
  vurderingen dermed låst uten ny kode.
- Tildeling (`tildel_meg_sak.md`) har gitt `Tilgangsjekker.kt`,
  `SakOppdatert` + `SkrivSakslogg`, `tildelSak`/`frigjoerSak` som felles
  funksjoner for handler og `SakProjection`, og `SAK_UGYLDIG_STATUS` (409).
- Nye saker får `kilde_til_behandling = EKSPERTBISTAND` (`OpprettSak`). Eldre saker
  er `ARENA`, og status på dem settes av Arena-eventer i `SakProjection`.
- `OpprettSak` er registrert bare utenfor prod, og `SakProjection` bare i prod
  (`basedOnEnvSuspending` i `configureEventHandlers` og
  `configureProjectionBuilders`). Projeksjonen backfiller dermed bare Arena-saker
  og ser aldri en sak med kilde `EKSPERTBISTAND` (Ken, 2026-10-10).
- `LocalApplication` legger inn to søknader og publiserer
  `InnsendtSoknadJournalfoert` for begge. Lokalt kjører `OpprettSak`, så begge
  får kilde `EKSPERTBISTAND`. Det finnes ingen lokal Arena-sak.
- `SakPage` har allerede fanene «Vilkårsvurdering» og «Foreløpig vedtak», og
  lenken «Fatte foreløpig vedtak» som bytter fane.
- `DokumentService` har maler for søknad, tilskuddsbrev (Arena-data) og
  Arena-notat. Ingen mal for vedtaksbrev.

Relatert, ikke del av dette kortet:

- Kortet «Vedtak til deltakere og tilsagn til arbeidsgiver»
  (`6a8d564c1012dc836b9e2a1b`) eier brevene. Hanna har lagt inn dagens
  vedtakstekst til deltaker der (2026-09-24).
- Kortet «Varsle deltaker om vedtak» (`6aab96635bae2266d1c1396f`).
- ADR-forslaget `adr/0002-vedtak-og-klagebehandling.md` på branchen
  `adr_vedtak_klage` (status «Forslag»). Den skiller foreløpig vedtak
  (innstilling) fra vedtak, versjonerer innstillingen ved retur (B3), har
  utfallene `innvilge | innvilge_redusert | avslaa` og registrerer `innstiltAv`
  ved innsending (B17). Spec-en følger dette der det ikke krever resten av ADR-en
  (`Behandling`, `Vedtak`, `Underretning`).

## Skisser

Fil: `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand). Lest med Figma MCP 2026-10-09.

| Node | Lenke | Viser |
|------|-------|-------|
| `224:18685` | [innvilge, skjema](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=224-18685) | Fanen «Foreløpig vedtak». Overskrift «Vurdering», `RadioGroup` med «Innvilge (5 av 5 vilkår oppfyllt)», «Innvilge med redusert beløp» og «Avslå». Grønt infokort «Vurdering: Innvilge» med tilskudd (søknadssum), deltaker og arbeidsgiver. «Tiltaksperiode» med to datovelgere, startdato forhåndsutfylt med søknadens startdato og sluttdato med 31.12 samme år. `Textarea` «Deltaker - tilpass begrunnelse» med hjelpetekst «Teksten blir synlig i vedtaksbrevet til deltaker.», forhåndsutfylt med standardtekst. Knappen «Forhåndvis foreløpig vedtak». |
| `27:4374` | [innvilge, forhåndsvis](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=27-4374) | Infokortet med tiltaksperioden. Begrunnelsen låst (låsikon, kun lesing). Lenkene «Forhandvis foreløpig vedtak - deltakere» og «… - arbeidsgiver» med ekstern-ikon. Knappene «Forrige» (sekundær) og «Send til beslutter» (primær). |
| `18:4135` | [innvilge, bekreftelse](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=18-4135) | Helside. Grønn hake, «Foreløpig vedtak sendt til beslutter», «Saken er oversendt beslutter for godkjenning. Arbeidsgiver og deltaker vil bli varslet etter endelig godkjenning.» Oppsummering: vurdering, tilskudd, arbeidsgiver, deltakere, tiltaksperiode. Lenken «Tilbake til liste av saker». |
| `723:25524` | [innvilge, sendt](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=723-25524) | Fanen etter innsending for innvilge: «Foreløpig vedtak sendt til beslutter», grønt infokort med tilskudd, deltaker, arbeidsgiver og tiltaksperiode, låst begrunnelse til deltaker og to forhåndsvisningslenker. Ingen knapper. |
| `414:18602` | [redusert, skjema](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=414-18602) | Gult infokort «Vurdering: Innvilge med redusert beløp» med søknadssum. Tekstfeltet «Nytt beløp», tiltaksperiode, begrunnelse til deltaker og «Arbeidsgiver - tilpass begrunnelse» («Teksten blir synlig i vedtaksbrevet til arbeidsgiver.»). |
| `414:18714` | [redusert, forhåndsvis](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=414-18714) | Gult infokort med søknadssum, tilskudd (nytt beløp), deltaker, arbeidsgiver og tiltaksperiode. Begge begrunnelser låst. Standardteksten til begge parter nevner beløpene: «Dere søkte om <søknadssum> kr, Nav har innvilget <nytt beløp> kr i tilskudd til ekspertbistand.» Forhåndsvisningslenker, «Forrige» og «Send til beslutter». |
| `723:25411` | [redusert, bekreftelse](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=723-25411) | Som `18:4135`, med søknadssum og tilskudd (nytt beløp). |
| `723:25766` | [redusert, sendt](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=723-25766) | Fanen etter innsending: «Foreløpig vedtak sendt til beslutter», infokortet, låste begrunnelser og forhåndsvisningslenker. Ingen knapper. |
| `16:3774` | [avslå, skjema](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=16-3774) | Rødt infokort «Vurdering: Avslå» (tommel ned) med tilskudd, deltaker og arbeidsgiver. Ingen tiltaksperiode eller beløp. Begrunnelse til deltaker og arbeidsgiver. |
| `38:1255` | [avslå, forhåndsvis](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=38-1255) | Som `27:4374`, med begge begrunnelser låst. |
| `30:994` | [avslå, bekreftelse](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=30-994) | Som `18:4135`, men oppsummeringen har bare vurdering, arbeidsgiver og deltakere. |
| `513:28774` | [avslå, sendt](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=513-28774) | Som `723:25766` for avslå. |

Skissene har realistiske testdata (navn, fødselsnummer). De er ikke kopiert hit.

Avvik mellom kort og skisse:

- **Begrunnelse til arbeidsgiver ved innvilgelse.** Kortet nevner «tilskuddsbrev»
  for innvilge. Skissen har bare begrunnelse til deltaker. Vi følger skissen,
  bekreftet av Ken 2026-10-09: arbeidsgiver får ingen fritekst ved vanlig
  innvilgelse.
- **«Innvilge (5 av 5 vilkår oppfyllt)».** Skissen viser antallet i etiketten.
  Etiketten beholdes som informasjon. Innvilgelse krever ikke at alle er oppfylt
  (Ken, 2026-10-09).
- **Sluttdato.** Skissen forhåndsutfyller 31.12. Vi bruker startdato + 3 måneder,
  begrenset til 31.12 (Ken, 2026-10-09).
- **Begrunnelser uten standardtekst.** Skissen forhåndsutfyller begrunnelsene
  med standardtekster og kaller feltene «Deltaker - tilpass begrunnelse» og
  «Arbeidsgiver - tilpass begrunnelse». Begrunnelsen til deltaker, og til
  arbeidsgiver ved avslag, må skrives for hver enkelt. Feltene starter tomme, er
  påkrevd og heter «Begrunnelse til deltaker» og «Begrunnelse til arbeidsgiver».
  Bare arbeidsgiver ved redusert beløp har standardtekst og beholder
  «Arbeidsgiver - tilpass begrunnelse» (Ken, 2026-10-12).
- **Forhåndsvisningslenker.** Skissen har lenker. De bygges ikke, se *Avgrensning*.
- **Stavefeil i skissen.** «oppfyllt», «Forhåndvis», «Forhandvis». Vi skriver
  «oppfylt» og «Forhåndsvis».

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen; Figma | Foreløpig vedtak kan fattes når alle vilkår er vurdert. Utfallene er innvilge, innvilge med redusert beløp og avslå, med feltene i *Mål*. Flyten har tre steg: skjema, forhåndsvisning med låste tekster, bekreftelse. Etter innsending viser fanen det foreløpige vedtaket uten knapper. |
| Ken, 2026-10-09 | Innvilgelse krever ikke at alle vilkår er oppfylt, bare at alle er vurdert. |
| Ken, 2026-10-09 (svar på kortkommentar fra Hanna, 2026-10-07) | Tiltaksperioden kan ikke gå over årsskiftet, fordi satsen kan endres med nytt statsbudsjett. Frontend og backend validerer at start og slutt er i samme år. Sluttdato forhåndsutfylles med startdato + 3 måneder, men aldri senere enn 31.12 samme år. Ingen regel for startdato i fortiden eller før søknadens startdato. |
| Ken, 2026-10-09 og 2026-10-10; korrigert 2026-10-22 | Redusert beløp må være mindre enn søknadssummen, og innvilget beløp kan aldri være høyere enn maks sats. Satsen er 24 900 kr fra 1. januar 2026 og kommer fra MD-brev eller fag. Vi støtter flere satser nå: hver sats har en gyldig fra-dato, slik at en ny sats kan deployes i god tid. Satsene ligger i koden, og en hjelpefunksjon gir gjeldende sats for en dato. Se *2b*. |
| Ken, 2026-10-09, 2026-10-10 og 2026-10-12 | Begrunnelse til arbeidsgiver kreves bare ved redusert beløp og avslag. Begrunnelse til deltaker kreves for alle utfall. Bare begrunnelsen til arbeidsgiver ved redusert beløp har standardtekst. De andre må skrives for hver enkelt. Standardteksten kommer fra backend, sammen med brevmalene, og står ordrett som i skissen. PR-beskrivelsen flagger ordlyden til gjennomgang. |
| Ken, 2026-10-09 | Forhåndsvisning av brevene er ikke med. Hanna lager et forslag med forhåndsvisnings-«pills» i stedet for lenker. |
| Ken, 2026-10-10 | Visning av tilsagnsbrev i saksbildet er en egen oppgave. |
| Ken, 2026-10-09 og 2026-10-10 | Saker med `kilde_til_behandling = ARENA` vises i en nedstrippet versjon, men saksbehandles ikke hos oss. De har bare søknad og tilsagnsbrev, ingen vilkårsvurdering, refusjon eller sluttrapport, og behandles ferdig i Arena. De kan tildeles, fordi det viser hvem som har saken i Arena. Se *2c*. |
| Ken, 2026-10-10 | `SakProjection` kjører bare i prod og backfiller bare Arena-saker. Den får ingen funksjonalitet for nye saker. |
| Ken, 2026-10-10 | `LocalApplication` har én sak med kilde `EKSPERTBISTAND` (via køen) og én med kilde `ARENA`, der `initTestData` kaller `SakProjection(database).handle(event)` direkte i stedet for `publishEventQueue`. Registreringen av handlere per miljø endres ikke. |
| Ken, 2026-10-09 | Saksbehandlingsløsningen finnes bare i dev, uten saksbehandlingsdata i prod. `V22` kan derfor slette vilkår på Arena-saker, og vi skriver ikke migreringstester. De to som fantes, slettes i en egen commit. |
| Ken, 2026-10-06 (minne, `tildel_meg_sak.md`) | API-et publiserer en event, og en handler gjør endringen. Saksloggen skrives via `SakOppdatert` og `SkrivSakslogg`. |
| Ken, 2026-10-08 (`tildel_meg_sak.md`) | Topartskontrollen: den som sendte saken til `TIL_BESLUTNING`, kan ikke beslutte den. Dette kortet lagrer hvem som sendte, og sjekken bygges i beslutterkortet. |
| `SakApi.kt`, `Tilgangsjekker.kt` | Tilgang som vilkårsvurderingen: rolle `SAKSBEHANDLER`, enhet, Tilgangsmaskinen (`KJERNE`), og innlogget må ha saken (`IKKE_TILDELT_SAK`). Ugyldig status gir 409 `SAK_UGYLDIG_STATUS`. |

## Avgrensning

Ikke i dette kortet:

- Beslutterens side: oversikt over saker til beslutning, godkjenne, returnere,
  `beslutter_ident`, habilitetssjekken og bruk av `sak_retur`.
- Vedtaksbrev og tilskuddsbrev som PDF. Forhåndsvisning av vedtaksbrevene med
  PDF-«pills» er beskrevet i `forelopig_vedtak_brev.md`.
- Varsling av arbeidsgiver og deltaker.
- Bestilling mot tiltaksøkonomi.
- Å trekke tilbake et foreløpig vedtak etter innsending.
- Visning av tilsagnsbrevet i saksbildet. Det er en egen oppgave (Ken,
  2026-10-10).
- For Arena-saker: refusjon. Refusjonssøknad og sluttrapport er ikke relevant for Arena-saker,
  og eventuell refusjonsinformasjon fra Arena tas senere (Ken, 2026-10-09).
- Hvordan beslutter plukker saker. Det er beskrevet i
  [`listevisning.md`](listevisning.md): oppgavetypen «Søknad - beslutter» og
  «Tildel meg» som beslutter.

## Tilnærming

```
POST …/forelopig-vedtak ── publishEventQueue(ForelopigVedtakSendtTilBeslutter) ──> event_queue
                                                                                      │
LagreForelopigVedtak ── lagreForelopigVedtak(...) + publishEventQueue(SakOppdatert)
                         ├─ INSERT forelopig_vedtak (versjon n)
                         └─ UPDATE sak: status = TIL_BESLUTNING, foreslatt_utfall
                                                                                      │
SkrivSakslogg ── INSERT sakslogg
```

`SakProjection` får ikke eventen. Foreløpig vedtak gjelder bare saker med kilde
`EKSPERTBISTAND`, og projeksjonen backfiller bare Arena-saker (Ken, 2026-10-10).

### 0. Migrering

`V21__forelopig_vedtak.sql`:

```sql
CREATE TABLE forelopig_vedtak (
    id                       BIGSERIAL   PRIMARY KEY,
    sak_id                   UUID        NOT NULL REFERENCES sak (sak_id),
    versjon                  INT         NOT NULL,
    event_id                 BIGINT      NOT NULL UNIQUE,
    utfall                   TEXT        NOT NULL,
    innvilget_belop          INT,
    tiltaksperiode_fra       DATE,
    tiltaksperiode_til       DATE,
    begrunnelse_deltaker     TEXT        NOT NULL,
    begrunnelse_arbeidsgiver TEXT,
    innstilt_av_ident        TEXT        NOT NULL,
    sendt_tidspunkt          TIMESTAMPTZ NOT NULL,
    UNIQUE (sak_id, versjon),
    CONSTRAINT chk_forelopig_vedtak_utfall
        CHECK (utfall IN ('INNVILGE', 'INNVILGE_REDUSERT', 'AVSLAA')),
    CONSTRAINT chk_forelopig_vedtak_felter CHECK (
        (utfall = 'AVSLAA'
            AND innvilget_belop IS NULL
            AND tiltaksperiode_fra IS NULL AND tiltaksperiode_til IS NULL
            AND begrunnelse_arbeidsgiver IS NOT NULL)
        OR (utfall IN ('INNVILGE', 'INNVILGE_REDUSERT')
            AND innvilget_belop > 0
            AND tiltaksperiode_fra IS NOT NULL AND tiltaksperiode_til IS NOT NULL
            AND tiltaksperiode_fra <= tiltaksperiode_til
            AND EXTRACT(YEAR FROM tiltaksperiode_fra) = EXTRACT(YEAR FROM tiltaksperiode_til))
    ),
    CONSTRAINT chk_forelopig_vedtak_redusert_begrunnelse
        CHECK (utfall <> 'INNVILGE_REDUSERT' OR begrunnelse_arbeidsgiver IS NOT NULL)
);

CREATE INDEX idx_forelopig_vedtak_sak_id ON forelopig_vedtak (sak_id);
```

Valget: egen tabell med `versjon` i stedet for kolonner på `sak`. Det koster en
join, men beslutters retur gir en ny runde, og ADR-forslaget (B3) og
topartskontrollen krever at tidligere innstillinger og hvem som sendte dem,
bevares. Kolonner på `sak` ville blitt overskrevet ved neste innsending.

`event_id UNIQUE` gjør innsettingen idempotent, også om handleren kjører eventen
på nytt etter en feil.
`innvilget_belop` er hele kroner, som søknadssummen.

`sak.foreslatt_utfall` settes til `INNVILGET` (innvilge og redusert) eller
`AVSLATT`, som `sak-datamodell.md` beskriver. Den gir listevisningen utfallet
uten join. Detaljene ligger i `forelopig_vedtak`.

`V22__slett_vilkar_for_arena_saker.sql` er beskrevet i *2c*.

### 1. Ny event

I `Events.kt`:

```kotlin
@Serializable
data class ForelopigVedtakSendtTilBeslutter(
    val sakId: String,
    val soknadId: String,
    val utfall: ForelopigUtfall,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
    val begrunnelseDeltaker: String,
    val begrunnelseArbeidsgiver: String?,
    val innstiltAvIdent: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

`enum class ForelopigUtfall { INNVILGE, INNVILGE_REDUSERT, AVSLAA }` i
`saksbehandling/Db.kt`. Eventen bærer det ferdige beløpet: for `INNVILGE` setter
API-et søknadssummen, slik at handleren ikke må lese søknaden.

`event/README.md` får eventen i tabellen over `aggregateRootId` (`soknadId`).

### 2. Backend: API

🔴 Rød sone: tilgang, status og vilkårssjekk avgjør hvem som kan sende en sak til
beslutning. Gå gjennom denne nøye.

Ny fil `saksbehandling/ForelopigVedtakApi.kt`, montert under
`/api/saksbehandling/v1/saker` som vilkårsvurderingen.

`POST /api/saksbehandling/v1/saker/{sakId}/forelopig-vedtak`

```json
{
  "utfall": "INNVILGE_REDUSERT",
  "innvilgetBelop": 17000,
  "tiltaksperiodeFra": "2026-11-22",
  "tiltaksperiodeTil": "2026-12-31",
  "begrunnelseDeltaker": "…",
  "begrunnelseArbeidsgiver": "…"
}
```

Rekkefølge, som `VilkarsvurderingApi`:

1. `principalMedRolle(Role.SAKSBEHANDLER)`, `sakIdParameter()`.
2. `hentSakTilgangsgrunnlag` (404), `sjekkTilgangTilEnhet` (403),
   `sjekkTilgangsmaskin` med `KJERNE` (403/503). Er kilden `ARENA`, svar 409
   `SAK_UGYLDIG_KILDE` (se *2c*). `SakTilgangsgrunnlag` får
   `kildeTilBehandling` til dette. Deretter `sjekkErSaksbehandlerPåSak`
   (403 `IKKE_TILDELT_SAK`).
3. `call.receive<ForelopigVedtakRequest>()` og `valider(request)` (tekstsjekkene,
   maks 10 000 tegn). Deretter feltregler. Brudd kaster `UgyldigInputException`
   med feltstien, som `Application.kt` gjør om til 400:
   - `begrunnelseDeltaker` er påkrevd og ikke blank.
   - `INNVILGE`: tiltaksperiode påkrevd. `innvilgetBelop` og
     `begrunnelseArbeidsgiver` skal være null.
   - `INNVILGE_REDUSERT`: tiltaksperiode, `innvilgetBelop` og
     `begrunnelseArbeidsgiver` påkrevd. `0 < innvilgetBelop <= sats`, der
     `sats = gjeldendeMaksSats(tiltaksperiodeFra)` (se *2b*).
   - `AVSLAA`: `begrunnelseArbeidsgiver` påkrevd. Tiltaksperiode og beløp skal
     være null.
   - `tiltaksperiodeFra <= tiltaksperiodeTil`, og begge i samme år.
   - `INNVILGE` og `INNVILGE_REDUSERT`: `gjeldendeMaksSats(tiltaksperiodeFra)`
     må finnes. Er startdatoen før den første satsen gjelder, gir det 400 på
     `tiltaksperiodeFra`.
4. I `transaction(database)`:
   - Lås saken med `SELECT … FOR UPDATE`.
   - Status må være `UNDER_BEHANDLING`, ellers 409 `SAK_UGYLDIG_STATUS`.
   - `hentVilkarsvurdering(sakId)`: alle vilkår må ha `godkjent != null`, ellers
     409 `VILKAR_IKKE_VURDERT`. Om de er oppfylt, spiller ingen rolle.
   - Les søknadssummen. Kan den ikke tolkes som et positivt heltall, svar 409
     `UGYLDIG_SOKNADSSUM`.
   - `INNVILGE`: `innvilgetBelop = søknadssum`. Er søknadssummen høyere enn
     `gjeldendeMaksSats(tiltaksperiodeFra)`, svar 409 `BELOP_OVER_SATS`.
     Saksbehandler må da velge redusert beløp.
   - `INNVILGE_REDUSERT`: `innvilgetBelop < søknadssum`, ellers 400.
   - `publishEventQueue(ForelopigVedtakSendtTilBeslutter(…, innstiltAvIdent =
     principal.navIdent, tidspunkt = now))`.
5. Svar 202.

Sjekkene i transaksjonen gjøres i API-et for å gi brukeren feilmelding, og igjen
i handleren, siden status kan endres mellom publisering og behandling.

`GET /saker/{sakId}` (`SakDetaljer`) får feltet `forelopigVedtak`: siste versjon
fra `forelopig_vedtak`, eller `null`.

```kotlin
@Serializable
data class ForelopigVedtakDTO(
    val versjon: Int,
    val utfall: ForelopigUtfall,
    val innvilgetBelop: Int?,
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
    val begrunnelseDeltaker: String,
    val begrunnelseArbeidsgiver: String?,
    val innstiltAvIdent: String,
    val sendtTidspunkt: Instant,
)
```

### 2b. Backend: maks sats og standardtekster

Maks sats i ny `saksbehandling/Sats.kt` (Ken, 2026-10-10):

```kotlin
data class MaksSats(val gyldigFra: LocalDate, val belop: Int)

/** Maks tilskudd per sak. Kilde: MD-brev eller fag. Ny sats legges til med gyldigFra og kan deployes før den gjelder. */
val MAKS_SATSER: List<MaksSats> = listOf(
    MaksSats(gyldigFra = LocalDate(2026, 1, 1), belop = 24_900),
)

/** Satsen med senest [MaksSats.gyldigFra] som ikke er etter [dato], eller null hvis ingen gjelder. */
fun gjeldendeMaksSats(dato: LocalDate, satser: List<MaksSats> = MAKS_SATSER): MaksSats? =
    satser.filter { it.gyldigFra <= dato }.maxByOrNull { it.gyldigFra }
```

Satsen slås opp på tiltaksperiodens startdato. Perioden kan ikke gå over
årsskiftet, så for satser som gjelder fra 1. januar, gir start og slutt samme
sats. Får en sats en gyldig fra-dato midt i året, gjelder satsen på
startdatoen for hele perioden.

Søknadsskjemaet henter satsen fra `GET /api/soknad/v1/maks-sats?startdato=ÅÅÅÅ-MM-DD`.
Uten valgt startdato viser skjemaet dagens sats, men krever startdato før
brukeren kan fortsette. Når startdatoen endres, hentes satsen på nytt.
Backend validerer søknadssummen mot satsen for søknadens startdato ved innsending.
Hvis neste års sats ennå ikke er lagt inn, gjelder siste kjente sats.
Utkast kan fortsatt lagres selv om beløpet overstiger satsen.

Valget: en liste med gyldig fra-dato i koden. Det vi får: en ny sats kan
deployes i god tid og gjelder fra riktig dato, og saker med startdato før
skiftet vurderes fortsatt mot den gamle satsen. Det vi gir opp: å endre satsen
uten deploy. Database og miljøvariabel er forkastet, fordi vi ikke har noe sted
å endre satsen fra, og fordi satsen er en del av regelverket som bør
kodegjennomgås. Det første utkastet, én `const`, er forkastet fordi vi vet at
vi trenger flere satser (Ken, 2026-10-10).

Er datoen før den første satsen, finnes ingen sats. Det er en feil i
inndataene, ikke i koden, og gir 400 (se *2*). Vi finner ikke opp en sats for
tidligere år.

Standardtekstene ligger i
`backend/src/main/resources/dokumentmaler/standardtekster/forelopig_vedtak.json`,
ved siden av brevmalene. Bare begrunnelsen til arbeidsgiver ved redusert beløp
har standardtekst. Begrunnelsen til deltaker, og til arbeidsgiver ved avslag,
skrives for hver enkelt (Ken, 2026-10-12). Første versjon er teksten fra
skissen `414:18714`:

```json
{
  "INNVILGE_REDUSERT": {
    "arbeidsgiver": "Dere søkte om {soknadssum} kr, Nav har innvilget {innvilgetBelop} kr i tilskudd til ekspertbistand."
  }
}
```

Tekstene er ren tekst uten Handlebars. Den eneste flettingen er
`{soknadssum}` og `{innvilgetBelop}`, som frontend bytter ut med beløp formatert
med tusenskille når teksten fylles inn i feltet. Endrer saksbehandler beløpet
etterpå, fylles teksten inn på nytt bare hvis saksbehandler ikke har redigert
den. Backend lagrer den ferdige teksten, ikke malen. Tekstene står ordrett som i
skissene, og PR-beskrivelsen flagger ordlyden til gjennomgang (Ken, 2026-10-10).

Tekstene ligger ikke i `templates/`, fordi hver mappe der er en mal med
`template.hbs` og `testdata/`, og `DokumentmalTest` forventer det. Når
brevkortet lager vedtaksbrevmalene, kan tekstene flyttes inn til dem.

`GET /api/saksbehandling/v1/forelopig-vedtak/oppsett` krever rolle
`SAKSBEHANDLER` og returnerer:

```json
{
  "standardtekster": { "INNVILGE_REDUSERT": { "arbeidsgiver": "…" } },
  "maksSatser": [{ "gyldigFra": "2026-01-01", "belop": 24000 }]
}
```

Frontend velger satsen for valgt startdato med samme regel som
`gjeldendeMaksSats`. Backend sjekker igjen ved innsending.

Filen leses og valideres ved oppstart: redusert beløp må ha en tekst til
arbeidsgiver som ikke er blank, og andre utfall skal ikke ha tekst. Er noe
feil, starter ikke appen.

### 2c. Arena-saker: bare visning

Ken, 2026-10-09: en Arena-sak er bare en sak å se på. Den har søknad og
tilsagnsbrev, ingen vilkårsvurdering, og refusjon og sluttrapport er ikke
relevant. Den behandles ferdig i Arena, fordi vi ikke kan gjøre noe med
utbetalingen. Vi sperrer derfor ikke handling for handling, men lar være å lage
det som skal behandles.

Backend:

- **Ingen vilkår for Arena-saker.** `SakProjection.opprettSak` slutter å kalle
  `opprettVilkarForSak`. Projeksjonen oppretter bare saker med kilde `ARENA`, så
  kallet fjernes helt, sammen med løkken «også for eksisterende saker». KDoc-en
  oppdateres. Versjonen bumpes ikke: `V22` rydder rader som finnes.
- **Eksisterende vilkår slettes.** Migreringen `V22__slett_vilkar_for_arena_saker.sql`:

  ```sql
  DELETE FROM saksvilkar
  WHERE sak_id IN (SELECT sak_id FROM sak WHERE kilde_til_behandling = 'ARENA');
  ```

  ⚠️ Dette er irreversibelt. Det er akseptert fordi løsningen bare finnes i
  dev, og prod ikke har saksbehandlingsdata (Ken, 2026-10-09).
- **Tildeling.** Arena-saker kan tildeles og frigjøres som andre saker (Ken,
  2026-10-10). Det viser hvem som har saken i Arena. `tildelingHindring` endres
  ikke. `tildelSak` setter `UNDER_BEHANDLING` fra `OPPRETTET`, også for
  Arena-saker. Det er ufarlig: `SaksbehandlingStartetIArena` setter samme
  status, og `TilskuddsbrevMottatt` og `SoknadAvlystIArena` setter fortsatt
  sluttstatus.
- **Vilkårsvurdering.** Ingen ny sjekk. Uten vilkårsrader svarer `PATCH` 404
  «fant ikke vilkår på saken», som i dag.
- **Foreløpig vedtak.** `POST …/forelopig-vedtak` sjekker kilden (se *2*). Det er
  den eneste skrivende ruten der manglende rader ikke stopper kallet av seg selv,
  fordi `hentVilkarsvurdering` fyller inn manglende vilkår som «ikke vurdert».
  Sjekken på vurderte vilkår ville også stoppet det, men med feil feilmelding.

Valget: sperren følger av at dataene ikke finnes, ikke av en sjekk i hver rute.
Det gir færre steder å glemme når nye handlinger kommer, for eksempel refusjon.
Det vi gir opp, er en presis feilkode på `PATCH …/vilkarsvurdering` (404 i stedet
for 409). Alternativet, en felles `sjekkKanSaksbehandles` i alle skrivende ruter,
er forkastet fordi frontend uansett ikke viser handlingene.

### 3. Backend: handler

Ny `event/handlers/LagreForelopigVedtak.kt`, registrert i
`configureEventHandlers` for alle miljøer. Registreringen per miljø ellers
endres ikke (Ken, 2026-10-10). Mønster som `TildelSaksbehandler`:

- `IdempotencyGuard`: allerede håndtert gir `Success`.
- Finnes ikke saken: `unrecoverableError`.
- I én transaksjon: `lagreForelopigVedtak(...)`, deretter
  `publishEventQueue(SakOppdatert(..., utfortAvRolle = SAKSBEHANDLER,
  utfortAvIdent = innstiltAvIdent, notat = "Foreløpig vedtak sendt til beslutter:
  <utfall>", tidspunkt))` og guard. Feil gir `rollback()` og `transientError`.
- Returnerer `lagreForelopigVedtak` false (saken er ikke lenger
  `UNDER_BEHANDLING`), logges det på `warn` uten personopplysninger, og eventen
  er `Success` uten `SakOppdatert`. Som `OppdaterVilkarsvurdering`.

Funksjon i `saksbehandling/Db.kt`, ved siden av `tildelSak`:

```kotlin
fun lagreForelopigVedtak(data: ForelopigVedtakSendtTilBeslutter, eventId: Long): Boolean
```

1. `UPDATE sak SET status = 'TIL_BESLUTNING', foreslatt_utfall = …, sist_endret = …
   WHERE soknad_id = ? AND status = 'UNDER_BEHANDLING'`.
2. Er én rad oppdatert: `insertIgnore` i `forelopig_vedtak` med
   `versjon = max(versjon) + 1` for saken. `event_id UNIQUE` stopper duplikater.
3. Returnerer om saken ble oppdatert.

Saksbehandleren beholder saken (`saksbehandler_ident` endres ikke).

### 4. Frontend: hook

`hooks/useForelopigVedtak.ts`, mønster som `useVilkårsvurdering`:

- `sendTilBeslutter(input)` POSTer til
  `SAKSBEHANDLING_FORELOPIG_VEDTAK_URL(sakId)` (ny i `utils/constants.ts`).
- Ved 202 oppdateres `useSak`-cachen med `mutate(…, { revalidate: false })`:
  `status = "TIL_BESLUTNING"` og `forelopigVedtak` fra input.
- Feilmeldinger: 400 «Sjekk feltene og prøv igjen.», 403 `begrunnelse` eller
  «Du har ikke tilgang …», 409 per `kode` (ikke under behandling, vilkår ikke
  vurdert, beløp over sats, saken behandles i Arena).

`SakDetaljer` i `useSak.ts` får `forelopigVedtak: ForelopigVedtak | null`.

`hooks/useForelopigVedtakOppsett.ts` henter standardtekster og `maksSatser` fra
`…/forelopig-vedtak/oppsett` med SWR, uten revalidering. Satsene hardkodes ikke
i frontend. `utils/maksSats.ts` har `gjeldendeMaksSats(satser, dato)` med samme
regel som backend.

### 5. Frontend: fanen «Foreløpig vedtak»

Nye komponenter i `components/forelopigVedtak/`:

- `ForelopigVedtakSkjema.tsx`: steg 1. `RadioGroup` for utfall, infokort i farge
  etter utfall (`InfoCard`/`Box` med Aksel-tokens for success, warning, danger),
  `DatePicker` for start og slutt (`useRangeDatepicker` hvis den passer
  skissen), `TextField` «Nytt beløp» (`inputMode="numeric"`), `Textarea` for
  begrunnelsene med hjelpetekstene fra skissen. Knappen «Forhåndsvis foreløpig
  vedtak» validerer i klienten med samme regler som backend og viser feil på
  feltet (`error`-prop) og i `ErrorSummary`.
- `ForelopigVedtakOppsummering.tsx`: steg 2 og visning etter innsending.
  Infokort med tiltaksperiode og beløp, låste begrunnelser (`Textarea readOnly`
  med låsikon, som i skissen). Steg 2 har «Forrige» og «Send til beslutter».
- `ForelopigVedtakKvittering.tsx`: steg 3, helside som erstatter
  tre-kolonne-visningen i `SakPage` til brukeren går tilbake til listen.
  Oppsummeringstabellen følger skissen per utfall.

Forhåndsutfylling:

- Startdato: `behovForBistand.startdato`.
- Sluttdato: startdato + 3 måneder, men aldri senere enn 31.12 samme år.
  Endres startdatoen, regnes sluttdatoen ut på nytt hvis saksbehandler ikke har
  endret den selv.
- `DatePicker` for sluttdato har `toDate` 31.12 i startdatoens år. Klientvalidering
  gir feil hvis datoene er i ulike år.
- «Begrunnelse til deltaker»: tomt felt, påkrevd for alle utfall.
- «Begrunnelse til arbeidsgiver» ved avslag: tomt felt, påkrevd.
- «Arbeidsgiver - tilpass begrunnelse» ved redusert beløp: standardteksten fra
  `useForelopigVedtakOppsett` med `{soknadssum}` og `{innvilgetBelop}` flettet
  inn (se *2b*).
- Forhåndsvisning og sendt vedtak bruker de samme ledetekstene.
- Bytter saksbehandler utfall, beholdes tekst hen har skrevet eller endret.
- Antall oppfylte vilkår i etiketten «Innvilge (n av 5 vilkår oppfylt)» fra
  `useVilkår`. Etiketten er informasjon, ikke en sperre.

Sats i skjemaet, slått opp på valgt startdato (forhåndsutfylt med søknadens
startdato):

- Er søknadssummen høyere enn gjeldende sats, er «Innvilge» deaktivert med
  teksten «Søknadssummen er høyere enn maks sats (<sats> kr). Velg innvilge med
  redusert beløp.» Endres startdatoen, vurderes dette på nytt. Er «Innvilge»
  valgt og blir deaktivert, viser feltet en feil i stedet for å bytte utfall.
- «Nytt beløp» valideres mot søknadssum og gjeldende sats.
- Finnes ingen sats for startdatoen, får startdatoen feilen «Det finnes ingen
  maks sats for denne datoen.»

Forhåndsvisnings-«pills» kommer etter knappene i et senere kort. La plass til dem
nederst i panelet, men bygg ikke noe nå.

Hva fanen viser:

| Tilstand | Visning |
|----------|---------|
| `forelopigVedtak` finnes og status er `TIL_BESLUTNING` | Oppsummering uten knapper, overskrift «Foreløpig vedtak sendt til beslutter» |
| Status `UNDER_BEHANDLING`, tildelt meg, alle vilkår vurdert | Skjemaet |
| Status `UNDER_BEHANDLING`, tildelt meg, ikke alle vurdert | `Alert variant="info"`: «Alle vilkår må vurderes før du kan fatte foreløpig vedtak.» |
| Ikke tildelt meg | `Alert variant="info"`: «Du må ha saken for å fatte foreløpig vedtak.» |
| Annen status | Ingenting å vise, som i dag |

Skjemaet holdes i state i `SakPage`-treet, slik at bytte av fane og «Forrige»
ikke mister det som er skrevet.

Nedstrippet visning for Arena-saker (*2c*). Når `sak.kildeTilBehandling` er
`ARENA`:

- Venstre og midtre kolonne (arbeidsgiver, deltaker, ekspert og søknaden) vises
  som i dag.
- Høyre kolonne viser ingen faner, bare `Alert variant="info"`: «Saken behandles
  i Arena. Vilkårsvurdering, vedtak og utbetaling gjøres der.»
- `useVilkår` kalles med `undefined`, så vilkårene ikke hentes.
- «Tildel meg» og «Frigjør» vises som for andre saker.

Valget: én sjekk på kilden øverst i høyre kolonne i stedet for sperrer i hver
komponent. `VilkårItem` og fanene endres ikke.

### 6. MSW-mock

`mocks/handlers.ts`:

- `POST …/forelopig-vedtak` svarer 202 og oppdaterer mock-saken (status,
  `forelopigVedtak`).
- `GET …/forelopig-vedtak/oppsett` med standardtekstene og
  `maksSatser: [{ gyldigFra: "2026-01-01", belop: 24000 }]`.
- Én mock-sak i `TIL_BESLUTNING` med `forelopigVedtak`, slik at visningen etter
  innsending kan sjekkes lokalt.
- Én mock-sak med søknadssum over satsen.
- Én mock-sak med kilde `ARENA`, som kan tildeles som andre saker.

### 6b. `LocalApplication`

`LocalApplication` skal gi én sak med kilde `EKSPERTBISTAND` og én med kilde
`ARENA`, slik at begge kan testes manuelt (Ken, 2026-10-10). Begge lages med
eventer, ikke med direkte skriving til `sak` (Ken, 2026-10-10).

- **`EKSPERTBISTAND`.** Uendret: søknadene `f8f48c1f-…` og `2f3f8f6d-…`
  publiserer `SoknadInnsendt` og `InnsendtSoknadJournalfoert` som i dag, og
  `OpprettSak` oppretter sakene med vilkår.
- **`ARENA`.** En ny søknad legges inn i `soknad` slik `initTestData` gjør med
  de andre. I stedet for `publishEventQueue` kaller `initTestData`
  `SakProjection(database).handle(event, tidspunkt)` direkte med eventene
  søknaden ville fått i prod: `SoknadInnsendt`, `InnsendtSoknadJournalfoert`,
  `TiltaksgjennomforingOpprettet` og `SaksbehandlingStartetIArena`, i den
  rekkefølgen (Ken, 2026-10-10). Projeksjonen oppretter saken med kilde
  `ARENA`, behandlende enhet, Arena-saksnummer og status `UNDER_BEHANDLING`,
  uten vilkår. Eventene går ikke via køen og skrives ikke i `event_log`, så
  ingen handler kjører, heller ikke `OpprettSak`.

Valget: Arena-saken lages ved at `initTestData` kaller `SakProjection` direkte.
Projeksjonen er ikke registrert lokalt, så ingen andre søknader berøres, og det
finnes ikke noe kappløp med `OpprettSak`. Det vi gir opp, er at handlerne
kjører for Arena-søknaden lokalt. Det trengs ikke, fordi saken ikke skal
behandles hos oss.

### 7. Dokumentasjon

- `sak-datamodell.md`: tabellen `forelopig_vedtak`, `ForelopigUtfall`, hvem som
  setter `foreslatt_utfall`, og at flyten til `TIL_BESLUTNING` er bygd.
- `event/README.md` og `event/event-flow.md`: ny event og handler.

### 8. Tester

Backend følger testoppsettet som finnes i appen, uten ny teknologi (Ken,
2026-10-10): `kotlin.test`, `testApplicationWithDatabase` mot Postgres fra
`docker-compose` (`TestDatabase`), `MockAzureAdIntrospector` for token og
`QueuedEvents` for publiserte eventer. API-testene følger
`VilkarsvurderingApiTest`, og handlertesten følger `TildelSaksbehandlerTest`.

- `ForelopigVedtakApiTest`:
  - 202 og publisert event for hvert utfall, med `innstiltAvIdent` fra token og
    søknadssummen som beløp for `INNVILGE`.
  - 403 uten rolle, uten enhet, avslått i Tilgangsmaskinen, ikke tildelt.
    503 når Tilgangsmaskinen feiler.
  - 404 ukjent sak.
  - 400 for hver feltregel i steg 3, og for tekst som bryter `valider`.
  - 400 for tiltaksperiode over årsskiftet (31.12 til 01.01).
  - 400 for redusert beløp lik søknadssummen, og for beløp over gjeldende sats.
  - 400 på `tiltaksperiodeFra` når startdatoen er før den første satsen.
  - 202 for innvilgelse der ett vilkår er vurdert som ikke oppfylt.
  - 202 for innvilgelse med søknadssum lik gjeldende sats.
  - 409 `SAK_UGYLDIG_STATUS` for `OPPRETTET`, `TIL_BESLUTNING`, `INNVILGET`,
    `AVSLATT`, `AVSLUTTET`.
  - 409 `VILKAR_IKKE_VURDERT` når ett vilkår er ikke vurdert.
  - 409 `SAK_UGYLDIG_KILDE` for `ARENA`.
  - 409 `BELOP_OVER_SATS` for innvilgelse med søknadssum over satsen.
  - Ingen endring i `sak` eller `forelopig_vedtak` før handleren har kjørt.
- `ForelopigVedtakOppsettTest`: bare redusert beløp har tekst til arbeidsgiver.
  `maksSatser` er `MAKS_SATSER`. 403 uten rolle.
- `SatsTest` (ren enhetstest): `gjeldendeMaksSats` gir satsen på og etter
  `gyldigFra`, den forrige satsen dagen før en ny sats gjelder (med en lokal
  testliste med to satser), og null før den første. `MAKS_SATSER` har unike
  `gyldigFra` og bare positive beløp. Funksjonen tar derfor listen som
  parameter med `MAKS_SATSER` som standard.
- `LagreForelopigVedtakTest`: status, `foreslatt_utfall`, rad med versjon 1,
  `SakOppdatert` publisert. Samme event to ganger gir én rad. Sak ikke lenger
  `UNDER_BEHANDLING` gir `Success` uten endring. Ukjent sak gir
  `unrecoverableError`.
- `SaksbehandlingSakApiTest`: `forelopigVedtak` i `GET /saker/{sakId}`, siste
  versjon. `GET /saker/{sakId}` virker for `ARENA`. Tildeling av en
  `ARENA`-sak gir 202, og `kanTildeleMeg` er true. Testhjelperen `lagreSak`
  bruker allerede `ARENA`, så eksisterende tildelingstester dekker det.
- `SakProjectionTest`: utvid testen «SoknadInnsendt oppretter sak med status
  OPPRETTET og kilde ARENA» med at saken ikke får rader i `saksvilkar`.
- `V22` får ingen migreringstest (Ken, 2026-10-09). Antall vilkårsrader på
  Arena-saker telles i dev før og etter deploy.
- `VilkarsvurderingApiTest`: testen «oppdater når saken ikke er under
  behandling gir 409» finnes. Legg til et tilfelle med `TIL_BESLUTNING` hvis den
  bruker en annen status.

Frontend har ikke testoppsett. `pnpm typecheck` og `pnpm lint` i
`frontend/saksbehandling`, og manuell sjekk mot MSW for alle tre utfall og for
en Arena-sak.

Manuelt mot `LocalApplication` (*6b*): Arena-saken har kilde `ARENA`, ingen
vilkår og nedstrippet høyre kolonne, og kan tildeles. Den andre saken har kilde
`EKSPERTBISTAND` og kan tildeles, vurderes og sendes til beslutter.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V21__forelopig_vedtak.sql` | Ny tabell |
| `backend/src/main/resources/db/migration/V22__slett_vilkar_for_arena_saker.sql` | Sletter vilkår på Arena-saker |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | `ForelopigVedtakTable`, `ForelopigUtfall`, `lagreForelopigVedtak`, `hentSisteForelopigVedtak` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/ForelopigVedtakApi.kt` | Ny: `POST …/forelopig-vedtak` og `GET …/forelopig-vedtak/oppsett` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Sats.kt` | Ny: `MaksSats`, `MAKS_SATSER`, `gjeldendeMaksSats` |
| `backend/src/main/resources/dokumentmaler/standardtekster/forelopig_vedtak.json` | Ny: standardtekster |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Tilgangsjekker.kt` | `kildeTilBehandling` i `SakTilgangsgrunnlag` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `forelopigVedtak` i `SakDetaljer`, montering av ny rute |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | `ForelopigVedtakSendtTilBeslutter`, registrering av handler |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/LagreForelopigVedtak.kt` | Ny handler |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/projections/SakProjection.kt` | Oppretter ikke vilkår, KDoc |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/README.md`, `event-flow.md` | Ny event og handler |
| `backend/src/test/kotlin/no/nav/ekspertbistand/LocalApplication.kt` | Ny sak med kilde `ARENA` via `SakProjection(database).handle` |
| `backend/src/test/kotlin/…` | Tester over |
| `frontend/saksbehandling/src/utils/constants.ts` | `SAKSBEHANDLING_FORELOPIG_VEDTAK_URL`, `SAKSBEHANDLING_FORELOPIG_VEDTAK_OPPSETT_URL` |
| `frontend/saksbehandling/src/hooks/useSak.ts` | `forelopigVedtak` på `SakDetaljer` |
| `frontend/saksbehandling/src/hooks/useForelopigVedtak.ts` | Ny hook |
| `frontend/saksbehandling/src/hooks/useForelopigVedtakOppsett.ts` | Ny hook |
| `frontend/saksbehandling/src/utils/maksSats.ts` | Ny: `gjeldendeMaksSats` |
| `frontend/saksbehandling/src/components/forelopigVedtak/*` | Nye komponenter |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Fanen «Foreløpig vedtak», kvitteringsvisning, nedstrippet visning for Arena-saker |
| `frontend/saksbehandling/src/mocks/handlers.ts` | POST, oppsett, mock-sak i `TIL_BESLUTNING`, mock-sak med kilde `ARENA` |
| `specifications/sak-datamodell.md` | Ny tabell og flyt, Arena-saker har ingen vilkår |

## Kanttilfeller og feilmodus

- **Dobbeltklikk på «Send til beslutter».** Knappen er `loading` under kallet.
  Kommer to kall likevel, låser `FOR UPDATE` saken, men begge kan se
  `UNDER_BEHANDLING` før handleren har kjørt, og begge publiserer. Handleren
  oppdaterer bare fra `UNDER_BEHANDLING`, så eventen som kommer sist, gjør
  ingenting. Én rad og én sakslogglinje.
- **En kollega tar saken mellom skjema og innsending.** `sjekkErSaksbehandlerPåSak`
  gir 403 `IKKE_TILDELT_SAK`. Frontend viser feilen og revaliderer saken.
- **Tildeling etter innsending.** `tildelSak` endrer bare status fra `OPPRETTET`,
  så en sak i `TIL_BESLUTNING` kan tildeles uten å miste statusen. Frigjøring
  endrer heller ikke status.
- **Vilkår endres etter innsending.** `PATCH …/vilkarsvurdering` gir 409 fordi
  status ikke er `UNDER_BEHANDLING`.
- **Vilkår endres mellom API og handler.** Vilkårsvurderingen er låst i samme
  øyeblikk som status går til `TIL_BESLUTNING`, men et `PATCH` publisert før
  innsendingen kan behandles etter. `OppdaterVilkarsvurdering` hopper over den
  når status ikke er `UNDER_BEHANDLING`. Vurderingen fryses ikke som egen
  kopi i dette kortet (ADR B5 gjelder vedtaket).
- **Søknadssum som ikke er et tall.** `estimertKostnad` er `String`. For
  `INNVILGE` gir det 409 `UGYLDIG_SOKNADSSUM`, for redusert kan ikke
  sammenligningen gjøres og gir samme feil.
- **Arena-saker.** Kan vises og tildeles, men ikke saksbehandles (Ken,
  2026-10-09 og 2026-10-10). De har ingen vilkår, høyre kolonne viser bare at
  saken behandles i Arena, og foreløpig vedtak gir 409 `SAK_UGYLDIG_KILDE`
  (*2c*).
- **Arena-sak med vurderte vilkår.** Er vilkår vurdert på en Arena-sak i dev før
  V22, slettes vurderingen. Linjene i saksloggen blir stående.
- **Søknadssum over satsen.** Innvilgelse er sperret i frontend og gir 409
  `BELOP_OVER_SATS` i backend. Saksbehandler velger redusert beløp.
- **Ny sats.** En ny `MaksSats` legges til med `gyldigFra` og deployes i god tid.
  Saker med startdato før `gyldigFra` vurderes mot den gamle satsen, også etter
  deploy. Saker som er sendt, beholder beløpet. Frontend får den nye listen ved
  neste innlasting. Om beslutter skal kontrollere mot satsen, avgjøres i
  beslutterkortet.
- **Startdato før den første satsen.** Gir feil på startdatoen i frontend og 400
  i backend. Saksbehandling finnes bare i dev, og alle saker har startdato i
  2026 eller senere.
- **Startdato sent i desember.** Sluttdatoen blir 31.12, og perioden kan bli noen
  få dager. Det er tillatt.
- **Kjøring på nytt.** `IdempotencyGuard` og `event_id UNIQUE` gjør at handleren
  kan kjøre eventen to ganger uten å lage to rader. `SakProjection` får ikke
  eventen (*Tilnærming*).
- **Personopplysninger.** Begrunnelsene kan inneholde helseopplysninger. De logges
  ikke, verken i API, handler eller frontend.

## Åpne spørsmål

Ingen. Spørsmålene er besvart av Ken 2026-10-09 og 2026-10-10 og flyttet til
*Beslutninger*, *Avgrensning* og *2b*/*2c*.


## Ferdig når

- [ ] API-et publiserer `ForelopigVedtakSendtTilBeslutter` og skriver ikke selv til `sak` eller `forelopig_vedtak`
- [ ] Bare tildelt saksbehandler med tilgang (enhet, Tilgangsmaskinen `KJERNE`) kan sende, og bare fra `UNDER_BEHANDLING` med alle vilkår vurdert, på saker med kilde `EKSPERTBISTAND`
- [ ] Arena-saker har ingen vilkår (`SakProjection` og V22), vises uten faner i høyre kolonne, kan tildeles og frigjøres, og kan ikke få foreløpig vedtak
- [ ] Feltreglene per utfall håndheves i backend (400) og i databasen (CHECK)
- [ ] Tiltaksperioden kan ikke gå over årsskiftet, i frontend, backend og database
- [ ] Innvilget beløp er aldri over satsen som gjelder på startdatoen (`gjeldendeMaksSats`), og redusert beløp er under søknadssummen
- [ ] En ny sats kan legges til med `gyldigFra` og deployes før den gjelder
- [ ] Standardtekster og maks satser kommer fra `GET …/forelopig-vedtak/oppsett`, og appen starter ikke hvis en tekst mangler
- [ ] Handleren setter `TIL_BESLUTNING` og `foreslatt_utfall`, lagrer en versjonert rad med `innstilt_av_ident`, og publiserer `SakOppdatert`, idempotent
- [ ] Registreringen per miljø i `configureEventHandlers` og `configureProjectionBuilders` er uendret, bortsett fra at `LagreForelopigVedtak` er lagt til for alle miljøer
- [ ] `LocalApplication` gir én sak med kilde `EKSPERTBISTAND` (via køen) og én med kilde `ARENA` (`initTestData` kaller `SakProjection(database).handle` direkte)
- [ ] `GET /saker/{sakId}` returnerer siste foreløpige vedtak
- [ ] Fanen «Foreløpig vedtak» har skjema, forhåndsvisning og kvittering for alle tre utfall, og viser vedtaket låst etter innsending
- [ ] Innsendingen vises i «Logg» på `SakPage`
- [ ] Vilkårsvurderingen kan ikke endres etter innsending
- [ ] Begrunnelser logges ikke
- [ ] `sak-datamodell.md`, `event/README.md` og `event-flow.md` er oppdatert
- [ ] PR-beskrivelsen ber om gjennomgang av ordlyden i standardtekstene
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
