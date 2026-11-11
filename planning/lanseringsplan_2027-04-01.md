# Lanseringsplan ekspertbistand: frist 1. april 2027

Status per 11.11.2026. Grunnlaget er Trello-tavla «Team FAGER» (listene
`Ekspertbistand`, `DOING`, `Weekly commits`, `Huskeliste ekspertbistand` og
`Backlog`), åpne PR-er og koden på `main`.

Planen gir oversikt. Detaljer i hver oppgave tar vi i teamet, kort for kort.

## Kort fortalt

- Søknad → sak → vilkårsvurdering → foreløpig vedtak → beslutter fatter
  vedtak er bygget og kjører i dev. Etter vedtaket journalføres brevet til
  arbeidsgiver, pengene settes av i OeBS, og arbeidsgiver ser vedtaket på ny
  rute. Det som mangler, er vedtaksbrevet til deltaker, varslene,
  refusjonsbehandlingen og utbetalingen.
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
- Største risikoer er eksterne: at OeBS får T1 klart for test, og hvordan
  Salesforce og Modia skal få data om nye saker. Tilgang til Aa-reg og
  dokdist er på plass.
- Ny scope uten avklaring: omgjøring av vedtak
  ([689](https://trello.com/c/KwIMHZP9)). Den er ikke regnet med, se
  [Åpne spørsmål](#åpne-spørsmål).

## Statusoversikt

🟢 ferdig · 🟡 pågår eller delvis ferdig · 🔴 ikke startet.
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

**Totalt cirka 97 utviklerdager** (20 utvikleruker). Frigjøring av beslutter
og omgjøring av vedtak er ikke med. Kapasiteten er cirka 180 dager: 16 uker ×
3 utviklere, minus vinterferie, ganger 80 %. Det gir rom for at anslagene
sprekker med rundt 80 %, men ikke for mye ny scope.

### Søknad (arbeidsgiver)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Søknadsskjema, innsending og journalføring (i prod) | – |
| 🟢 | – | – | Varsle arbeidsgiver ved mottatt søknad | [475](https://trello.com/c/GC5c2wJi) |
| 🟢 | – | – | Kall mot aareg-services i stedet for arbeidsgiver-innsyn-aareg | [478](https://trello.com/c/nhonb0wT) |
| 🟢 | – | – | 🧹 Makssats per startdato | [651](https://trello.com/c/3zvmjKiu) |
| 🟢 | – | – | 🧹 Fjerne utgiftsbeskrivelse (#234) | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 🛤️ Ny rute bak feature toggle (`EKSPERTBISTAND_V2`) for søknaden og funksjonene i ny løsning (#238). Dagens sider beholdes uendret. | [682](https://trello.com/c/cEJ38zjf) |
| 🟢 | – | – | 🛤️ Kvitteringen i ekspansjonskort på ny rute, før og etter vedtak (#250, #253) | [680](https://trello.com/c/RZqSZ8p3) |
| 🟢 | – | – | 🛤️ Tilskuddsbrevet (vedtaksbrevet i nye saker) i ekspansjonskort på ny rute, med tittel per utfall (#250) | [681](https://trello.com/c/H7jYzwos) |
| 🟢 | – | – | 🪪 Aa-reg-sjekk av deltaker i søknaden, i v1 og v2 (#230, i prod) | [601](https://trello.com/c/3Oq7KiFc) |
| 🟢 | – | – | 📨 Kopi av søknaden til deltaker: journalført som utgående på personmappa og distribuert med dokdist (#251, i prod) | [676](https://trello.com/c/khxsoU0N), [675](https://trello.com/c/2QCwaAJX), [692](https://trello.com/c/LU0D56cu) |
| 🟢 | – | – | 📨 Arbeidsgiver får beskjed i søknadsskjemaet om at deltaker får kopi, og infoskriv til deltaker (i prod) | [675](https://trello.com/c/2QCwaAJX), [695](https://trello.com/c/lPVEHuKB) |
| 🟢 | – | – | Behandlingskatalog for tilgang til Aa-reg | [534](https://trello.com/c/MWtJNaH0) |
| 🟢 | – | – | Lagre adressebeskyttelse og skjerming ved opprettelse | [661](https://trello.com/c/6L5KVmvN) |
| 🟢 | – | – | Sletterutiner, 10 år og 1 år for arbeidsgiver | [583](https://trello.com/c/gzIhkeip) |
| 🔴 | M3 | `XS ▪` | Informere arbeidsgiver om at søknader fjernes etter 1 år | [595](https://trello.com/c/9pex2Tgp) |
| 🟢 | – | – | 🧹 Sikkerhetsfunn H1 og H2 | [664](https://trello.com/c/3qjMQJdw) |

Gjenstår: cirka 1 time.

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
| 🔴 | M1 | `S  ■` | 📜 Kvitteringshendelser for varsler til arbeidsgiver som mangler i dag: søknad mottatt, godkjent og avlyst. Noop-handlere, ingen backfill. Starter på nytt fra `main`, PR #201 videreføres ikke. | [598](https://trello.com/c/2HQeHav3) |
| 🟢 | – | – | Entra-grupper og tilgangsrutine i prod (STS-56018) | [506](https://trello.com/c/9wiRRC1M), [532](https://trello.com/c/WB9G2Pxx) |
| 🔴 | M1 | `M  ■■` | ⚖️ «Automatisk»-tag og oppfylt som standard på «Deltaker er enig» og «Arbeidsforhold» (fra Aa-reg). Saksbehandler kan overstyre. | [655](https://trello.com/c/WCMkpira) |
| 🔴 | M1 | `M  ■■` | ⚖️ Tre nye vilkår | [656](https://trello.com/c/hLd2jDAj) |
| 🔴 | M1 | `S  ■` | Beslutter kan ikke endre vilkår (GUI) | [636](https://trello.com/c/eUsR2C7t) |
| 🔴 | M1 | `XS ▪` | «Nytt beløp» blir «Beløp» | [652](https://trello.com/c/fQeOJnTi) |
| 🔴 | M1 | `S  ■` | Breadcrumb med arbeidsgiver og deltaker | [653](https://trello.com/c/ulp1r0W4) |
| 🔴 | M3 | `M  ■■` | Idempotens og samtidighet i saksbehandlings-API-ene: to raske kall får begge 202, men bare det ene får effekt | [694](https://trello.com/c/kVXWdoeJ) |
| 🔴 | Etter | `M  ■■` | Frigjør beslutter og tildel annen beslutter (kan vente) | [648](https://trello.com/c/Nouutlp7) |

Gjenstår: cirka 12 dager, uten frigjøring av beslutter.

### Etter vedtak: brev, journalføring og varsel

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | 🗄️📝 Lage og journalføre vedtaksbrevet til arbeidsgiver (tilsagnsbrevet i nye saker) for innvilget, redusert beløp og avslått, med sladding ved kode 6/7 på arbeidsgivermappa (#237, bare dev) | [691](https://trello.com/c/is5SjNKk) |
| 🟢 | – | – | 🛤️ Arbeidsgiver ser vedtaket og brevet på ny rute, rendret usladdet med Gotenberg (#250, bare dev) | [693](https://trello.com/c/mmcwqp5V) |
| 🟡 | M1 | `XS ▪` | 🔔 Varsle arbeidsgiver om vedtak, med lenke til vedtaket på ny rute (PR #252) | [645](https://trello.com/c/XZvrwKjG) |
| 🔴 | M1 | `M  ■■` | 🗄️ Journalføre vedtaksbrevet til deltaker på personmappa | [646](https://trello.com/c/4qcIiWBB) |
| 🔴 | M1 | `S  ■` | 📬 Distribuere vedtaksbrevet til deltaker med dokdist (klienten finnes). Avklar nytt varsel når brevet ikke er åpnet. | [599](https://trello.com/c/QA5bXVrf) |
| 🔴 | M1 | `S  ■` | 🗄️ Sladde deltaker ved kode 6/7 i søknaden som journalføres på arbeidsgivermappa. Gjenbruk sladdingen fra 691. I dag ligger en TODO i `JournalfoerInnsendtSoknad`. | [703](https://trello.com/c/NBU7M75w) |

Gjenstår: cirka 5 dager. Kortet «Send ut vedtaksbrev og tilsagnsbrev»
([647](https://trello.com/c/6l4XA2H5)) dekkes av egne kort (Hanna, 30.10.).

### Økonomi (OeBS)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | 🏦 OeBS-klient via tiltaksøkonomi (OEBS-2283 løst) | [565](https://trello.com/c/m53Ud4QY) |
| 🟢 | – | – | 🏦 Sette av penger når vedtaket er innvilget | [650](https://trello.com/c/abzeX7gO) |
| 🟢 | – | – | 🔢 Sak-ID som løpenummer | [627](https://trello.com/c/NxvZl7bH) |
| 🟢 | – | – | 🏦 Avklare startdato og sluttdato med OeBS | [625](https://trello.com/c/znIGenFd) |
| 🔴 | M1 | `S  ■` | 🏦 Teste bestilling mot OeBS i T1. Nummerserien er godkjent, T1 er ikke klart for test ennå. | Mangler kort |
| 🔴 | M2 | `XL ■■■■` | 💸 Fakturere i OeBS når refusjonskravet er godkjent, og gjøre opp bestillingen i samme melding. Avslag gir ingen faktura. | [679](https://trello.com/c/Cue4nB5k) |
| 🔴 | M3 | `S  ■` | 🏦 Registrere ekspertbistand som økonomisystem (blant annet i Ardoq) | [690](https://trello.com/c/c6qNIdJK) |

Gjenstår: cirka 12 dager.

### Refusjon og sluttrapport

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Sluttrapport vises i saksbehandling | [477](https://trello.com/c/YJI9Yf9G) |
| 🟢 | – | – | 📤 Refusjonskrav og sluttrapport knyttet bare til saken, ett krav per sak (#247) | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 🧾 Refusjon og sluttrapport som oppgaver i oversikten for saksbehandler (#248, bare dev) | – |
| 🟢 | – | – | 📤 Refusjonskravet kan ikke overstige innvilget beløp, og Arena-saker avvises | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 📤 Sikkerhetsfunn M4: minne ved opplasting | [664](https://trello.com/c/3qjMQJdw) |
| 🔴 | M2 | `M  ■■` | 📤 Justere refusjonskravet etter Figma: vis avsatt beløp og fristen | [685](https://trello.com/c/ZY79MovZ) |
| 🔴 | M2 | `S  ■` | 📤 Justere innsending av sluttrapport (beskrivelse mangler) | [686](https://trello.com/c/W4cdyLLf) |
| 🔴 | M2 | `M  ■■` | 🛤️ Arbeidsgiver ser innsendt sluttrapport (bare filnavn) og refusjonskrav i ekspansjonskort på ny rute | [683](https://trello.com/c/omYxyPPP), [684](https://trello.com/c/hxzNc8Z4) |
| 🟡 | M2 | `S  ■` | 🧾 Hendelser for refusjonskrav og sluttrapport. Mottatt er i PR #249. «Send til utbetaling» og «bekreft mottatt» gjenstår. | [702](https://trello.com/c/TK7W0dt3) |
| 🔴 | M2 | `L  ■■■` | 🧾 Behandle refusjonskrav: godkjenn, eller avslå via beslutter. Statuser og GUI etter Figma. Sluttrapporten må være mottatt. | Mangler kort |
| 🔴 | M2 | `M  ■■` | 🗄️ Journalføre sluttrapporten på personmappa når den er mottatt | [701](https://trello.com/c/fHjZ4Yue) |
| 🔴 | M3 | `S  ■` | 📣 Varsle arbeidsgiver når refusjonen er godkjent | Mangler kort |
| 🔴 | M2 | `M  ■■` | 💸 Avslutte saken etter utbetaling og sluttrapport | Mangler kort |
| 🔴 | M3 | `S  ■` | 📤 Åpne innsending av refusjon og sluttrapport for arbeidsgiver i prod (i dag bare utenfor prod) | – |

Gjenstår: cirka 19 dager.

### Prodsetting og overgang fra Arena

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Automatisk deploy av saksbehandling til dev | [481](https://trello.com/c/4VdvOjsW) |
| 🔴 | M3, M4 | `L  ■■■` | 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten | Mangler kort |
| 🔴 | M3 | `L  ■■■` | 📡 Salesforce får arbeidsgiveraktivitet for nye saker fra go-live (i dag Kafka fra Arena) | [552](https://trello.com/c/6mVfQgod) |
| 🔴 | M3 | `L  ■■■` | 📡 Modia viser tiltaket for nye saker fra go-live (i dag fra Arena) | [596](https://trello.com/c/bgtBcIzk) |
| 🔴 | M3 | `L  ■■■` | 🧪 Akseptansetest og opplæring med saksbehandlere | Mangler kort |
| 🔴 | M4 | `XS ▪` | 🚦 Gjøre Entra-gruppene synlige i prod og bestille visning i Mine tilganger | [687](https://trello.com/c/zAfM9svq) |

Gjenstår: cirka 20 dager. Salesforce og Modia er grove anslag til vi vet
hvordan de vil ta imot data.

### Etterlevelse, sikkerhet og drift

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Statistikk til datavarehus (BigQuery) | [480](https://trello.com/c/AH74rdE8) |
| 🟡 | M3 | `M  ■■` | Trusselmodell, verdivurdering og livsløp ferdig. ROS gjenstår, også at arbeidsgiver ser navnet på deltaker med fortrolig adresse. | [582](https://trello.com/c/80W3GhIB) |
| 🟡 | M3 | `M  ■■` | Juridisk logg (ikke-benekt), avklaring pågår | [555](https://trello.com/c/94yFdkSX) |
| 🟡 | M3 | `S  ■` | Begrense hver frontend til sin del av backend-API-et | [603](https://trello.com/c/XED3wgnm) |
| 🟡 | M3 | `XS ▪` | Validere klient i token i backend: TokenX `client_id` og Entra `azp_name` (PR #254 venter på review) | [633](https://trello.com/c/jdsNoa2n) |
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
| 🧹 Fjerne «Hvilke utgifter skal tilskuddet dekke?» | 🟢 Ferdig (#234) |
| 🪪 **Aa-reg: deltaker må ha arbeidsforhold hos valgt virksomhet.** Arbeidsgiver taster fnr, vi slår opp i Aa-reg og viser navn fra PDL. Uten arbeidsforhold kan søknaden ikke sendes inn. Backend validerer på nytt ved innsending. Arbeidsgiver ser navnet også når deltaker har fortrolig adresse, og det må stå i ROS og etterlevelse. | 🟢 I prod fra 9.11. (#230), i både v1 og v2 av skjemaet. Arbeidsgiver kan legge inn seg selv, men må bekrefte det. |
| 🛤️ **Ny rute bak feature toggle.** Søknaden og alt arbeidsgiver ser i ny løsning, lages som nye sider på en egen rute. Dagens sider beholdes uendret for Arena-saker. Det er enklere å lage nye visninger på en rute med feature toggle enn å toggle funksjoner i sider som er i prod. | 🟢 Ruta finnes (#238), bak `EKSPERTBISTAND_V2`, bare utenfor prod. 🔴 Kvitteringssiden og tilskuddsbrevet i ekspansjonskort har egne kort. |
| Lagre adressebeskyttelse og skjerming ved opprettelse | 🟢 Ferdig |
| 📨 **Varsle deltaker når søknaden er sendt inn, med kopi av søknaden.** Kopien journalføres som utgående på personmappa og distribueres med dokdist. | 🟢 I prod fra 11.11. (#251), i dagens Arena-flyt. |
| 📨 **Arbeidsgiver får beskjed i søknadsskjemaet** om at deltaker får kopi av søknaden, før søknaden sendes inn. | 🟢 I prod, i v1 og v2 |
| 🧹 Sikkerhetsfunn H1 (andres utkast kan leses/endres) og H2 (sletting stopper ved vedtak) | 🟢 Ferdig |
| 📤 Sikkerhetsfunn M4: minnebruk ved opplasting av sluttrapport og refusjon | 🟢 Ferdig |

### 2. Saksbehandling fram til vedtak

| Hva | Status |
|-----|--------|
| Innlogging, roller, enheter, Tilgangsmaskinen (KJERNE) | 🟢 Ferdig i dev |
| Ruting via Norg, egne ansatte og kode 6/7 | 🟢 Ferdig |
| Listevisning, tildeling, sakslogg, foreløpig vedtak, beslutter, retur | 🟢 Ferdig i dev |
| 📝 Brevmaler for vedtak (seks maler) | 🟡 QA på tekstene med fag pågår (678). Brevene til deltaker skal ikke vise tilskuddsbeløpet. |
| ⚖️ Vilkårene «Deltaker er enig» og «Arbeidsforhold» settes **automatisk oppfylt**, med taggen «Automatisk». Saksbehandler kan overstyre, og da forsvinner taggen. | 🔴 Ett kort for begge (655), i Weekly commits |
| ⚖️ Tre nye vilkår | 🔴 Kort i Weekly commits |
| Småfiks i GUI: beslutter kan ikke endre vilkår, «Beløp», breadcrumb med arbeidsgiver og deltaker | 🔴 Kort i Weekly commits |
| Idempotens og samtidighet: to raske kall mot samme sak får begge 202, men bare det ene får effekt | 🔴 Kort finnes (694). Oppdaget i arbeidet med 691. |
| Frigjør beslutter / tildel annen beslutter | 🔴 Kort finnes. Kan vente hvis tida blir knapp. |
| 📜 Hendelser som viser hva arbeidsgiver ble varslet om (ikke-benekt), der de mangler i dag | 🔴 Kort finnes (598). Startes på nytt fra `main`, bygger på kvitteringshendelsen fra 645. |

### 3. Etter vedtak: brev, journalføring og varsel

Når beslutter fatter vedtaket i dev, lages og journalføres brevet til
arbeidsgiver, pengene settes av i OeBS, og arbeidsgiver ser vedtaket på ny
rute. Varsel til arbeidsgiver og alt til deltaker mangler.

| Hva | Status |
|-----|--------|
| 🗄️📝 Lage og journalføre vedtaksbrevet til arbeidsgiver på arbeidsgivermappa. I nye saker er dette tilsagnsbrevet, med beløp og refusjonsfrist. | 🟢 Ferdig i dev (#237) |
| 🗄️ Sladde deltaker ved kode 6/7 i vedtaksbrevet som journalføres på arbeidsgivermappa | 🟢 Ferdig i dev (#237), som Team Tiltak. Arbeidsgiver ser brevet usladdet. |
| 🗄️ Sladde deltaker ved kode 6/7 i søknaden som journalføres på arbeidsgivermappa | 🔴 Kort finnes (703). TODO i `JournalfoerInnsendtSoknad`. |
| 🗄️ Journalføre vedtaksbrevet til deltaker på personmappa | 🔴 Kort finnes (646). `DokArkivClient` støtter person som bruker. |
| 🔔 **Varsle arbeidsgiver om vedtak** (innvilget og avslag) via notifikasjonsplattformen | 🟡 PR #252 åpen. Varselet lenker til vedtaket på ny rute. |
| 🛤️ **Arbeidsgiver ser vedtaket på ny rute:** innvilget, innvilget med redusert beløp eller avslått, med brevet. Dagens visning av tilsagnsbrevet beholdes for Arena-saker. | 🟢 Ferdig i dev (#250) |
| 📬 **Varsle deltaker om vedtak.** Vedtaksbrevet distribueres med dokdist og vises under «Dine saker» på nav.no. | 🔴 Kort finnes (599). Klienten finnes. Avklar nytt varsel når brevet ikke er åpnet (etterlevelseskrav 204). |

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
| 🏦 Klient, outbox, producer, status-consumer, topic-manifester | 🟢 Ferdig |
| 🔢 Egen nummerserie og kildesystem hos OeBS (OEBS-2283) | 🟢 Godkjent |
| 🏦 Teste bestilling mot OeBS i T1 | 🔴 **Venter på at T1 blir klart for test** |
| 🔢 Sak-ID som løpenummer som matcher OeBS | 🟢 Ferdig |
| 🏦 Sette av penger (bestilling) når vedtaket er innvilget | 🟢 Ferdig |
| 💸 Utbetale (faktura) når refusjonskravet er godkjent, og gjøre opp bestillingen i samme melding | 🔴 Kort finnes (679) |
| 🏦 Registrere ekspertbistand som økonomisystem (blant annet i Ardoq) | 🔴 Kort finnes (690) |
| Tiltakskode og konto registrert hos Team VALP | 🟡 Avtalt, men ikke bekreftet levert |

### 5. Refusjon og sluttrapport

| Hva | Status |
|-----|--------|
| 📤 Arbeidsgiver sender inn refusjonskrav og sluttrapport | 🟡 Bygget, bare utenfor prod. Ett krav per sak, knyttet bare til saken (#247). |
| 📤 Beløp i refusjonskravet kan ikke overstige innvilget beløp | 🟢 Ferdig |
| 📤 Justere refusjonskravet og sluttrapporten etter Figma: vis avsatt beløp og fristen | 🔴 Kort finnes (685, 686) |
| 🛤️ Arbeidsgiver ser innsendt sluttrapport og refusjonskrav i ekspansjonskort på ny rute. Sluttrapporten vises bare med filnavn, fordi den kan inneholde sensitive personopplysninger. | 🔴 Kort finnes (683, 684). Åpent på kortet: skal kontonummeret vises? |
| Saksbehandler ser refusjonskrav, sluttrapport og vedlegg | 🟢 Ferdig i dev |
| 🧾 Refusjon og sluttrapport som egne oppgaver i oversikten | 🟢 Ferdig i dev (#248) |
| 🧾 Hendelser for refusjonskrav og sluttrapport | 🟡 Mottatt er i PR #249 (702). «Send til utbetaling» og «bekreft mottatt» gjenstår. |
| 🧾 **Behandle refusjonskrav:** godkjenn → send til utbetaling (ingen beslutter). Avslå → via beslutter. Én sak har ett refusjonskrav, som kan ha flere vedlegg. Sluttrapporten må være mottatt før kravet kan godkjennes (dobbeltsjekkes). | 🔴 Figma finnes. Ikke på Trello som eget kort. |
| 🗄️ Journalføre sluttrapporten på personmappa | 🔴 Kort finnes (701) |
| 📣 Varsle arbeidsgiver når refusjonen er godkjent (ikke deltaker) | 🔴 Ikke på Trello. Om deltaker skal varsles, er et åpent spørsmål (688). |
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
| Validere klient i token i backend (hindre kryssing mellom apper) | 🟡 PR #254 venter på review |
| Vise tydelig i saksbehandling at deltaker har adressebeskyttelse (krav 255) | 🔴 Kort finnes (592) |

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
[lanseringsplan_2027-04-01.html](https://htmlpreview.github.io/?https://github.com/navikt/ekspertbistand/blob/main/planning/lanseringsplan_2027-04-01.html),
med ett spor per rad og uke for uke fram til 1. april. Lenken går via
htmlpreview.github.io, fordi GitHub bare viser kildekoden. Den viser versjonen
på `main`. Hvert steg har samme emoji i diagrammet som i
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
| Uke 3 (22.1.) | **M1: vedtaket går ut** | ⚖️ 🗄️ 🔔 📝 📬 📜 🏦 | I dev: arbeidsgiver får varsel om vedtaket. Vilkårene om arbeidsforhold og samtykke settes automatisk, og de tre nye vilkårene finnes. Fag har godkjent brevmalene. Deltaker får vedtaksbrevet journalført og distribuert med dokdist. Søknaden sladdes på arbeidsgivermappa. Hendelsene for varsler til arbeidsgiver finnes (598). OeBS-bestillingen er testet i T1. |
| Uke 6 (12.2.) | **M2: refusjonen går ut** | 🧾 💸 📤 🛤️ 🗄️ | I dev: arbeidsgiver sender inn og ser refusjonskrav og sluttrapport på ny rute, etter Figma. Saksbehandler godkjenner, eller avslår via beslutter. Sluttrapporten journalføres. Utbetaling via OeBS, og saken avsluttes. |
| Uke 9 (5.3.) | **M3: prodklar** | 📡 📣 📜 🛡️ 🧪 🚦 🏦 📤 og etterlevelse | Salesforce og Modia har testet mottaket. Arbeidsgiver får varsel når refusjonen er godkjent. Refusjon og sluttrapport er åpnet for arbeidsgiver i prod. Ekspertbistand er registrert som økonomisystem. Idempotens i saksbehandlings-API-ene. Oppslagslogg og ArcSight, personvern, adressebeskyttelse vises i saksbehandling, alerts, DR testet, ROS og behandlingskatalog. Akseptansetest med saksbehandlere. Saksbehandlingsappen er deployet til prod bak tilgangsgruppene. Generalprøve på overgangen i dev. |
| Uke 11 (19.3.) | **M4: go-live** | 🚦 | Entra-gruppene er synlige i Mine tilganger. Arena stenger for nye ekspertbistand-saker. Søknader sendt inn etter go-live behandles hos oss. Arena behandler ferdig sakene som ligger der. Salesforce og Modia får data om nye saker fra samme dag. |
| Uke 12–13 | Buffer | – | Påske. Ingen planlagte prodsettinger. |

M1 er flyttet fra uke 5 til uke 3. P1 ble ferdig tidlig, og det meste som
gjenstår i M1, er små kort. M2 og M3 står fast. Vinterferien (uke 8 eller 9)
ligger i M3. Juleferien (uke 52–53) er ikke regnet med.

### Siste dato for eksterne svar

| Avhengighet | Trengs innen | Hvis ikke |
|-------------|--------------|-----------|
| 🔔 Tekst i varsel til arbeidsgiver om vedtak (jurist). PR #252 gjenbruker e-posttittelen og SMS-teksten fra varselet om mottatt søknad, som er i prod. | Uke 47 | Vi merger med teksten fra varselet om mottatt søknad. |
| 📬 Om dokdist kan varsle deltaker på nytt når brevet ikke er åpnet (etterlevelseskrav 204, Team Dokumentløsninger) | Uke 49 | Vi må lage revarslingen selv, eller avklare kravet med etterlevelse. |
| 🏦 OeBS T1 klart for test (nummerserien er godkjent) | Uke 2 | Bestilling og utbetaling blir ikke testet mot OeBS før prod. Eskaler i uke 50. |
| 📡 Hvordan Salesforce og Modia vil ta imot data om nye saker | Uke 49 | Vi rekker ikke å bygge det til M3. Uten data kan vi ikke gå live, fordi Salesforce og Modia skal ha nye saker fra go-live. |

Hanna og Ida driver avklaringene, men en utvikler bør ha teknisk eierskap til
hver av dem.

## Hvordan tre utviklere jobber parallelt

Vi deler arbeidet i tre spor som eier hver sin del av koden. Hvert spor går
fra backend til frontend, så ingen venter på en annen for å få noe ferdig.

### Spor A: søknad og deltaker

Eier `frontend/client`, `soknad/`, `aareg/`, `vedlegg/` og arbeidsgiversiden
av `refusjon/`.

Ferdig: 🧹 utgiftsbeskrivelsen er fjernet, 🛤️ ny rute med kvitteringen og
vedtaket i ekspansjonskort, 🪪 Aa-reg i prod.

1. ⚖️ Automatisk vilkår for arbeidsforhold og «Deltaker er enig» (655), og de
   tre nye vilkårene (656).
2. 📤 Refusjonskrav og sluttrapport etter Figma (685, 686), visning på ny rute
   (683, 684). Åpne refusjon og sluttrapport for arbeidsgiver i prod.
3. 📡 Data om nye saker til Salesforce og Modia fra go-live.
4. 🛡️ Personvern: stanse behandling, sletting når deltaker trekker seg.

### Spor B: brev, journalføring og varsel

Eier `dokument/`, `dokarkiv/`, `dokdist/`, `notifikasjon/` og
varsel-handlerne i `event/handlers/`.

Ferdig: 📨 kopi av søknaden til deltaker med dokdist i prod, 🗄️📝 vedtaksbrevet
til arbeidsgiver journalført med sladding.

1. 🔔 Varsle arbeidsgiver om vedtak (645, PR #252).
2. 📝 QA av brevmalene med fag. Brevene til deltaker uten tilskuddsbeløp (678).
3. 🗄️📬 Journalfør vedtaksbrevet til deltaker og distribuer det med dokdist,
   som kopien av søknaden (646, 599).
4. 🗄️ Sladd søknaden på arbeidsgivermappa (703). Journalfør sluttrapporten
   (701).
5. 📜 Hendelser for varsler til arbeidsgiver (598). Oppslagslogg og ArcSight
   (sikkerhetsfunn M2 og M3).
6. 📣 Varsle arbeidsgiver når refusjonen er godkjent.

### Spor C: økonomi og refusjonsbehandling

Eier `oebs/`, saksbehandlersiden av `refusjon/`, `Saksstatus`/`Oppgavetype`
og refusjonsfanen i `frontend/saksbehandling`.

Ferdig: 🏦 bestilling i OeBS, refusjon knyttet bare til saken og 🧾 refusjon
som oppgave i oversikten.

1. 🧾 Hendelser for refusjonskrav og sluttrapport (702, PR #249). Behandle
   refusjonskrav: statuser, godkjenn, avslå via beslutter, GUI etter Figma.
2. 💸 Faktura og status tilbake fra OeBS (679). Avslutt saken.
3. 🏦 Test bestilling og utbetaling mot OeBS i T1 når det er klart. Registrer
   ekspertbistand som økonomisystem (690).
4. Idempotens og samtidighet i saksbehandlings-API-ene (694).
5. 🚦 Prod-manifest for saksbehandling, overgang fra Arena-flyten, Entra-gruppene
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

OeBS T1 klart ──> bestilling og utbetaling testet ──> økonomi i prod

Salesforce/Modia-mottak avklart ──> data om nye saker ──┐
M2/M3 + akseptansetest ─────────────────────────────────┴──> saksbehandling i prod ──> go-live
```

Dette kan skje samtidig:

- Brev og varsel til deltaker (B) og visningene på ny rute (A). Kontrakten er
  vedtaket og brevet som finnes i dag (#237, #250).
- Refusjonsbehandling (C) og innsending av refusjon etter Figma (A).
  Kontrakten er tabellene og API-et som finnes, og hendelsene i PR #249.

### Unngå kollisjoner i koden

- `event/Events.kt` er felles. Alle sporene legger til `EventData` og
  registrerer handlere i `configureEventHandlers`. Hold PR-ene små og merge
  ofte. Husk eksempel i `TestEventData.allEventSamples`.
- Flyway-versjoner kolliderer når to PR-er lager `V<n>` samtidig. Ta neste
  ledige nummer rett før merge, ikke når branchen lages.
- `saksbehandling/Db.kt` (`Vilkar`, `Saksstatus`) og `SakPage.tsx` deles av A
  og C. Avtal hvem som endrer hva i samme uke.
- Kjør `EventFlowDiagram` etter nye events, ikke rediger `event-flow.md`.
- Én spec per kort i `specifications/`, som i dag. Specen er kontrakten
  mellom sporene.
- Vurder å la bygget på branchen finne logiske konflikter i migreringer og
  `EventData` automatisk, så vi slipper å rydde opp etter merge.

## Mangler på Trello

Disse bør bli kort:

1. 🧾 Behandle refusjonskrav: godkjenn, avslå via beslutter, statuser
2. 📣 Varsle arbeidsgiver når refusjonen er godkjent
3. 💸 Avslutte saken etter utbetaling og sluttrapport
4. 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten i prod
5. 🧪 Akseptansetest og opplæring med saksbehandlere
6. 🏦 Teste bestilling og utbetaling mot OeBS i T1

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
- **OeBS (31.10.):** Nummerserien og kildesystemet er godkjent (OEBS-2283),
  og klienten vår er klar. Vi venter på at T1 blir klart for test.
- **Entra-grupper (31.10.):** På plass i prod (STS-56018). Gruppene må gjøres
  synlige og bestilles i Mine tilganger før go-live (687).

## Åpne spørsmål

1. Hvordan vil Salesforce og Modia ta imot data om nye saker? Kan vi
   publisere samme kontrakt som Arena-topicet for arbeidsgiveraktivitet, og
   hvem eier visningen i Modia? Trengs innen uke 49.
2. Bekreft at sluttrapporten må være mottatt før refusjonskravet godkjennes.
3. Hva skjer med saken og OeBS-bestillingen når refusjonsfristen går ut uten
   krav? Se [funn 4](README.md#funn).
4. Får arbeidsgiver brev og varsel når refusjonskravet avslås, og skal
   bestillingen da annulleres? Se [funn 5](README.md#funn).
5. Skal omgjøring av vedtak ([689](https://trello.com/c/KwIMHZP9)) være med
   i første release? Kortet har ingen beskrivelse, og det er ikke regnet med.
6. Skal deltaker varsles ved refusjon, sluttrapport og trukket søknad
   ([688](https://trello.com/c/TobxmCHI))? Planen regner bare med varsel til
   arbeidsgiver ved godkjent refusjon.
7. Kan dokdist varsle deltaker på nytt når vedtaksbrevet ikke er åpnet
   (etterlevelseskrav 204, [599](https://trello.com/c/QA5bXVrf))?

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
| 46 | 11.11. | 97 | −36 | +13 | 180 | 🟢 54 % | Gjelder uke 45 og 46. **P1 er i prod**, fem uker før planen: Aa-reg-sjekk og kopi av søknaden til deltaker med dokdist. Ferdig i dev: utgiftsbeskrivelsen fjernet, 🛤️ ny rute, vedtaksbrevet til arbeidsgiver journalført med sladding og vist på ny rute, refusjon knyttet bare til saken og som oppgave i oversikten. Vedtakssida (#250) løste også tilskuddsbrevet i ekspansjonskort (681), og #253 gjorde ferdig kvitteringssida (680). 598 er avgrenset til hendelser for varsler til arbeidsgiver og starter på nytt. Validering av klient i token er i PR #254. M1 flyttes fra uke 5 til uke 3. Ny: 685, 686, 694, 701, 703, 690, 687, 592 og QA av brevene til deltaker (678). Omgjøring av vedtak (689) er ikke regnet med. |
