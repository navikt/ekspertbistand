# Ekspertbistand

Dette repoet inneholder kode for håndtering av tiltaket Ekspertbistand.
Den består av en frontend-applikasjon for innsending og utlisting av innsendte søknader, samt en backend-tjeneste for
behandling av disse søknadene.

Applikasjonen er utviklet av Team Fager som en del av utfasing av Altinn 2 for NAV.
Den erstatter eksisterende løsning i Altinn 2 for Ekspertbistand.

For spørsmål eller bistand, kontakt Team Fager på Slack: [#team-fager](https://nav-it.slack.com/archives/C01V9FFEHEK)

## Eksisterende funksjonalitet i Altinn 2 som erstattes

Dette repoet erstatter funksjonalitet som tidligere var tilgjengelig i Altinn 2 for Ekspertbistand.
Tidligere sendte arbeidsgiver inn søknader om ekspertbistand via Altinn 2, hvor de ble rutet videre til saksbehandler.
Dette mottaket har vært under utfasing men
er [beskrevet på confluence](https://confluence.adeo.no/spaces/TAD/pages/90553562/Verdikjeder).

Når søknad sendes inn opprettes det en midlertidig journalpost og det sendes en kafka melding som lyttes på
av [Dokumentfordeling](https://github.com/navikt/dokumentfordeling) applikasjonen.
Den applikasjonen ruter så videre journalposten til riktig saksbehandler basert på regler definert i applikasjonen.
Løsningsbeskrivelse for denne appen
finnes [på confluence](https://confluence.adeo.no/spaces/AR/pages/294497858/Dokumentfordeling+-+tiltak+-+Funksjonell+bekrivelse#DokumentfordelingtiltakFunksjonellbekrivelse-Ruting).

## Ny løsning

I den nye løsningen sendes søknader direkte til Ekspertbistand applikasjonen uten å gå via Altinn 2.
Koden for den nye løsningen er i dette repoet, og den erstatter funksjonaliteten beskrevet i forrige avsnitt.
Arbeidsgiver sender inn søknad ved å logge inn i [frontend applikasjonen](/frontend) som er tilgjengelig på
arbeidsgiver.nav.no.
Frontend applikasjonen kommuniserer med [backend applikasjonen](/backend) som inneholder all logikk for behandling av
søknader, opprettelse av saker i arena, og kommunikasjon med produsent-api / Min side arbeidsgiver for å opprette
beskjeder og statusoppdateringer der.

### Frontend applikasjonen

Frontend applikasjonen er en React app som håndterer innsendingsskjema for søknader, og en oversikt over innsendte
søknader for arbeidsgiver.
Applikasjonen er utviklet i Vite og React, og bruker Typescript for type-sikkerhet. For mer informasjon om frontend
applikasjonen, se [frontend/README.md](/frontend/README.md).

### Backend applikasjonen

Backend applikasjonen er en Ktor applikasjon skrevet i Kotlin, og håndterer all logikk for behandling av søknader,
opprettelse av saker i arena, og kommunikasjon med produsent-api / Min side arbeidsgiver for å opprette beskjeder og
statusoppdateringer der. For mer informasjon om backend applikasjonen, se [backend/README.md](/backend/README.md).

