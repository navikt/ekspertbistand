# Lanseringsplan ekspertbistand: frist 1. april 2027

Status per 20.11.2026. Grunnlaget er Trello-tavla «Team FAGER» (listene
`Ekspertbistand`, `DOING`, `Weekly commits`, `Huskeliste ekspertbistand` og
`Backlog`), åpne PR-er og koden på `main`.

Planen gir oversikt. Detaljer i hver oppgave tar vi i teamet, kort for kort.

## Kort fortalt

- Søknad → sak → vilkårsvurdering → foreløpig vedtak → beslutter fatter
  vedtak er bygget og kjører i dev. Etter vedtaket journalføres brevet til
  arbeidsgiver, arbeidsgiver får varsel og ser vedtaket på ny rute, og
  pengene settes av i OeBS. Det som mangler, er vedtaksbrevet til deltaker,
  refusjonsbehandlingen og utbetalingen.
- **OeBS runde 1 er godkjent (13.11.).** Bestilling, kvittering og
  annullering virker mot T1 fra dev. Runde 2, faktura og oppgjør, venter på
  at OeBS er klare. Prosesseringen kjører i dev fra 20.11. ([#259](https://github.com/navikt/ekspertbistand/pull/259)), og
  saksloggen viser status fra OeBS. Prod er ikke skrudd på.
- **P1 er i prod (9.–11.11.), fem uker før planen.** Aa-reg-sjekken i
  søknaden, kopi av søknaden til deltaker med dokdist og teksten om kopien i
  søknadsskjemaet er i prod, i dagens Arena-flyt.
- Alle søknader som sendes inn etter go-live, behandles i vår løsning. Saker
  som er opprettet i Arena, behandles ferdig der. Vi synker ingenting mellom
  oss og Arena. Arena stenger for nye saker når vi går live, senest 1. april,
  og Salesforce og Modia må få data om nye saker fra samme dag.
- Den reelle fristen er **fredag 19. mars 2027 (uke 11)**. Påskedag er
  28. mars, og 1. april er torsdagen etter påske. Vi bør ikke prodsette i
  påskeuka.
- Vi har cirka 16 arbeidsuker (uke 47–51 og uke 1–11) for tre utviklere,
  minus ferie. Det holder hvis vi jobber i tre spor som ikke tråkker på
  hverandre.
- Største eksterne risiko er hvordan Salesforce og Modia skal få data om nye
  saker. Neste risiko er når OeBS er klare for runde 2. Tilgang til Aa-reg
  og dokdist er på plass.
- DOING har fem kort ved slutten av uke 47: tre nye vilkår (656) og
  beslutter kan ikke endre vilkår (636) hos Anders, refusjonskrav og
  sluttrapport etter Figma (685, 686) hos Jonas, og kostnadssted (728) hos
  Ken. Neste kort i hvert spor står under
  [Hvordan tre utviklere jobber parallelt](#hvordan-tre-utviklere-jobber-parallelt).
- Ny scope fra Hanna (16.11.): saksbehandler skal kunne trekke og avvise en
  søknad før vedtak og avbryte et tiltak som er i gang. Det er regnet med,
  cirka 10 dager, se
  [8. Trekke, avvise, avbryte og omgjøre](#8-trekke-avvise-avbryte-og-omgjøre).
  Omgjøring av vedtak ([689](https://trello.com/c/KwIMHZP9)) er fortsatt ikke regnet med, se
  [Åpne spørsmål](#åpne-spørsmål).
- Refusjonsbehandlingen ble planlagt 18.11. Godkjenning skjer i ett steg,
  avslag i to. Arbeidsgiver får to påminnelser før refusjonsfristen, og
  beløpet frigjøres hvis kravet ikke kommer. Det gir to nye oppgaver, cirka
  5 dager. Se [Avklart](#avklart).
- Ny scope 18.–20.11.: saksbehandler velger kostnadssted ved foreløpig
  vedtak (728, 727), feilhåndtering mot OeBS (724), ny innsending av
  sluttrapport (717) og KRR-merking av deltaker (719). Til sammen cirka
  9 dager. Feilhåndteringen mot OeBS holdes enkel: saken flagges, og teamet
  følger opp med OeBS.

## Statusoversikt

🟢 ferdig · 🟡 pågår eller delvis ferdig · 🔴 ikke startet. Fargen viser om
oppgaven er ferdig bygget, ikke om den er i prod. Det som er i prod, står i
teksten. Resten går i prod samlet ved go-live (🚦).
«Mangler kort» betyr at oppgaven ikke finnes på Trello, se
[Mangler på Trello](#mangler-på-trello). Ferdige kort som er arkivert, lenkes
via specen som viser til dem. «Milepæl» viser når oppgaven skal være ferdig,
se [Milepæler](#milepæler). «Etter» betyr at den kan vente til etter lansering.

[Aktører og tilstander](README.md) viser det samme fra
aktørenes side: hva arbeidsgiver, deltaker, saksbehandler og beslutter gjør
fra innsending til utbetalt refusjon, og hvilke tilstander saken går gjennom.

Størrelse er et grovt anslag for gjenstående utviklertid, ikke ventetid på
andre. Antall blokker viser størrelsen:

| Størrelse | Tid |
|-----------|-----|
| `XS ▪` | 1 time |
| `S  ■` | 1 dag |
| `M  ■■` | 2–3 dager |
| `L  ■■■` | 1 uke |
| `XL ■■■■` | 2 uker eller mer |

For 🟡 gjelder anslaget det som gjenstår. Summene regner M som 2,5 dager
og XL som 10 dager.

**Totalt cirka 114 utviklerdager** (23 utvikleruker). Frigjøring av beslutter
og omgjøring av vedtak er ikke med. Kapasiteten er cirka 168 dager: 15 uker
fra uke 48 × 3 utviklere, minus vinterferie, ganger 80 %. Det gir rom for at
anslagene sprekker med rundt 45 %, men ikke for mye ny scope.

### Søknad (arbeidsgiver)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Søknadsskjema, innsending og journalføring (i prod) | – |
| 🟢 | – | – | Varsle arbeidsgiver ved mottatt søknad | [475](https://trello.com/c/GC5c2wJi) |
| 🟢 | – | – | Kall mot aareg-services i stedet for arbeidsgiver-innsyn-aareg | [478](https://trello.com/c/nhonb0wT) |
| 🟢 | – | – | 🧹 Makssats per startdato | [651](https://trello.com/c/3zvmjKiu) |
| 🟢 | – | – | 🧹 Fjerne utgiftsbeskrivelse ([#234](https://github.com/navikt/ekspertbistand/pull/234)) | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 🛤️ Ny rute bak feature toggle (`EKSPERTBISTAND_V2`) for søknaden og funksjonene i ny løsning ([#238](https://github.com/navikt/ekspertbistand/pull/238)). Dagens sider beholdes uendret. | [682](https://trello.com/c/cEJ38zjf) |
| 🟢 | – | – | 🛤️ Kvitteringen i ekspansjonskort på ny rute, før og etter vedtak ([#250](https://github.com/navikt/ekspertbistand/pull/250), [#253](https://github.com/navikt/ekspertbistand/pull/253), [#257](https://github.com/navikt/ekspertbistand/pull/257)) | [680](https://trello.com/c/RZqSZ8p3), [707](https://trello.com/c/Fo4qNluC) |
| 🟢 | – | – | 🛤️ Tilskuddsbrevet (vedtaksbrevet i nye saker) i ekspansjonskort på ny rute, med tittel per utfall ([#250](https://github.com/navikt/ekspertbistand/pull/250)) | [681](https://trello.com/c/H7jYzwos) |
| 🟢 | – | – | 🪪 Aa-reg-sjekk av deltaker i søknaden, i v1 og v2 ([#230](https://github.com/navikt/ekspertbistand/pull/230), i prod) | [601](https://trello.com/c/3Oq7KiFc) |
| 🟢 | – | – | 📨 Kopi av søknaden til deltaker: journalført som utgående på personmappa og distribuert med dokdist ([#251](https://github.com/navikt/ekspertbistand/pull/251), i prod) | [676](https://trello.com/c/khxsoU0N), [675](https://trello.com/c/2QCwaAJX), [692](https://trello.com/c/LU0D56cu) |
| 🟢 | – | – | 📨 Arbeidsgiver får beskjed i søknadsskjemaet om at deltaker får kopi, og infoskriv til deltaker (i prod) | [675](https://trello.com/c/2QCwaAJX), [695](https://trello.com/c/lPVEHuKB) |
| 🟢 | – | – | Behandlingskatalog for tilgang til Aa-reg | [534](https://trello.com/c/MWtJNaH0) |
| 🟢 | – | – | Lagre adressebeskyttelse og skjerming ved opprettelse | [661](https://trello.com/c/6L5KVmvN) |
| 🟢 | – | – | Sletterutiner, 10 år og 1 år for arbeidsgiver | [583](https://trello.com/c/gzIhkeip) |
| 🔴 | M3 | `XS ▪` | Informere arbeidsgiver om at søknader fjernes etter 1 år | [595](https://trello.com/c/9pex2Tgp) |
| 🟢 | – | – | 🧹 Sikkerhetsfunn H1 og H2 | [664](https://trello.com/c/3qjMQJdw) |
| 🟢 | – | – | 🐛 Utkastet lagres ikke når navnet på ekspertens virksomhet inneholder kontrolltegn ([#269](https://github.com/navikt/ekspertbistand/pull/269)) | [714](https://trello.com/c/P7i4SLgv) |
| 🟢 | – | – | Ikke opprette utkast før deltaker er bekreftet. Hele utkastet valideres før innsending ([#269](https://github.com/navikt/ekspertbistand/pull/269)) | [708](https://trello.com/c/O1CTFxEB) |

Gjenstår: under 1 dag.

### Saksbehandling fram til vedtak

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Roller via entra-proxy | [445](https://trello.com/c/HY9i8MkL) |
| 🟢 | – | – | Tilgangsmaskinen med KJERNE-regelsett | [458](https://trello.com/c/6RJ8pn4X) |
| 🟢 | – | – | Ruting av egne ansatte og kode 6/7 | [484](https://trello.com/c/SNQ9LgWb), [485](https://trello.com/c/BAJeTZsi) |
| 🟢 | – | – | Listevisning | [471](https://trello.com/c/5Yw04ZwB), [482](https://trello.com/c/3JeC5mSq) |
| 🟢 | – | – | Tildeling og detaljvisning | [457](https://trello.com/c/wu5GnoXw), [469](https://trello.com/c/qNWp34z2) |
| 🟢 | – | – | Oppdatert tekst på vilkårsvurdering | [474](https://trello.com/c/AR3hzcce) |
| 🟢 | – | – | Foreløpig vedtak med forhåndsvisning av brev | [473](https://trello.com/c/M6Ei4UAR), [468](https://trello.com/c/gh8l45zx) |
| 🟢 | – | – | Beslutter fatter vedtak eller sender i retur | [476](https://trello.com/c/4kkrg41a) |
| 🟡 | M1 | `S  ■` | 📝 Brevmaler for vedtak. QA på tekstene med fag pågår. Deltaker skal ikke få vite tilskuddsbeløpet, så brevene til deltaker endres (Hanna, 4.11.). | [563](https://trello.com/c/3moZGD2U), [678](https://trello.com/c/EXPjdaDS) |
| 🟢 | – | – | 📜 Kvitteringshendelser for varsler til arbeidsgiver: søknad mottatt, godkjent og avlyst. Ingen backfill ([#266](https://github.com/navikt/ekspertbistand/pull/266)) | [598](https://trello.com/c/2HQeHav3) |
| 🟢 | – | – | Entra-grupper og tilgangsrutine i prod (STS-56018) | [506](https://trello.com/c/9wiRRC1M), [532](https://trello.com/c/WB9G2Pxx) |
| 🟢 | – | – | ⚖️ «Automatisk»-tag og oppfylt som standard på «Deltaker er enig» og «Arbeidsforhold» (fra Aa-reg). Saksbehandler kan overstyre. ([#267](https://github.com/navikt/ekspertbistand/pull/267)) | [655](https://trello.com/c/WCMkpira) |
| 🟡 | M1 | `M  ■■` | ⚖️ Tre nye vilkår (i DOING) | [656](https://trello.com/c/hLd2jDAj) |
| 🟡 | M1 | `S  ■` | Beslutter kan ikke endre vilkår (GUI, i DOING) | [636](https://trello.com/c/eUsR2C7t) |
| 🟢 | – | – | 🧹 Småfiks: «Beløp», ingen handlingskolonne i sakslista, vedtaksfanen er aktiv når saken har vedtak ([#258](https://github.com/navikt/ekspertbistand/pull/258)) | [652](https://trello.com/c/fQeOJnTi), [709](https://trello.com/c/BPcP6uqr), [713](https://trello.com/c/SqJMcrA6) |
| 🟢 | – | – | Breadcrumb (sakshode) med arbeidsgiver og deltaker ([#265](https://github.com/navikt/ekspertbistand/pull/265)) | [653](https://trello.com/c/ulp1r0W4) |
| 🔴 | M3 | `S  ■` | Vise KRR-reservasjon for deltaker i saksbildet. Skilt ut fra 653. Oppslagsfeil skal ikke vises som «ikke reservert». | [719](https://trello.com/c/rVqtatv5) |
| 🔴 | M3 | `S  ■` | Lenker fra deltaker til andre systemer: Aa-reg, Modia og Gosys | [710](https://trello.com/c/O7T6n36x) |
| 🔴 | M3 | `M  ■■` | Idempotens og samtidighet i saksbehandlings-API-ene: to raske kall får begge 202, men bare det ene får effekt | [694](https://trello.com/c/kVXWdoeJ) |
| 🔴 | Etter | `M  ■■` | Frigjør beslutter og tildel annen beslutter (kan vente) | [648](https://trello.com/c/Nouutlp7) |

Gjenstår: cirka 9 dager, uten frigjøring av beslutter.

### Etter vedtak: brev, journalføring og varsel

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | 🗄️📝 Lage og journalføre vedtaksbrevet til arbeidsgiver (tilsagnsbrevet i nye saker) for innvilget, redusert beløp og avslått, med sladding ved kode 6/7 på arbeidsgivermappa ([#237](https://github.com/navikt/ekspertbistand/pull/237), bare dev) | [691](https://trello.com/c/is5SjNKk) |
| 🟢 | – | – | 🛤️ Arbeidsgiver ser vedtaket og brevet på ny rute, rendret usladdet med Gotenberg ([#250](https://github.com/navikt/ekspertbistand/pull/250), bare dev) | [693](https://trello.com/c/mmcwqp5V) |
| 🟢 | – | – | 🔔 Varsle arbeidsgiver om vedtak, med lenke til vedtaket på ny rute ([#252](https://github.com/navikt/ekspertbistand/pull/252), bare dev) | [645](https://trello.com/c/XZvrwKjG) |
| 🔴 | M1 | `M  ■■` | 🗄️ Journalføre vedtaksbrevet til deltaker på personmappa | [646](https://trello.com/c/4qcIiWBB) |
| 🔴 | M1 | `S  ■` | 📬 Distribuere vedtaksbrevet til deltaker med dokdist (klienten finnes). Dokdist varsler på nytt når brevet ikke er åpnet. | [599](https://trello.com/c/QA5bXVrf) |
| 🟢 | – | – | 🗄️ Sladde deltaker ved kode 6/7 i søknaden som journalføres på arbeidsgivermappa ([#261](https://github.com/navikt/ekspertbistand/pull/261)) | [703](https://trello.com/c/NBU7M75w) |

Gjenstår: cirka 4 dager. Kortet «Send ut vedtaksbrev og tilsagnsbrev»
([647](https://trello.com/c/6l4XA2H5)) dekkes av egne kort (Hanna, 30.10.).

### Økonomi (OeBS)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | 🏦 OeBS-klient via tiltaksøkonomi (OEBS-2283 løst) | [565](https://trello.com/c/m53Ud4QY) |
| 🟢 | – | – | 🏦 Sette av penger når vedtaket er innvilget | [650](https://trello.com/c/abzeX7gO) |
| 🟢 | – | – | 🔢 Sak-ID som løpenummer | [627](https://trello.com/c/NxvZl7bH) |
| 🟢 | – | – | 🏦 Avklare startdato og sluttdato med OeBS | [625](https://trello.com/c/znIGenFd) |
| 🟢 | – | – | 🏦 Skru på OeBS-integrasjonen hos oss og Valp i dev. Valp har gitt oss lesetilgang i dev (Valp [#8572](https://github.com/navikt/mulighetsrommet/pull/8572)). | [704](https://trello.com/c/zUU0nQvu) |
| 🟢 | – | – | 🏦 Teste bestilling og kvittering mot OeBS i T1 (runde 1, godkjent 13.11.) | [705](https://trello.com/c/HShDcuFH) |
| 🟢 | – | – | 🏦 Teste annullering mot OeBS i T1 (runde 1). Testet teknisk med rader lagt rett i `oebs_outbox`, fordi ingen flyt annullerer ennå. | [706](https://trello.com/c/qnJXAfzO) |
| 🟢 | – | – | 🏦 Status fra OeBS til saksgangen: hendelser for status på bestilling og faktura. Saksloggen viser `SENDT`, `AKTIV` og `FEILET` med feilkode og feilmelding fra Valp. Prosesseringen kjører i dev ([#259](https://github.com/navikt/ekspertbistand/pull/259), 20.11.) | [726](https://trello.com/c/u2XIE5PJ) |
| 🟡 | M1 | `XS ▪` | 🏦 Avslutt den manuelle testen: trekk tilbake skrivetilgangen i dev og gi Valp og OeBS beskjed. Testdataene blir liggende, og videre testing skjer via saksgangen i dev (Ken, 20.11.). | – (sjekkliste i specen `test_oebs_integrasjon_dev.md`) |
| 🟡 | M1 | `M  ■■` | 🏦 Saksbehandler velger kostnadssted ved foreløpig vedtak. Kostnadssted er ikke behandlende enhet, slik vi antok. Listen er hardkodet til arbeidslivssentrene til 727 er klar (i DOING). | [728](https://trello.com/c/gVCEUEry) |
| 🔴 | M3 | `XS ▪` | 🏦 Liste over alle kostnadssteder i ekspertbistand, og bekreftelse på at de finnes i OeBS (1891 i T1, 1991 og 11 enheter i prod). Ida skaffer listen. | [727](https://trello.com/c/6K5Czv2I) |
| 🔴 | M2 | `XL ■■■■` | 💸 Fakturere i OeBS når refusjonskravet er godkjent, og gjøre opp bestillingen i samme melding. Avslag gir ingen faktura. Kontrakten har byttet `kid` med `referanse` (Valp [#8527](https://github.com/navikt/mulighetsrommet/pull/8527)), så speilingen vår må oppdateres. Testes mot T1 i runde 2 når OeBS er klare. | [679](https://trello.com/c/Cue4nB5k) |
| 🔴 | M2 | `M  ■■` | 🏦 Feilhåndtering mot OeBS, holdt enkelt: saken flagges når bestilling, faktura eller oppgjør feiler, og saksgangen stopper til teamet har fulgt opp med OeBS. Utbetaling sperres til bestillingen er aktiv. Trenger saken ny bestilling etter retting, må den ha nytt nummer. | [724](https://trello.com/c/jgHBBvu3) |
| 🔴 | M3 | `S  ■` | 🏦 Registrere ekspertbistand som økonomisystem (blant annet i Ardoq) | [690](https://trello.com/c/c6qNIdJK) |
| 🔴 | M3 | `S  ■` | 🏦 Skru på OeBS i prod: start prosesseringen i prod og få lesetilgang i prod (Valp [#8573](https://github.com/navikt/mulighetsrommet/pull/8573)) | Mangler kort |

Gjenstår: cirka 17 dager.

### Refusjon og sluttrapport

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Sluttrapport vises i saksbehandling | [477](https://trello.com/c/YJI9Yf9G) |
| 🟢 | – | – | 📤 Refusjonskrav og sluttrapport knyttet bare til saken, ett krav per sak ([#247](https://github.com/navikt/ekspertbistand/pull/247)) | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 🧾 Refusjon og sluttrapport som oppgaver i oversikten for saksbehandler ([#248](https://github.com/navikt/ekspertbistand/pull/248), bare dev) | – |
| 🟢 | – | – | 📤 Refusjonskravet kan ikke overstige innvilget beløp, og Arena-saker avvises | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 📤 Sikkerhetsfunn M4: minne ved opplasting | [664](https://trello.com/c/3qjMQJdw) |
| 🟡 | M2 | `M  ■■` | 📤 Justere refusjonskravet etter Figma: vis avsatt beløp og fristen (i DOING) | [685](https://trello.com/c/ZY79MovZ) |
| 🟡 | M2 | `S  ■` | 📤 Justere innsending av sluttrapport (i DOING) | [686](https://trello.com/c/W4cdyLLf) |
| 🔴 | M2 | `M  ■■` | 📤 Saksbehandler åpner for ny innsending av sluttrapport når arbeidsgiver har sendt feil dokument. Den gamle sluttrapporten slettes. | [717](https://trello.com/c/icSAEClC) |
| 🟢 | – | – | 🛤️ Arbeidsgiver ser innsendt sluttrapport (bare filnavn) og refusjonskrav i ekspansjonskort på ny rute ([#268](https://github.com/navikt/ekspertbistand/pull/268), [#270](https://github.com/navikt/ekspertbistand/pull/270)) | [683](https://trello.com/c/omYxyPPP), [684](https://trello.com/c/hxzNc8Z4) |
| 🟢 | – | – | 🧾 Hendelser når refusjonskravet og sluttrapporten er mottatt ([#249](https://github.com/navikt/ekspertbistand/pull/249)) | [702](https://trello.com/c/TK7W0dt3) |
| 🔴 | M2 | `S  ■` | 🧾 Hendelse når sluttrapporten er bekreftet, med post i saksloggen | [715](https://trello.com/c/KBHePFbQ) |
| 🔴 | M2 | `S  ■` | 🧾 Hendelser når refusjonskravet er sendt til utbetaling eller avslått, med post i saksloggen | [716](https://trello.com/c/BvM7Dir9) |
| 🔴 | M2 | `L  ■■■` | 🧾 Behandle refusjonskrav. Saksbehandler eller beslutter attesterer i ett steg: godkjenn, eller godkjenn med redusert beløp. Avslag går i to trinn: saksbehandler innstiller, beslutter avslår. Statuser og GUI etter Figma. Sluttrapporten må være mottatt. | Mangler kort |
| 🔴 | M2 | `M  ■■` | 🗄️ Journalføre sluttrapporten på personmappa når den er mottatt | [701](https://trello.com/c/fHjZ4Yue) |
| 🔴 | M3 | `S  ■` | 📣 Varsle arbeidsgiver når refusjonskravet er godkjent eller avslått. Deltaker varsles ikke. | Mangler kort |
| 🔴 | M3 | `M  ■■` | 📣 To påminnelser til arbeidsgiver før refusjonsfristen, når kravet ikke er sendt inn | Mangler kort |
| 🔴 | M3 | `M  ■■` | 💸 Fristen går ut uten refusjonskrav: avslutt saken og frigjør beløpet ved å annullere bestillingen i OeBS | Mangler kort |
| 🔴 | M2 | `M  ■■` | 💸 Avslutte saken etter utbetaling og sluttrapport | Mangler kort |
| 🔴 | M3 | `S  ■` | 📤 Åpne innsending av refusjon og sluttrapport for arbeidsgiver i prod (i dag bare utenfor prod) | – |

Gjenstår: cirka 25 dager.

### Trekke, avvise og avbryte

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🔴 | M2 | `M  ■■` | 🚫 Saksbehandler trekker eller avviser søknaden før vedtak, med årsak. Saken og søknaden får egen sluttstatus. En godkjent søknad kan ikke trekkes. | Mangler kort |
| 🔴 | M2 | `M  ■■` | 🚫 Informasjon til arbeidsgiver og deltaker når søknaden er trukket eller avvist: varsel og visning på ny rute for arbeidsgiver, brev med dokdist til deltaker. Ved trekk står det at arbeidsgiver mister klageretten. | Mangler kort |
| 🔴 | M3 | `L  ■■■` | 🚫 Avbryte tiltaket: saksbehandler legger inn ny sluttdato i et notatfelt, og arbeidsgiver og deltaker får vedtak. Tilsagnet annulleres ikke, og arbeidsgiver kan fortsatt sende sluttrapport og refusjonskrav. | Mangler kort |
| 🔴 | Uavklart | – | Omgjøring av vedtak: nytt vedtak til arbeidsgiver og deltaker, og det gamle tilsagnet annulleres. Ikke regnet med. Grovt anslag: 2 uker eller mer. | [689](https://trello.com/c/KwIMHZP9) |

Gjenstår: cirka 10 dager, uten omgjøring. Brevtekstene til trekk, avvisning
og avbrudd må komme fra fag.

### Prodsetting og overgang fra Arena

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Automatisk deploy av saksbehandling til dev | [481](https://trello.com/c/4VdvOjsW) |
| 🔴 | M3, M4 | `L  ■■■` | 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten | Mangler kort |
| 🔴 | M3 | `L  ■■■` | 📡 Salesforce får arbeidsgiveraktivitet for nye saker fra go-live (i dag Kafka fra Arena) | [552](https://trello.com/c/6mVfQgod) |
| 🔴 | M3 | `L  ■■■` | 📡 Modia viser tiltaket for nye saker fra go-live (i dag fra Arena) | [596](https://trello.com/c/bgtBcIzk) |
| 🔴 | M3 | `L  ■■■` | 🧪 Akseptansetest og opplæring med saksbehandlere | Mangler kort |
| 🔴 | M4 | `XS ▪` | 🚦 Gjøre Entra-gruppene synlige i prod og bestille visning i Mine tilganger | [687](https://trello.com/c/zAfM9svq) |
| 🔴 | M3 | `XS ▪` | 📡 Avklare hvor saksbehandlingsløsningen åpnes fra, for eksempel Modia | [711](https://trello.com/c/MfEIrQO6) |

Gjenstår: cirka 20 dager. Salesforce og Modia er grove anslag til vi vet
hvordan de vil ta imot data.

### Etterlevelse, sikkerhet og drift

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Statistikk til datavarehus (BigQuery) | [480](https://trello.com/c/AH74rdE8) |
| 🟡 | M3 | `M  ■■` | Trusselmodell, verdivurdering og livsløp ferdig. ROS gjenstår, også at arbeidsgiver ser navnet på deltaker med fortrolig adresse. | [582](https://trello.com/c/80W3GhIB) |
| 🟡 | M3 | `M  ■■` | Juridisk logg (ikke-benekt), avklaring pågår | [555](https://trello.com/c/94yFdkSX) |
| 🟡 | M3 | `S  ■` | Begrense hver frontend til sin del av backend-API-et | [603](https://trello.com/c/XED3wgnm) |
| 🟢 | – | – | Validere klient i token i backend: TokenX `client_id` og Entra `azp_name` ([#254](https://github.com/navikt/ekspertbistand/pull/254)) | [633](https://trello.com/c/jdsNoa2n) |
| 🔴 | M3 | `L  ■■■` | 📜 Oppslagslogg og ArcSight (sikkerhetsfunn M2, M3) | [553](https://trello.com/c/s5v5Occl), [664](https://trello.com/c/3qjMQJdw) |
| 🔴 | M3 | `M  ■■` | Auditlogging av databaseendringer (uavklart om det gjelder oss) | [554](https://trello.com/c/y5GXvQ7x) |
| 🔴 | M3 | `L  ■■■` | 🛡️ Stanse behandling og slette | [584](https://trello.com/c/ft1q5B6v) |
| 🔴 | M3 | `M  ■■` | 🛡️ Slette søknad når deltaker trekker seg | [607](https://trello.com/c/aegaxk5R) |
| 🔴 | M3 | `S  ■` | Ekspertbistand i behandlingskatalogen | [665](https://trello.com/c/LAZXAYcx) |
| 🔴 | M3 | `M  ■■` | Disaster recovery-plan, testet | [591](https://trello.com/c/4xGXShb5) |
| 🔴 | M3 | `S  ■` | Alerts som `PrometheusRule` | [619](https://trello.com/c/VBAPFZPH) |
| 🔴 | M3 | `M  ■■` | UU-tester i bygg for saksbehandling | [593](https://trello.com/c/Flaq2NEE) |
| 🔴 | M3 | `S  ■` | Vise tydelig i saksbehandling at deltaker har adressebeskyttelse (krav 255) | [592](https://trello.com/c/jRixpurm) |

Gjenstår: cirka 29 dager.

## Hva må være med i første release

Klagebehandling er utenfor scope. Det løses trolig felles, utenfor teamet.
Tilbakekreving er også utenfor scope. Økonomi gjør det manuelt.

### 1. Søknad (arbeidsgiver)

| Hva | Status |
|-----|--------|
| Søknadsskjema, innsending, journalføring | 🟢 Ferdig, i prod |
| 🧹 Makssats per startdato (24 900 kr) | 🟢 Ferdig |
| 🧹 Fjerne «Hvilke utgifter skal tilskuddet dekke?» | 🟢 Ferdig ([#234](https://github.com/navikt/ekspertbistand/pull/234)) |
| 🪪 **Aa-reg: deltaker må ha arbeidsforhold hos valgt virksomhet.** Arbeidsgiver taster fnr, vi slår opp i Aa-reg og viser navn fra PDL. Uten arbeidsforhold kan søknaden ikke sendes inn. Backend validerer på nytt ved innsending. Arbeidsgiver ser navnet også når deltaker har fortrolig adresse, og det må stå i ROS og etterlevelse. | 🟢 I prod fra 9.11. ([#230](https://github.com/navikt/ekspertbistand/pull/230)), i både v1 og v2 av skjemaet. Arbeidsgiver kan legge inn seg selv, men må bekrefte det. |
| 🛤️ **Ny rute bak feature toggle.** Søknaden og alt arbeidsgiver ser i ny løsning, lages som nye sider på en egen rute. Dagens sider beholdes uendret for Arena-saker. Det er enklere å lage nye visninger på en rute med feature toggle enn å toggle funksjoner i sider som er i prod. | 🟢 Ruta finnes ([#238](https://github.com/navikt/ekspertbistand/pull/238)), bak `EKSPERTBISTAND_V2`, bare utenfor prod. Kvitteringen og tilskuddsbrevet i ekspansjonskort er ferdige (680, 681, 707). |
| Lagre adressebeskyttelse og skjerming ved opprettelse | 🟢 Ferdig |
| 📨 **Varsle deltaker når søknaden er sendt inn, med kopi av søknaden.** Kopien journalføres som utgående på personmappa og distribueres med dokdist. | 🟢 I prod fra 11.11. ([#251](https://github.com/navikt/ekspertbistand/pull/251)), i dagens Arena-flyt. |
| 📨 **Arbeidsgiver får beskjed i søknadsskjemaet** om at deltaker får kopi av søknaden, før søknaden sendes inn. | 🟢 I prod, i v1 og v2 |
| 🧹 Sikkerhetsfunn H1 (andres utkast kan leses/endres) og H2 (sletting stopper ved vedtak) | 🟢 Ferdig |
| 📤 Sikkerhetsfunn M4: minnebruk ved opplasting av sluttrapport og refusjon | 🟢 Ferdig |
| 🐛 Utkastet lagres ikke når virksomheten til eksperten inneholder kontrolltegn | 🟢 Ferdig ([#269](https://github.com/navikt/ekspertbistand/pull/269), 714) |
| Ikke opprette utkast før deltaker er bekreftet | 🟢 Ferdig ([#269](https://github.com/navikt/ekspertbistand/pull/269), 708) |

### 2. Saksbehandling fram til vedtak

| Hva | Status |
|-----|--------|
| Innlogging, roller, enheter, Tilgangsmaskinen (KJERNE) | 🟢 Ferdig i dev |
| Ruting via Norg, egne ansatte og kode 6/7 | 🟢 Ferdig |
| Listevisning, tildeling, sakslogg, foreløpig vedtak, beslutter, retur | 🟢 Ferdig i dev |
| 📝 Brevmaler for vedtak (seks maler) | 🟡 QA på tekstene med fag pågår (678). Brevene til deltaker skal ikke vise tilskuddsbeløpet. |
| ⚖️ Vilkårene «Deltaker er enig» og «Arbeidsforhold» settes **automatisk oppfylt**, med taggen «Automatisk». Saksbehandler kan overstyre, og da forsvinner taggen. | 🟢 Ferdig i dev ([#267](https://github.com/navikt/ekspertbistand/pull/267), 655) |
| ⚖️ Tre nye vilkår | 🟡 I DOING (656) |
| Småfiks i GUI: «Beløp», handlingskolonnen i sakslista og aktiv vedtaksfane | 🟢 Ferdig ([#258](https://github.com/navikt/ekspertbistand/pull/258)) |
| Småfiks i GUI: beslutter kan ikke endre vilkår, breadcrumb med arbeidsgiver og deltaker | 🟡 Breadcrumb er ferdig ([#265](https://github.com/navikt/ekspertbistand/pull/265), 653). 636 er i DOING. |
| Saksbehandler ser om deltaker har reservert seg i KRR | 🔴 Kort finnes (719) |
| Lenker fra deltaker til Aa-reg, Modia og Gosys | 🔴 Kort finnes (710) |
| Idempotens og samtidighet: to raske kall mot samme sak får begge 202, men bare det ene får effekt | 🔴 Kort finnes (694). Oppdaget i arbeidet med 691. |
| Frigjør beslutter / tildel annen beslutter | 🔴 Kort finnes. Kan vente hvis tida blir knapp. |
| 📜 Hendelser som viser hva arbeidsgiver ble varslet om (ikke-benekt), der de mangler i dag | 🟢 Ferdig ([#266](https://github.com/navikt/ekspertbistand/pull/266), 598) |

### 3. Etter vedtak: brev, journalføring og varsel

Når beslutter fatter vedtaket i dev, lages og journalføres brevet til
arbeidsgiver, pengene settes av i OeBS, og arbeidsgiver får varsel og ser
vedtaket på ny rute. Alt til deltaker mangler.

| Hva | Status |
|-----|--------|
| 🗄️📝 Lage og journalføre vedtaksbrevet til arbeidsgiver på arbeidsgivermappa. I nye saker er dette tilsagnsbrevet, med beløp og refusjonsfrist. | 🟢 Ferdig i dev ([#237](https://github.com/navikt/ekspertbistand/pull/237)) |
| 🗄️ Sladde deltaker ved kode 6/7 i vedtaksbrevet som journalføres på arbeidsgivermappa | 🟢 Ferdig i dev ([#237](https://github.com/navikt/ekspertbistand/pull/237)), som Team Tiltak. Arbeidsgiver ser brevet usladdet. |
| 🗄️ Sladde deltaker ved kode 6/7 i søknaden som journalføres på arbeidsgivermappa | 🟢 Ferdig ([#261](https://github.com/navikt/ekspertbistand/pull/261)) |
| 🗄️ Journalføre vedtaksbrevet til deltaker på personmappa | 🔴 Kort finnes (646). `DokArkivClient` støtter person som bruker. |
| 🔔 **Varsle arbeidsgiver om vedtak** (innvilget og avslag) via notifikasjonsplattformen | 🟢 Ferdig i dev ([#252](https://github.com/navikt/ekspertbistand/pull/252)). Varselet lenker til vedtaket på ny rute. |
| 🛤️ **Arbeidsgiver ser vedtaket på ny rute:** innvilget, innvilget med redusert beløp eller avslått, med brevet. Dagens visning av tilsagnsbrevet beholdes for Arena-saker. | 🟢 Ferdig i dev ([#250](https://github.com/navikt/ekspertbistand/pull/250)) |
| 📬 **Varsle deltaker om vedtak.** Vedtaksbrevet distribueres med dokdist og vises under «Dine saker» på nav.no. | 🔴 Kort finnes (599). Klienten finnes. Dokdist varsler deltaker på nytt når brevet ikke er åpnet (etterlevelseskrav 204). |

Arbeidsgiver får vedtaksbrevet i vår løsning, rendret usladdet med Gotenberg.
Vi henter aldri PDF fra Joark til arbeidsgiver. Beskjeden på
notifikasjonsplattformen lenker til vedtaket på ny rute. Dagens visning
beholdes for Arena-saker.

Deltaker får kopi av søknaden og vedtaksbrevet med dokdist, slik Team Tiltak
sender vedtak til deltaker. Kopien av søknaden er i prod, og vedtaksbrevet
gjenbruker det samme:

- Brevet journalføres som **utgående** journalpost med deltaker som bruker og
  mottaker. Da vises det under «Dine saker» på nav.no. Dokarkiv henter navnet
  på mottakeren fra PDL.
- `DokDistClient` sender den ferdigstilte journalposten til
  [dokdistfordeling](https://github.com/navikt/dokdistfordeling) med
  `distribusjonstidspunkt = KJERNETID`. `bestillendeFagsystem` og
  `dokumentProdApp` er `EKSPERTBISTAND`.
- `distribusjonstype` er `ANNET` for kopien av søknaden og `VEDTAK` for
  vedtaksbrevet.
- Dokdist velger kanal etter KRR, også papirbrev. Svarer dokdist 409, er
  journalposten allerede distribuert, og handleren regner det som suksess.

### 4. Økonomi (OeBS via tiltaksøkonomi)

| Hva | Status |
|-----|--------|
| 🏦 Klient, outbox, producer, status-consumer, topic-manifester | 🟢 Ferdig. Kjører i dev fra 20.11. ([#259](https://github.com/navikt/ekspertbistand/pull/259)). Ikke startet i prod. |
| 🔢 Egen nummerserie og kildesystem hos OeBS (OEBS-2283) | 🟢 Godkjent |
| 🏦 Valp leser bestillingene våre (ACL på topicet vårt, consumer hos Valp) | 🟢 Ferdig |
| 🏦 Lesetilgang for oss til status-topicene hos Valp | 🟢 Dev (Valp [#8572](https://github.com/navikt/mulighetsrommet/pull/8572)). 🟡 Prod venter på Valp [#8573](https://github.com/navikt/mulighetsrommet/pull/8573). |
| 🏦 Teste bestilling og kvittering mot OeBS i T1 | 🟢 Runde 1 godkjent 13.11. (705). Bestillingen går fra `SENDT` til `AKTIV`, og samme melding to ganger gir ingen dobbel bestilling. |
| 🏦 Teste annullering mot OeBS i T1 | 🟢 Testet teknisk 13.11. (706). Ingen flyt annullerer ennå, se spørsmål 3 og 4. |
| 🏦 Status fra OeBS til saksgangen, med sakslogg for `SENDT`, `AKTIV` og `FEILET` | 🟢 Ferdig ([#259](https://github.com/navikt/ekspertbistand/pull/259), 726) |
| 🏦 **Saksbehandler velger kostnadssted** ved foreløpig vedtak. Kostnadssted er ikke behandlende enhet. Alle kostnadsstedene må finnes i OeBS. | 🟡 728 i DOING, med hardkodet liste. Listen kommer i 727. |
| 🏦 Skru på OeBS i prod | 🔴 Ikke på Trello |
| 🔢 Sak-ID som løpenummer som matcher OeBS | 🟢 Ferdig |
| 🏦 Sette av penger (bestilling) når vedtaket er innvilget | 🟢 Ferdig |
| 💸 Utbetale (faktura) når refusjonskravet er godkjent, og gjøre opp bestillingen i samme melding | 🔴 Kort finnes (679). Runde 2 mot T1 venter på OeBS. `kid` er byttet med `referanse` i kontrakten. |
| 🏦 **Feilhåndtering mot OeBS.** Saken flagges ved feil, og saksgangen stopper til teamet har fulgt opp. Saksbehandler kan ikke utbetale før bestillingen er aktiv. | 🔴 Kort finnes (724). |
| 🏦 Registrere ekspertbistand som økonomisystem (blant annet i Ardoq) | 🔴 Kort finnes (690) |
| Tiltakskode og konto registrert hos Team VALP | 🟡 Virker i T1 (runde 1). Bekreft at det er på plass i prod. |

#### Kjente feil fra OeBS

Valp sender feilkode og feilmelding sammen med `FEILET`
([mulighetsrommet#8631](https://github.com/navikt/mulighetsrommet/pull/8631)),
og saksloggen viser dem. Hilde-Mari har delt et utvalg av kjente feil, og
Valp har dokumentert feil de har sett i prod
([Feilkoder fra OeBS](https://github.com/navikt/mulighetsrommet/tree/main/mulighetsrommet-tiltaksokonomi#feilkoder-fra-oebs-og-indikasjoner-p%C3%A5-hva-det-kan-bety)). Vi fyller på der når vi får forklaring
fra OeBS på nye feil. Ifølge Sondre skjer flere av feilene trolig bare i dev.

| Feilkode | Feilmelding | Område | Hva vi gjør |
|----------|-------------|--------|----------------|
| `PO_PDOI_ZERO_QTY` | Feil: Mengden må være større enn 0. | Antall | Feil i meldingen vår. Rett og send ny bestilling. |
| `PO_PDOI_INVALID_PROJ_INFO` | Feil: Prosjektopplysninger for konteringen er ikke gyldig. Valideringsfeil for prosjekt-/aktivitet. Kontakt Oracle Norge. | Prosjektopplysning | Feilkonfigurasjon hos OeBS, for eksempel tilsagnsår for langt fram i tid (Valp). Meld til OeBS. Send ny bestilling når det er rettet. |
| `PO_PDOI_INVALID_CHG_ACCOUNT` | Feil: Kostnadskonto (verdi = 0) er ikke en gyldig kostnadskonto. | Kontering | Oppsett hos OeBS eller Valp, som konteringsfeilen i T1 i november. |
| `PO_PDOI_CHARGE_FAILED` | Feil: Generering av Kostnadskonto mislyktes. | Kontering | Oppsett hos OeBS eller Valp. |
| `PO_PDOI_INVALID_ACTION` | Feil: Handling (VERDI= ) er ugyldig. | | Feil i meldingen vår. |
| `PO_PDOI_DOC_NUM_UNIQUE` | Feil: Dokumentnummeret må være unikt. ................. eksisterer allerede. | Tilsagnsnummer må være unikt | Nummeret er brukt før. Bekrefter at en ny bestilling må ha nytt nummer. |
| `PO_PDOI_INVALID_VENDOR` | Feil: Den angitte leverandøren (VERDI = 26643091) er ugyldig eller inaktiv. Velg en annen leverandør. (COLUMN_NAME=VENDOR_ID) | Leverandør | Meld til OeBS. Vi sender bare organisasjonsnummeret, så vi kan ikke rette dette selv. |
| `PO_PDOI_DERV_ERROR` | Feil: Avledningsfeil: Angitt VENDOR_NAME (VERDI= .................) er ugyldig. | Leverandør | Som over. |
| `PO_PDOI_COLUMN_NOT_NULL` | Feil: Kolonnen VENDOR_SITE_ID skal ikke være NULL. (VALUE=) | Leverandørsted | Som over. |
| `PO_PDOI_DERV_ERROR` | Feil: Avledningsfeil: Angitt VENDOR_SITE_CODE (VERDI= 987138130) er ugyldig. | Leverandørsted | Som over. |
| `DUPLICATE INVOICE NUMBER` | (faktura) | Duplikat | Tiltaksøkonomi sender på nytt når et HTTP-kall feiler, og kan da lage duplikat. Løser seg selv når OeBS har utbetalt den første (Valp). Ingen handling. |

### 5. Refusjon og sluttrapport

| Hva | Status |
|-----|--------|
| 📤 Arbeidsgiver sender inn refusjonskrav og sluttrapport | 🟢 Bygget, bare utenfor prod. Justering etter Figma og åpning i prod har egne rader. Ett krav per sak, knyttet bare til saken ([#247](https://github.com/navikt/ekspertbistand/pull/247)). |
| 📤 Beløp i refusjonskravet kan ikke overstige innvilget beløp | 🟢 Ferdig |
| 📤 Justere refusjonskravet og sluttrapporten etter Figma: vis avsatt beløp og fristen | 🟡 I DOING (685, 686) |
| 📤 Saksbehandler åpner for ny innsending av sluttrapport når feil dokument er sendt inn | 🔴 Kort finnes (717) |
| 🛤️ Arbeidsgiver ser innsendt sluttrapport og refusjonskrav i ekspansjonskort på ny rute. Sluttrapporten vises bare med filnavn, fordi den kan inneholde sensitive personopplysninger. | 🟢 Ferdig i dev ([#268](https://github.com/navikt/ekspertbistand/pull/268), [#270](https://github.com/navikt/ekspertbistand/pull/270)) |
| Saksbehandler ser refusjonskrav, sluttrapport og vedlegg | 🟢 Ferdig i dev |
| 🧾 Refusjon og sluttrapport som egne oppgaver i oversikten | 🟢 Ferdig i dev ([#248](https://github.com/navikt/ekspertbistand/pull/248)) |
| 🧾 Hendelser for refusjonskrav og sluttrapport | 🟡 Mottatt er ferdig ([#249](https://github.com/navikt/ekspertbistand/pull/249), 702). Bekreftet sluttrapport (715) og krav sendt til utbetaling eller avslått (716) har egne kort. |
| 🧾 **Behandle refusjonskrav:** saksbehandler eller beslutter attesterer i ett steg: godkjenn, eller godkjenn med redusert beløp, og send til utbetaling. Avslag går i to trinn: saksbehandler innstiller, beslutter avslår. Én sak har ett refusjonskrav, som kan ha flere vedlegg. Sluttrapporten må være mottatt før kravet kan godkjennes (dobbeltsjekkes). | 🔴 Figma finnes. Avklart 18.11. Ikke på Trello som eget kort. |
| 🗄️ Journalføre sluttrapporten på personmappa | 🔴 Kort finnes (701) |
| 📣 Varsle arbeidsgiver når refusjonskravet er godkjent eller avslått. Deltaker varsles ikke. | 🔴 Ikke på Trello. Om arbeidsgiver også skal få brev ved avslag, er et åpent spørsmål (4). |
| 📣 To påminnelser til arbeidsgiver før refusjonsfristen, når kravet ikke er sendt inn | 🔴 Ikke på Trello |
| 💸 Fristen går ut uten krav: avslutte saken og frigjøre beløpet (annullere bestillingen i OeBS) | 🔴 Ikke på Trello. Om det skjer automatisk, er et åpent spørsmål (3). |
| 💸 Avslutte saken når utbetalingen er gjort (`AVSLUTTET`) | 🔴 Ikke på Trello |
| 📤 Åpne innsending av refusjon og sluttrapport i prod | 🔴 Rutene er stengt i prod i dag |

Refusjon har naturlig slakk. Første refusjonskrav på en ny sak kommer først
når ekspertbistanden er gjennomført, altså uker til måneder etter første
vedtak. Hvis tida blir knapp, kan vi lansere søknad og vedtak først og
refusjonsbehandling noen uker etter. Det er en nødplan, ikke målet.

### 6. Prodsetting og overgang fra Arena

| Hva | Status |
|-----|--------|
| 🚦 Saksbehandlingsappen i prod | 🔴 Ingen `prod-gcp-saksbehandling.yaml` ennå |
| 🚦 Bytte prod fra Arena-flyten til `OpprettSak` (`basedOnEnvSuspending` i `configureEventHandlers`). Søknader sendt inn etter go-live går til oss, eldre saker blir i Arena. Ingen synk. | 🔴 Ikke på Trello |
| Entra-grupper i prod og rutine for å be om tilgang (STS-56018, Mine tilganger) | 🟢 Ferdig |
| 📡 **Salesforce og Modia får data om nye saker fra go-live.** Salesforce leser i dag arbeidsgiveraktivitet fra et Kafka-topic i Arena, og Modia viser tiltaket fra Arena. | 🔴 Krav for overgangen. Kort finnes, mottaket er ikke avklart. |
| 🚦 Gjøre Entra-gruppene synlige i prod og bestille visning i Mine tilganger | 🔴 Kort finnes (687). Gjøres rett før go-live. |
| 🧪 Akseptansetest og opplæring med saksbehandlere | 🔴 Ikke på Trello |
| 📡 Hvor saksbehandlingsløsningen åpnes fra, for eksempel Modia | 🔴 Kort finnes (711), uten beskrivelse |

### 7. Etterlevelse, sikkerhet og drift (krav før prod)

| Hva | Status |
|-----|--------|
| Sletterutiner (10 år, 1 år for arbeidsgiver) | 🟢 Ferdig |
| 📜 Sikkerhetsfunn M2 og M3: sakslogg og oppslagslogg til ArcSight | 🔴 Ikke startet. Må være på plass før saksbehandling åpnes i prod. |
| Juridisk logg (ikke-benekt) | 🟡 Avklaring pågår (Hanna) |
| Auditlogging av databaseendringer | 🔴 Uavklart om det gjelder oss |
| 🛡️ Stanse behandling og slette (personvern, krav 262), sletting når deltaker trekker seg | 🔴 Ikke startet |
| ROS, behandlingskatalog (også for Aa-reg), PVK. ROS må dekke at arbeidsgiver ser navnet på deltaker med fortrolig eller strengt fortrolig adresse. | 🟡 Delvis |
| Disaster recovery-plan, testet | 🔴 Ikke startet |
| Alerts som `PrometheusRule` i stedet for Grafana | 🔴 Ikke startet |
| UU-tester i bygg for saksbehandling | 🔴 Ikke startet |
| Validere klient i token i backend (hindre kryssing mellom apper) | 🟢 Ferdig ([#254](https://github.com/navikt/ekspertbistand/pull/254)) |
| Vise tydelig i saksbehandling at deltaker har adressebeskyttelse (krav 255) | 🔴 Kort finnes (592) |

### 8. Trekke, avvise, avbryte og omgjøre

Fra Hanna (e-post 16.11.). Klage er fortsatt utenfor scope.

| Situasjon | Når | Vedtak | Hvem får beskjed | Status |
|-----------|-----|:------:|------------------|--------|
| 🚫 **Trekke søknaden.** Arbeidsgiver vil trekke en søknad som ikke er behandlet, og mister da klageretten. I første omgang registrerer saksbehandler trekket. Arbeidsgiver kan ikke trekke søknaden selv. | Før vedtak. En godkjent søknad kan ikke trekkes. | Nei | Arbeidsgiver og deltaker får informasjon | 🔴 Mangler kort |
| 🚫 **Avvise søknaden.** Søknaden er kommet inn på feil grunnlag, for eksempel fordi deltaker ikke var informert. Vilkårene før søknad er ikke oppfylt, så saksbehandler behandler den ikke ordinært. | Før vedtak | Nei | Arbeidsgiver og deltaker får informasjon | 🔴 Mangler kort |
| 🚫 **Avbryte tiltaket.** Tiltaket er godkjent, og arbeidsgiver eller deltaker vil avslutte før planlagt slutt. Saksbehandler legger inn ny sluttdato i et notatfelt. Tilsagnet annulleres ikke, så arbeidsgiver kan fortsatt sende sluttrapport og refusjonskrav for påløpte utgifter. Deltaker skal ikke lenger stå på tiltak i Modia. | Etter innvilget vedtak. Startdatoen spiller ingen rolle. | Ja | Arbeidsgiver og deltaker får vedtak | 🔴 Mangler kort |
| **Omgjøre vedtaket.** Et vedtak må endres eller oppheves, etter klage eller fordi Nav finner en feil selv, for eksempel for lavt tilskudd eller feil ekspert. Gjelder retting og omgjøring etter forvaltningsloven kapittel 9 (§ 70 og § 71). Det gamle tilsagnet annulleres når nytt vedtak fattes. | Etter innvilget eller avslått vedtak | Ja | Arbeidsgiver og deltaker får vedtak | 🔴 Kort finnes (689). Ikke regnet med. |

Trekk og avvisning skjer før vedtak, så det finnes ingen bestilling i OeBS å
annullere. Ved avbrudd står bestillingen, fordi arbeidsgiver skal få dekket
påløpte utgifter. Omgjøring er den eneste av de fire som annullerer et
tilsagn.

Trekk og avvisning er ikke det samme som at deltaker trekker seg
([607](https://trello.com/c/aegaxk5R)). Da skal behandlingen stanses og søknaden slettes av
personvernhensyn (🛡️). Se spørsmål 9–13 under [Åpne spørsmål](#åpne-spørsmål).

### Kan vente til etter lansering

Utkast til prioritering. Rekkefølgen styres av tre spørsmål:

1. Stopper det saksbehandlingen?
2. Hvor fort etter lansering trenger vi det?
3. Mister vi data hvis vi venter?

Bruk ledig tid mellom milepælene på det som står øverst.

| # | Hva | Størrelse | Hvorfor denne plassen | Trello |
|:-:|-----|-----------|------------------------|--------|
| 1 | Frigjør beslutter og tildel annen beslutter | `M  ■■` | Saken er låst til én beslutter, også etter retur. Er beslutter syk eller på ferie, står saken stille. Behovet kommer fra første dag. Kutter vi den, må en utvikler endre tildelingen rett i prod-databasen. Det bør vi unngå. | [648](https://trello.com/c/Nouutlp7) |
| 2 | Oversikt over saker og refusjonsstatus (erstatter skyggeregnskap) | `L  ■■■` | Fag fører egne Excel-lister i dag, og Arena ber om refusjonsstatus (ARENA-11719). Fag har bekreftet at det ikke er et krav. Det er lavthengende frukt, og behovet vokser når de første refusjonskravene kommer inn. | [609](https://trello.com/c/ymoKGEi1) |
| 3 | Playwright: fiks eller fjern | `XS ▪` å fjerne, `M  ■■` å fikse | Ustabile tester i bygget koster tid hver uke fram mot lansering. Avgjør det nå, selv om resten av lista venter. | [618](https://trello.com/c/jy6tu0ys) |
| 4 | Vis at deltaker bor i en annen region enn arbeidsgiver | `S  ■` | Vi ruter på arbeidsgivers kommune, så saksbehandler kan mangle tilgang til deltaker i andre systemer. Fag har en omvei (flytter deltaker midlertidig), men vi bør ikke bygge på den. | [668](https://trello.com/c/z9vYoiqe) |
| 5 | «Fullverdig» mock med mocket backend og testbrukere | `L  ■■■` | Gjør demo, akseptansetest og opplæring enklere. Har størst verdi hvis den er klar før M3. | [654](https://trello.com/c/sUrtiwWE) |
| 6 | Statistikk over antall returer | `S  ■` | Returer og årsaker lagres allerede, så tallene kan lages i etterkant uten at data går tapt. Avklar først hvem som trenger dem. | [649](https://trello.com/c/NojzDsa9) |
| 7 | Måle effekt på sykefravær med Team Sykefravær | `M  ■■`, mest avklaring | Målingen sammenligner sykefravær 6 måneder før og etter. Dataene ligger i datavarehuset, så ingenting går tapt ved å vente. Hanna mener selv at det kan vente. | [658](https://trello.com/c/B9xGKaTM) |
| 8 | Navneopprydding i koden, nais/apm | `XS ▪` – `S  ■` per endring | Ingen brukereffekt. Ta det når vi likevel jobber i koden. | [626](https://trello.com/c/wAfv2ZmE) |

Refusjonsbehandling står ikke på denne lista. Den skal med i første
release, og å skyve den noen uker er bare en nødplan (se avsnitt 5).

## Tidslinje

Tidslinjen ligger som Gantt-diagram i
[lanseringsplan_2027-04-01.html](https://htmlpreview.github.io/?https://github.com/navikt/ekspertbistand/blob/lanseringsplan/planning/lanseringsplan_2027-04-01.html),
med ett spor per rad og uke for uke fram til 1. april. Lenken går via
htmlpreview.github.io, fordi GitHub bare viser kildekoden. Den viser versjonen
på branchen `lanseringsplan`. Hvert steg har samme emoji i diagrammet som i
statusoversikten, i
[Hva må være med i første release](#hva-må-være-med-i-første-release), i
sporene og i [Mangler på Trello](#mangler-på-trello). Endrer vi planen, må
vi oppdatere `PLAN` og `MILEPAELER` i HTML-fila også.

### Milepæler

Kolonnen «Milepæl» i statusoversikten viser hvilken milepæl hver oppgave hører
til. «Steg» er emojiene fra statusoversikten, sporene og tidslinjen. Sporene
jobber parallelt, så M1 og M2 bygges samtidig. Datoen er når siste steg er
ferdig i tidslinjen. Sikkerhetsfunn M2–M4 er noe annet enn milepælene M2–M4.

| Ferdig innen | Milepæl | Steg | Ferdig når |
|--------------|---------|------|------------|
| Uke 45 (6.11.) | **M0: rydd bordet** 🟡 | 🧹 | 🟢 Utgiftsbeskrivelsen er fjernet, og de eksterne avklaringene har eier. 📝 Brevmalene og 📜 hendelsene (598) ble ikke ferdige og er flyttet til M1. |
| Uke 46 (11.11.) | **P1: tidlig i prod** 🟢 | 🪪 📨 | 🟢 Nådd fem uker før planen. I prod, i dagens Arena-flyt: Aa-reg-sjekk i søknaden, kopi av søknaden til deltaker med dokdist, og tekst om kopien til arbeidsgiver i søknadsskjemaet. |
| Uke 3 (22.1.) | **M1: vedtaket går ut** | ⚖️ 🗄️ 🔔 📝 📬 📜 🏦 | I dev: 🟢 arbeidsgiver får varsel om vedtaket. 🟢 Vilkårene om arbeidsforhold og samtykke settes automatisk. De tre nye vilkårene finnes. Fag har godkjent brevmalene. Deltaker får vedtaksbrevet journalført og distribuert med dokdist. 🟢 Søknaden sladdes på arbeidsgivermappa. 🟢 Hendelsene for varsler til arbeidsgiver finnes (598). 🟢 Bestilling, kvittering og annullering er testet mot OeBS i T1 (13.11.), og prosesseringen kjører i dev. Saksbehandler velger kostnadssted. |
| Uke 6 (12.2.) | **M2: refusjonen går ut** | 🧾 💸 📤 🛤️ 🗄️ 🚫 | I dev: arbeidsgiver sender inn og ser refusjonskrav og sluttrapport på ny rute, etter Figma. Saksbehandler eller beslutter godkjenner, helt eller med redusert beløp. Avslag går via beslutter. Sluttrapporten journalføres. Utbetaling via OeBS, testet mot T1 i runde 2, og saken avsluttes. Utbetaling er sperret til bestillingen er aktiv, og teamet kan få en sak ut av en feilet bestilling, faktura eller oppgjør. Saksbehandler kan trekke og avvise en søknad før vedtak, og arbeidsgiver og deltaker får beskjed. |
| Uke 9 (5.3.) | **M3: prodklar** | 📡 📣 📜 🛡️ 🧪 🚦 🏦 💸 📤 🚫 og etterlevelse | Salesforce og Modia har testet mottaket. Arbeidsgiver får varsel når refusjonskravet er godkjent eller avslått, og to påminnelser før fristen. Beløpet frigjøres når fristen går ut uten krav. Saksbehandler kan avbryte et tiltak, og arbeidsgiver og deltaker får vedtak. Refusjon og sluttrapport er åpnet for arbeidsgiver i prod. Ekspertbistand er registrert som økonomisystem, og OeBS er skrudd på i prod. Idempotens i saksbehandlings-API-ene. Oppslagslogg og ArcSight, personvern, adressebeskyttelse vises i saksbehandling, alerts, DR testet, ROS og behandlingskatalog. Akseptansetest med saksbehandlere. Saksbehandlingsappen er deployet til prod bak tilgangsgruppene. Generalprøve på overgangen i dev. |
| Uke 11 (19.3.) | **M4: go-live** | 🚦 | Entra-gruppene er synlige i Mine tilganger. Arena stenger for nye ekspertbistand-saker. Søknader sendt inn etter go-live behandles hos oss. Arena behandler ferdig sakene som ligger der. Salesforce og Modia får data om nye saker fra samme dag. |
| Uke 12–13 | Buffer | – | Påske. Ingen planlagte prodsettinger. |

M1 er flyttet fra uke 5 til uke 3. P1 ble ferdig tidlig, og det meste som
gjenstår i M1, er små kort. M2 og M3 står fast. Vinterferien (uke 8 eller 9)
ligger i M3. Juleferien (uke 52–53) er ikke regnet med.

### Siste dato for eksterne svar

| Avhengighet | Trengs innen | Hvis ikke |
|-------------|--------------|-----------|
| 📡 Hvordan Salesforce og Modia vil ta imot data om nye saker | Uke 49 | Vi rekker ikke å bygge det til M3. Uten data kan vi ikke gå live, fordi Salesforce og Modia skal ha nye saker fra go-live. |
| 💸 OeBS er klare for runde 2: faktura og oppgjør av bestillingen i T1 | Uke 2 | Vi bygger faktura mot kontrakten og tester mot T1 etter M2. Da flyttes risikoen til M3. Eskaler i #samarbeid-valp-oebs. |
| 🏦 Lesetilgang i prod for `ekspertbistand-backend` til status-topicene (Valp [#8573](https://github.com/navikt/mulighetsrommet/pull/8573)) | Uke 6 | Vi får ikke kvitteringer fra OeBS i prod og kan ikke skru på OeBS der. |

Hanna og Ida driver avklaringene, men en utvikler bør ha teknisk eierskap til
hver av dem.

## Hvordan tre utviklere jobber parallelt

Vi deler arbeidet i tre spor som eier hver sin del av koden. Hvert spor går
fra backend til frontend, så ingen venter på en annen for å få noe ferdig.

### Spor A: søknad og deltaker

Eier `frontend/client`, `soknad/`, `aareg/`, `vedlegg/` og arbeidsgiversiden
av `refusjon/`.

Ferdig: 🧹 utgiftsbeskrivelsen er fjernet, 🛤️ ny rute med kvitteringen og
vedtaket i ekspansjonskort, 🪪 Aa-reg i prod, ⚖️ automatisk vilkår for
arbeidsforhold og «Deltaker er enig» (655), 🛤️ innsendt sluttrapport og
refusjonskrav på ny rute (683, 684), 🐛 kontrolltegn i utkastet (714),
utkast først når deltaker er bekreftet (708) og breadcrumb (653).

1. ⚖️ De tre nye vilkårene (656) og beslutter kan ikke endre vilkår (636).
   Begge i DOING.
2. 📤 Refusjonskrav og sluttrapport etter Figma (685, 686, i DOING). Ny
   innsending av sluttrapport (717). Åpne refusjon og sluttrapport for
   arbeidsgiver i prod.
3. 📡 Data om nye saker til Salesforce og Modia fra go-live. Avklar hvor
   saksbehandlingsløsningen åpnes fra (711).
4. 🛡️ Personvern: stanse behandling, sletting når deltaker trekker seg.
   KRR-merking av deltaker i saksbildet (719).

### Spor B: brev, journalføring og varsel

Eier `dokument/`, `dokarkiv/`, `dokdist/`, `notifikasjon/` og
varsel-handlerne i `event/handlers/`.

Ferdig: 📨 kopi av søknaden til deltaker med dokdist i prod, 🗄️📝 vedtaksbrevet
til arbeidsgiver journalført med sladding, 🔔 varsel til arbeidsgiver om
vedtak, 🗄️ sladding av søknaden på arbeidsgivermappa og 📜 hendelser for
varsler til arbeidsgiver (598).

1. 📝 QA av brevmalene med fag. Brevene til deltaker uten tilskuddsbeløp (678).
2. 🗄️📬 Journalfør vedtaksbrevet til deltaker og distribuer det med dokdist,
   som kopien av søknaden (646, 599).
3. 🗄️ Journalfør sluttrapporten (701).
4. 📜 Oppslagslogg og ArcSight (sikkerhetsfunn M2 og M3).
5. 📣 Varsle arbeidsgiver når refusjonskravet er godkjent eller avslått, og
   send to påminnelser før refusjonsfristen.
6. 🚫 Trekke og avvise søknaden før vedtak, og avbryte tiltaket: handlingen i
   saksbehandling, brev og varsel til arbeidsgiver og deltaker. Brevtekstene
   kommer fra fag.

### Spor C: økonomi og refusjonsbehandling

Eier `oebs/`, saksbehandlersiden av `refusjon/`, `Saksstatus`/`Oppgavetype`
og refusjonsfanen i `frontend/saksbehandling`.

Ferdig: 🏦 bestilling, kvittering og annullering testet mot OeBS i T1
(runde 1, 13.11.), refusjon knyttet bare til saken, 🧾 refusjon som oppgave i
oversikten, hendelser når refusjonskravet og sluttrapporten er mottatt, og
🏦 status fra OeBS til saksgangen (726). Prosesseringen kjører i dev.

1. 🏦 Kostnadssted ved foreløpig vedtak (728, i DOING), med liste fra 727.
   Trekk tilbake skrivetilgangen etter den manuelle testen, og si fra til
   Valp og OeBS.
2. 🧾 Behandle refusjonskrav: statuser, godkjenn (også med redusert beløp)
   av saksbehandler eller beslutter, avslå i to trinn, GUI
   etter Figma. Hendelsene for bekreftet sluttrapport (715) og krav sendt til
   utbetaling eller avslått (716) hører til her.
3. 💸 Faktura og status tilbake fra OeBS (679). Oppdater kontrakten med
   `referanse` før vi bygger. Test i runde 2 mot T1 når OeBS er klare.
   Avslutt saken. Frigjør beløpet når fristen går ut uten krav.
4. 🏦 Feilhåndtering mot OeBS (724): flagg saken ved feil og stopp
   saksgangen til teamet har fulgt opp. Sperr utbetaling til bestillingen er
   aktiv. Må være klart før faktura går i M2.
5. 🏦 Registrer ekspertbistand som økonomisystem (690). Skru på OeBS i prod
   når Valp har gitt lesetilgang ([#8573](https://github.com/navikt/mulighetsrommet/pull/8573)).
6. Idempotens og samtidighet i saksbehandlings-API-ene (694).
7. 🚦 Prod-manifest for saksbehandling, overgang fra Arena-flyten, Entra-gruppene
   synlige i prod (687).

### Felles arbeid

Drift og etterlevelse (alerts, DR, UU-tester, ROS, småfiks i GUI) tar den som
har ledig kapasitet mellom milepælene. Ikke start slike oppgaver midt i en
leveranse i sporet.

### Dette må skje i rekkefølge

```
Aa-reg i søknad (i prod) ──> automatisk vilkår

VedtakFattet ──> vedtaksbrev til arbeidsgiver (ferdig) ──> varsle arbeidsgiver
           ├──> vedtaksbrev til deltaker ──> distribuer med dokdist
           └──> OeBS-bestilling (ferdig) ──> refusjon godkjent ──> utbetaling ──> sak avsluttet

OeBS T1 klart (11.11.) ──> bestilling, kvittering og annullering testet (13.11.)
OeBS klare for runde 2 ──> faktura (💸, 679) ──> faktura testet i T1 ──┐
Valp prod-ACL (#8573) ─────────────────────────────────────────────────┴──> OeBS i prod

Brevtekster fra fag ──> 🚫 brev ved trukket, avvist og avbrutt
📡 Modia-mottak ──> 🚫 deltaker står ikke på tiltak etter avbrudd

Salesforce/Modia-mottak avklart ──> data om nye saker ──┐
M2/M3 + akseptansetest ─────────────────────────────────┴──> saksbehandling i prod ──> go-live
```

Dette kan skje samtidig:

- Brev og varsel til deltaker (B) og visningene på ny rute (A). Kontrakten er
  vedtaket og brevet som finnes i dag ([#237](https://github.com/navikt/ekspertbistand/pull/237), [#250](https://github.com/navikt/ekspertbistand/pull/250)).
- Refusjonsbehandling (C) og innsending av refusjon etter Figma (A).
  Kontrakten er tabellene og API-et som finnes, og hendelsene fra [#249](https://github.com/navikt/ekspertbistand/pull/249).

### Unngå kollisjoner i koden

- `event/Events.kt` er felles. Alle sporene legger til `EventData` og
  registrerer handlere i `configureEventHandlers`. Hold PR-ene små og merge
  ofte. Husk eksempel i `TestEventData.allEventSamples`.
- Flyway-versjoner kolliderer når to PR-er lager `V<n>` samtidig. Ta neste
  ledige nummer rett før merge, ikke når branchen lages.
- `saksbehandling/Db.kt` (`Vilkar`, `Saksstatus`) og `SakPage.tsx` deles av A,
  B og C. B legger til statusene for trukket og avvist, C statusene for
  refusjonen. Avtal hvem som endrer hva i samme uke.
- Kjør `EventFlowDiagram` etter nye events, ikke rediger `event-flow.md`.
- Én spec per kort i `specifications/`, som i dag. Specen er kontrakten
  mellom sporene.
- Vurder å la bygget på branchen finne logiske konflikter i migreringer og
  `EventData` automatisk, så vi slipper å rydde opp etter merge.

## Mangler på Trello

Disse bør bli kort:

1. 🧾 Behandle refusjonskrav: godkjenn helt eller redusert i ett steg, avslå i to trinn, statuser
2. 📣 Varsle arbeidsgiver når refusjonskravet er godkjent eller avslått
3. 💸 Avslutte saken etter utbetaling og sluttrapport
4. 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten i prod
5. 🧪 Akseptansetest og opplæring med saksbehandlere
6. 🏦 Teste utbetaling mot OeBS i T1, runde 2 (kan bli del av 679)
7. 🏦 Skru på OeBS i prod: start prosesseringen og lesetilgang i prod (Valp [#8573](https://github.com/navikt/mulighetsrommet/pull/8573))
8. 🚫 Trekke eller avvise søknaden før vedtak, med informasjon til arbeidsgiver og deltaker
9. 🚫 Avbryte tiltaket med vedtak til arbeidsgiver og deltaker
10. 📣 To påminnelser til arbeidsgiver før refusjonsfristen
11. 💸 Frigjøre beløpet og avslutte saken når fristen går ut uten krav

## Avklart

- **Go-live:** All saksbehandling av søknader som sendes inn etter go-live,
  skjer i ny løsning. Saker opprettet i Arena behandles ferdig der. Ingen
  synk mellom oss og Arena.
- **Varsel til deltaker:** Aa-reg-sjekken og 📨 kopi av søknaden gikk i prod
  før go-live (P1). 📬 Vedtaksbrevet til deltaker kommer først når den nye
  saksbehandlingsløsningen er live, fordi vedtak i Arena-saker ikke går via
  oss. Kanalen er dokdist, som Team Tiltak bruker for vedtak til deltaker.
- **Dokdist (Anders, 10.11.):** Vi har tilgang i dev og prod.
  `bestillendeFagsystem` er `EKSPERTBISTAND`, og kopien av søknaden har
  `distribusjonstype` `ANNET`. Handleren kjører i alle miljøer uten toggle.
- **Revarsling (16.11.):** Dokdist varsler deltaker på nytt automatisk når
  brevet ikke er åpnet. Det dekker etterlevelseskrav 204, så vi lager ikke
  revarsling selv.
- **Feil fra OeBS (18.11.):** Teamet følger opp avviste bestillinger og
  fakturaer, ikke saksbehandler. Feil må uansett rettes manuelt mot OeBS.
  `FEILET` gir allerede `log.error` som trigger alert i Slack, testet i dev.
  Saksloggen viser `SENDT`, `AKTIV` og `FEILET` med feilkode fra 20.11.
  (726). Saksbehandler skal ikke kunne utbetale før bestillingen er aktiv.
- **Feilhåndtering mot OeBS holdes enkel (Ken og Sondre, 20.11.):** Saken
  flagges når OeBS svarer `FEILET`, og saksgangen stopper til noen i teamet
  har fulgt opp (724). Vi bygger ikke egen retting per feilkode. Feil om
  leverandør kan vi ikke rette: vi sender bare organisasjonsnummeret,
  tiltaksøkonomi slår det opp i Brreg, og OeBS oppretter eller oppdaterer
  leverandøren selv. Det trengs ingen registrering før vi bestiller. Slike
  feil meldes til OeBS. Valp har ikke sett problemer med nye leverandører i
  prod.
- **Kostnadssted (Ken, 20.11.):** Kostnadssted er ikke behandlende enhet.
  Saksbehandler velger kostnadssted ved foreløpig vedtak (728). Til listen
  er klar (727), hardkoder vi arbeidslivssentrene. Alle kostnadsstedene må
  finnes i OeBS.
- **Manuell OeBS-test avsluttet (Ken, 20.11.):** Testdataene blir liggende i
  dev, hos Valp og i OeBS T1. Videre testing skjer via saksgangen i dev. Ny
  manuell test bare hvis OeBS ber om det.
- **Trekke, avvise og avbryte (Hanna, 16.11.):** I første omgang kan bare
  saksbehandler trekke en søknad, og bare før vedtak. Trekk og avvisning er
  ikke vedtak. Arbeidsgiver og deltaker får informasjon. Avbrudd av tiltaket
  er vedtak til begge, og tilsagnet annulleres ikke.
- **Omgjøring (Hanna, 16.11.):** Gjelder både etter klage og når Nav finner
  feil selv. Det gamle tilsagnet annulleres når nytt vedtak fattes.
- **Vedtaksbrev til deltaker (Hanna, 4.11.):** Deltaker skal ikke se
  tilskuddsbeløpet. Brevene til deltaker endres i QA-runden med fag.
- **Vedtaksbrev til arbeidsgiver:** I nye saker er vedtaksbrevet
  tilsagnsbrevet. Arbeidsgiver ser det i vår løsning, rendret usladdet med
  Gotenberg. Vi journalfører det sladdet på arbeidsgivermappa ved kode 6/7.
  Vi henter aldri PDF fra Joark til arbeidsgiver.
- **«Send ut vedtaksbrev og tilsagnsbrev» (Hanna, 30.10.):** Dekkes av egne
  kort ([647](https://trello.com/c/6l4XA2H5)).
- **Refusjon:** Én sak har ett refusjonskrav, som kan ha flere vedlegg.
  Sluttrapporten må være mottatt før kravet godkjennes. Det siste skal
  dobbeltsjekkes.
- **Refusjonsbehandling (møte 18.11., Ida, Espen, Ole Peter, Ken og Hanna):**
  Saksbehandler eller beslutter attesterer refusjonskravet i ett steg:
  godkjenn, eller godkjenn med redusert beløp. Avslag krever to trinn.
  Beslutter må ha budsjettdisponeringsmyndighet, og rollebeskrivelsen i Mine
  tilganger er oppdatert. Første kontroll, der arbeidsgiver kan bes sende
  inn vedlegg på nytt, regnes ikke som forhåndsvarsel.
- **Varsel og frist for refusjon (Jonas, 18.11.):** Bare arbeidsgiver
  varsles når refusjonskravet er godkjent eller avslått. Arbeidsgiver får to
  påminnelser før refusjonsfristen. Kommer ikke kravet, kan beløpet
  fristilles, altså frigjøres ved at bestillingen i OeBS annulleres.
- **Kapasitet:** Teamet har fullt fokus på ekspertbistand. 80 % er et godt
  anslag.
- **Fortrolig adresse:** Arbeidsgiver får se navnet på deltaker etter
  Aa-reg-oppslaget, også ved fortrolig eller strengt fortrolig adresse. Det
  må beskrives i ROS og etterlevelse.
- **Arena, Salesforce og Modia:** Arena stenger for nye saker når vi går
  live, senest 1. april. Salesforce og Modia må få data fra samme dag.
- **Oversikt over refusjonsstatus:** Ikke et krav. Fag fører egne Excel-lister.
  Lavthengende frukt hvis vi har tid.
- **Aa-reg (31.10.):** Hjemmelen er avklart, og oppslaget er i prod fra 9.11.
- **OeBS (11.11.):** Nummerserien og kildesystemet er godkjent (OEBS-2283).
  EKSPBIST er installert i T1.
- **OeBS runde 1 (13.11.):** Godkjent. Fra dev mot T1 gikk bestillingen fra
  `SENDT` til `AKTIV`, og samme melding to ganger ga ingen dobbel bestilling.
  Annulleringen gikk fra `ANNULLERING_SENDT` til `ANNULLERT`, med rader lagt
  rett i `oebs_outbox`. Feilet bestilling ble ikke testet. Tiltaksøkonomi i
  dev går mot T1. Runde 2, faktura og oppgjør, venter på at OeBS er klare.
  Valp har byttet `kid` med `referanse` i fakturakontrakten (Valp [#8527](https://github.com/navikt/mulighetsrommet/pull/8527)).
- **Entra-grupper (31.10.):** På plass i prod (STS-56018). Gruppene må gjøres
  synlige og bestilles i Mine tilganger før go-live (687).

## Åpne spørsmål

1. Hvordan vil Salesforce og Modia ta imot data om nye saker? Kan vi
   publisere samme kontrakt som Arena-topicet for arbeidsgiveraktivitet, og
   hvem eier visningen i Modia? Trengs innen uke 49.
2. Bekreft at sluttrapporten må være mottatt før refusjonskravet godkjennes.
3. Når fristen går ut uten krav, skal beløpet frigjøres automatisk, eller
   av saksbehandler? Hvor lenge før fristen sendes de to påminnelsene? Se
   [funn 4](README.md#funn).
4. Får arbeidsgiver brev i tillegg til varsel når refusjonskravet avslås,
   og skal bestillingen da annulleres? Varsel er avklart (18.11.). Se
   [funn 5](README.md#funn).
5. Skal omgjøring av vedtak ([689](https://trello.com/c/KwIMHZP9)) være med
   i første release? Den er ikke regnet med. Grovt anslag er 2 uker eller
   mer, fordi den krever nytt vedtak, annullering og ny bestilling i OeBS.
   Hanna spør også om vi må forhåndsvarsle ved omgjøring.
6. Skal deltaker få beskjed når sluttrapporten er mottatt
   ([688](https://trello.com/c/TobxmCHI))? Ved godkjent og avslått refusjon
   varsles bare arbeidsgiver (18.11.). Ved trukket og avvist søknad får
   deltaker informasjon (16.11.).
7. Hvor skal saksbehandlingsløsningen åpnes fra, Modia eller noe annet
   ([711](https://trello.com/c/MfEIrQO6))? Kortet har ingen beskrivelse.
8. Hvilken flyt skal annullere bestillingen i prod? Annullering er testet
   teknisk, men ingen flyt kaller den. Omgjøring annullerer det gamle
   tilsagnet, og avbrudd gjør det ikke (Hanna, 16.11.). Utløpt frist uten
   krav frigjør beløpet (18.11.). Ellers henger svaret på spørsmål 4 og på
   sletting når deltaker trekker seg (607).
9. Skal refusjonsfristen flyttes når tiltaket avbrytes og får ny sluttdato?
   Må bestillingen i OeBS også få ny sluttdato?
10. Skal avbrudd av tiltaket gå via beslutter, slik andre vedtak gjør?
11. Er avvisning et vedtak? Etter forvaltningsloven av 1967 § 2 tredje ledd
    regnes avvisning av en sak som enkeltvedtak, med klagerett. Sjekk med
    jurist, også hva ny forvaltningslov sier, før vi lager avvisning uten
    vedtak.
12. Skal saksbehandler trekke eller avvise søknaden når deltaker trekker seg
    før vedtak, før søknaden slettes ([607](https://trello.com/c/aegaxk5R))?
13. Hvordan får arbeidsgiver informasjonen ved trekk og avvisning: varsel og
    visning på ny rute, eller også brev?
14. Hvordan forhåndsvarsler vi arbeidsgiver før avslag eller redusert beløp
    på refusjonskravet? Første kontroll, der arbeidsgiver kan bes sende
    vedlegg på nytt, regnes ikke som forhåndsvarsel (18.11.).
15. Skal saksbehandler kunne be arbeidsgiver sende vedlegg til
    refusjonskravet på nytt i løsningen? Det er ikke regnet med. Ny
    innsending av sluttrapport har eget kort (717).
16. Hva får arbeidsgiver vite når kravet godkjennes med redusert beløp: bare
    varsel, eller også begrunnelse og brev? Frigjør oppgjøret av bestillingen
    resten av beløpet i OeBS?
17. Trenger vi alert når kvitteringen fra OeBS uteblir helt? I dag merker
    vi bare `FEILET`, ikke en bestilling som aldri får svar. Tas med i 724.
18. Feilhåndtering mot OeBS (724): hvordan nummererer vi en ny bestilling
    for samme sak når OeBS har rettet feilen, og hva ser arbeidsgiver mens
    saken er flagget? Se [Kjente feil fra OeBS](#kjente-feil-fra-oebs).

## Endringslogg

Én rad per uke, skrevet fredag. «Gjenstår» er summen fra statusoversikten.
«Kapasitet igjen» er ukene som er igjen fram til uke 11, × 3 utviklere × 80 %,
minus vinterferie. Vi er i rute når «Gjenstår» er lavere enn «Kapasitet igjen»
og avstanden øker uke for uke.

«Status» er «Gjenstår» delt på «Kapasitet igjen»: 🟢 under 70 %, 🟡 70–90 %,
🔴 over 90 %.

| Uke | Dato | Gjenstår | Ferdig eller nedjustert | Ny scope | Kapasitet igjen | Status | Hva skjedde |
|----:|------|---------:|------------------------:|---------:|----------------:|--------|-------------|
| 43 | 25.10. | 138 | – | – | 215 | 🟢 64 % | Første plan. |
| 44 | 31.10. | 120 | −27 | +9 | 204 | 🟢 59 % | Ferdig: makssats, adressebeskyttelse, sletterutiner, H1 og H2, Entra-grupper i prod, OeBS-klient og bestilling, sak-ID, M4 og beløpsgrense på refusjonskrav. Brevmalene venter bare på fag. Ny: 🛤️ nye ruter og visninger for arbeidsgiver, 🏦 test mot OeBS i T1. |
| 46 | 11.11. | 97 | −36 | +13 | 180 | 🟢 54 % | Gjelder uke 45 og 46. **P1 er i prod**, fem uker før planen: Aa-reg-sjekk og kopi av søknaden til deltaker med dokdist. Ferdig i dev: utgiftsbeskrivelsen fjernet, 🛤️ ny rute, vedtaksbrevet til arbeidsgiver journalført med sladding og vist på ny rute, refusjon knyttet bare til saken og som oppgave i oversikten. Vedtakssida ([#250](https://github.com/navikt/ekspertbistand/pull/250)) løste også tilskuddsbrevet i ekspansjonskort (681), og [#253](https://github.com/navikt/ekspertbistand/pull/253) gjorde ferdig kvitteringssida (680). 598 er avgrenset til hendelser for varsler til arbeidsgiver og starter på nytt. Validering av klient i token er i PR [#254](https://github.com/navikt/ekspertbistand/pull/254). M1 flyttes fra uke 5 til uke 3. Ny: 685, 686, 694, 701, 703, 690, 687, 592 og QA av brevene til deltaker (678). Omgjøring av vedtak (689) er ikke regnet med. OeBS T1 er klart 11.11., og ballen ligger hos oss: vi mangler lesetilgang til status-topicene hos Valp og har ikke skrudd på OeBS-integrasjonen. Nye kort: skru på (704), test bestilling (705) og test annullering (706). 🏦 flyttes fra uke 3 til uke 47. |
| 46 | 15.11. | 99 | −3,6 | +5,4 | 180 | 🟢 55 % | Resten av uke 46. **OeBS runde 1 er godkjent**: bestilling, kvittering og annullering virker mot T1 fra dev (704, 705, 706). Runde 2 (faktura) venter på OeBS. Ferdig i dev: 🔔 varsel til arbeidsgiver om vedtak (645), 🧾 hendelser for mottatt refusjonskrav og sluttrapport (702), 🗄️ sladding av søknaden på arbeidsgivermappa (703), validering av klient i token (633), visning av innsendt søknad (707) og småfiks (652, 709, 713). Ny: 🐛 714, 708, 710, 711, 715, 716, rydding etter runde 1 og å skru på OeBS i prod. DOING er tom ved starten av uke 47. |
| 47 | 20.11. | 114 | −8,5 | +23,6 | 168 | 🟢 68 % | **OeBS-prosesseringen kjører i dev** ([#259](https://github.com/navikt/ekspertbistand/pull/259)), og saksloggen viser `SENDT`, `AKTIV` og `FEILET` med feilkode fra Valp (726). Den manuelle testen er avsluttet, og testdataene blir liggende. Ferdig i dev: vilkåret om arbeidsforhold oppfylles fra Aa-reg (655), refusjonskrav på ny rute (683, 684), 🐛 kontrolltegn i utkastet og utkast først når deltaker er bekreftet (714, 708, [#269](https://github.com/navikt/ekspertbistand/pull/269)), 📜 hendelser for varsler til arbeidsgiver (598, [#266](https://github.com/navikt/ekspertbistand/pull/266)) og breadcrumb (653, [#265](https://github.com/navikt/ekspertbistand/pull/265)). Gotenberg 9 med PDF/A ([#275](https://github.com/navikt/ekspertbistand/pull/275)–[#277](https://github.com/navikt/ekspertbistand/pull/277)). Ny: trekke, avvise og avbryte (10 dager), påminnelser og frist for refusjonskrav (5), feilhåndtering mot OeBS (724), kostnadssted (728, 727), ny innsending av sluttrapport (717) og KRR-merking av deltaker (719). Kostnadssted er ikke behandlende enhet. Feilhåndteringen mot OeBS holdes enkel og er nedjustert fra L til M: saken flagges, og leverandørfeil meldes til OeBS. I DOING: 656, 636, 685, 686 og 728. |
