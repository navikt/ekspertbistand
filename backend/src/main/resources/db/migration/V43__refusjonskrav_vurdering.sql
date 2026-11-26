SET LOCAL lock_timeout = '3s';

ALTER TABLE refusjonskrav
    DROP CONSTRAINT refusjonskrav_sak_id_key,
    ADD COLUMN arsaker TEXT[],
    ADD COLUMN begrunnelse TEXT,
    ADD COLUMN vurdert_av_ident TEXT,
    ADD COLUMN vurdert_av_navn TEXT,
    ADD COLUMN vurdert_tidspunkt TIMESTAMPTZ;

CREATE INDEX idx_refusjonskrav_nyeste ON refusjonskrav (sak_id, opprettet DESC, id DESC);
