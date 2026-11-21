# Velge kostnadssted ved foreløpig vedtak

Trello: https://trello.com/c/gVCEUEry/728-velge-kostnadssted-ved-forel%C3%B8pig-vedtak
Kort-ID: `6afeb9240dab8d1c38e6ad38`

Henger sammen med:

- Kort 727, «Skaffe liste over alle koststed som skal brukes i ekspertbistand».
  Lista i dette kortet er midlertidig og byttes ut der.
- [test_oebs_integrasjon_dev.md](test_oebs_integrasjon_dev.md), avsnittet
  *Kostnadssteder i OeBS*. Der ble kortet skilt ut.
- [forelopig_vedtak.md](forelopig_vedtak.md) og
  [sette_av_penger_oebs_vedtak.md](sette_av_penger_oebs_vedtak.md). Skjemaet og
  bestillingen som endres.

## Mål

Saksbehandler velger kostnadssted når hen innstiller på innvilgelse, både
ordinær og med redusert beløp. Valget følger det foreløpige vedtaket til
beslutter og blir kostnadsstedet i bestillingen til OeBS. I dag sender
`BestillTilsagnIOebs` alltid `sak.behandlende_enhet`, og den antakelsen stemmer
ikke.

Saksbehandler velger fra en fast liste med de tolv kjente
arbeidslivssentrene. Backend eier lista, sender den til frontend og avviser
kostnadssteder som ikke står på den.

Avslag har ikke kostnadssted, fordi ingen penger settes av.

## Bakgrunn

- **Skjemaet** `ForelopigVedtakSkjema.tsx` har vurdering, tiltaksperiode og
  begrunnelser. Tilstanden ligger i `SakPage` (`ForelopigVedtakSkjemaTilstand`),
  og `validerSkjema` i `forelopigVedtak.ts` lager `ForelopigVedtakInput`.
  «Forhåndsvis foreløpig vedtak» viser `ForelopigVedtakOppsummering` i modus
  `forhandsvisning`. Samme komponent viser innstillingen til beslutter og etter
  vedtak (modus `sendt`).
- **Oppsettet** `GET /api/saksbehandling/v1/forelopig-vedtak/oppsett` gir
  standardtekster og maks satser. Det krever rollen `SAKSBEHANDLER`, så
  beslutter kan ikke lese det.
- **API-et** `POST …/saker/{sakId}/forelopig-vedtak` tar `ForelopigVedtakRequest`,
  sjekker feltene i `validerFelter` og publiserer
  `ForelopigVedtakSendtTilBeslutter`. Forhåndsvisningen av brevet
  (`SakDokumentApi.kt`) bruker samme request og `validerFelter`.
- **Lagringen** `LagreForelopigVedtak` skriver raden i `forelopig_vedtak`
  (`ForelopigVedtakTable`). `GET /saker/{sakId}` gir siste versjon som
  `ForelopigVedtakDTO`, og `tilstandFraForelopigVedtak` fyller skjemaet fra den
  etter retur.
- **Vedtaket** `VedtakFattet` har `vedtak: VedtakDomain`, bygd fra
  `ForelopigVedtakDTO.tilDomain()`. Handlerne skal bruke dataene i eventen, ikke
  projeksjonene.
- **Bestillingen** `BestillTilsagnIOebs` henter `BestillingGrunnlag` fra
  databasen og setter `kostnadssted = NavEnhetNummer(behandlendeEnhet)`.
  `NavEnhetNummer` krever fire sifre.
- **OeBS** (avklart 2026-11-17, se `test_oebs_integrasjon_dev.md`): 1891 mangler
  i T1, og 1991 er ikke aktiv i prod. 2103 (Nav Vikafossen) finnes i T1, men står
  ikke på lista i kortet.
- **Aksel `Combobox`** har enkeltvalg, men ingen grupper eller overskrifter i
  nedtrekkslista (sjekket med Aksel MCP 2026-11-20). Aksel `Select` er en native
  `<select>` og tar vilkårlige barn, så `<optgroup>` virker (sjekket
  2026-11-21). Saksbehandlingsfrontenden bruker ikke `Select` i dag.
- **`forelopig_vedtak`** har `innstilt_av_ident` og `sendt_tidspunkt`. Med
  kolonnen `kostnadssted` kan vi finne kostnadsstedene en saksbehandler har brukt
  sist.
- **Frontend for saksbehandling** har bare Playwright-tester under `e2e/`, ingen
  enhetstester.

## Skisser

Figma-fil `lvEwrzOupYmavMI2QmsMPI`, lest med Figma MCP 2026-11-20.

| Tilstand | Ramme | Lenke |
|----------|-------|-------|
| Skjema med åpen nedtrekksliste | `1215:15767` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1215-15767> |
| Skjema med valgt kostnadssted | `224:18685` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=224-18685> |
| Forhåndsvisning, skrivebeskyttet | `27:4374` | <https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=27-4374> |

Alle tre viser fanen «Foreløpig vedtak» med vurderingen «Innvilge».

- **Skjemaet** (`224:18685`): feltet «Kostnadssted» står under «Tiltaksperiode»
  og over «Deltaker - tilpass begrunnelse». Beskrivelsen er plassholderen «Kan vi
  ha noen nyttig hjelpetekst her...». Feltet viser «Nav arbeidslivssenter
  Innlandet (0491)».
- **Nedtrekkslista** (`1215:15767`) har to grupper. «Mest brukte» har det valgte
  kostnadsstedet med hake og «Nav Innlandet fylke (0377)». Deretter kommer
  «Alle».
- **Forhåndsvisningen** (`27:4374`): kostnadsstedet står som et låst felt med
  hengelås under den låste begrunnelsen til deltaker, over lenkene til brevene og
  knappene «Forrige» og «Send til beslutter». Infokortet viser ikke
  kostnadsstedet.

Kostnadsstedet settes inn mellom tiltaksperioden og begrunnelsene. Resten av
skjemaet står i samme rekkefølge som i dag.

## Beslutninger

Kortet har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-20).
Beslutningene kommer fra kortbeskrivelsen og fra
`test_oebs_integrasjon_dev.md`.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Kostnadssted er ikke alltid behandlende enhet. Saksbehandler må kunne velge kostnadssted når hen innstiller på vedtak. |
| Kortbeskrivelsen | Til kort 727 er ferdig, hardkoder vi de tolv arbeidslivssentrene i beskrivelsen. |
| Kortbeskrivelsen | Aksel `Combobox` med enkeltvalg. Endret av Ken 2026-11-21, se under. |
| Kortbeskrivelsen (spørsmål) | «Kan vi lage mest brukte som et valg øverst?» Besvart av Ken 2026-11-21, se under. |
| Ken, 2026-11-20 (`test_oebs_integrasjon_dev.md`) | Saksbehandler skal kunne velge enhet, og vi må ha en liste over enhetene og sikre at alle finnes i OeBS. Listen og OeBS-sjekken hører til kort 727. |
| Ken, 2026-11-21 (review) | Vi skal helst ha en gruppe med mest eller sist brukte, og kanskje må vi lage noe eget som ser ut som `Combobox`. |
| Ken, 2026-11-21 (review) | Sakens behandlende enhet er forhåndsvalgt. |
| Ken, 2026-11-21 (review) | Hjelpeteksten er «Enheten som skal belastes for tilskuddet.» |
| Ken, 2026-11-21 (review) | Nav Vikafossen (2103) skal ikke stå på lista. |
| Ken, 2026-11-21 (review) | Lista er de tolv enhetene i kortet, også 1991, til kort 727 har avklart alle kostnadssteder. |
| Ken, 2026-11-21 (review) | Vi bruker Aksel `Select` med grupper inntil videre. Gir kort 727 mange flere kostnadssteder, må vi vurdere komponenten på nytt. |
| Minne: eventmodell | Nye eventfelt bruker domain-typer i `event/domain/`, ikke API-DTO-er. Handlere bruker dataene i eventen. |
| Minne: kodekonvensjoner | Nye parametere får ingen standardverdi. Unntaket er felt i eventer, der gamle eventer må kunne leses. |

### Valg i spec-en

Disse står ikke i kortet. De ble foreslått i spec-en og godtatt i review
2026-11-21:

- **Backend eier lista.** `KOSTNADSSTEDER` ligger i backend, går til frontend
  gjennom oppsettet og brukes til å validere requesten. Kort 727 bytter da ut
  innholdet ett sted.
- **Vi lagrer bare enhetsnummeret** i eventen, tabellen og `VedtakDomain`. Navnet
  er visning og slås opp i lista.
- **Kostnadsstedet er påkrevd ved innvilgelse og skal ikke settes ved avslag**,
  som tiltaksperioden.
- **Sakens behandlende enhet er forhåndsvalgt** når den står på lista (avklart).
  Ellers er feltet tomt. Saksbehandler ser verdien i forhåndsvisningen før hen
  sender.
- **«Sist brukte» er per saksbehandler.** Gruppen har de tre siste ulike
  kostnadsstedene saksbehandleren har sendt til beslutter, nyeste først, og bare
  de som står på lista. Vi velger «sist brukte» framfor «mest brukte» fordi den
  følger med når en saksbehandler bytter område, og fordi det er én enkel spørring.
  Saksbehandler uten tidligere innstillinger får ingen gruppe. Grunnlaget er
  hens egne innstillinger, så ingen ser andres data.
- **Gruppen «Alle»** har alle tolv i fast rekkefølge, også de som står under
  «Sist brukte», som i skissen.
- **Komponenten er Aksel `Select` med `<optgroup>`** (avklart). En
  native `<select>` gir grupper, tastatur og skjermleser uten egen kode. Vi mister
  søk og utseendet til `Combobox` i skissen, men tolv valg kan leses uten søk. En
  egen combobox med grupper krever ARIA-mønsteret for combobox og listbox, med
  tastaturnavigasjon, gruppeoverskrifter og filtrering, og må testes med
  skjermleser. Det er mer kode å eie enn feltet er verdt nå.
- **Hjelpeteksten er «Enheten som skal belastes for tilskuddet.»** Formuleringen
  følger VALP, som kaller feltet «Nav-enhet som skal belastes for bestillingen».
- **Gamle foreløpige vedtak uten kostnadssted** finnes bare i dev, fordi ny
  saksbehandling ikke er i prod. `BestillTilsagnIOebs` bruker behandlende enhet
  når kostnadssted mangler, som i dag. Frontend viser ikke feltet når verdien
  mangler.

## Avgrensning

- Ingen endring i Arena-saker.
- Beslutter kan ikke endre kostnadssted, og vi legger ikke til en egen
  returårsak for feil kostnadssted. Beslutter returnerer med dagens årsaker.
- Kostnadssted styrer ikke tilgang. I tiltaksadministrasjon må beslutter høre til
  kostnadsstedet. Det tar vi ikke med her.
- Ingen statistikk over hva som er mest brukt på tvers av saksbehandlere.
- Vedtaksbrevene og saksloggen endres ikke.
- Nav Vikafossen (2103) står ikke på lista. Saksbehandler på strengt fortrolige
  saker velger et arbeidslivssenter.
- Hvilke kostnadssteder som finnes i OeBS, og hva vi gjør med 1891 og 1991,
  hører til kort 727.

## Tilnærming

🔴 Rød sone: validering av kostnadssted (input fra saksbehandler) og hvilket
kostnadssted OeBS belaster (økonomi). Gå gjennom steg 2 og 4 nøye.

### 1. Liste over kostnadssteder

Ny fil `saksbehandling/Kostnadssted.kt`:

```kotlin
@Serializable
data class Kostnadssted(val enhetsnummer: String, val navn: String)

/** Midlertidig liste til kort 727 er ferdig. */
val KOSTNADSSTEDER: List<Kostnadssted> = listOf(
    Kostnadssted("0291", "Nav arbeidslivssenter Øst-Viken"),
    // … de tolv enhetene fra kortet
)
```

Navnet er uten enhetsnummer. Frontend viser «navn (enhetsnummer)», som i
skissen.

`ForelopigVedtakOppsett` får feltene `kostnadssteder: List<Kostnadssted>` og
`sistBrukteKostnadssteder: List<Kostnadssted>`. Oppsettet er i dag en fast verdi.
Endepunktet svarer nå med `oppsett.copy(sistBrukteKostnadssteder = …)` for den
innloggede saksbehandleren, slik at frontend fortsatt henter alt i ett kall.

### 2. Backend: API

- `ForelopigVedtakRequest` får `kostnadssted: String? = null`, som de andre
  valgfrie feltene.
- `validerFelter`:
  - `INNVILGE` og `INNVILGE_REDUSERT`: `kostnadssted` er påkrevd og må finnes i
    `KOSTNADSSTEDER`. Ellers `UgyldigInputException("kostnadssted", "påkrevd")`
    eller `("kostnadssted", "ukjent kostnadssted")`, som gir 400.
  - `AVSLAA`: `kostnadssted` gir `UgyldigInputException("kostnadssted", "skal
    ikke settes")`.
- `publiserForelopigVedtak` sender `kostnadssted` videre til eventen.

Forhåndsvisningen av brevet bruker `validerFelter` og får derfor samme regler.
Frontend sender kostnadsstedet også der.

### 3. Event, lagring og domain

- `ForelopigVedtakSendtTilBeslutter` får `kostnadssted: String? = null`.
  Standardverdien trengs for å lese gamle eventer i dev.
- Migrering `V40__forelopig_vedtak_kostnadssted.sql`:
  `ALTER TABLE forelopig_vedtak ADD COLUMN kostnadssted TEXT NULL`. Ingen
  utfylling av gamle rader.
- `ForelopigVedtakTable.kostnadssted` og `lagreForelopigVedtak` skriver feltet.
- `ForelopigVedtakDTO` får `kostnadssted: String?` og `kostnadsstedNavn: String?`.
  Navnet slås opp i `KOSTNADSSTEDER` når DTO-en lages, og er null når
  enhetsnummeret ikke står på lista. Da kan beslutter se navnet uten å lese
  oppsettet.
- `VedtakDomain` får `kostnadssted: String?`, satt i `ForelopigVedtakDTO.tilDomain()`.
  Nullable av samme grunn som eventen.
- Ny funksjon `hentSistBrukteKostnadssteder(navIdent, antall = 3)` i `Db.kt`
  henter kostnadssted fra `forelopig_vedtak` der `innstilt_av_ident` er
  saksbehandleren og `kostnadssted` ikke er null, gruppert på kostnadssted og
  sortert på nyeste `sendt_tidspunkt`. Resultatet filtreres mot `KOSTNADSSTEDER`
  før det går ut. Tabellen er liten, så vi lager ingen ny indeks. API-et leser
  projeksjonstabellen, ikke `event_log`.

### 4. Backend: bestilling til OeBS

`BestillTilsagnIOebs` leser kostnadsstedet fra `event.data.vedtak.kostnadssted`.
Mangler det, brukes `grunnlag.behandlendeEnhet` som i dag, og handleren logger at
den falt tilbake (uten personopplysninger). Mangler begge, gir handleren
`unrecoverableError` som i dag.

`NavEnhetNummer` validerer fortsatt fire sifre.

### 5. Frontend: skjemaet

- `ForelopigVedtakOppsett` (`useForelopigVedtakOppsett.ts`) får
  `kostnadssteder` og `sistBrukteKostnadssteder`, begge
  `{ enhetsnummer: string; navn: string }[]`.
- `SakDetaljer` (`useSak.ts`) får `behandlendeEnhet: string | null`, som backend
  sender allerede. `ForelopigVedtak` får `kostnadssted` og `kostnadsstedNavn`.
- `ForelopigVedtakSkjemaTilstand` får `kostnadssted: string | null`.
  `startTilstand` tar sakens behandlende enhet og lista, og setter verdien når
  enheten står på lista. `tilstandFraForelopigVedtak` bruker kostnadsstedet fra
  innstillingen.
- `ForelopigVedtakInput` får `kostnadssted: string | null`.
- `validerSkjema` får feltet `kostnadssted`. Ved innvilgelse gir tomt felt «Du må
  velge kostnadssted.» Ved avslag sendes `null`.
- Skjemaet viser en Aksel `Select` under tiltaksperioden når `harTiltaksperiode(utfall)`:
  - `label="Kostnadssted"`, `description="Enheten som skal belastes for tilskuddet."`
  - Et tomt førstevalg «Velg kostnadssted» når ingenting er valgt.
  - `<optgroup label="Sist brukte">` med `sistBrukteKostnadssteder`, bare når
    lista ikke er tom.
  - `<optgroup label="Alle">` med alle `kostnadssteder`.
  - Hvert valg viser «navn (enhetsnummer)» og har enhetsnummeret som `value`.
    Samme enhet kan stå i begge gruppene. Nettleseren viser da det første
    treffet som valgt, og verdien er den samme.
  - `value` fra tilstanden og `onChange` for å oppdatere den.
  - `id="forelopig-vedtak-kostnadssted"`, så `ErrorSummary` kan lenke til feltet.

### 6. Frontend: forhåndsvisning og beslutter

`ForelopigVedtakOppsummering` viser kostnadsstedet som et skrivebeskyttet
`TextField` med `label="Kostnadssted"` under begrunnelsen til deltaker, som i
`27:4374`. Verdien er «navn (enhetsnummer)», eller bare enhetsnummeret når navnet
mangler. Feltet vises ikke når kostnadssted mangler, altså ved avslag og for
gamle innstillinger.

- I modus `forhandsvisning` kommer navnet fra oppsettet, som skjemaet allerede
  har lastet. Komponenten får navnet som prop, så den ikke trenger oppsettet.
- I modus `sendt` kommer navnet fra `kostnadsstedNavn` i `ForelopigVedtak`.

### 7. MSW-mock og lokale data

- `mocks/handlers.ts`: oppsettet får `kostnadssteder` og
  `sistBrukteKostnadssteder`. Mocket foreløpig vedtak og sak får `kostnadssted`,
  `kostnadsstedNavn` og `behandlendeEnhet`.
- `LokaleSaker.kt` og `TestEventData.kt`: innvilgede foreløpige vedtak får et
  kostnadssted fra lista.

### 8. Dokumentasjon

- `oebs/README.md`: punktet *Kostnadssteder* sier at saksbehandler velger
  kostnadssted, med behandlende enhet som reserve for gamle innstillinger. Lista
  er fortsatt kort 727.
- `specifications/sak-datamodell.md`: kolonnen `kostnadssted` i `forelopig_vedtak`.
- KDoc på `ForelopigVedtakSendtTilBeslutter`, `VedtakDomain` og
  `BestillTilsagnIOebs`.

Ingen nye eventer eller handlere, så `event-flow.md` regenereres ikke.

### 9. Tester

Backend følger eksisterende oppsett.

- `ForelopigVedtakApiTest`:
  - 202 og `kostnadssted` i eventen for `INNVILGE` og `INNVILGE_REDUSERT`.
  - 400 på `kostnadssted` når det mangler ved innvilgelse, er ukjent, eller er
    satt ved avslag.
- `ForelopigVedtakOppsettTest`: `kostnadssteder` er `KOSTNADSSTEDER`.
  `sistBrukteKostnadssteder` er tom for en saksbehandler uten innstillinger, og
  ellers de tre siste ulike kostnadsstedene hen har brukt, nyeste først. Andre
  saksbehandleres valg og kostnadssteder som ikke står på lista tas ikke med.
- `LagreForelopigVedtakTest`: kostnadsstedet lagres. En event uten
  `kostnadssted` (JSON fra før endringen) leses og lagres med null.
- `SaksbehandlingSakApiTest`: `kostnadssted` og `kostnadsstedNavn` i
  `forelopigVedtak`, og null navn for et enhetsnummer som ikke står på lista.
- `BestillTilsagnIOebsTest`: bestillingen bruker kostnadsstedet fra vedtaket, ikke
  behandlende enhet. Uten kostnadssted brukes behandlende enhet.
- `KOSTNADSSTEDER` har unike enhetsnumre med fire sifre, så `NavEnhetNummer`
  godtar alle.

Frontend: `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling`, og manuell
sjekk mot MSW for innvilgelse, redusert beløp, avslag, retur og beslutters
visning. Ingen ny Playwright-test.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V40__forelopig_vedtak_kostnadssted.sql` | Ny kolonne |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Kostnadssted.kt` | Ny: `Kostnadssted`, `KOSTNADSSTEDER` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/ForelopigVedtakApi.kt` | Request, `validerFelter`, oppsett med sist brukte, publisering |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | Tabell, lagring, `ForelopigVedtakDTO`, `hentSistBrukteKostnadssteder` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | Felt i `ForelopigVedtakSendtTilBeslutter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/domain/VedtakDomain.kt` | Felt `kostnadssted` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/domain/TilDomain.kt` | Mapping |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/BestillTilsagnIOebs.kt` | Kostnadssted fra vedtaket |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/README.md` | Oppdatert punkt |
| `backend/src/test/kotlin/…` | Testene i steg 9, `LokaleSaker.kt`, `TestEventData.kt` |
| `frontend/saksbehandling/src/hooks/useForelopigVedtakOppsett.ts` | `kostnadssteder`, `sistBrukteKostnadssteder` |
| `frontend/saksbehandling/src/hooks/useSak.ts` | `behandlendeEnhet`, `kostnadssted`, `kostnadsstedNavn` |
| `frontend/saksbehandling/src/hooks/useForelopigVedtak.ts` | `kostnadssted` i input |
| `frontend/saksbehandling/src/components/forelopigVedtak/forelopigVedtak.ts` | Tilstand, start, retur, validering |
| `frontend/saksbehandling/src/components/forelopigVedtak/ForelopigVedtakSkjema.tsx` | `Select` med grupper |
| `frontend/saksbehandling/src/components/forelopigVedtak/ForelopigVedtakOppsummering.tsx` | Skrivebeskyttet felt |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Starttilstand med behandlende enhet og navn til oppsummeringen |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Mockdata |
| `specifications/sak-datamodell.md` | Ny kolonne |

## Kanttilfeller og feilmodus

- **Behandlende enhet står ikke på lista** (for eksempel 2103): feltet starter
  tomt, og saksbehandler må velge.
- **Ny saksbehandler uten innstillinger:** gruppen «Sist brukte» vises ikke.
- **Saksbehandler bytter fra innvilgelse til avslag og tilbake:** kostnadsstedet
  står i tilstanden, men sendes ikke ved avslag.
- **Retur fra beslutter:** skjemaet starter med kostnadsstedet fra innstillingen.
- **Lista endres i kort 727, og et lagret kostnadssted forsvinner fra den:**
  visningen viser bare enhetsnummeret. Ved ny innsending etter retur avviser
  backend verdien, og saksbehandler må velge på nytt.
- **Gammel innstilling uten kostnadssted (bare dev):** bestillingen bruker
  behandlende enhet, og frontend viser ikke feltet.
- **OeBS avviser kostnadsstedet** (1891 i T1): bestillingen får `FEILET` som i
  dag. Feilhåndtering er kort 724.

## Åpne spørsmål

Ingen. Alle spørsmål ble avklart 2026-11-21 (se *Beslutninger*).

## Ferdig når

- Saksbehandler velger kostnadssted ved innvilgelse og redusert beløp, med
  gruppene «Sist brukte» og «Alle», og ser det låst i forhåndsvisningen.
- Beslutter ser kostnadsstedet på innstillingen.
- Backend avviser manglende eller ukjent kostnadssted ved innvilgelse, og
  kostnadssted ved avslag.
- `BestillTilsagnIOebs` sender kostnadsstedet fra vedtaket til OeBS.
- Gamle eventer og rader uten kostnadssted kan fortsatt leses.
- Testene i steg 9 er grønne, og `pnpm typecheck` og `pnpm lint` går gjennom.
