CREATE TABLE IF NOT EXISTS skjema (id uuid PRIMARY KEY, tittel TEXT NOT NULL, organisasjonsnummer TEXT NOT NULL, beskrivelse TEXT NOT NULL, opprettet_av TEXT NOT NULL, opprettet_tidspunkt TEXT NOT NULL);
CREATE INDEX skjema_organisasjonsnummer ON skjema (organisasjonsnummer);
CREATE TABLE IF NOT EXISTS utkast (id uuid PRIMARY KEY, tittel TEXT NULL, organisasjonsnummer TEXT NULL, beskrivelse TEXT NULL, opprettet_av TEXT NOT NULL, opprettet_tidspunkt TEXT NOT NULL);
