-- Adressebeskyttelse og skjerming ved opprettelse, se specifications/adressebeskyttelse_og_skjerming_ved_opprettelse.md.
SET lock_timeout = '3s';

-- FORTROLIG eller STRENGT_FORTROLIG fra PDL da saken ble opprettet. Null uten adressebeskyttelse, for Arena-saker og
-- for saker opprettet før denne migreringen.
ALTER TABLE sak ADD COLUMN IF NOT EXISTS adressebeskyttelse TEXT;

-- Skjerming (egen ansatt) fra skjermede-personer-pip da saken ble opprettet.
ALTER TABLE sak ADD COLUMN IF NOT EXISTS skjermet BOOLEAN NOT NULL DEFAULT false;
