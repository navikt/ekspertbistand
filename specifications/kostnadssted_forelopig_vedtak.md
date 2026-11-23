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
arbeidslivssentrene i en Aksel `Combobox` med enkeltvalg. Backend eier lista,
sender den til frontend og avviser kostnadssteder som ikke står på den.

Feltet er forhåndsutfylt med sakens behandlende enhet. Er saken hos Nav
Vikafossen, er det forhåndsutfylt med enheten saken hadde fått uten
adressebeskyttelse. Saksbehandler kan alltid velge et annet.

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
- **Frontend for saksbehandling** har bare Playwright-tester under `e2e/`, ingen
  enhetstester.
- **Rutingen** (`BehandlendeEnhetUtleder`) slår opp deltakerens gradering i PDL:
  - Strengt fortrolig (kode 6, også utland): Norg med diskresjonskode og
    deltakerens geografiske tilknytning. Det gir Nav Vikafossen (2103), og
    `Ruting.virksomhetKommunenummer` er null.
  - Ellers: Norg med kommunenummeret fra **virksomhetens** forretningsadresse i
    Ereg (`rutingPaaVirksomhet`). Fortrolig (kode 7) rutes som ugradert.

  Enheten saken hadde fått uten adressebeskyttelse er altså enheten for
  virksomhetens kommune. Den avslører ikke hvor deltakeren bor. For kode 6 lagrer
  vi ikke virksomhetens kommune på saken (`sak.kommunenummer` er null), så den må
  slås opp i Ereg før Norg.
- **Saker hos Nav Vikafossen er sjeldne**, anslagsvis 1 av 1000.

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
  «Alle». Gruppene er utdatert etter beslutningen 2026-11-23: lista har ingen
  grupper.
- **Forhåndsvisningen** (`27:4374`): kostnadsstedet står som et låst felt med
  hengelås under den låste begrunnelsen til deltaker, over lenkene til brevene og
  knappene «Forrige» og «Send til beslutter». Infokortet viser ikke
  kostnadsstedet.

Kostnadsstedet settes inn mellom tiltaksperioden og begrunnelsene. Resten av
skjemaet står i samme rekkefølge som i dag.

## Beslutninger

Kortet hadde ingen kommentarer, sjekklister eller vedlegg da spec-en ble skrevet
(2026-11-20). Beslutningene kom fra kortbeskrivelsen, fra
`test_oebs_integrasjon_dev.md` og fra review. 2026-11-22 lagde vi tre varianter
til demo: `Select` med gruppene «Sist brukte» og «Alle», `Combobox` uten grupper,
og `Combobox` med chips for «Mest brukt i din enhet». Teamet valgte etter
gjennomgangen 2026-11-23, og Ken skrev beslutningen som kommentar på kortet.

En senere beslutning gjelder foran en tidligere. Rader som er erstattet, er
merket.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Kostnadssted er ikke alltid behandlende enhet. Saksbehandler må kunne velge kostnadssted når hen innstiller på vedtak. |
| Kortbeskrivelsen | Til kort 727 er ferdig, hardkoder vi de tolv arbeidslivssentrene i beskrivelsen. |
| Kortbeskrivelsen | Aksel `Combobox` med enkeltvalg. Gjelder igjen fra 2026-11-23. |
| Kortbeskrivelsen (spørsmål) | «Kan vi lage mest brukte som et valg øverst?» Nei, se 2026-11-23. |
| Ken, 2026-11-20 (`test_oebs_integrasjon_dev.md`) | Saksbehandler skal kunne velge enhet, og vi må ha en liste over enhetene og sikre at alle finnes i OeBS. Listen og OeBS-sjekken hører til kort 727. |
| Ken, 2026-11-21 (review) | ~~Vi skal helst ha en gruppe med mest eller sist brukte.~~ Erstattet 2026-11-23. |
| Ken, 2026-11-21 (review) | Sakens behandlende enhet er forhåndsvalgt. Utvidet med unntak for Vikafossen 2026-11-23. |
| Ken, 2026-11-21 (review) | Hjelpeteksten er «Enheten som skal belastes for tilskuddet.» |
| Ken, 2026-11-21 (review) | Nav Vikafossen (2103) skal ikke stå på lista. |
| Ken, 2026-11-21 (review) | Lista er de tolv enhetene i kortet, også 1991, til kort 727 har avklart alle kostnadssteder. |
| Ken, 2026-11-21 (review) | ~~Aksel `Select` med grupper inntil videre.~~ Erstattet 2026-11-23. |
| Ken, 2026-11-23 (kommentar på kortet, etter gjennomgang med teamet) | Combobox med enkeltvalg. |
| Ken, 2026-11-23 (kommentar på kortet) | Ingen «mest brukt» eller «sist brukt». |
| Ken, 2026-11-23 (kommentar på kortet) | Standardverdien er behandlende enhet, og saksbehandler kan overstyre. Unntaket er Vikafossen. Der er standardverdien enheten saken hadde havnet hos hvis deltakeren ikke hadde adressebeskyttelse. |
| Ken, 2026-11-23 (review) | Enheten uten adressebeskyttelse lagres ikke ved rutingen. Er behandlende enhet Vikafossen, slår vi den opp i Norg ved behov. Det gjelder omtrent 1 av 1000 saker. |
| Ken, 2026-11-23 (review) | Kode 7 (fortrolig) er ikke relevant. Regelen gjelder bare saker der behandlende enhet er Vikafossen. |
| Minne: eventmodell | Nye eventfelt bruker domain-typer i `event/domain/`, ikke API-DTO-er. Handlere bruker dataene i eventen. |
| Minne: eventmodell | API-er slår ikke opp i `event_log`. Data til visning lagres i en egen tabell. |
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
- **Hjelpeteksten er «Enheten som skal belastes for tilskuddet.»** Formuleringen
  følger VALP, som kaller feltet «Nav-enhet som skal belastes for bestillingen».
- **Gamle foreløpige vedtak uten kostnadssted** finnes bare i dev, fordi ny
  saksbehandling ikke er i prod. `BestillTilsagnIOebs` bruker behandlende enhet
  når kostnadssted mangler, som i dag. Frontend viser ikke feltet når verdien
  mangler.

Valg etter beslutningen 2026-11-23:

- **Backend regner ut forslaget.** `GET /saker/{sakId}` får
  `foreslattKostnadssted`. Frontend forhåndsutfyller med verdien og trenger ikke
  kjenne Vikafossen-regelen.
- **For Vikafossen slår vi opp i Ereg og Norg når saken hentes.** Vi trenger
  virksomhetens kommunenummer fra Ereg før vi kan spørre Norg. Oppslaget gjøres
  bare når behandlende enhet er 2103.
- **Feiler oppslaget, er forslaget null**, og saken vises som vanlig. Feltet
  starter da tomt, og saksbehandler velger selv. Vi logger en advarsel med
  sak-ID.

## Avgrensning

- Ingen endring i Arena-saker.
- Beslutter kan ikke endre kostnadssted, og vi legger ikke til en egen
  returårsak for feil kostnadssted. Beslutter returnerer med dagens årsaker.
- Kostnadssted styrer ikke tilgang. I tiltaksadministrasjon må beslutter høre til
  kostnadsstedet. Det tar vi ikke med her.
- Ingen gruppe eller snarvei for mest eller sist brukte kostnadssteder.
- Vedtaksbrevene og saksloggen endres ikke.
- Nav Vikafossen (2103) står ikke på lista. På saker hos Vikafossen er feltet
  forhåndsutfylt med enheten saken hadde fått uten adressebeskyttelse.
- Vi lagrer ikke enheten uten adressebeskyttelse. Rutingen og eventene endres
  ikke.
- Hvilke kostnadssteder som finnes i OeBS, og hva vi gjør med 1891 og 1991,
  hører til kort 727.

## Tilnærming

🔴 Rød sone: validering av kostnadssted (input fra saksbehandler), hvilket
kostnadssted OeBS belaster (økonomi) og forslaget for saker hos Vikafossen
(personvern). Gå gjennom steg 2, 4 og 5 nøye.

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

`ForelopigVedtakOppsett` får feltet `kostnadssteder: List<Kostnadssted>`.

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

### 4. Backend: bestilling til OeBS

`BestillTilsagnIOebs` leser kostnadsstedet fra `event.data.vedtak.kostnadssted`.
Mangler det, brukes `grunnlag.behandlendeEnhet` som i dag, og handleren logger at
den falt tilbake (uten personopplysninger). Mangler begge, gir handleren
`unrecoverableError` som i dag.

`NavEnhetNummer` validerer fortsatt fire sifre.

### 5. Backend: forslag til kostnadssted

- `BehandlendeEnhetUtleder` får
  `suspend fun enhetForVirksomhet(virksomhetsnummer: String): String`. Den gjør
  det samme som `rutingPaaVirksomhet` med gradering `UGRADERT`: kommunenummer fra
  Ereg, så Norg. Finner Norg ingen enhet, gir den samme reserve som rutingen
  (0391). Mangler virksomheten kommunenummer, kastes
  `ManglerDataForBehandlendeEnhetException` som i dag.
- `SakApi.kt` henter `BehandlendeEnhetUtleder` fra `dependencies`.
  `GET /saker/{sakId}` gir `foreslattKostnadssted: String?` i `SakDetaljer`:
  1. Er behandlende enhet `NAV_VIKAFOSSEN`, kall `enhetForVirksomhet` med
     sakens virksomhetsnummer. Feiler kallet, logg en advarsel med sak-ID og bruk
     null.
  2. Ellers bruk behandlende enhet.
  3. Står enheten ikke på `KOSTNADSSTEDER`, eller mangler den, er forslaget null.

  Lista over saker (`GET /saker`) får ikke feltet, så den gjør aldri oppslag.
  Tilgangen til saken er uendret, så bare de som har tilgang til en
  Vikafossen-sak, ser forslaget.

### 6. Frontend: skjemaet

- `ForelopigVedtakOppsett` (`useForelopigVedtakOppsett.ts`) får
  `kostnadssteder: { enhetsnummer: string; navn: string }[]`.
- `SakDetaljer` (`useSak.ts`) får `foreslattKostnadssted: string | null`.
  `ForelopigVedtak` får `kostnadssted` og `kostnadsstedNavn`.
- `ForelopigVedtakSkjemaTilstand` får `kostnadssted: string | null`.
  `startTilstand` bruker `foreslattKostnadssted`. `tilstandFraForelopigVedtak`
  bruker kostnadsstedet fra innstillingen.
- `ForelopigVedtakInput` får `kostnadssted: string | null`.
- `validerSkjema` får feltet `kostnadssted`. Ved innvilgelse gir tomt felt «Du må
  velge kostnadssted.» Ved avslag sendes `null`.
- Skjemaet viser en Aksel `UNSAFE_Combobox` med enkeltvalg under
  tiltaksperioden når `harTiltaksperiode(utfall)`, som `Kommunefilter.tsx`:
  - `label="Kostnadssted"`, `description="Enheten som skal belastes for tilskuddet."`
  - `options` er alle `kostnadssteder` i fast rekkefølge, uten grupper. Hvert valg
    viser «navn (enhetsnummer)» og har enhetsnummeret som `value`.
  - `selectedOptions` fra tilstanden. `onToggleSelected` setter verdien, eller
    null når saksbehandler fjerner valget.
  - `id="forelopig-vedtak-kostnadssted"`, så `ErrorSummary` kan lenke til feltet.

### 7. Frontend: forhåndsvisning og beslutter

`ForelopigVedtakOppsummering` viser kostnadsstedet som et skrivebeskyttet
`TextField` med `label="Kostnadssted"` under begrunnelsen til deltaker, som i
`27:4374`. Verdien er «navn (enhetsnummer)», eller bare enhetsnummeret når navnet
mangler. Feltet vises ikke når kostnadssted mangler, altså ved avslag og for
gamle innstillinger.

- I modus `forhandsvisning` kommer navnet fra oppsettet, som skjemaet allerede
  har lastet. Komponenten får navnet som prop, så den ikke trenger oppsettet.
- I modus `sendt` kommer navnet fra `kostnadsstedNavn` i `ForelopigVedtak`.

### 8. MSW-mock og lokale data

- `mocks/handlers.ts`: oppsettet får `kostnadssteder`. Mocket foreløpig vedtak
  får `kostnadssted` og `kostnadsstedNavn`. Mockede saker får
  `foreslattKostnadssted`, minst én med en enhet fra lista, så forhåndsutfyllingen
  kan prøves lokalt.
- `LokaleSaker.kt` og `TestEventData.kt`: innvilgede foreløpige vedtak får et
  kostnadssted fra lista.

### 9. Dokumentasjon

- `oebs/README.md`: punktet *Kostnadssteder* sier at saksbehandler velger
  kostnadssted, med behandlende enhet som reserve for gamle innstillinger. Lista
  er fortsatt kort 727.
- `specifications/sak-datamodell.md`: kolonnen `kostnadssted` i
  `forelopig_vedtak`.
- KDoc på `ForelopigVedtakSendtTilBeslutter`, `VedtakDomain`,
  `BestillTilsagnIOebs` og `BehandlendeEnhetUtleder.enhetForVirksomhet`.

Ingen nye eventer eller handlere, så `event-flow.md` regenereres ikke.

### 10. Tester

Backend følger eksisterende oppsett.

- `ForelopigVedtakApiTest`:
  - 202 og `kostnadssted` i eventen for `INNVILGE` og `INNVILGE_REDUSERT`.
  - 400 på `kostnadssted` når det mangler ved innvilgelse, er ukjent, eller er
    satt ved avslag.
- `ForelopigVedtakOppsettTest`: `kostnadssteder` er `KOSTNADSSTEDER`.
- `LagreForelopigVedtakTest`: kostnadsstedet lagres. En event uten
  `kostnadssted` (JSON fra før endringen) leses og lagres med null.
- `SaksbehandlingSakApiTest`: `kostnadssted` og `kostnadsstedNavn` i
  `forelopigVedtak`, og null navn for et enhetsnummer som ikke står på lista.
- `BestillTilsagnIOebsTest`: bestillingen bruker kostnadsstedet fra vedtaket, ikke
  behandlende enhet. Uten kostnadssted brukes behandlende enhet.
- `KOSTNADSSTEDER` har unike enhetsnumre med fire sifre, så `NavEnhetNummer`
  godtar alle.
- `BehandlendeEnhetTest`: `enhetForVirksomhet` gir Norg-enheten for
  virksomhetens kommune, også når deltakeren har strengt fortrolig adresse.
- `SaksbehandlingSakApiTest`:
  - `foreslattKostnadssted` er behandlende enhet når den står på lista, og null
    når den ikke gjør det. Ingen kall til Ereg eller Norg.
  - For en sak hos 2103 er forslaget enheten for virksomhetens kommune.
  - For en sak hos 2103 der oppslaget feiler, er forslaget null, og saken hentes
    med 200.

Frontend: `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling`, og manuell
sjekk mot MSW for innvilgelse, redusert beløp, avslag, retur, forhåndsutfylling
og beslutters visning. Ingen ny Playwright-test.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/db/migration/V40__forelopig_vedtak_kostnadssted.sql` | Ny kolonne |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Kostnadssted.kt` | Ny: `Kostnadssted`, `KOSTNADSSTEDER` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/ForelopigVedtakApi.kt` | Request, `validerFelter`, oppsett, publisering |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/Db.kt` | Tabell, lagring, `ForelopigVedtakDTO` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandling/SakApi.kt` | `foreslattKostnadssted` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/norg/BehandlendeEnhetUtleder.kt` | `enhetForVirksomhet` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/Events.kt` | Felt i `ForelopigVedtakSendtTilBeslutter` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/domain/VedtakDomain.kt` | Felt `kostnadssted` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/domain/TilDomain.kt` | Mapping |
| `backend/src/main/kotlin/no/nav/ekspertbistand/event/handlers/BestillTilsagnIOebs.kt` | Kostnadssted fra vedtaket |
| `backend/src/main/kotlin/no/nav/ekspertbistand/oebs/README.md` | Oppdatert punkt |
| `backend/src/test/kotlin/…` | Testene i steg 10, `LokaleSaker.kt`, `TestEventData.kt` |
| `frontend/saksbehandling/src/hooks/useForelopigVedtakOppsett.ts` | `kostnadssteder` |
| `frontend/saksbehandling/src/hooks/useSak.ts` | `foreslattKostnadssted`, `kostnadssted`, `kostnadsstedNavn` |
| `frontend/saksbehandling/src/hooks/useForelopigVedtak.ts` | `kostnadssted` i input |
| `frontend/saksbehandling/src/components/forelopigVedtak/forelopigVedtak.ts` | Tilstand, start, retur, validering |
| `frontend/saksbehandling/src/components/forelopigVedtak/ForelopigVedtakSkjema.tsx` | `UNSAFE_Combobox` med enkeltvalg |
| `frontend/saksbehandling/src/components/forelopigVedtak/ForelopigVedtakOppsummering.tsx` | Skrivebeskyttet felt |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | Starttilstand med forslaget og navn til oppsummeringen |
| `frontend/saksbehandling/src/mocks/handlers.ts` | Mockdata |
| `specifications/sak-datamodell.md` | Ny kolonne |

## Kanttilfeller og feilmodus

- **Behandlende enhet står ikke på lista** (en annen enn 2103): feltet starter
  tomt, og saksbehandler må velge.
- **Sak hos Vikafossen:** feltet starter med enheten for virksomhetens kommune.
  Feiler oppslaget, mangler virksomheten kommunenummer, eller står enheten ikke
  på lista, starter feltet tomt.
- **Norg finner ingen enhet for virksomhetens kommune:** forslaget er 0391, samme
  reserve som rutingen.
- **Deltakeren mister adressebeskyttelsen etter at saken er opprettet:**
  behandlende enhet er fortsatt 2103, og oppslaget gir enheten for virksomheten.
- **Saksbehandler fjerner valget i comboboxen:** verdien blir null, og
  valideringen gir «Du må velge kostnadssted.» ved innvilgelse.
- **Saksbehandler bytter fra innvilgelse til avslag og tilbake:** kostnadsstedet
  står i tilstanden, men sendes ikke ved avslag.
- **Retur fra beslutter:** skjemaet starter med kostnadsstedet fra innstillingen,
  ikke forslaget.
- **Lista endres i kort 727, og et lagret kostnadssted forsvinner fra den:**
  visningen viser bare enhetsnummeret. Ved ny innsending etter retur avviser
  backend verdien, og saksbehandler må velge på nytt.
- **Gammel innstilling uten kostnadssted (bare dev):** bestillingen bruker
  behandlende enhet, og frontend viser ikke feltet.
- **OeBS avviser kostnadsstedet** (1891 i T1): bestillingen får `FEILET` som i
  dag. Feilhåndtering er kort 724.

## Ferdig når

- Saksbehandler velger kostnadssted ved innvilgelse og redusert beløp i en
  combobox uten grupper, og ser det låst i forhåndsvisningen.
- Feltet er forhåndsutfylt med behandlende enhet, eller med enheten uten
  adressebeskyttelse for saker hos Vikafossen.
- Beslutter ser kostnadsstedet på innstillingen.
- Backend avviser manglende eller ukjent kostnadssted ved innvilgelse, og
  kostnadssted ved avslag.
- `BestillTilsagnIOebs` sender kostnadsstedet fra vedtaket til OeBS.
- Gamle eventer og rader uten kostnadssted kan fortsatt leses.
- Testene i steg 10 er grønne, og `pnpm typecheck` og `pnpm lint` går gjennom.
