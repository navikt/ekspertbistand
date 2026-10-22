# Ruting av saker: egne ansatte og fordeling per kommune

Trello:

- https://trello.com/c/BAJeTZsi/608-hvor-skal-saker-med-deltaker-som-jobber-i-nav-rutes
  (kort-ID `6ab3c2ac2b43947454aed933`)
- https://trello.com/c/SNQ9LgWb/610-ruting-av-sak-til-enhet-via-norg-m%C3%A5-ta-hensyn-til-skjerming-nav-ansatte
  (kort-ID `6ab3cacaf7d82050a52a9782`)

Henger sammen med: https://trello.com/c/6L5KVmvN (661, lagre adressebeskyttelse
og skjerming på `SakOpprettet`). Del 1 venter ikke på 661, se 1.2.

## Mål

1. En sak der deltakeren er skjermet (egen ansatt), skal aldri forsvinne uten
   at noen ser den. Saksbehandlere uten tilgang skal se at saken finnes og
   forstå hvorfor de ikke kan åpne den.
2. Saksbehandlere på et arbeidslivssenter skal kunne avgrense listen til
   kommunene de jobber med. Norg fordeler bare til arbeidslivssenter i fylket.

Rutingen til enhet endres ikke i første omgang. Saker om egne ansatte går til
samme arbeidslivssenter som andre saker, og behandles av saksbehandlere der
som er med i `0000-GA-Egne_ansatte`.

## Bakgrunn

### Ruting i dag

`JournalfoerInnsendtSoknad` kaller `BehandlendeEnhetUtleder.utled`:

- Strengt fortrolig adresse (kode 6): deltakerens geografiske tilknytning fra
  PDL, oppslag i Norg med diskresjonskode `SPSF`. Fallback er Nav Vikafossen
  (`2103`).
- Ellers, også fortrolig adresse (kode 7) og ukjent person: kommunenummer fra
  virksomhetens forretningsadresse i Ereg, oppslag i Norg med `ANY`.

Norg-regelen for tema `TIL` og behandlingstema `ab0423` gir Nav
arbeidslivssenter i fylket, tolv enheter. Vestfold og Telemark går til Nav
arbeid og helse Vestfold og Telemark (`0891`). Reglene ligger dels per
fylke og dels per kommune. Ingen regel har egen enhet
for egne ansatte, så vi sender ikke `skjermet` til Norg.

Enheten havner i `sak.behandlende_enhet` via `InnsendtSoknadJournalfoert` og
`OpprettSak`.

### Tilgang i dag

- Listen (`GET /saker`) viser alle saker på innloggets enheter, med
  deltakerens navn. Den kaller ikke Tilgangsmaskinen.
- Detaljoppslag og tildeling sjekker enhet og så Tilgangsmaskinen med
  `KJERNE` (`tilgangsmaskin_uten_geografisk_tilknytning.md`).
  Kjernereglene avviser skjermede personer med `AVVIST_SKJERMING`, med mindre
  saksbehandleren er med i `0000-GA-Egne_ansatte`.
- Ved 403 viser `SakPage` «Du har ikke tilgang til denne saken.» og
  `begrunnelse` fra Tilgangsmaskinen. Teksten sier ikke hva saksbehandleren
  skal gjøre.

Resultatet er at en sak om en Nav-ansatt ligger i listen på
arbeidslivssenteret med navnet synlig for alle, men kan bare åpnes av dem med
egne ansatte-tilgang. Har ingen på enheten den tilgangen, blir saken liggende.

### Hvor skjerming kommer fra

Skjerming (egne ansatte) registreres i NOM og slås opp i
`skjermede-personer-pip` (team nom). Det er det samme registeret
Tilgangsmaskinen bruker. Kort 661 slår opp skjerming og lagrer svaret på
`SakOpprettet`, på samme måte som adressebeskyttelse. Detaljer om API og
Nais-oppsett står på kortet.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 610, Agnes Cecelie via Hanna, 2026-09-24 | I Arena har hvert fylke en egen benk for egne ansatte. Søknaden kommer til ordinær benk (0691), saksbehandler ber om midlertidig tilgang og flytter saken til egne ansatte-benken (0683). |
| Kort 610, Ken, 2026-09-25 | `0000-GA-Egne_ansatte` finnes i Entra og kan kanskje brukes i stedet for en egen gruppe. |
| Kort 608, Ken, 2026-10-21 | Gjeldende gruppe for egne ansatte er `0000-GA-Egne_ansatte`. Norg-dokumentasjonen sier at oppgaver for egne ansatte rutes til spesielle køer. |
| Slack, Øystein, 2026-10-21 | Tilgangskontroll og ruting er to ting. Egne ansatte behandles typisk på de vanlige enhetene av ansatte med ekstra tilgang, ikke på en egen enhet. Egen enhet er mulig, men uvanlig. Kjerneregler sjekker ikke geografi. |
| Slack, Ida, 2026-10-21 | Egne ansatte-tilgang gis til erfarne ansatte på enheten og beholdes til de slutter. Den gis normalt ikke midlertidig. Enkelttilgang (oppslag utenfor egen geografi mot begrunnelse) finnes i pilot. |
| Ken, 2026-10-22 | Ruting: adressebeskyttet deltaker rutes på deltakerens geografiske tilknytning, ellers på virksomhetens kommune. Norg gir arbeidslivssenter på fylkesnivå. |
| Ken, 2026-10-22 | Finere fordeling enn fylke må vi lage selv, for eksempel ved at saksbehandler velger kommuner. |
| Ken, 2026-10-22 | Egne ansatte skal ikke forsvinne usynlig. Minstekrav: fange opp at Tilgangsmaskinen avviser på grunn av skjerming, og gi en forklarende feilmelding. Søknader der Nav er arbeidsgiver må håndteres spesielt. |
| Ken, 2026-10-22 | `skjermede-personer-pip` kjører i GCP (namespace `nom`). |
| Teamet, 2026-10-22 | Oppslaget i `skjermede-personer-pip` lagres på `SakOpprettet` på samme måte som adressebeskyttelse. Det gjøres under kort 661. |
| Teamet, 2026-10-22 | Søker er arbeidsgiver, og vi kan kreve digital innsending. Papir og skanning er ikke med. |
| Ken, 2026-10-22 | Listen bruker både skjermingsflagget fra opprettelsen og bulk-sjekk mot Tilgangsmaskinen. Uten tilgang skjules både deltaker og virksomhet. |
| Ken, 2026-10-22 | Den som åpner en kode 6-sak uten tilgang, får en feilmelding med feil-ID til brukerstøtte. Oppslaget logges i team logs uten opplysninger om personen med kode 6. |
| Ken, 2026-10-22 | Kommunefilter: `Select` for enhet, bare ved flere enheter, og `Combobox` for kommuner der tomt betyr alle. Bytte av enhet tømmer kommunene. Filteret lagres per bruker i backend. Kommunen vises i listen og kan brukes som hurtigfilter. Skisse i Figma. |
| Ken, 2026-10-22 | Kommunevalgene bygges fra sakene i listen, ikke fra arbeidsfordelingen i Norg. Enklere, men en kommune kan ikke velges før den har en sak. |
| Ken, 2026-10-22 | Fagansvarlig må sørge for at hvert arbeidslivssenter har saksbehandlere og beslutter med `0000-GA-Egne_ansatte`. Det løses ikke i koden, men skrives i PR-en. |
| Ken, 2026-10-22 | Habilitet: Vi kan ikke flytte saker. Vi lener oss på internkontroll og skjønn. Saksbehandleren har taushetsplikt, vurderer selv om hen er inhabil og lar en annen ta saken. |
| Ken, 2026-10-22 | Kommunefilteret er en brukerinnstilling for appen og skrives direkte til databasen, ikke via event. Det er et bevisst unntak. `localStorage` ble vurdert, men følger ikke saksbehandleren mellom maskiner. |
| Ken, 2026-10-22 | Kode 7 i listen: raden vises som «Ingen tilgang» uten navn. Åpner noen saken, logges avvisningen med feil-ID og følges opp derfra. |
| Ken, 2026-10-22 | Vestfold og Telemark rutes i Norg til Nav arbeid og helse Vestfold og Telemark (`0891`). Enheten har regel for alle 23 kommunene. |
| Ken, 2026-10-22 | Svalbard og Jan Mayen har ingen regel i Norg. Fallback til Oslo (`0391`) i `BehandlendeEnhetService` står som den er. |
| Ken, 2026-10-22 | Del 1 venter ikke på 661. `skjermet` kommer fra en hook som returnerer `false` til 661 er på plass. Kode 6 kjennes igjen på avvisningskoden fra Tilgangsmaskinen og på Vikafossen, ikke på et lagret flagg. |

Kortbeskrivelsen på 608 og Norg-dokumentasjonen peker mot egen kø for egne
ansatte. Slack-svarene fra 2026-10-21 er nyere og sier vanlig enhet med ekstra
tilgang. Spec-en følger Slack-svarene.

## Avgrensning

- Arena. Saker som fortsatt opprettes i Arena med samme enhet, får samme
  egne ansatte-problem som før (FAGSYSTEM-329532). Vi endrer ikke
  enhetsnummeret vi sender til Arena.
- Enkelttilgang fra Tilgangsmaskinen. Noteres som mulig utvidelse.
- Habilitet når deltakeren jobber på samme arbeidslivssenter som behandler
  saken. Se beslutningene.
- Oppslag i `skjermede-personer-pip` og lagring på `SakOpprettet`. Det gjøres
  under kort 661.

## Tilnærming

Arbeidet deles i to deler som kan leveres hver for seg. Del 1 må være på
plass før saksbehandling for nye saker går i prod.

### Del 1: Egne ansatte forsvinner ikke

🔴 Rød sone: tilgangskontroll og personvern i listen.

#### 1.1 Rolle for egne ansatte

`Role` får `EGNE_ANSATTE("0000-GA-Egne_ansatte")`, slik `FORTROLIG_ADRESSE`
og `STRENGT_FORTROLIG_ADRESSE` er der i dag. Gruppene hentes fra entra-proxy,
så det trengs ingen endring i Nais-manifestet. `GET /api/saksbehandling/v1/meg`
returnerer rollen sammen med de andre.

Rollen brukes bare til visning. Tilgangsmaskinen er fortsatt fasit.

#### 1.2 Flagg ved opprettelse og bulk-sjekk i listen

Listen bruker to kilder:

- **Flagget fra opprettelsen.** Kort 661 lagrer skjerming på saken.
  `SakListeElement` får `skjermet: Boolean` fra flagget. Det merker saken uten
  kall til andre tjenester. Til 661 er på plass, kommer verdien fra en hook
  `skjermetVedOpprettelse()` som returnerer `false` og har en TODO som peker
  på 661. Det er samme mønster som
  `EventData.SakOpprettet.adressebeskyttetVedOpprettelse()` i
  `SaksbehandlingsstatistikkProjection.kt`. Til da mangler tagen «Egen
  ansatt» for dem som har tilgang. Ingen saker skjules.
- **Bulk-sjekk ved visning.** `GET /saker` kaller
  `tilgangsmaskinClient.evaluerBulk` med `Regelsett.KJERNE` for distinkte
  `ansattFnr` i resultatet, i bolker på maks 1000. Den avgjør hva den
  innloggede får se.

Er kildene uenige, vinner Tilgangsmaskinen. Den fanger personer som er blitt
skjermet etter innsending, og personer som ikke lenger er det.

For hver sak der Tilgangsmaskinen avviser:

- `soknad.ansattNavn` blir `null`.
- `soknad.virksomhet` blir `null`. Det skjuler både navn og
  virksomhetsnummer.
- `kommunenummer` og `kommunenavn` (del 2) blir `null`.
- Nytt felt `deltakerTilgang` på `SakListeElement`:
  `{ "avvist": true, "kode": "AVVIST_SKJERMING" }` eller `null` når innvilget.
  Koden er `AVVIST_SKJERMING` eller `IKKE_TILGANG`. Alle andre koder fra
  Tilgangsmaskinen maskeres til `IKKE_TILGANG`, slik at listesvaret aldri sier
  at personen har kode 6 eller 7.
- `skjermet` blir `false`, så flagget ikke lekker for kode 6 eller 7.
- `kanTildeleMeg` og `kanTildeleMegSomBeslutter` blir `false`.

Feiler kallet til Tilgangsmaskinen, er listen fail-closed: alle rader får
`null` på deltaker, virksomhet, kommune og `deltakerTilgang`, `false` på
`skjermet` og tildeling, og responsen får
`tilgangssjekkFeilet: true`. Listen er fortsatt brukbar for å se hvilke saker
som finnes. 404 for en ident i bulk-svaret behandles som avvist.

Listen sporingslogges fortsatt ikke (`listevisning.md`).

Valget og alternativet: Flagget alene blir utdatert når skjermingen endres.
Bulk-sjekken alene gir ingen merking for dem som har tilgang. Med begge får
saksbehandlere med tilgang se at saken gjelder en egen ansatt, mens
Tilgangsmaskinen avgjør hva som vises. Prisen er ett OBO-bytte og ett kall til
Tilgangsmaskinen per listevisning.

#### 1.3 Visning i listen

Kolonnen «Deltaker»:

- Med tilgang: navnet, og `Tag` «Egen ansatt» når `skjermet` er `true`.
- Uten tilgang (`deltakerTilgang.avvist`): `Tag` «Egen ansatt» når koden er
  `AVVIST_SKJERMING`, ellers «Ingen tilgang». Ikke navn.

Kolonnen «Arbeidsgiver» er tom uten tilgang. Raden kan åpnes, og detaljsiden
viser forklaringen i 1.4.

Ved `tilgangssjekkFeilet` viser siden en `Alert` (warning) over tabellen:
«Vi kunne ikke sjekke tilgangen din. Deltaker og arbeidsgiver vises ikke. Prøv
igjen om litt.»

Vi viser ikke «Fortrolig adresse» i listen. Det ville fortalt saksbehandlere
uten tilgang at personen har kode 7. «Egen ansatt» viser vi, fordi saken ellers
ville se tom ut uten forklaring. Arbeidsgiveren skjules, slik at raden ikke
sier hvor den ansatte jobber.

#### 1.4 Forklarende feilmelding

`feilmelding` i `SakPage.tsx` og feilhåndteringen i `useTildeling.ts` og
`useBeslutning.ts` får egen tekst for `kode === "AVVIST_SKJERMING"`:

> Saken gjelder en Nav-ansatt eller en annen skjermet person. Du må ha tilgang
> til egne ansatte (`0000-GA-Egne_ansatte`) for å åpne den. Snakk med lederen
> din hvis du skal behandle saken.

Andre koder fra Tilgangsmaskinen viser `begrunnelse` som i dag, utenom kode 6
(1.5).

`TilgangAvvistResponse` får `feilId`: correlation-ID-en til kallet, som
`CallId` allerede lager og legger i MDC som `x_correlation_id`. Frontend viser
den under feilmeldingen ved alle 403:

> Feil-ID: 3f2a…. Oppgi den hvis du kontakter brukerstøtte.

#### 1.5 Kode 6 uten tilgang

🔴 Rød sone: strengt fortrolig adresse.

Vi bruker ikke et lagret flagg for kode 6. Signalet kommer fra der avvisningen
skjer:

- `sjekkTilgangsmaskin`: avvisningskoden er
  `AVVIST_STRENGT_FORTROLIG_ADRESSE` eller `AVVIST_STRENGT_FORTROLIG_UTLAND`.
  Dette fanger også personer som har fått kode 6 etter innsending.
- `sjekkTilgangTilEnhet`: saken ligger på Nav Vikafossen
  (`BehandlendeEnhetService.NAV_VIKAFOSSEN`, `2103`). Svaret
  `IKKE_TILGANG_ENHET` er generisk og avslører ikke kode 6, men vi trenger
  linjen i team logs.

Ved slik avvisning:

- Svaret er 403 med `kode = "IKKE_TILGANG"`,
  `begrunnelse = "Du har ikke tilgang til saken."` og `feilId`. Vi sender
  ikke koden eller begrunnelsen fra Tilgangsmaskinen. De ville fortalt at
  personen har kode 6.
- Frontend viser «Du har ikke tilgang til denne saken.» og feil-ID-en.
- `teamLog.warn` logger `navIdent`, `sakId`, rute, avvisningskoden og
  `feilId`. Det er nok til at teamet kan følge opp ut fra feil-ID-en.
- Vi logger ikke fnr, navn, adresse, geografisk tilknytning eller annet om
  personen med kode 6, verken i team logs eller i vanlig logg.

Sjekken ligger i `Tilgangsjekker.kt`, så den gjelder alle ruter som bruker
`sjekkTilgangTilEnhet` og `sjekkTilgangsmaskin`.

#### 1.6 Tester og mock

- `SaksbehandlingSakApiTest`: mock for `POST /api/v1/bulk/obo`.
  - Avvist ident gir `ansattNavn = null`, `virksomhet = null`, ingen kommune,
    `deltakerTilgang.kode` og `kanTildeleMeg = false`.
  - Innvilget ident gir navn, virksomhet og `deltakerTilgang = null`.
  - `skjermet` er `false` til 661 kobler hooken. Testen for `skjermet = true`
    hører til 661.
  - Feil fra Tilgangsmaskinen gir 200 med `tilgangssjekkFeilet = true`, og
    ingen navn eller virksomhet.
  - Ingen saker gir ikke kall til Tilgangsmaskinen.
  - Sak på `2103` avvist på enhet, og sak avvist med
    `AVVIST_STRENGT_FORTROLIG_ADRESSE`, gir 403 med `IKKE_TILGANG` og
    `feilId` lik `X-Correlation-Id` i svaret.
  - `AVVIST_SKJERMING` og `AVVIST_FORTROLIG_ADRESSE` sendes videre uendret.
- `RolesTest` eller tilsvarende: `0000-GA-Egne_ansatte` gir `EGNE_ANSATTE`.
- MSW: én skjermet sak med tilgang, én med `AVVIST_SKJERMING` i listen og 403
  med samme kode på detaljoppslaget, og én kode 6-sak med 403 og `feilId`.

### Del 2: Fordeling per kommune

Dette er arbeidsfordeling, ikke tilgangskontroll. Alle på enheten har fortsatt
tilgang til alle sakene, slik som i dag.

Skisse:
https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=975-39388

- `BehandlendeEnhetUtleder.utled` returnerer
  `Ruting(enhetNr, virksomhetKommunenummer)`. Kommunenummeret er det samme som
  brukes mot Norg. For kode 6 er det `null`: der ruter vi på deltakerens
  bosted, og vi lagrer ikke det.
- `InnsendtSoknadJournalfoert` får `virksomhetKommunenummer: String? = null`.
- Migrering `V27__sak_kommunenummer.sql`: `sak.kommunenummer` (nullable).
  `OpprettSak` lagrer verdien.
- `SakListeElement` får `kommunenummer` og `kommunenavn`.

#### 2.1 Kommunevalg fra listen

Valgene i kommunefeltet er kommunene på sakene i listen. Vi henter ikke
arbeidsfordelingen til enheten fra Norg.

- Frontend bygger valgene fra alle saker på den valgte enheten, før
  kommunefilteret brukes. Valgene er distinkte og sortert på navn.
- Lagrede kommuner er alltid med blant valgene, også når ingen saker har dem
  nå. Settet saksbehandleren har valgt, blir dermed ikke borte.
- `kommunenavn` slås opp i kodeverket `geografi` i Norg
  (`GET /norg2/api/v1/kodeverk/geografi`, `{ "navn": "<kode>", "term": "<navn>" }`).
  Svaret caches i backend i ett døgn. Feiler oppslaget, er `kommunenavn` null,
  og frontend viser kommunenummeret.

Valget og alternativet: Vi slipper å koble enhet til kommuner, og filteret
viser bare kommuner der det finnes saker. Prisen er at saksbehandleren ikke kan
velge en kommune før den første saken derfra kommer inn. Kommer en sak fra en
kommune som ikke er i filteret, er den skjult til kommunen legges til. Teksten
«N saker skjult av kommunefilteret» i 2.2 gjør det synlig. Vi forkastet å
hente kommuner per enhet fra arbeidsfordelingen i Norg. Det krever tolking av
både fylkes- og kommuneregler og en cache som kan bli utdatert.

#### 2.2 Filter over listen

Over fanene på `OversiktPage`, som i skissen:

- `Select` «Enhet» med enhetene til den innloggede. Den vises bare når
  saksbehandleren har mer enn én enhet. Med én enhet er den valgt.
- `UNSAFE_Combobox` «Kommune» med `isMultiSelect` og kontrollert
  `selectedOptions`. Valgene er fra 2.1, med kommunenummer som `value`. Tomt
  felt betyr alle kommuner.
- Bytter saksbehandleren enhet, tømmes kommunefeltet.
- Er det ingen valg, vises ikke kommunefeltet. Det gjelder for eksempel
  Vikafossen, der kode 6-saker ikke har kommune.
- Skjuler filteret saker, står det «N saker skjult av kommunefilteret» under
  feltet.

Filteret gjelder alle fanene og tallene i fanetitlene. Frontend filtrerer
`saker` på `behandlendeEnhet` og `kommunenummer` før fanefiltrene, slik fanene
gjør i dag. `GET /saker` er uendret, utenom de nye feltene.

Saker uten kommune vises bare når kommunefeltet er tomt. Rader uten tilgang
(`deltakerTilgang.avvist`) og alle rader ved `tilgangssjekkFeilet` mangler
kommune, men vises alltid, uansett kommunefelt. Slik forsvinner de ikke.

```tsx
{enheter.length > 1 && (
  <Select
    label="Enhet"
    value={filter.enhet}
    onChange={(e) => lagreFilter({ enhet: e.target.value, kommuner: [] })}
  >
    {enheter.map((enhet) => (
      <option key={enhet.enhetsnummer} value={enhet.enhetsnummer}>
        {enhet.navn}
      </option>
    ))}
  </Select>
)}
{kommuneValg.length > 0 && (
  <UNSAFE_Combobox
    label="Kommune"
    isMultiSelect
    options={kommuneValg}
    selectedOptions={kommuneValg.filter((k) => filter.kommuner.includes(k.value))}
    onToggleSelected={(nummer, valgt) =>
      lagreFilter({
        ...filter,
        kommuner: valgt
          ? [...filter.kommuner, nummer]
          : filter.kommuner.filter((k) => k !== nummer),
      })
    }
  />
)}
```

#### 2.3 Kommune i listen

- Ny kolonne «Kommune». Cellen er tom når saken mangler kommune.
- Kommunenavnet er en `Button variant="tertiary" size="xsmall"` med
  `aria-label="Filtrer på Moss"`. Et klikk legger kommunen til i kommunefeltet
  hvis den ikke er der fra før.

#### 2.4 Lagring av filteret

Filteret lagres per `navIdent` i backend, slik at det følger saksbehandleren
mellom maskiner.

- Tabell `saksbehandler_kommunefilter` i samme migrering: én rad per
  `nav_ident` med valgt enhet og listen med kommunenummer.
- `GET /api/saksbehandling/v1/meg/kommunefilter` returnerer det lagrede
  filteret, med kommunenavn fra `geografi`. Enhetene kommer fra
  `GET /meg`, som allerede har dem.
- `PUT /api/saksbehandling/v1/meg/kommunefilter` med
  `{ "enhet": "0287", "kommuner": ["3103", "3205"] }` erstatter filteret.
  `valider()` sjekker at enheten er en av den innloggedes, og at hvert
  kommunenummer har fire sifre. Ellers 400.
- Frontend lagrer ved hver endring: enhet, kommunefelt og klikk i listen.
  Listen filtreres med en gang. Feiler lagringen, gjelder filteret resten av
  økten, og siden viser `Alert variant="warning"` «Vi kunne ikke lagre
  filteret ditt.»
- `PUT` skriver direkte til tabellen, ikke via event og handler. Filteret er
  en brukerinnstilling, ikke tilstand på saken. Se beslutningene.

Valget og alternativet: Feltene ligger direkte over listen, og «tomt betyr
alle» gjør at ingen saker skjules før saksbehandleren har valgt noe. Prisen er
at kommunene ikke er gruppert på fylke, så Vest-Viken gir 41 kommuner i én
liste. Søket i `UNSAFE_Combobox` veier opp for det. Vi forkastet en modal med
avkrysning per fylke: den gir flere klikk, og filteret er ikke synlig over
listen.

## Berørte filer

| Fil | Del | Endring |
|-----|-----|---------|
| `backend/.../saksbehandling/Roles.kt` | 1 | `EGNE_ANSATTE` |
| `backend/.../saksbehandling/SakApi.kt` | 1, 2 | Bulk-sjekk i listen, nye og nullbare felter på `SakListeElement` og `SakerResponse` (`skjermet`, `deltakerTilgang`, `tilgangssjekkFeilet`, `virksomhet`, `kommunenummer`, `kommunenavn`), `feilId` på `TilgangAvvistResponse` |
| `backend/.../saksbehandling/Tilgangsjekker.kt` | 1 | Nøytralt svar og `teamLog` for kode 6 ut fra avvisningskode og Vikafossen, `feilId` |
| `backend/.../saksbehandling/KommunefilterApi.kt` (ny) | 2 | `GET` og `PUT /meg/kommunefilter` |
| `backend/.../norg/NorgKlient.kt` | 2 | Kodeverket `geografi` for kommunenavn, med cache |
| `backend/.../norg/BehandlendeEnhetUtleder.kt` | 2 | Kommunenummer |
| `backend/.../event/Events.kt` | 2 | `virksomhetKommunenummer` |
| `backend/.../event/handlers/OpprettSak.kt` | 2 | Lagrer kommunenummer |
| `backend/src/main/resources/db/migration/V27__sak_kommunenummer.sql` | 2 | `sak.kommunenummer`, `saksbehandler_kommunefilter` |
| `frontend/saksbehandling/src/pages/OversiktPage.tsx` | 1, 2 | Tag, tom arbeidsgiver uten tilgang, `Alert`, filter over fanene, kolonnen «Kommune» |
| `frontend/saksbehandling/src/components/Kommunefilter.tsx` (ny) | 2 | `Select` og `UNSAFE_Combobox` |
| `frontend/saksbehandling/src/hooks/useKommunefilter.ts` (ny) | 2 | Henter og lagrer filteret |
| `frontend/saksbehandling/src/pages/SakPage.tsx` | 1 | Tekst for `AVVIST_SKJERMING` og `IKKE_TILGANG`, feil-ID |
| `frontend/saksbehandling/src/hooks/useTildeling.ts`, `useBeslutning.ts` | 1 | Samme tekster og feil-ID |
| `frontend/saksbehandling/src/hooks/useSaker.ts` | 1, 2 | Typer |
| `frontend/saksbehandling/src/mocks/handlers.ts` | 1, 2 | Mock-saker med kommune, `/meg/kommunefilter` |
| `specifications/listevisning.md` | 1 | Listen kaller nå Tilgangsmaskinen |

## Kanttilfeller og feilmodus

- **Ingen på enheten har egne ansatte-tilgang.** Saken vises i listen som
  «Egen ansatt», men ingen kan åpne den. Det er synlig, men løser seg ikke av
  seg selv. Fagansvarlig må sørge for tilgangen, se beslutningene.
- **Beslutter uten egne ansatte-tilgang.** Topartskontrollen krever at både
  saksbehandler og beslutter har tilgangen.
- **Deltakeren blir skjermet etter innsending.** Del 1 fanger det, fordi listen
  og detaljen sjekker Tilgangsmaskinen ved hvert oppslag.
- **Skjermingen opphører.** Navnet vises igjen ved neste listevisning.
  Flagget fra opprettelsen står fortsatt, så raden har «Egen ansatt» for dem
  med tilgang.
- **Fortrolig adresse (kode 7).** Rutes på virksomhetens kommune til vanlig
  arbeidslivssenter. Tilgangsmaskinen avviser saksbehandlere uten
  `0000-GA-Fortrolig_Adresse` med `AVVIST_FORTROLIG_ADRESSE`. Samme problem
  som egne ansatte. Del 1 skjuler deltaker og arbeidsgiver og viser «Ingen
  tilgang». Detaljen gir 403 med feil-ID.
- **Svalbard og Jan Mayen** (`2100`, `2112`, `2113`, `2115`, `2211`). Ingen av
  de tolv enhetene har regel for dem, og bestmatch i Norg gir ingen enhet.
  `BehandlendeEnhetService` faller derfor tilbake til Oslo (`0391`). Det er
  slik i dag, og det står som det er. Se beslutningene.
- **Kode 6.** Rutes til Vikafossen. Åpner noen utenfor den saken, får de det
  nøytrale svaret i 1.5 med feil-ID.
- **Feil-ID uten treff i team logs.** Feil-ID-en finnes i vanlig logg for alle
  403. Bare kode 6 får egen linje i team logs.
- **Arena-saker** (`kilde_til_behandling = ARENA`). Får bulk-sjekken i
  listen som andre saker. Har ingen kommune, og vises bare når kommunefeltet
  er tomt.
- **Lagret enhet som saksbehandleren ikke lenger har.** Filteret ignoreres.
  Første enhet velges, og kommunefeltet er tomt.
- **Lagret kommune som ikke finnes lenger** (sammenslåing, nytt nummer).
  Fjernes fra filteret når den mangler i `geografi`.
- **Sak fra en kommune utenfor filteret.** Saken er skjult og telles i
  «N saker skjult av kommunefilteret». Kommunen er blant valgene, fordi
  valgene bygges før kommunefilteret brukes.
- **Kommune uten saker.** Kan ikke velges før den første saken kommer, med
  mindre den er lagret fra før.
- **Norg svarer ikke.** `kommunenavn` hentes fra cachen. Er cachen tom, viser
  frontend kommunenummeret. Filteret virker som før.
- **Mange saker i listen.** Bulk tar 1000 identer per kall. Over det går det
  flere kall i sekvens.
- **Personopplysninger.** Fnr sendes til Tilgangsmaskinen i request-body, ikke
  i URL. Ingenting nytt logges med fnr eller navn.

## Ferdig når

### Del 1

- [ ] `Role.EGNE_ANSATTE` finnes og returneres fra `GET /api/saksbehandling/v1/meg`
- [ ] `GET /saker` returnerer `skjermet` fra hooken `skjermetVedOpprettelse()` (TODO 661) og sjekker Tilgangsmaskinen i bulk med `KJERNE`
- [ ] Avviste rader har ikke deltaker, virksomhet eller kommune, har `deltakerTilgang` og kan ikke tildeles
- [ ] Feil mot Tilgangsmaskinen gir liste uten deltaker og virksomhet, og `tilgangssjekkFeilet`
- [ ] Listen viser «Egen ansatt» for skjermede saker, og «Ingen tilgang» for andre avviste rader
- [ ] Detalj, tildeling og beslutning viser forklarende tekst for `AVVIST_SKJERMING`
- [ ] Alle 403 har `feilId`, og frontend viser den
- [ ] Kode 6 uten tilgang gir `IKKE_TILGANG` og én linje i team logs uten opplysninger om personen
- [ ] `listevisning.md` er oppdatert
- [ ] PR-beskrivelsen sier at fagansvarlig må sørge for at hvert arbeidslivssenter har saksbehandlere og beslutter med `0000-GA-Egne_ansatte`
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne

### Del 2

- [ ] `sak.kommunenummer` lagres for nye saker (ikke kode 6)
- [ ] Kommunevalgene er kommunene på sakene på enheten, pluss lagrede kommuner
- [ ] «N saker skjult av kommunefilteret» vises når filteret skjuler saker
- [ ] Listen har kolonnen «Kommune», og et klikk på kommunen legger den til i
      filteret
- [ ] «Enhet» vises bare ved flere enheter, og bytte tømmer kommunefeltet
- [ ] «Kommune» filtrerer alle fanene, og tomt felt betyr alle
- [ ] Filteret lagres per saksbehandler og er der etter ny innlogging
- [ ] `PUT /meg/kommunefilter` gir 400 for enhet utenfor den innloggedes
      enheter og for ugyldig kommunenummer
- [ ] API-tester for `GET` og `PUT /meg/kommunefilter`
- [ ] `mvn -B package` er grønn
- [ ] `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling` er grønne
