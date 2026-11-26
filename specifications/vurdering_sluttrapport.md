# Vurdering av sluttrapport

## Mål

Saksbehandler skal kunne bekrefte en mottatt sluttrapport, eller kreve at
arbeidsgiver sender inn en ny. Sluttrapporten må være bekreftet før
refusjonskravet kan behandles. Det er første steg i løpet sluttrapport,
refusjonskrav og utbetaling.

## Tilstander

```
IKKE_MOTTATT → MOTTATT → BEKREFTET
                  │  ▲
                  ▼  │ ny innsending (eget steg)
          NY_INNSENDING_KREVES
```

- Hver innsending er en egen rad i `sluttrapport` med egen status.
  Sluttrapportens status for saken er statusen til nyeste rad.
- `IKKE_MOTTATT` lagres ikke. Saken har ingen rad i `sluttrapport`.
- Bare nyeste rad med status `MOTTATT` kan vurderes.

## Krav

### Endepunkt

`POST /api/saksbehandling/v1/saker/{sakId}/sluttrapport/{sluttrapportId}/vurdering`

```json
{ "utfall": "BEKREFTET" | "NY_INNSENDING_KREVES", "begrunnelse": "..." }
```

- `begrunnelse` er påkrevd ved `NY_INNSENDING_KREVES`, høyst 4000 tegn etter
  trimming. Den skal ikke settes ved `BEKREFTET`.
- `sluttrapportId` i stien er rapporten saksbehandler ser på. Er det kommet en
  nyere innsending, avvises vurderingen.

Tilgang, i denne rekkefølgen:

1. Rollen `SAKSBEHANDLER`.
2. Tilgang til sakens behandlende enhet.
3. Tilgangsmaskinen (kjerneregler), fail-closed.
4. Saken har kilde `EKSPERTBISTAND`.
5. Innlogget bruker er tildelt saken som saksbehandler.

Vakt på den låste sakraden:

| Brudd | Svar |
|-------|------|
| Saken finnes ikke | 404 |
| Saken er ikke `INNVILGET` | 409 `SAK_UGYLDIG_STATUS` |
| Sluttrapporten finnes ikke på saken | 404 |
| Det finnes en nyere sluttrapport | 409 `SLUTTRAPPORT_IKKE_NYESTE` |
| Sluttrapporten er ikke `MOTTATT` | 409 `SLUTTRAPPORT_ALLEREDE_VURDERT` |

Ellers publiseres eventen i samme transaksjon, og svaret er 202.

### Hendelser

`sluttrapportBekreftet` og `sluttrapportNyInnsendingKrevd`:

| Felt | Innhold |
|------|---------|
| `sluttrapportId`, `sakId`, `soknadId` | Id-er fra databasen |
| `vurdertAvIdent`, `vurdertAvNavn` | NAV-ident og navn til saksbehandler |
| `begrunnelse` | Bare `sluttrapportNyInnsendingKrevd` |
| `tidspunkt` | Når saksbehandler sendte vurderingen |

Begge har `aggregateRootId = soknadId`.

### Lagring

`LagreSluttrapportBekreftet` og `LagreSluttrapportNyInnsendingKrevd`
oppdaterer raden bare hvis status fortsatt er `MOTTATT`. De setter `status`,
`vurdert_av_ident`, `vurdert_av_navn`, `vurdert_tidspunkt` og `begrunnelse`, og publiserer
`SakOppdatert` med rollen `SAKSBEHANDLER`. Oppdatering, sakslogg og
idempotensvern skrives i samme transaksjon. Var raden allerede vurdert, lagres
ingenting og saksloggen får ingen post.

Notat i saksloggen: «Sluttrapport bekreftet» og «Ny innsending av
sluttrapport kreves». Begrunnelsen står ikke i notatet.

### Visning for saksbehandler

`GET …/saker/{sakId}/sluttrapport` får `sluttrapportId`, `status`,
`vurdertAvIdent`, `vurdertAvNavn`, `vurdertTidspunkt` og `begrunnelse`. Arbeidsgiverens API endres ikke.

Saksbehandler som er tildelt saken, ser nedtrekkslista «Kontroll av
sluttrapport» når status er `MOTTATT`:

| Valg | Knapp | Lagres |
|------|-------|--------|
| Mottatt sluttrapport dokumenterer tiltaket | Registrer som mottatt | `BEKREFTET`. Teksten «Sluttrapporten blir journalført på personmappa i Gosys.» vises under knappen. |
| Be arbeidsgiver sende sluttrapporten på nytt | Be om sluttrapport på nytt | `NY_INNSENDING_KREVES` med fast begrunnelse |

Den faste begrunnelsen vises for saksbehandler under «Tekst som vises til
arbeidsgiver»: «Send sluttrapporten på nytt. Kontroller at vedlegget er riktig
dokument og gjelder riktig person.»

Etter vurderingen viser kontrollpanelet:

| Status | Overskrift | Tag | Innhold |
|--------|-----------|-----|---------|
| `BEKREFTET` | Sluttrapport registrert som mottatt | Mottatt | «Registrert av» med navn, ident og dato, og teksten om journalføring |
| `NY_INNSENDING_KREVES` | Sluttrapport avvist | Venter på ny sluttrapport | «Avvist av» med navn, ident, dato og klokkeslett, «Beskjed til arbeidsgiver» med begrunnelsen, og at Nav sender påminnelse etter en uke |

Navnet til saksbehandler kommer fra entra-proxy (`AzureAdPrincipal.navn`) og
lagres i eventen og på raden, slik `sakTildeltSaksbehandler` gjør.

Oppgaven i oversikten forsvinner av seg selv, fordi den filtrerer på
`status = 'MOTTATT'`.

### Opplasting fra arbeidsgiver

`POST /api/soknad/v1/{soknadId}/sluttrapport` tar bare imot en sluttrapport når
saken ikke har noen, eller når nyeste har status `NY_INNSENDING_KREVES`. Ellers
svarer ruten 409, og ingenting lagres.

- Ruten sjekker før den leser filen, så arbeidsgiver får svar uten å vente på
  virusskanning.
- `registrerSluttrapport` sjekker på nytt på den låste sakraden. Den sjekken
  hindrer at to samtidige opplastinger, eller en opplasting og en vurdering,
  begge slipper gjennom.

### Sletting av erstattet fil

Når arbeidsgiver laster opp en ny sluttrapport, slettes filene til sakens
sluttrapporter med status `NY_INNSENDING_KREVES` i samme transaksjon
(`registrerSluttrapport`). Radene i `sluttrapport` beholdes med status, vurdering
og begrunnelse. Filer til mottatte og bekreftede sluttrapporter slettes aldri.
Oppgavelista teller bare nyeste sluttrapport per sak.

- Arbeidsgiver kan ha lastet opp feil personopplysninger. En erstattet fil
  trengs ikke lenger.
- `sluttrapportMottatt` har bare metadata om filen (id, filnavn, størrelse),
  ikke innholdet. Eventene endres ikke.
- Sluttrapporter journalføres ikke ennå, så filen finnes ikke andre steder.

### Observerbarhet

- Ruten logger `sakId` og utfall. Begrunnelsen logges aldri.
- Saksloggen er revisjonssporet.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Jonas, 2026-11-24 | Sluttrapport, refusjonskrav og utbetaling får hver sin tilstandsmaskin, knyttet til `sakId`. `Saksstatus` endres ikke. |
| Jonas, 2026-11-24 | `IKKE_MOTTATT` utledes av at raden mangler. Radene opprettes ikke når vedtaket fattes. |
| Jonas, 2026-11-24 | Sluttrapporten vurderes uten beslutter. |
| Jonas, 2026-11-24 | Ved ny opplasting slettes filen til sluttrapporter med status `NY_INNSENDING_KREVES`. Raden med status og begrunnelse beholdes. |
| Jonas, 2026-11-24 | Arbeidsgiver kan bare laste opp sluttrapport når saken ikke har noen, eller når nyeste har status `NY_INNSENDING_KREVES`. |

Valg i spesifikasjonen:

- To hendelser i stedet for én med utfall, fordi bare den ene har begrunnelse.
- Handleren skriver saksloggen selv, slik `LagreForelopigVedtak` gjør. Da får
  saksloggen bare en post når vurderingen faktisk ble lagret.
- `sluttrapportId` i stien hindrer at saksbehandler vurderer en eldre rapport
  enn den som er nyest.

## Avgrensning

- Arbeidsgiver ser ikke `NY_INNSENDING_KREVES`, og kan ikke sende inn på nytt
  fra v2. Kommer i neste steg, sammen med varsel.
- Behandling av refusjonskrav og vakten mot sluttrapporten.
- Journalføring av sluttrapport. Teksten om journalføring i Gosys vises allerede,
  men journalføringen lages i et senere steg.
- Påminnelse til arbeidsgiver etter en uke. Teksten vises allerede, men
  påminnelsen lages i et senere steg.
