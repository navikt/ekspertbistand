# Lanseringsplan ekspertbistand: frist 1. april 2027

Status per 31.10.2026. Grunnlaget er Trello-tavla «Team FAGER» (listene
`Ekspertbistand`, `DOING`, `Weekly commits` og `Backlog`), åpne PR-er og
koden på `main`.

Planen gir oversikt. Detaljer i hver oppgave tar vi i teamet, kort for kort.

## Kort fortalt

- Søknad → sak → vilkårsvurdering → foreløpig vedtak → beslutter fatter
  vedtak er bygget og kjører i dev. Det som mangler, er alt som skjer
  **etter** at vedtaket er fattet: brev, journalføring, varsling, penger og
  refusjon.
- Alle søknader som sendes inn etter go-live, behandles i vår løsning. Saker
  som er opprettet i Arena, behandles ferdig der. Vi synker ingenting mellom
  oss og Arena. Arena stenger for nye saker når vi går live, senest 1. april,
  og Salesforce og Modia må få data om nye saker fra samme dag.
- Aa-reg-sjekken og varsel til deltaker ved innsending går i prod så snart de
  er klare. De fungerer selv om saksbehandlingen fortsatt skjer i Arena.
- Den reelle fristen er **fredag 19. mars 2027 (uke 11)**. Påskedag er
  28. mars, og 1. april er torsdagen etter påske. Vi bør ikke prodsette i
  påskeuka.
- Vi har cirka 18 arbeidsuker (uke 45–51 og uke 1–11) for tre utviklere,
  minus ferie. Det holder hvis vi starter de eksterne avklaringene nå og
  jobber i tre spor som ikke tråkker på hverandre.
- Største risikoer er eksterne: at OeBS får T1 klart for test, tilgang til
  dokdist og hvordan Salesforce og Modia skal få data om nye saker.

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

**Totalt cirka 120 utviklerdager** (24 utvikleruker). Frigjøring av beslutter
er ikke med. Kapasiteten er cirka 204 dager: 18 uker × 3
utviklere, minus vinterferie, ganger 80 %. Det gir rom for at anslagene
sprekker med rundt 70 %, men ikke for mye ny scope.

### Søknad (arbeidsgiver)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Søknadsskjema, innsending og journalføring (i prod) | – |
| 🟢 | – | – | Varsle arbeidsgiver ved mottatt søknad | [475](https://trello.com/c/GC5c2wJi) |
| 🟢 | – | – | Kall mot aareg-services i stedet for arbeidsgiver-innsyn-aareg | [478](https://trello.com/c/nhonb0wT) |
| 🟢 | – | – | 🧹 Makssats per startdato | [651](https://trello.com/c/3zvmjKiu) |
| 🟡 | M0 | `S  ■` | 🧹 Fjerne utgiftsbeskrivelse (PR #190 lukket, branch finnes) | [620](https://trello.com/c/NoJhv4wi) |
| 🔴 | M1 | `M  ■■` | 🛤️ Ny rute bak feature toggle for søknaden og funksjonene i ny løsning. Dagens sider beholdes uendret. | [682](https://trello.com/c/cEJ38zjf) |
| 🔴 | M1 | `S  ■` | 🛤️ Kvitteringssiden i ekspansjonskort på ny rute, med samme innhold som i dag | [680](https://trello.com/c/RZqSZ8p3) |
| 🟡 | P1 | `L  ■■■` | 🪪 Aa-reg-sjekk av deltaker i søknaden (klient finnes, hjemmel avklart). Går i prod før overgangen. | [601](https://trello.com/c/3Oq7KiFc) |
| 🔴 | P1 | `M  ■■` | 📨 Varsle deltaker om innsendt søknad: kopi av søknaden journalført som utgående og distribuert med dokdist. Går i prod før overgangen. | [599](https://trello.com/c/QA5bXVrf) |
| 🔴 | P1 | `S  ■` | 📨 Fortelle arbeidsgiver i søknadsskjemaet at deltaker får varsel og kopi av søknaden. Må i prod samtidig med varselet. | Mangler kort |
| 🔴 | P1 | `S  ■` | Behandlingskatalog for tilgang til Aa-reg | [534](https://trello.com/c/MWtJNaH0) |
| 🟢 | – | – | Lagre adressebeskyttelse og skjerming ved opprettelse | [661](https://trello.com/c/6L5KVmvN) |
| 🟢 | – | – | Sletterutiner, 10 år og 1 år for arbeidsgiver | [583](https://trello.com/c/gzIhkeip) |
| 🔴 | M3 | `XS ▪` | Informere arbeidsgiver om at søknader fjernes etter 1 år | [595](https://trello.com/c/9pex2Tgp) |
| 🟢 | – | – | 🧹 Sikkerhetsfunn H1 og H2 | [664](https://trello.com/c/3qjMQJdw) |

Gjenstår: cirka 14 dager.

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
| 🟡 | M0 | `XS ▪` | 📝 Brevmaler for vedtak (ferdige, venter på finpuss fra fag) | [563](https://trello.com/c/3moZGD2U) |
| 🟡 | M0 | `S  ■` | 📜 Event for alt som skjer (PR #201) | [598](https://trello.com/c/2HQeHav3) |
| 🟢 | – | – | Entra-grupper og tilgangsrutine i prod (STS-56018) | [506](https://trello.com/c/9wiRRC1M), [532](https://trello.com/c/WB9G2Pxx) |
| 🔴 | M1 | `M  ■■` | ⚖️ Vilkåret «deltaker har arbeidsforhold» automatisk oppfylt fra Aa-reg | Mangler kort |
| 🔴 | M1 | `S  ■` | ⚖️ «Automatisk»-tag på «Deltaker er enig» | [655](https://trello.com/c/WCMkpira) |
| 🔴 | M1 | `M  ■■` | ⚖️ To nye vilkår | [656](https://trello.com/c/hLd2jDAj) |
| 🔴 | M1 | `S  ■` | Beslutter kan ikke endre vilkår (GUI) | [636](https://trello.com/c/eUsR2C7t) |
| 🔴 | M1 | `XS ▪` | «Nytt beløp» blir «Beløp» | [652](https://trello.com/c/fQeOJnTi) |
| 🔴 | M1 | `S  ■` | Breadcrumb med arbeidsgiver og deltaker | [653](https://trello.com/c/ulp1r0W4) |
| 🔴 | Etter | `M  ■■` | Frigjør beslutter og tildel annen beslutter (kan vente) | [648](https://trello.com/c/Nouutlp7) |

Gjenstår: cirka 9 dager, uten frigjøring av beslutter.

### Etter vedtak: brev, journalføring og varsel

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🔴 | M1 | `L  ■■■` | 🗄️ Journalføre vedtaksbrev og tilsagnsbrev | [646](https://trello.com/c/4qcIiWBB), [691](https://trello.com/c/is5SjNKk) |
| 🔴 | M1 | `M  ■■` | 🗄️ Sladde kode 6/7 ved journalføring på arbeidsgivermappa | [592](https://trello.com/c/jRixpurm) |
| 🔴 | M1 | `M  ■■` | 📬 Gjøre vedtaksbrev og tilsagnsbrev tilgjengelig (arbeidsgiver på ny rute, se 🛤️, deltaker under «Dine saker» på nav.no). Krever journalføring med deltaker som bruker, i dag støtter klienten bare orgnr. | [647](https://trello.com/c/6l4XA2H5) |
| 🔴 | M1 | `M  ■■` | 🛤️ Arbeidsgiver ser vedtaket på ny rute: innvilget, innvilget med redusert beløp eller avslått, med tilsagnsbrev eller avslagsbrev. Må være klar før varselet. Skisser mangler. | [691](https://trello.com/c/is5SjNKk), [682](https://trello.com/c/cEJ38zjf) |
| 🔴 | M1 | `S  ■` | 🔔 Varsle arbeidsgiver om vedtak | [645](https://trello.com/c/XZvrwKjG) |
| 🔴 | M1 | `S  ■` | 📬 Distribuere vedtaksbrevet til deltaker med dokdist (gjenbruker klienten fra varsel ved innsending) | [599](https://trello.com/c/QA5bXVrf) |
| 🟡 | M1 | `M  ■■` | 📝 Opprette tilsagnsbrev for nye saker med tilsagnsdata fra vedtaket og OeBS-bestillingen. Malen er ferdig og venter på finpuss fra fag. Journalføringen ligger i 🗄️. | Mangler kort |

Gjenstår: cirka 17 dager.

### Økonomi (OeBS)

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | 🏦 OeBS-klient via tiltaksøkonomi (OEBS-2283 løst) | [565](https://trello.com/c/m53Ud4QY) |
| 🟢 | – | – | 🏦 Sette av penger når vedtaket er innvilget | [650](https://trello.com/c/abzeX7gO) |
| 🟢 | – | – | 🔢 Sak-ID som løpenummer | [627](https://trello.com/c/NxvZl7bH) |
| 🟢 | – | – | 🏦 Avklare startdato og sluttdato med OeBS | [625](https://trello.com/c/znIGenFd) |
| 🔴 | M1 | `S  ■` | 🏦 Teste bestilling mot OeBS i T1. Nummerserien er godkjent, T1 er ikke klart for test ennå. | Mangler kort |
| 🔴 | M2 | `XL ■■■■` | 💸 Utbetale (faktura) og motta status | Mangler kort |

Gjenstår: cirka 11 dager.

### Refusjon og sluttrapport

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Sluttrapport vises i saksbehandling | [477](https://trello.com/c/YJI9Yf9G) |
| 🟡 | M2 | `M  ■■` | 📤 Arbeidsgiver sender inn refusjonskrav og sluttrapport (bare dev) | – |
| 🟡 | M2 | `S  ■` | 📤 Saksbehandler henter refusjon og sluttrapport (rydding av `sak_id` gjenstår) | [620](https://trello.com/c/NoJhv4wi) |
| 🔴 | M2 | `M  ■■` | 🛤️ Arbeidsgiver ser innsendt sluttrapport (bare filnavn) og refusjonskrav i ekspansjonskort på ny rute | [683](https://trello.com/c/omYxyPPP), [684](https://trello.com/c/hxzNc8Z4) |
| 🟢 | – | – | 📤 Refusjonskravet kan ikke overstige innvilget beløp | [620](https://trello.com/c/NoJhv4wi) |
| 🟢 | – | – | 📤 Sikkerhetsfunn M4: minne ved opplasting | [664](https://trello.com/c/3qjMQJdw) |
| 🔴 | M2 | `XL ■■■■` | 🧾 Behandle refusjonskrav: godkjenn, avslå via beslutter, oppgavetype. Ett krav per sak, sluttrapport må være mottatt. | Mangler kort |
| 🔴 | M3 | `S  ■` | 📣 Varsle arbeidsgiver når refusjonen er godkjent | Mangler kort |
| 🔴 | M2 | `M  ■■` | 💸 Avslutte saken etter utbetaling og sluttrapport | Mangler kort |

Gjenstår: cirka 20 dager.

### Prodsetting og overgang fra Arena

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Automatisk deploy av saksbehandling til dev | [481](https://trello.com/c/4VdvOjsW) |
| 🔴 | M3, M4 | `L  ■■■` | 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten | Mangler kort |
| 🔴 | M3 | `L  ■■■` | 📡 Salesforce får arbeidsgiveraktivitet for nye saker fra go-live (i dag Kafka fra Arena) | [552](https://trello.com/c/6mVfQgod) |
| 🔴 | M3 | `L  ■■■` | 📡 Modia viser tiltaket for nye saker fra go-live (i dag fra Arena) | [596](https://trello.com/c/bgtBcIzk) |
| 🔴 | M3 | `L  ■■■` | 🧪 Akseptansetest og opplæring med saksbehandlere | Mangler kort |

Gjenstår: cirka 20 dager. Salesforce og Modia er grove anslag til vi vet
hvordan de vil ta imot data.

### Etterlevelse, sikkerhet og drift

| Status | Milepæl | Størrelse | Leveranse | Trello |
|:------:|---------|-----------|-----------|--------|
| 🟢 | – | – | Statistikk til datavarehus (BigQuery) | [480](https://trello.com/c/AH74rdE8) |
| 🟡 | M3 | `M  ■■` | Trusselmodell, verdivurdering og livsløp ferdig. ROS gjenstår, også at arbeidsgiver ser navnet på deltaker med fortrolig adresse. | [582](https://trello.com/c/80W3GhIB) |
| 🟡 | M3 | `M  ■■` | Juridisk logg (ikke-benekt), avklaring pågår | [555](https://trello.com/c/94yFdkSX) |
| 🟡 | M3 | `S  ■` | Begrense hver frontend til sin del av backend-API-et | [603](https://trello.com/c/XED3wgnm) |
| 🔴 | M3 | `S  ■` | Validere klient-claim i backend | [633](https://trello.com/c/jdsNoa2n) |
| 🔴 | M3 | `L  ■■■` | 📜 Oppslagslogg og ArcSight (sikkerhetsfunn M2, M3) | [553](https://trello.com/c/s5v5Occl), [664](https://trello.com/c/3qjMQJdw) |
| 🔴 | M3 | `M  ■■` | Auditlogging av databaseendringer (uavklart om det gjelder oss) | [554](https://trello.com/c/y5GXvQ7x) |
| 🔴 | M3 | `L  ■■■` | 🛡️ Stanse behandling og slette | [584](https://trello.com/c/ft1q5B6v) |
| 🔴 | M3 | `M  ■■` | 🛡️ Slette søknad når deltaker trekker seg | [607](https://trello.com/c/aegaxk5R) |
| 🔴 | M3 | `S  ■` | Ekspertbistand i behandlingskatalogen | [665](https://trello.com/c/LAZXAYcx) |
| 🔴 | M3 | `M  ■■` | Disaster recovery-plan, testet | [591](https://trello.com/c/4xGXShb5) |
| 🔴 | M3 | `S  ■` | Alerts som `PrometheusRule` | [619](https://trello.com/c/VBAPFZPH) |
| 🔴 | M3 | `M  ■■` | UU-tester i bygg for saksbehandling | [593](https://trello.com/c/Flaq2NEE) |

Gjenstår: cirka 29 dager.

## Hva må være med i første release

Klagebehandling er utenfor scope. Det løses trolig felles, utenfor teamet.
Tilbakekreving er også utenfor scope. Økonomi gjør det manuelt.

### 1. Søknad (arbeidsgiver)

| Hva | Status |
|-----|--------|
| Søknadsskjema, innsending, journalføring | 🟢 Ferdig, i prod |
| 🧹 Makssats per startdato (24 900 kr) | 🟢 Ferdig |
| 🧹 Fjerne «Hvilke utgifter skal tilskuddet dekke?» | 🟡 PR #190 er lukket uten merge. Branchen `refusjon-fjern-utgiftsbeskrivelse` finnes. |
| 🪪 **Aa-reg: deltaker må ha arbeidsforhold hos valgt virksomhet.** Arbeidsgiver taster fnr, vi slår opp i Aa-reg og viser navn fra PDL. Uten arbeidsforhold kan søknaden ikke sendes inn. Backend validerer på nytt ved innsending. Arbeidsgiver ser navnet også når deltaker har fortrolig adresse, og det må stå i ROS og etterlevelse. Går i prod før overgangen. | 🟡 `AaregClient` finnes, men brukes ikke. Kort i DOING. Hjemmelen er avklart. Figma-skisser finnes. |
| 🛤️ **Ny rute bak feature toggle.** Søknaden og alt arbeidsgiver ser i ny løsning, lages som nye sider på en egen rute. Dagens sider beholdes uendret for Arena-saker. Det er enklere å lage nye visninger på en rute med feature toggle enn å toggle funksjoner i sider som er i prod. | 🔴 Kort finnes, ikke startet. Kvitteringssiden i ekspansjonskort har eget kort. |
| Lagre adressebeskyttelse og skjerming ved opprettelse | 🟢 Ferdig |
| 📨 **Varsle deltaker når søknaden er sendt inn, med kopi av søknaden.** Krever bekreftet deltaker fra Aa-reg. Går i prod før overgangen, også mens saksbehandlingen skjer i Arena. | 🔴 Del av kortet om varsel til deltaker. Dokdist, se under. |
| 📨 **Arbeidsgiver får beskjed i søknadsskjemaet** om at deltaker får varsel og innsyn i en kopi av søknaden, før søknaden sendes inn. | 🔴 Ikke på Trello. Teksten avklares med fag og jurist. |
| 🧹 Sikkerhetsfunn H1 (andres utkast kan leses/endres) og H2 (sletting stopper ved vedtak) | 🟢 Ferdig |
| 📤 Sikkerhetsfunn M4: minnebruk ved opplasting av sluttrapport og refusjon | 🟢 Ferdig |

### 2. Saksbehandling fram til vedtak

| Hva | Status |
|-----|--------|
| Innlogging, roller, enheter, Tilgangsmaskinen (KJERNE) | 🟢 Ferdig i dev |
| Ruting via Norg, egne ansatte og kode 6/7 | 🟢 Ferdig |
| Listevisning, tildeling, sakslogg, foreløpig vedtak, beslutter, retur | 🟢 Ferdig i dev |
| 📝 Brevmaler for vedtak (seks maler) | 🟡 Ferdige. Venter på finpuss fra fag. |
| ⚖️ Vilkåret «deltaker har arbeidsforhold» settes **automatisk oppfylt** fra Aa-reg, med taggen «Automatisk». Saksbehandler kan overstyre. | 🔴 Ikke startet. Samme mønster som kortet for «Deltaker er enig». |
| ⚖️ To nye vilkår, «Automatisk»-tag på «Deltaker er enig» | 🔴 Kort i Weekly commits |
| Småfiks i GUI: beslutter kan ikke endre vilkår, «Beløp», breadcrumb med arbeidsgiver og deltaker | 🔴 Kort i `Ekspertbistand` |
| Frigjør beslutter / tildel annen beslutter | 🔴 Kort finnes. Kan vente hvis tida blir knapp. |
| 📜 «Opprett event for alt som skjer» | 🟡 PR #201 åpen |

### 3. Etter vedtak: brev, journalføring og varsel

Ingenting skjer i dag når `VedtakFattet` publiseres, bortsett fra status på
saken og søknaden.

| Hva | Status |
|-----|--------|
| 🗄️ Lage og journalføre vedtaksbrev til deltaker (personmappe) og tilsagnsbrev til arbeidsgiver (arbeidsgivermappe) | 🔴 Kort finnes, ikke startet |
| 🗄️ Sladde personopplysninger for kode 6/7 i brev som journalføres på arbeidsgivermappa | 🔴 Kort i backlog, ikke startet |
| 🔔 **Varsle arbeidsgiver om vedtak** (innvilget og avslag) via notifikasjonsplattformen | 🔴 Kort finnes. Tekst i e-post/SMS venter på jurist. Varselet lenker til vedtaket på ny rute. |
| 🛤️ **Arbeidsgiver ser vedtaket på ny rute:** innvilget, innvilget med redusert beløp eller avslått, med brevet. Dagens visning av tilsagnsbrevet beholdes for Arena-saker. | 🔴 Kort finnes, ikke startet. Venter på ny rute og skisser for de tre utfallene. |
| 📬 **Varsle deltaker om vedtak.** Vedtaksbrevet journalføres som utgående til deltaker og vises under «Dine saker» på nav.no. | 🔴 Kort finnes. Distribueres med dokdist, samme klient som ved innsending (avsnitt 1). |
| 📝 Tilsagnsbrev for nye saker | 🟡 Malen er ferdig og venter på finpuss fra fag. Gjenstår å opprette brevet med tilsagnsdata (tilsagnsnummer, beløp, refusjonsfrist) fra vedtaket og OeBS-bestillingen, og å journalføre det. Ikke på Trello. |

Arbeidsgiver får tilsagnsbrevet slik som i dag. Vi journalfører det på
arbeidsgivermappa, og beskjeden på notifikasjonsplattformen lenker til
vedtaket i søknadsløsningen. For nye saker starter flyten på `VedtakFattet`
i stedet for tilsagnet fra Arena, og arbeidsgiver ser vedtaket på den nye
ruta. Dagens visning beholdes for Arena-saker.

Deltaker får kopi av søknaden og vedtaksbrevet med dokdist, slik Team Tiltak
sender vedtak til deltaker. Vi lager en utgående journalpost og distribuerer
den.

- Brev og kopi av søknaden journalføres som **utgående** journalpost med
  deltaker som bruker og mottaker. Da vises dokumentet også under «Dine
  saker» på nav.no.
- Søknaden journalføres i dag som inngående fra arbeidsgiver. Deltaker ser
  da at dokumentet finnes, men får ikke åpne det, fordi noen andre har
  sendt det inn. Kopien til deltaker må derfor være en egen utgående
  journalpost.
- `DokArkivClient` har allerede `forsoekFerdigstill`, journalførende
  enhet 9999, fagsaksystem og brevkode. Den mangler støtte for FNR som
  bruker.
- Den ferdigstilte journalposten sendes til
  [dokdistfordeling](https://github.com/navikt/dokdistfordeling) med
  `POST /rest/v1/distribuerjournalpost`: `journalpostId`,
  `bestillendeFagsystem`, `dokumentProdApp`, `distribusjonstype` og
  `distribusjonstidspunkt = KJERNETID`. Svaret er en `bestillingsId`.
- `distribusjonstype` er `VEDTAK` for vedtaksbrevet. Kopien av søknaden
  er trolig `VIKTIG` eller `ANNET`.
- Dokdist slår opp KRR og velger kanal: digital postkasse, Nav med SMS- eller
  e-postvarsel, eller papirbrev. Adressen hentes fra PDL, og Dokumentløsninger
  håndterer returpost.
- Vi skriver vår egen `DokdistClient` etter mønsteret til `DokArkivClient`,
  med Azure-token. En handler i `event/handlers/` distribuerer etter at
  journalposten er ferdigstilt.
- Et nytt kall på samme journalpost gir samme `bestillingsId`, med 200 eller
  409. Handleren behandler 409 som suksess, så retry gir ikke dobbel
  utsending.
- Team Dokumentløsninger gir tilgang og bekrefter verdien for
  `bestillendeFagsystem`. Spør i `#team_dokumentløsninger`.

### 4. Økonomi (OeBS via tiltaksøkonomi)

| Hva | Status |
|-----|--------|
| 🏦 Klient, outbox, producer, status-consumer, topic-manifester | 🟢 Ferdig |
| 🔢 Egen nummerserie og kildesystem hos OeBS (OEBS-2283) | 🟢 Godkjent |
| 🏦 Teste bestilling mot OeBS i T1 | 🔴 **Venter på at T1 blir klart for test** |
| 🔢 Sak-ID som løpenummer som matcher OeBS | 🟢 Ferdig |
| 🏦 Sette av penger (bestilling) når vedtaket er innvilget | 🟢 Ferdig |
| 💸 Utbetale (faktura) når refusjonskravet er godkjent | 🔴 Ikke på Trello |
| Tiltakskode og konto registrert hos Team VALP | 🟡 Avtalt, men ikke bekreftet levert |

### 5. Refusjon og sluttrapport

| Hva | Status |
|-----|--------|
| 📤 Arbeidsgiver sender inn refusjonskrav og sluttrapport | 🟡 Bygget, bare i dev (feature-flagg `EKSPERTBISTAND_SAKSBEHANDLING_BETA`) |
| 🛤️ Arbeidsgiver ser innsendt sluttrapport og refusjonskrav i ekspansjonskort på ny rute. Sluttrapporten vises bare med filnavn, fordi den kan inneholde sensitive personopplysninger. | 🔴 Kort finnes, ikke startet. Åpent på kortet: skal kontonummeret vises? |
| Saksbehandler ser refusjonskrav, sluttrapport og vedlegg | 🟢 Ferdig i dev |
| 📤 Rydde `soknad_id` → `sak_id` på refusjon og vedlegg | 🟡 Sjekkliste på kortet, ikke ferdig |
| 📤 Beløp i refusjonskravet kan ikke overstige innvilget beløp | 🟢 Ferdig |
| 🧾 **Behandle refusjonskrav:** godkjenn → send til utbetaling (ingen beslutter). Avslå → via beslutter. Egen oppgavetype i lista. Én sak har ett refusjonskrav, som kan ha flere vedlegg. Sluttrapporten må være mottatt før kravet kan godkjennes (dobbeltsjekkes). | 🔴 Figma finnes. Ikke på Trello som eget kort, ingen events eller statuser for det. `Oppgavetype` har bare søknad. |
| 📣 Varsle arbeidsgiver når refusjonen er godkjent (ikke deltaker) | 🔴 Ikke på Trello |
| 💸 Avslutte saken når utbetalingen er gjort (`AVSLUTTET`) | 🔴 Ikke på Trello |

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
| Validere klient-claim i backend (hindre kryssing mellom apper) | 🔴 Kort i Weekly commits |

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
| Uke 45 (6.11.) | **M0: rydd bordet** | 🧹 📝 📜 | Utgiftsbeskrivelsen er fjernet. Brevmalene er finpusset med fag. PR #201 er merget. Alle eksterne avklaringer under har en eier og en dato. |
| Uke 50 (11.12.) | **P1: tidlig i prod** | 🪪 📨 | I prod, i dagens Arena-flyt: Aa-reg-sjekk i søknaden, varsel til deltaker ved innsending med kopi av søknaden, og tekst om kopien til arbeidsgiver i søknadsskjemaet. Krever tilgang til Aa-reg og dokdist. |
| Uke 5 (5.2.) | **M1: vedtaket går ut** | 🛤️ ⚖️ 🗄️ 📬 🔔 📝 🏦 | I dev: arbeidsgiver ser vedtaket på ny rute. Vilkåret om arbeidsforhold settes automatisk. Vedtaket gir journalført vedtaksbrev og tilsagnsbrev. Deltaker får vedtaksbrevet via dokdist, og arbeidsgiver får varsel. OeBS-bestillingen er testet i T1. |
| Uke 6 (12.2.) | **M2: refusjonen går ut** | 🧾 💸 📤 🛤️ | I dev: arbeidsgiver sender inn og ser refusjonskrav og sluttrapport på ny rute. Saksbehandler godkjenner, eller avslår via beslutter. Utbetaling via OeBS, og saken avsluttes. |
| Uke 9 (5.3.) | **M3: prodklar** | 📡 📣 📜 🛡️ 🧪 🚦 og etterlevelse | Salesforce og Modia har testet mottaket. Arbeidsgiver får varsel når refusjonen er godkjent. Oppslagslogg og ArcSight, personvern, alerts, DR testet, ROS og behandlingskatalog. Akseptansetest med saksbehandlere. Saksbehandlingsappen er deployet til prod bak tilgangsgruppene. Generalprøve på overgangen i dev. |
| Uke 11 (19.3.) | **M4: go-live** | 🚦 | Arena stenger for nye ekspertbistand-saker. Søknader sendt inn etter go-live behandles hos oss. Arena behandler ferdig sakene som ligger der. Salesforce og Modia får data om nye saker fra samme dag. |
| Uke 12–13 | Buffer | – | Påske. Ingen planlagte prodsettinger. |

Vinterferien (uke 8 eller 9) ligger i M3. Juleferien (uke 52–53) er ikke
regnet med.

### Siste dato for eksterne svar

| Avhengighet | Trengs innen | Hvis ikke |
|-------------|--------------|-----------|
| 🪪 Tilgang til Aa-reg og behandlingskatalog (hjemmelen er avklart) | Uke 47 | Aa-reg-sjekken og varsel til deltaker kan ikke prodsettes, verken i P1 eller ved go-live. Vilkåret må vurderes manuelt. |
| 📨 Tilgang til dokdist og verdi for `bestillendeFagsystem` (Team Dokumentløsninger) | Uke 47 | Varsel til deltaker blir ikke klart til P1. |
| 🔔 Tekst i varsel til arbeidsgiver (jurist) | Uke 49 | Vi bruker nøytral tekst uten «ekspertbistand». |
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

1. 🧹 Fjern utgiftsbeskrivelsen (gjenåpne #190 eller ny PR).
2. 🛤️ Ny rute bak feature toggle, med kvitteringssiden i ekspansjonskort.
   Dagens sider beholdes uendret.
3. 🪪 Aa-reg i søknaden på ny rute: oppslag, navn fra PDL, feilmeldinger,
   validering ved innsending. Søknaden får en bekreftet deltaker. Prodsettes
   i Arena-flyten (P1).
4. ⚖️ Automatisk vilkår for arbeidsforhold og «Deltaker er enig», og de to nye
   vilkårene.
5. 🛤️ Vedtaket for arbeidsgiver på ny rute: innvilget, redusert beløp og
   avslått. Må være klar før spor B varsler arbeidsgiver (🔔).
6. 📤 Rydde `sak_id`, visning av sluttrapport og refusjonskrav på ny rute, skru på
   refusjon og sluttrapport for arbeidsgiver i prod.
7. 📡 Data om nye saker til Salesforce og Modia fra go-live.
8. 🛡️ Personvern: stanse behandling, sletting når deltaker trekker seg.

### Spor B: brev, journalføring og varsel

Eier `dokument/`, `dokarkiv/`, ny `dokdist/`-klient, `notifikasjon/` og
varsel-handlerne i `event/handlers/`.

1. 📨 Varsel til deltaker ved innsending: dokdist-klient, kopi av
   søknaden journalført som utgående til deltaker og tekst til arbeidsgiver i
   søknadsskjemaet om at deltaker får kopi. Handleren registreres i
   Arena-flyten for P1 og i ny flyt for go-live.
2. 📝 Finpuss av brevmalene med fag. Opprett tilsagnsbrev for nye saker.
3. 🗄️ Journalfør vedtaksbrev og tilsagnsbrev på `VedtakFattet`, med sladding for
   kode 6/7 på arbeidsgivermappa.
4. 📬 Distribuer vedtaksbrevet til deltaker med dokdist, som i punkt 1.
5. 🔔 Varsle arbeidsgiver om vedtak.
6. 📣 Senere: varsle arbeidsgiver når refusjonen er godkjent.
7. 📜 Oppslagslogg og ArcSight (sikkerhetsfunn M2 og M3), «event for alt som skjer».

### Spor C: økonomi og refusjonsbehandling

Eier `oebs/`, saksbehandlersiden av `refusjon/`, `Saksstatus`/`Oppgavetype`
og refusjonsfanen i `frontend/saksbehandling`.

1. 🧾 Refusjonsbehandling: statuser, oppgavetype, events, godkjenn og avslå via
   beslutter, GUI etter Figma.
2. 💸 Utbetaling (faktura) og statuser tilbake fra OeBS. Avslutt saken.
3. 🏦 Test bestilling og utbetaling mot OeBS i T1 når det er klart.
4. 🚦 Prod-manifest for saksbehandling, overgang fra Arena-flyten.

### Felles arbeid

Drift og etterlevelse (alerts, DR, UU-tester, ROS, småfiks i GUI) tar den som
har ledig kapasitet mellom milepælene. Ikke start slike oppgaver midt i en
leveranse i sporet.

### Dette må skje i rekkefølge

```
Aa-reg-tilgang ──> Aa-reg i søknad ──> automatisk vilkår
                         └──────────> varsel til deltaker ved innsending i prod (P1)

VedtakFattet (ferdig) ──> journalfør brev ──> distribuer til deltaker
                    ├──> varsle arbeidsgiver
                    └──> OeBS-bestilling ──> tilsagnsnummer i tilsagnsbrev
                                     └──> refusjon godkjent ──> utbetaling ──> sak avsluttet

OeBS T1 klart ──> bestilling og utbetaling testet ──> økonomi i prod

Salesforce/Modia-mottak avklart ──> data om nye saker ──┐
M2/M3 + akseptansetest ─────────────────────────────────┴──> saksbehandling i prod ──> go-live
```

Dette kan skje samtidig:

- Spor A sin Aa-reg-jobb og spor B sin brev- og varseljobb. Varselet til
  deltaker bygges mot fnr i søknaden. Aa-reg trengs først når det skal i prod.
- Refusjonsbehandling (C) og innsending av refusjon i prod (A). Kontrakten
  er tabellene og API-et som finnes i dag.

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

1. 🧾 Behandle refusjonskrav: godkjenn, avslå via beslutter, oppgavetype
2. 💸 Utbetale via OeBS (faktura) og motta status
3. 📣 Varsle arbeidsgiver når refusjonen er godkjent
4. 💸 Avslutte saken etter utbetaling og sluttrapport
5. 📝 Opprette og journalføre tilsagnsbrev for nye saker (uten Arena)
6. ⚖️ Automatisk vilkår «deltaker har arbeidsforhold» fra Aa-reg
7. 🚦 Prod-manifest for saksbehandling og overgang fra Arena-flyten i prod
8. 🧪 Akseptansetest og opplæring med saksbehandlere
9. 🏦 Teste bestilling og utbetaling mot OeBS i T1
10. 📨 Del kortet om varsel til deltaker i to: ved innsending (P1) og ved vedtak
11. 📨 Tekst i søknadsskjemaet om at deltaker får varsel og kopi av søknaden

## Avklart

- **Go-live:** All saksbehandling av søknader som sendes inn etter go-live,
  skjer i ny løsning. Saker opprettet i Arena behandles ferdig der. Ingen
  synk mellom oss og Arena.
- **Varsel til deltaker:** Aa-reg-sjekken og 📨 varsel om mottatt søknad går
  i prod så snart de er klare, uten å vente på go-live. 📬 Varsel om vedtak
  kommer først når den nye saksbehandlingsløsningen er live, fordi vedtak i
  Arena-saker ikke går via oss. Kanalen er dokdist, som Team Tiltak bruker
  for vedtak til deltaker. Arbeidsgiver skal få beskjed i søknadsskjemaet om
  at deltaker får innsyn i og kopi av søknaden.
- **Tilsagnsbrev til arbeidsgiver:** Samme flyt som i dag, uten dokdist. Bare
  triggeren endres fra tilsagnet i Arena til vedtaket i ny løsning.
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
- **Aa-reg (31.10.):** Hjemmelen er avklart.
- **OeBS (31.10.):** Nummerserien og kildesystemet er godkjent (OEBS-2283),
  og klienten vår er klar. Vi venter på at T1 blir klart for test.
- **Entra-grupper (31.10.):** På plass i prod (STS-56018).

## Åpne spørsmål

1. Hvordan vil Salesforce og Modia ta imot data om nye saker? Kan vi
   publisere samme kontrakt som Arena-topicet for arbeidsgiveraktivitet, og
   hvem eier visningen i Modia? Trengs innen uke 49.
2. Bekreft at sluttrapporten må være mottatt før refusjonskravet godkjennes.
3. Hva skjer med saken og OeBS-bestillingen når refusjonsfristen går ut uten
   krav? Se [funn 4](README.md#funn).
4. Får arbeidsgiver brev og varsel når refusjonskravet avslås, og skal
   bestillingen da annulleres? Se [funn 5](README.md#funn).

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
