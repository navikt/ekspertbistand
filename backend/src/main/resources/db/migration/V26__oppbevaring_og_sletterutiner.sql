-- Oppbevaring og sletterutiner, se specifications/sletterutiner_10_ar_og_notifikasjon_455_dager.md.
--
-- - Søknader slettes ikke lenger etter 455 dager. slette_tidspunkt får navnet arbeidsgiver_tilgang_til
--   og styrer bare hvor lenge arbeidsgiver ser søknaden.
-- - Data slettes etter 10 år av AggregateRetention. Migreringen legger til det jobben trenger:
--   CASCADE på fremmednøkler og indekser.
SET lock_timeout = '3s';

-- 1. Arbeidsgivers tilgang til søknaden. Gamle pods som bruker slette_tidspunkt, feiler under utrulling.
ALTER TABLE soknad RENAME COLUMN slette_tidspunkt TO arbeidsgiver_tilgang_til;

-- 2. Fremmednøkler som manglet CASCADE. Uten dem feiler sletting av en søknad med vedtak.
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

-- 3. Indekser for slettejobben. Tabellene er små nok til vanlig CREATE INDEX.
CREATE INDEX IF NOT EXISTS event_log_aggregate_root_id_created_at_idx
    ON event_log (aggregate_root_id, created_at);
CREATE INDEX IF NOT EXISTS event_queue_aggregate_root_id_idx
    ON event_queue (aggregate_root_id);
CREATE INDEX IF NOT EXISTS tilsagndata_soknad_id_idx ON tilsagndata (soknad_id);
CREATE INDEX IF NOT EXISTS tilsagndata_tilsagnnummer_idx ON tilsagndata (tilsagnnummer);
