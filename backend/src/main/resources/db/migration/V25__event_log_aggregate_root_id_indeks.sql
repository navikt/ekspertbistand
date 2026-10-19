-- SaksbehandlingsstatistikkProjection bygger tilstanden til en behandling fra event_log ved å slå opp
-- tidligere eventer for samme aggregatrot. Se backend/src/main/kotlin/no/nav/ekspertbistand/statistikk/README.md.
CREATE INDEX IF NOT EXISTS event_log_aggregate_root_id_idx ON event_log (aggregate_root_id, id);
