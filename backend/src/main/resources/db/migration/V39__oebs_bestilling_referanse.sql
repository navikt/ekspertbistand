-- Kobling fra OeBS-bestilling til sak og søknad, eid av oebs-pakken. Consumeren slår opp her når
-- VALP melder status, og publiserer eventen på søknaden. Ingen fremmednøkkel: AggregateRetention
-- sletter radene med de andre oebs_-tabellene. Se specifications/oebs_status_til_saksgang.md.
--
-- Fylles fra sak_bestilling, som bare har rader i dev og lokalt.
SET lock_timeout = '3s';

CREATE TABLE IF NOT EXISTS oebs_bestilling_referanse (
    bestillingsnummer TEXT        PRIMARY KEY,
    sak_id            UUID        NOT NULL,
    soknad_id         UUID        NOT NULL,
    opprettet         TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO oebs_bestilling_referanse (bestillingsnummer, sak_id, soknad_id, opprettet)
SELECT sb.bestillingsnummer, sb.sak_id, s.soknad_id, sb.opprettet
FROM sak_bestilling sb
JOIN sak s ON s.sak_id = sb.sak_id
ON CONFLICT (bestillingsnummer) DO NOTHING;
