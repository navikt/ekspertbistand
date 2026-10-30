# Varsle arbeidsgiver om vedtak

Trello: https://trello.com/c/XZvrwKjG/645-varsle-arbeidsgiver-om-vedtak
Kort-ID: `6acf883cff6b65a16442444e`

Status: utkast, pauset til journalføringen av vedtaksbrevet til arbeidsgiver er
spesifisert (kort 691). Se *Rekkefølge*.

## Mål

Arbeidsgiver skal få beskjed på Min side – arbeidsgiver, og e-post eller SMS,
for alle vedtak beslutter fatter i ny saksbehandling: innvilgelse, innvilgelse
med redusert beløp og avslag. Saken i notifikasjonsplattformen settes til
`FERDIG` med en statustekst for utfallet.

Varselet sendes når vedtaksbrevet til arbeidsgiver er journalført, slik at
arbeidsgiver finner brevet når hen følger lenken. Journalføringen er ikke en
del av dette kortet. Se *Rekkefølge*.

Vi kvitterer hva vi varslet. Handleren publiserer eventen
`ArbeidsgiverVarsletVedtakFattet` med teksten, lenken og det eksterne varselet
slik de ble bestilt, og en linje i saksloggen.

Kvitteringen beslutter ser, sier allerede «Arbeidsgiver og deltaker vil bli
varslet om vedtaket.» (`beslutter_vedtak.md`).

## Bakgrunn

Fra før:

- `VarsleArbeidsgiverSakOpprettet` (ikke prod) oppretter sak og beskjed i
  notifikasjonsplattformen når en sak opprettes i ny løsning, med
  `grupperingsid = soknadId` (`opprett_sak.md`).
- `VedtakFattet` publiseres av vedtaks-API-et. `FattVedtak` lagrer vedtaket i
  `vedtak`, `SettSoknadStatusVedtakFattet` setter søknaden til
  `godkjent`/`avslatt` og `arbeidsgiver_tilgang_til` til nå + 455 dager, og
  `BestillTilsagnIOebs` bestiller midler ved innvilgelse.
- Malene `vedtakArbeidsgiverInnvilget`, `vedtakArbeidsgiverInnvilgetRedusert`
  og `vedtakArbeidsgiverAvslag` ligger i
  `backend/src/main/resources/dokumentmaler/templates/`
  (`forelopig_vedtak_brev.md`). Ingen kode journalfører dem ennå.
- For Arena-saker varsler `VarsleArbeidsgiverSoknadGodkjent` (på
  `TilsagnsdataLagret`, etter at tilskuddsbrevet er journalført) og
  `VarsleArbeidsgiverSoknadAvlyst` (på `SoknadAvlystIArena`). Begge sender
  beskjed og setter saken til `FERDIG`, med to idempotens-subtasks:
  `notifikasjonsplatform_ny_beskjed` og `notifikasjonsplatform_ny_status_sak`.
- `ProdusentApiKlient.opprettNyBeskjed` er idempotent på `eksternId`
  (`DuplikatEksternIdOgMerkelapp` hoppes over). `nyStatusSak` bruker
  idempotencyKey `"$grupperingsid-$statusTekst"`, setter hard delete til 455
  dager og kaster på `SakFinnesIkke`. Eksterne varsler sendes i sendevinduet
  `NKS_AAPNINGSTID`.
- `SkrivSakslogg` skriver `SakOppdatert` til `sakslogg`. `BestillTilsagnIOebs`
  publiserer `SakOppdatert` med `AktorRolle.SYSTEM` for systemhendelser.
- `EventManager` gir `TransientError` for eventer uten registrert handler, og
  lagrer handlerstatus på `handler.id`.

## Skisser

Kortet har ingen Figma-lenker. Varselet vises i notifikasjonsplattformen sin
egen visning. Lenken går til kvitteringssiden for søknaden. Hvordan
vedtaksbrevet vises der, kommer i nye skisser (Ken, 2026-10-30), og hører til
kvitteringssiden, ikke til dette kortet.

## Beslutninger

Kortet har ingen kommentarer eller vedlegg (sjekket 2026-10-30). Sjekklisten
har ett åpent punkt om teksten i e-post og SMS (Hanna lager forslag og sjekker
med jurist).

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Arbeidsgiver varsles både ved innvilgelse og avslag. Må være på plass før lansering. |
| Ken, 2026-10-30 | Vi bruker dagens tekster for beskjed, statustekst, e-post og SMS. Sjekklistepunktet om tekst løses ikke i dette kortet. |
| Ken, 2026-10-30 | Alle vedtak varsles. Vedtaksbrevet til arbeidsgiver, også ved avslag, journalføres og gjøres tilgjengelig i søknadsløsningen. Nye skisser kommer for visningen. |
| Ken, 2026-10-30 | Varselet venter på at vedtaksbrevet til arbeidsgiver er journalført, som for Arena-saker i dag. Journalføring av tilsagnsbrevet er kort 691 (<https://trello.com/c/is5SjNKk>). |
| Ken, 2026-10-30 | Vi kvitterer hva vi varslet, for ikke-benekting: ny event `ArbeidsgiverVarsletVedtakFattet`. |
| Ken, 2026-10-30 | Journalføringseventen skal ha nok data til at varselet kan sendes uten å hente utfallet fra databasen. |
| Ken, 2026-10-30 | Kortet pauses. Journalføringen løses først, og eventen designes der. |
| Ken, 2026-10-30 | `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` er feilnavngitt og skal hete `VarsleArbeidsgiverSoknadMottattBehandlesIArena`. |
| Ken, 2026-10-25 (`QA5bXVrf`) | Deltaker varsles via dokdist i eget kort. Ikke en del av dette. |
| Hanna, 2026-10-30 (`6l4XA2H5`) | Utsending av brev dekkes av egne kort. |
| Minne | `SakProjection` skal ikke få funksjonalitet for nye saker. `event_log` endres aldri, og eventer i prod endres aldri. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Én handler på journalføringseventen.** `VarsleArbeidsgiverVedtakFattet`
  lytter på eventen som sier at vedtaksbrevet til arbeidsgiver er journalført,
  og tar alt den trenger fra eventen. Alternativet, en handler på `VedtakFattet` som
  venter med `transientError` til brevet er journalført, ble forkastet: den gir
  retries uten øvre grense hvis journalføringen feiler.
- **Én journalføringsevent for alle utfall.** Navnet og feltene avgjøres i
  spec-en for journalføringen. Se *Åpne spørsmål*.
- **Saksloggen via `SakOppdatert`.** Handleren publiserer `SakOppdatert`
  (`SYSTEM`, notat «Arbeidsgiver varslet om vedtak») i samme transaksjon som
  kvitteringen, som `BestillTilsagnIOebs`. Kvitteringseventen er kilden til hva
  som ble sendt. Saksloggen viser bare at det skjedde.
- **Kvitteringen beskriver bestillingen, ikke leveransen.**
  Notifikasjonsplattformen sender e-post og SMS senere, i sendevinduet. Vi
  lagrer det vi ba om å sende, med tidspunktet bestillingen lyktes.
- **Deterministisk `eksternId`**: `<soknadId>-vedtak`. Det er ett vedtak per
  søknad. En retry gir samme beskjed, ikke en ny.
- **Klassen får nytt navn, `id` beholdes.** `id =
  "VarsleArbeidsgiverSoknadMottatt"` står, så eventer i kø i prod ikke kjøres
  på nytt.

## Rekkefølge

**Kortet er satt på pause** (Ken, 2026-10-30) til journalføringen av
vedtaksbrevet til arbeidsgiver er løst. Eventen derfra lages i det kortet, og
denne spec-en tilpasses etterpå.

Kort 691 heter «Journalfør tilsagnsbrev til arbeidsgiver» og dekker etter
navnet bare innvilgelse. Forslag: kort 691 utvides til «Journalfør vedtaksbrev
til arbeidsgiver», med alle tre malene.

- **Nå:** omdøpingen (*0*). Den er uavhengig.
- **Etter kort 691:** eventen, handleren og testene (*1*–*5*).

### Krav til journalføringseventen

Handleren skal ikke slå opp i databasen for å varsle. Eventen fra
journalføringen må derfor ha minst:

- `sakId` og `soknadId`
- utfallet: `INNVILGET` eller `AVSLATT`
- `virksomhetsnummer` og `virksomhetsnavn`
- `journalpostId`, så kvitteringen kan peke på brevet arbeidsgiver ble varslet
  om

## Avgrensning

Ikke i dette kortet:

- Ny tekst i beskjed, e-post og SMS. Sjekklistepunktet står åpent på kortet.
- Journalføring av vedtaksbrevet: kort 691 (<https://trello.com/c/is5SjNKk>).
- Visning av vedtaksbrevet i søknadsløsningen. Nye skisser kommer. Henger
  sammen med «Oppdatere kvitteringssiden for arbeidsgiver»
  (<https://trello.com/c/RZqSZ8p3>) og «Oppdatere tilskuddsbrev for
  arbeidsgiver» (<https://trello.com/c/H7jYzwos>).
- Varsling av deltaker: <https://trello.com/c/QA5bXVrf>.
- Varsel ved ny vurdering, klage eller endret vedtak. Vi har ett vedtak per
  søknad i dag.
- Leveransestatus for e-post og SMS.
- Arena-saker. De varsles som før.

## Tilnærming

### 0. Omdøping (egen commit)

- `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` →
  `VarsleArbeidsgiverSoknadMottattBehandlesIArena`, fil og klasse.
  `id = "VarsleArbeidsgiverSoknadMottatt"` endres ikke.
- Oppdater referansene i `Events.kt` (KDoc for `TiltaksgjennomforingOpprettet`
  og `register`), KDoc i `VarsleArbeidsgiverSakOpprettet`,
  `VarsleArbeidsgiverSoknadMottattTest.kt` og `opprett_sak.md`.
- Generer `event-flow.md` på nytt.

### 1. Ny event

```kotlin
@Serializable
@SerialName("arbeidsgiverVarsletVedtakFattet")
data class ArbeidsgiverVarsletVedtakFattet(
    val sakId: String,
    val soknadId: String,
    val utfall: Saksstatus,
    val virksomhetsnummer: String,
    val merkelapp: String,
    val grupperingsid: String,
    val eksternId: String,
    val journalpostId: String,
    val beskjedTekst: String,
    val lenke: String,
    val statusTekst: String,
    val epostTittel: String,
    val epostHtmlBody: String,
    val smsTekst: String,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

- `init` krever `utfall` `INNVILGET` eller `AVSLATT`, som `VedtakFattet`.
- KDoc: hvem publiserer, og at feltene er det som ble bestilt.
- Eventen får en no-op-handler, `ArbeidsgiverVarsletVedtakFattetKvittert`,
  fordi `EventManager` ellers gir `TransientError`.
- Ingen personopplysninger: virksomhetsnummer og virksomhetsnavn er
  organisasjonsdata.

### 2. `VarsleArbeidsgiverVedtakFattet`

Lytter på journalføringseventen fra kort 691. Registreres for alle miljøer.

1. Les utfall, søknad og virksomhet fra eventen. Handleren slår ikke opp i
   `vedtak` eller `soknad`. Hvilke felter eventen må ha, står i *Krav til
   journalføringseventen*.
2. Utenfor transaksjon, guardet på `notifikasjonsplatform_ny_beskjed`:
   `opprettNyBeskjed` med `grupperingsid = soknadId`,
   `eksternId = "<soknadId>-vedtak"` og lenke til kvitteringssiden
   (`/ekspertbistand/skjema/<soknadId>/kvittering`). `kvitteringsLenke` er en
   extension på `DTO.Soknad` og må få en variant som tar `soknadId`.
3. Utenfor transaksjon, guardet på `notifikasjonsplatform_ny_status_sak`:
   `nyStatusSak(FERDIG, statusTekst)`.
4. I én transaksjon, guardet på `arbeidsgiver_varslet_kvittert`: publiser
   `ArbeidsgiverVarsletVedtakFattet` og `SakOppdatert`, og guard. Feil gir
   `rollback()` og `transientError`.

Ingen suspend-kall inne i `transaction {}`.

### 3. Tekster

Fra Arena-handlerne. Innvilget er uendret. Arena sier «trukket eller avslått»
fordi `avlyst` kan bety begge. I ny løsning er avslått avslått (Ken,
2026-10-14), så avslag bruker samme tekst uten «trukket», og statusteksten
«Søknad avslått» fra kvitteringssiden. Innvilgelse med redusert beløp er
`INNVILGET` og får samme tekst som innvilgelse.

| | Innvilget | Avslått |
|-|-----------|---------|
| Beskjed | «Søknaden er godkjent og ekspertbistand kan nå tas i bruk.» | «Søknaden om ekspertbistand er avslått.» (Arena: «Søknaden om ekspertbistand trukket eller avslått.») |
| Statustekst | «Søknad godkjent» | «Søknad avslått» |
| E-posttittel | «Nav – angående søknad om ekspertbistand» | samme |
| E-post og SMS | «<virksomhetsnavn> har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.» | samme |

Tekstene ligger som konstanter ett sted. Arena-handlerne endres ikke.

### 4. Dokumentasjon

- KDoc på journalføringseventen: legg til `VarsleArbeidsgiverVedtakFattet` som
  konsument.
- `event-flow.md` genereres med `EventFlowDiagram.kt`.

### 5. Tester

Mønster fra `VarsleArbeidsgiverSoknadGodkjentTest` (`setProdusentApiResultat`)
og `SettSoknadStatusVedtakFattetTest`, med `testApplicationWithDatabase`.

- Innvilget og avslått: beskjed og `FERDIG` med riktige tekster og
  `eksternId`, og én `ArbeidsgiverVarsletVedtakFattet` og én `SakOppdatert` i
  køen.
- Ingen databaseoppslag: handleren varsler med data fra eventen alene.
- Feil fra `nyStatusSak` etter vellykket beskjed: retry sender ikke ny
  beskjed, og kvitteringen publiseres én gang.
- Retry etter suksess: ingen nye kall og ingen nye eventer.
- `ArbeidsgiverVarsletVedtakFattet` i `TestEventData.allEventSamples` og
  `EventDataAggregateRootIdTest`.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `event/handlers/VarsleArbeidsgiverSoknadMottattBehandlesIAltinn.kt` | Omdøpt til `...IArena.kt` |
| `event/handlers/VarsleArbeidsgiverSakOpprettet.kt` | KDoc-referanse |
| `event/Events.kt` | Ny event, KDoc, registrering |
| `event/handlers/VarsleArbeidsgiverVedtakFattet.kt` | Ny |
| `event/handlers/ArbeidsgiverVarsletVedtakFattetKvittert.kt` | Ny: no-op-handler |
| `soknad/Api.kt` | Kvitteringslenke fra `soknadId` |
| `event/event-flow.md` | Generert |
| `test/.../VarsleArbeidsgiverSoknadMottattTest.kt` | Klassenavn |
| `test/.../VarsleArbeidsgiverVedtakFattetTest.kt` | Ny |
| `test/.../TestEventData.kt`, `EventDataAggregateRootIdTest.kt` | Ny event |
| `specifications/opprett_sak.md` | Klassenavn |

## Kanttilfeller og feilmodus

- **Saken finnes ikke i notifikasjonsplattformen.** `nyStatusSak` kaster
  `SakFinnesIkke`, og handleren gir `transientError`. Det skjer hvis
  `VarsleArbeidsgiverSakOpprettet` ikke har kjørt. Retry løser det når saken
  kommer.
- **Retry etter delvis suksess.** Hver subtask er guardet, og `eksternId` og
  idempotencyKey er faste. Arbeidsgiver får én beskjed og ett eksternt varsel.
- **Kvitteringen feiler etter at varselet er sendt.** Transaksjonen rulles
  tilbake, og retry publiserer kvitteringen uten å varsle på nytt. Vinduet der
  varselet er sendt uten kvittering, er kort og lukkes av retry.
- **Journalføringen feiler.** Ingen varsel. Feilen hører til
  journalføringshandleren og synes der.
- **Arena-saker.** Journalføringseventen publiseres bare for saker med kilde
  `EKSPERTBISTAND`. Arena-varslene er uendret.
- **Vedtak før kort 691 er ute.** Ingen varsel. Kvitteringen til beslutter
  lover varsel, så begge kortene må være ute før lansering.
- **Personopplysninger.** Varselet og kvitteringen har bare organisasjonsdata
  og faste tekster. Ingen begrunnelse, ingen identer.

## Åpne spørsmål

1. **Utvides kort 691 til alle vedtaksbrev til arbeidsgiver**, også avslag?
   Ellers trengs et eget kort for journalføring av avslagsbrevet.
2. **Journalføringseventen.** Navn og felter avgjøres i spec-en for 691, med
   kravene i *Krav til journalføringseventen*.
3. **Lenken i beskjeden.** Kvitteringssiden i dag. Peker de nye skissene et
   annet sted, for eksempel til vedtaksbrevet, endres lenken da.

## Ferdig når

- [ ] `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` heter `...IArena`, med uendret `id`, i egen commit
- [ ] `ArbeidsgiverVarsletVedtakFattet` finnes, med det som ble bestilt, og er med i eventtestene
- [ ] Innvilgelse og avslag gir beskjed, `FERDIG` med riktig statustekst, kvittering og linje i saksloggen, etter at vedtaksbrevet er journalført, idempotent
- [ ] Ingen dobbel beskjed eller dobbelt eksternt varsel ved retry
- [ ] `event-flow.md` er generert, og KDoc på journalføringseventen er oppdatert
- [ ] `mvn -B package` er grønn
