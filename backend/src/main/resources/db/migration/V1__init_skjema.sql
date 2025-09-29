CREATE TABLE IF NOT EXISTS skjema
(
    id                  uuid PRIMARY KEY,
    tittel              TEXT NOT NULL,
    organisasjonsnummer TEXT NOT NULL,
    beskrivelse         TEXT NOT NULL,
    opprettet_av        TEXT NOT NULL,
    opprettet_tidspunkt TEXT NOT NULL
);
CREATE INDEX skjema_organisasjonsnummer ON skjema (organisasjonsnummer);
