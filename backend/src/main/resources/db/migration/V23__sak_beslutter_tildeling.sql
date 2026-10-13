ALTER TABLE sak ADD COLUMN IF NOT EXISTS beslutter_navn TEXT NULL;
ALTER TABLE sak ADD COLUMN IF NOT EXISTS beslutter_tildeling_event_id BIGINT NULL;

ALTER TABLE sak DROP CONSTRAINT IF EXISTS chk_beslutter_navn_med_ident;
ALTER TABLE sak ADD CONSTRAINT chk_beslutter_navn_med_ident
    CHECK ((beslutter_ident IS NULL) = (beslutter_navn IS NULL));
