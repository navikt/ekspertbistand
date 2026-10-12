-- Arena-saker saksbehandles ikke hos oss og skal ikke ha vilkår. Irreversibelt, men
-- saksbehandling finnes bare i dev. Se specifications/forelopig_vedtak.md (2c).
DELETE FROM saksvilkar
WHERE sak_id IN (SELECT sak_id FROM sak WHERE kilde_til_behandling = 'ARENA');
