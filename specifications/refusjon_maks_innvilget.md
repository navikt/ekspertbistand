# Refusjon begrenset til innvilget beløp

## Mål

Arbeidsgiver skal ikke kunne kreve mer i refusjon enn Nav har innvilget. Refusjon
og sluttrapport for Arena-saker sendes ikke inn via denne løsningen.

## Krav

### Arena-saker

- Saker med `kilde_til_behandling = ARENA` kan ikke få refusjonskrav eller
  sluttrapport. Backend svarer 409.
- Sjekken ligger i `hentGodkjentSakIdForUpdate`, etter at søknad og sak er låst,
  så den gjelder både refusjon og sluttrapport.
- Begge innsendingsendepunktene sjekker kilden også før filene leses og skannes,
  så vi slipper å skanne filer for et krav som uansett avvises.

### Maksbeløp

- Maksbeløpet er `forelopig_vedtak.innvilget_belop` for vedtaket på saken, når
  `vedtak.utfall = INNVILGET`. Det er søknadssummen ved innvilgelse og det
  reduserte beløpet ved innvilgelse med redusert beløp.
- Et krav lik innvilget beløp tillates. Et krav over gir 400 med meldingen
  «Beløpet kan ikke være høyere enn innvilget beløp (X kr)».
- Finnes ikke et innvilget beløp større enn 0, avvises kravet med 409.
- Grensen regnes i øre som `Long`, så store beløp ikke gir overflow.
- Sjekken gjøres i samme transaksjon som lagringen, etter låsing. Frontend-
  validering er bare for brukeropplevelsen.
- Refusjonskrav som allerede er sendt, kontrolleres ikke på nytt.

### Endepunkt for maksbeløp

- `GET /api/soknad/v1/{id}/refusjon/maksbelop`, beskyttet med TokenX.
- Svar: `{ "maksBelopKroner": 24900 }`.
- Tilgang sjekkes mot Altinn før noe annet oppslag, slik at svarkodene ikke
  avslører kilde eller vedtak for virksomheter brukeren ikke har tilgang til.
- 404 når søknaden eller saken ikke finnes, 403 uten tilgang, 409 for
  Arena-saker og når innvilget beløp mangler.

### Frontend (bare v2)

- `/v2/skjema/:id/refusjon` henter maksbeløpet og viser «Du kan kreve inntil
  X kr» ved beløpsfeltet.
- Beløp over maksbeløpet gir feltfeil før innsending.
- Svarer endepunktet 409, viser siden en melding i stedet for skjemaet.
- v1 endres ikke. Backend avviser for høye krav også der.

### Observerbarhet

- Telleren `refusjon_avvist_total{arsak}` med `arsak` lik `ARENA`,
  `OVER_INNVILGET` eller `BELOP_MANGLER`.
- Loggen inneholder bare `soknadId`, `belopOre` og `maksOre`.

## Utenfor

- Refusjon for Arena-saker med beløpet fra tilsagnsbrevet som grense. Arena kan
  ha flere tilsagn per sak, og regelen for dem må avklares med fagsiden først.
- Hendelser for refusjon og sluttrapport.
