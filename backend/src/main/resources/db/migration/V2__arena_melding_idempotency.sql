CREATE TABLE IF NOT EXISTS arena_melding_idempotency (
    meldingstype TEXT NOT NULL,
    ekstern_id INT NOT NULL,
    CONSTRAINT arena_melding_idempotency_pkey PRIMARY KEY (meldingstype, ekstern_id)
);