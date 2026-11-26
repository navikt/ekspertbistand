-- Virksomhetstype i saksoversikten, se specifications/kategorisering_virksomhetstype.md.
SET lock_timeout = '3s';

-- Sektorkoden til juridisk enhet på toppen av kjeden over virksomheten, fra Ereg da saken ble opprettet.
-- Null for eldre saker, Arena-saker og når Ereg ikke har koden. Ingen utfylling av gamle rader.
ALTER TABLE sak ADD COLUMN IF NOT EXISTS sektorkode TEXT;

-- Saksfilteret til saksbehandleren: enhet, kommuner og virksomhetstyper. En brukerinnstilling, ikke tilstand på
-- saken, og skrives derfor direkte av API-et. Tabellen er bare i bruk i dev, så den får nytt navn uten kopi.
ALTER TABLE IF EXISTS saksbehandler_kommunefilter RENAME TO saksbehandler_saksfilter;
ALTER INDEX IF EXISTS saksbehandler_kommunefilter_pkey RENAME TO saksbehandler_saksfilter_pkey;
ALTER TABLE saksbehandler_saksfilter ADD COLUMN IF NOT EXISTS virksomhetstyper TEXT[] NOT NULL DEFAULT '{}';
