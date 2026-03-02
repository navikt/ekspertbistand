CREATE TABLE IF NOT EXISTS arena_melding_idempotency (meldingstype VARCHAR(50), ekstern_id INT, CONSTRAINT pk_arena_melding_idempotency PRIMARY KEY (meldingstype, ekstern_id));
