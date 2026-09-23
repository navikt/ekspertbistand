-- Etterlevelse: varig revisjonsspor for OeBS/tiltaksøkonomi-integrasjonen.
--
-- Vi kan ikke lene oss på Kafka-topics (90 dagers retention) for å svare for hva vi har bestilt og
-- hvilke svar vi fikk. Disse to tabellene er append-only revisjonslogger som lever like lenge som
-- forretningsbehovet, uavhengig av topic-retention.
--
-- Kun katalog-endringer (CREATE TABLE/INDEX). Ingen radskriving eller tabell-rewrite → O(1) og trygt
-- å kjøre ved oppstart.
SET lock_timeout = '3s';

-- Utgående: nøyaktig hva vi publiserte til tiltaksøkonomi-topicen, med Kafka-koordinater.
-- Skrives av outbox-polleren i SAMME transaksjon som den markerer outbox-raden publisert, slik at
-- loggen og publiseringen er atomiske.
CREATE TABLE IF NOT EXISTS oebs_sendt_melding (
    id                BIGSERIAL PRIMARY KEY,
    bestillingsnummer TEXT      NOT NULL,
    meldingstype      TEXT      NOT NULL,
    -- Nøyaktig serialisert melding slik den ble sendt.
    melding_json      JSONB     NOT NULL,
    kafka_topic       TEXT      NOT NULL,
    kafka_partition   INTEGER   NOT NULL,
    kafka_offset      BIGINT    NOT NULL,
    sendt_tidspunkt   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Oppslag: hva har vi sendt for en gitt bestilling.
CREATE INDEX IF NOT EXISTS oebs_sendt_melding_bestilling_idx
    ON oebs_sendt_melding (bestillingsnummer);

-- Inngående: alle svar/statusmeldinger vi mottok fra VALP/OeBS, rå og komplette.
-- Til forskjell fra oebs_bestilling_status (som holder siste tilstand) er dette full historikk.
CREATE TABLE IF NOT EXISTS oebs_mottatt_status (
    id                BIGSERIAL PRIMARY KEY,
    bestillingsnummer TEXT      NOT NULL,
    kafka_topic       TEXT      NOT NULL,
    kafka_partition   INTEGER   NOT NULL,
    kafka_offset      BIGINT    NOT NULL,
    kafka_tidspunkt   TIMESTAMP NOT NULL,
    -- Rå melding slik vi mottok den.
    raw_json          JSONB     NOT NULL,
    mottatt_tidspunkt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Idempotens: samme Kafka-melding kan bli reprosessert (at-least-once). Unik på Kafka-koordinatene
-- gjør at gjentatt prosessering ikke gir dupliserte revisjonsrader.
CREATE UNIQUE INDEX IF NOT EXISTS oebs_mottatt_status_koordinat_idx
    ON oebs_mottatt_status (kafka_topic, kafka_partition, kafka_offset);

-- Oppslag: alle svar for en gitt bestilling.
CREATE INDEX IF NOT EXISTS oebs_mottatt_status_bestilling_idx
    ON oebs_mottatt_status (bestillingsnummer);
