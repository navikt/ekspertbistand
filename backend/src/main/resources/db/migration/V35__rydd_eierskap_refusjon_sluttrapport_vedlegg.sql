-- Eierskapet går én vei: refusjonskrav/sluttrapport → sak, vedlegg → refusjonskrav/sluttrapport.
-- Sletting av søknad treffer alt via sak (ON DELETE CASCADE).
ALTER TABLE sak
    DROP COLUMN refusjon_id,
    DROP COLUMN sluttrapport_id;

DROP INDEX idx_vedlegg_soknad_id;
ALTER TABLE vedlegg DROP COLUMN soknad_id;

ALTER TABLE vedlegg ADD CONSTRAINT chk_vedlegg_eier CHECK (
    (type = 'REFUSJONSDOKUMENTASJON' AND refusjonskrav_id IS NOT NULL AND sluttrapport_id IS NULL)
    OR (type = 'SLUTTRAPPORT' AND sluttrapport_id IS NOT NULL AND refusjonskrav_id IS NULL)
);

-- Ett refusjonskrav per sak. Unik-indeksen erstatter den vanlige indeksen på sak_id.
DROP INDEX idx_refusjonskrav_sak_id;
ALTER TABLE refusjonskrav ADD CONSTRAINT refusjonskrav_sak_id_key UNIQUE (sak_id);
