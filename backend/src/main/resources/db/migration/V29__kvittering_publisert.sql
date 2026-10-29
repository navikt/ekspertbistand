-- Kvitteringshendelser (ArbeidsgiverVarslet, ArenaNotatJournalfoert) publiseres bare når raden blir satt inn.
-- Nøkkelen er den samme som det eksterne kallet bruker (eksternId i Notifikasjonsplattform,
-- eksternReferanseId i DokArkiv). Se event/KvitteringPublisert.kt.
SET lock_timeout = '3s';

-- Ingen FK til soknad: KvitteringPublisertProjection kan møte hendelser for søknader som er slettet.
-- Radene slettes av AggregateRetention sammen med resten av aggregatet.
CREATE TABLE IF NOT EXISTS kvittering_publisert
(
    nokkel     TEXT PRIMARY KEY,
    soknad_id  TEXT        NOT NULL,
    type       TEXT        NOT NULL,
    registrert TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS kvittering_publisert_soknad_id_idx ON kvittering_publisert (soknad_id);
