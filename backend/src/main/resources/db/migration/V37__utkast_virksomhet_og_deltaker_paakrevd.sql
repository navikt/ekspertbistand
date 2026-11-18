-- Utkast opprettes først når deltakeren er bekreftet, og har alltid virksomhet og bekreftet deltaker.
-- Utkast uten dem har ikke kommet forbi steg 1, og slettes uansett etter 48 timer.
-- Se specifications/innsending_fra_utkast.md.
SET lock_timeout = '3s';

DELETE FROM utkast
WHERE virksomhetsnummer IS NULL
   OR virksomhetsnavn IS NULL
   OR ansatt_fnr IS NULL
   OR ansatt_navn IS NULL
   OR ansatt_bekreftet_tidspunkt IS NULL;

ALTER TABLE utkast ALTER COLUMN virksomhetsnummer SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN virksomhetsnavn SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_fnr SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_navn SET NOT NULL;
ALTER TABLE utkast ALTER COLUMN ansatt_bekreftet_tidspunkt SET NOT NULL;
