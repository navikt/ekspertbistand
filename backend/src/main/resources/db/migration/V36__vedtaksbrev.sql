-- Vedtaksbrevet til arbeidsgiver, lagret fra VedtaksbrevArbeidsgiverJournalfoert. Se specifications/vis_vedtak_arbeidsgiver.md.
CREATE TABLE IF NOT EXISTS vedtaksbrev (
    sak_id                 UUID        PRIMARY KEY REFERENCES sak (sak_id) ON DELETE CASCADE,
    utfall                 TEXT        NOT NULL,
    brevdata               JSONB       NOT NULL,
    journalfoert_tidspunkt TIMESTAMPTZ NOT NULL,
    event_id               BIGINT      NOT NULL
);
