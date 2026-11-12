# Hendelser for innsendt refusjonskrav og sluttrapport

## Mål

Når arbeidsgiver sender inn refusjonskrav eller sluttrapport, skal det stå i
`event_log` hva som kom inn, når og fra hvem. Saksbehandler skal se innsendingen
i saksloggen.

## Krav

### Hendelser

To nye hendelser i `Events.kt`. Begge har `aggregateRootId = soknadId`, så de
slettes sammen med resten av søknaden.

`refusjonskravMottatt`:

| Felt | Innhold |
|------|---------|
| `refusjonskravId`, `sakId`, `soknadId` | Id-er fra databasen |
| `virksomhetsnummer` | Virksomheten på søknaden, lest fra databasen |
| `innsendtAv` | Fødselsnummeret til innlogget bruker |
| `belopOre` | Beløpet som kreves, i øre |
| `innvilgetBelopKroner` | Grensen som gjaldt da kravet kom inn |
| `vedlegg` | Liste med `VedleggDomain(vedleggId, filnavn, storrelse)` |
| `tidspunkt` | `refusjonskrav.opprettet` |

`sluttrapportMottatt`:

| Felt | Innhold |
|------|---------|
| `sluttrapportId`, `sakId`, `soknadId` | Id-er fra databasen |
| `virksomhetsnummer` | Virksomheten på søknaden, lest fra databasen |
| `innsendtAv` | Fødselsnummeret til innlogget bruker |
| `vedlegg` | Én `VedleggDomain` |
| `tidspunkt` | `sluttrapport.opprettet` |

- Filinnholdet ligger i `vedlegg` og tas ikke med i hendelsen.
- Navn og fødselsnummer på deltakeren tas ikke med. De står i `soknadInnsendt`.
- `VedleggDomain` ligger i `event/domain/`, ikke i API-et.
- En sak kan få flere sluttrapporter. Hver innsending gir én hendelse.

### Publisering

- Hendelsene publiseres med `publishEventQueue` i samme transaksjon som raden
  og vedleggene lagres. Feiler noe, lagres ingenting, og arbeidsgiver får 500.
- Avviste innsendinger (Arena-sak, over innvilget beløp, refusjonskrav finnes
  fra før) gir verken rad eller hendelse.
- `RefusjonDb.lagreRefusjonskrav` og `VedleggDb.lagreSluttrapport` får
  `virksomhetsnummer` og `innsendtAv` som påkrevde parametere uten
  standardverdi.

### Sakslogg

- `SkrivSaksloggForRefusjonskrav` og `SkrivSaksloggForSluttrapport` behandler
  mottatt-hendelsene og publiserer `SakOppdatert(SYSTEM, null, notat)`.
  `SkrivSakslogg` skriver posten asynkront.
- Publisering og idempotensvern lagres i samme transaksjon. Ny behandling av
  samme hendelse gir ikke flere `SakOppdatert`.
- Notat: «Refusjonskrav mottatt fra arbeidsgiver» og «Sluttrapport mottatt fra
  arbeidsgiver».
- `AktorRolle` får ikke en ny verdi. `SakOppdatert` er i prod og endres ikke.

### Filnavn

- `lesOpplasting` fjerner sti, kontrolltegn og linjeskift fra filnavnet og
  kutter det til 255 tegn. Blir navnet tomt, brukes `vedlegg.pdf`.
- Filnavnet havner i hendelsen, som aldri kan endres, så det renses før lagring.

### Observerbarhet

- Mottatte innsendinger telles allerede av `refusjon_mottatt_total` og
  `vedlegg_lastet_opp_total{type=SLUTTRAPPORT}` (én fil per sluttrapport).
  Det kommer ingen ny teller.
- Hendelsene logges ikke. Fødselsnummer og filnavn skal ikke i applikasjonsloggen.

### Lokale data

`LokaleSaker` lagrer innsendingene med samme hjelpefunksjon som API-et, så
lokale saker får hendelser og sakslogg.

## Beslutninger

| Kilde | Beslutning |
|-------|-----------|
| Ken, 2026-11-10 | Hendelsene skal ha med alt som kan være interessant. |
| Ken, 2026-11-10 | Innsendingene skal vises i saksloggen. |
| Ken, 2026-11-10 | Det finnes ingen refusjonskrav eller sluttrapporter i prod, så ingen innsendinger mangler hendelse. |
| Ken, 2026-11-10 | Mottatte innsendinger telles. |

Valg i spesifikasjonen:

- Oversikten leser fortsatt tabellene. Tabell og hendelse skrives i samme
  transaksjon, så de er alltid like, og en projeksjon ville ikke gitt noe nytt.
- Hendelsen publiseres i samme transaksjon, ikke etter commit. Da kan en lagret
  innsending aldri mangle hendelse, og `event_log` skal ikke backfilles.
- Mottatt-hendelsene driver saksloggen via egne handlere, ikke noop-handlere.
  Lagringskoden publiserer bare mottatt-hendelsen.

## Avgrensning

- Journalføring av refusjonskrav og sluttrapport.
- Varsel til arbeidsgiver eller saksbehandler.
- Endring av saksstatus når innsendingen kommer.
