-- P6 steg 2 (aggregate_root_id): terminal innstramming til NOT NULL.
--
-- Forutsetning: P6 steg 1 (i backfill-jobben) har kjørt ferdig i miljøet — dvs. alle rader er
-- backfillet og CHECK-constrainten event_log_aggregate_root_id_nn er VALIDATED. Verifiser med
-- kontrollene i event/README.md (kontroll 1 og 2 = 0) FØR denne migreringen deployes.
--
-- lock_timeout gjør at migreringen feiler raskt og rent i stedet for å stå i lås-kø bak en
-- langvarig transaksjon.
SET lock_timeout = '3s';

-- Postgres 12+ bruker den allerede validerte CHECK-constrainten og hopper over tabell-scanen, så
-- SET NOT NULL blir O(1). CHECK-en er overflødig når kolonnen er NOT NULL og droppes rett etterpå.
ALTER TABLE event_log ALTER COLUMN aggregate_root_id SET NOT NULL;
ALTER TABLE event_log DROP CONSTRAINT event_log_aggregate_root_id_nn;

-- Køen er drenert (rader slettes ved finalize) og alle nye rader settes av publishEventQueue, så
-- scanen er trivielt kort — event_queue går rett på SET NOT NULL uten CHECK-omveien.
ALTER TABLE event_queue ALTER COLUMN aggregate_root_id SET NOT NULL;
