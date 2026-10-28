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
| Melding | En rad i BigQuery-tabellen `ekspertbistand_statistikk.saksbehandlingsstatistikk`. Mottakerne leser viewet `saksbehandlingsstatistikk_delt` |

## Avklarte beslutninger
| Tema | Valg |
|------|------|
| Hvilke saker | Bare saker med kilde EKSPERTBISTAND, altså saker der `SakOpprettet` finnes. Arena-saker vedtas i Arena og gir ingen meldinger |
| Miljø | Registreres bare utenfor prod, som `OpprettSak`. Skrus på i prod når vi fatter vedtak der. Datasettet finnes allerede i prod. Tabellene og viewet kommer når projeksjonen starter, og først da kan viewet registreres på Datamarkedsplassen |
| Lagring | Appen skriver direkte til BigQuery-datasettet `ekspertbistand_statistikk`, som Nais oppretter fra `spec.gcp.bigQueryDatasets`. Ingen tabell i Postgres |
| Tilgang | Bare viewet `saksbehandlingsstatistikk_delt` registreres som datasett i et dataprodukt på Datamarkedsplassen, som autoriserer det mot tabellene i samme BigQuery-datasett. Tabellene deles ikke. Team Sak og datavarehuset søker om tilgang der, med en behandling i Behandlingskatalogen. Vi gir ikke tilgang direkte i GCP |
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
| Adressebeskyttelse | Adressebeskyttede behandlinger holdes helt utenfor viewet, med all historikk. Ellers kan rader fra før personen fikk adressebeskyttelse avsløre både hvem som har den og hvor de jobber. En behandling holdes utenfor hvis deltakeren hadde adressebeskyttelse ved saksopprettelse (felt på `SakOpprettet`, egen oppgave), har det i PDL når en rad skrives, eller får det senere (`Adressebeskyttelseskontroll` hver time). Alt annet enn `UGRADERT` (kode 6 og 7, også ukjente verdier) regnes som adressebeskyttelse. Behandlende enhet brukes ikke som signal. En behandling slippes aldri inn igjen. Rådata får i tillegg `-5` (maskert) i `opprettet_av`, `saksbehandler`, `ansvarlig_beslutter`, `ansvarlig_enhet`, `arrangor_orgnr` og `ekspert_virksomhet_orgnr`. Vi sletter ikke rader i BigQuery, fordi `insertAll` hindrer DML de første minuttene og time travel beholder data. «Skjerming» brukes ikke her: i Nav betyr det egne ansatte, som ikke påvirker statistikken |
| Datavarehus | Ekstra kolonner i samme tabell. Feltnavnene er våre egne. Mappingen står i `statistikk/README.md` |
| Ekspertens kompetanse | Faste valg blir koder (`EkspertStatistikkoder.kt`). Egne verdier og fritekst blir `ANNET`, fordi fritekst ikke kan grupperes og kan inneholde personopplysninger |
| Timer og kostnad | Arbeidsgiverens anslag fra søknaden. Saksbehandler kontrollerer dem ikke |
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
- `interface AdressebeskyttedeBehandlinger { fun registrer(behandlingIder); fun utenAdressebeskyttelse(): Map<behandlingId, fnr> }`.
- `SaksbehandlingsstatistikkRad` har ett felt per kolonne og `insertId`. `toString` tar ikke med `fnr`.
- `BigQuerySaksbehandlingsstatistikk` implementerer begge. Første gang oppretter den `saksbehandlingsstatistikk` og
  `adressebeskyttede_behandlinger` hvis de mangler, og oppretter eller oppdaterer viewet `saksbehandlingsstatistikk_delt`
  (`behandling_id NOT IN adressebeskyttede_behandlinger`). Den skriver med `insertAll` og leser med `listTableData`, så
  appen trenger ikke å kjøre spørrejobber. Feil gir unntak, slik at `EventLogProjectionBuilder` prøver på nytt. Feilmeldingen inneholder ikke radverdier.
- Én liste med kolonner gir både BigQuery-skjemaet og verdiene som sendes. Tidspunkt og datoer sendes som ISO 8601.
- `Application.kt` gir samme BigQuery-instans for begge grensesnittene. `LocalApplication.kt` gir varianter som ikke
  gjør noe.
- `statistikk/Adressebeskyttelseskontroll.kt`: henter `utenAdressebeskyttelse()`, slår opp hver person i PDL og
  registrerer alle behandlingene til dem som har adressebeskyttelse. Den registrerer det den finner, også når enkelte
  oppslag feiler, og feiler så. `configureAdressebeskyttelseskontroll()` kjører den hver time utenfor prod, som
  projeksjonen.
- `statistikk/AdressebeskyttelseOppslag.kt`: `fun interface AdressebeskyttelseOppslag` og
  `PdlAdressebeskyttelseOppslag`, som bruker `PdlApiKlient.hentAdressebeskyttelse`. Begge applikasjonene gir
  PDL-varianten. Lokalt svarer den mockede PDL-klienten `UGRADERT`.

### 4. `statistikk/SaksbehandlingsstatistikkProjection.kt`
`class SaksbehandlingsstatistikkProjection(database, skriver, adressebeskyttelse, adressebeskyttede) : EventLogProjectionBuilder(database)`,
`name = "Saksbehandlingsstatistikk-v1"`.

- `handle` bygger forrige tilstand fra `event_log` (samme `aggregate_root_id`, lavere id, status `COMPLETED`),
  kaller den rene funksjonen `nesteTilstand(forrige, eventId, data, eventTimestamp)` og skriver en rad hvis
  `meldingsinnhold()` er endret.
- `nesteTilstand` returnerer `Overgang(tilstand, hendelseTid)`, eller `null` når eventen ikke endrer noe.
- `BehandlingTilstand.meldingsinnhold()` er `null` før `SakOpprettet`. Den er ikke maskert, slik at duplikatsjekken
  sammenligner faktiske verdier.
- Når `AdressebeskyttelseOppslag` (PDL) finner adressebeskyttelse, registrerer `handle` behandlingen i
  `AdressebeskyttedeBehandlinger` før raden skrives, og skriver raden med `Meldingsinnhold.maskert()`. Det samme skjer når `BehandlingTilstand.adressebeskyttetVedOpprettelse` er satt. Den kommer fra `SakOpprettet.adressebeskyttetVedOpprettelse()`, som returnerer `false` til feltet finnes (TODO). Feil fra PDL kastes, så posisjonen ikke flyttes og ingen umaskert rad skrives.
- Enum-ene `StatistikkStatus`, `StatistikkResultat` og `BehandlingMetode` holder kodeverdiene.
- `EkspertStatistikkoder.kt` har `EkspertUtdanning` og `EkspertKompetanse` (kode og etikett fra skjemaet), og
  `DTO.Ekspert.utdanningKoder` og `kompetanseKoder`, som gjør valgene om til koder.

| Event | Effekt | `behandling_status` | `hendelse_tid` |
|-------|--------|---------------------|----------------|
| `SoknadInnsendt` | Nullstiller tilstanden og lagrer `mottatt_tid`. Ingen melding, saken finnes ikke ennå | – | – |
| `InnsendtSoknadJournalfoert` | Lagrer behandlende enhet, mappet til Norg-nummer med `BehandlendeEnhetService.arenaTilNorgEnhetNr`. Ingen melding | – | – |
| `SakOpprettet` | Første melding. Setter `sak_id`, `registrert_tid`, `fnr`, `forventet_oppstart_tid`, `arrangor_orgnr`, `estimert_antall_timer`, `estimert_kostnad` og ekspertfeltene | `REGISTRERT` | tidspunktet i event-loggen |
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
| `sak_ytelse` | Alltid `EKSPERTBISTAND` |
| `sak_utland` | Alltid `NASJONAL` |
| `behandling_type` | Alltid `SOKNAD` |
| `behandling_resultat` | `INNVILGE` → `INNVILGET`, `INNVILGE_REDUSERT` → `DELVIS_INNVILGET`, `AVSLAA` → `AVSLATT` |
| `resultat_begrunnelse` | `FEIL_FAKTA` → `FEIL_FAKTUM`, `FEIL_REGELFORSTAELSE` → `FEIL_LOVANVENDELSE`, `ANNET` → `ANNET`. Flere årsaker sorteres og skilles med komma |
| `behandling_metode` | `AUTOMATISK` på `REGISTRERT`, `MANUELL` fra første handling av saksbehandler eller beslutter |
| `opprettet_av` | Alltid `EKSPERTBISTAND` i viewet (`-5` i rådata ved adressebeskyttelse), fordi systemet oppretter saken når søknaden er journalført |
| `saksbehandler` | Ident fra `SakTildeltSaksbehandler`, `null` før tildeling og etter frigjøring |
| `ansvarlig_beslutter` | Ident fra `SakTildeltBeslutter` eller `VedtakFattet.fattetAv` |
| `ansvarlig_enhet` | Behandlende enhet (Norg-nummer) |
| `vedtaksloesning_navn` | Alltid `EKSPERTBISTAND` |
| `vedtaksloesning_versjon` | Image-taggen fra `NAIS_APP_IMAGE`, eller `lokal` |
| `tiltakskode_arena` | Alltid `EKSPEBIST` (`EKSPERTBISTAND_TILTAKSKODE`) |
| `arrangor_orgnr` | `soknad.virksomhet.virksomhetsnummer`, som Arena bruker som arrangør |
| `estimert_antall_timer` | `behovForBistand.timer` som heltall, `null` hvis verdien ikke er et tall |
| `estimert_kostnad` | Påkrevd. `behovForBistand.estimertKostnad` som tall, avrundet til hele kroner. Projeksjonen stopper hvis verdien ikke er et tall |
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
- Adressebeskyttelse i PDL registrerer behandlingen som adressebeskyttet og maskerer de seks feltene med `-5`
- Adressebeskyttelse etter innsending registrerer behandlingen som adressebeskyttet
- `AdressebeskyttelseskontrollTest`: registrerer alle behandlingene til personer med adressebeskyttelse, og feil i ett
  oppslag hindrer ikke de andre
- Feil mot PDL gir ingen rad før oppslaget lykkes
- `PdlAdressebeskyttelseOppslag` (i `PdlApiKlientTest`): `FORTROLIG`, `STRENGT_FORTROLIG` og
  `STRENGT_FORTROLIG_UTLAND` gir adressebeskyttelse, `UGRADERT` gjør det ikke, ukjent person er ugradert og andre feil kastes
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
- Appen legger til nye kolonner i BigQuery-tabellen første gang den bruker datasettet etter oppstart. Bare valgfrie kolonner kan legges til.
- Rader skrevet før personen fikk adressebeskyttelse, endres ikke i rådata. Viewet skjuler dem. Mottakerne leser
  inkrementelt på `melding_tid` og håndterer adressebeskyttelse i egne kopier selv. Vi gir ikke ut en liste over
  skjulte behandlinger, fordi den ville vist hvem som har adressebeskyttelse.
- Alle med tilgang til viewet får `metadata viewer` på datasettet og kan se antall rader i
  `adressebeskyttede_behandlinger`.
- Oppslaget i PDL skjer inne i projeksjonens databasetransaksjon, ett kall per rad som skrives.
- Kjent begrensning: `SakOpprettet` sier ikke ennå om deltakeren hadde adressebeskyttelse ved saksopprettelse. Til
  feltet kommer (egen oppgave, før prodsetting), avgjør bare PDL, når raden skrives og i kontrolljobben. Står ikke i
  `statistikk/README.md`, fordi mottakerne ikke trenger å vite om det.
- Tabellen het tidligere `skjermede_behandlinger`. Den fikk nytt navn uten kopi, fordi projeksjonen ikke kjører i
  prod. I dev oppretter appen den nye tabellen, og projeksjonen og kontrolljobben fyller den igjen. Den gamle tabellen
  kan slettes.
- Rammeverket hopper over eventer som havner i loggen med lavere id enn gjeldende posisjon. Det gjelder alle
  projeksjoner og er ikke løst her.
