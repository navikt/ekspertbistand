-- Saksbehandlers vurdering av en sluttrapport. Status er MOTTATT, BEKREFTET eller
-- NY_INNSENDING_KREVES. Se specifications/vurdering_sluttrapport.md.
SET lock_timeout = '3s';

ALTER TABLE sluttrapport
    ADD COLUMN IF NOT EXISTS vurdert_av_ident  TEXT,
    ADD COLUMN IF NOT EXISTS vurdert_tidspunkt TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS begrunnelse       TEXT;
