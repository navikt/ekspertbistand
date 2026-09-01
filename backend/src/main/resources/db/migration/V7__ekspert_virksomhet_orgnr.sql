ALTER TABLE soknad
    ADD COLUMN IF NOT EXISTS ekspert_virksomhet_navn  TEXT NULL,
    ADD COLUMN IF NOT EXISTS ekspert_virksomhet_orgnr TEXT NULL;

ALTER TABLE utkast
    ADD COLUMN IF NOT EXISTS ekspert_virksomhet_navn  TEXT NULL,
    ADD COLUMN IF NOT EXISTS ekspert_virksomhet_orgnr TEXT NULL;
