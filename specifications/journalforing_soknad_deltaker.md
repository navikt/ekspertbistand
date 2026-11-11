# Journalføring av innsendt søknad på personmappen og arbeidsgivermappen

Trello: https://trello.com/c/khxsoU0N/676-2-journalf%C3%B8ring-av-innsendt-s%C3%B8knad-p%C3%A5-personmappen-arbeidgsivermappa-i-gosys
(kort 676, kort-ID `6ae1d9b0ce5b94e8531a49e6`)

Henger sammen med:

- Kort 695, [tekst på infoskriv](https://trello.com/c/lPVEHuKB/695-kopi-av-s%C3%B8knad-til-deltaker-eksiterende-l%C3%B8sning)
  (`6aeb322036ce8d935963a4aa`). Godkjent tekst til infoskrivet.
- Kort 675, [3. Varsle deltaker om at søknad er sendt inn](https://trello.com/c/2QCwaAJX/675-3-varsle-deltaker-om-at-s%C3%B8knad-er-sendt-inn-i-eksisterande-l%C3%B8sning)
  (`6ae1d89e20829d6aafd08cb6`). Bygger på journalposten dette kortet lager.
- `DokDistClient` på samme branch (`feature/dokdist-klient`). Klienten er ikke i
  bruk ennå. Den tas i bruk i kort 675.

## Mål

I dag journalføres innsendt søknad bare på arbeidsgivermappen i Gosys
(`JournalfoerInnsendtSoknad`, bruker = orgnr). Etter dette kortet:

1. **Arbeidsgivermappen:** Søknaden journalføres som før. Vi har ingen
   funksjonalitet for sladding, så deltakeren skjules ikke i dette kortet.
   `JournalfoerInnsendtSoknad` får en TODO om at navn, fødselsnummer og adresse
   skal skjules når deltakeren har adressebeskyttelse.
2. **Personmappen:** En kopi av søknaden journalføres i tillegg på deltakeren
   (bruker = fødselsnummer). PDF-en starter med infoskrivet fra kort 695, og
   søknaden følger etter. Denne PDF-en skjules ikke, heller ikke når deltakeren
   har adressebeskyttelse.

Journalposten på personmappen er utgående og ferdigstilt, slik at kort 675 kan
distribuere den til deltakeren med dokdist uten å journalføre på nytt.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 676, beskrivelse | Søknaden skal journalføres på personmappen i tillegg til arbeidsgivermappen, fordi vi skal varsle deltakeren om at søknaden er sendt inn. |
| Kort 676, beskrivelse | Arbeidsgivermappen: kopi av søknaden. Har deltakeren adressebeskyttelse, skal alt som identifiserer deltakeren (navn, fødselsnummer, «eventuelt annet??») anonymiseres. Team Tiltak har en løsning for sladding, «hør med dem». |
| Kort 676, beskrivelse | Personmappen: kopi av søknaden og infoskrivet til deltakeren. Det kan være samme fil. Ingen anonymisering ved adressebeskyttelse. |
| Kort 676, kommentar fra Hanna, 2026-11-10 | Teksten til infoskrivet er godkjent og ligger på kort 695. |
| Kort 695, kommentar fra Ida, 2026-11-10 | Ida og Cecilie har lagt inn endringer. Teksten kan brukes slik den står nå i kortbeskrivelsen. |
| Anders, 2026-11-10 | Har deltakeren adressebeskyttelse (alle graderinger utenom ugradert), skal navn, fødselsnummer og adresse skjules på arbeidsgivermappen. Finnes det sladding fra før, brukes den. Ellers legges det inn en TODO i `JournalfoerInnsendtSoknad`. Vi lager ikke ny funksjonalitet for sladding i dette kortet. |
| Anders, 2026-11-10 | Kodebasen har ingen sladding av dokumenter. Den eneste maskeringen er for statistikkmeldinger (`Meldingsinnhold.maskert()`) og logger. Derfor blir det en TODO. |
| Anders, 2026-11-10 | Journalposten på personmappen er `UTGAAENDE`. |
| Anders, 2026-11-10 | Infoskriv og søknad er én PDF. |
| Anders, 2026-11-10 | Kortet kan prodsettes før kort 675. Det er greit at utgående journalposter ligger udistribuert på personmappen til da. |
| Anders, 2026-11-10 | Kopien bruker samme brevkode som søknaden (`nav_tiltak_ekspertbistand`). |

Kort 676 har ingen sjekklister eller vedlegg.

## Avgrensning

- Distribusjon av journalposten til deltakeren (dokdist) og varsling. Det er
  kort 675.
- Søknader som er sendt inn før dette er i prod. Vi journalfører ikke på nytt
  bakover i tid.
- Søknader fra Altinn (`*KildeAltinn`). Vi har ikke søknaden der, bare tilsagnet.
- Andre dokumenter som journalføres på arbeidsgivermappen (tilskuddsbrev,
  vedtaksbrev, Arena-notat).
- Sladding av søknaden på arbeidsgivermappen ved adressebeskyttelse. Vi har ingen
  sladding, og vi lager den ikke her. Det blir en TODO.

## Tilnærming

🔴 Rød sone: personopplysninger. Hvem som får journalposten på personmappen, og
at den ikke havner hos feil bruker, må kontrolleres manuelt i review.

### 1. `DokArkivClient`: bruker

I dag er bruker alltid `ORGNR`. Det endres slik:

- `opprettOgFerdigstillJournalpost` tar `bruker: Bruker` i stedet for
  `virksomhetsnummer: String`. `Bruker` blir offentlig med `Bruker.orgnr(...)` og
  `Bruker.fnr(...)`, på samme måte som `AvsenderMottaker`.
- `AvsenderMottaker` får `AvsenderMottaker.fnr(...)` (`idType = "FNR"`).
- `kanal` er fortsatt `NAV_NO` for alle journalposter (Anders, 2026-11-10).
- De fire kallene som finnes nå, endres til `Bruker.orgnr(...)` og oppfører seg
  likt.

### 2. Arbeidsgivermappen: TODO om sladding

`JournalfoerInnsendtSoknad` endres ikke, bortsett fra en TODO i KDoc-en:

```kotlin
// TODO: Har deltakeren adressebeskyttelse (alle graderinger utenom UGRADERT), skal navn,
//  fødselsnummer og adresse til deltakeren skjules i søknaden som journalføres på
//  arbeidsgivermappen. Vi har ingen funksjonalitet for sladding ennå. Team Tiltak har en
//  løsning for dette. Se https://trello.com/c/khxsoU0N
```

Søknaden har i dag ikke deltakerens adresse, bare beliggenhetsadressen til
virksomheten. Adressen kommer med i TODO-en, slik at den ikke glemmes hvis den
legges til senere.

### 3. Personmappen: ny mal og ny handler

**Mal `soknadKopiDeltaker`:**

- Side 1 er infoskrivet. Hodet gjenbruker `felles/vedtakHodeDeltaker`
  («Unntatt offentlighet», «Til {navn}» og dato). Teksten er fra kort 695,
  skrevet om til HTML. Lenkene skrives som faste `https://www.nav.no/...`-URL-er,
  fordi `DokumentmalTest` bare tillater `https://` (kortet har `http://`).
- Sideskift, deretter søknaden.
- Søknadsinnholdet i `soknad/template.hbs` flyttes til partialen
  `felles/soknadInnhold.hbs`. Både `soknad` og `soknadKopiDeltaker` bruker den,
  slik at kopien alltid er lik søknaden. Snapshot-testen for `soknad` skal ikke
  endre seg.

**`DokumentService.genererSoknadKopiDeltakerPdf(soknad)`:** samme payload som
`genererSoknadPdf`, pluss `deltaker.navn` og `dato`.

**Ny handler `JournalfoerSoknadKopiDeltaker`** på `EventData.SoknadInnsendt`.
Den er en egen handler, slik at den prøves på nytt uavhengig av journalføringen
på arbeidsgivermappen.

| Felt | Verdi |
|------|-------|
| `journalposttype` | `UTGAAENDE` |
| `bruker` | `Bruker.fnr(soknad.ansatt.fnr)` |
| `avsenderMottaker` | `AvsenderMottaker.fnr(soknad.ansatt.fnr)` |
| `tittel` | «Kopi av søknad om tilskudd til ekspertbistand» |
| `sak` | Samme `FagSak` som søknaden (`FagsakIdService`) |
| `eksternReferanseId` | `"$soknadId-kopi-deltaker"`. Duplikatkontrollen i dokarkiv krever en annen verdi enn journalposten på arbeidsgivermappen. |
| `tema`, `behandlingstema`, `journalfoerendeEnhet`, `brevkode` | Som i dag (`TIL`, `ab0423`, `9999`, `nav_tiltak_ekspertbistand`) |
| `kanal` | `NAV_NO`, som de andre journalpostene |

Etter journalføringen publiseres en ny event:

```kotlin
data class SoknadKopiDeltakerJournalfoert(
    val soknad: DTO.Soknad,
    val journalpostId: Int,
) : EventData
```

Ingen handler lytter på den i dette kortet. Kort 675 kobler distribusjonen på
den. Handleren bruker `IdempotencyGuard` som `JournalfoerInnsendtSoknad`. Svarer
dokarkiv 409 ved et nytt forsøk, brukes journalposten som finnes fra før.

### 4. Oppsett og dokumentasjon

- `Events.kt`: registrer handleren og dokumenter den nye eventen.
- `event/event-flow.md`: ny handler og ny event.
- `LocalApplication`: ingen endring. Mocken for `DokArkivClient` svarer likt for
  begge journalpostene.

### 5. Tester

- `DokArkivClientTest`: `Bruker.fnr`, og `AvsenderMottaker.fnr`.
- `JournalfoerInnsendtSkjemaTest`: oppdateres til `Bruker.orgnr`, ellers uendret.
- Ny `JournalfoerSoknadKopiDeltakerTest`: riktige felt mot dokarkiv, event
  publiseres, idempotens og 409.
- `DokumentmalTest`: testdata for `soknadKopiDeltaker`, og uendret snapshot
  for `soknad`.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/.../dokarkiv/DokArkivClient.kt` | `Bruker` offentlig, `AvsenderMottaker.fnr` |
| `backend/.../event/handlers/JournalfoerInnsendtSoknad.kt` | `Bruker.orgnr(...)` og TODO om sladding |
| `backend/.../event/handlers/JournalfoerSoknadKopiDeltaker.kt` | Ny |
| `backend/.../event/handlers/JournalfoerTilskuddsbrev.kt`, `JournalfoerTilskuddsbrevKildeAltinn.kt`, `JournalfoerNotatArenaSakOpprettet.kt` | `Bruker.orgnr(...)` |
| `backend/.../event/Events.kt` | Ny event, registrere handleren |
| `backend/.../event/event-flow.md` | Ny handler og event |
| `backend/.../dokument/DokumentService.kt` | `genererSoknadKopiDeltakerPdf` |
| `backend/src/main/resources/dokumentmaler/templates/felles/soknadInnhold.hbs` | Ny partial, flyttet fra `soknad` |
| `backend/src/main/resources/dokumentmaler/templates/soknad/template.hbs` | Bruker partialen |
| `backend/src/main/resources/dokumentmaler/templates/soknadKopiDeltaker/` | Ny mal, `schema.json` og `testdata/` |
| `backend/src/test/...` | Tester over |

## Kanttilfeller og feilmodus

- **Deltakeren finnes ikke i PDL.** Dokarkiv kan avvise et ukjent
  fødselsnummer på personmappen. Da gir handleren transient feil og prøver på nytt, og feilen må
  følges opp manuelt. Etter kort 601 er deltakeren slått opp i PDL før
  innsending, så dette skal ikke skje for nye søknader.
- **Deltaker med adressebeskyttelse.** Søknaden på arbeidsgivermappen viser
  navn og fødselsnummer, som i dag, til sladding er på plass (TODO). Kopien på
  personmappen skal ikke skjules.
- **Nytt forsøk etter at journalposten er opprettet.** Dokarkiv svarer 409 med
  den journalposten som finnes fra før. Det håndteres i `DokArkivClient` i dag.
- **Den ene journalføringen feiler, den andre lykkes.** Handlerne er
  uavhengige. Saken opprettes fortsatt bare på
  `InnsendtSoknadJournalfoert` (arbeidsgivermappen), som i dag.
- **Journalpost på personmappen som ikke distribueres.** Fram til kort 675 er i
  prod, ligger journalposten i Gosys som utgående og ikke distribuert. Det er
  avklart (Anders, 2026-11-10).

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] Søknaden journalføres på arbeidsgivermappen som før, med en TODO om sladding ved adressebeskyttelse
- [ ] Kopien av søknaden med infoskrivet journalføres som utgående og ferdigstilt på personmappen til deltakeren, uten at noe er skjult
- [ ] `SoknadKopiDeltakerJournalfoert` publiseres med journalpostId
- [ ] Begge handlerne er idempotente og tåler 409 fra dokarkiv
- [ ] Infoskrivet har teksten fra kort 695 og bare `https://`-lenker
- [ ] Snapshot for `soknad` er uendret, og `soknadKopiDeltaker` har testdata
- [ ] `event-flow.md` er oppdatert
- [ ] Backend-testene er grønne
