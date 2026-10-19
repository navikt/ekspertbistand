# Statistikk fra ekspertbistand

Ekspertbistand skriver én tabell i BigQuery som dekker to behov:

- felles saksbehandlingsstatistikk hos Team Sak («Teknisk beskrivelse av behov til felles saksbehandlingsstatistikk»)
- tiltaksstatistikk i datavarehuset, som i dag får ekspertbistand fra Arena (tiltakskode `EKSPEBIST`)

Dokumentet beskriver hva vi sender, hvilket felt som svarer til hvilket behov, og hva som mangler og hvorfor.
Feltnavnene er våre egne. Tabellene under viser hvilket av feltene våre som dekker feltene Team Sak og
datavarehuset har bedt om.

## Status

| Tema | Status |
|------|--------|
| Skriving til BigQuery | Ferdig i dev |
| Prod | Datasettet finnes, men er tomt. Tabellen kommer når ekspertbistand fatter vedtak selv i prod. Til da behandles alle saker i Arena |

## Tilgang

| | |
|-|-|
| Datasett | `ekspertbistand_statistikk` i GCP-prosjektet til teamet vårt |
| Tabell | `saksbehandlingsstatistikk` |

Vi eier datasettet. Nais oppretter det fra `spec.gcp.bigQueryDatasets` i manifestet til `ekspertbistand-backend`,
og appen oppretter tabellen første gang den skriver. Team Sak og datavarehuset leser tabellen direkte i BigQuery.
Vi leverer ingen kopi eller fil.

### Tilgang via Datamarkedsplassen

Vi deler tabellen som et dataprodukt på [Datamarkedsplassen](https://data.ansatt.nav.no), slik
[Nav-dokumentasjonen for dataprodukter](https://docs.knada.io/dataprodukter/dele/dataprodukt/) beskriver.
Datamarkedsplassen gir tilgangen i BigQuery, så vi gir ikke tilgang direkte i GCP.

Slik gjør vi det:

1. Vi registrerer dataproduktet på Datamarkedsplassen med team, beskrivelse og kontaktpunkt.
2. Vi legger tabellen `saksbehandlingsstatistikk` til som datasett i dataproduktet. Tabellen må finnes først. I
   prod finnes den når projeksjonen har skrevet første rad.
3. Team Sak og datavarehuset søker om tilgang til datasettet på Datamarkedsplassen. Søknaden gjelder en bruker, en
   gruppe eller en servicebruker, og må vise til en behandling i
   [Behandlingskatalogen](https://behandlingskatalog.nais.adeo.no/).
4. Vi godkjenner søknaden når behandlingen finnes. Datamarkedsplassen gir da lesetilgang til tabellen.

Tabellen inneholder `fnr`. Trenger en mottaker ikke fødselsnummeret i klartekst, kan vi registrere et
pseudonymisert view av tabellen på Datamarkedsplassen i stedet. Se
[tilgangsstyring](https://docs.knada.io/dataprodukter/tilgangsstyring/) for detaljer.

Den som registrerer dataproduktet eller godkjenner søknader, må ha lagt til Google Cloud Platform i
[myapps.microsoft.com](https://myapps.microsoft.com) og være medlem av teamet vårt.

### Innsiden og utsiden

[Kom i gang med dataprodukter](https://docs.knada.io/dataprodukter/) skiller mellom data på innsiden og data på
utsiden. Hos oss er hendelsesloggen i Postgres innsiden. Den endrer seg når vi utvikler løsningen. Tabellen i
BigQuery er utsiden: en stabil modell for statistikk, som appen skriver direkte. Vi har ingen råsone i BigQuery.

Endrer vi tabellen på en måte som bryter for mottakerne, eller vil vi skjule duplikater og eldre versjoner av
projeksjonen, kan vi legge et view foran tabellen og registrere viewet på Datamarkedsplassen i stedet.
Datamarkedsplassen autoriserer viewet selv når det ligger i samme BigQuery-datasett som tabellen. Skjemaet til et
view oppdateres ikke av seg selv, så viewet må lages på nytt når kolonnene endres.

## Slik fungerer det

1. Alt som skjer i en sak, lagres som hendelser i en hendelseslogg i ekspertbistand.
2. `SaksbehandlingsstatistikkProjection` leser loggen i rekkefølge. For hver hendelse som endrer noe i meldingen,
   skriver den én ny rad i BigQuery.
3. Radene endres eller slettes aldri. En ny rad for samme behandling erstatter den forrige.

Bare saker som behandles i ekspertbistand kommer med. Saker som behandles ferdig i Arena, rapporteres fortsatt
fra Arena.

Hos oss mottas søknaden og opprettes saken i samme operasjon. Derfor sender vi ingen egen melding for mottatt
søknad. Første rad kommer når saken er opprettet, og `mottatt_tid` står på alle rader.

### Begreper

| Begrep | Hos Team Sak | Hos datavarehuset | I ekspertbistand |
|--------|--------------|-------------------|------------------|
| Sak | Sak | Tiltaksgjennomføring | Saken som opprettes når søknaden er journalført (`sak_id`) |
| Behandling | Behandling | Tiltaksdeltakelse | Behandlingen av søknaden (`behandling_id`, lik søknadens id) |
| Hendelse | Hendelse | Endring | Tildeling, foreløpig vedtak, retur fra beslutter, vedtak |
| Melding | Rad i BigQuery | Rad i leveransen | Rad i `saksbehandlingsstatistikk` |

Ekspertbistand er et individuelt tiltak. Hver søknad gjelder én arbeidstaker hos én arbeidsgiver, så det er én
gjennomføring per deltakelse.

Søknaden er den eneste behandlingen vi rapporterer. Refusjon og sluttrapport er ikke med. Trengs de senere, kan de
legges til som egne behandlinger med søknadens id i `relatert_behandling_id`.

### Lese tabellen

- `sekvensnummer` er id-en til hendelsen i hendelsesloggen vår. Den øker, men har hull.
- Gjeldende tilstand for en behandling er raden med høyest `sekvensnummer` for `behandling_id`.
- BigQuery fjerner de fleste duplikater, men ikke alle. Får vi feil etter at en rad er skrevet, skriver vi den på
  nytt. Den nye raden har samme `sekvensnummer` og `projeksjon` og ny `melding_tid`. Behold én rad per
  `projeksjon` og `sekvensnummer`.
- Vi kan sende alle meldinger på nytt, for eksempel etter en feilretting. De nye radene har samme `hendelse_tid` og
  `sekvensnummer`, ny `melding_tid` og ny verdi i `projeksjon`. Bruk den nyeste `projeksjon` hvis dere bare vil ha
  én versjon av historikken.

Eksempel på gjeldende tilstand per behandling:

```sql
SELECT *
FROM `<prosjekt>.ekspertbistand_statistikk.saksbehandlingsstatistikk`
WHERE projeksjon = 'Saksbehandlingsstatistikk-v1'
QUALIFY ROW_NUMBER() OVER (PARTITION BY behandling_id ORDER BY sekvensnummer DESC, melding_tid) = 1
```

## Feltene i tabellen

| Felt | Type | Innhold |
|------|------|---------|
| `sekvensnummer` | INT64 | Id-en til hendelsen i hendelsesloggen vår |
| `sak_id` | STRING | Sakens id i ekspertbistand |
| `sak_nummer` | STRING | Samme som `sak_id` |
| `behandling_id` | STRING | Søknadens id |
| `fnr` | STRING | Fødselsnummeret til arbeidstakeren som skal ha ekspertbistand |
| `hendelse_tid` | TIMESTAMP | Når hendelsen skjedde |
| `melding_tid` | TIMESTAMP | Når raden ble skrevet |
| `mottatt_tid` | TIMESTAMP | Når arbeidsgiver sendte inn søknaden |
| `registrert_tid` | TIMESTAMP | Når saken ble opprettet |
| `ferdigbehandlet_tid` | TIMESTAMP | Når vedtaket ble fattet |
| `forventet_oppstart_tid` | DATE | Ønsket oppstart fra søknaden |
| `sak_ytelse` | STRING | Alltid `EKSPERTBISTAND` |
| `sak_utland` | STRING | Alltid `NASJONAL` |
| `behandling_type` | STRING | Alltid `SOKNAD` |
| `behandling_status` | STRING | Se «Kodeverk» |
| `behandling_resultat` | STRING | Se «Kodeverk». Midlertidig når saken ligger hos beslutter, endelig når status er `AVSLUTTET` |
| `resultat_begrunnelse` | STRING | Årsaker til retur fra beslutter, se «Kodeverk» |
| `behandling_metode` | STRING | `AUTOMATISK` til første manuelle hendelse, deretter `MANUELL` |
| `opprettet_av` | STRING | Alltid `EKSPERTBISTAND`, fordi systemet oppretter saken. Unntatt `-5` ved strengt fortrolig adresse |
| `saksbehandler` | STRING | Nav-ident til saksbehandler. Kan være tom, se under |
| `ansvarlig_beslutter` | STRING | Nav-ident til beslutter |
| `ansvarlig_enhet` | STRING | Enhetsnummeret til behandlende Nav-enhet, for eksempel `0315` |
| `vedtaksloesning_navn` | STRING | Alltid `EKSPERTBISTAND` |
| `vedtaksloesning_versjon` | STRING | Versjonen av appen som skrev raden |
| `tiltakskode_arena` | STRING | Alltid `EKSPEBIST` |
| `arrangor_orgnr` | STRING | Virksomhetsnummeret til arbeidsgiveren som søkte. Arena bruker samme nummer som arrangør |
| `estimert_antall_timer` | INT64 | Antall timer med ekspertbistand arbeidsgiver har anslått i søknaden, for hele perioden |
| `ekspert_virksomhet_orgnr` | STRING | Organisasjonsnummeret til virksomheten eksperten kommer fra. Tom for eldre søknader |
| `ekspert_utdanning` | STRING | Ekspertens offentlig godkjente utdanning eller autorisasjon, se «Kodeverk» |
| `ekspert_kompetanse` | STRING | Ekspertens relevante kompetanse for saken, se «Kodeverk» |
| `innvilget_belop` | INT64 | Innvilget beløp i kroner |
| `tiltaksperiode_fra` | DATE | Første dag i innvilget periode |
| `tiltaksperiode_til` | DATE | Siste dag i innvilget periode |
| `projeksjon` | STRING | Versjonen av projeksjonen som skrev raden |

Feltene fra `tiltakskode_arena` til `tiltaksperiode_til` er tillegg for datavarehuset. Team Sak kan se bort fra dem.

`saksbehandler` er tom fra saken er opprettet til en saksbehandler tar den, og etter at saksbehandleren har
frigjort den. Saken ligger da i en felles kø uten ansvarlig saksbehandler, så vi har ingen ident å sende.

`innvilget_belop` og tiltaksperioden kommer fra saksbehandlers foreløpige vedtak. De vises mens saken ligger hos
beslutter, blir tomme hvis beslutter sender saken i retur, og står fast når status er `AVSLUTTET`. Bruk dem som
endelige verdier bare når `behandling_status = 'AVSLUTTET'`.

## Felt til Team Sak

| Felt hos Team Sak | Vårt felt | Merknad |
|-------------------|-----------|---------|
| `sak_id` | `sak_id` | |
| `sak_nummer` | `sak_nummer` | |
| `behandling_id` | `behandling_id` | |
| `behandling_nummer` | mangler | Vi har ikke behandlingsnummer |
| `relatert_behandling_id` | mangler | Søknaden er den eneste behandlingen vi rapporterer |
| `relatert_vedtaksloesning` | mangler | Som over |
| `aktor_id` eller `fnr` | `fnr` | |
| `hendelse_tid` | `hendelse_tid` | |
| `melding_tid` | `melding_tid` | |
| `mottatt_tid` | `mottatt_tid` | |
| `registrert_tid` | `registrert_tid` | |
| `ferdigbehandlet_tid` | `ferdigbehandlet_tid` | |
| `utbetalt_tid` | mangler | Utbetaling skjer etter refusjon, som vi ikke rapporterer |
| `forventet_oppstart_tid` | `forventet_oppstart_tid` | |
| `sak_ytelse` | `sak_ytelse` | Alltid `EKSPERTBISTAND` |
| `sak_utland` | `sak_utland` | Alltid `NASJONAL` |
| `behandling_type` | `behandling_type` | Alltid `SOKNAD` |
| `behandling_status` | `behandling_status` | |
| `behandling_resultat` | `behandling_resultat` | |
| `resultat_begrunnelse` | `resultat_begrunnelse` | Bare ved retur fra beslutter. Avslagsgrunn er ikke med, se under |
| `behandling_metode` | `behandling_metode` | |
| `behandling_aarsak` | mangler | Alle behandlinger er søknader. Vi har ingen årsakskoder |
| `opprettet_av` | `opprettet_av` | Alltid `EKSPERTBISTAND`, unntatt `-5` ved strengt fortrolig adresse |
| `saksbehandler` | `saksbehandler` | Påkrevd hos Team Sak, men kan være tom hos oss. Se «Feltene i tabellen» |
| `ansvarlig_beslutter` | `ansvarlig_beslutter` | |
| `ansvarlig_enhet` | `ansvarlig_enhet` | Team Sak ber om organisasjons-id. Vi sender enhetsnummeret |
| `funksjonell_periode_fom`, `funksjonell_periode_tom`, `tilbakekrevingsbeløp` | mangler | Gjelder bare tilbakekreving |
| `vedtaksløsning_navn` | `vedtaksloesning_navn` | Alltid `EKSPERTBISTAND` |
| `vedtaksløsning_versjon` | `vedtaksloesning_versjon` | |
| `sekvensnummer` | `sekvensnummer` | |

Saksbehandler skriver begrunnelsen for avslag som fritekst. Vi har ingen koder for avslagsgrunn, og fritekst kan
inneholde helseopplysninger. Derfor er avslagsgrunn ikke med i statistikken.

## Felt til datavarehuset

### Tiltakstype

Tiltakstypen er fast og ligger ikke som egne rader i tabellen.

| Felt hos datavarehuset | Verdi | Merknad |
|------------------------|-------|---------|
| Tiltakskode | `EKSPERTBISTAND` | Alltid `EKSPERTBISTAND` i `sak_ytelse` |
| Tiltakskode_Arena | `EKSPEBIST` | Alltid `EKSPEBIST` i `tiltakskode_arena` |
| Tiltaksnavn | Ekspertbistand | |
| Startdato | sendes ikke | Samme verdi for alle rader. Datavarehuset kan legge den til selv |
| Sluttdato | sendes ikke | Ingen planlagt slutt |
| Opprettet_dato, Endret_dato | sendes ikke | Tiltakstypen er ikke registrert som data hos oss |

### Tiltaksgjennomføring

| Felt hos datavarehuset | Vårt felt | Merknad |
|------------------------|-----------|---------|
| GjennomføringsID | `sak_id` | |
| Tiltakskode | `sak_ytelse` | Alltid `EKSPERTBISTAND` |
| Startdato | `tiltaksperiode_fra` | Når saken er `AVSLUTTET` med innvilgelse |
| Sluttdato | `tiltaksperiode_til` | Som over |
| Arrangør | `arrangor_orgnr` | Arbeidsgiveren, som i Arena |
| Avtale | mangler | Ekspertbistand har ingen avtaler |
| Ekstern_id | mangler | Sakene våre finnes ikke i Arena |
| Opprettet_dato | `registrert_tid` | |
| Endret_dato | `hendelse_tid` | På siste rad for behandlingen |

### Tiltaksdeltakelse

| Felt hos datavarehuset | Vårt felt | Merknad |
|------------------------|-----------|---------|
| DeltakerID | `behandling_id` | |
| GjennomføringsID | `sak_id` | |
| PersonIdent | `fnr` | |
| Startdato | `tiltaksperiode_fra` | |
| Sluttdato | `tiltaksperiode_til` | |
| Status | mangler | Se under |
| Ekstern_id | mangler | Sakene våre finnes ikke i Arena |
| Opprettet_dato | `registrert_tid` | |
| Endret_dato | `hendelse_tid` | På siste rad for behandlingen |

Vi har ikke deltakerstatus i løsningen vår, og sender den derfor ikke. Datavarehuset kan utlede den fra
`behandling_status`, `behandling_resultat`, `tiltaksperiode_fra` og `tiltaksperiode_til` på siste rad, sammen med
dagens dato. Vi vet ikke om bistanden ble fullført eller avbrutt, fordi sluttrapport og refusjon ikke er med.

### Felt som gjelder ekspertbistand

| Spørsmål fra datavarehuset | Vårt felt | Merknad |
|----------------------------|-----------|---------|
| Hva slags ekspertbistand blir gitt? | `ekspert_utdanning`, `ekspert_kompetanse` | Se «Kodeverk» |
| Hvor mange timer per uke eller måned? | `estimert_antall_timer` | Se under |
| Andre nyttige felt | `innvilget_belop`, `ekspert_virksomhet_orgnr`, `ansvarlig_enhet` | |

`estimert_antall_timer` er arbeidsgiverens svar på «Hvor mange timer skal eksperten hjelpe dere?» i søknaden,
altså totalt for hele bistanden. Saksbehandler kontrollerer ikke tallet og vedtar beløp, ikke timer. Et omtrentlig
snitt per måned kan regnes ut med antall måneder fra `tiltaksperiode_fra` til `tiltaksperiode_til`.

## Kodeverk

Kodene under er våre egne. Team Sak og datavarehuset må kontrollere at de passer med kodeverkene deres, særlig
`TIL_BESLUTNING`, `UNDERKJENT_BESLUTTER`, `AVSLATT` og `EKSPERTBISTAND`.

### `behandling_status`

| Kode | Når |
|------|-----|
| `REGISTRERT` | Saken er opprettet |
| `UNDER_BEHANDLING` | Første gang en saksbehandler tar saken |
| `TIL_BESLUTNING` | Saksbehandler har sendt foreløpig vedtak til beslutter |
| `UNDERKJENT_BESLUTTER` | Beslutter har sendt saken tilbake. Står til saksbehandler sender på nytt |
| `AVSLUTTET` | Beslutter har fattet vedtak. Siste melding for behandlingen |

### `behandling_resultat`

| Kode | Betyr |
|------|-------|
| `INNVILGET` | Innvilget som søkt |
| `DELVIS_INNVILGET` | Innvilget, men redusert i forhold til søknaden |
| `AVSLATT` | Avslått |

### `resultat_begrunnelse`

Brukes når beslutter sender saken i retur. Flere årsaker skilles med komma, sortert alfabetisk.

| Kode | Årsak i ekspertbistand |
|------|------------------------|
| `FEIL_LOVANVENDELSE` | Feil regelforståelse |
| `FEIL_FAKTUM` | Feil i faktagrunnlaget |
| `ANNET` | Annet |

### `ekspert_utdanning` og `ekspert_kompetanse`

Arbeidsgiver velger fra en liste i søknaden og kan også skrive inn egne verdier. Egne verdier blir `ANNET`,
fordi fritekst ikke kan grupperes og kan inneholde personopplysninger. Flere valg skilles med komma, sortert
alfabetisk, og hver kode kommer bare én gang. Eksempel: `ANNET,PSYKOLOG`.

| `ekspert_utdanning` | Valg i søknaden |
|---------------------|-----------------|
| `ERGOTERAPEUT` | Ergoterapeut |
| `FYSIOTERAPEUT` | Fysioterapeut |
| `KARRIEREVEILEDER` | Karriereveileder |
| `PSYKOLOG` | Psykolog |
| `SYKEPLEIER` | Sykepleier |
| `ANNET` | Egen verdi, eller fritekst fra eldre søknader |

| `ekspert_kompetanse` | Valg i søknaden |
|----------------------|-----------------|
| `RELASJONER_OG_KOMMUNIKASJON` | Relasjoner og kommunikasjon på arbeidsplassen |
| `TILRETTELEGGING` | Tilrettelegging på arbeidsplassen |
| `KONFLIKTHANDTERING` | Konflikthåndtering |
| `OMSTILLINGSARBEID` | Omstillingsarbeid (endring i arbeidslivet) |
| `ARBEIDSPLASSVURDERING` | Arbeidsplassvurdering |
| `OFFENTLIGE_VIRKEMIDLER` | God kjennskap til offentlige virkemidler |
| `KARRIEREVEILEDNING` | Karriereveiledning |
| `ANNET` | Egen verdi |

Nye valg i søknaden gir `ANNET` til vi har lagt dem inn i kodeverket.

## Personvern

### Strengt fortrolig adresse (kode 6)

Når saken behandles av Nav Vikafossen (enhet `2103`), har arbeidstakeren strengt fortrolig adresse. Da får disse
feltene verdien `-5`:

- `opprettet_av`, `saksbehandler` og `ansvarlig_enhet`
- `ansvarlig_beslutter`, men bare når feltet har en verdi
- `arrangor_orgnr` og `ekspert_virksomhet_orgnr`, fordi arbeidsgiveren og eksperten kan avsløre hvor personen bor

### Det vi ikke sender

| Opplysning | Hvorfor |
|------------|---------|
| Ekspertens navn | Personopplysning om eksperten. Ikke nødvendig for statistikk |
| Arbeidstakerens navn | `fnr` er nok til å koble mot andre data |
| Kontaktperson hos arbeidsgiver og i Nav | Personopplysninger. Ikke nødvendig for statistikk |
| Fritekst fra søknad og vedtak (behov, begrunnelse, avslagsgrunn, tilrettelegging) | Kan inneholde helseopplysninger |
| Egne verdier for utdanning og kompetanse | Sendes som `ANNET`, se «Kodeverk» |
| Arbeidsgiverens anslåtte kostnad | Ikke med nå. Kan legges til hvis datavarehuset trenger det |

`fnr` skal bare brukes til statistikk og må ikke logges.

## For utviklere

- Koden ligger i denne pakken. `SaksbehandlingsstatistikkProjection` lager radene, og
  `BigQuerySaksbehandlingsstatistikkSkriver` skriver dem.
- Projeksjonen lagrer ingen tilstand. For hver hendelse bygger den tilstanden fra tidligere hendelser i loggen med
  samme aggregatrot (søknad-id). Indeksen `event_log_aggregate_root_id_idx` gjør oppslaget raskt.
- Bump versjonen i `name` for å sende alle meldinger på nytt.
- Nye kolonner må legges til i tabellen i BigQuery før appen skriver dem. Appen oppretter tabellen, men endrer den
  ikke.
- Kolonnebeskrivelser legges inn i BigQuery-konsollet. Datamarkedsplassen henter skjema og beskrivelser fra
  BigQuery én gang i timen.
- Lokalt og i tester skriver projeksjonen ikke til BigQuery.
- Planen og beslutningene står i `specifications/saksbehandlingsstatistikk.md`.
