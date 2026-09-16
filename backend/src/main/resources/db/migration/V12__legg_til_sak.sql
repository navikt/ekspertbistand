CREATE TABLE sluttrapport (
    sluttrapport_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status          TEXT NOT NULL DEFAULT 'MOTTATT',
    opprettet       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sak (
    sak_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soknad_id            UUID NOT NULL UNIQUE REFERENCES soknad(id),
    status               TEXT NOT NULL DEFAULT 'OPPRETTET',
    kilde_til_behandling TEXT NOT NULL DEFAULT 'ARENA',
    behandlende_enhet    TEXT NULL,
    saksbehandler_ident  TEXT NULL,
    beslutter_ident      TEXT NULL,
    foreslatt_utfall     TEXT NULL,
    arena_sak_id         TEXT NULL,
    refusjon_id          UUID UNIQUE REFERENCES refusjonskrav(id),
    sluttrapport_id      UUID UNIQUE REFERENCES sluttrapport(sluttrapport_id),
    opprettet            TIMESTAMPTZ NOT NULL DEFAULT now(),
    sist_endret          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_saksbehandler_ulik_beslutter
        CHECK (beslutter_ident IS NULL OR beslutter_ident <> saksbehandler_ident)
);

CREATE INDEX idx_sak_status ON sak(status);
CREATE INDEX idx_sak_saksbehandler_ident ON sak(saksbehandler_ident);
CREATE INDEX idx_sak_behandlende_enhet ON sak(behandlende_enhet);

CREATE TABLE saksvilkar (
    sak_id                            UUID PRIMARY KEY REFERENCES sak(sak_id) ON DELETE CASCADE,
    har_arbeidsforhold                BOOLEAN NULL,
    fylles_ut_i_samrad_godkjent       BOOLEAN NULL,
    provd_tilrettelegging             BOOLEAN NULL,
    provd_tilrettelegging_notat       TEXT NULL,
    har_sykefravaershistorikk         BOOLEAN NULL,
    har_sykefravaershistorikk_notat   TEXT NULL,
    ekspert_har_kompetanse            BOOLEAN NULL,
    ekspert_har_kompetanse_notat      TEXT NULL
);

CREATE TABLE sakslogg (
    sakslogg_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sak_id          UUID NOT NULL REFERENCES sak(sak_id) ON DELETE CASCADE,
    utfort_av_type  TEXT NOT NULL,
    utfort_av_rolle TEXT NULL,
    utfort_av_ident TEXT NULL,
    notat           TEXT NULL,
    utfort_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sakslogg_sak_id ON sakslogg(sak_id);
CREATE INDEX idx_sakslogg_sak_ident ON sakslogg(sak_id, utfort_av_ident);

CREATE TABLE sak_retur (
    retur_id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sak_id                  UUID NOT NULL REFERENCES sak(sak_id) ON DELETE CASCADE,
    returnert_av_ident      TEXT NOT NULL,
    til_saksbehandler_ident TEXT NULL,
    aarsak                  TEXT NOT NULL,
    forklaring              TEXT NOT NULL,
    opprettet               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_sak_retur_forklaring_lengde CHECK (char_length(forklaring) <= 1000)
);

CREATE INDEX idx_sak_retur_sak_id ON sak_retur(sak_id);

ALTER TABLE vedlegg
    ADD COLUMN sluttrapport_id UUID REFERENCES sluttrapport(sluttrapport_id) ON DELETE CASCADE;

CREATE INDEX idx_vedlegg_sluttrapport_id ON vedlegg(sluttrapport_id);
