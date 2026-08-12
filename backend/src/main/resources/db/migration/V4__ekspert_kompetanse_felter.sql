ALTER TABLE soknad
    ADD COLUMN IF NOT EXISTS ekspert_godkjent_utdanning_eller_autorisasjon TEXT NULL,
    ADD COLUMN IF NOT EXISTS ekspert_relevant_kompetanse                    TEXT NULL;

ALTER TABLE utkast
    ADD COLUMN IF NOT EXISTS ekspert_godkjent_utdanning_eller_autorisasjon TEXT NULL,
    ADD COLUMN IF NOT EXISTS ekspert_relevant_kompetanse                    TEXT NULL;
