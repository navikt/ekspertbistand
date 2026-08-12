CREATE TABLE IF NOT EXISTS arena_sak_under_behandling (sak_id INT PRIMARY KEY, saksnummer TEXT NOT NULL, soknad_id uuid NOT NULL, brukerid_ansvarlig TEXT NOT NULL, aetatenhet_ansvarlig TEXT NULL, sakstatuskode TEXT NULL, observert_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL);
ALTER TABLE arena_sak ADD CONSTRAINT arena_sak_saksnummer_unique UNIQUE (saksnummer);
