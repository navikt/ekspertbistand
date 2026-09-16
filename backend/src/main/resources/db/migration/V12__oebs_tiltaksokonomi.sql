-- OeBS/tiltaksøkonomi-integrasjon: outbox, nummerserie og statustabeller.
--
-- Kun katalog-endringer (CREATE TABLE). Ingen radskriving eller tabell-rewrite, så migreringen
-- er O(1) og trygg å kjøre ved oppstart. lock_timeout gjør at den feiler raskt og rent i stedet
-- for å stå i lås-kø.
SET lock_timeout = '3s';

-- Outbox for utgående meldinger til Team VALP sin tiltaksøkonomi-topic.
-- Kallere skriver hit i sin egen transaksjon; en bakgrunnspoller publiserer til Kafka.
-- Dermed ingen hard avhengighet til Kafka-oppetid, og publiseringsfeil bæres ikke innover.
CREATE TABLE IF NOT EXISTS oebs_outbox (
    id                BIGSERIAL PRIMARY KEY,
    -- Kafka record key = bestillingsnummer, gir ordering per bestilling i én partisjon.
    bestillingsnummer TEXT      NOT NULL,
    -- Diskriminator for meldingstype: BESTILLING / FAKTURA / ANNULLERING / GJOR_OPP_BESTILLING.
    meldingstype      TEXT      NOT NULL,
    -- Ferdig serialisert OebsBestillingMelding (JSON) klar til publisering.
    melding_json      JSONB     NOT NULL,
    status            TEXT      NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER   NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Poller henter neste upubliserte rad; indeks på (status, id) for effektiv FOR UPDATE SKIP LOCKED.
CREATE INDEX IF NOT EXISTS oebs_outbox_status_id_idx ON oebs_outbox (status, id);

-- Nummerserie: inkrementerende løpenummer per tilsagn per sak.
-- Én rad per sak holder neste ledige løpenummer. Oppdatering skjer i samme transaksjon som
-- outbox-skrivingen slik at et bestillingsnummer aldri deles ut to ganger.
CREATE TABLE IF NOT EXISTS oebs_lopenummer (
    sak_id         TEXT    PRIMARY KEY,
    neste_lopenr   INTEGER NOT NULL DEFAULT 1,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Status/kvittering fra OeBS (via VALP sine status-topics). Bærer flagg for manuell oppfølging
-- ved feilede bestillinger/utbetalinger.
CREATE TABLE IF NOT EXISTS oebs_bestilling_status (
    bestillingsnummer         TEXT      PRIMARY KEY,
    status                    TEXT      NOT NULL,
    trenger_manuell_oppfolging BOOLEAN  NOT NULL DEFAULT FALSE,
    mottatt_tidspunkt         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_json                  JSONB     NOT NULL
);

-- Oppslag for oppfølgingsflate: hvilke bestillinger trenger manuell håndtering.
CREATE INDEX IF NOT EXISTS oebs_bestilling_status_manuell_idx
    ON oebs_bestilling_status (trenger_manuell_oppfolging)
    WHERE trenger_manuell_oppfolging = TRUE;
