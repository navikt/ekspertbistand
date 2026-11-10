-- Refusjonskrav hører til saken. Sletting av søknad treffer fortsatt kravet via sak (ON DELETE CASCADE).
ALTER TABLE refusjonskrav ALTER COLUMN sak_id SET NOT NULL;

DROP INDEX idx_refusjonskrav_soknad_id;
ALTER TABLE refusjonskrav DROP COLUMN soknad_id;
