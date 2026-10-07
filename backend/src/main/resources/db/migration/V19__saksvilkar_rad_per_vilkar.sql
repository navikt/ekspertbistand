-- saksvilkar bygges om fra én kolonne per vilkår til én rad per vilkår. Tabellen er tom når
-- migreringen kjøres, fordi ingenting skriver til saksvilkar ennå. Se specifications/sak-datamodell.md.
DROP TABLE saksvilkar;

CREATE TABLE saksvilkar (
    sak_id            UUID NOT NULL REFERENCES sak(sak_id) ON DELETE CASCADE,
    vilkar_id         TEXT NOT NULL,
    godkjent          BOOLEAN NULL,
    notat             TEXT NULL,
    vurdert_tidspunkt TIMESTAMPTZ NULL,
    vurdert_av_ident  TEXT NULL,
    PRIMARY KEY (sak_id, vilkar_id)
);
