# Varsle deltaker om at søknaden er sendt inn

Trello: https://trello.com/c/2QCwaAJX/675-3-varsle-deltaker-om-at-s%C3%B8knad-er-sendt-inn-i-eksisterande-l%C3%B8sning
(kort 675, kort-ID `6ae1d89e20829d6aafd08cb6`)

Henger sammen med:

- Kort 599, [Varsle deltaker om vedtak i ny løsning](https://trello.com/c/QA5bXVrf/599-varsle-deltaker-om-vedtak-i-ny-l%C3%B8sning)
  (`6aab96635bae2266d1c1396f`). Beslutningen om å bruke dokdist er tatt her.
- Kort 676 og spec-en [journalforing_soknad_deltaker.md](journalforing_soknad_deltaker.md).
  Lager journalposten som dette kortet distribuerer (`SoknadKopiDeltakerJournalfoert`).
- Kort 601, [Sjekke om deltaker jobber i virksomheten](https://trello.com/c/3Oq7KiFc)
  (`6aabde383d44a078348cecb8`). Deltakeren er bekreftet mot Aa-reg før innsending.
- `DokDistClient` på samme branch (`feature/dokdist-klient`). Klienten finnes, men er
  ikke koblet inn i appen ennå.

## Mål

Når arbeidsgiveren sender inn søknaden, skal deltakeren få kopien av søknaden med
infoskriv. Journalposten fra kort 676 distribueres med dokdist. Dokdist velger
kanal ut fra KRR (digitalt eller papirbrev), og dokumentet vises under
«Dine saker» på nav.no.

Søknadsskjemaet sier fra til arbeidsgiveren at den ansatte får en kopi av
søknaden.

## Beslutninger og innspill

| Kilde | Innhold |
|-------|---------|
| Kort 599, kommentar fra Ken, 2026-10-25 | Vi varsler deltakeren med dokdist (`distribuerJournalpost`), slik Team Tiltak gjør for vedtak. Vi bruker ikke brukernotifikasjoner på Min side. |
| Kort 599, kommentar fra Ken, 2026-10-25 | Varsel ved innsending kan gå i prod før go-live, i dagens Arena-flyt. Det krever at deltakeren er bekreftet mot Aa-reg (kort 601). |
| Kort 599, kommentar fra Ken, 2026-10-25 | I søknadsskjemaet får arbeidsgiveren beskjed om at deltakeren får kopi av søknaden. Teksten må i prod samtidig med varselet. |
| Kort 599, kommentar fra Ken, 2026-10-25 | Handleren distribuerer når journalposten er ferdigstilt, med distribusjonstidspunkt `KJERNETID`, og lagrer bestillingsId. Svarer dokdist 409, er journalposten allerede distribuert. Det regnes som suksess, så et nytt forsøk gir ikke dobbel utsending. |
| Kort 599, kommentar fra Ken, 2026-10-25 | Dokdist med Azure-token i systemkontekst, etter samme mønster som dokarkiv-klienten. |
| Kort 599, sjekkliste | Avklart: deltakere som har reservert seg mot digital kommunikasjon, får brevet i posten. Dokdist håndterer det. |
| Kort 675, sjekkliste | Tre tekstendringer i søknadsskjemaet. Se avsnitt 3. |
| Anders, 2026-11-10 | Distribusjonstypen for kopien er `ANNET`. |
| Anders, 2026-11-10 | `bestillendeFagsystem` og `dokumentProdApp` er fortsatt `EKSPERTBISTAND`. |
| Anders, 2026-11-10 | Tekstendringene tas både i v1 (`pages/`) og v2 (`v2/pages/`) av skjemaet. |
| Anders, 2026-11-10 | Ekspertbistand har ennå ikke tilgang til dokdistfordeling i prod. Handleren registreres bare i dev og lokalt. Prod slås på senere. |
| Anders, 2026-11-10 | Vi sender ikke navn på mottakeren. Dokdist krever `avsenderMottaker.navn` ([`JournalpostValidator`](https://github.com/navikt/dokdistfordeling/blob/master/rdist002/src/main/java/no/nav/dokdistfordeling/validate/JournalpostValidator.java#L46)), men dokarkiv henter navnet fra PDL når `idType` er `FNR` ([`AvsenderMottaker`](https://github.com/navikt/dokarkiv/blob/master/journalpost/src/main/java/no/nav/dokarkiv/journalpost/v1/api/AvsenderMottaker.java)). |

## Avgrensning

- Varsel ved vedtak (kort 599, punkt 2). Det kommer når ny saksbehandling er live.
- Å slå på distribusjon i prod. Det krever tilgang fra Team Dokumentløsninger
  (`ENTRA_ID_PREAUTH` i dokdistfordeling).
- Nytt varsel når brevet ikke er åpnet. Se åpne spørsmål.
- Søknader som er sendt inn før dette. Vi distribuerer ikke bakover i tid.

## Tilnærming

🔴 Rød sone: personopplysninger. At brevet går til riktig person og bare én gang,
må kontrolleres manuelt i review.

### 1. `DokDistClient` inn i appen

- `Application.kt`: `provide(DokDistClient::class)`.
- `LocalApplication`: en `DokDistClient` med `MockEngine` som svarer
  `{"bestillingsId": "..."}`, som for `DokArkivClient`.
- `nais/dev-gcp-backend.yaml`: utgående til `dokdistfordeling.dev-fss-pub.nais.io`.
  Prod får ikke utgående trafikk før distribusjon slås på der.

### 2. Ny handler `DistribuerSoknadKopiDeltaker`

Lytter på `EventData.SoknadKopiDeltakerJournalfoert`.

| Felt | Verdi |
|------|-------|
| `journalpostId` | `event.data.journalpostId` |
| `distribusjonstype` | `ANNET` |
| `distribusjonstidspunkt` | `KJERNETID` |
| `bestillendeFagsystem`, `dokumentProdApp` | `EKSPERTBISTAND` (som i klienten i dag) |

Etter distribusjonen publiseres en ny event, slik at bestillingsId blir lagret i
eventloggen:

```kotlin
data class SoknadKopiDeltakerDistribuert(
    val soknad: DTO.Soknad,
    val journalpostId: Int,
    val bestillingsId: String,
) : EventData
```

Ingen handler lytter på den. Den vises derfor rødt i `event-flow.md`, slik
`SoknadKopiDeltakerJournalfoert` gjør i dag.

- `IdempotencyGuard`, som de andre handlerne.
- 409 fra dokdist håndteres av `DokDistClient` og gir suksess med bestillingsId
  fra svaret.
- Andre feil fra dokdist gir transient feil og nytt forsøk.
- I `Events.kt` registreres handleren i `basedOnEnvSuspending(other = { ... })`.
  Den kjører da i dev og lokalt, men ikke i prod. En kommentar sier at den flyttes
  ut når tilgangen i prod er på plass.

### 3. Tekst i søknadsskjemaet

Fra sjekklisten på kort 675. Endres likt i `frontend/client/src/pages/` og
`frontend/client/src/v2/pages/`.

| Sted | Fil | Ny tekst |
|------|-----|----------|
| «Før du søker», 2. kulepunkt | `SoknadPage.tsx` | Du, den ansatte og Nav er enige om at det er hensiktsmessig med ekspertbistand. Den ansatte får kopi av søknaden du sender inn. |
| Steg 3, varselet over «Behov for bistand» | `SkjemaSteg3Page.tsx` | Overskrift: «Unngå sensitive personopplysninger». Tekst: «For eksempel arbeidstakerens diagnose eller sensitive opplysninger om arbeidstakerens familie eller andre ansatte. Husk at den ansatte får en kopi av søknaden når du sender den inn.» |
| Oppsummeringen, nederst | `OppsummeringPage.tsx` | Ny `BodyLong` mellom oppsummeringen og knappene: «Når du sender inn søknaden, får den ansatte også en kopi av søknaden. Du kommer til en kvitteringsside med informasjon om veien videre.» |

Teksten i oppsummeringen har fått et komma etter «søknaden», ellers er den lik
sjekklisten.

### 4. Oppsett og dokumentasjon

- `Events.kt`: ny event med dokumentasjon, registrere handleren, oppdatere
  konsumentlisten til `SoknadKopiDeltakerJournalfoert`.
- `event/event-flow.md`: genereres på nytt med `EventFlowDiagram`.

### 5. Tester

- Ny `DistribuerSoknadKopiDeltakerTest`: riktige felt mot dokdist, at eventen
  publiseres med bestillingsId, idempotens, 409 og transient feil.
- `TestEventData` og `EventDataAggregateRootIdTest`: eksempel på den nye eventen.
- Frontend: eksisterende tester er grønne. Ingen nye tester for statisk tekst.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `backend/.../event/handlers/DistribuerSoknadKopiDeltaker.kt` | Ny |
| `backend/.../event/Events.kt` | Ny event, registrere handleren |
| `backend/.../event/event-flow.md` | Generert på nytt |
| `backend/.../Application.kt` | `provide(DokDistClient::class)` |
| `backend/src/test/.../LocalApplication.kt` | Mock av `DokDistClient` |
| `nais/dev-gcp-backend.yaml` | Utgående til dokdistfordeling |
| `frontend/client/src/{pages,v2/pages}/{SoknadPage,SkjemaSteg3Page,OppsummeringPage}.tsx` | Tekst |
| `backend/src/test/...` | Tester over |

## Kanttilfeller og feilmodus

- **Ingen tilgang til dokdist.** Dokdist svarer 401 eller 403. Handleren gir
  transient feil og prøver på nytt. Derfor kjører den ikke i prod før tilgangen er
  på plass.
- **Nytt forsøk etter at dokdist har tatt imot bestillingen.** Dokdist svarer 409
  med bestillingsId. Det regnes som suksess, og deltakeren får ikke brevet to ganger.
- **Deltakeren er død eller har ukjent adresse.** Dokdist svarer 410 Gone eller
  400. Handleren gir transient feil, og feilen må følges opp manuelt.
- **Deltakeren har adressebeskyttelse.** Dokdist henter adressen selv og
  håndterer adressebeskyttelse. Vi sender ingen adresse.
- **Deltakeren har reservert seg mot digital kommunikasjon.** Dokdist sender
  papirbrev (sjekklisten på kort 599).
- **Prod.** `SoknadKopiDeltakerJournalfoert` publiseres i prod uten at noen
  distribuerer. Når handleren slås på i prod, plukker den bare opp nye events.
  Journalposter fra før det må distribueres manuelt, eller det må avklares at
  de ikke skal sendes.
- **Tekst før varsel.** Skjemaet lover kopi også i prod, før distribusjonen er
  slått på der. Kopien journalføres likevel og vises under «Dine saker» på
  nav.no, men deltakeren får ikke varsel.

## Åpne spørsmål

1. **Nytt varsel når brevet ikke er åpnet.** Etterlevelse krever nytt varsel
   (kort 599). Dekker dokdist det for `ANNET`? Avklares med Team
   Dokumentløsninger.
2. **Tekst i prod før distribusjonen.** Skal tekstendringene vente til
   distribusjonen er slått på i prod (Ken: teksten må i prod samtidig med
   varselet)? Eller er det greit at de går ut nå, siden kopien journalføres og
   vises på nav.no?
3. **Journalposter fra før distribusjonen i prod er på.** Skal de distribueres
   i etterkant?

## Ferdig når

- [ ] `DokDistClient` er koblet inn i appen, lokalt og i dev
- [ ] `DistribuerSoknadKopiDeltaker` distribuerer med `ANNET` og `KJERNETID`, og publiserer `SoknadKopiDeltakerDistribuert` med bestillingsId
- [ ] Handleren er idempotent og tåler 409 fra dokdist
- [ ] Handleren kjører i dev og lokalt, ikke i prod
- [ ] Tekstendringene fra sjekklisten er gjort i v1 og v2 av skjemaet
- [ ] `event-flow.md` er oppdatert
- [ ] Backend- og frontend-testene er grønne
