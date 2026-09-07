-- P2 (aggregate_root_id): kun katalog-endringer.
-- ADD COLUMN av en nullbar kolonne uten DEFAULT er metadata-only i Postgres (O(1) uansett
-- tabellstørrelse). lock_timeout gjør at migreringen feiler raskt og rent i stedet for å stå i
-- lås-kø bak en langvarig transaksjon.
SET lock_timeout = '3s';

ALTER TABLE event_queue ADD COLUMN IF NOT EXISTS aggregate_root_id TEXT NULL;
ALTER TABLE event_log   ADD COLUMN IF NOT EXISTS aggregate_root_id TEXT NULL;

-- Generisk state-tabell for restartbare backfill-jobber.
CREATE TABLE IF NOT EXISTS backfill_state (
    job_name     TEXT PRIMARY KEY,
    cursor_pos   BIGINT    NOT NULL DEFAULT 0,
    scanned      BIGINT    NOT NULL DEFAULT 0,
    updated      BIGINT    NOT NULL DEFAULT 0,
    skipped      BIGINT    NOT NULL DEFAULT 0,
    completed_at TIMESTAMP NULL,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
