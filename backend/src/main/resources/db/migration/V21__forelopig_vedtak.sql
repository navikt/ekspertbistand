-- Saksbehandlers foreløpige vedtak (innstilling) til beslutter. Ny versjon per innsending.
-- Se specifications/forelopig_vedtak.md.
CREATE TABLE IF NOT EXISTS forelopig_vedtak (
    id                       BIGSERIAL   PRIMARY KEY,
    sak_id                   UUID        NOT NULL REFERENCES sak (sak_id),
    versjon                  INT         NOT NULL,
    event_id                 BIGINT      NOT NULL UNIQUE,
    utfall                   TEXT        NOT NULL,
    innvilget_belop          INT,
    tiltaksperiode_fra       DATE,
    tiltaksperiode_til       DATE,
    begrunnelse_deltaker     TEXT        NOT NULL,
    begrunnelse_arbeidsgiver TEXT,
    innstilt_av_ident        TEXT        NOT NULL,
    sendt_tidspunkt          TIMESTAMPTZ NOT NULL,
    UNIQUE (sak_id, versjon),
    CONSTRAINT chk_forelopig_vedtak_utfall
        CHECK (utfall IN ('INNVILGE', 'INNVILGE_REDUSERT', 'AVSLAA')),
    CONSTRAINT chk_forelopig_vedtak_felter CHECK (
        (utfall = 'AVSLAA'
            AND innvilget_belop IS NULL
            AND tiltaksperiode_fra IS NULL AND tiltaksperiode_til IS NULL
            AND begrunnelse_arbeidsgiver IS NOT NULL)
        OR (utfall IN ('INNVILGE', 'INNVILGE_REDUSERT')
            AND innvilget_belop > 0
            AND tiltaksperiode_fra IS NOT NULL AND tiltaksperiode_til IS NOT NULL
            AND tiltaksperiode_fra <= tiltaksperiode_til
            AND EXTRACT(YEAR FROM tiltaksperiode_fra) = EXTRACT(YEAR FROM tiltaksperiode_til))
    ),
    CONSTRAINT chk_forelopig_vedtak_redusert_begrunnelse
        CHECK (utfall <> 'INNVILGE_REDUSERT' OR begrunnelse_arbeidsgiver IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_forelopig_vedtak_sak_id ON forelopig_vedtak (sak_id);
