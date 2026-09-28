-- Søknader slettes etter slettSøknadOm (se slettGamleInnsendteSoknader). Saken hører til søknaden
-- og skal slettes sammen med den, på samme måte som refusjonskrav og vedlegg.
ALTER TABLE sak DROP CONSTRAINT sak_soknad_id_fkey;
ALTER TABLE sak ADD CONSTRAINT sak_soknad_id_fkey
    FOREIGN KEY (soknad_id) REFERENCES soknad(id) ON DELETE CASCADE;
