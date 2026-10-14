# Vis årsaken når backend svarer 400

Trello: https://trello.com/c/BSI3Afgs/637-ved-http-400-b%C3%B8r-%C3%A5rsak-vises-til-brukeren
Kort-ID: `6acf319e065900f1109e7fa1`

## Mål

Når backend avviser input med 400, ser brukeren hvorfor:

- Saksbehandling viser meldingen fra backend, ikke «Kunne ikke hente data (400).».
- Søknadsskjemaet sier fra når et utkast ikke blir lagret, og viser feil på
  feltet før arbeidsgiveren går videre eller sender inn.

## Bakgrunn

- PR #151 innførte `valider()` i `infrastruktur/InputValidering.kt`. Den
  avviser tekst med `<` eller `>`, kontrolltegn, usynlige formattegn (Unicode
  `Cf`, for eksempel myk bindestrek og zero-width space), tekst over 10 000 tegn
  og lister over 100 elementer. Bruddet blir `UgyldigInputException`.
- `StatusPages` i `Application.kt` svarer på `UgyldigInputException` med 400 og
  **ren tekst**: `Ugyldig verdi i felt '<feltsti>': <årsak>`.
- `parseErrorPayload` (saksbehandling) og `parseErrorMessage` (søknad) leser
  bare JSON med `message`. Ren tekst gir `null`, og `fetchJson` faller tilbake
  til `Kunne ikke hente data (400).`. Det er rotårsaken i saksbehandling.
- Andre 400-svar i saksbehandlings-API-et sender allerede
  `mapOf("message" to …)`, for eksempel «ugyldig vilkårsvurdering».
- `useVilkårsvurdering` viser `error.message` for 400. Den får riktig tekst så
  snart backend sender JSON.
- `useForelopigVedtak` gjør 400 om til «Sjekk feltene og prøv igjen.» og kaster
  meldingen fra backend. `validerFelter` i `ForelopigVedtakApi.kt` bruker også
  `UgyldigInputException` («påkrevd», «over maks sats» og så videre).
- Forhåndsvisning av vedtaksbrev (`POST …/forelopig-vedtak/forhandsvisning`)
  kaller `valider` og `validerFelter`. `useVedtaksbrev` leser `message`, men
  `Brev` i `Vedtaksbrev.tsx` viser alltid «Kunne ikke lage forhåndsvisning av
  brevene.».
- Søknad: `useAutosaveDraft` lagrer utkastet med `PATCH` 800 ms etter hver
  endring. `SoknadDraftProvider.saveDraft` svelger feilen
  (`.catch(() => undefined)`) og ruller tilbake den optimistiske oppdateringen.
  Hele `PATCH`-en blir avvist, så ingen felt i utkastet blir lagret, og brukeren
  får ikke vite det.
- `soknadSchema` i `features/soknad/schema.ts` sjekker ikke tegnene backend
  avviser. Skjemaet bruker `mode: "onSubmit"`, så feltfeil vises når brukeren
  trykker «Neste» eller «Send inn».
- `OppsummeringPage` viser `error.message` i en `Alert` når innsending feiler.
  Den får riktig tekst så snart backend sender JSON.

## Skisser

Kortet har ingen Figma-lenke.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Kortbeskrivelsen | Ved 400 i vilkårsvurdering skal saksbehandler se feilmeldingen fra backend. |
| Kortbeskrivelsen | Søknaden skal ikke feile stille når et felt inneholder for eksempel `<` eller `>`. |
| Repo-konvensjon (`InputValidering.kt`) | Feilmeldingen ekkoer aldri verdien brukeren sendte, bare feltsti og årsak. |
| Ken, 2026-10-14 | 400 fra `valider()` er en fallback. Zod skal fange feilen i søknaden før den når backend. Søknaden oversetter derfor ikke `felt` til ledetekst. |
| Ken, 2026-10-14 | Bare 400-feil fra brukerhandlinger i søknad og saksbehandling tas med. Interne 400-feil (ugyldig id, orgnr, parametre) endres ikke. |

Kortet har ingen kommentarer, sjekklister eller vedlegg.

Valg i spec-en:

- **Fiks formatet i backend, ikke parseren i frontend.** `StatusPages` sender
  JSON slik resten av API-et gjør. Alternativet var å lese ren tekst i begge
  frontendene, men da får API-et to feilformater.
- **Speil reglene i søknadsskjemaet.** Arbeidsgiveren får feilen på feltet, med
  norsk tekst, før data sendes. Backend er fortsatt det som håndhever reglene.
- **Lagringsfeil vises ved «Sist lagret».** Der ser brukeren allerede status for
  lagring. Det er fallbacken når zod ikke har fanget feilen.

## Avgrensning

- Reglene i `valider()` endres ikke.
- Interne 400-svar med ren tekst endres ikke (Ken, 2026-10-14): `EregApi`,
  `KontoregisterApi`, `tilsagndata/Routing`, `soknad/Routing`,
  `RefusjonRouting`, `VedleggRouting` og `Parametre.kt`.
- Refusjon og sluttrapport (`RefusjonApi`, `VedleggApi`, `SokRefusjonPage`,
  `SluttrapportPage`) er egne flyter, ikke søknad, og endres ikke her.
- Søknaden oversetter ikke `felt` til ledetekst (Ken, 2026-10-14).

## Tilnærming

### 1. Backend: JSON fra `StatusPages`

`UgyldigInputException` gir:

```json
{
  "kode": "UGYLDIG_INPUT",
  "message": "Ugyldig verdi i felt 'notat': inneholder < eller >",
  "felt": "notat"
}
```

`message` beholder dagens tekst. `felt` er feltstien, slik at frontend kan
knytte feilen til et felt. Loggingen endres ikke.

### 2. Saksbehandling: vis meldingen fra backend

- `useForelopigVedtak`: 400 gir `e.message` fra backend i stedet for
  «Sjekk feltene og prøv igjen.».
- `Vedtaksbrev.tsx`: ved 400 viser `Brev` «Kunne ikke lage forhåndsvisning av
  brevene: {error.message}». Andre feil gir dagens tekst.
- `useVilkårsvurdering` trenger ingen endring.
- MSW: `PATCH …/vilkarsvurdering` og `POST …/forelopig-vedtak` svarer 400 med
  JSON-en over når en tekst inneholder `<` eller `>`, så feilen kan sjekkes
  lokalt.

### 3. Søknad: regler i `soknadSchema`

Ny `fritekst`-sjekk i `schema.ts` som speiler `standardTekstSjekker`:

| Backend-sjekk | Feilmelding på feltet |
|---------------|----------------------|
| `IngenVinkelparenteser` | «Teksten kan ikke inneholde < eller >.» |
| `IngenKontrolltegn`, `IngenUsynligeFormatTegn` | «Teksten inneholder usynlige tegn. Skriv inn teksten på nytt i stedet for å lime den inn.» |
| `IngenForLangTekst` | «Teksten kan ikke være lengre enn 10 000 tegn.» |

Sjekken legges i `trimmedText`, som alle fritekstfeltene og `emailField`
bruker. Kontrolltegn er Unicode `Cc` unntatt tab, linjeskift og CR. Usynlige
tegn er `\p{Cf}`. Det matcher `isISOControl` og `Character.FORMAT` i backend.

### 4. Søknad: vis at utkastet ikke ble lagret

- `SoknadDraftProvider` holder `saveError: string | null`. En feilet `PATCH`
  setter den til meldingen fra `HttpError`, eller «Utkastet ble ikke lagret.
  Prøv igjen senere.» når feilen ikke er en `HttpError`. Neste vellykkede
  lagring nullstiller den.
- `SoknadDraftContext` får `saveError`.
- `SistLagretInfo` får `saveError` og viser en inline `Alert` (`variant="error"`,
  `size="small"`) med «Utkastet ble ikke lagret: {saveError}» under «Sist lagret».
  Steg 1, steg 2 og oppsummeringen sender den inn.
- 401 sender brukeren til innlogging som før.

### 5. Tester

- Backend: `VilkarsvurderingApiTest` og `SoknadTest` får hver én test der
  `<` i et tekstfelt gir 400 med `kode`, `felt` og `message`, og der verdien
  ikke står i svaret.
- Søknad: `schema.test.ts` (ny) dekker de tre reglene og at vanlig norsk tekst,
  linjeskift og tab er gyldig.
- `pnpm typecheck`, `pnpm lint` og `pnpm test:run` i `frontend/client`.
  `pnpm typecheck` og `pnpm lint` i `frontend/saksbehandling`, som ikke har
  testoppsett. Manuell sjekk mot MSW.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | JSON-svar for `UgyldigInputException` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandling/VilkarsvurderingApiTest.kt` | Test av 400-svaret |
| `backend/src/test/kotlin/no/nav/ekspertbistand/soknad/SoknadTest.kt` | Test av 400-svaret |
| `frontend/saksbehandling/src/hooks/useForelopigVedtak.ts` | Bruker meldingen fra backend for 400 |
| `frontend/saksbehandling/src/components/forelopigVedtak/Vedtaksbrev.tsx` | Viser meldingen fra backend for 400 |
| `frontend/saksbehandling/src/mocks/handlers.ts` | 400 for `<` og `>` |
| `frontend/client/src/features/soknad/schema.ts` | `fritekst`-sjekk i `trimmedText` |
| `frontend/client/src/features/soknad/schema.test.ts` | Ny |
| `frontend/client/src/providers/SoknadDraftProvider.tsx` | `saveError` |
| `frontend/client/src/context/SoknadDraftContext.ts` | `saveError` i typen |
| `frontend/client/src/components/SistLagretInfo.tsx` | Viser lagringsfeil |
| `frontend/client/src/pages/SkjemaSteg1Page.tsx`, `SkjemaSteg2Page.tsx`, `OppsummeringPage.tsx` | Sender `saveError` til `SistLagretInfo` |
| `frontend/client/src/mocks/handlers.ts` | 400 for `<` og `>` på `PATCH` og `PUT` |

## Kanttilfeller og feilmodus

- **Verdier fra Ereg.** `virksomhetsnavn`, `beliggenhetsadresse` og
  `ekspert.virksomhetNavn` kommer fra oppslag og bruker ikke `trimmedText`. Et
  navn med `<` i Ereg gir fortsatt 400, men nå med synlig lagringsfeil.
- **Lim inn fra Word eller PDF.** Myk bindestrek og zero-width space er `Cf`.
  Brukeren ser ikke tegnet, så feilmeldingen ber dem skrive teksten på nytt.
- **Lagringsfeil i bakgrunnen.** Skjemaet validerer med zod ved «Neste» og
  «Send inn», men autolagring kjører mens brukeren skriver. Et felt med `<` gir
  derfor fortsatt 400 på autolagringen, og brukeren ser lagringsfeilen før
  feltfeilen. Det er fallbacken. Vil vi unngå 400 helt, må autolagringen hoppe
  over ugyldige verdier. Det er ikke med her.
- **Eldre utkast med ugyldige tegn.** Kan ikke finnes, siden backend har avvist
  dem siden PR #151.
- **Personopplysninger.** Svaret og loggen inneholder feltsti og årsak, aldri
  verdien. `Alert` viser bare backend-meldingen.
- **Tilgjengelighet.** Lagringsfeilen bruker Aksel `Alert`, som leses opp av
  skjermleser. Feltfeilene bruker skjemaets eksisterende `ErrorSummary`.

## Åpne spørsmål

Ingen. Ken besvarte spørsmålene om feltsti og interne 400-feil 2026-10-14.
Svarene står under *Beslutninger*.

## Ferdig når

- [ ] `UgyldigInputException` gir 400 med JSON (`kode`, `message`, `felt`)
- [ ] Vilkårsvurdering med `<` viser meldingen fra backend
- [ ] Foreløpig vedtak med ugyldig felt viser meldingen fra backend
- [ ] Forhåndsvisning av vedtaksbrev med ugyldig felt viser meldingen fra backend
- [ ] Søknadsfelt med `<`, `>`, usynlige tegn eller over 10 000 tegn får feil på feltet
- [ ] Feilet autolagring vises ved «Sist lagret», og forsvinner etter neste vellykkede lagring
- [ ] Backend-testene og `pnpm test:run` i `frontend/client` er grønne
- [ ] `pnpm typecheck` og `pnpm lint` er grønne i `frontend/client` og `frontend/saksbehandling`
