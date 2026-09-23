-- OeBS/tiltaksøkonomi: løpenummer-serie for fakturaer.
--
-- Kun katalog-endring (CREATE TABLE). Ingen radskriving eller tabell-rewrite, så migreringen
-- er O(1) og trygg å kjøre ved oppstart. lock_timeout gjør at den feiler raskt og rent i stedet
-- for å stå i lås-kø.
SET lock_timeout = '3s';

-- Nummerserie for fakturaer: inkrementerende løpenummer per faktura per bestilling.
-- Fakturanummer = <bestillingsnummer>-<løpenr>, så telleren er skopet til bestillingsnummeret
-- (ikke saken). Én rad per bestilling holder neste ledige løpenummer. Oppdatering skjer i samme
-- transaksjon som outbox-skrivingen slik at et fakturanummer aldri deles ut to ganger.
CREATE TABLE IF NOT EXISTS oebs_faktura_lopenummer (
    bestillingsnummer TEXT      PRIMARY KEY,
    neste_lopenr      INTEGER   NOT NULL DEFAULT 1,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
