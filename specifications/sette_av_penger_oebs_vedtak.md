# Sette av penger i OeBS når vedtak er fattet, med saksnummer som matcher OeBS

Trello:

- https://trello.com/c/abzeX7gO/650-sette-av-penger-i-oebs-n%C3%A5r-vedtak-er-fattet
  (kort-ID `6acf8995a55149a3014ca6b0`)
- https://trello.com/c/NxvZl7bH/627-endre-id-p%C3%A5-sak-til-%C3%A5-v%C3%A6re-et-l%C3%B8penummer-matcher-oebs
  (kort-ID `6ac89572cf96df1d80b4e9d7`)

## Mål

Når beslutter fatter et innvilget vedtak, legger vi en bestilling til OeBS i
outboxen. Bestillingen setter av det innvilgede beløpet for tiltaksperioden.
Avslag gir ingen bestilling (kort 650).

Bestillingsnummeret bygger på sakens eget saksnummer (kort 627). `OpprettSak`
gir hver ny sak et saksnummer på formen `<år>/<løpenummer>`, for eksempel
`2026/1000000`, i ny kolonne `sak.saksnummer`. Bestillingsnummeret blir
`E-2026/1000000-1`. Formatet er forslaget teamet sendte OeBS i OEBS-2283, med
løpenummer fra 1 000 000, og OeBS har godtatt det (2026-10-28).
Saksnummeret brukes bare mot OeBS. `sak.sak_id` og journalføringen er uendret.

En ny handler, `BestillTilsagnIOebs`, lytter på `VedtakFattet`. Den bygger
`OpprettBestilling` fra vedtaket, det foreløpige vedtaket, saken og søknaden, og
kaller `OebsKlient.opprettBestilling` i samme transaksjon som den lagrer
koblingen mellom vedtaket og bestillingsnummeret. Bestillingsnummeret er
tilsagnsnummeret som tilsagnsbrevet og utbetalingen trenger senere.

Kortene kobler ikke outbox-polleren inn i appen. Meldingene blir liggende som
`PENDING` til polleren startes, og det hører til «Integrere med OEBS for å sette
av penger og utbetale» (<https://trello.com/c/m53Ud4QY>). Se *Avgrensning*.

## Bakgrunn

Fra før:

- `oebs/` har `OebsKlient` med `opprettBestilling(sakId) { bestillingsnummer -> OpprettBestilling }`.
  Den lager bestillingsnummeret med `nesteBestillingsnummer` og legger meldingen
  i `oebs_outbox` i kallerens transaksjon. Ingen kaller den i dag.
- `OebsProcessor` (outbox-poller og status-consumer) er bygget, men
  `startOebsProsessering` er ikke koblet inn i `Application.kt`. Wiring venter på
  VALP (read-ACL og abonnement på `fager.ekspertbistand.bestillinger-v1`) og på
  OeBS (oppsett mot T1, se *Åpne spørsmål*).
- `nesteBestillingsnummer(sakId: String)` gir `<FAGSYSTEM_KILDE>-<sakId>-<løpenr>`
  og krever maks 20 tegn. `FAGSYSTEM_KILDE` er `E`, som OeBS har godtatt
  (2026-10-28). KDoc-en bruker
  allerede `E-2026/10000-1` som eksempel. Løpenummeret per sak ligger i
  `oebs_lopenummer`, med `sak_id TEXT` som nøkkel.
- `sak.sak_id` er en UUID på 36 tegn (V12, `gen_random_uuid()`). Med UUID blir
  bestillingsnummeret 40 tegn, og `require` feiler. UUID-en er primærnøkkel og
  fremmednøkkel i ni tabeller, står i alle saksbehandlings-URL-er
  (`/api/saksbehandling/v1/saker/{sakId}`, `/oversikt/:sakId`) og i `sakId` på
  eventer som allerede ligger i `event_log`.
- Ingen saks-ID vises i saksbehandling i dag, verken UUID-en eller
  `arena_sak_id`. Søkerens kvittering viser søknads-ID-en som «Saksnummer».
- `fagsak_id_mapping` (V3) gir hver søknad et løpenummer fra 1, som er
  `fagsakId` mot Joark. Søknaden journalføres før saken finnes. Det endres ikke.
- `OpprettSak` lager saken med `insertIgnore` i `opprettSakForSoknad`.
  `SakProjection` lager Arena-saker i prod.
- `AggregateRetention` sletter søknaden etter 10 år. Slettingen kaskaderer til
  `sak`, `forelopig_vedtak` og `vedtak` (V26). OeBS-radene slettes på prefikset
  `E-<sak.arena_sak_id>-`, og `oebs_lopenummer` på `sak_id = arena_sak_id`.
  Slettingen venter så lenge en melding med prefikset ikke er publisert.
- Arena har saksnummer på formen år pluss løpenummer (`arena/ArenaClient.kt`).
- `VedtakFattet` publiseres av `POST …/vedtak`. `FattVedtak` lagrer raden i
  `vedtak` (unik på `event_id`) og setter saken til `INNVILGET` eller `AVSLATT`.
  `SettSoknadStatusVedtakFattet` lytter på samme event og slår opp vedtaket med
  `hentVedtakUtfallForEvent` og `vedtakKanFattes`, fordi handlerne kjører hver
  for seg (`beslutter_vedtak.md`, *4b*).
- `forelopig_vedtak` (V21) krever for innvilgelse `innvilget_belop > 0`, begge
  datoene i tiltaksperioden, `fra <= til` og at begge datoene er i samme år.
  `innstilt_av_ident` og `sendt_tidspunkt` er saksbehandlers innstilling.
- `sak.behandlende_enhet` er Norg-enhetsnummeret med fire sifre. `OpprettSak`
  setter den alltid for saker med kilde `EKSPERTBISTAND`. Saker med strengt
  fortrolig adresse får `2103` (Nav Vikafossen).
- `soknad.virksomhetsnummer` er arbeidsgiveren som søkte. Arena bruker samme
  nummer som arrangør på tilsagnet (`statistikk/README.md`).
- `VedtakFattet` finnes bare for saker med kilde `EKSPERTBISTAND`. Slike saker
  opprettes bare utenfor prod (`OpprettSak` i `other`-blokken). I prod lager
  `SakProjection` Arena-saker.

### Hva perioden brukes til i tiltaksøkonomi

Lest i `navikt/mulighetsrommet`, `mulighetsrommet-tiltaksokonomi`, commit
`4e4f80dd` (2026-10-26). Tiltaksøkonomi oversetter `OpprettBestilling` til
meldingen til OeBS.

- `Periode` har eksklusiv `slutt`. Tiltaksøkonomi sender `sluttDato` til OeBS
  som `slutt - 1 dag`, altså inklusiv
  ([OebsMeldingMapper.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/oebs/OebsMeldingMapper.kt#L17-L45)).
- `tilsagnsAar` er startåret. OeBS bruker det til PA-prosjekt og kontering, og
  kontrakten sier at et tilsagn aldri varer over et årsskifte
  ([OebsBestilling.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/oebs/OebsBestilling.kt#L115-L122)).
  Tiltaksøkonomi sjekker det ikke selv. Månedslinjene har bare månedskode, så
  en periode over nyttår gir feil linjer.
- Tiltaksøkonomi fordeler beløpet på én bestillingslinje per kalendermåned i
  perioden, etter antall dager
  ([DivideBelopByMonthsInPeriode.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/model/DivideBelopByMonthsInPeriode.kt#L6-L22)).
  Vi sender bare beløp og periode, og kan ikke styre fordelingen. OeBS krever at
  hver linje ligger innenfor én måned
  ([OebsBestilling.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/oebs/OebsBestilling.kt#L87-L91)).
  Fakturaen fordeles på samme måte, så fordelingen påvirker ikke beløpet som
  utbetales, så lenge fakturaen har tiltaksperioden som periode.
- Tiltaksøkonomi må ha en kontering for `TILTAK_DRIFTSTILSKUDD` og
  `EKSPERTBISTAND` som dekker hele perioden. Ellers feiler bestillingen
  ([TiltaksokonomiService.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/service/TiltaksokonomiService.kt#L78-L85)).
  Tilskuddstypen sendes ikke til OeBS. Den er bare en nøkkel i oppslaget i
  `tiltak_kontering_oebs`, sammen med tiltakskode og periode, og OeBS får
  `statsregnskapsKonto` og `artsKonto` fra raden. `Tilskuddstype` har ikke
  verdien `TILTAK_EKSPERTBISTAND`, så raden Sondre la inn må ha
  `TILTAK_DRIFTSTILSKUDD` (sjekket på `main` 2026-10-26).
- Fakturaen har ingen periode mot OeBS. `fakturaDato` er datoen fakturaen ble
  godkjent. Internt fordeler tiltaksøkonomi fakturabeløpet på bestillingslinjene
  etter måned, så fakturaens periode må ligge innenfor bestillingens måneder
  ([Faktura.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/model/Faktura.kt#L79-L104)).
- Ingenting stopper en faktura etter at perioden er slutt eller i et senere år.
  Bestillingen må bare være `AKTIV`, altså ikke gjort opp eller annullert
  ([TiltaksokonomiService.kt](https://github.com/navikt/mulighetsrommet/blob/4e4f80dd06f28618f86e5e8944ee93b30b6d9d61/mulighetsrommet-tiltaksokonomi/src/main/kotlin/no/nav/tiltak/okonomi/service/TiltaksokonomiService.kt#L160-L191)).
  Mulighetsrommet-api setter fakturaens periode til tjenesteperioden, ikke
  datoen den betales. En utbetaling for desember som godkjennes i februar,
  belastes dermed fjorårets bestilling.

Refusjon inntil seks måneder etter tiltaksperioden støttes så lenge
bestillingen gjelder tiltaksperioden og refusjonsfakturaen senere sendes med
tiltaksperioden som periode. VALP og OeBS har bekreftet at bestillingen blir
stående i OeBS etter årsskiftet, og at utbetalingen kan komme året etter (se
*Beslutninger*).

Relatert, ikke del av kortene:

- `integrasjon_oebs_tiltaksokonomi_bestilling.md`: klienten, outboxen,
  nummerserien og statuslyttingen. Den specen holdt domene-eventene utenfor (A-4).
- «Prat med OEBS/økonomi om startdato, sluttdato etc.»
  (<https://trello.com/c/znIGenFd>): hva start- og sluttdato betyr for OeBS og
  for refusjonsfristen. Delvis besvart av VALP 2026-10-28, se *Beslutninger*.

## Skisser

Ingen av kortene har Figma-lenke. Endringen er bare i backend.

## Beslutninger

Kort 650 og kort 627 har ingen kommentarer, sjekklister eller vedlegg (sjekket
2026-10-26). Kort 627 har beskrivelsen «venter på avklaring fra OEBS på
nummerserie» og etiketten `BLOKKERT`. Kort 565 har tre kommentarer: en lenke til
et etterlevelseskrav om tilbakekreving, en lenke til OEBS-2283 og forespørselen
i OEBS-2283 (2026-10-26) med forslaget til nummerserie.

| Kilde | Beslutning |
|-------|-----------|
| Kort 650, beskrivelsen | Innvilget vedtak gir en bestilling for tilsagnet når vedtaket er fattet. Avslag gir ingen bestilling. Topic, kildesystem og nummerserie hører til kort 565 og ligger i `oebs/`. |
| Kort 627 og Ken, 2026-10-26 | Saken får et løpenummer som matcher OeBS, løst sammen med kort 650. Ny kolonne `saksnummer`, `sak_id` beholdes. Saksnummeret brukes bare mot OeBS: det vises ikke i saksbehandling, og journalføringen endres ikke. |
| OEBS-2283, Ken og Sondre (VALP), godtatt av Kjersti (OeBS) 2026-10-28 | Bestilling `E-<saksnummer>-<løpenr per sak>`, maks 20 tegn, og VALP avviser nummer uten `E-`. Faktura `<bestillingsnummer>-<løpenr per bestilling>`, maks 50 tegn. Oppbyggingen er den samme som for tiltaksadministrasjon. Løpenummeret starter på 1 000 000, over høyeste Arena-løpenummer i prod (298 690). Normalt får en sak én bestilling, men flere må støttes. |
| Sondre (VALP) og Ken, 2026-10-26 | Tilskuddstype `TILTAK_DRIFTSTILSKUDD` (ikke `TILTAK_EKSPERTBISTAND` fra kort 565). VALP slår opp kontoene på tilskuddstype, tiltakskode og periode, og konteringen gjelder `[2026-11-01, 2099-01-01)` i dev og prod. Kostnadssted er Norg-enhetsnummeret (`sak.behandlende_enhet`). VALP sender videre til OeBS med kilde `EKSPBIST`. |
| Ken, VALP og Kjersti (OeBS), 2026-10-26 til 2026-10-28 | En bestilling kan ikke gå over årsskiftet, men blir stående i OeBS. Refusjonsfristen er 6 måneder etter tiltaksperiodens slutt, og OeBS godtar faktura mot en bestilling med fjorårets `tilsagnsAar` så lenge perioden på utbetalingen overlapper bestillingens. For oss er perioden den samme på begge. Fordelingen per måned beholdes: OeBS og tiltaksøkonomi krever den, og den har ingen praktisk betydning for oss. |
| VALP og teamet, 2026-10-28 | Perioden på en bestilling kan ikke endres. Endepunktet er idempotent: samme bestillingsnummer på nytt gir ok, uten endring. Forlengelse krever annullering og ny bestilling, trolig med nytt tilsagn som erstatter det gamle for saksbehandler. Detaljene avklares når forlengelse løses. |
| Hilde-Mari (OeBS), Sondre (VALP) og Ken, 2026-10-26 | OeBS må gjøre funksjonelt og teknisk oppsett mot T1 før vi sender, og gir beskjed når de er klare. Start med tilsagn. VALP-consumeren for `fager.ekspertbistand.bestillinger-v1` er av, og VALP skrur den på når vi skal teste. Poststed er løst i navikt/mulighetsrommet#8264. Det ligger ingen bestillinger i dev. |
| `integrasjon_oebs_tiltaksokonomi_bestilling.md` og Ken (minne) | Bestillingen skrives til outboxen i samme transaksjon som forretningsendringen, i en handler på en event. Saksloggen skrives via `SakOppdatert`. `SakProjection` får ikke ny funksjonalitet. |

### Valg i spec-en

Disse er ikke avklart i kortene. De er valgt her og kan endres ved review:

- **`OpprettSak` setter saksnummeret, ikke en database-default.**
  `opprettSakForSoknad` setter `saksnummer = neste_saksnummer()` i
  `insertIgnore`. En default ville også gitt Arena-saker fra `SakProjection` et
  nummer, og nye nummer hver gang `sak` bygges opp på nytt. Kolonnen er unik og
  påkrevd for saker med kilde `EKSPERTBISTAND`, og tom for Arena-saker.
  Å gjenbruke `fagsak_id_mapping.fagsak_id` ble forkastet (Ken, 2026-10-26):
  sekvensen startet på 1 og overlapper med Arena, den har ikke år, og den hører
  til søknaden, ikke saken.
- **Formatet er `<år>/<løpenummer>`**, slik OEBS-2283 foreslår og OeBS har
  godtatt. Året er året saken opprettes, i norsk tid. Løpenummeret kommer fra én
  sekvens som starter på 1 000 000 og ikke nullstilles ved nyttår, så nummeret
  er unikt også uten året og overlapper ikke med Arena (Ken, 2026-10-26).
  Formatet ligger bare i SQL-funksjonen `neste_saksnummer()`, som både
  backfillen og `OpprettSak` bruker.
- **Egen tabell `sak_bestilling`** knytter vedtaket til bestillingsnummeret, med
  beløp og periode. `vedtak_id` er unik, så ett vedtak gir én bestilling.
  Normalt har en sak én bestilling, men `sak_id` er ikke unik, så en sak kan få
  flere senere, for eksempel ved omgjøring (Ken, 2026-10-26).
  Tilsagnsbrevet og utbetalingen slår opp her. Å lese bestillingsnummeret fra
  `oebs_outbox` ble forkastet: outboxen er arbeidsdata som dreneres, og den har
  ingen `sak_id`. Fremmednøklene har `ON DELETE CASCADE`, så `AggregateRetention`
  kan slette søknaden etter 10 år.
- **Egen handler, ikke en del av `FattVedtak`.** Samme mønster som
  `SettSoknadStatusVedtakFattet`: handleren slår opp vedtaket for eventen og
  prøver igjen hvis `FattVedtak` ikke har kjørt. Å bestille i `FattVedtak` er
  enklere, men blander saksbehandling og økonomi i én handler.
- **Perioden er tiltaksperioden.** Det er perioden pengene gjelder for, og den
  ligger alltid i ett kalenderår, slik OeBS krever. Å strekke perioden til
  refusjonsfristen ble forkastet: perioden ville krysset nyttår, og
  tiltaksøkonomi trenger det ikke for å ta imot fakturaen senere.
- **Data fra databasen, ikke fra eventen.** Beløp, periode og saksbehandler
  hentes fra det foreløpige vedtaket som `vedtak.forelopig_vedtak_id` peker på.
  Beslutter og tidspunkt hentes fra `vedtak`. `VedtakFattet` har ikke beløp og
  periode.
- **Ingen ny event, og ingen event får saksnummeret.** Handleren
  publiserer bare `SakOppdatert`. Tilsagnsbrevet kan lytte på `VedtakFattet` og
  vente på raden i `sak_bestilling` på samme måte. En egen `TilsagnBestilt` lages
  når en konsument trenger den.
- **Bestillingen bare utenfor prod.** `BestillTilsagnIOebs` registreres i
  `other`-blokken sammen med `OpprettSak`. Saker med kilde `EKSPERTBISTAND`
  finnes ikke i prod ennå, og polleren er ikke koblet inn.
- **Ingen endring i prod.** `OpprettSak` kjører bare utenfor prod, og
  Arena-saker får ikke saksnummer. Migreringene kjører i prod, men
  `sak_bestilling` forblir tom.

## Avgrensning

Ikke i dette arbeidet:

- Wiring av `startOebsProsessering` i `Application.kt`, topic-manifester og
  ACL for VALP: kort 565. Ende-til-ende-test i dev krever også at Valp skrur på
  consumeren sin for topicen. Mocken i *4* gjelder bare `LocalApplication` og
  `MockApplication`.
- Visning av saksnummeret i saksbehandling og i API-et (Ken, 2026-10-26).
- Journalføring på saksnummeret. Joark bruker fortsatt `fagsak_id_mapping`
  (Ken, 2026-10-26).
- Saksnummer for Arena-saker i `sak`.
- Saksnummer i statistikken (`sak_nummer` i BigQuery er fortsatt UUID-en).
- Saksnummer som URL-nøkkel.
- Utbetaling (faktura) når refusjonen er godkjent, og oppgjør av bestillingen.
  Fakturaen må ha tiltaksperioden som periode, se *Hva perioden brukes til i
  tiltaksøkonomi*.
- Annullering ved omgjøring eller klage.
- Forlengelse. Perioden på en bestilling kan ikke endres, så forlengelse krever
  annullering og ny bestilling, trolig med nytt tilsagn.
- Visning av bestillingen eller OeBS-status i saksbehandling.
- Tilsagnsbrev med tilsagnsnummer.
- Bestilling for Arena-saker. De får ikke `VedtakFattet` og bestilles fortsatt i
  Arena.

## Tilnærming

```
InnsendtSoknadJournalfoert ──> OpprettSak ── INSERT sak (saksnummer = neste_saksnummer())

VedtakFattet ──> FattVedtak ── INSERT vedtak, UPDATE sak
            │
            └──> BestillTilsagnIOebs (samme event, kjører for seg)
                  ├─ vedtak for event_id mangler, men kan fattes ── transientError
                  ├─ vedtak for event_id mangler, og er avvist ──── success, ingen endring
                  ├─ AVSLATT ───────────────────────────────────── success, ingen endring
                  ├─ sak_bestilling finnes for vedtaket ─────────── success, ingen endring
                  └─ INNVILGET, i én transaksjon:
                       ├─ OebsKlient.opprettBestilling(sak.saksnummer)
                       │     └─ nesteBestillingsnummer + oebs_outbox (PENDING)
                       ├─ INSERT sak_bestilling
                       └─ publishEventQueue(SakOppdatert, SYSTEM)

oebs_outbox ── (OebsOutboxPoller, ikke wiret ennå: kort 565) ──> fager.ekspertbistand.bestillinger-v1
```

Implementeres i to commits etter spec-commiten: først saksnummeret (kort 627),
så bestillingen (kort 650).

### 0a. Migrering: saksnummer (kort 627)

`V29__sak_saksnummer.sql`:

```sql
SET lock_timeout = '3s';

CREATE SEQUENCE IF NOT EXISTS saksnummer_seq START WITH 1000000;

CREATE OR REPLACE FUNCTION neste_saksnummer(tidspunkt TIMESTAMPTZ DEFAULT now())
    RETURNS TEXT
    LANGUAGE sql
    VOLATILE
AS
$$
SELECT extract(year FROM tidspunkt AT TIME ZONE 'Europe/Oslo')::int || '/' || nextval('saksnummer_seq')
$$;

ALTER TABLE sak ADD COLUMN IF NOT EXISTS saksnummer TEXT;

DO
$$
    DECLARE
        s RECORD;
    BEGIN
        FOR s IN
            SELECT sak_id, opprettet
            FROM sak
            WHERE kilde_til_behandling = 'EKSPERTBISTAND'
              AND saksnummer IS NULL
            ORDER BY opprettet, sak_id
            LOOP
                UPDATE sak SET saksnummer = neste_saksnummer(s.opprettet) WHERE sak_id = s.sak_id;
            END LOOP;
    END
$$;

ALTER TABLE sak
    ADD CONSTRAINT sak_saksnummer_unique UNIQUE (saksnummer),
    ADD CONSTRAINT sak_saksnummer_ekspertbistand
        CHECK (kilde_til_behandling <> 'EKSPERTBISTAND' OR saksnummer IS NOT NULL);

ALTER TABLE oebs_lopenummer RENAME COLUMN sak_id TO saksnummer;
```

Backfillen gir eksisterende saker i dev et nummer med året saken ble opprettet.
Løkken går i rekkefølgen `opprettet, sak_id`, så eldre saker får lavere
løpenummer. Et vanlig `UPDATE` kaller `nextval` i den rekkefølgen Postgres
leser radene, og den er ikke garantert.
I prod finnes ingen saker med kilde `EKSPERTBISTAND`, så den gjør ingenting der.

Exposed: `SakTable.saksnummer = text("saksnummer").nullable()` og et uttrykk
`nesteSaksnummer()` (`CustomFunction<String>("neste_saksnummer", TextColumnType())`)
i `saksbehandling/Db.kt`. `opprettSakForSoknad` setter
`it[saksnummer] = nesteSaksnummer()` i `insertIgnore`. Oppdateringen når
`behandlende_enhet` mangler rører ikke saksnummeret. `insertIgnore` bruker et
nummer fra sekvensen også når raden finnes, og det gir bare et hull.

### 0b. Migrering: bestilling (kort 650)

`V30__sak_bestilling.sql`:

```sql
SET lock_timeout = '3s';

CREATE TABLE IF NOT EXISTS sak_bestilling (
    id                BIGSERIAL   PRIMARY KEY,
    sak_id            UUID        NOT NULL REFERENCES sak (sak_id) ON DELETE CASCADE,
    vedtak_id         BIGINT      NOT NULL UNIQUE REFERENCES vedtak (id) ON DELETE CASCADE,
    bestillingsnummer TEXT        NOT NULL UNIQUE,
    belop             INT         NOT NULL CHECK (belop > 0),
    periode_fra       DATE        NOT NULL,
    periode_til       DATE        NOT NULL,
    opprettet         TIMESTAMPTZ NOT NULL,
    CHECK (periode_fra <= periode_til)
);

CREATE INDEX IF NOT EXISTS sak_bestilling_sak_id_idx ON sak_bestilling (sak_id);
```

`periode_til` lagres inklusiv, som tiltaksperioden. Bare meldingen til VALP
bruker eksklusiv slutt. `opprettet` settes fra `vedtak.fattet_tidspunkt`.

`SakBestillingTable` legges i ny fil `saksbehandling/Bestilling.kt`, så vi unngår
flere endringer i den felles `saksbehandling/Db.kt`.

### 1. Nummerserien

Nøkkelen i nummerserien heter i dag `sakId`, men den er saksnummeret, ikke
`sak.sak_id`. Begge er `String`, så en UUID sendt inn gir et bestillingsnummer
på 40 tegn, og `require` feiler først i kjøring. Vi omdøper nå, mens
`oebs_lopenummer` er tom og ingen kaller `opprettBestilling`:

- `nesteBestillingsnummer(saksnummer: String)` og
  `OebsKlient.opprettBestilling(saksnummer: String, …)`
- `OebsLopenummer.saksnummer`, kolonnen `oebs_lopenummer.saksnummer`
- KDoc-en: `<FAGSYSTEM_KILDE>-<saksnummer>-<løpenr>`, og at saksnummeret er
  `sak.saksnummer`. KDoc-en på `FAGSYSTEM_KILDE` oppdateres: OeBS har godtatt
  `E` og nummerserien (2026-10-28).
- `NummerserieTest` og `oebs/README.md`

Kolonnen omdøpes i V29 (kort 627, se 0a). Omdøpingen og
`AggregateRetention`-endringen hører til commiten for kort 627.

`AggregateRetention` bruker `sak.saksnummer` i stedet for `sak.arena_sak_id`
når den sletter OeBS-radene. Arena-saker får ingen bestilling fra oss, så
prefikset fra `arena_sak_id` treffer ingenting. Med `arena_sak_id` ville
OeBS-radene for nye saker blitt liggende etter at søknaden er slettet.

Lengde: `E-2026/1000000-1` er 16 tegn. Normalt har en sak én bestilling, men
løpenummeret per sak gjør at den kan få flere. Med under 10 bestillinger per sak
nås grensen på 20 tegn ved saksnummer 99 999 999 999. KDoc-eksempelet endres
til `E-2026/1000000-1`.

### 2. Grunnlaget for bestillingen

I `saksbehandling/Bestilling.kt`:

```kotlin
data class BestillingGrunnlag(
    val vedtakId: Long,
    val sakId: UUID,
    val saksnummer: String,
    val soknadId: UUID,
    val utfall: Saksstatus,
    val belop: Int?,
    val periodeFra: LocalDate?,
    val periodeTil: LocalDate?,
    val behandlendeEnhet: String?,
    val virksomhetsnummer: String,
    val innstiltAvIdent: String,
    val innstiltTidspunkt: Instant,
    val fattetAvIdent: String,
    val fattetTidspunkt: Instant,
)

fun hentBestillingGrunnlagForEvent(eventId: Long): BestillingGrunnlag?
fun bestillingFinnesForVedtak(vedtakId: Long): Boolean
fun lagreBestilling(grunnlag: BestillingGrunnlag, bestillingsnummer: String)
```

`hentBestillingGrunnlagForEvent` joiner `vedtak` (på `event_id`),
`forelopig_vedtak` (på `vedtak.forelopig_vedtak_id`), `sak` og `soknad`. Alle
kalles i en pågående transaksjon.

### 3. Backend: handler

Ny `event/handlers/BestillTilsagnIOebs.kt` på `VedtakFattet`. Konstruktør:
`database`, og `oebsKlient: OebsKlient = OebsKlient()` på samme måte som `clock`
i `SettSoknadStatusVedtakFattet`. `IdempotencyGuard` med subtask
`oebs_bestilling_vedtak`.

🔴 Rød sone: økonomikritisk. En dobbel bestilling setter av pengene to ganger, og
en bestilling på et avvist vedtak setter av penger uten vedtak. Gå gjennom
denne nøye.

I én transaksjon:

1. `hentBestillingGrunnlagForEvent(event.id)` er null:
   - `vedtakKanFattes(event.data)`: `FattVedtak` har ikke kjørt ferdig.
     `transientError`.
   - ellers: `FattVedtak` avviste eventen. Guard og `success`.
2. `utfall == AVSLATT`: guard og `success`.
3. `bestillingFinnesForVedtak(vedtakId)`: guard og `success`. Det skjer bare hvis
   guarden mangler etter en tidligere kjøring.
4. `belop`, `periodeFra`, `periodeTil` eller `behandlendeEnhet` er null:
   `unrecoverableError`. Skal ikke skje for innvilgede saker med kilde
   `EKSPERTBISTAND`. Loggen har bare `sakId`.
5. Ellers:
   - `bestillingsnummer = with(oebsKlient) { opprettBestilling(grunnlag.saksnummer) { nr -> … } }`
   - `lagreBestilling(grunnlag, bestillingsnummer)`
   - `publishEventQueue(SakOppdatert(utfortAvRolle = SYSTEM, utfortAvIdent = null, notat = "Bestilling til OeBS opprettet: $bestillingsnummer"))`
   - guard og `success`

Unntak gir `rollback()` og `transientError`. Siden løpenummer, outbox-rad,
`sak_bestilling` og guard committes sammen, gir en ny kjøring etter feil ikke et
nytt løpenummer.

`OpprettBestilling`:

| Felt | Verdi |
|------|-------|
| `bestillingsnummer` | Fra `nesteBestillingsnummer(sak.saksnummer)` |
| `tilskuddstype` | `TILTAK_DRIFTSTILSKUDD` |
| `tiltakskode` | `EKSPERTBISTAND` |
| `arrangor` | `Arrangor.Norsk(Organisasjonsnummer(soknad.virksomhetsnummer))` |
| `kostnadssted` | `NavEnhetNummer(sak.behandlende_enhet)` |
| `avtalenummer` | `null` |
| `belop` | `forelopig_vedtak.innvilget_belop` |
| `periode` | `Periode(start = tiltaksperiode_fra, slutt = tiltaksperiode_til + 1 dag)` |
| `behandletAv` | `OkonomiPart.NavAnsatt(forelopig_vedtak.innstilt_av_ident)` |
| `behandletTidspunkt` | `forelopig_vedtak.sendt_tidspunkt` |
| `besluttetAv` | `OkonomiPart.NavAnsatt(vedtak.fattet_av_ident)` |
| `besluttetTidspunkt` | `vedtak.fattet_tidspunkt` |
| `valuta` | `NOK` |

Registreres i `configureEventHandlers`, i `other`-blokken i
`basedOnEnvSuspending`.

### 4. OeBS-mock i `LocalApplication` og `MockApplication`

Begge appene starter `startLokalApplikasjon`, så mocken legges der én gang.
Den simulerer VALP og OeBS, så bestillinger blir «utført» og får status, uten
Kafka. Koden ligger i `src/test`, så den kommer ikke med i prod-imaget.

Ny fil `backend/src/test/kotlin/no/nav/ekspertbistand/mocks/LokalOebs.kt` med
`fun Application.startLokalOebs(database: Database)`:

- **Utgående.** Kjører den ekte `OebsOutboxPoller` med
  `TiltaksokonomiProducer(producer = MockProducer(true, …))`, med Kafkas egen
  `MockProducer` fra `kafka-clients` og autocomplete på. Outbox-raden blir
  `PUBLISHED`, og `oebs_sendt_melding` får raden sin, som i dev og prod.
- **Svar.** En egen løkke finner `PUBLISHED`-rader i `oebs_outbox` uten svar.
  For hver rad lager den en `ConsumerRecord` med headeren `fagsystem` satt til
  `EKSPERTBISTAND` og sender den til den ekte
  `TiltaksokonomiConsumer.processRecord`. Da går svaret gjennom samme
  deserialisering, revisjonslogg og `oebs_bestilling_status` som i dev og prod:

  | `meldingstype` | Topic | Status |
  |----------------|-------|--------|
  | `BESTILLING` | `BESTILLING_STATUS_TOPIC` | `AKTIV` |
  | `ANNULLERING` | `BESTILLING_STATUS_TOPIC` | `ANNULLERT` |
  | `GJOR_OPP_BESTILLING` | `BESTILLING_STATUS_TOPIC` | `OPPGJORT` |
  | `FAKTURA` | `FAKTURA_STATUS_TOPIC` | `FULLT_BETALT`, med fakturanummeret som referanse |

  Bare `BESTILLING` oppstår i dette arbeidet. De andre er med så mocken ikke må
  utvides når annullering og faktura kommer.

  Recorden får partisjon 0 og outbox-id som offset. Unik-indeksen på
  `oebs_mottatt_status (kafka_topic, kafka_partition, kafka_offset)` gjør svaret
  idempotent, og løkken bruker den samme koordinaten til å se hvilke rader som
  allerede har fått svar. Den svarer derfor bare én gang per rad, også etter en
  omstart av `LocalApplication`.
- **Logging.** `log.info("OeBS-mock: {} {} -> {}", meldingstype, referanse, status)`.
  Bestillings- og fakturanummer er ikke personopplysninger. Meldingen logges
  ikke, fordi den har Nav-identer.
- **Intervall.** Ett sekund for begge løkkene, så saksbehandler ser status nesten
  med en gang.

`startLokalApplikasjon` kaller `startLokalOebs(database)` etter
`configureEventHandlers()`. `startOebsProsessering` brukes ikke, fordi den lager
en ekte `KafkaProducer` og `CoroutineKafkaConsumer`.

Ingen `FEILET`-svar i første versjon. Feilflyten er dekket av testene til
`TiltaksokonomiConsumer`.

### 5. Dokumentasjon

- `oebs/README.md`: domene-triggeren finnes, bestillingsnummeret bygger på
  `sak.saksnummer`, `sak_bestilling`. Avsnittet om at ingen kaller `OebsKlient`
  oppdateres.
- `sak-datamodell.md`: kolonnen `saksnummer` på `sak` og tabellen
  `sak_bestilling`.
- KDoc på `opprettSakForSoknad`: saksnummeret.
- `event/README.md`: handleren.
- `event-flow.md`: generert med `EventFlowDiagram.kt`.

### 6. Tester

`kotlin.test`, `testApplicationWithDatabase`, samme oppsett som
`OpprettSakTest` og `SettSoknadStatusVedtakFattetTest`.

Kort 627:

- `OpprettSakTest`: ny sak får `saksnummer` på formen
  `<inneværende år>/<tall ≥ 1000000>`. To saker får ulike nummer, og retry
  beholder nummeret.
- `NummerserieTest`: uendret oppførsel etter omdøpingen.
- `RetentionTest`: OeBS-radene slettes på prefikset fra `sak.saksnummer`, og
  `oebs_lopenummer` på `saksnummer`. Sletting av en søknad med `sak_bestilling`
  går gjennom.

`BestillTilsagnIOebsTest.kt` (kort 650):

- innvilget vedtak gir én rad i `oebs_outbox` med `BESTILLING` og feltene i
  tabellen over, én rad i `sak_bestilling` og én `SakOppdatert` med rolle `SYSTEM`
- bestillingsnummeret er `E-<sak.saksnummer>-1` og maks 20 tegn
- `periode.slutt` er dagen etter `tiltaksperiode_til`
- avslått vedtak gir ingen outbox-rad og ingen `sak_bestilling`
- vedtak avvist av `FattVedtak` (retur først, inhabil) gir ingen endring og `success`
- `FattVedtak` ikke kjørt, saken i `TIL_BESLUTNING`: `TransientError`. Etter
  `FattVedtak` gir ny kjøring bestillingen
- samme event to ganger gir én outbox-rad, og `oebs_lopenummer.neste_lopenr` er 2

Backfillen i V29 testes ikke automatisk, fordi testdatabasen er tom når
migreringen kjører. Den sjekkes i dev etter deploy: alle saker med kilde
`EKSPERTBISTAND` har saksnummer.

Ingen endring i `TestEventData` eller `EventDataAggregateRootIdTest`, siden det
ikke kommer noen ny event.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V29__sak_saksnummer.sql` | Ny |
| `backend/src/main/resources/db/migration/V30__sak_bestilling.sql` | Ny |
| `backend/.../event/handlers/OpprettSak.kt` | Setter `sak.saksnummer` |
| `backend/.../saksbehandling/Db.kt` | `SakTable.saksnummer`, `nesteSaksnummer()` |
| `backend/.../oebs/model/Nummerserie.kt`, `oebs/Oebs.kt` | `sakId` omdøpt til `saksnummer`, KDoc |
| `backend/.../soknad/AggregateRetention.kt` | OeBS-rader slettes på `sak.saksnummer` |
| `backend/src/test/.../oebs/NummerserieTest.kt` | Omdøping |
| `backend/.../saksbehandling/Bestilling.kt` | Ny: `SakBestillingTable`, grunnlag, oppslag og lagring |
| `backend/.../event/handlers/BestillTilsagnIOebs.kt` | Ny |
| `backend/.../event/Events.kt` | Registrering i `other`, konsument i KDoc på `VedtakFattet` |
| `backend/.../oebs/README.md` | Oppdatert |
| `backend/.../event/README.md`, `event-flow.md` | Oppdatert / generert |
| `backend/src/test/.../event/handlers/OpprettSakTest.kt` | Saksnummer |
| `backend/src/test/.../soknad/RetentionTest.kt` | `sak_bestilling` slettes med søknaden |
| `backend/src/test/.../event/handlers/BestillTilsagnIOebsTest.kt` | Ny |
| `backend/src/test/.../mocks/LokalOebs.kt` | Ny: OeBS-mock med poller og simulerte svar |
| `backend/src/test/.../LocalApplication.kt` | Kaller `startLokalOebs(database)` |
| `specifications/sak-datamodell.md` | `saksnummer`, `sak_bestilling` |

## Kanttilfeller og feilmodus

- **Polleren kjører ikke i dev.** I `ekspertbistand-backend` i dev blir
  bestillingene liggende som `PENDING` i `oebs_outbox`. Ingenting når VALP eller
  OeBS før kort 565 kobler inn polleren. Det ligger ingen bestillinger i dev i
  dag (Ken, 2026-10-26), så bare bestillinger fra dette arbeidet publiseres da.
  Lokalt og i mock-backenden svarer `LokalOebs` i stedet (se *4*).
- **Ombygging av `sak`.** Gjelder bare Arena-saker fra `SakProjection`, som ikke
  har saksnummer.
- **Hull i nummerserien.** En sekvens gir hull når en transaksjon ruller
  tilbake eller `insertIgnore` treffer en eksisterende rad. Saksnummeret må være
  unikt, ikke sammenhengende.
- **Nyttår.** Saker opprettet etter midnatt 31.12 norsk tid får nytt år, men
  løpenummeret fortsetter.
- **Kafka er nede.** Påvirker ikke handleren. Outboxen tar imot.
- **OeBS avviser bestillingen.** `TiltaksokonomiConsumer` setter
  `trenger_manuell_oppfolging` og logger `log.error` uten personopplysninger.
  Handleren prøver ikke igjen. Oppfølgingen er manuell.
- **Vedtaket kommer to ganger.** `vedtak.sak_id` er unik, så bare ett vedtak blir
  lagret. Guarden og `sak_bestilling.vedtak_id UNIQUE` hindrer dobbel bestilling
  for samme vedtak.
- **Handleren krasjer etter `opprettBestilling`.** Transaksjonen ruller tilbake.
  Løpenummeret, outbox-raden og `sak_bestilling` forsvinner sammen, og neste
  kjøring bruker samme løpenummer.
- **Refusjonen kommer neste år.** Bestillingen blir stående i OeBS og tar imot
  fakturaen, så lenge fakturaperioden overlapper tiltaksperioden. Bekreftet av
  VALP og OeBS 2026-10-28.
- **Samme bestilling sendes på nytt.** VALP svarer ok og endrer ingenting, heller
  ikke periode eller beløp.
- **Tiltaksperioden starter før 2026-11-01.** Konteringen hos VALP dekker ikke
  perioden, og tiltaksøkonomi avviser bestillingen. Gjelder bare testdata i dev.
  Bestillinger i prod kommer tidligst i 2027. Test i dev med tiltaksperiode fra
  01.11.2026 eller senere.
- **Tiltaksperioden krysser nyttår.** Kan ikke skje: `forelopig_vedtak` krever
  samme år på begge datoene.
- **Strengt fortrolig adresse.** Kostnadsstedet blir `2103` (Nav Vikafossen),
  som er sakens behandlende enhet i Norg.
- **Retensjon.** `sak_bestilling` slettes med saken. OeBS-radene slettes på
  prefikset `E-<sak.saksnummer>-`, og bare når alle meldingene er publisert.
- **Rullerende deploy etter V29.** En gammel pod som kjører `AggregateRetention`
  med `sak_id` i `oebs_lopenummer`, feiler på den omdøpte kolonnen.
  Transaksjonen ruller tilbake, og neste kjøring på ny pod sletter søknaden.
- **Prod.** Ingen endring i oppførsel. Migreringene kjører, men Arena-saker får
  ikke saksnummer, og `BestillTilsagnIOebs` er ikke registrert.
- **Personopplysninger.** Bestillingen har orgnr og Nav-identer, ikke fnr.
  Identene logges bare i teamLog. Saksnummeret og bestillingsnummeret er ikke
  personopplysninger og kan stå i saksloggen.

## Åpne spørsmål

Stopper ikke implementasjonen. Stilt i #samarbeid-valp-oebs (Ken, 2026-10-26).
Faktura mot fjorårets bestilling og nummerserien er besvart av OeBS
(Kjersti, 2026-10-28), se *Beslutninger*.

1. **Klar for test i dev.** OeBS må gjøre funksjonelt og teknisk oppsett før vi
   sender, og sier fra når de er klare. Integrasjonen må gå mot OeBS T1, og
   flyttes den, berører det VALP (Hilde-Mari, OeBS, 2026-10-26). Vi vet ikke om
   oppsettet dekker PA-prosjekt for tilsagnsåret.

## Ferdig når

Kort 627:

- [ ] Nye saker med kilde `EKSPERTBISTAND` får et unikt `saksnummer` på formen `<år>/<løpenummer>` fra `OpprettSak`
- [ ] Eksisterende saker med kilde `EKSPERTBISTAND` i dev har fått saksnummer
- [ ] Arena-saker, `sak_id`, `SakProjection` og journalføringen er uendret

Kort 650:

- [ ] Innvilget vedtak gir én `BESTILLING` i `oebs_outbox` og én rad i `sak_bestilling`, i samme transaksjon, idempotent
- [ ] Bestillingsnummeret er `E-<saksnummer>-<løpenr>` og maks 20 tegn
- [ ] Avslått eller avvist vedtak gir ingen bestilling
- [ ] Handleren venter (`transientError`) til `FattVedtak` har lagret vedtaket
- [ ] Bestillingen har feltene i tabellen i *3*, med eksklusiv sluttdato
- [ ] Saksloggen viser «Bestilling til OeBS opprettet» med bestillingsnummeret
- [ ] Handleren er bare registrert utenfor prod
- [ ] Identer logges ikke utenfor teamLog
- [ ] Lokalt og i mock-backenden blir bestillingen `PUBLISHED` og får status `AKTIV` i `oebs_bestilling_status` fra `LokalOebs`, og loggen viser «OeBS-mock»

Felles:

- [ ] `oebs/README.md`, `sak-datamodell.md`, `event/README.md` og `event-flow.md` er oppdatert
- [ ] `mvn -B package` er grønn
