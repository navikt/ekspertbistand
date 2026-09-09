-- P3 (aggregate_root_id): lås invarianten for nye logg-rader umiddelbart, uten å bry seg om
-- historiske rader. NOT VALID betyr «ikke sjekk eksisterende rader», men constrainten håndheves
-- for alle nye og oppdaterte rader fra dette øyeblikket. Den er O(1) å legge til.
--
-- Kun event_log får constrainten nå: en NOT VALID-check håndheves ved UPDATE, og EventQueue.poll
-- oppdaterer status/attempts/updated_at på eksisterende kø-rader — en gammel NULL-rad ville da
-- blokkere køen. event_log er insert-only. Kø-constrainten legges i P6, etter backfill.
SET lock_timeout = '3s';

ALTER TABLE event_log
    ADD CONSTRAINT event_log_aggregate_root_id_nn
    CHECK (aggregate_root_id IS NOT NULL) NOT VALID;
