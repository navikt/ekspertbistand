-- Fordeling per kommune, se specifications/ruting_egne_ansatte_og_kommune.md (del 2).
SET lock_timeout = '3s';

-- Kommunen til virksomheten som ble brukt mot Norg. Null for strengt fortrolig adresse og eldre saker.
ALTER TABLE sak ADD COLUMN IF NOT EXISTS kommunenummer TEXT;

-- Kommunefilteret til saksbehandleren. En brukerinnstilling, ikke tilstand på saken, og skrives derfor
-- direkte av API-et.
CREATE TABLE IF NOT EXISTS saksbehandler_kommunefilter
(
    nav_ident   TEXT PRIMARY KEY,
    enhet       TEXT        NOT NULL,
    kommuner    TEXT[]      NOT NULL,
    sist_endret TIMESTAMPTZ NOT NULL DEFAULT now()
);
