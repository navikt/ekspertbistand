# Sladd deltaker i dokumenter som journalføres på arbeidsgivermappa ved kode 6 og 7

Trello: https://trello.com/c/NBU7M75w/703-sladde-deltakers-navn-ved-jorunalf%C3%B8ring-mot-arbeidsgiver-ved-kode-6-7
(kort 703, kort-ID `6af42511ac749200fcdf8910`)

Henger sammen med:

- `journalfor_vedtaksbrev_arbeidsgiver.md`. Vedtaksbrevet til arbeidsgiver
  sladder allerede deltakeren med `sladdVedAdressebeskyttelse` i `Vedtaksbrev.kt`.
  Dette kortet gjør det samme for de andre dokumentene på arbeidsgivermappa.
- `journalforing_soknad_deltaker.md` (kort 676). Der ble sladding av søknaden
  utsatt.

## Mål

Arbeidsgivermappa i Gosys og Joark ligger på orgnr, så tilgangskontrollen på
person beskytter ikke deltakeren der. Har deltakeren adressebeskyttelse
(fortrolig eller strengt fortrolig), skal deltakerens navn og fødselsnummer
sladdes med `***********` i disse dokumentene:

1. **Søknaden** (`JournalfoerInnsendtSoknad`).
2. **Tilskuddsbrevet fra Arena** (`JournalfoerTilskuddsbrev` og
   `JournalfoerTilskuddsbrevKildeAltinn`).

Bare den journalførte PDF-en sladdes. Data i eventene og det arbeidsgiver ser i
vår løsning, er usladdet.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 703 | Tittel: «Sladde deltakers navn ved journalføring mot arbeidsgiver ved kode 6/7». Kortet har ingen beskrivelse, kommentarer, sjekklister eller vedlegg. |
| Anders, 2026-11-13 | Gjelder søknaden og tilskuddsbrevet, begge Arena-variantene. Vedtaksbrevet er sladdet fra før. |
| Anders, 2026-11-13 | Navn og fødselsnummer sladdes, som Team Tiltak gjør. |
| Anders, 2026-11-13 | `BehandlendeEnhetUtleder` returnerer adressebeskyttelsen i `Ruting`, slik at søknaden ikke trenger et ekstra kall til PDL. |
| Anders, 2026-11-10 (kort 676) | Har deltakeren adressebeskyttelse, skal navn, fødselsnummer og adresse skjules på arbeidsgivermappa. Finnes det sladding fra før, brukes den. |
| Ken, 2026-11-01 og 2026-11-02, ROS 2106 - 28079 (fra `journalfor_vedtaksbrev_arbeidsgiver.md`) | Deltaker sladdes i journalførte brev ved kode 6 og 7 med `***********`, som Team Tiltak. Skjerming alene gir ingen sladding. |

## Avgrensning

- **Vedtaksbrevet** er sladdet fra før og endres ikke.
- **Arena-notatet** (`JournalfoerNotatArenaSakOpprettet`) har bare saksnummer og
  tiltaksgjennomføring-ID, ingen opplysninger om deltakeren.
- **Kopien av søknaden på personmappen** (`JournalfoerSoknadKopiDeltaker`) skal
  ikke sladdes (kort 676).
- **Adresse.** Ingen av malene viser deltakerens adresse. Søknaden har den ikke.
  `TilsagnData.Deltaker` har adressefelt, men tilskuddsbrevet viser dem ikke.
- **Fritekst** i søknaden, for eksempel «Beskriv den ansattes arbeidssituasjon»,
  kan identifisere deltakeren. Den sladdes ikke. Det må eventuelt løses i et eget kort.
- **Journalposter som finnes fra før.** Vi sladder ikke bakover i tid.
- **Skjerming** (egne ansatte) gir ingen sladding.

## Tilnærming

🔴 Rød sone: personopplysninger og adressebeskyttelse. At sladdingen faktisk
skjer, og at feil mot PDL ikke gir usladdet journalpost, må kontrolleres manuelt i review.

### Adressebeskyttelse

Graderingen er av typen `Adressebeskyttelse?` (`FORTROLIG`, `STRENGT_FORTROLIG`
eller null), og hentes slik:

- **Søknaden:** `BehandlendeEnhetUtleder` henter allerede graderingen fra PDL for
  å rute saken. `Ruting` får et nytt felt, `adressebeskyttelse: Adressebeskyttelse?`,
  som settes med `hoyesteGradering().tilAdressebeskyttelse()`. Er personen ikke i
  PDL, blir feltet null. `JournalfoerInnsendtSoknad` bruker feltet, så det blir
  ikke noe nytt kall til PDL, og sladdingen bygger på samme gradering som
  rutingen (Anders, 2026-11-13).
- **Tilskuddsbrevet:** Begge handlerne bruker `GraderingOppslag`, som finnes fra
  før og brukes i `OpprettSak`.

Feil mot PDL gir `transientError` i alle tre handlerne. Vi journalfører altså aldri
når graderingen er ukjent. En person som ikke finnes i PDL, regnes som ugradert, som i
rutingen.

### Sladding

Konstanten `SLADDET_NAVN` (`***********`) i `Vedtaksbrev.kt` gjenbrukes for både
navn og fødselsnummer.

**Søknaden:** `DokumentService.genererSoknadPdf(soknad, adressebeskyttelse: Adressebeskyttelse? = null)`.
Ved `FORTROLIG` eller `STRENGT_FORTROLIG` blir `ansatt.navn` og `ansatt.fnr` i
`SoknadRequest` erstattet med `***********`. `genererSoknadKopiDeltakerPdf`
endres ikke.

**Tilskuddsbrevet:** `DokumentService.genererTilskuddsbrevPdf(tilsagnData, adressebeskyttelse: Adressebeskyttelse? = null)`.
Ved `FORTROLIG` eller `STRENGT_FORTROLIG` blir `deltaker.fornavn` og
`deltaker.fodselsnr` `***********`, og `deltaker.etternavn` blir tom. Malen viser
da `*********** (***********)`. `genererTilskuddsbrevHtml`, som arbeidsgiver ser,
endres ikke.

### Handlerne

| Handler | Fødselsnummer til oppslag | Endring |
|---------|---------------------------|---------|
| `JournalfoerInnsendtSoknad` | Ingen eget oppslag | Bruker `ruting.adressebeskyttelse` når PDF-en genereres. |
| `JournalfoerTilskuddsbrev` | `tilsagnData.deltaker?.fodselsnr` | Ny avhengighet `GraderingOppslag`. Slår opp før PDF-en genereres. |
| `JournalfoerTilskuddsbrevKildeAltinn` | `tilsagnData.deltaker?.fodselsnr` | Som over. |

Mangler `tilsagnData.deltaker`, viser ikke tilskuddsbrevet noen deltaker. Da gjøres ingen
oppslag.

Eventene `InnsendtSoknadJournalfoert`, `TilskuddsbrevJournalfoert` og
`TilskuddsbrevJournalfoertKildeAltinn` endres ikke, og har fortsatt usladdede
data.

`GraderingOppslag` er registrert i DI i `Application` og `LocalApplication` fra
før. Det trengs ingen endring i oppsettet.

### Tester

- `DokumentService` eller `DokumentmalTest`: søknad og tilskuddsbrev sladdes ved
  `FORTROLIG` og `STRENGT_FORTROLIG`, ikke ved null. Kopien til deltaker sladdes aldri.
- `BehandlendeEnhetUtleder`-testene: `adressebeskyttelse` i `Ruting` for
  ugradert, fortrolig, strengt fortrolig, strengt fortrolig utland og person
  som ikke finnes i PDL.
- `JournalfoerInnsendtSkjemaTest`, `JournalfoerTilskuddsbrevTest` og
  `JournalfoerTilskuddsbrevKildeAltinnTest`:
  - Med adressebeskyttelse inneholder PDF-en til dokarkiv verken navn eller fødselsnummer.
  - Uten adressebeskyttelse er PDF-en uendret.
  - Feil fra PDL gir `TransientError` og ingen journalføring.
  - Tilskuddsbrev uten deltaker slår ikke opp i PDL.

## Delproblemer

1. Søknaden: `adressebeskyttelse` i `Ruting`, sladding i `DokumentService` og
   sladdet PDF i `JournalfoerInnsendtSoknad`, med tester.
2. Tilskuddsbrevet: sladding i `DokumentService` og oppslag i begge
   tilskuddsbrev-handlerne, med tester.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/.../dokument/DokumentService.kt` | `adressebeskyttelse`-parameter og sladding for søknad og tilskuddsbrev |
| `backend/.../norg/BehandlendeEnhetUtleder.kt` | `adressebeskyttelse` i `Ruting` |
| `backend/.../event/handlers/JournalfoerInnsendtSoknad.kt` | Sladdet PDF med `ruting.adressebeskyttelse` |
| `backend/.../event/handlers/JournalfoerTilskuddsbrev.kt` | `GraderingOppslag`, sladdet PDF |
| `backend/.../event/handlers/JournalfoerTilskuddsbrevKildeAltinn.kt` | `GraderingOppslag`, sladdet PDF |
| `backend/src/test/...` | Tester over |

## Kanttilfeller og feilmodus

- **PDL er nede.** Handleren svarer `transientError` og prøver på nytt.
  Journalføringen blir forsinket, men det journalføres aldri usladdet ved ukjent gradering.
- **Deltakeren finnes ikke i PDL.** Regnes som ugradert, som i rutingen og i `OpprettSak`.
- **Graderingen endres etter innsending.** Vi bruker graderingen på
  journalføringstidspunktet. Journalposter som finnes fra før, endres ikke.
- **Strengt fortrolig utland** regnes som strengt fortrolig (`tilAdressebeskyttelse`).

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Søknaden på arbeidsgivermappa har sladdet navn og fødselsnummer når deltakeren har kode 6 eller 7
- [ ] Tilskuddsbrevet på arbeidsgivermappa har sladdet navn og fødselsnummer når deltakeren har kode 6 eller 7, i begge Arena-variantene
- [ ] Uten adressebeskyttelse er dokumentene uendret
- [ ] Feil mot PDL gir transient feil, ikke en usladdet journalpost
- [ ] Kopien til deltaker, eventdata og det arbeidsgiver ser i vår løsning er usladdet
- [ ] Backend-testene er grønne
