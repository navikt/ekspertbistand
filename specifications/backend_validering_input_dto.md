# Backend-validering av inputfelter fra frontend

Trello: https://trello.com/c/VqwIwQcj/585-backend-validering-av-inputfelter-fra-frontend (kort-ID `6aa3eb434cf42b3edff72cad`)

## Mål

Alle `String`-verdier i DTO-er som tas imot fra frontend på backend-API-et skal
valideres før de brukes, persisteres eller logges. Valideringen skal fange script-tags
og andre potensielt maliciøse verdier, slik at fritekst fra frontend ikke blir en
angrepsvektor. I tillegg skal en unit-test via refleksjon (statisk analyse av DTO-grafen)
sikre at ingen `String`-felt slipper unna valideringen når nye felt eller DTO-er legges til.

Dette dekker etterlevelseskrav, suksesskriterium 3 av 8 — «Vi validerer input og output».

> 🔴 **Rød sone.** Dette er sikkerhetskritisk inputvalidering. Les gjennom
> valideringsregelen og refleksjonswalkeren grundig før den merges — en for løs regel gir
> falsk trygghet, en for streng regel avviser legitim brukerinput (f.eks. navn med `&`,
> adresser, e-post).

## Bakgrunn

I dag tas request-body imot uten innholdsvalidering. To endepunkt i
`backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt` mottar frontend-input:

- `oppdaterUtkast` — `call.receive<DTO.Utkast>()` (linje ~132)
- `sendInnSoknad` — `call.receive<DTO.Soknad>()` (linje ~231)

DTO-grafen (`sealed interface DTO`, samme fil) har mange fritekst-`String`-felt, bl.a.
`BehovForBistand.begrunnelse/behov/tilrettelegging`, `Ekspert.kompetanse`,
`Kontaktperson.navn`, `Virksomhet.virksomhetsnavn`, `Nav.kontaktperson`, samt
`List<String>` (`Ekspert.godkjentUtdanningEllerAutorisasjon`, `relevantKompetanse`).
Ingen valideringsbibliotek er i bruk i dag (`ktor-server-request-validation` er ikke en
avhengighet). Feil på ktor-toppnivå håndteres av `StatusPages` i `Application.kt` (linje
~187), som i dag mapper alt uventet til `500`.

Multipart-opplasting (`VedleggApi`, `RefusjonApi`) tar imot **filer**, ikke tekst-DTO-er,
og valideres allerede separat (PDF-sjekk, størrelse). Disse er utenfor scope her.

## Beslutninger fra kort-kommentarene

| Kilde | Beslutning |
|-------|-----------|
| Ken, kommentar 2026-09-24 (nyeste, styrer scope) | Legg **contextual validering på alle `String`-verdier** i DTO-er som kommer inn i backend-API-et. Valideringen skal passe på at en angriper ikke har sendt inn script-tags eller andre maliciøse verdier. |
| Ken, samme kommentar | **Se på mulighet for en unit-test som via statisk analyse** sjekker at alle DTO-er har validering på slike felt. Denne spec-en gjør det til et krav, ikke bare en mulighet (se avklaring Q1). |
| Beskrivelse (eldst) | Rammer inn kravet: sjekk mot tillatte tegn, unngå fritekst der mulig, husk at logg er output. |

Kommentaren er nyere enn beskrivelsen og utvider den: der beskrivelsen sier «kontroll på
tillatte tegn», presiserer kommentaren at det gjelder **alle** `String`-felt i innkommende
DTO-er, og legger til refleksjons-testen som eget krav.

## Tilnærming

En sentral, refleksjonsbasert validator kalles rett etter `call.receive<...>()` på begge
endepunktene. Den går rekursivt gjennom hele DTO-grafen og kjører en delt
«trygg tekst»-regel på hvert `String`- og `List<String>`-felt. Brudd kaster
`UgyldigInputException`, som `StatusPages` mapper til `400 Bad Request` uten å ekko den
ugyldige verdien tilbake.

Hvorfor refleksjonsbasert framfor per-felt-annotering eller ktor `RequestValidation`:

- **Dekning by default.** En sentral walker treffer automatisk alle `String`-felt, også
  nye. Per-felt-annotering (eller manuell `RequestValidation`-registrering per type) må
  vedlikeholdes for hånd, og et glemt felt = et hull. Kommentarens krav er nettopp «alle
  String-verdier».
- **Testbar med samme mekanisme.** Den samme refleksjonswalken som validerer, kan
  verifiseres av refleksjons-testen — testen og produksjonskoden ser DTO-grafen likt.
- **Tradeoff:** Refleksjon er mindre eksplisitt enn annotering (du ser ikke på DTO-en at
  feltet valideres) og har en liten kjøretidskostnad per request. Vi bøter på det første
  med refleksjons-testen (fanger utilsiktet udekkede felt) og en kort KDoc på validatoren;
  kostnaden er ubetydelig mot ett HTTP-kall.
- **Ingen ny avhengighet.** `kotlin-reflect` er allerede på klassestien; vi trenger ikke
  dra inn `ktor-server-request-validation`.

## Valideringsregel («trygg tekst»)

Vi starter **enkelt**, men bygger regelen slik at det er **lett å legge til flere sjekker på
samme string** senere (Q2). Regelen modelleres derfor som en **liste av navngitte sjekker**
som hver `String` kjøres gjennom — ikke ett monolittisk regex. Å utvide dekningen = å legge
til én sjekk i lista, uten å røre walkeren eller mottakspunktene.

```
fun interface TekstSjekk {
    /** Returnerer null hvis ok, ellers en kort grunn (uten selve verdien). */
    fun sjekk(verdi: String): String?
}
```

Sjekkene kjøres i rekkefølge; første som returnerer en grunn gir avvisning. Startsett:

- `IngenVinkelparenteser` — **avvis** verdier som inneholder `<` eller `>` (blokkerer
  `<script>`, HTML/tag-injeksjon).
- `IngenKontrolltegn` — **avvis** kontrolltegn, unntatt vanlig whitespace (mellomrom, tab,
  linjeskift).
- `IngenForLangTekst` — **avvis** tekst over `MAKS_TEKST_LENGDE` (10 000 tegn); vern mot
  oversized payloads / DoS.
- `IngenUsynligeFormatTegn` — **avvis** usynlige Unicode-formattegn (kategori Cf): bidi-
  overstyringer/isolater (U+202A–202E, U+2066–2069), zero-width-tegn, BOM m.fl. Beskytter mot
  «Trojan Source» og tekstspoofing.

I tillegg sjekker walkeren **samlingsstørrelse**: en `Collection` med flere enn
`MAKS_LISTE_STORRELSE` (100) elementer avvises (vern mot svært store arrays).

Alt annet **tillates**: norske tegn, tall, whitespace og vanlig tegnsetting som brukes i navn,
adresser, e-post og fritekst (`. , - _ @ / ( ) : ; ' + &` osv.).

Vi bruker en **avvis-liste for det farlige** (særlig `< >` og kontrolltegn) framfor en snever
allowlist, fordi feltene er ekte fritekst (begrunnelser, kompetansebeskrivelser) der en for
streng allowlist ville avvist legitim input. Dette er en bevisst avveining: `< >`-blokkering
+ output-encoding (allerede via kotlinx.serialization ved respons, og maskering i logg)
dekker de praktiske angrepsvektorene (XSS/tag-injeksjon) uten å ødelegge brukeropplevelsen.
Flere sjekker (f.eks. `javascript:`, null-byte, unicode-kontrolltegn) legges til som nye
`TekstSjekk`-elementer når behovet melder seg.

Regelen skal ikke trimme eller endre verdien — kun **validere og avvise** (Q3). Sanitering/
normalisering er bevisst utelatt: vi endrer aldri brukerens input, vi avviser den.

Valideringen er **generell** og bryr seg kun om trygg tekst. Formatvalidering av spesifikke
felt (fnr, orgnr o.l.) er eksplisitt **ikke** en del av dette (Q5) — slike `String`-koder
passerer «trygg tekst» som alle andre felt.

## Implementasjonsplan

### 1. Ny valideringsmodul

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/InputValidering.kt`.
Den legges i `infrastruktur/` med en gang fordi den er generell og skal gjenbrukes av
**saksbehandling-API-et** (Q4), ikke bare `soknad`.

- `class UgyldigInputException(feltsti: String) : IllegalArgumentException(...)` — meldingen
  inneholder **feltstien** (f.eks. `behovForBistand.begrunnelse`), **aldri selve verdien**.
- `fun interface TekstSjekk { fun sjekk(verdi: String): String? }` + startsettet av sjekker
  (`IngenVinkelparenteser`, `IngenKontrolltegn`) i en `val standardTekstSjekker: List<TekstSjekk>`.
  Nye sjekker legges til i denne lista (Q2).
- `fun valider(dto: Any, sti: String = "")` — rekursiv refleksjonswalk:
  - For hver `KProperty1` på klassen: les verdien.
  - `String` → kjør alle `standardTekstSjekker`, kast `UgyldigInputException(sti + navn)` ved
    første brudd.
  - `List<*>`/`Collection<*>` → valider hvert element (String-elementer sjekkes, nøstede
    data-klasser rekurseres).
  - Nøstet `@Serializable data class` (DTO-typene) → rekurser.
  - `null`, `enum`, `LocalDate`, tall, `Boolean` → hopp over.
  - Beskytt mot uendelig rekursjon dersom grafen skulle bli syklisk (DTO-grafen er i dag
    asyklisk; en `visited`-sjekk eller dybdegrense er billig forsikring).

### 2. Kall validering på mottakspunktene

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt`.

- I `oppdaterUtkast`: `val oppdatertUtkast = call.receive<DTO.Utkast>().also { valider(it) }`
- I `sendInnSoknad`: `val soknad = call.receive<DTO.Soknad>().also { valider(it) }`

Valideringen skjer **før** tilgangssjekk/persistering, slik at ugyldig input avvises tidlig.

### 3. Map til 400 i StatusPages

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` (`install(StatusPages)`).

Legg til en gren for `UgyldigInputException` **før** den generiske `else`-grenen:

- Svar `400 Bad Request` med en generisk melding som kun oppgir **feltstien**, ikke verdien
  (unngå å reflektere angriperinput tilbake i respons — output-siden av kriteriet).
- Logg med `log.warn` (uten PII/verdien). Ikke `log.error` — ugyldig input er en forventet
  klientfeil, ikke en applikasjonsfeil, og skal ikke trigge alerts.

### 4. Tester

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/infrastruktur/InputValideringTest.kt`
(enhets- og refleksjonstester, ved siden av validatoren) og API-testene i
`soknad/SoknadTest.kt`-stil. Samme testharness som i dag (`kotlin.test`,
`testApplicationWithDatabase`). Ingen nye testbibliotek.

- **Happy path (enhet):** en fullt utfylt `DTO.Soknad`/`DTO.Utkast` med legitim norsk input
  (navn med `æøå`, e-post med `@`, adresse med tall og bindestrek) passerer `valider(...)`.
- **Avvisning per felt (enhet):** for hvert fritekstfelt, sett `"<script>alert(1)</script>"`
  og assert `UgyldigInputException` med riktig feltsti. Dekk også et nøstet `List<String>`-felt.
- **Refleksjons-/statisk-analyse-test (krav, Q1):** en test som via refleksjon
  traverserer hele `DTO`-grafen og asserter at `valider(...)` faktisk besøker **hvert**
  `String`- og `List<String>`-felt (f.eks. ved å bygge en DTO der ett bestemt felt har en
  giftig verdi og verifisere at nettopp det feltet avvises — gjentatt for alle felt walkeren
  finner). Dette feiler automatisk hvis noen legger til et nytt `String`-felt eller en ny
  nøstet DTO som walkeren ikke dekker.
- **Utvidbarhet (enhet, Q2):** en test som legger til en ekstra `TekstSjekk` og verifiserer
  at den slår inn på alle `String`-felt uten andre endringer — dokumenterer at regelen er lett
  å utvide.
- **API-nivå (integrasjon):** `POST`/`PUT` mot utkast/innsending med gift i et fritekstfelt
  gir `400`; gyldig body gir `200/201` som før. Bygger på eksisterende
  `testApplicationWithDatabase`-oppsett i `SoknadTest`.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/InputValidering.kt` | Ny: `valider`, `TekstSjekk`, `standardTekstSjekker`, `UgyldigInputException` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/soknad/Api.kt` | Kall `valider(...)` etter `receive` i `oppdaterUtkast` og `sendInnSoknad` |
| `backend/src/main/kotlin/no/nav/ekspertbistand/Application.kt` | `StatusPages`: map `UgyldigInputException` → `400` |
| `backend/src/test/kotlin/no/nav/ekspertbistand/infrastruktur/InputValideringTest.kt` | Ny: enhets-, refleksjons- og utvidbarhetstester |
| `backend/src/test/kotlin/no/nav/ekspertbistand/soknad/SoknadTest.kt` | Ev. nye API-tester for `400` ved gift input |

## Edge cases og feilmodus

- **`null`-felt (Utkast er delvis utfylt):** hoppes over, ikke feil.
- **Tomme strenger og whitespace:** tillatt av valideringen (dette er ikke en
  påkrevd-felt-sjekk; nødvendig-validering er et separat anliggende).
- **`List<String>` med gift i ett element:** avvises med feltsti + indeks.
- **Legitim input med `&`, `@`, `/`, `+`:** skal passere (dekkes av happy-path-testen for å
  hindre regresjon mot en for streng regel).
- **Nytt `String`-felt lagt til senere uten dekning:** fanges av refleksjons-testen (rød sone-
  garantien).
- **Angriperinput i respons/logg:** verdien ekkoes aldri tilbake i `400`-respons og logges
  aldri; kun feltstien. Logg-maskering finnes allerede (`MaskingAppender` i `Logging.kt`).
- **Ytelse:** én refleksjonswalk per innkommende request på to endepunkt. Ubetydelig mot
  eksisterende I/O (DB, altinn-tilganger). Ingen caching nødvendig.

## Beslutninger

| Beslutning | Valg | Begrunnelse |
|-----------|------|-------------|
| Valideringsmekanisme | Sentral refleksjonsbasert walker | Dekker alle `String`-felt by default; ingen glemte felt |
| Regeltype | Avvis-liste (`< >` + kontrolltegn) | Fritekstfelt tåler ikke snever allowlist; blokkerer de reelle vektorene |
| Ny avhengighet | Nei (`kotlin-reflect` finnes) | Unngår `ktor-server-request-validation` |
| HTTP-status | `400 Bad Request` | Ugyldig klientinput, ikke serverfeil |
| Logging ved brudd | `log.warn`, kun feltsti | Forventet klientfeil; ingen PII/verdi, ingen alert |
| Respons ved brudd | Generisk melding + feltsti, aldri verdien | Output-siden av kriteriet — ikke reflekter angriperinput |
| Refleksjons-test | Krav, ikke bare mulighet | Kommentarens intensjon; hindrer regresjon når DTO-er endres |
| Sanitering | Nei — kun validering | Vi avviser, endrer ikke brukerens input |
| Regelstruktur | Liste av `TekstSjekk` (komponerbar) | Lett å utvide med flere sjekker på samme string (Q2) |
| Plassering | `infrastruktur/` fra start | Generell; skal gjenbrukes av saksbehandling-API-et (Q4) |
| Formatvalidering (fnr/orgnr) | Utenfor scope | Valideringen er generell «trygg tekst», ikke feltformat (Q5) |

## Avklaringer (avklart i review 2026-09-24)

- **Q1 – test som krav:** ✅ Refleksjons-testen er et **krav**, ikke bare en mulighet.
- **Q2 – regelstruktur:** ✅ Start **enkelt** (`< >` + kontrolltegn), men regelen bygges som en
  **liste av `TekstSjekk`** slik at det er lett å legge til flere sjekker på samme string senere.
- **Q3 – validere vs. sanitere:** ✅ **Validere og avvise** (400). Ingen sanitering — vi endrer
  aldri brukerens input.
- **Q4 – plassering/gjenbruk:** ✅ Legges i **`infrastruktur/`** med en gang; skal også brukes av
  **saksbehandling-API-et**.
- **Q5 – felt-format:** ✅ Valideringen er **generell**; vi bryr oss ikke om formatvalidering av
  fnr/orgnr o.l. her.

## Definisjon av ferdig

- [ ] `valider(...)` validerer rekursivt alle `String`- og `List<String>`-felt i DTO-grafen
- [ ] Kalt etter `call.receive` i både `oppdaterUtkast` og `sendInnSoknad`
- [ ] Brudd gir `400 Bad Request` med feltsti, uten å ekko verdien; logges som `warn` uten PII
- [ ] `standardTekstSjekker` (liste av `TekstSjekk`) avviser `< >`, kontrolltegn, for lang tekst
      (>10 000 tegn) og usynlige Unicode-formattegn; walkeren avviser lister med >100 elementer.
      Tillater legitim norsk fritekst/e-post/adresse, og er lett å utvide med flere sjekker
- [ ] Validatoren ligger i `infrastruktur/` og kan gjenbrukes av saksbehandling-API-et
- [ ] Refleksjons-test verifiserer at hvert `String`/`List<String>`-felt i DTO-grafen dekkes,
      og feiler hvis et nytt udekket felt legges til
- [ ] Enhets- og API-tester dekker happy path, avvisning per felt og `400` fra endepunktene,
      med prosjektets eksisterende testharness (ingen nye testbibliotek)
- [ ] Ingen ny runtime-avhengighet lagt til
