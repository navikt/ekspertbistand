# Oppslag av deltaker: arbeidsforhold og navn

Trello: https://trello.com/c/3Oq7KiFc/601-1-sjekke-om-deltaker-jobber-i-virksomheten-hvis-ja-vise-navn-p%C3%A5-deltaker
(kort 601, kort-ID `6aabde383d44a078348cecb8`)

Bygger på `sjekk_deltaker_ansettelsesforhold.md`, som la til `AaregClient`.
Klienten er ikke i bruk ennå. Dette kortet tar den i bruk.

## Mål

I dag skriver arbeidsgiver inn fødselsnummer og navn på deltakeren for hånd.
Etter dette kortet oppgir arbeidsgiver bare fødselsnummer og virksomhet i
steg 1. Når hen trykker «Neste», sjekker backend i Aa-registeret at personen har
et aktivt arbeidsforhold i virksomheten:

- Finnes arbeidsforholdet, henter backend navnet fra PDL og lagrer fødselsnummer,
  navn og virksomhet på utkastet. Steg 2 viser de tre feltene låst.
- Finnes det ikke, får arbeidsgiver en feilmelding på steg 1 og kommer ikke
  videre.

Navnet kommer fra PDL og kan ikke endres av klienten. Søknaden kan bare sendes
inn med en deltaker som er bekreftet i Aa-registeret.

## Figma

Fil `lvEwrzOupYmavMI2QmsMPI` (Ekspertbistand).

| Node | Viser |
|------|-------|
| [`1018:46063`](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1018-46063) | Steg 1 av 4. Overskriften «Ansatt som skal delta i tiltaket», med fødselsnummer (11 siffer) og «Virksomheten som deltakeren er ansatt i» (virksomhetsvelger med beliggenhetsadresse). Knappene «Forrige steg» og «Neste», og lenkene «Fortsett senere» og «Slett søknaden». |
| [`1006:44445`](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1006-44445) | Steg 1 når arbeidsforholdet mangler. Feiloppsummering med overskriften «For å gå videre må du rette opp følgende:». Punktet lenker til virksomhetsvelgeren og har en ekstra lenke for å be om tilgang i Altinn. Skissen viser «Steg 1 av 3», men vi bruker fire steg som i de andre skissene. |
| [`1018:46355`](https://www.figma.com/design/lvEwrzOupYmavMI2QmsMPI/Ekspertbistand?node-id=1018-46355) | Steg 2 av 4. Fødselsnummer, navn og virksomhet er låst og har hengelås foran ledeteksten. Under kommer «Kontaktperson i virksomheten» (navn, e-post, telefonnummer) og «Ekspert» (navn, tilknyttet virksomhet, kompetanse/autorisasjon). |

Skissen for steg 2 har ikke med Nav-kontaktfeltet og de nye ekspertfeltene som
finnes i dag. De blir værende på steg 2, se *Tilnærming*.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 601, beskrivelse | Arbeidsgiver fyller inn fullt fødselsnummer og velger virksomhet blant virksomhetene hen har tilgang til ekspertbistand for i Altinn. «Neste» validerer først fødselsnummeret og sjekker deretter arbeidsforholdet. |
| Kort 601, beskrivelse | Mangler arbeidsforholdet, vises feilmeldingen fra `1006:44445`. Finnes det, går arbeidsgiver til steg 2, der navnet fra PDL vises. Fødselsnummer, navn og virksomhet er låst. Steg 3 er som før. |
| Kort 601, kommentar fra Hanna, 2026-10-26 (gjengir Espen og Ken) | Ifølge ROS-en til Aa-registeret er det greit at vi slår opp arbeidsforhold. Vi som konsumerer API-et må selv stå for tilgangsstyringen for kode 6/7 og egne ansatte. Ken: «Dette håndterer vi. Vi får passe på å beskrive det godt i etterlevelse / behandling osv.» |
| Kort 601, sjekkliste | Rate limit på hvor mange ganger arbeidsgiver kan slå opp et fødselsnummer, for eksempel 3 ganger på 20 minutter. |
| Kort 601, sjekkliste | Egen feilmelding når innlogget bruker legger inn seg selv som deltaker. |
| Anders, 2026-10-29 | Rate limit er med i dette kortet. |
| Anders, 2026-10-30 | Arbeidsgiver kan legge inn seg selv som deltaker, men må bekrefte det i en dialog. Backend avgjør om fødselsnummeret er innlogget brukers. Erstatter «tas i et senere kort» fra 2026-10-29, og sjekklistens «eget feilmeddelande». |
| Anders, 2026-10-30 | Sjekken på eget fødselsnummer gjøres i frontend før kallet til `valider-deltaker`. Den er bare et «heads up» til arbeidsgiver, ikke en sperre i backend. Erstatter «backend avgjør» over. |
| Anders, 2026-10-29 | Oppslagene lagres i databasen, slik at grensen gjelder på tvers av pods. Oppslag uten treff teller også. |
| Anders, 2026-10-30 | Grensen er 5 ulike fødselsnummer per 5 minutter per innlogget bruker. På et fødselsnummer som allerede er slått opp i vinduet, kan arbeidsgiver prøve så mange virksomheter hen vil. Erstatter «3 oppslag per 20 minutter» fra sjekklisten og fra 2026-10-29. |
| Anders, 2026-10-29 | Navnet vises også for deltakere med adressebeskyttelse eller skjerming. Arbeidsgiver kjenner den ansatte, og oppslaget gir aldri adresse. Valget beskrives i etterlevelse. |
| Anders, 2026-10-29 | Backend eier fødselsnummer, navn og bekreftelsen. Klienten kan ikke sette navnet. Innsending krever et bekreftet oppslag. |
| Anders, 2026-10-29 | Steg 1 kan alltid redigeres. Endrer arbeidsgiver fødselsnummer eller virksomhet og trykker «Neste», gjøres et nytt oppslag som erstatter det gamle. |
| Anders, 2026-10-29 | Skjemaet får fire steg som i skissen. Dagens steg 1 deles i to. |
| Anders, 2026-10-29 | Eksisterende utkast beholder fødselsnummeret, men navnet fjernes. Arbeidsgiver må gjøre et oppslag fra steg 1 før innsending. |
| Anders, 2026-10-29 | `navn` er registrert for behandlingsnummer B591 i behandlingskatalogen. |

## Avgrensning

- Ny sjekk mot Aa-registeret ved innsending. Innsendingen bruker oppslaget som er
  lagret på utkastet.
- Saksbehandlingsløsningen. `SoknadTable` og `SoknadInnsendt` får ingen nye felter.
- Endringer i virksomhetsvelgeren (`@navikt/virksomhetsvelger`).

## Tilnærming

🔴 Rød sone: personopplysninger og tilgangsstyring. Endepunktet gjør det mulig
for en innlogget arbeidsgiver å slå opp navn og arbeidsforhold på et
fødselsnummer. Det må derfor være strengt avgrenset. Oppslaget gjøres bare for
virksomheter brukeren har Altinn-tilgang til, svarer bare med navn ved treff,
og har rate limit.

### 1. Navn fra PDL

Ny spørring `backend/src/main/resources/graphql/pdl/HentPersonNavn.graphql`:

```graphql
query HentPersonNavn($ident: ID!) {
    hentPerson(ident: $ident) {
        navn(historikk: false) {
            fornavn
            mellomnavn
            etternavn
        }
    }
}
```

`PdlApiKlient.hentNavn(fnr): Result<String>` følger mønsteret i
`hentAdressebeskyttelse`, med samme feilmapping (`NotFound`, `ServerError` osv.).
Navnet bygges som `fornavn [mellomnavn] etternavn`. Tom `navn`-liste gir
`NotFound`.

Den lokale PDL-mocken i `LocalApplication.kt` matcher på `hentPerson` i
request-body. Den skal svare med `navn` når spørringen er `HentPersonNavn`, og
med adressebeskyttelse som i dag ellers.

### 2. Database

Migrering `V31__deltaker_oppslag.sql`:

```sql
SET lock_timeout = '3s';

ALTER TABLE utkast ADD COLUMN IF NOT EXISTS ansatt_bekreftet_tidspunkt TIMESTAMP NULL;
UPDATE utkast SET ansatt_navn = NULL;

CREATE TABLE IF NOT EXISTS deltaker_oppslag (
    id BIGSERIAL PRIMARY KEY,
    innlogget_bruker_hash TEXT NOT NULL,
    fnr_hash TEXT NOT NULL,
    tidspunkt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS deltaker_oppslag_bruker_tidspunkt
    ON deltaker_oppslag (innlogget_bruker_hash, tidspunkt);
```

- `UtkastTable` får `ansattBekreftetTidspunkt`. Er den null, er deltakeren ikke
  bekreftet.
- `UPDATE utkast SET ansatt_navn = NULL` fjerner navnene som er skrevet inn for
  hånd i eksisterende utkast. Fødselsnummeret blir stående.
- `deltaker_oppslag` lagrer en SHA-256-hash av innlogget brukers
  fødselsnummer, tidspunktet og en SHA-256-hash av fødselsnummeret det ble
  slått opp på. Hashene trengs for å telle ulike fødselsnummer per bruker.
  Ingen av fødselsnumrene lagres i klartekst. Rader eldre enn én
  time slettes i samme jobb som `slettGamleUtkast`.

### 3. Rate limit

Ny `soknad/DeltakerOppslagRateLimit.kt`:

```kotlin
class DeltakerOppslagRateLimit(
    private val database: Database,
    private val maksUlikeFnr: Int = 5,
    private val vindu: Duration = 5.minutes,
    private val clock: Clock = Clock.System,
) {
    /** Registrerer oppslaget og returnerer true hvis det er innenfor grensen. */
    fun registrer(innloggetBruker: String, fnr: String): Boolean
}
```

`registrer` kjører i én transaksjon:

1. `pg_advisory_xact_lock(hashtext(innlogget_bruker_hash))`, slik at samtidige kall
   fra samme bruker ikke slipper forbi grensen.
2. Henter de ulike `fnr_hash` brukeren har innenfor vinduet (rullerende
   vindu, siste 5 minutter).
3. Finnes hashen for dette fødselsnummeret blant dem, er oppslaget innenfor
   grensen. Arbeidsgiver kan prøve flere virksomheter på samme fødselsnummer.
4. Hvis ikke, er oppslaget innenfor grensen bare hvis brukeren har færre enn
   `maksUlikeFnr` ulike fødselsnummer i vinduet.
5. Er oppslaget innenfor grensen, lagres en ny rad og funksjonen returnerer
   `true`. Ellers lagres ingenting og den returnerer `false`.

Registreringen skjer før kallene til Aa-registeret og PDL. Nye fødselsnummer
teller derfor også når oppslaget ikke gir treff, eller når Aareg eller PDL
feiler. Et nytt forsøk på samme fødselsnummer teller ikke på nytt.

Raden lagres også når fødselsnummeret er kjent fra før. Det forlenger tiden
fødselsnummeret er med i vinduet, men endrer ikke antallet ulike fødselsnummer.

### 4. Endepunkt

`POST /api/soknad/v1/{id}/valider-deltaker` (TokenX) gjør alle sjekkene på
deltakeren i ett kall. Det validerer fødselsnummeret og slår opp arbeidsforhold
og navn. Stien bruker bindestrek (kebab-case), som er vanlig i REST-stier. Body:

```json
{ "fnr": "12345678910", "virksomhetsnummer": "910456900" }
```

Steg, i rekkefølge:

| # | Sjekk | Svar ved feil |
|---|-------|---------------|
| 1 | Utkastet finnes og er i utkast-status | 409, som PATCH |
| 2 | Innlogget bruker har opprettet utkastet | 403 |
| 3 | Validering av input: `valider(body)`, `fnr` er 11 siffer med gyldige kontrollsiffer (mod 11) og `virksomhetsnummer` er 9 siffer | 400 `UGYLDIG_INPUT` med feltsti, via `UgyldigInputException` |
| 4 | Altinn-tilgang til `virksomhetsnummer` | 403 |
| 5 | Samme fødselsnummer og virksomhet er allerede bekreftet på utkastet | Svarer 200 med utkastet uten nytt oppslag. Teller ikke mot grensen |
| 6 | `DeltakerOppslagRateLimit.registrer(innloggetBruker, fnr)` | 429 `{ "kode": "FOR_MANGE_OPPSLAG" }` |
| 7 | `AaregClient.hentArbeidsforhold(fnr, virksomhetsnummer)`. Treff er minst ett arbeidsforhold der `arbeidssted.identer` har `ident == virksomhetsnummer` | 422 `{ "kode": "INGEN_ARBEIDSFORHOLD" }` |
| 8 | `PdlApiKlient.hentNavn(fnr)` | 503 `{ "kode": "OPPSLAG_FEILET" }` |
| 9 | Lagrer på utkastet: `ansatt_fnr`, `ansatt_navn`, `virksomhetsnummer`, `virksomhetsnavn` (fra Altinn-hierarkiet, se under) og `ansatt_bekreftet_tidspunkt = now()` | – |

Backend sjekker ikke om fødselsnummeret er innlogget brukers eget. Det gjør
frontend før kallet, som et varsel til arbeidsgiver (se 7).

Ved 200 svarer endepunktet med hele utkastet (`DTO.Utkast`), som PATCH.

Ved 422 lagres `ansatt_fnr`, `virksomhetsnummer` og `virksomhetsnavn`, mens `ansatt_navn` og
`ansatt_bekreftet_tidspunkt` settes til null. Utkastet viser da det arbeidsgiver
skrev, og et tidligere bekreftet navn kan ikke bli stående på feil person.

Når Aa-registeret eller PDL feiler (timeout, 5xx, token), svarer endepunktet
503 `OPPSLAG_FEILET`, og utkastet endres ikke. Svaret fra
Aa-registeret med 404 er et åpent spørsmål, se under.

Kodene i 409, 422, 429 og 503 bruker samme form som `UGYLDIG_INPUT`:
`{ "kode": "...", "message": "..." }`.

Kontrollsifferet sjekkes med en ny funksjon i `infrastruktur/InputValidering.kt`,
`erGyldigFodselsnummer(fnr)`. Syntetiske nummer fra Nav og Tenor har gyldige
kontrollsiffer, så sjekken er lik i alle miljøer.

### 5. PATCH og innsending

`DTO.Utkast` får `deltakerBekreftet: Boolean = false`, som settes fra
`ansatt_bekreftet_tidspunkt != null`. Klienten kan sende feltet, men backend
ignorerer det.

**PATCH `/{id}`:**

- `ansatt` (fødselsnummer og navn) ignoreres alltid.
- `virksomhet.virksomhetsnummer` og `virksomhet.virksomhetsnavn` ignoreres
  alltid. Kontaktpersonen lagres som før.
- PATCH sjekker ikke lenger Altinn-tilgang, siden den ikke skriver
  virksomhetsnummer. Bare den som opprettet utkastet, kan endre det.

Fødselsnummer, virksomhetsnummer og virksomhetsnavn settes bare av
`valider-deltaker` (avgjort av Anders Vik Lysne 30. oktober 2026). Dermed kan
bekreftelsen ikke komme ut av takt med det som er lagret. Et fødselsnummer blir
heller ikke lagret før det er slått opp. Arbeidsgiver må skrive det inn på nytt
hvis hen forlater steg 1 før «Neste».

Virksomhetsnavnet hentes fra Altinn-tilgangene som endepunktet allerede slår
opp: `AltinnTilgangerClientResponse.navn(orgnr)` søker gjennom hele hierarkiet,
også underenheter. Det er samme kilde som virksomhetsvelgeren i frontend bruker.
Klienten sender ikke navnet.

`DTO.Utkast.virksomhet` er `null` til virksomhetsnummeret er satt. En
kontaktperson som er lagret før det, vises derfor først etter oppslaget.

**PUT `/{id}` (innsending):** før maks-sats-sjekken krever backend at

- `ansatt_bekreftet_tidspunkt` er satt,
- `soknad.ansatt.fnr` er likt lagret `ansatt_fnr`,
- `soknad.virksomhet.virksomhetsnummer` er likt lagret `virksomhetsnummer`.

Feiler en av sjekkene, kaster backend `UgyldigInputException("ansatt.fnr",
"deltaker er ikke bekreftet")`, som gir 400. `SoknadTable.ansattNavn` og
`SoknadTable.virksomhetsnavn` får verdiene fra utkastet, ikke fra request-body.

### 6. Lokal utvikling og DI

- Oppslagene mot Aa-registeret og PDL ligger bak grensesnittet
  `DeltakerOppslag` (`harAktivtArbeidsforhold`, `hentNavn`), som følger mønsteret
  til `AdressebeskyttelseOppslag`. `AaregPdlDeltakerOppslag` bruker `AaregClient`
  og `PdlApiKlient`. Aareg-svaret 404 gir `false`.
- `Application.kt`: `provide<DeltakerOppslag> { AaregPdlDeltakerOppslag(resolve(), resolve()) }`.
- `configureSoknadApiV1` henter `DeltakerOppslag`, lager
  `DeltakerOppslagRateLimit(database)` og sender begge til `SoknadApi`.
- `LocalApplication.kt` får en `AaregClient` med `MockEngine`:
  - fødselsnummer som slutter på `00000` gir tom liste (ingen arbeidsforhold),
  - alle andre gir ett aktivt arbeidsforhold på virksomhetsnummeret i
    `Nav-Arbeidsstedident`.

### 7. Frontend (`frontend/client`)

**Steg og ruter.** Skjemaet får fire steg:

| Rute | Side | Innhold |
|------|------|---------|
| `steg-1` | `SkjemaSteg1Page` (endres) | Fødselsnummer og virksomhet med beliggenhetsadresse |
| `steg-2` | `SkjemaSteg2Page` (ny) | Låst fødselsnummer, navn og virksomhet. Kontaktperson, ekspert og Nav-kontakt, flyttet fra dagens steg 1 |
| `steg-3` | `SkjemaSteg3Page` (dagens `SkjemaSteg2Page`) | Behov for bistand, uendret |
| `oppsummering` | `OppsummeringPage` | Som i dag. «Endre» for deltaker går til steg 1 |

- `SkjemaFormProgress` får `totalSteps={4}` og stegene «Deltaker»,
  «Kontaktperson og ekspert», «Behov for bistand» og «Oppsummering».
- `useSkjemaNavigation` får `goToStep3`.
- I `schema.ts` endres feltgruppene:
  - `STEP1_FIELDS` er `virksomhet.virksomhetsnummer`, `virksomhet.virksomhetsnavn`,
    `virksomhet.beliggenhetsadresse` og `ansatt.fnr`.
  - `STEP2_FIELDS` er kontaktperson, ekspert og `nav.kontaktperson`.
  - `STEP3_FIELDS` er dagens `STEP2_FIELDS`.
- `ansatt.navn` er fortsatt påkrevd i `soknadSchema`, med meldingen «Du må gå
  videre fra steg 1 for å hente navnet på deltakeren.» Feilen peker til steg 1.

**Validering av deltaker.** Ny funksjon
`validerDeltaker(draftId, { fnr, virksomhetsnummer })`
i `features/soknad/`. Den kaller endepunktet med `fetchJson` og tolker svaret med
`server-schemas.ts`.

`handleSubmitStep1`:

1. `form.trigger(STEP1_FIELDS)`. Ugyldig fødselsnummer stopper her med
   feltfeil, som i dag.
2. Er `deltakerBekreftet` `true`, og fødselsnummer og virksomhet er de samme som
   sist bekreftet, går funksjonen rett til steg 2 uten kall.
3. Er fødselsnummeret likt `opprettetAv` på utkastet, åpnes dialogen for egen
   deltakelse (se under) før kallet. `opprettetAv` er innlogget brukers
   fødselsnummer, fordi bare den som opprettet utkastet har tilgang til det.
4. Ellers, eller når arbeidsgiver har bekreftet i dialogen, kaller den
   `validerDeltaker`. Knappen «Neste» viser `loading` mens kallet pågår.
5. Ved 200 setter funksjonen `ansatt.navn` og `deltakerBekreftet` i skjemaet og
   går til steg 2.
6. Ved feil vises:

| Svar | Visning |
|------|---------|
| 422 `INGEN_ARBEIDSFORHOLD` | `ErrorSummary` som i `1006:44445`. Overskrift «For å gå videre må du rette opp følgende:». Punktet «Velg virksomheten som deltaker er ansatt i, det vil si virksomheten dere rapporterer deltakerens lønn på i A-meldingen.» lenker til virksomhetsvelgeren. Under står «Hvis du ikke kan velge riktig virksomhet her, må du be om tilgang til enkelttjenesten «Tilskudd til ekspertbistand» for riktig virksomhet i Altinn.», med lenke til `TILGANGSSTYRING_URL` |
| 429 `FOR_MANGE_OPPSLAG` | `LocalAlert status="warning"`: «Du har slått opp for mange ulike fødselsnummer på kort tid. Vent 5 minutter og prøv igjen.» |
| 503 `OPPSLAG_FEILET` eller annen feil | `LocalAlert status="error"`: «Vi kunne ikke sjekke arbeidsforholdet akkurat nå. Prøv igjen senere.» |
| 400 `UGYLDIG_INPUT` | Feltfeil på `ansatt.fnr` eller virksomheten, ut fra `felt` |

`ErrorSummary` sin første lenke flytter fokus til virksomhetsvelgeren, slik
`FormErrorSummary` gjør i dag.

Lenkene til steg 2, steg 3 og oppsummeringen i stegindikatoren på steg 1 går
gjennom den samme flyten. De går dit lenken peker etter et vellykket oppslag.

Autolagring (PATCH) skriver ikke deltaker eller virksomhet inn i
utkastet frontend har i minnet, siden backend ignorerer feltene. Sammenligningen
i punkt 2 bruker derfor alltid det backend sist bekreftet.

**Dialog når deltakeren er innlogget bruker.** Ny komponent
`components/BekreftEgenDeltakelseModal.tsx` med Aksel `Modal`. Dialogen er
bare et varsel. Backend sperrer ikke eget fødselsnummer.

- `header={{ heading: "Er du selv deltakeren?" }}`.
- `Modal.Body`: «Fødselsnummeret du har skrevet inn, er ditt eget. Bekreft at
  du søker om ekspertbistand der du selv er deltakeren.»
- `Modal.Footer` med to knapper:
  - «Ja, jeg er deltakeren» (`variant="primary"`). Lukker dialogen og kaller
    `validerDeltaker`. Svaret håndteres som over.
  - «Nei, endre fødselsnummer» (`variant="secondary"`). Lukker dialogen og
    flytter fokus til fødselsnummerfeltet.
- Lukkes dialogen med Esc eller lukkeknappen, skjer det samme som ved «Nei».
  `closeOnBackdropClick` er av, som standard.

Bekreftelsen gjelder bare det ene kallet. Den lagres ikke. Endrer
arbeidsgiver fødselsnummer eller virksomhet og kommer tilbake til sitt eget
fødselsnummer, får hen spørsmålet på nytt. Er deltakeren uendret og bekreftet
(punkt 2), kommer ikke spørsmålet.

**Låste felter på steg 2.**

- Fødselsnummer og navn vises som `TextField` med `readOnly`. Aksel 8.20 legger
  automatisk til hengelåsikonet (`PadlockLockedFillIcon`) foran ledeteksten når
  `readOnly` er satt, slik skissen viser.
- Virksomheten vises som `TextField` med `readOnly`, ledeteksten «Virksomheten
  som deltakeren er ansatt i», `virksomhetsnavn` som verdi og «Org.nr.
  …» som `description`. Under kommer beliggenhetsadressen som på steg 1.
  Virksomhetsvelgeren har ingen låst variant, se åpne spørsmål.

**Vakt.** Steg 2, steg 3 og oppsummeringen sender arbeidsgiver til steg 1 hvis
`deltakerBekreftet` er `false`. Det gjelder også eksisterende utkast.

**Innsending.** 400 med `felt` lik `ansatt.fnr` sender arbeidsgiver til steg 1,
på samme måte som feltfeil i dag sendes til riktig steg. Frontend henter
utkastet på nytt og viser feltfeilen «Du må sjekke deltakeren på nytt før du
sender inn. Trykk «Neste steg».» på fødselsnummeret.

**Mock (MSW).** `handlers.ts` får en enkel handler for `POST .../:id/valider-deltaker`.
Den godtar alle fødselsnummer og alle virksomheter og svarer 200 med navnet
«Mona Moonlight». Handleren lagrer resultatet på mock-utkastet. PATCH-handleren
ignorerer `ansatt`, `virksomhet.virksomhetsnummer` og `virksomhet.virksomhetsnavn`, som i 5.
Den innloggede mock-brukeren (`MOCK_BRUKER`, som blir `opprettetAv`) får et
gyldig syntetisk fødselsnummer, slik at dialogen for egen deltakelse kan prøves
lokalt. E2E-hjelperen `createDraftId` bekrefter deltakeren, slik at vakten
slipper testene inn på steg 2, steg 3 og oppsummeringen.

Feiltilfellene (409, 422, 429 og 503) testes med `server.use(...)` i den enkelte
testen, ikke i den felles handleren.

Aksel (`@navikt/ds-react` ^8.16.1, installert 8.20.0): `TextField` (`readOnly`),
`ErrorSummary`, `LocalAlert`, `Modal` og `Button` (`loading`). Komponentene er
slått opp i Aksel MCP. Repoet bruker `Alert` fra legacy enkelte steder. Nye
meldinger bruker `LocalAlert`.

### 8. Tester

Backend:

- `PdlApiKlientTest`: `hentNavn` med og uten mellomnavn, tom `navn`-liste
  (`NotFound`) og serverfeil.
- `DeltakerOppslagRateLimitTest`:
  - fem ulike fødselsnummer godtas, og det sjette avvises;
  - etter fem ulike fødselsnummer godtas fortsatt nye oppslag på de samme fem;
  - mange oppslag på samme fødselsnummer godtas;
  - fødselsnummer utenfor vinduet teller ikke;
  - ulike brukere telles hver for seg;
  - verken brukerens eller deltakerens fødselsnummer lagres i klartekst.
- `InputValideringTest`: gyldige kontrollsiffer, D-nummer, syntetiske nummer,
  feil lengde og feil kontrollsiffer.
- `AaregPdlDeltakerOppslagTest` (`MockEngine`): treff bare når
  `arbeidssted.identer` har virksomhetsnummeret, 404 gir `false`, andre feil
  kastes.
- `ValiderDeltakerTest` (ny, `testApplicationWithDatabase`, falsk
  `DeltakerOppslag` som teller kall):
  - treff gir 200 og lagret navn, virksomhetsnavn (også for underenhet) og bekreftelse;
  - ingen arbeidsforhold gir 422, og navn og bekreftelse nullstilles;
  - arbeidsforhold i en annen virksomhet gir 422;
  - sjette ulike fødselsnummer gir 429;
  - samme fødselsnummer mot flere virksomheter gir ikke 429;
  - uendret og allerede bekreftet deltaker gir 200 uten kall til Aareg og uten å
    telle;
  - Aareg- og PDL-feil gir 503 og ingen endring;
  - ugyldig fødselsnummer gir 400;
  - manglende Altinn-tilgang gir 403;
  - utkast opprettet av en annen bruker gir 403;
  - PATCH ignorerer fødselsnummer, navn, virksomhetsnummer og virksomhetsnavn, beholder
    bekreftelsen og lagrer kontaktperson.
- `SoknadTest`:
  - PUT uten bekreftet deltaker, eller med et annet fødselsnummer enn det
    bekreftede, gir 400 med `felt = ansatt.fnr`;
  - PUT bruker deltakerens navn og virksomhetsnavnet fra utkastet.
  - Eksisterende tester som sender inn søknad, setter bekreftelsen først.

Frontend (vitest og MSW):

- `schema.test.ts`: nye feltgrupper.
- `validerDeltaker.test.ts` (ny): svar fra MSW-handleren, at PATCH ikke endrer
  deltaker eller virksomhet, og at 400, 409, 422, 429, 503 og nettverksfeil
  gjøres om til riktig feiltype.
- `SkjemaSteg1Page.test.tsx` (ny):
  - treff går til steg 2;
  - 422 viser feiloppsummeringen med Altinn-lenke;
  - 429 og 503 viser `LocalAlert`;
  - ugyldig fødselsnummer gjør ikke kallet;
  - uendret og bekreftet deltaker går til steg 2 uten kall;
  - fødselsnummer likt `opprettetAv` åpner dialogen før kallet;
  - «Ja, jeg er deltakeren» gjør kallet og går til steg 2;
  - «Nei, endre fødselsnummer» lukker dialogen uten kall og gir fokus til
    fødselsnummerfeltet.
- `SkjemaSteg2Page.test.tsx` (ny): låste felter vises med verdier. Ikke
  bekreftet deltaker sender brukeren til steg 1.

E2E-testene i `e2e/` oppdateres bare hvis de brekker på de nye stegene.

### 9. Personvern og etterlevelse

- Fødselsnummeret sendes bare i request-body og i Aareg-headere, aldri i URL
  eller logg. Navnet logges ikke.
- `deltaker_oppslag` lagrer SHA-256-hasher av innlogget brukers
  fødselsnummer og av fødselsnummeret det ble slått opp på, sammen med
  tidspunktet, i høyst én time. Ingen av fødselsnumrene lagres i klartekst.
  Hashene er pseudonymisering, ikke anonymisering:
  fødselsnummer har få nok mulige verdier til at hashen kan knekkes. Derfor er
  lagringstiden kort.
- Endepunktet svarer med navn bare ved treff i Aa-registeret, for en virksomhet
  brukeren har Altinn-tilgang til.
- Navn vises også for kode 6/7 og egne ansatte (Anders, 2026-10-29).
  Behandlingen beskrives i etterlevelse.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/resources/graphql/pdl/HentPersonNavn.graphql` (ny) | Spørring for navn |
| `backend/.../pdl/PdlApiKlient.kt` | `hentNavn` |
| `../backend/src/main/resources/db/migration/V31__deltaker_oppslag.sql` (ny) | Kolonne, nullstilling og tabell |
| `backend/.../soknad/Db.kt` | `ansattBekreftetTidspunkt`, `DeltakerOppslagTable`, `deltakerBekreftet` i `tilUtkastDTO` |
| `backend/.../soknad/DeltakerOppslagRateLimit.kt` (ny) | Rate limit |
| `backend/.../soknad/DeltakerOppslag.kt` (ny) | Grensesnitt og Aareg/PDL-implementasjon |
| `backend/.../soknad/Api.kt` | `validerDeltaker`, regler i PATCH og PUT, `DTO.Utkast.deltakerBekreftet` |
| `backend/.../altinn/AltinnTilgangerClient.kt` | `navn(orgnr)` slår opp virksomhetsnavn i hierarkiet |
| `backend/.../soknad/Routing.kt` | Ruten `POST /{id}/valider-deltaker`, opprydding i `deltaker_oppslag` |
| `backend/.../infrastruktur/InputValidering.kt` | `erGyldigFodselsnummer` |
| `backend/.../Application.kt` | DI og statuskoder |
| `backend/src/test/.../LocalApplication.kt` | Aareg-mock, navn i PDL-mock |
| `backend/src/test/.../pdl/PdlApiKlientTest.kt` | `hentNavn` |
| `backend/src/test/.../soknad/DeltakerOppslagRateLimitTest.kt` (ny) | Rate limit |
| `backend/src/test/.../soknad/ValiderDeltakerTest.kt` (ny) | Endepunktet |
| `backend/src/test/.../soknad/SoknadTest.kt` | PATCH og PUT |
| `frontend/client/src/App.tsx` | Ruten `steg-3` |
| `frontend/client/src/pages/SkjemaSteg1Page.tsx` | Bare deltaker og virksomhet, oppslag |
| `frontend/client/src/pages/SkjemaSteg2Page.tsx` | Ny side: låste felter, kontaktperson, ekspert, Nav |
| `frontend/client/src/pages/SkjemaSteg3Page.tsx` | Dagens steg 2 |
| `frontend/client/src/pages/OppsummeringPage.tsx` | Fire steg, lenker og feilruting |
| `frontend/client/src/components/SkjemaFormProgress.tsx` | Fire steg |
| `frontend/client/src/hooks/useSkjemaNavigation.ts` | `goToStep3` |
| `frontend/client/src/features/soknad/schema.ts` | Feltgrupper |
| `frontend/client/src/features/soknad/server-schemas.ts` | `deltakerBekreftet` |
| `frontend/client/src/features/soknad/validerDeltaker.ts` (ny) | API-kall |
| `frontend/client/src/components/BekreftEgenDeltakelseModal.tsx` (ny) | Dialog når deltakeren er innlogget bruker |
| `frontend/client/src/mocks/handlers.ts` | Handler og PATCH-regler |
| Tester i `frontend/client/src/` | Se 8 |

## Kanttilfeller og feilmodus

- **Arbeidsgiver endrer fødselsnummer eller virksomhet etter et vellykket
  oppslag.** Det lagrede utkastet beholder det forrige oppslaget til «Neste»
  gjør et nytt oppslag, som overskriver fødselsnummer, virksomhet, navn og
  bekreftelse.
- **Arbeidsgiver går frem og tilbake uten å endre noe.** Frontend kaller ikke
  endepunktet, og spørsmålet om egen deltakelse kommer ikke på nytt. Kalles
  endepunktet likevel, svarer steg 5 uten nytt oppslag, så grensen brukes ikke
  opp.
- **Arbeidsforholdet avsluttes etter oppslaget.** Søknaden kan sendes inn med det
  lagrede oppslaget. Saksbehandler vurderer arbeidsforholdet.
- **Flere arbeidsforhold i samme virksomhet.** Ett aktivt arbeidsforhold er
  nok.
- **Arbeidsforhold på underenhet mot hovedenhet.** Virksomhetsvelgeren gir
  underenheter, og Aa-registeret registrerer arbeidsforhold på underenheten.
  Velger arbeidsgiver hovedenheten, blir det ingen treff, og feilmeldingen ber
  arbeidsgiver velge riktig virksomhet.
- **Samtidige oppslag fra samme bruker.** Advisory lock i `registrer` hindrer at
  flere enn fem ulike fødselsnummer slipper gjennom.
- **Arbeidsgiver prøver flere virksomheter på samme deltaker.** Teller som ett
  fødselsnummer, uansett hvor mange virksomheter som prøves.
- **Aa-registeret eller PDL er nede.** Arbeidsgiver får 503 og en melding om å
  prøve igjen. Et nytt forsøk på samme fødselsnummer teller ikke på nytt.
- **Personen finnes i Aa-registeret, men ikke i PDL.** Svarer 503
  `OPPSLAG_FEILET`. Det bør ikke skje, og det logges uten fødselsnummer.
- **Arbeidsgiver legger inn seg selv.** Frontend ser at fødselsnummeret er likt `opprettetAv` og
  viser dialogen før kallet. Bekrefter arbeidsgiver, gjøres oppslaget som vanlig.
  Innlogget bruker må da selv ha et aktivt arbeidsforhold i virksomheten, ellers
  blir svaret 422.
- **Arbeidsgiver lukker dialogen uten å svare.** Behandles som «Nei». Ingenting
  lagres, og grensen brukes ikke.
- **Eksisterende utkast.** Navnet er fjernet. Arbeidsgiver sendes til steg 1 og
  må trykke «Neste».
- **Bokmerker eller lenker til `/steg-2`.** Fører nå til det nye steg 2. Vakten
  sender arbeidsgiver til steg 1 hvis deltakeren ikke er bekreftet.

## Åpne spørsmål

1. **Svarer Aa-registeret 404 for en person uten arbeidsforhold?** `AaregClient`
   kaster ved 404 (`expectSuccess`). Forslag: 404 tolkes som ingen
   arbeidsforhold (422). Det må sjekkes mot dev før endepunktet ferdigstilles.
2. **Hva skal stegene hete i `FormProgress`?** Forslaget er «Deltaker»,
   «Kontaktperson og ekspert», «Behov for bistand» og «Oppsummering». Skissen
   har overskriften «Opprett søknad på» på steg 1 og «Deltakere» på steg 2.
3. **Låst virksomhet.** Skissen viser en låst nedtrekksliste. Forslaget er
   `TextField` med `readOnly`, fordi virksomhetsvelgeren ikke har en låst
   variant. Er det godt nok?
4. **Hash av fødselsnummer i `deltaker_oppslag`.** For å telle ulike
   fødselsnummer per bruker må vi lagre noe som identifiserer begge. Forslaget er en
   SHA-256-hash uten hemmelig nøkkel, som slettes etter én time. En HMAC med
   hemmelig nøkkel fra Nais er sikrere, men krever en ny hemmelighet. Er hash
   uten nøkkel godt nok?

## Ferdig når

- [ ] `PdlApiKlient.hentNavn` finnes og er testet
- [ ] Migreringen legger til bekreftelsen, fjerner navn fra eksisterende utkast og oppretter `deltaker_oppslag`
- [ ] `POST /api/soknad/v1/{id}/valider-deltaker` sjekker eierskap, input, Altinn, rate limit, Aa-registeret og PDL, og lagrer resultatet
- [ ] Et sjette ulike fødselsnummer innen 5 minutter gir 429, også på tvers av pods. Oppslag på et fødselsnummer som allerede er slått opp, gir ikke 429
- [ ] PATCH kan ikke endre fødselsnummer, navn, virksomhetsnummer eller virksomhetsnavn
- [ ] Innsending uten bekreftet deltaker gir 400
- [ ] Eget fødselsnummer gir en dialog. Oppslaget gjøres bare når arbeidsgiver bekrefter
- [ ] Skjemaet har fire steg, med låste felter på steg 2 og feilmeldinger på steg 1 som i Figma
- [ ] Eksisterende utkast sendes til steg 1
- [ ] MSW-mocken godtar alle fødselsnummer og virksomheter. Lokal backend støtter treff og ingen arbeidsforhold
- [ ] Testene i 8 finnes og er grønne
- [ ] `mvn -B package` er grønn, og frontend bygger og passerer lint og tester
- [ ] Behandlingen er beskrevet i etterlevelse (Ken eller Anders)
