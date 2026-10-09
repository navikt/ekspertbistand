ALTER TABLE sak ADD COLUMN IF NOT EXISTS saksbehandler_navn TEXT NULL;
ALTER TABLE sak ADD COLUMN IF NOT EXISTS tildeling_event_id BIGINT NULL;

ALTER TABLE sak DROP CONSTRAINT IF EXISTS chk_saksbehandler_navn_med_ident;
ALTER TABLE sak ADD CONSTRAINT chk_saksbehandler_navn_med_ident
    CHECK ((saksbehandler_ident IS NULL) = (saksbehandler_navn IS NULL));

-- Topartskontrollen gjelder vedtaket, ikke saken. Se specifications/tildel_meg_sak.md.
ALTER TABLE sak DROP CONSTRAINT IF EXISTS chk_saksbehandler_ulik_beslutter;
