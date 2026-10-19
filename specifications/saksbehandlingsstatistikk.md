# Plan: SaksbehandlingsstatistikkProjection, meldinger til saksbehandlingsstatistikk og datavarehus

## Problem
Team Sak lager felles saksbehandlingsstatistikk for Nav (etterlevelseskrav K184). Alle vedtaksløsninger skal
levere én melding per relevant hendelse i en behandling: registrert, tildelt, sendt til beslutter, avsluttet og så
videre. Grensesnittet er en tabell eller et view i BigQuery der én rad er én melding. Kravene står i «Teknisk
beskrivelse av behov til felles saksbehandlingsstatistikk» fra Team Sak.

Datavarehuset får i dag tiltaksstatistikk for ekspertbistand fra Arena (tiltakstype, gjennomføring, deltakelse).
Team Sak har godtatt ekstra felter, så vi løser begge behovene i samme tabell.

Vi lager en `EventLogProjectionBuilder` som bygger meldingene fra event-loggen og skriver dem direkte til en
BigQuery-tabell i et datasett vi eier. Dokumentasjonen for Team Sak og datavarehuset står i
[`statistikk/README.md`](../backend/src/main/kotlin/no/nav/ekspertbistand/statistikk/README.md).

## Begrepene til Team Sak i vårt domene
| Team Sak | Ekspertbistand |
|----------|----------------|
| Sak | Raden i `sak`. `sak_id` og `sak_nummer` er `sak.sak_id`, som saksbehandlingsløsningen slår opp på |
| Behandling | Søknadsbehandlingen. `behandling_id` er søknad-id-en, som også er aggregatroten til eventene |
| Hendelse | En event i `event_log` som endrer noe Team Sak skal ha |
| Melding | En rad i BigQuery-tabellen `ekspertbistand_statistikk.saksbehandlingsstatistikk` |

## Avklarte beslutninger
| Tema | Valg |
|------|------|
| Hvilke saker | Bare saker med kilde EKSPERTBISTAND, altså saker der `SakOpprettet` finnes. Arena-saker vedtas i Arena og gir ingen meldinger |
| Miljø | Registreres bare utenfor prod, som `OpprettSak`. Skrus på i prod når vi fatter vedtak der. Datasettet finnes allerede i prod, så tilgang kan gis før det kommer data |
| Lagring | Appen skriver direkte til BigQuery-datasettet `ekspertbistand_statistikk`, som Nais oppretter fra `spec.gcp.bigQueryDatasets`. Ingen tabell i Postgres. Team Sak og datavarehuset ber om lesetilgang i GCP |
| Tabellen | Appen oppretter `saksbehandlingsstatistikk` første gang den skriver. Radene endres eller slettes aldri |
| Tilstand | Lagres ikke. For hver event folder projeksjonen tidligere fullførte eventer med samme aggregatrot fra `event_log` |
| Ny melding | Bare når meldingsinnholdet endres. Ny tildeling til samme saksbehandler gir ingen rad |
| Avviste hendelser | Projeksjonen speiler vaktene i handlerne. En event som ikke endret saken, gir ingen melding |
| Sekvensnummer | Id-en til eventen i `event_log`. Den øker, men har hull |
| Duplikater | `insertId = "$projeksjon-$sekvensnummer"` lar BigQuery fjerne de fleste. Leveransen er minst én gang, så konsumentene beholder én rad per `projeksjon` og `sekvensnummer` |
| Resending | Bump versjonen i `name`. Projeksjonen leser loggen på nytt og skriver nye rader med samme `hendelse_tid` og `sekvensnummer`, og ny `melding_tid` og `projeksjon` |
| Mottak | Ingen egen melding for mottatt søknad. Mottak og opprettelse av sak er i praksis det samme i ny løsning. `mottatt_tid` står på alle rader |
| Saksbehandler | Kan være tom: før tildeling og etter frigjøring ligger saken i felles kø |
| Avslagsgrunn | Fritekst hos oss. Ikke med i statistikken |
| Kodeverdier | Dokumentert i README-en. Team Sak og datavarehuset kontrollerer at de passer |
| `ansvarlig_enhet` | Enhetsnummeret, ikke organisasjons-id |
| Kode 6 | Behandlende enhet 2103 (Nav Vikafossen) betyr strengt fortrolig adresse. Da settes `opprettet_av`, `saksbehandler`, `ansvarlig_beslutter`, `ansvarlig_enhet`, `arrangor_orgnr` og `ekspert_virksomhet_orgnr` til `-5` |
| Datavarehus | Ekstra kolonner i samme tabell. Feltnavnene er våre egne. Mappingen står i `statistikk/README.md` |
| Ekspertens kompetanse | Faste valg blir koder (`EkspertStatistikkoder.kt`). Egne verdier og fritekst blir `ANNET`, fordi fritekst ikke kan grupperes og kan inneholde personopplysninger |
| Timer | Arbeidsgiverens anslag fra søknaden. Saksbehandler kontrollerer det ikke |
| Deltakerstatus | Sendes ikke, fordi vi ikke har feltet. Datavarehuset kan utlede den fra feltene som finnes |
| Startdato for tiltakstypen | Sendes ikke. Samme verdi for alle rader, så den kan legges til utenfor eksporten |
| Utenfor scope | Refusjon og sluttrapport (kan bli egne behandlinger senere), avslagskoder, ekspertens navn, fritekst |

## Tilnærming

### 1. Avhengighet og Nais
- `com.google.cloud:libraries-bom` i `dependencyManagement` og `google-cloud-bigquery` i `backend/pom.xml`.
- `nais/dev-gcp-backend.yaml` og `nais/prod-gcp-backend.yaml`: `gcp.bigQueryDatasets` med `name: ekspertbistand_statistikk` og
  `permission: READWRITE`. Nais gir appen tilgang med workload identity og setter `GCP_TEAM_PROJECT_ID`.

### 2. Migrering `V25__event_log_aggregate_root_id_indeks.sql`
Indeks på `event_log (aggregate_root_id, id)`. Uten den skanner projeksjonen hele `event_log` for hver event, også
ved resending.

### 3. `statistikk/SaksbehandlingsstatistikkSkriver.kt`
- `fun interface SaksbehandlingsstatistikkSkriver { fun skriv(rad: SaksbehandlingsstatistikkRad) }`.
- `SaksbehandlingsstatistikkRad` har ett felt per kolonne og `insertId`. `toString` tar ikke med `fnr`.
- `BigQuerySaksbehandlingsstatistikkSkriver` oppretter tabellen hvis den mangler, og skriver med `insertAll`.
  Feil gir unntak, slik at `EventLogProjectionBuilder` prøver på nytt. Feilmeldingen inneholder ikke radverdier.
- Én liste med kolonner gir både BigQuery-skjemaet og verdiene som sendes. Tidspunkt og datoer sendes som ISO 8601.
- `Application.kt` gir BigQuery-skriveren, `LocalApplication.kt` en som ikke gjør noe.

### 4. `statistikk/SaksbehandlingsstatistikkProjection.kt`
`class SaksbehandlingsstatistikkProjection(database, skriver) : EventLogProjectionBuilder(database)`,
`name = "Saksbehandlingsstatistikk-v1"`.

- `handle` bygger forrige tilstand fra `event_log` (samme `aggregate_root_id`, lavere id, status `COMPLETED`),
  kaller den rene funksjonen `nesteTilstand(forrige, eventId, data, eventTimestamp)` og skriver en rad hvis
  `meldingsinnhold()` er endret.
- `nesteTilstand` returnerer `Overgang(tilstand, hendelseTid)`, eller `null` når eventen ikke endrer noe.
- `BehandlingTilstand.meldingsinnhold()` er `null` før `SakOpprettet` og gjør skjermingen ved kode 6.
- Enum-ene `StatistikkStatus`, `StatistikkResultat` og `BehandlingMetode` holder kodeverdiene.
- `EkspertStatistikkoder.kt` har `EkspertUtdanning` og `EkspertKompetanse` (kode og etikett fra skjemaet), og
  `DTO.Ekspert.utdanningKoder` og `kompetanseKoder`, som gjør valgene om til koder.

| Event | Effekt | `behandling_status` | `hendelse_tid` |
|-------|--------|---------------------|----------------|
| `SoknadInnsendt` | Nullstiller tilstanden og lagrer `mottatt_tid`. Ingen melding, saken finnes ikke ennå | – | – |
| `InnsendtSoknadJournalfoert` | Lagrer behandlende enhet, mappet til Norg-nummer med `BehandlendeEnhetService.arenaTilNorgEnhetNr`. Ingen melding | – | – |
| `SakOpprettet` | Første melding. Setter `sak_id`, `registrert_tid`, `fnr`, `forventet_oppstart_tid`, `arrangor_orgnr`, `estimert_antall_timer` og ekspertfeltene | `REGISTRERT` | tidspunktet i event-loggen |
| `SakTildeltSaksbehandler` | Setter `saksbehandler` når `tildelingEventId` er null eller lavere enn event-id-en. Fra `REGISTRERT` blir status `UNDER_BEHANDLING` | uendret eller `UNDER_BEHANDLING` | `tidspunkt` |
| `SakFrigjort` | Nuller `saksbehandler` når identen har saken og eventen er nyere enn forrige tildeling | uendret | `tidspunkt` |
| `ForelopigVedtakSendtTilBeslutter` | Midlertidig `behandling_resultat` fra utfallet, og `innvilget_belop` og tiltaksperiode fra det foreløpige vedtaket. Krever saksstatus `UNDER_BEHANDLING` | `TIL_BESLUTNING` | `tidspunkt` |
| `SakTildeltBeslutter` | Setter `ansvarlig_beslutter`. Krever `TIL_BESLUTNING`, nyere tildeling og at beslutter ikke er den som innstilte | uendret | `tidspunkt` |
| `ForelopigVedtakReturnert` | Nuller resultat, beløp og periode, og setter `resultat_begrunnelse` fra returårsakene. Bruker `beslutningHindring()` fra `saksbehandling/Db.kt` | `UNDERKJENT_BESLUTTER` | `tidspunkt` |
| `VedtakFattet` | Endelig resultat, `ferdigbehandlet_tid` og `ansvarlig_beslutter = fattetAv`. Beløp og periode fra siste foreløpige vedtak står fast. Bruker `beslutningHindring()` | `AVSLUTTET` | `tidspunkt` |
| øvrige | Ignoreres, også `VilkarsvurderingOppdatert` og alle eventer etter `AVSLUTTET` | – | – |

### 5. Feltmapping
| Felt | Kilde |
|------|-------|
| `sekvensnummer` | `event.id` |
| `sak_id`, `sak_nummer` | `SakOpprettet.sakId` |
| `behandling_id` | `event.aggregateRootId` (søknad-id) |
| `fnr` | `soknad.ansatt.fnr` fra `SakOpprettet` |
| `hendelse_tid` | Se tabellen over |
| `melding_tid` | `Clock.System.now()` når raden skrives |
| `mottatt_tid` | Tidspunktet for `SoknadInnsendt` i event-loggen |
| `registrert_tid` | Tidspunktet for `SakOpprettet` i event-loggen |
| `ferdigbehandlet_tid` | `VedtakFattet.tidspunkt` |
| `forventet_oppstart_tid` | `behovForBistand.startdato` |
| `sak_ytelse` | `EKSPERTBISTAND` |
| `sak_utland` | `NASJONAL` |
| `behandling_type` | `SOKNAD` |
| `behandling_resultat` | `INNVILGE` → `INNVILGET`, `INNVILGE_REDUSERT` → `DELVIS_INNVILGET`, `AVSLAA` → `AVSLATT` |
| `resultat_begrunnelse` | `FEIL_FAKTA` → `FEIL_FAKTUM`, `FEIL_REGELFORSTAELSE` → `FEIL_LOVANVENDELSE`, `ANNET` → `ANNET`. Flere årsaker sorteres og skilles med komma |
| `behandling_metode` | `AUTOMATISK` på `REGISTRERT`, `MANUELL` fra første handling av saksbehandler eller beslutter |
| `opprettet_av` | `EKSPERTBISTAND`, fordi systemet oppretter saken når søknaden er journalført |
| `saksbehandler` | Ident fra `SakTildeltSaksbehandler`, `null` før tildeling og etter frigjøring |
| `ansvarlig_beslutter` | Ident fra `SakTildeltBeslutter` eller `VedtakFattet.fattetAv` |
| `ansvarlig_enhet` | Behandlende enhet (Norg-nummer) |
| `vedtaksloesning_navn` | `EKSPERTBISTAND` |
| `vedtaksloesning_versjon` | Image-taggen fra `NAIS_APP_IMAGE`, eller `lokal` |
| `tiltakskode_arena` | `EKSPEBIST` (`EKSPERTBISTAND_TILTAKSKODE`) |
| `arrangor_orgnr` | `soknad.virksomhet.virksomhetsnummer`, som Arena bruker som arrangør |
| `estimert_antall_timer` | `behovForBistand.timer` som heltall, `null` hvis verdien ikke er et tall |
| `ekspert_virksomhet_orgnr` | `soknad.ekspert.virksomhetOrgnr` |
| `ekspert_utdanning` | `godkjentUtdanningEllerAutorisasjon` som koder. Eldre søknader med bare `kompetanse` gir `ANNET` |
| `ekspert_kompetanse` | `relevantKompetanse` som koder |
| `innvilget_belop`, `tiltaksperiode_fra`, `tiltaksperiode_til` | Siste foreløpige vedtak |
| `projeksjon` | `name` |

Kodene er sorterte, unike og skilt med komma. Sammenligningen med de faste valgene ignorerer store og små bokstaver
og mellomrom foran og bak. Valgene må holdes like i `EkspertStatistikkoder.kt` og `SkjemaSteg1Page.tsx`.

Vi fyller ikke `behandling_nummer`, `relatert_*`, `behandling_aarsak`, `utbetalt_tid` og feltene som bare gjelder
tilbakekreving.

### 6. Registrering
`dependencies.create(SaksbehandlingsstatistikkProjection::class)` i `other`-grenen av
`configureProjectionBuilders` (`EventLogProjections.kt`), altså ikke i prod. DI gir skriveren.

### 7. Tester, `SaksbehandlingsstatistikkProjectionTest`
Testene bruker en skriver som legger radene i en liste.

- Livsløp med retur fra beslutter gir én melding per relevant hendelse, med riktig status, resultat, begrunnelse og tider
- Beløp og periode følger det foreløpige vedtaket, nulles ved retur og står fast ved vedtak
- Ekspertens kompetanse blir koder, og fritekst og eldre `kompetanse` blir `ANNET`
- Sak uten `SakOpprettet` (Arena-sak) gir ingen meldinger
- Hendelser handleren avviser gir ingen melding
- Strengt fortrolig adresse (enhet 2103) skjermer de seks feltene med `-5`
- Ny levering av samme eventer gir samme rader og samme `insertId`
- Raden passer med BigQuery-skjemaet: alle påkrevde felter er med, og tidspunkt og datoer er ISO 8601

### 8. Dokumentasjon
- Kjør `executables/EventFlowDiagram.kt` for å oppdatere `event/event-flow.md`.
- Nevn `SaksbehandlingsstatistikkProjection` i `event/projections/README.md`.
- `statistikk/README.md` for Team Sak og datavarehuset: tilgang, felter, mapping, kodeverk og personvern.

## Merknader
- `fnr` er deltakerens fødselsnummer. Det skal aldri logges.
- Tidspunkter kommer fra eventene, ikke `now()`, slik at resending gir samme `hendelse_tid`. Bare `melding_tid` er ny.
- BigQuery er ikke med i Postgres-transaksjonen. Feiler en senere event i samme batch etter at en rad er skrevet,
  skrives raden på nytt ved neste forsøk. Derfor fjerner konsumentene duplikater på `projeksjon` og `sekvensnummer`.
- Nye kolonner må legges til i BigQuery-tabellen før appen skriver dem. Appen endrer ikke tabellen.
- Vi utleder strengt fortrolig adresse fra enhet 2103. Gir Norg en annen enhet for adressebeskyttede, må vi lagre
  graderingen i en event.
- Rammeverket hopper over eventer som havner i loggen med lavere id enn gjeldende posisjon. Det gjelder alle
  projeksjoner og er ikke løst her.
