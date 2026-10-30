SET lock_timeout = '3s';

CREATE TABLE IF NOT EXISTS sak_bestilling (
    id                BIGSERIAL   PRIMARY KEY,
    sak_id            UUID        NOT NULL REFERENCES sak (sak_id) ON DELETE CASCADE,
    vedtak_id         BIGINT      NOT NULL UNIQUE REFERENCES vedtak (id) ON DELETE CASCADE,
    bestillingsnummer TEXT        NOT NULL UNIQUE,
    belop             INT         NOT NULL CHECK (belop > 0),
    periode_fra       DATE        NOT NULL,
    periode_til       DATE        NOT NULL,
    opprettet         TIMESTAMPTZ NOT NULL,
    CHECK (periode_fra <= periode_til)
);

CREATE INDEX IF NOT EXISTS sak_bestilling_sak_id_idx ON sak_bestilling (sak_id);
