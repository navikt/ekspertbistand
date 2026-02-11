# Ekspertbistand Backend API

Denne applikasjonen er en del av migrering ut av altinn 2 skjema. 
I ny løsning er hele løsningen eid og forvaltet av Nav, og skjemaet er ikke lenger et altinn skjema.

## Beskrivelse av applikasjonen

Ekspertbistand backend er en applikasjon som håndterer søknader om ekspertbistand fra arbeidsgivere. 
Den mottar søknader fra web/frontend via et REST endepunkt, validerer og lagrer dem i en database, og håndterer videre prosessering av søknadene, inkludert opprettelse av saker i arena og kommunikasjon med produsent-api / Min side arbeidsgiver for å opprette beskjeder og statusoppdateringer.
Backend applikasjonen består av et rest endepunkt, en database for lagring av søknader og tilhørende data, og integrasjoner med eksterne systemer som arena og produsent-api.
Løsningen er utviklet i kotlin med ktor og bruker postgresql for datalagring. Forretningslogikk er frakoblet fra rest endepunktet via en Event sourcing arkitektur, hvor rest endepunktet kun er ansvarlig for å motta og validere søknader, og deretter trigge hendelser som håndteres av separate event handlers som inneholder all forretningslogikk.
se [egen README.md](src/main/kotlin/no/nav/ekspertbistand/event/README.md) for mer informasjon om denne arkitekturen.


## Ny løsning for Ekspertbistand

Når en søknad mottas starter følgende prosess i applikasjonen:

1. Søknaden valideres og lagres i databasen og prosess trigges for videre behandling. (SoknadInnsendt hendelse)
2. Avgjør behandlende enhet:
   - Sjekk Adressebeskyttelse i pdl for arbeidstaker
     - Hvis kode 6 aka. SPSF (Sperret adresse, strengt fortrolig) (PDL: STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND)
       - hent geotilknytning for arbeidstaker fra pdl, default NAV_VIKAFOSSEN:2103 dersom mangler
       - slå opp behandlende enhet i norg for geotilknytning og diskresjonskode SPSF
     - Hvis kode 7 aka. SPFO (Sperret adresse, fortrolig) (PDL: FORTROLIG)
       - rutes som normalt *
     - hvis ingen adressebeskyttelse
       - rutes som normalt *
     - normalt *:
       - slå opp kommunenr for virksomhet og slå opp behandlende enhet i norg for kommunenr
     - Untak: dersom behandlende enhet er NAV_ARBEIDSLIVSSENTER_NORDLAND:1891 må denne mappes om til 1899 som er NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA
3. opprett og ferdigstill journalpost
4. opprett sak i arena og ta vare på saksnummer
6. send bekreftelse til arbeidsgiver via notifikasjonsplattformen
7. Når vedtak blir gjort i arena får denne applikasjonen vite det via en melding på kafka topic i arena. Dette håndteres i dag av tiltak-tilsagnsbrev applikasjonen.
   Siden tilsagnsbrev håndteres av to applikasjoner sørger vi for at det ikke blir dobbelthåndtering av vedtak ved at vi har en dato toggle i denne applikasjonen og i tiltak-tilsagnsbrev som styrer hvilken applikasjon som håndterer vedtak. Toggle skrur av behandling av vedtak i tiltak-tilsagnsbrev når denne applikasjonen er klar til å håndtere vedtak.
   Dersom vedtaket er gjort på en søknad som er sendt inn i gammel løsning, gir vi fortsatt beskjed til arbeidsgiver, men lenker til en egen side som kun inneholder tilskuddsbrevet og informasjon om at søknaden er godkjent. 
   Dersom vedtaket er gjort på en søknad som er sendt inn i ny løsning, oppdaterer vi status i vår database og gir beskjed til arbeidsgiver med lenke til ekspertbistand appen hvor de kan se at søknaden er godkjent og få informasjon om hva de skal gjøre videre.
   Dersom det er et avslag så håndteres dette kun for søknader sendt inn i ny løsning.

## Fremtidig løsning, Søknad om Refusjon (ikke i scope nå)

Når en arbeidsgiver har gjennomført et tiltak med ekspertbistand, kan de søke om refusjon av kostnadene. Dette skal typisk gjøres innen 6 måneder etter at tiltaket er gjennomført.
I dag er dette en manuell prosess som gjøres på papirskjema og sendes til Nav. Dette skal vi nå digitalisere.

Ny løsning for søknad om refusjon vil gjøre følgende:
* motta søknad via et REST endepunkt
* lagre søknaden i ekspertbistand sin database
* Opprette sak og beskjer i produsent-api / Min side arbeidsgiver med lenke til ekspertbistand appen
  * Her kan vi også ta i bruk fremtidig status, påminnelser og andre muligheter som ligger i produsent-api
  * "Vi har mottatt din søknad om refusjon, normalt vil pengene være på konto innen X uker"
* Sende søknaden til saksbehandling i Nav
* Lytte på utbetalingstatus fra Nav via kafka topic og oppdatere status i ekspertbistand sin database
* Opprette beskjed i produsent-api / Min side arbeidsgiver med lenke til ekspertbistand appen
  * "Din søknad om refusjon er godkjent, pengene er på vei til din konto"


## Gammel løsning som denne applikasjonen erstatter

### mottak av ekspertbistand skjema i altinn 2

I dagens løsning er ekspertbistand et skjema i altinn 2.
Skjemaet har tjenestekode og version: (5384:1 Søknad om tilskudd til ekspertbistand)
Skjemaet fylles ut i altinn og mottas av Nav via kanalmottak https://confluence.adeo.no/spaces/TAD/pages/90553562/Verdikjeder og plukkes opp av appen https://github.com/navikt/dokumentfordeling.
dokumentfordeling integrerer med gosys og arena for opprettelse av sak og oppgave. 
Denne flyten erstattes i sin helhet i første versjon av denne applikasjonen.
Det betyr at vi kun trenger å slå av altinn 2 skjema for å skru av den gamle løsningen for ekspertbistand.

Ny løsning for mottak vil da oppsummert gjøre følgende:
* motta søknad via et REST endepunkt
* opprette sak/oppgave tilsvarende det dokumentfordeling gjør i dag
* Opprette sak og beskjer i produsent-api / Min side arbeidsgiver med lenke til ekspertbistand appen
  * Her kan vi også ta i bruk fremtidig status, påminnelser og andre muligheter som ligger i produsent-api
  * "Vi har mottatt din søknad, typisk svartid er ca 1 uke"

### Godkjent søknad (tildligere kalt tilsagnsbrev) 

Når en søker får godkjent sin søknad om ekspertbistand, kalles det i dag et tilsagn, og det sendes et tilsagnsbrev til søker.
Dette trigges i dag ved at saksbehandler i arena oppretter et tilsagn, og det sendes en melding til altinn 2 innboks. I dag er dette tilgangsstyrt på 
en annen tjenestekode (5278:1 Tilskuddsbrev NAV-tiltak) noe som gjør det vanskelig for arbeidsgiver.
Dette skjer ved at applikasjonen https://github.com/navikt/tiltak-tilsagnsbrev lytter på kafka topic som arena skriver til (`aapen-arena-tilsagnsbrevgodkjent-v1`).
Deretter sendes tilsagnsbrevet til altinn 2 innboks for arbeidsgiver. 









