-- Interne arbeidsnotater på en sak, skrevet av saksbehandler eller beslutter. Skrives av handlerne for
-- SaksnotatOpprettet, SaksnotatEndret og SaksnotatSlettet. Se specifications/notat_i_saken.md.
CREATE TABLE IF NOT EXISTS saksnotat (
    notat_id         UUID PRIMARY KEY,
    sak_id           UUID        NOT NULL REFERENCES sak (sak_id) ON DELETE CASCADE,
    tekst            TEXT        NOT NULL,
    skrevet_av_ident TEXT        NOT NULL,
    skrevet_av_navn  TEXT        NOT NULL,
    skrevet_av_rolle TEXT        NOT NULL,
    opprettet_at     TIMESTAMPTZ NOT NULL,
    endret_at        TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_saksnotat_sak_id ON saksnotat (sak_id, opprettet_at DESC);
