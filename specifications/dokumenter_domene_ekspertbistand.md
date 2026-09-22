# Dokumenter domenet: hva ekspertbistand er og hva løsningen skal løse

Trello: https://trello.com/c/rvrSzUKi/600-dokumenter-hva-ekspertbistand-er-og-hva-l%C3%B8sningen-skal-l%C3%B8se-i-repoet-for-agenter-og-mennesker
Kort-ID: `6aabb0f6ff8a60048fb5c565`

## Mål

Skrive en domeneoversikt som forklarer **hva ekspertbistand er** og **hva
løsningen skal løse**, skrevet slik at både mennesker (nye teammedlemmer, fagfolk)
og AI-agenter kan lese seg opp raskt. Oversikten **erstatter dagens `README.md`**
på rot og blir repoets inngang: den gir felles domenekontekst før man leser koden,
med pekere videre til teknisk dokumentasjon (`frontend/README.md`,
`backend/README.md`, `ADR.md`, `specifications/`).

## Bakgrunn

Repoet har i dag teknisk orientert dokumentasjon: `README.md` (frontend/backend,
Altinn 2-utfasing), `ADR.md` (asynkron kø) og 11 filer under `specifications/`
(datamodell, ruter, integrasjoner). Det finnes ingen samlet, ikke-teknisk
domenebeskrivelse som svarer på «hva er ekspertbistand og hvorfor bygger vi
dette». Det finnes heller ingen `AGENTS.md` / `.github/copilot-instructions.md`,
så agenter har ingen kort inngang til domenet.

Kortet ber eksplisitt om at dokumentasjonen skal treffe **både agenter og
mennesker**.

## Beslutninger fra kortet

Kortet har **ingen kommentarer og ingen sjekklister** (sjekket 2026-09-22).
Beslutningsgrunnlaget er derfor kun kortbeskrivelsen. Alt innhold under er utledet
fra den; punkter som ikke er avklart i kortet er markert som **åpne spørsmål**.

Kortbeskrivelsen ber om at dokumentet dekker:

- **Hva er ekspertbistand?** Kort forklaring av ordningen.
- **Hvilket problem løser vi?** Hva arbeidsgiver, arbeidstaker og NAV trenger.
- **Hva gjør løsningen?** Fra søknad til saksbehandling, vedtak, refusjon og
  sluttrapport.
- **Aktører og begreper**: arbeidsgiver, deltaker, saksbehandler, beslutter,
  ekspert m.fl.
- **Behandlingens livsløp**: hvor personopplysninger innhentes fra, hvordan de
  flyter under behandling, og om/hvor de sendes videre i Nav eller til eksterne.

Kortet oppgir også faste fakta som skal inn i dokumentet:

- **Systemets formål:** legge til rette for saksbehandling av søknader og
  refusjonskrav for tilskudd til ekspertbistand. Skjer i dag i Arena; systemet
  overtar når Arena fases ut.
- **Systembeskrivelse:** saksbehandlingssystemet tar imot søknader fra
  arbeidsgiver. Saker fordeles på virksomhetens organisasjonsnummer. To roller:
  **saksbehandler** og **beslutter**. Systemet støtter vilkårsvurdering. Vedtak
  gis til arbeidsgiver og deltaker; avslag på refusjonssøknad gis til
  arbeidsgiver. Journalføring av inn- og utgående dokumenter skjer i **Joark**.
  Utbetaling skjer via integrasjon mot **OEBS** gjennom Team **VALP**s API-er, til
  kontonummer arbeidsgiver har oppgitt i kontoregister.
- Referanse: https://www.nav.no/ekspertbistand

## Beslutninger fra review

Bekreftet av Ken Gullaksen 2026-09-22:

1. **Plassering:** domeneoversikten **erstatter `README.md`** på rot (ikke et nytt
   dokument under `docs/`). Nåværende README-innhold (Altinn 2-utfasing, ny
   løsning, frontend/backend) tas inn eller erstattes av den nye disposisjonen,
   og pekerne til `frontend/README.md` og `backend/README.md` beholdes.
2. **Ingen `AGENTS.md`** i denne oppgaven. «For agenter» betyr kun at domenet skal
   være lett å forstå for en agent som leser README — ikke egne agent-instrukser.
3. **Dataflyt tegnes som Mermaid-diagram** i README.

## Leveranse

`README.md` på rot skrives om til en domeneoversikt, disponert etter bolkene i
kortbeskrivelsen:

1. **Hva er ekspertbistand** — ordningen forklart kort og klart (kilde:
   nav.no/ekspertbistand + kortbeskrivelsen).
2. **Problemet vi løser** — behovene til arbeidsgiver, arbeidstaker/deltaker og
   NAV; hvorfor Arena/Altinn 2 fases ut.
3. **Hva løsningen gjør** — flyten fra innsendt søknad → fordeling på
   organisasjonsnummer → vilkårsvurdering → to-trinns vedtak
   (saksbehandler/beslutter) → refusjon → sluttrapport.
4. **Aktører og begreper** — kort ordliste: arbeidsgiver, deltaker/arbeidstaker,
   ekspert, saksbehandler, beslutter, sak vs. søknad, refusjon, sluttrapport,
   vilkårsvurdering.
5. **Behandlingens livsløp / dataflyt** — **Mermaid-diagram** som viser hvor
   opplysninger innhentes, hvordan de flyter internt, og hvor de sendes videre:
   Joark (journalføring), OEBS via VALP (utbetaling), kontoregister (kontonummer).
   Beskrives på aktør/system-nivå, uten å liste konkrete personopplysningsfelter
   fra koden.
6. **Applikasjonene og videre lesing** — kort om frontend (arbeidsgiver) og
   backend, med pekere til `frontend/README.md`, `backend/README.md`, `ADR.md` og
   relevante `specifications/`.

Dokumentet skrives på **norsk**, i klarspråk, og holdes teknologi-nøytralt i
domenedelen (ingen klassenavn, filstier eller kolonnenavn) slik at det ikke råtner
når koden endres.

## Filer som berøres

- **Endret:** `README.md` på rot skrives om til domeneoversikten (erstatter
  dagens innhold, beholder pekere til frontend/backend-READMEs). Ingen annen kode
  berøres.

## Ferdig når

- `README.md` dekker alle seks bolkene over og de faste fakta fra kortet
  (formål, systembeskrivelse, aktører, Joark/OEBS/VALP/kontoregister).
- Dataflyten er tegnet som et Mermaid-diagram.
- Dokumentet er lesbart for både menneske og agent: klarspråk, ingen
  kodedetaljer i domenedelen, med pekere til øvrig dokumentasjon
  (`frontend/README.md`, `backend/README.md`, `ADR.md`, `specifications/`).
- Ingen personopplysninger eller NAV-interne detaljer utover det som allerede står
  åpent i kortet/på nav.no.
