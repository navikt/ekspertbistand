# Kvittering for varsel til arbeidsgiver om mottatt, godkjent og avlyst søknad

Trello: https://trello.com/c/2HQeHav3/598-opprett-event-der-det-mangler-i-en-ikke-benekt-sammenheng
Kort-ID: `6aab93ae125b2207a83b275b`

Henger sammen med:

- Kort 645, [varsle_arbeidsgiver_vedtak.md](varsle_arbeidsgiver_vedtak.md).
  Innførte `ArbeidsgiverVarselDomain` og kvitteringen
  `ArbeidsgiverVarsletVedtakFattet`. Dette kortet bygger videre på mønsteret.
- PR #201 (lukket). Fant de tre eventene som mangler. Koden har endret seg
  siden, så vi starter på nytt fra `main`.

## Mål

Eventloggen skal vise hva vi sendte arbeidsgiver i notifikasjonsplattformen når
en søknad er mottatt, godkjent eller avlyst. I dag har vi bare idempotens-rader
som sier at kallet lyktes, ikke hva som ble sendt.

Tre nye kvitteringseventer, én per situasjon:

| Event | Publiseres av | Miljø |
|---|---|---|
| `ArbeidsgiverVarsletSoknadMottatt` | `VarsleArbeidsgiverSoknadMottattBehandlesIArena` | prod |
| `ArbeidsgiverVarsletSoknadMottatt` | `VarsleArbeidsgiverSakOpprettet` | utenfor prod |
| `ArbeidsgiverVarsletSoknadGodkjent` | `VarsleArbeidsgiverSoknadGodkjent`, én per tilsagn | alle |
| `ArbeidsgiverVarsletSoknadAvlyst` | `VarsleArbeidsgiverSoknadAvlyst` | alle |

Hver event har et `ArbeidsgiverVarselDomain` med det vi bestilte, som i kort
645. Feiler kallet mot notifikasjonsplattformen, publiseres ingen kvittering.
Eventene har bare en no-op-handler.

Vi backfiller ikke. Søknader som er mottatt, godkjent eller avlyst før
prodsetting får ingen kvittering. Det står i KDoc på eventene.

## Bakgrunn

Fra før:

- **`VarsleArbeidsgiverSoknadMottattBehandlesIArena`** (prod, på
  `TiltaksgjennomforingOpprettet`) og **`VarsleArbeidsgiverSakOpprettet`**
  (utenfor prod, på `SakOpprettet`) sender det samme: `opprettNySak` med
  tittelen «Ekspertbistand <navn> f. <fødselsdato>» og lenke til
  v1-kvitteringen, og `opprettNyBeskjed` med «Nav har mottatt deres søknad om
  ekspertbistand.», uten eksternt varsel. Subtaskene er
  `notifikasjonsplatform_ny_sak` og `notifikasjonsplatform_ny_beskjed`. Koden
  er duplisert i de to handlerne.
- **`VarsleArbeidsgiverSoknadGodkjent`** (på `TilsagnsdataLagret`) sender
  beskjed med eksternt varsel og setter saken til `FERDIG` med «Søknad
  godkjent». Beskjedteksten avhenger av hvor mange tilsagn søknaden har i
  databasen: ett gir «Søknaden er godkjent og ekspertbistand kan nå tas i
  bruk.», flere gir «Søknaden er godkjent på nytt, se oppdaterte
  opplysninger.». `eksternId` er `<soknadId>-godkjent-<tilsagnNummer>`.
- **`VarsleArbeidsgiverSoknadAvlyst`** (på `SoknadAvlystIArena`) sender
  beskjed med eksternt varsel og setter saken til `FERDIG` med «Søknad trukket
  eller avslått». `eksternId` er `<soknadId>-soknad-avlyst`.
- **`ArbeidsgiverVarselDomain`** (kort 645) har `merkelapp`, `mottaker`,
  `virksomhetsnummer`, `grupperingsid`, `beskjed` og `saksstatus`. Alle er
  påkrevd, også `beskjed.eksterntVarsel` og `saksstatus.nyLenkeTilSak`. Typen
  passer derfor ikke for varslene i dette kortet: mottatt har ny sak og ingen
  eksternt varsel eller status, og godkjent og avlyst sender
  `nyLenkeTilSak = null`.
- **`ArbeidsgiverVarsletVedtakFattet` finnes ikke i prod.**
  `VedtaksbrevArbeidsgiverJournalfoert` publiseres bare for saker med kilde
  `EKSPERTBISTAND`, og de opprettes bare utenfor prod (`OpprettSak`). Det
  finnes altså ingen `ArbeidsgiverVarselDomain` i prod sin `event_log`, bare i
  dev og lokalt.
- **`event_log` og `event_queue` bruker `Json.Default`.** Et felt med
  standardverdi utelates når det har standardverdien, og et nytt felt uten
  standardverdi gjør at eldre JSON ikke lar seg lese.

## Skisser

Kortet har ingen Figma-lenker. Ingen endring i det arbeidsgiver ser.

## Beslutninger

Kortet har ingen kommentarer, sjekklister eller vedlegg (sjekket 2026-11-16).
Beslutningene kommer fra kortbeskrivelsen og fra spec-en for kort 645.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Vi tar ikke vare på hva vi varslet arbeidsgiver når søknad mottas i prod i dag. Løsningen fra kort 645 er utgangspunktet. |
| Kortbeskrivelsen | Eventene som mangler er `ArbeidsgiverVarsletSoknadMottatt`, `ArbeidsgiverVarsletSoknadGodkjent` og `ArbeidsgiverVarsletSoknadAvlyst`. |
| Kortbeskrivelsen | Eventene legges til i eksisterende løp, men backfilles ikke. Det dokumenteres i KDoc på eventene. |
| Kortbeskrivelsen | Eventene er no-op og registreres med en no-op-handler. |
| Kortbeskrivelsen | Eventer for søknader med kilde Altinn er ikke relevant. `VarsleArbeidsgiverSoknadGodkjentKildeAltinn` endres ikke. |
| Kortbeskrivelsen | Arena-notat er ikke relevant for ikke-benekting. `JournalfoerNotatArenaSakOpprettet` endres ikke. |
| Kortbeskrivelsen | Vi starter på nytt fra `main`, ikke fra branchen til PR #201. |
| Kort 645 (Ken, 2026-11-11) | Domain-objektet tar vare på det vi sendte, og publiseres ikke hvis kallet mot notifikasjonsplattformen feiler. |
| Minne: eventmodell | Nye eventfelt bruker domain-typer i `event/domain/`, ikke API-DTO-er. |
| Minne: event sourcing | Eventloggen er masterdata. Personopplysninger kan ligge i eventer, men ikke i applogger. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Vi utvider `ArbeidsgiverVarselDomain` i stedet for å lage nye typer.** Ett
  domain-objekt beskriver én bestilling hos notifikasjonsplattformen, uansett
  situasjon. Endringen er trygg fordi typen ikke finnes i prod, og den er
  bakoverkompatibel for eventene i dev (se punkt 1 under Tilnærming). Ulempen
  er at `ArbeidsgiverVarsletVedtakFattet` mister garantien om at
  `eksterntVarsel`, `saksstatus` og `nyLenkeTilSak` alltid er satt. Alternativet
  var egne domain-typer per situasjon. Det gir strengere typer, men dupliserer
  `Mottaker`, `Beskjed` og `EksterntVarsel`.
- **Én event for mottatt søknad, uansett løp.** Arena-løpet og ny løsning
  sender det samme, og eventnavnet fra kortet skiller ikke på løp.
- **Domain-objektet bygges før vi sender, og vi sender fra det.** Som i kort
  645. En ren funksjon per situasjon lager `ArbeidsgiverVarselDomain`, og
  handleren kaller notifikasjonsplattformen med verdiene i objektet. For mottatt
  søknad deler de to handlerne funksjonen, siden de sender det samme.
- **Tekster, lenker og `eksternId` er uendret.** `eksternId` gir idempotens i
  notifikasjonsplattformen og kan ikke endres. Handler-`id` og
  subtask-navnene er også uendret, så eventer i kø ikke sender på nytt.
- **Ny subtask `arbeidsgiver_varslet_kvittert`**, samme navn som i kort 645.
  Kvitteringen publiseres i egen transaksjon etter at alle kallene har lyktes.
  En event som er midt i retry ved prodsetting får kvittering når den lykkes.
- **`ArbeidsgiverVarsletSoknadGodkjent` har `tilsagnNummer`** (som
  `TilsagnNummer.concat()`, `aar:loepenrSak:loepenrTilsagn`). En søknad kan bli
  godkjent flere ganger, én gang per tilsagn.
- **Ingen `SakOppdatert` til saksloggen.** Arena-saker har ingen sakslogg, og
  kortet ber ikke om det for ny løsning.
- **Inline no-op-handlere** i `configureEventHandlers`, som
  `ArbeidsgiverVarsletVedtakFattetNoop`. De logger ingenting.

## Avgrensning

Ikke i dette kortet:

- Backfill av kvitteringer for varsler sendt før prodsetting.
- `VarsleArbeidsgiverSoknadGodkjentKildeAltinn` og
  `JournalfoerNotatArenaSakOpprettet`.
- Nye tekster eller lenker i varslene.
- Sakslogg for varsel om mottatt søknad i ny løsning.
- Å slå sammen de to handlerne for mottatt søknad. De deler bare funksjonen
  som bygger varselet.

## Tilnærming

```
TiltaksgjennomforingOpprettet (prod) ─> VarsleArbeidsgiverSoknadMottattBehandlesIArena ─┐
SakOpprettet (utenfor prod)          ─> VarsleArbeidsgiverSakOpprettet ─────────────────┤
                                          ├─ opprettNySak     (guard: notifikasjonsplatform_ny_sak)
                                          ├─ opprettNyBeskjed (guard: notifikasjonsplatform_ny_beskjed)
                                          └─ transaksjon      (guard: arbeidsgiver_varslet_kvittert)
                                               └─> ArbeidsgiverVarsletSoknadMottatt ─> no-op

TilsagnsdataLagret ─> VarsleArbeidsgiverSoknadGodkjent ─┐
SoknadAvlystIArena ─> VarsleArbeidsgiverSoknadAvlyst ───┤
                                          ├─ opprettNyBeskjed (guard: notifikasjonsplatform_ny_beskjed)
                                          ├─ nyStatusSak      (guard: notifikasjonsplatform_ny_status_sak)
                                          └─ transaksjon      (guard: arbeidsgiver_varslet_kvittert)
                                               └─> ArbeidsgiverVarsletSoknadGodkjent / ...Avlyst ─> no-op
```

### 1. `ArbeidsgiverVarselDomain`

```kotlin
@Serializable
data class ArbeidsgiverVarselDomain(
    val merkelapp: String,
    val mottaker: Mottaker,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val sak: Sak? = null,              // ny
    val beskjed: Beskjed,
    val saksstatus: Saksstatus?,       // var påkrevd
) {
    @Serializable
    data class Sak(val tittel: String, val lenke: String)   // ny

    data class Beskjed(..., val eksterntVarsel: EksterntVarsel?)   // var påkrevd
    data class Saksstatus(..., val nyLenkeTilSak: String?)         // var påkrevd
}
```

- `sak` får standardverdi `null`, så eventene i dev uten feltet kan leses.
  De andre feltene blir nullable uten standardverdi. Eldre JSON har dem, og ny
  JSON skriver dem eksplisitt.
- KDoc oppdateres: `sak.tittel` har deltakers navn og fødselsdato, slik
  arbeidsgiver ser saken i notifikasjonsplattformen. Typen har derfor
  personopplysninger når `sak` er satt.
- `VarsleArbeidsgiverVedtakFattet` lager `saksstatus` og `eksterntVarsel` som
  lokale verdier uten null, sender fra dem og legger dem i domain-objektet.
  Oppførselen er uendret.

### 2. Nye eventer

```kotlin
@Serializable
@SerialName("arbeidsgiverVarsletSoknadMottatt")
data class ArbeidsgiverVarsletSoknadMottatt(
    val soknadId: String,
    val varsel: ArbeidsgiverVarselDomain,
    val tidspunkt: Instant,
) : EventData { override val aggregateRootId get() = soknadId }

@Serializable
@SerialName("arbeidsgiverVarsletSoknadGodkjent")
data class ArbeidsgiverVarsletSoknadGodkjent(
    val soknadId: String,
    val tilsagnNummer: String,
    val varsel: ArbeidsgiverVarselDomain,
    val tidspunkt: Instant,
) : EventData { override val aggregateRootId get() = soknadId }

@Serializable
@SerialName("arbeidsgiverVarsletSoknadAvlyst")
data class ArbeidsgiverVarsletSoknadAvlyst(
    val soknadId: String,
    val varsel: ArbeidsgiverVarselDomain,
    val tidspunkt: Instant,
) : EventData { override val aggregateRootId get() = soknadId }
```

KDoc på hver event:

- hvem som publiserer den, og at feltene er det som ble bestilt, ikke levert
- at den ikke er backfillet: varsler sendt før prodsetting har ingen kvittering
- for mottatt: at `varsel.sak.tittel` har deltakers navn og fødselsdato, og ikke
  skal logges
- konsumenter: ingen, registrert med en no-op-handler

`tidspunkt` er når bestillingen lyktes, fra en injisert `Clock`.

### 3. Handlerne

Alle fire handlerne får samme form som `VarsleArbeidsgiverVedtakFattet`:

1. Bygg `varsel` med en ren funksjon. Ingen kall mot notifikasjonsplattformen.
2. Gjør kallene som i dag, med verdiene fra `varsel`, guardet som i dag.
3. I én transaksjon, guardet på `arbeidsgiver_varslet_kvittert`: publiser
   kvitteringen og guard. Feil gir `rollback()` og `transientError`.

Ingen suspend-kall inne i `transaction {}`.

| Handler | Funksjon | `sak` | `beskjed.eksterntVarsel` | `saksstatus` |
|---|---|---|---|---|
| `...SoknadMottattBehandlesIArena`, `...SakOpprettet` | `arbeidsgiverVarselSoknadMottatt(soknad)` | tittel og v1-kvittering | `null` | `null` |
| `...SoknadGodkjent` | `arbeidsgiverVarselSoknadGodkjent(soknad, tilsagnNummer, flereTilsagn)` | `null` | dagens tekster | `FERDIG`, «Søknad godkjent», `nyLenkeTilSak = null` |
| `...SoknadAvlyst` | `arbeidsgiverVarselSoknadAvlyst(soknad)` | `null` | dagens tekster | `FERDIG`, «Søknad trukket eller avslått», `nyLenkeTilSak = null` |

- `VarsleArbeidsgiverSoknadGodkjent` slår opp antall tilsagn før den bygger
  varselet, ikke inne i `nyBeskjed` som i dag.
- `arbeidsgiverVarselSoknadMottatt` flyttes ut av handlerne, og
  `tilFødselsdato()` blir der den er.
- `VarsleArbeidsgiverSoknadAvlyst` har feil KDoc (kopiert fra godkjent). Den
  rettes når vi likevel endrer fila.

### 4. Oppsett og dokumentasjon

- `Events.kt`: tre nye eventer med KDoc, tre no-op-handlere. KDoc på
  `TiltaksgjennomforingOpprettet`, `SakOpprettet`, `TilsagnsdataLagret` og
  `SoknadAvlystIArena` er uendret, siden konsumentene er de samme.
- `event-flow.md` genereres med `EventFlowDiagram.kt`.

### 5. Tester

Mønster fra `VarsleArbeidsgiverVedtakFattetTest`. Per handler:

- Suksess: én kvittering i køen, og feltene i `varsel` er like variablene i
  GraphQL-kallene.
- Feil fra notifikasjonsplattformen: ingen kvittering, og `transientError`.
- Nytt forsøk etter suksess: ingen nye kall og ingen ny kvittering.

I tillegg:

- Godkjent: `tilsagnNummer` og teksten for andre tilsagn («godkjent på nytt»).
- `ArbeidsgiverVarselDomain` fra JSON uten `sak` lar seg lese (event fra dev
  før endringen).
- De tre eventene i `TestEventData.allEventSamples` og
  `EventDataAggregateRootIdTest`.
- `VarsleArbeidsgiverVedtakFattetTest` er grønn uten endring i forventningene.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `event/domain/ArbeidsgiverVarselDomain.kt` | `sak`, nullable `eksterntVarsel`, `saksstatus` og `nyLenkeTilSak`, KDoc |
| `event/Events.kt` | Tre nye eventer, no-op-handlere |
| `event/handlers/VarsleArbeidsgiverSoknadMottattBehandlesIArena.kt` | Bygg varsel, kvittering |
| `event/handlers/VarsleArbeidsgiverSakOpprettet.kt` | Bygg varsel, kvittering |
| `event/handlers/VarsleArbeidsgiverSoknadGodkjent.kt` | Bygg varsel, kvittering |
| `event/handlers/VarsleArbeidsgiverSoknadAvlyst.kt` | Bygg varsel, kvittering, KDoc |
| `event/handlers/VarsleArbeidsgiverVedtakFattet.kt` | Tilpasset nullable felt, uendret oppførsel |
| `event/event-flow.md` | Generert |
| `test/.../VarsleArbeidsgiver*Test.kt` (fire filer) | Kvitteringstester |
| `test/.../TestEventData.kt`, `EventDataAggregateRootIdTest.kt` | Nye eventer |

## Kanttilfeller og feilmodus

- **Nytt forsøk etter delvis suksess.** Hver subtask er guardet, og
  `eksternId` og idempotencyKey er faste. Arbeidsgiver får ett varsel, og
  kvitteringen publiseres én gang.
- **Kvitteringen feiler etter at varselet er sendt.** Transaksjonen rulles
  tilbake, og et nytt forsøk publiserer kvitteringen uten å varsle på nytt.
- **Nytt tilsagn mellom sending og kvittering (godkjent).** Feiler
  kvitteringen og et nytt tilsagn lagres før neste forsøk, kan teksten i
  kvitteringen bli «godkjent på nytt» mens beskjeden hadde den første teksten.
  Det samme gjelder en deploy med nye tekster. Vinduet er kort, og vi godtar
  det, som i kort 645.
- **Event i kø ved prodsetting.** En event som allerede er ferdig behandlet,
  kjøres ikke på nytt og får ingen kvittering. En event som er midt i retry,
  får kvittering når den lykkes.
- **Eventer i dev med gammelt `ArbeidsgiverVarselDomain`.** Leses fordi `sak`
  har standardverdi og de andre feltene bare blir nullable.
- **Personopplysninger.** `sak.tittel` i mottatt-kvitteringen har deltakers
  navn og fødselsdato. Kildeeventene har allerede hele søknaden med
  fødselsnummer, så eventloggen får ingen nye kategorier. No-op-handlerne
  logger ingenting, og feilmeldinger i handlerne nevner bare id-er.

## Åpne spørsmål

Ingen. Valgene over kan endres ved review, særlig om
`ArbeidsgiverVarselDomain` skal utvides eller om vi heller vil ha egne typer.

## Ferdig når

- [ ] `ArbeidsgiverVarselDomain` har `sak` og nullable felt, og eldre JSON lar seg lese
- [ ] `ArbeidsgiverVarsletSoknadMottatt`, `...Godkjent` og `...Avlyst` finnes med KDoc om manglende backfill, og er med i eventtestene
- [ ] De fire handlerne publiserer kvittering lik det som ble sendt, og ingen kvittering når notifikasjonsplattformen feiler
- [ ] Ingen dobbelt varsel eller dobbel kvittering ved nytt forsøk
- [ ] Handler-id-er, subtask-navn, tekster og `eksternId` er uendret
- [ ] `VarsleArbeidsgiverVedtakFattet` oppfører seg som før
- [ ] `event-flow.md` er generert
- [ ] `mvn -B package` er grønn
