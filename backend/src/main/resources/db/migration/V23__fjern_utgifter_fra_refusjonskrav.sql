-- Feltet er fjernet fra refusjonssøknaden. Eksisterende beskrivelser slettes permanent.
ALTER TABLE refusjonskrav
    DROP COLUMN utgifter;
