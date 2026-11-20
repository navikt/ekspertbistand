-- «Arbeidsforhold» og «Deltaker er enig» forhåndsutfylles som oppfylt for eksisterende saker i ny
-- saksbehandling, som OpprettSak gjør for nye saker. Bare urørte rader endres: vurderinger, notater
-- og nullstillinger fra saksbehandler beholdes. Se specifications/automatisk_vilkarsvurdering.md.
UPDATE saksvilkar
SET godkjent = TRUE
WHERE vilkar_id IN ('DELTAKER_HAR_ARBEIDSFORHOLD', 'FYLLES_UT_I_SAMRAD_GODKJENT')
  AND godkjent IS NULL
  AND notat IS NULL
  AND vurdert_av_ident IS NULL
  AND vurdert_tidspunkt IS NULL
  AND sak_id IN (SELECT sak_id FROM sak WHERE kilde_til_behandling = 'EKSPERTBISTAND');
