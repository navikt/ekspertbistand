# Postmortem: tilsagnsbrev-consumeren stod fast, og log.error-alerten var ødelagt

- **Dato:** 01.10.2026 (oppdaget), 02.10.2026 (rettet)
- **Tjeneste:** `ekspertbistand-backend` (prod-gcp, namespace `fager`)
- **Status:** Rettet i kode (PR #171). Deploy og opprydding gjenstår, se tiltak.

## Sammendrag

Consumeren for `teamarenanais.aapen-arena-tilsagnsbrevgodkjent-v1` stod fast på én melding og behandlet ingen nye tilsagnsbrev på partition 0. Meldingen var et ekspertbistand-tilsagn fra Arena uten `deltaker`. Koden kastet exception når deltaker manglet, og consumeren prøvde den samme meldingen på nytt hvert 5. sekund.

Vi oppdaget det ikke fordi alerten på `log.error` i Grafana var ødelagt. Datakilden alerten pekte på var fjernet, og regelen evaluerte ikke lenger. Siste varsel fra alerten kom 19.06.2026. Consumeren stod mest sannsynlig fast fra 29.09.2026 kl. 12:42 til fiksen er deployet.

Arena bekrefter at `deltaker` ikke er påkrevd, også for ekspertbistand. Det har skjedd fire ganger siden 2020.

## Konsekvens

Fra 29.09.2026 kl. 12:42 og til fiksen er deployet, ble ingen ekspertbistand-tilsagn bak den feilende meldingen på partition 0 behandlet. For hvert av disse tilsagnene gjelder dette:

- Tilskuddsbrevet ble ikke journalført.
- Søknaden fikk ikke status godkjent.
- Arbeidsgiver fikk ikke beskjed om at søknaden var godkjent, og fikk ikke se tilskuddsbrevet hos oss.

Tilsagnet er gyldig i Arena, så pengene er ikke berørt. Arbeidsgiverne har ventet på svar som Nav allerede har gitt. Antall berørte tilsagn er ikke kartlagt ennå.

Alerten har vært ødelagt i hvert fall siden 24.08.2026, sannsynligvis lenger. I hele denne perioden har vi ikke fått varsel om noen feil i noen av våre apper. Hadde alerten virket, hadde vi visst om den fastlåste consumeren i løpet av minutter 29.09.2026, ikke to og et halvt døgn senere.

## Tidslinje

| Tidspunkt                | Hendelse |
|--------------------------|----------|
| 2020–2025                | Arena godkjenner tre ekspertbistand-tilsagn uten deltaker, det siste i 2025. Alle er fra før vi begynte å behandle topicet. |
| 26.02.2026               | Commit fd09633: `ArenaTilsagnsbrevProcessor` kaster exception når et ekspertbistand-tilsagn mangler `deltaker`. |
| 19.06.2026               | Siste varsel fra alerten «log errors». |
| Ukjent, etter 19.06.2026 | Datakilden alerten bruker blir fjernet eller endret i Grafana. Teamet har ikke gjort endringer her, så vidt vi vet. |
| 03.08.2026               | `log.error` i loggene uten at alerten varslet. Tidligste bevis på at alerten ikke virket. |
| 29.09.2026 12:41:59      | Arena godkjenner et ekspertbistand-tilsagn uten deltaker (key 472349, offset 472125). Consumeren stopper. |
| 01.10.2026 ca. 20:00     | Vi oppdager at alerten på `log.error` er borte. Regelen viser «This datasource has been removed». |
| 01.10.2026               | Vi setter riktig datakilde og legger inn spørringen på nytt. Alerten virker igjen og varsler om feilen i consumeren. |
| 01.10.2026 20:18         | Feilen bekreftet i loggene: `TilsagnsbrevKafkaMelding mangler deltaker. key: 472349`, partition 0, offset 472125. |
| 02.10.2026 ca. 08:40     | PR #171 opprettet med fiks. |
| 02.10.2026               | Arena bekrefter at `deltaker` ikke er påkrevd, og at tilsagnet fra 29.09.2026 mest sannsynlig er meldingen vi står fast på. |
| 02.10.2026               | Alert på consumer lag for `fager.ekspertbistand.tilsagnsbrev` lagt til. |
| Ikke gjort ennå          | Deploy til prod. Consumeren tar igjen etterslepet. |

## Årsak

### Hvorfor consumeren stod fast

Arena-skjemaet for tilsagnsbrev har `deltaker` som valgfritt felt. Vi gjorde feltet nullable, men lot `ArenaTilsagnsbrevProcessor` kaste exception når det manglet for ekspertbistand-tilsagn. Tanken var at vi skulle merke det hvis det skjedde.

Vi leste sysdoken til Arena da vi skrev valideringen, men tolket den feil. Alle eksemplene vi hadde sett, hadde deltaker. Vi trodde derfor at feltet var valgfritt fordi noen tiltakstyper ikke har deltaker, ikke at det kunne mangle innenfor samme tiltakstype.

Arena forklarer hvordan et tilsagn kan bli godkjent uten deltaker:

- I prosessen «Forbered tiltaksgjennomføring» er «Godkjenn tiltaksplass» et obligatorisk trinn før «Registrer tilsagn». Det er der deltakeren knyttes til.
- Søker saksbehandler frem tiltaksgjennomføringen via «Søk tiltaksgjennomføring», kan hen lage oppgaven «Registrer tilsagn» direkte, uten å fullføre trinnene før.
- I tilfellet 29.09.2026 sto deltakeren bare i kommentarfeltet, sammen med en kommentar om at deltaker manglet. Tilsagnet ble likevel godkjent.
- I tilfellet fra 2025 ble deltakeren aldri lagt til på gjennomføringen, men tilsagnet ble utbetalt.

Et tilsagn skal i utgangspunktet ha deltaker, men Arena hindrer det ikke. Vi må håndtere at det mangler.

`CoroutineKafkaConsumer` gjør bevisst ikke skip av meldinger. Ved feil går den tilbake til samme offset og prøver igjen, slik at vi aldri mister meldinger. Sammen betyr disse valgene at én uventet melding blir en poison pill som stopper hele partisjonen til noen retter koden. Det forutsetter at noen blir varslet, og det ble vi ikke.

### Hvorfor vi ikke oppdaget det

Alerten «log errors» ligger bare i Grafana, ikke i kode. Datakilden den pekte på ble fjernet eller fikk ny ID, sannsynligvis etter en endring eller oppgradering i Grafana. Etter det evaluerte ikke regelen, og vi fikk ingen varsel om at alerten selv var ødelagt.

Dette er andre gang noe vi har satt opp i Grafana slutter å virke etter endringer vi ikke har gjort selv. Vi bruker metrikker og alerts for å vite at appen er frisk. Når alerten feiler stille, ser vi ingenting.

Vi manglet også alert på consumer lag for `fager.ekspertbistand.tilsagnsbrev`. Den hadde fanget at consumeren stod fast, uavhengig av loggene. Alerten er nå lagt til. Den dekker bare Kafka. Mange av feilene i appen har ingenting med Kafka å gjøre, og dem ser vi bare via alerten på `log.error`.

## Løsning

PR #171:

- `ArenaTilsagnsbrevProcessor` logger warn og behandler tilsagnet videre når `deltaker` mangler.
- Tilskuddsbrev-malen viser bare raden «Deltakeren» når deltaker finnes.
- Nye tester for tilsagn uten deltaker, både i processoren og i malen.
- Kdoc presiserer at saksnummer bare mangler i `arena_sak` for søknader sendt inn via Altinn 2 i starten etter prodsetting.

Vi valgte å behandle tilsagnet uten deltaker i stedet for å hoppe over meldingen. Tilsagnet er godkjent i Arena, og arbeidsgiver skal ha beskjed og tilskuddsbrev. Ulempen er at brevet ikke navngir den ansatte.

Behandlingen av tilsagnsbrev er idempotent per `tilsagnBrevId`. Etterslepet kan derfor behandles på nytt etter deploy uten at noe blir duplisert.

## Tiltak

### Hindre at alerting feiler stille

Alert på consumer lag er lagt til, men den fanger bare Kafka-feil. Det viktigste tiltaket er derfor å sikre at alerten på `log.error` ikke kan slutte å virke uten at vi merker det. Forslag, i prioritert rekkefølge:

1. **Alerts som kode i repoet.** Backend eksponerer allerede `logback_events_total` via `LogbackMetrics`. En `PrometheusRule` i `nais/` som varsler når antall `level="error"` øker, ligger i git, blir deployet sammen med appen og er ikke avhengig av en datakilde i Grafana. Ulempen er at varselet ikke inneholder selve loggmeldingen. Vi legger inn lenke til Loki i stedet.
2. **Varsle når regelen selv feiler.** Grafana har innstillinger for hva en regel skal gjøre ved «No data» og ved feil under evaluering. Vi må finne ut hvorfor den fjernede datakilden ikke ga varsel: om innstillingene var satt til «Normal», om varselet ble rutet feil, eller om regelen ikke ble evaluert i det hele tatt. Billig å sjekke nå, mens vi vurderer resten.
3. **Heartbeat («ulv ulv»).** En planlagt jobb logger en kjent feil med fast intervall, for eksempel hver mandag kl. 09:00. Den går gjennom samme alert og samme Slack-kanal som ekte feil. Uteblir varselet, vet vi at kjeden er brutt. Ulempen er at noen må legge merke til at noe ikke skjedde. En dead man's switch, der en ekstern tjeneste varsler når et signal *slutter* å komme, løser det, men krever en slik tjeneste.
4. **Alert på consumer lag for alle consumer-gruppene våre**, ikke bare `fager.ekspertbistand.tilsagnsbrev`.

Vi anbefaler 1 og 2. Heartbeat (3) er et mulig tillegg hvis vi beholder alerten på `log.error` i Grafana, og den tester også PrometheusRule-varianten fra ende til ende, men vil medføre støy. Det beste er om vi kan stole på at alerter vi lager ikke fjernes pga endringer utenfor vår kontroll.

## Åpne spørsmål

- Når ble datakilden fjernet, og hvorfor? Bør vi melde fra til Nais om endringen?
- Hva var den første gangen noe i Grafana sluttet å virke? Har det samme årsak?
