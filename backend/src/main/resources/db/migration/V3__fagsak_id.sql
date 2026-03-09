CREATE TABLE IF NOT EXISTS fagsak_id_mapping (soknad_id TEXT PRIMARY KEY, fagsak_id BIGSERIAL NOT NULL);
ALTER TABLE fagsak_id_mapping ADD CONSTRAINT fagsak_id_mapping_fagsak_id_unique UNIQUE (fagsak_id);
ALTER TABLE fagsak_id_mapping ADD CONSTRAINT fagsak_id_mapping_soknad_id_fagsak_id_unique UNIQUE (soknad_id, fagsak_id);
CREATE SEQUENCE IF NOT EXISTS fagsak_id_mapping_fagsak_id_seq START WITH 1 MINVALUE 1 MAXVALUE 9223372036854775807;
