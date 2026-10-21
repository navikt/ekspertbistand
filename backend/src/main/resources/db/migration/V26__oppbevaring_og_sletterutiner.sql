-- Oppbevaring og sletterutiner, se specifications/sletterutiner_10_ar_og_notifikasjon_455_dager.md.
--
-- - Søknader slettes ikke lenger etter 455 dager. Ny kolonne arbeidsgiver_tilgang_til styrer bare hvor
--   lenge arbeidsgiver ser søknaden. slette_tidspunkt brukes ikke av ny kode.
-- - Data slettes etter 10 år av AggregateRetention. Migreringen legger til det jobben trenger:
--   CASCADE på fremmednøkler og indekser.
--
-- Tidsstempler i TIMESTAMP-kolonner er UTC (Exposed skriver UTC), derfor now() AT TIME ZONE 'UTC'.
SET lock_timeout = '3s';

-- 1. Arbeidsgivers tilgang til søknaden. Expand/contract: ny kolonne ved siden av slette_tidspunkt, så
--    gamle pods fungerer under utrulling. slette_tidspunkt fjernes i en senere migrering.
--    Ny standardverdi langt fram i tid, så en rollback til gammel kode ikke sletter nye søknader.
ALTER TABLE soknad ADD COLUMN arbeidsgiver_tilgang_til TIMESTAMP NULL;
UPDATE soknad SET arbeidsgiver_tilgang_til = slette_tidspunkt;
ALTER TABLE soknad
    ALTER COLUMN arbeidsgiver_tilgang_til SET DEFAULT ((now() AT TIME ZONE 'UTC') + INTERVAL '455 days'),
    ALTER COLUMN arbeidsgiver_tilgang_til SET NOT NULL,
    ALTER COLUMN slette_tidspunkt SET DEFAULT '9999-12-31 00:00:00';

-- 2. Arbeidsgivers tilgang til tilsagn uten søknad (Altinn-sporet).
ALTER TABLE tilsagndata ADD COLUMN arbeidsgiver_tilgang_til TIMESTAMP NULL;

UPDATE tilsagndata t
SET arbeidsgiver_tilgang_til = COALESCE(
        (SELECT max(e.created_at) + INTERVAL '455 days'
         FROM event_log e
         WHERE e.aggregate_root_id = t.tilsagnnummer),
        (now() AT TIME ZONE 'UTC') + INTERVAL '455 days'
    );

ALTER TABLE tilsagndata
    ALTER COLUMN arbeidsgiver_tilgang_til SET DEFAULT ((now() AT TIME ZONE 'UTC') + INTERVAL '455 days'),
    ALTER COLUMN arbeidsgiver_tilgang_til SET NOT NULL;

-- 3. Fremmednøkler som manglet CASCADE. Uten dem feiler sletting av en søknad med vedtak.
ALTER TABLE forelopig_vedtak DROP CONSTRAINT forelopig_vedtak_sak_id_fkey;
ALTER TABLE forelopig_vedtak ADD CONSTRAINT forelopig_vedtak_sak_id_fkey
    FOREIGN KEY (sak_id) REFERENCES sak (sak_id) ON DELETE CASCADE;

ALTER TABLE vedtak DROP CONSTRAINT vedtak_sak_id_fkey;
ALTER TABLE vedtak ADD CONSTRAINT vedtak_sak_id_fkey
    FOREIGN KEY (sak_id) REFERENCES sak (sak_id) ON DELETE CASCADE;

ALTER TABLE vedtak DROP CONSTRAINT vedtak_forelopig_vedtak_id_fkey;
ALTER TABLE vedtak ADD CONSTRAINT vedtak_forelopig_vedtak_id_fkey
    FOREIGN KEY (forelopig_vedtak_id) REFERENCES forelopig_vedtak (id) ON DELETE CASCADE;

ALTER TABLE sak_retur DROP CONSTRAINT sak_retur_forelopig_vedtak_id_fkey;
ALTER TABLE sak_retur ADD CONSTRAINT sak_retur_forelopig_vedtak_id_fkey
    FOREIGN KEY (forelopig_vedtak_id) REFERENCES forelopig_vedtak (id) ON DELETE CASCADE;

-- 4. Indekser for slettejobben. Tabellene er små nok til vanlig CREATE INDEX.
CREATE INDEX IF NOT EXISTS event_log_aggregate_root_id_created_at_idx
    ON event_log (aggregate_root_id, created_at);
CREATE INDEX IF NOT EXISTS event_queue_aggregate_root_id_idx
    ON event_queue (aggregate_root_id);
CREATE INDEX IF NOT EXISTS tilsagndata_soknad_id_idx ON tilsagndata (soknad_id);
CREATE INDEX IF NOT EXISTS tilsagndata_tilsagnnummer_idx ON tilsagndata (tilsagnnummer);
