# Ekspertbistand Backend API

Denne applikasjonen er en del av migrering ut av altinn 2 skjema. 
I ny løsning er hele løsningen eid og forvaltet av Nav, og skjemaet er ikke lenger et altinn skjema.

## Eksisterende løsning som denne appen skal erstatte

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
Deretter sendes tilsagnsbrevet til altinn 2 innboks for arbeidsgiver. `tiltak-tilsagnsbrev` gjør dette for flere tiltak, så her må vi koordinere med Team Tiltak 
og be dem skru av behandlingen av ekspertbistand når vi er klare til det. 

Ny løsning for tilsagnsbrev vil gjøre følgende:
* lytte på kafka topic `aapen-arena-tilsagnsbrevgodkjent-v1`
* filtrere på tiltakstype `EKSPEBIST`
* Opprette "Godkjent søknad" i ekspertbistand sin database og gjøre dette tilgjengelig via et REST endepunkt og frontend
* opprette beskjed i produsent-api / Min side arbeidsgiver med lenke til ekspertbistand appen
  * Her kan vi også ta i bruk fremtidig status, påminnelser og andre muligheter som ligger i produsent-api
  * "Din søknad er godkjent, du kan nå starte tiltaket. Husk å søke om refusjon når tiltaket er gjennomført"
  * Påminnelse om å søke om refusjon etter X uker dersom det ikke er søkt om refusjon.

### Søknad om Refusjon (Fremtidig løsning)

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







