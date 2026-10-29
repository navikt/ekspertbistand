-- Saksnummer som matcher OeBS, se specifications/sette_av_penger_oebs_vedtak.md.
SET lock_timeout = '3s';

-- Starter over høyeste løpenummer i Arena, så nummeret ikke overlapper. Nullstilles ikke ved nyttår.
CREATE SEQUENCE IF NOT EXISTS saksnummer_seq START WITH 1000000;

-- Formatet <år>/<løpenummer> ligger bare her. Året er året saken opprettes, i norsk tid.
CREATE OR REPLACE FUNCTION neste_saksnummer(tidspunkt TIMESTAMPTZ DEFAULT now())
    RETURNS TEXT
    LANGUAGE sql
    VOLATILE
AS
$$
SELECT extract(year FROM tidspunkt AT TIME ZONE 'Europe/Oslo')::int || '/' || nextval('saksnummer_seq')
$$;

-- Settes av OpprettSak. Null for Arena-saker.
ALTER TABLE sak ADD COLUMN IF NOT EXISTS saksnummer TEXT;

-- Én UPDATE per sak i rekkefølge, så eldre saker får lavere løpenummer. Et vanlig UPDATE kaller nextval i den
-- rekkefølgen Postgres leser radene.
DO
$$
    DECLARE
        s RECORD;
    BEGIN
        FOR s IN
            SELECT sak_id, opprettet
            FROM sak
            WHERE kilde_til_behandling = 'EKSPERTBISTAND'
              AND saksnummer IS NULL
            ORDER BY opprettet, sak_id
            LOOP
                UPDATE sak SET saksnummer = neste_saksnummer(s.opprettet) WHERE sak_id = s.sak_id;
            END LOOP;
    END
$$;

ALTER TABLE sak
    ADD CONSTRAINT sak_saksnummer_unique UNIQUE (saksnummer),
    ADD CONSTRAINT sak_saksnummer_ekspertbistand
        CHECK (kilde_til_behandling <> 'EKSPERTBISTAND' OR saksnummer IS NOT NULL);

ALTER TABLE oebs_lopenummer RENAME COLUMN sak_id TO saksnummer;
