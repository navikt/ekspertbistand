CREATE TABLE refusjonskrav (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soknad_id    UUID NOT NULL REFERENCES soknad(id) ON DELETE CASCADE,
    belop_ore    BIGINT NOT NULL,
    utgifter     TEXT NOT NULL,
    status       TEXT NOT NULL DEFAULT 'MOTTATT',
    opprettet    TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE vedlegg
    ADD COLUMN refusjonskrav_id UUID REFERENCES refusjonskrav(id) ON DELETE CASCADE;

CREATE INDEX idx_refusjonskrav_soknad_id ON refusjonskrav(soknad_id);
CREATE INDEX idx_vedlegg_refusjonskrav_id ON vedlegg(refusjonskrav_id);
