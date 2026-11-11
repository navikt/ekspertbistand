# Varsle arbeidsgiver om vedtak

Trello: https://trello.com/c/XZvrwKjG/645-varsle-arbeidsgiver-om-vedtak-i-ny-l%C3%B8sning
Kort-ID: `6acf883cff6b65a16442444e`

Henger sammen med:

- Kort 691, [journalfor_vedtaksbrev_arbeidsgiver.md](journalfor_vedtaksbrev_arbeidsgiver.md).
  Publiserer `VedtaksbrevArbeidsgiverJournalfoert`, som varselet lytter på.
- Kort 693, [vis_vedtak_arbeidsgiver.md](vis_vedtak_arbeidsgiver.md). Siden
  `/v2/skjema/:id/vedtak`, som varselet lenker til.
- Kort 4kkrg41a, «Beslutter fatter vedtak på søknaden el. sender tilbake»
  ([beslutter_vedtak.md](beslutter_vedtak.md)). Kortet er skilt ut derfra.

## Mål

Når beslutter har fattet vedtak i ny saksbehandling, får arbeidsgiver en beskjed
på Min side – arbeidsgiver og et eksternt varsel på e-post eller SMS. Det
gjelder alle tre utfall: innvilget, innvilget med redusert beløp og avslått.
Saken i notifikasjonsplattformen settes til `FERDIG` med statusteksten fra
vedtakssiden, og lenken på saken flyttes til kvitteringen i v2.

Varselet sendes når vedtaksbrevet til arbeidsgiver er journalført. Handleren
`VarsleArbeidsgiverVedtakFattet` lytter på `VedtaksbrevArbeidsgiverJournalfoert`
og tar alt den trenger fra eventen.

Vi kvitterer hva vi varslet. Når notifikasjonsplattformen har tatt imot
beskjeden og statusen, publiserer handleren `ArbeidsgiverVarsletVedtakFattet`.
Eventen har det nye domain-objektet `ArbeidsgiverVarselDomain`, som er det vi
sendte. Feiler kallet mot notifikasjonsplattformen, publiseres ingen
kvittering. Eventen har bare en no-op-handler.

Kvitteringen beslutter ser, sier allerede «Arbeidsgiver og deltaker vil bli
varslet om vedtaket.» (`beslutter_vedtak.md`). Kortet må derfor være ute før
lansering.

## Bakgrunn

Fra før:

- **`VedtaksbrevArbeidsgiverJournalfoert`** (kort 691) publiseres av
  `JournalfoerVedtaksbrevArbeidsgiver` etter at brevet er journalført i
  arbeidsgivermappa. Eventen har `sakId`, `soknadId`,
  `utfall: VedtakDomain.Utfall` (`INNVILGE`, `INNVILGE_REDUSERT`, `AVSLAA`),
  `virksomhetsnummer`, `virksomhetsnavn`, `vedtaksbrev`, `adressebeskyttelse`,
  `journalpostId`, `dokumentInfoId` og `tidspunkt`. Den publiseres bare for
  saker med kilde `EKSPERTBISTAND`. KDoc-en sier at varslingen kommer i kort 645.
- **`LagreVedtaksbrev`** (kort 693) lytter på samme event og lagrer raden i
  `vedtaksbrev`, som vedtakssiden leser.
- **Vedtakssiden** `/v2/skjema/:id/vedtak` (kort 693) viser vedtaket og brevet.
  Svarer API-et 404, sender siden videre til `/v2/skjema/:id/kvittering`.
  Kvitteringen i v2 sender til vedtakssiden når vedtaket finnes. v2 er bare
  på lokalt og i dev (`EKSPERTBISTAND_V2`). I prod går en `/v2`-URL til `/`.
- **`VarsleArbeidsgiverSakOpprettet`** (ikke prod) oppretter sak og beskjed i
  notifikasjonsplattformen når en sak opprettes i ny løsning, med
  `grupperingsid = soknadId` og lenke til v1-kvitteringen
  (`DTO.Soknad.kvitteringsLenke`, `soknad/Api.kt`).
- **For Arena-saker** varsler `VarsleArbeidsgiverSoknadGodkjent` (på
  `TilsagnsdataLagret`) og `VarsleArbeidsgiverSoknadAvlyst` (på
  `SoknadAvlystIArena`). Begge sender beskjed og setter saken til `FERDIG`, med
  idempotens-subtaskene `notifikasjonsplatform_ny_beskjed` og
  `notifikasjonsplatform_ny_status_sak`.
- **`ProdusentApiKlient`.** `opprettNyBeskjed` er idempotent på `eksternId`
  (`DuplikatEksternIdOgMerkelapp` hoppes over). `nyStatusSak` bruker
  idempotencyKey `"$grupperingsid-$statusTekst"`, setter hard delete til 455
  dager og kaster på `SakFinnesIkke`. Mutasjonen `NyStatusSak.graphql` har
  variabelen `$nyLenkeTilSak`, men klienten sender den ikke. Null betyr at
  lenken på saken står uendret. Merkelappen (`"Ekspertbistand"`), mottakeren
  (Altinn-ressursen `altinn3Ressursid`) og sendevinduet (`NKS_AAPNINGSTID`) er
  faste i klienten. Merkelappen er en privat konstant, og sendevinduet står
  i `EksterntVarsel.tilVarsel()`.
- **Kvittering med no-op-handler.** `SoknadKopiDeltakerDistribuert` er
  mønsteret: eventen lagrer det vi gjorde i eventloggen, og har en inline
  no-op-handler i `configureEventHandlers`. `EventManager` gir `TransientError`
  for eventer uten handler.
- **Saksloggen.** `SkrivSakslogg` skriver `SakOppdatert` til `sakslogg`.
  `BestillTilsagnIOebs` og `JournalfoerVedtaksbrevArbeidsgiver` publiserer
  `SakOppdatert` med `AktorRolle.SYSTEM`.
- **Omdøpingen fra forrige utkast er ikke gjort.**
  `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` heter fortsatt det.

## Skisser

Kortet har ingen Figma-lenker. Beskjeden vises i notifikasjonsplattformens egen
visning. Lenken går til vedtakssiden fra kort 693, med skissene som står i
`vis_vedtak_arbeidsgiver.md`.

## Beslutninger

Kortet har ingen kommentarer eller vedlegg (sjekket 2026-11-11). Beslutningene
kommer fra kortbeskrivelsen, fra Kens svar på utkastene av denne spec-en
(2026-10-30 og 2026-11-11) og fra spec-ene for kort 691 og 693.

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Arbeidsgiver varsles både ved innvilgelse og avslag. Må være på plass før lansering. |
| Kortbeskrivelsen (sist endret av Ken, 2026-11-11) | Vi varsler på samme måte som ved `TilsagnsdataLagret`, men lytter på `VedtaksbrevArbeidsgiverJournalfoert`. |
| Kortbeskrivelsen (sist endret av Ken, 2026-11-11) | For ikke-benekting publiserer vi en event med det som ble varslet til arbeidsgiver, med en no-op-handler. Et nytt domain-objekt tar vare på det vi sendte. Eventen publiseres ikke hvis kallet mot notifikasjonsplattformen feiler. |
| Kortets sjekkliste (åpen) | Teksten i e-post og SMS skal avklares. Kan vi nevne tilskudd til ekspertbistand? Hanna lager forslag og sjekker med jurist. |
| Ken, 2026-10-30 | Vi bruker dagens tekster for beskjed, statustekst, e-post og SMS. Sjekklistepunktet løses ikke i dette kortet. |
| Ken, 2026-10-30 | Alle vedtak varsles, også avslag. |
| Ken, 2026-10-30 | Varselet venter på at vedtaksbrevet til arbeidsgiver er journalført, som for Arena-saker. |
| Ken, 2026-10-30 | Journalføringseventen skal ha nok data til at varselet sendes uten databaseoppslag. Løst i kort 691. |
| Ken, 2026-10-30 | `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` er feilnavngitt og skal hete `VarsleArbeidsgiverSoknadMottattBehandlesIArena`. |
| Ken, 2026-10-25 (kort 599) | Deltaker varsles via dokdist i eget kort. Ikke en del av dette. |
| Ken, 2026-11-01 (kort 691) | Arbeidsgiver ser brevet i vår løsning, rendret med Gotenberg. Ikke Altinn-meldinger, og aldri PDF fra Joark. |
| Kort 693, valg i spec-en | Vedtaket vises når brevet er journalført, så siden er klar når arbeidsgiver får varselet. |
| Ken, 2026-11-11 | Lenken i beskjeden kan inneholde `/v2`. Ved lansering blir v2 trolig standard, eller så legger vi på redirects. |
| Ken, 2026-11-11 | Statusteksten på saken følger vedtakssiden, også ved redusert beløp. |
| Ken, 2026-11-11 | Lenken på saken oppdateres til kvitteringen i v2 med `nyLenkeTilSak` i `nyStatusSak`-mutasjonen. |
| Minne: kodekonvensjoner | Nye parametere på delte klienter får ingen standardverdi. Eksisterende kall sender verdien selv. |
| Minne: eventmodell | Nye eventfelt bruker domain-typer i `event/domain/` med egne enums, ikke API-DTO-er eller enums fra andre lag. Eventer i prod endres aldri. |

### Valg i spec-en

Disse er ikke avklart i kortet. De er valgt her og kan endres ved review:

- **Domain-objektet bygges før vi sender, og vi sender fra det.**
  `arbeidsgiverVarsel(data)` er en ren funksjon som lager
  `ArbeidsgiverVarselDomain` fra `VedtaksbrevArbeidsgiverJournalfoert`.
  Handleren kaller notifikasjonsplattformen med verdiene i objektet og
  publiserer det samme objektet. Da er kvitteringen lik det som ble sendt.
  Samme event gir samme objekt, så et nytt forsøk sender det samme.
- **Domain-objektet har også det klienten bestemmer:** merkelapp,
  Altinn-ressurs og sendevindu. De avgjør hvem som får varselet og når, og hører
  derfor med i ikke-benektingen. Klienten gjør merkelappen og sendevinduet til
  `internal`-konstanter, og domain-objektet leser dem derfra. Klienten endrer
  ikke oppførsel.
- **Egne enums i domain-objektet**, `Saksstatus` (`FERDIG`) og `Sendevindu`
  (`NKS_AAPNINGSTID`). Handleren oversetter dem til de genererte GraphQL-enumene
  med uttømmende `when`. Eventen er da uavhengig av GraphQL-skjemaet.
- **Kvitteringen beskriver bestillingen, ikke leveransen.**
  Notifikasjonsplattformen sender e-post og SMS senere, i sendevinduet. Vi
  lagrer det vi ba om å sende, med tidspunktet bestillingen lyktes.
- **Lenke direkte til vedtakssiden**, `/ekspertbistand/v2/skjema/<soknadId>/vedtak`.
  Kvitteringen i v2 sender videre til den samme siden, men en direkte lenke
  sier hva beskjeden gjelder.
- **Lenken på saken går til kvitteringen i v2**, `/ekspertbistand/v2/skjema/<soknadId>/kvittering`,
  ikke til vedtakssiden. Saken gjelder hele søknaden, og kvitteringen sender
  videre til vedtakssiden når vedtaket finnes. Lenken står i
  `saksstatus.nyLenkeTilSak` i domain-objektet, fordi den er en del av det vi
  sendte.
- **Deterministisk `eksternId`**: `<soknadId>-vedtak`. Det er ett vedtak per
  søknad. Et nytt forsøk gir samme beskjed, ikke en ny.
- **Saksloggen via `SakOppdatert`.** Handleren publiserer `SakOppdatert`
  (`SYSTEM`, notat «Arbeidsgiver varslet om vedtak») i samme transaksjon som
  kvitteringen. Kvitteringseventen er kilden til hva som ble sendt. Saksloggen
  viser bare at det skjedde.
- **Inline no-op-handler** `ArbeidsgiverVarsletVedtakFattetNoop`, som
  `SoknadKopiDeltakerDistribuertNoop`. Forrige utkast hadde en egen fil. Det
  gjør ikke de andre no-op-handlerne.
- **Klassen får nytt navn, `id` beholdes.** `id =
  "VarsleArbeidsgiverSoknadMottatt"` står, så eventer i kø i prod ikke kjøres
  på nytt.

## Avgrensning

Ikke i dette kortet:

- Ny tekst i beskjed, e-post og SMS. Sjekklistepunktet står åpent på kortet, og
  tekstene ligger ett sted så de er enkle å bytte.
- Lenken som `VarsleArbeidsgiverSakOpprettet` setter når saken opprettes. Den
  går fortsatt til v1-kvitteringen til vedtaket er fattet.
- Redirects fra `/v2` ved lansering.
- Varsling av deltaker: kort 599 (<https://trello.com/c/QA5bXVrf>).
- Varsel ved ny vurdering, klage eller endret vedtak. Vi har ett vedtak per
  søknad.
- Leveransestatus for e-post og SMS.
- Arena-saker. De varsles som før, og Arena-handlerne endres ikke.

## Tilnærming

```
VedtaksbrevArbeidsgiverJournalfoert ─┬─> LagreVedtaksbrev (fra før)
                                     └─> VarsleArbeidsgiverVedtakFattet
                                           ├─ opprettNyBeskjed   (guard: notifikasjonsplatform_ny_beskjed)
                                           ├─ nyStatusSak FERDIG (guard: notifikasjonsplatform_ny_status_sak)
                                           └─ transaksjon        (guard: arbeidsgiver_varslet_kvittert)
                                                ├─> ArbeidsgiverVarsletVedtakFattet ─> no-op
                                                └─> SakOppdatert ─> SkrivSakslogg
```

### 0. Omdøping (egen commit)

- `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` →
  `VarsleArbeidsgiverSoknadMottattBehandlesIArena`, fil og klasse.
  `id = "VarsleArbeidsgiverSoknadMottatt"` endres ikke.
- Oppdater referansene i `Events.kt`, KDoc i `VarsleArbeidsgiverSakOpprettet`,
  `VarsleArbeidsgiverSoknadMottattTest.kt` og `opprett_sak.md`.
- Generer `event-flow.md` på nytt.

### 1. `ArbeidsgiverVarselDomain`

Ny fil `event/domain/ArbeidsgiverVarselDomain.kt`:

```kotlin
/**
 * Et varsel til arbeidsgiver i notifikasjonsplattformen, slik vi bestilte det. Har bare
 * organisasjonsdata og faste tekster.
 */
@Serializable
data class ArbeidsgiverVarselDomain(
    val merkelapp: String,
    val mottaker: Mottaker,
    val virksomhetsnummer: String,
    val grupperingsid: String,
    val beskjed: Beskjed,
    val saksstatus: Saksstatus,
) {
    @Serializable
    data class Mottaker(val altinnRessursId: String)

    @Serializable
    data class Beskjed(
        val eksternId: String,
        val tekst: String,
        val lenke: String,
        val eksterntVarsel: EksterntVarsel,
    )

    @Serializable
    data class EksterntVarsel(
        val epostTittel: String,
        val epostHtmlBody: String,
        val smsTekst: String,
        val sendevindu: Sendevindu,
    )

    @Serializable
    data class Saksstatus(
        val status: Status,
        val statusTekst: String,
        val nyLenkeTilSak: String,
    )

    @Serializable
    enum class Status { FERDIG }

    @Serializable
    enum class Sendevindu { NKS_AAPNINGSTID }
}
```

### 2. Ny event

```kotlin
@Serializable
@SerialName("arbeidsgiverVarsletVedtakFattet")
data class ArbeidsgiverVarsletVedtakFattet(
    val sakId: String,
    val soknadId: String,
    val utfall: VedtakDomain.Utfall,
    val journalpostId: String,
    val varsel: ArbeidsgiverVarselDomain,
    val tidspunkt: Instant,
) : EventData {
    override val aggregateRootId: String get() = soknadId
}
```

- `journalpostId` er vedtaksbrevet arbeidsgiver ble varslet om, fra
  `VedtaksbrevArbeidsgiverJournalfoert`.
- KDoc: publiseres av `VarsleArbeidsgiverVedtakFattet` når
  notifikasjonsplattformen har tatt imot beskjeden og statusen. Feltene er det
  som ble bestilt. Konsumenter: ingen, registrert med en no-op-handler.
- Ingen personopplysninger. Virksomhetsnummer og virksomhetsnavn (i e-post og
  SMS) er organisasjonsdata.

### 3. `VarsleArbeidsgiverVedtakFattet`

Lytter på `VedtaksbrevArbeidsgiverJournalfoert`. Registreres i alle miljøer,
etter `LagreVedtaksbrev`. `id = "VarsleArbeidsgiverVedtakFattet"`.

1. `val varsel = arbeidsgiverVarsel(event.data)`. Ingen databaseoppslag.
2. Utenfor transaksjon, guardet på `notifikasjonsplatform_ny_beskjed`:
   `opprettNyBeskjed` med `grupperingsid`, `eksternId`, `virksomhetsnummer`,
   `tekst`, `lenke` og `EksterntVarsel` fra `varsel`. Feil gir `transientError`.
3. Utenfor transaksjon, guardet på `notifikasjonsplatform_ny_status_sak`:
   `nyStatusSak` med `status`, `statusTekst` og `nyLenkeTilSak` fra
   `varsel.saksstatus`. Feil gir `transientError`.
4. I én transaksjon, guardet på `arbeidsgiver_varslet_kvittert`: publiser
   `ArbeidsgiverVarsletVedtakFattet` og `SakOppdatert`, og guard. Feil gir
   `rollback()` og `transientError`.

Ingen suspend-kall inne i `transaction {}`. `Clock` injiseres, som i
`JournalfoerVedtaksbrevArbeidsgiver`.

Lenkene lages av to nye funksjoner ved siden av `kvitteringsLenke` i
`soknad/Api.kt`, med samme domener per miljø:

- `vedtakLenke(soknadId: String)`: `/ekspertbistand/v2/skjema/<soknadId>/vedtak`
- `kvitteringsLenkeV2(soknadId: String)`: `/ekspertbistand/v2/skjema/<soknadId>/kvittering`

### 4. Tekster

Beskjed, e-post og SMS er fra Arena-handlerne. Avslag bruker Arena-teksten uten
«trukket», fordi avslått er avslått i ny løsning. Statusteksten er statusbanneret
på vedtakssiden (Ken, 2026-11-11). Tekstene ligger som konstanter i handlerfila,
valgt med uttømmende `when` på `VedtakDomain.Utfall`.

| | `INNVILGE` | `INNVILGE_REDUSERT` | `AVSLAA` |
|-|-----------|---------|---------|
| Beskjed | «Søknaden er godkjent og ekspertbistand kan nå tas i bruk.» | samme som `INNVILGE` | «Søknaden om ekspertbistand er avslått.» |
| Statustekst | «Søknad godkjent» | «Søknad godkjent med redusert beløp» | «Søknad avslått» |
| E-posttittel | «Nav – angående søknad om ekspertbistand» | samme | samme |
| E-post og SMS | «<virksomhetsnavn> har fått svar på en søknad om ekspertbistand. Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det.» | samme | samme |

### 5. `ProdusentApiKlient`

- `merkelapp` blir `internal const val`.
- Ny `internal val sendevindu = Sendevindu.NKS_AAPNINGSTID`, brukt i
  `EksterntVarsel.tilVarsel()`.
- `nyStatusSak` får parameteren `nyLenkeTilSak: String?`, uten standardverdi,
  og sender den som `$nyLenkeTilSak`.
- `VarsleArbeidsgiverSoknadGodkjent`, `VarsleArbeidsgiverSoknadGodkjentKildeAltinn`,
  `VarsleArbeidsgiverSoknadAvlyst` og `ProdusentApiKlientTest` sender
  `nyLenkeTilSak = null`. Arena-sakene beholder lenken sin.

### 6. Oppsett og dokumentasjon

- `Events.kt`: ny event med KDoc, registrere handleren og no-op-handleren.
  KDoc på `VedtaksbrevArbeidsgiverJournalfoert`: legg til
  `VarsleArbeidsgiverVedtakFattet` som konsument, og fjern «Varslingen av
  arbeidsgiver kommer i kort 645».
- `event-flow.md` genereres med `EventFlowDiagram.kt`.

### 7. Tester

Mønster fra `VarsleArbeidsgiverSoknadGodkjentTest` (`setProdusentApiResultat`)
og `JournalfoerVedtaksbrevArbeidsgiverTest`, med `testApplicationWithDatabase`.

- Alle tre utfall: beskjed og `FERDIG` med riktige tekster, lenker og
  `eksternId`, `nyLenkeTilSak` til kvitteringen i v2, og én `ArbeidsgiverVarsletVedtakFattet` og én `SakOppdatert` i
  køen.
- Kvitteringen er lik det som ble sendt: feltene i `varsel` mot variablene i
  GraphQL-kallene.
- Feil fra `opprettNyBeskjed`: ingen kvittering i køen, og `transientError`.
- Feil fra `nyStatusSak` etter vellykket beskjed: nytt forsøk sender ikke ny
  beskjed, og kvitteringen publiseres én gang.
- Nytt forsøk etter suksess: ingen nye kall og ingen nye eventer.
- `ArbeidsgiverVarsletVedtakFattet` i `TestEventData.allEventSamples` og
  `EventDataAggregateRootIdTest`.
- `ProdusentApiKlientTest`: `nyLenkeTilSak` sendes med i `nyStatusSak`.
- Arena-testene: `nyStatusSak` sender `nyLenkeTilSak = null`.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `event/handlers/VarsleArbeidsgiverSoknadMottattBehandlesIAltinn.kt` | Omdøpt til `...IArena.kt` |
| `event/handlers/VarsleArbeidsgiverSakOpprettet.kt` | KDoc-referanse |
| `event/domain/ArbeidsgiverVarselDomain.kt` | Ny |
| `event/handlers/VarsleArbeidsgiverVedtakFattet.kt` | Ny |
| `event/Events.kt` | Ny event, KDoc, registrering |
| `notifikasjon/ProdusentApiKlient.kt` | `merkelapp` og `sendevindu` som `internal`, `nyLenkeTilSak` på `nyStatusSak` |
| `event/handlers/VarsleArbeidsgiverSoknadGodkjent.kt`, `...GodkjentKildeAltinn.kt`, `...SoknadAvlyst.kt` | `nyLenkeTilSak = null` |
| `soknad/Api.kt` | `vedtakLenke(soknadId)` og `kvitteringsLenkeV2(soknadId)` |
| `event/event-flow.md` | Generert |
| `test/.../VarsleArbeidsgiverSoknadMottattTest.kt` | Klassenavn |
| `test/.../VarsleArbeidsgiverVedtakFattetTest.kt` | Ny |
| `test/.../TestEventData.kt`, `EventDataAggregateRootIdTest.kt` | Ny event |
| `test/.../ProdusentApiKlientTest.kt` | `nyLenkeTilSak` |
| `specifications/opprett_sak.md` | Klassenavn |

## Kanttilfeller og feilmodus

- **Saken finnes ikke i notifikasjonsplattformen.** `nyStatusSak` kaster
  `SakFinnesIkke`, og handleren gir `transientError`. Det skjer hvis
  `VarsleArbeidsgiverSakOpprettet` ikke har kjørt. Nytt forsøk løser det når
  saken kommer. Beskjeden er sendt allerede, og kvitteringen venter til begge
  kallene har lyktes.
- **Nytt forsøk etter delvis suksess.** Hver subtask er guardet, og `eksternId`
  og idempotencyKey er faste. Arbeidsgiver får én beskjed og ett eksternt
  varsel.
- **Kvitteringen feiler etter at varselet er sendt.** Transaksjonen rulles
  tilbake, og et nytt forsøk publiserer kvitteringen uten å varsle på nytt.
- **Deploy med nye tekster mellom sending og kvittering.** Kvitteringen bygges
  på nytt og får de nye tekstene, mens beskjeden har de gamle. Vinduet er kort.
  Vi godtar det.
- **Varselet kommer før `LagreVedtaksbrev` har lagret raden.** Begge lytter på
  samme event. Følger arbeidsgiver lenken før raden finnes, sender
  vedtakssiden videre til kvitteringen. Det eksterne varselet går i
  sendevinduet, så i praksis er raden lagret før arbeidsgiver ser varselet.
- **Journalføringen feiler.** Ingen varsel. Feilen hører til
  `JournalfoerVedtaksbrevArbeidsgiver` og synes der.
- **Arena-saker.** `VedtaksbrevArbeidsgiverJournalfoert` publiseres bare for
  saker med kilde `EKSPERTBISTAND`. Arena-varslene er uendret.
- **Personopplysninger.** Varselet og kvitteringen har bare organisasjonsdata,
  `soknadId` i lenken og faste tekster. Ingen begrunnelse, ingen navn, ingen
  identer.

## Åpne spørsmål

Ingen. Spørsmålene om `/v2` i lenken, statusteksten ved redusert beløp og
lenken på saken er besvart av Ken 2026-11-11 (se Beslutninger).

## Ferdig når

- [ ] `VarsleArbeidsgiverSoknadMottattBehandlesIAltinn` heter `...IArena`, med uendret `id`, i egen commit
- [ ] `ArbeidsgiverVarselDomain` og `ArbeidsgiverVarsletVedtakFattet` finnes, og eventen er med i eventtestene
- [ ] Alle tre utfall gir beskjed med lenke til vedtakssiden, `FERDIG` med statusteksten fra vedtakssiden, lenke på saken til kvitteringen i v2, kvittering og linje i saksloggen
- [ ] `nyStatusSak` tar `nyLenkeTilSak` uten standardverdi, og Arena-handlerne sender `null`
- [ ] Kvitteringen er lik det som ble sendt, og publiseres ikke når notifikasjonsplattformen feiler
- [ ] Ingen dobbel beskjed, dobbelt eksternt varsel eller dobbel kvittering ved nytt forsøk
- [ ] `event-flow.md` er generert, og KDoc på `VedtaksbrevArbeidsgiverJournalfoert` er oppdatert
- [ ] `mvn -B package` er grønn
