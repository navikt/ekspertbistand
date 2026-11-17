# Innsending fra utkast

Bygger på `oppslag_deltaker_arbeidsforhold.md` (valider-deltaker) og
`journalfor_vedtaksbrev_arbeidsgiver.md` (`SoknadDomainV2` og planen om
`SoknadDomainV1`).

## Mål

Backend sender inn det utkastet den har lagret, ikke det klienten sender i
request-body. Endringene er:

- Et utkast opprettes først når deltakeren er validert. Utkastet har alltid
  virksomhet og deltaker.
- `PUT /api/soknad/v1/{id}` har ingen body. Backend validerer hele utkastet,
  kopierer det til `soknad` og sletter utkastet.
- Backend bruker bare virksomhetsnummeret klienten sender. Arbeidsgivers
  virksomhetsnavn hentes fra Altinn-tilgangene, og ekspertens fra Ereg.
- Eventene bruker `SoknadDomainV1`, ikke API-DTO-en `DTO.Soknad`. Da kan API-et
  endres uten at eventene i `event_log` knekker.

## Beslutninger

| Kilde | Innhold |
|-------|---------|
| Anders, 2026-11-17 | Utkastet opprettes når deltakeren er validert i steg 1. `POST /api/soknad/v1` tar `{fnr, virksomhetsnummer}` og gjør de samme sjekkene som `valider-deltaker`. |
| Anders, 2026-11-17 | `POST /{id}/valider-deltaker` blir værende. Den brukes når arbeidsgiver endrer deltaker eller virksomhet på et utkast som finnes. |
| Anders, 2026-11-17 | Innsending kopierer utkastet til `soknad`. Request-body brukes ikke. |
| Anders, 2026-11-17 | Bare den som opprettet utkastet, kan sende det inn. Tilgangssjekken er den samme som når utkastene listes, uten oppslag i Altinn. |
| Anders, 2026-11-17 | PATCH og dagens `valider()` endres ikke, med ett unntak: ekspertens virksomhet (se under). Ny validering skjer ellers bare ved innsending. |
| Anders, 2026-11-17 | Arbeidsgivers virksomhetsnavn hentes fra Altinn-tilgangene til innlogget bruker når deltakeren valideres, og lagres på utkastet. Arbeidsgiver skal ikke kunne slå opp vilkårlige virksomheter. Innsendingen kopierer navnet fra utkastet uten nytt oppslag. Visningen leser fra tabellene. |
| Anders, 2026-11-17 | Når arbeidsgiver setter ekspertens virksomhet, leser backend bare `ekspert.virksomhetOrgnr` fra PATCH og slår opp navnet i Ereg. `virksomhet` og `virksomhetNavn` fra klienten ignoreres. |
| Anders, 2026-11-17 | Ekspertens orgnr og virksomhetsnavn er påkrevd ved innsending. Innsendingen kopierer `ekspert_virksomhet`, `ekspert_virksomhet_navn` og `ekspert_virksomhet_orgnr` fra utkastet uten nytt oppslag i Ereg. |
| Anders, 2026-11-17 | `virksomhetsnummer`, `virksomhetsnavn`, `ansatt_fnr`, `ansatt_navn` og `ansatt_bekreftet_tidspunkt` i `utkast` blir NOT NULL. Migreringen sletter utkast som mangler dem først. |
| Anders, 2026-11-17 | Et utkast har alltid en bekreftet deltaker. Gir `valider-deltaker` 422, endres ikke utkastet, og den forrige deltakeren beholdes. `deltakerBekreftet` i `DTO.Utkast` er derfor alltid true. |
| Anders, 2026-11-17 | `DTO.Soknad` deles i `SoknadDomainV1` (eventene) og `DTO.Soknad` (API). |
| Anders, 2026-11-17 | Både v1- og v2-flyten i frontend tilpasses. |
| `journalfor_vedtaksbrev_arbeidsgiver.md` | `SoknadDomainV1` er en nøyaktig kopi av `DTO.Soknad`, så JSON-en i `event_log` kan leses uten endring. Det blir ingen `V1.tilV2()`. |

## Avgrensning

- `DTO.Utkast` endres ikke. PATCH `/{id}` endres bare for ekspertens
  virksomhet.
- Ingen ny sjekk mot Aa-registeret eller PDL ved innsending. Innsendingen bruker
  oppslaget som er lagret på utkastet.
- Eventene bytter ikke til `SoknadDomainV2`. Det er eget arbeid.
- Kolonnen `ekspert_virksomhet` fjernes ikke.
- Saksbehandlingsløsningen endres ikke, utover at den leser `SoknadDomainV1` fra
  eventene.

## Tilnærming

### 1. `SoknadDomainV1`

Ny `event/domain/SoknadDomainV1.kt` er en nøyaktig kopi av dagens `DTO.Soknad`
med de nøstede typene. Den beholder feltnavn, nullbarhet og standardverdier,
også `ekspert.kompetanse` med fritekst fra før 2026-08-11. Dermed gir samme JSON
samme objekt, og `event_log` endres ikke.

`SoknadDomainV1` erstatter `DTO.Soknad` i

- eventene i `Events.kt`,
- JSON-en i `ArenaSakTable.soknad` (`arena/Db.kt` og Arena-prosessorene),
- handlerne, projeksjonene og `BehandlendeEnhetUtleder`,
- utvidelsene `aggregateRootId` og `kvitteringsLenke`.

`ResultRow.tilSoknadDomainV1()` i `soknad/Db.kt` bygger typen fra
søknadsraden, slik `tilSoknadDTO()` gjør for API-et.

### 2. Skille API-DTO-en fra eventene

`DTO.Soknad` brukes bare i API-et og beholder dagens JSON-form. Siden den ikke
lenger er i eventene, kan den endres senere uten migrering av `event_log`.

`DTO.Soknad.tilDomain()` til `SoknadDomainV2` erstattes av en funksjon som
bygger fra søknadsraden. `BeslutterApi` bruker den nye funksjonen.

### 3. Validering ved innsending

Ny `soknad/InnsendingValidering.kt` har `validerKomplett(utkast)`. Den returnerer
en `InnsendingsklarSoknad` der alle påkrevde felt er ikke-null, eller kaster
`UgyldigInputException` med feltsti for første feil (400 `UGYLDIG_INPUT`, som i
dag).

Reglene speiler `soknadSchema` i `frontend/client/src/v2/features/soknad/schema.ts`:

| Felt | Regel |
|------|-------|
| `virksomhet.virksomhetsnummer` | 9 siffer |
| `virksomhet.kontaktperson.navn` | Ikke blank |
| `virksomhet.kontaktperson.epost` | Gyldig e-post |
| `virksomhet.kontaktperson.telefonnummer` | Gyldig telefonnummer, som i `phoneField` |
| `ansatt.fnr`, `ansatt.navn` | Ingen sjekk. Kolonnene er NOT NULL, og utkastet har alltid en bekreftet deltaker |
| `ekspert.navn` | Ikke blank |
| `ekspert.virksomhet`, `ekspert.virksomhetNavn` | Ikke blank |
| `ekspert.virksomhetOrgnr` | 9 siffer |
| `ekspert.godkjentUtdanningEllerAutorisasjon`, `ekspert.relevantKompetanse` | Minst ett element |
| `behovForBistand.begrunnelse`, `behov`, `tilrettelegging` | Ikke blank |
| `behovForBistand.timer` | Positivt heltall |
| `behovForBistand.estimertKostnad` | Positivt heltall, ikke over maks sats for startdatoen |
| `behovForBistand.startdato` | Satt, ikke i fortiden, og en maks sats gjelder for datoen |
| `nav.kontaktperson` | Ikke blank |

Maks-sats-sjekken flyttes fra `sendInnSoknad` inn i `validerKomplett`.

### 4. Opprette utkast

`POST /api/soknad/v1` tar samme body som `valider-deltaker`:

```json
{ "fnr": "12345678910", "virksomhetsnummer": "910456900" }
```

Sjekkene er de samme som i `valider-deltaker` (input, Altinn-tilgang, rate limit,
Aa-registeret, PDL), men uten sjekkene på et eksisterende utkast. Logikken flyttes
til en felles funksjon som begge endepunktene bruker.

| Utfall | Svar |
|--------|------|
| Deltakeren er bekreftet | 201 med `DTO.Utkast`. Utkastet lagres med fødselsnummer, navn, virksomhet og `ansatt_bekreftet_tidspunkt` |
| Ugyldig input | 400 `UGYLDIG_INPUT`. Ingen utkast lagres |
| Mangler Altinn-tilgang, eller virksomheten har ikke navn i Altinn-tilgangene | 403. Ingen utkast lagres |
| For mange oppslag | 429 `FOR_MANGE_OPPSLAG`. Ingen utkast lagres |
| Ingen arbeidsforhold | 422 `INGEN_ARBEIDSFORHOLD`. Ingen utkast lagres |
| Aa-registeret eller PDL feiler | 503 `OPPSLAG_FEILET`. Ingen utkast lagres |

`valider-deltaker` henter virksomhetsnavnet fra Altinn-tilgangene. Den lagrer
bare en bekreftet deltaker. Ved 422 endres ikke utkastet, så det beholder den
forrige deltakeren.

Migrering `V37__utkast_virksomhet_og_deltaker_paakrevd.sql`:

```sql
SET lock_timeout = '3s';

DELETE FROM utkast
WHERE virksomhetsnummer IS NULL
   OR virksomhetsnavn IS NULL
   OR ansatt_fnr IS NULL
   OR ansatt_navn IS NULL
   OR ansatt_bekreftet_tidspunkt IS NULL;

ALTER TABLE utkast ALTER COLUMN virksomhetsnummer SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN virksomhetsnavn SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_fnr SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_navn SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_bekreftet_tidspunkt SET NOT NULL;
```

Utkastene som slettes, har ikke kommet forbi steg 1, og ville uansett blitt
slettet etter 48 timer.

### 5. Ekspertens virksomhet ved PATCH

PATCH leser bare `ekspert.virksomhetOrgnr` av virksomhetsfeltene til eksperten.
`ekspert.virksomhet` og `ekspert.virksomhetNavn` fra klienten ignoreres. Resten
av PATCH er som før.

| Orgnr i request | Det backend gjør |
|-----------------|------------------|
| Likt det som er lagret | Ingen oppslag. Feltene beholdes |
| Null eller tom streng | Setter `ekspert_virksomhet`, `ekspert_virksomhet_navn` og `ekspert_virksomhet_orgnr` til null |
| Nytt orgnr | Sjekker at det er 9 siffer, slår opp navnet med `EregService.hentVirksomhetsnavn` og lagrer `ekspert_virksomhet_orgnr`, `ekspert_virksomhet_navn` og `ekspert_virksomhet` som «navn (orgnr)», som frontend gjør i dag |

Feil ved nytt orgnr:

| Feil | Svar |
|------|------|
| Ikke 9 siffer, eller finnes ikke i Ereg | 400 `UGYLDIG_INPUT` med feltsti `ekspert.virksomhetOrgnr`. Utkastet endres ikke |
| Ereg feiler | 503 `OPPSLAG_FEILET`. Utkastet endres ikke |

Oppslaget gjøres bare når orgnr er endret, så autolagringen gir ikke et kall
til Ereg for hver PATCH. Svaret er utkastet med navnet fra Ereg, og frontend
viser det som før.

`EregService` får `hentVirksomhetsnavn(orgnr): String?`. Den returnerer null når
Ereg svarer 404, og kaster ved alle andre svar som ikke er 2xx. Det skiller den fra
`finnOrganisasjonForOrgnr`, som gir tom liste ved alle feil.

### 6. Innsending

`PUT /api/soknad/v1/{id}` leser ikke request-body. Stegene:

| # | Steg | Svar ved feil |
|---|------|---------------|
| 1 | Utkastet finnes og er i utkast-status | 409 |
| 2 | Innlogget bruker har opprettet utkastet. Altinn-tilgangen sjekkes ikke, som når utkastene listes | 403 |
| 3 | `validerKomplett(utkast)` | 400 `UGYLDIG_INPUT` |
| 4 | Ereg: forretningsadressen til arbeidsgiver, som i dag | Ingen. Feiler oppslaget, lagres adressen som null |
| 5 | Kopierer utkastet til `soknad`, sletter utkastet og publiserer `SoknadInnsendt(SoknadDomainV1)` i samme transaksjon | – |

I `soknad` lagres

- `virksomhetsnavn` fra utkastet, slik det ble hentet fra Altinn-tilgangene da
  deltakeren ble validert,
- ekspertfeltene fra utkastet, der navnet ble hentet fra Ereg ved PATCH,
- `beliggenhetsadresse` fra Ereg som i dag. Mangler adressen, lagres null som i
  dag.

Svaret er `DTO.Soknad` som i dag.

### 7. Frontend v2 (`frontend/client/src/v2`)

- Startsiden (`SoknadPage`) oppretter ikke lenger utkast. Den sender arbeidsgiver
  til steg 1 uten id, for eksempel `/v2/skjema/ny/steg-1`.
- Steg 1 uten id lagrer ingenting automatisk. «Neste steg» kaller `POST` og
  sender arbeidsgiver til `/v2/skjema/{id}/steg-2`. «Slett søknaden» og
  «Fortsett senere» sender bare arbeidsgiver til oversikten.
- Steg 1 med id bruker `valider-deltaker` som i dag.
- Oppsummeringen venter på at autolagringen er ferdig, og sender så `PUT` uten
  body.
- Steg 2 viser 400 og 503 fra PATCH på feltet for ekspertens virksomhet.
- MSW-handlerne, e2e-`mockApi.ts` og testene oppdateres.

### 8. Frontend v1 (`frontend/client/src`)

Samme endringer som i v2, med stiene `/skjema/...`.

### 9. Dokumentasjon

- `event/README.md` og `event/event-flow.md` nevner `SoknadDomainV1`.
- Kommentaren over endepunktene i `soknad/Routing.kt` beskriver ny POST og PUT,
  og at PATCH slår opp ekspertens virksomhet.

### 10. Tester

- `SoknadDomainV1`: JSON for alle tre `ekspert`-formene i prod deserialiseres og
  serialiseres til samme JSON.
- `validerKomplett`: hver regel i tabellen over, og at et komplett utkast blir
  godkjent.
- `POST`: 201 og lagret utkast ved bekreftet deltaker, og at ingen utkast
  lagres ved 400, 403, 422, 429 og 503.
- PATCH: nytt orgnr gir navn fra Ereg og ignorerer `virksomhet` og
  `virksomhetNavn` fra klienten. Uendret orgnr gir ikke oppslag. Null nullstiller
  feltene. Ukjent orgnr gir 400, og feil i Ereg gir 503.
- `PUT`: innsending kopierer utkastet, ignorerer body, sletter utkastet og
  publiserer `SoknadInnsendt`. 400 ved ufullstendig utkast.
- Frontend: steg 1 uten id oppretter utkast og navigerer til steg 2, og
  feilene fra `POST` vises som i dag.

## Berørte filer

| Fil | Endring |
|-----|---------|
| `event/domain/SoknadDomainV1.kt` | Ny |
| `event/Events.kt`, `event/handlers/*`, `event/projections/*` | `DTO.Soknad` blir `SoknadDomainV1` |
| `arena/*.kt` | `ArenaSakTable.soknad` bruker `SoknadDomainV1` |
| `norg/BehandlendeEnhetUtleder.kt` | Tar `SoknadDomainV1` |
| `event/domain/TilDomain.kt` | `SoknadDomainV2` bygges fra søknadsraden |
| `soknad/Api.kt` | Ny `opprettUtkast`, ny `sendInnSoknad`, felles deltakerlogikk, ekspertens virksomhet i `oppdaterUtkast` |
| `soknad/Db.kt` | `tilSoknadDomainV1()`, NOT NULL i `UtkastTable` |
| `soknad/InnsendingValidering.kt` | Ny |
| `ereg/EregService.kt` | `hentVirksomhetsnavn` |
| `db/migration/V37__utkast_virksomhet_og_deltaker_paakrevd.sql` | Ny |
| `frontend/client/src/v2/...`, `frontend/client/src/...` | Steg 1 uten id, PUT uten body, mocks |

## Kanttilfeller og feilmodus

- **Autolagring som ikke er ferdig ved innsending.** Backend sender inn det som
  er lagret. Frontend må derfor vente på pågående PATCH før `PUT`.
- **To samtidige innsendinger av samme utkast.** Transaksjonen sletter utkastet
  før den lagrer søknaden. Den andre innsendingen sletter ingen rader og får 409.
- **Arbeidsgivers navn i Altinn er endret siden steg 1.** Innsendingen bruker
  navnet som ble lagret på utkastet. Utkast slettes etter 48 timer, så navnet er
  aldri eldre enn det.
- **Ekspertens virksomhet er lagret før endringen.** Utkast der frontend har
  satt `virksomhetNavn` selv, beholder verdien til orgnr endres. Utkast slettes
  etter 48 timer, så dette gjelder bare utkast som finnes når vi deployer.
- **Utkast opprettet før endringen.** Utkast med virksomhet og deltaker beholdes
  og kan sendes inn. De andre slettes av migreringen.
- **Gamle eventer i `event_log`.** `SoknadDomainV1` har samme form som
  `DTO.Soknad`, så de leses som før.

## Åpne spørsmål

Ingen.

## Ferdig når

- [ ] `SoknadDomainV1` finnes, og ingen eventer eller `ArenaSakTable` bruker `DTO.Soknad`
- [ ] `POST /api/soknad/v1` oppretter utkast bare når deltakeren er bekreftet
- [ ] `PUT /api/soknad/v1/{id}` sender inn det lagrede utkastet etter `validerKomplett`
- [ ] Arbeidsgivers virksomhetsnavn kommer fra Altinn og ekspertens fra Ereg, og innsendingen kopierer alle virksomhetsfelt fra utkastet
- [ ] Kolonnene i `utkast` er NOT NULL
- [ ] v1 og v2 i frontend oppretter utkast fra steg 1 og sender inn uten body
- [ ] Testene over er grønne
