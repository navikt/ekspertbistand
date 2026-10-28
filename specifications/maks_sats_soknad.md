# Maksgrense for søknadssum

## Mål

Arbeidsgiver skal se og kunne søke om høyst gjeldende makssats for søknadens
startdato. Maksgrensen er 24 900 kr fra 1. januar 2026.

## Krav

- Backend velger satsen med seneste `gyldigFra` som ikke er etter startdatoen.
  Siste kjente sats gjelder til en ny sats trer i kraft.
- Arbeidsgiverklienten henter `{ gyldigFra, belop }` fra det TokenX-beskyttede
  `GET /api/soknad/v1/maks-sats?startdato=ÅÅÅÅ-MM-DD`.
- Uten valgt startdato vises dagens sats som veiledning, men startdato kreves
  før brukeren kan gå videre.
- Søknadssumfeltet viser: «Maksgrensen er 24 900 kr. Kontakt Nav hvis du er
  usikker på hvor mye du skal søke om.» Beløpet følger oppslått sats.
- Når startdato eller beløp endres, kontrollerer skjemaet beløpet mot satsen
  for valgt dato. Et beløp over grensen gir feltfeil.
- Steg 2 og oppsummeringen hindrer videreføring eller innsending hvis satsen
  ikke kan hentes. Brukeren kan prøve oppslaget på nytt.
- Oppsummeringen bruker satsen som er hentet for startdatoen, uten et ekstra
  oppslag ved klikk på «Send søknad». Backend kontrollerer beløpet på nytt.
- Ved innsending validerer backend et positivt heltallsbeløp mot satsen for
  søknadens startdato. Ugyldig beløp eller dato uten sats gir 400 med feltsti.
  Oppsummeringen sender brukeren tilbake til riktig felt ved slike feil.
- Utkast kan lagres selv om beløpet overstiger satsen. Ingen søknad lagres som
  innsendt før backend-valideringen er bestått.
