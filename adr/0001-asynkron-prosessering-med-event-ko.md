# Asynkron prosessering av søknader og kø i Ekstpertbistand

## Bakgrunn
Vi ønsker å kunne prosessere skjemainnsendinger asynkront, siden det er flere parter som skal viteta stilling til skjemaet før det godkjennes/avslås. For å få til dette ønsker vi å bruke en Kø, slik at innsendt data kan prosesseres asynkront.

## Hvordan skal køen være bygget opp?
- Køen skal være FIFO (first-in-first-out)
- Køen skal være én enkelt tabell i postgres.
- Køen skal inneholde events, der hver rad i tabellen skal beskrive en maskinell handling/prosess som skal skje.
- Et event skal bestå av (mer eller minde)
    - Id - auto-inkrementerende integer. vil fungere som et løpenummer. events med lavest løpenummer prosesseres først (FIFO).
    - SkjemaId - koble alle events til et innsendt skjema?
    - EventType - sier hvilken sterkt typet klasse EventPayload kan serialiseres/deserialisers fra/til
    - EventPayload - en json string som kan deserialiseres til en sterkt typet klasse, gitt av EventType
    - State - kan si hvilken tilstand raden er i (i.e Ny, Prosessing, Finished, Failed etc. Litt usikker på hvordan denne skal brukes)
    - Attempts - kan sier hvor mange ganger eventet er forsøkt prosessert.

## Hvordan skal køen prosesseres?
Vi har en event-poller, som henter ut neste tilgjengelige event basert på løpenummer / Id. 
Event-polleren har en exhaustive when-clause på EventType, med en prosessor for hver. Dette sikrer at vi skal kunne håndtere alle definerte events.
Pollingen og prosesseringen av eventet skjer i konteksten av en database-transaksjon, slik at raden blir "låst" frem til prosesseringen er ferdig. Dette skal sikre at neste tilgjengelige event alltid vil være et som ikke allerede blir prosessert.

**MERK: State feltet brukes ikke av event-polleren. Dette feltet er først og fremst tiltenkt for å kunne si noe om mulige feil.**

### Prosesseringen kan være velykket eller feile:
- Velykket: Eventet fjernes fra køen og legges i en "ferdig" tabell
- Feilet: Attempts += 1. Usikker på om vi oppdaterer state her


### event-prosessor
- Håndterer én event-type. Outputen herifra kan gjerne være ett eller flere nye events, som registreres i samme kø.
- Så langt det lar seg gjøre ønsker vi at hver event-prosessor kun snakker med én ekstern tjeneste. Dette gjør det enklere å håndtere situasjoner der eksterne tjenester går ned, uten å være avhengig av at de er idempotente. 
