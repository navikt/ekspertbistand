-- Rollen SYSTEM erstatter utfort_av_type. Tabellen er tom når migreringen kjøres, fordi ingenting
-- skriver til sakslogg ennå. Se specifications/sakslogg.md.
ALTER TABLE sakslogg DROP COLUMN utfort_av_type;
ALTER TABLE sakslogg ALTER COLUMN utfort_av_rolle SET NOT NULL;
