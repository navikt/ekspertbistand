-- Beslutters vedtak og retur av foreløpig vedtak. Se specifications/beslutter_vedtak.md.
CREATE TABLE IF NOT EXISTS vedtak (
    id                  BIGSERIAL   PRIMARY KEY,
    sak_id              UUID        NOT NULL UNIQUE REFERENCES sak (sak_id),
    forelopig_vedtak_id BIGINT      NOT NULL UNIQUE REFERENCES forelopig_vedtak (id),
    utfall              TEXT        NOT NULL,
    fattet_av_ident     TEXT        NOT NULL,
    fattet_av_navn      TEXT        NOT NULL,
    fattet_tidspunkt    TIMESTAMPTZ NOT NULL,
    event_id            BIGINT      NOT NULL UNIQUE,
    CONSTRAINT chk_vedtak_utfall CHECK (utfall IN ('INNVILGET', 'AVSLATT'))
);

-- sak_retur er tom i alle miljøer, så NOT NULL på nye kolonner er trygt.
ALTER TABLE sak_retur DROP COLUMN IF EXISTS aarsak;
ALTER TABLE sak_retur
    ADD COLUMN IF NOT EXISTS aarsaker            TEXT[] NOT NULL,
    ADD COLUMN IF NOT EXISTS forelopig_vedtak_id BIGINT NOT NULL REFERENCES forelopig_vedtak (id),
    ADD COLUMN IF NOT EXISTS returnert_av_navn   TEXT   NOT NULL,
    ADD COLUMN IF NOT EXISTS event_id            BIGINT NOT NULL UNIQUE;

ALTER TABLE sak_retur DROP CONSTRAINT IF EXISTS chk_sak_retur_aarsaker;
ALTER TABLE sak_retur ADD CONSTRAINT chk_sak_retur_aarsaker
    CHECK (cardinality(aarsaker) >= 1
        AND aarsaker <@ ARRAY ['FEIL_REGELFORSTAELSE', 'FEIL_FAKTA', 'ANNET']::TEXT[]);
