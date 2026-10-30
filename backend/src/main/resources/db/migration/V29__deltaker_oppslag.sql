-- Oppslag av deltakers arbeidsforhold og navn, se specifications/oppslag_deltaker_arbeidsforhold.md.
SET lock_timeout = '3s';

-- Satt når deltakerens arbeidsforhold er bekreftet i Aa-registeret og navnet er hentet fra PDL. Null betyr ikke bekreftet.
ALTER TABLE utkast ADD COLUMN IF NOT EXISTS ansatt_bekreftet_tidspunkt TIMESTAMP NULL;

-- Navnet hentes nå fra PDL. Navn skrevet inn for hånd i eksisterende utkast fjernes; fødselsnummeret blir stående.
UPDATE utkast SET ansatt_navn = NULL;

-- Rate limit for oppslag. Innlogget brukers fødselsnummer og deltakerens fødselsnummer lagres kun som SHA-256-hash.
-- Rader eldre enn én time slettes.
CREATE TABLE IF NOT EXISTS deltaker_oppslag (
    id BIGSERIAL PRIMARY KEY,
    innlogget_bruker_hash TEXT NOT NULL,
    fnr_hash TEXT NOT NULL,
    tidspunkt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS deltaker_oppslag_bruker_tidspunkt
    ON deltaker_oppslag (innlogget_bruker_hash, tidspunkt);
