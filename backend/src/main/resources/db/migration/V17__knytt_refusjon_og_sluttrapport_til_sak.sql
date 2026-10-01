ALTER TABLE refusjonskrav
    ADD COLUMN sak_id UUID NULL,
    ADD CONSTRAINT refusjonskrav_sak_id_fkey
        FOREIGN KEY (sak_id) REFERENCES sak(sak_id) ON DELETE CASCADE;

CREATE INDEX idx_refusjonskrav_sak_id ON refusjonskrav(sak_id);

ALTER TABLE sluttrapport
    ADD COLUMN sak_id UUID NULL,
    ADD CONSTRAINT sluttrapport_sak_id_fkey
        FOREIGN KEY (sak_id) REFERENCES sak(sak_id) ON DELETE CASCADE;

CREATE INDEX idx_sluttrapport_sak_id ON sluttrapport(sak_id);
