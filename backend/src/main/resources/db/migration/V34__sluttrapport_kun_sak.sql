-- Sluttrapport hører til saken. Sletting av søknad treffer fortsatt rapporten via sak (ON DELETE CASCADE).
ALTER TABLE sluttrapport ALTER COLUMN sak_id SET NOT NULL;
