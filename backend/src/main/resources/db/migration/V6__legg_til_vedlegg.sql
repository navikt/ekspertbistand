CREATE TABLE vedlegg (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soknad_id   UUID NOT NULL REFERENCES soknad(id) ON DELETE CASCADE,
    type        TEXT NOT NULL,
    filnavn     TEXT NOT NULL,
    innhold     BYTEA NOT NULL,
    storrelse   INT NOT NULL,
    lastet_opp  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vedlegg_soknad_id ON vedlegg(soknad_id);
