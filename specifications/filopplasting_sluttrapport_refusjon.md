# Filopplasting — Sluttrapport

## Mål

Legge til støtte for opplasting av PDF-filer i backend for sluttrapport-innsendingsflyten:

- `POST /api/soknad/v1/{id}/sluttrapport` — arbeidsgiver laster opp sluttrapport fra ekspert

Filene virusscanes med ClamAV, verifiseres som gyldig PDF via magic bytes, og lagres midlertidig i PostgreSQL. Journalføring til DokArkiv planlegges som eget steg.

> **Merk:** Refusjon-endepunktet (`POST /api/soknad/v1/{id}/refusjon`) utsettes til eget steg. Infrastruktur (ClamAV-klient, vedlegg-tabell) designes likevel gjenbrukbart.

## Referanser

- [ClamAV på NAIS](https://doc.nais.io/services/antivirus/) — oppsett og API
- [NAIS Playbook — filopplasting](https://sikkerhet.nav.no) — virusscanning, filtype-verifisering, kryptering
- Eksempel fra pale-2: `ClamAvClient.kt` (se ovenfor i spec-prosessen)

## Bakgrunn

Frontend-siden `SluttrapportPage` er implementert bak feature-flagget `EKSPERTBISTAND_SAKSBEHANDLING_BETA` (kun local/dev). Backend mangler i dag endepunkter for å motta og lagre disse filene. Auth er TokenX (arbeidsgiver, eksisterende oppsett).

---

## Arkitektur

### Flyt per request

```
Browser (multipart/form-data)
  → POST /api/soknad/v1/{id}/sluttrapport
  → [1] Valider TokenX — innlogget bruker eier søknaden
  → [2] Les multipart — maks 5 filer, 10 MB per fil, kun PDF
  → [3] Verifiser magic bytes (%PDF-header)
  → [4] ClamAV virusskanning — blokkér ved FOUND eller ERROR
  → [5] Lagre i PostgreSQL (bytea) knyttet til soknad_id
  → 201 Created
```

### Tilgangskontroll (🔴 rød sone)

Innlogget bruker (`pid` fra TokenX) må tilhøre virksomheten som eier søknaden. Denne sjekken **må skrives manuelt** — se eksisterende mønster i `SoknadApi.kt`.

---

## API-kontrakt

### POST `/api/soknad/v1/{id}/sluttrapport`

**Content-Type:** `multipart/form-data`

| Part | Type | Beskrivelse |
|------|------|-------------|
| `fil` (1–5) | `application/pdf` | PDF-filer, maks 10 MB per fil |

**Responser:**

| Status | Beskrivelse |
|--------|-------------|
| `201 Created` | Filer lagret |
| `400 Bad Request` | Ugyldig filtype, for stor fil, ingen filer |
| `403 Forbidden` | Bruker tilhører ikke virksomheten |
| `404 Not Found` | Søknad ikke funnet |
| `422 Unprocessable Entity` | Virus funnet i fil |
| `503 Service Unavailable` | ClamAV utilgjengelig |

---

## Datamodell

### Ny tabell: `vedlegg`

```sql
CREATE TABLE vedlegg (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soknad_id   UUID NOT NULL REFERENCES soknad(id) ON DELETE CASCADE,
    type        TEXT NOT NULL,          -- 'SLUTTRAPPORT' (utvides med 'REFUSJON' senere)
    filnavn     TEXT NOT NULL,
    innhold     BYTEA NOT NULL,
    storrelse   INT NOT NULL,
    lastet_opp  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vedlegg_soknad_id ON vedlegg(soknad_id);
```

### Ny tabell: `refusjonskrav` _(utsatt — ikke implementeres nå)_

```sql
-- TODO: implementeres i eget steg for refusjon-flyten
CREATE TABLE refusjonskrav (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    soknad_id    UUID NOT NULL REFERENCES soknad(id) ON DELETE CASCADE,
    beskrivelse  TEXT NOT NULL,
    belop        INT NOT NULL,
    sendt_inn    TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## ClamAV-integrasjon

### Nais-konfigurasjon (tillegg i `dev-gcp-backend.yaml` og `prod-gcp-backend.yaml`)

```yaml
accessPolicy:
  outbound:
    rules:
      - application: clamav
        namespace: nais-system
```

### Klient

Ny fil: `backend/src/main/kotlin/no/nav/ekspertbistand/clamav/ClamAvClient.kt`

```kotlin
class ClamAvClient(
    private val httpClient: HttpClient,
    private val endpointUrl: String = "http://clamav.nais-system/scan",
) {
    suspend fun scan(filnavn: String, innhold: ByteArray, contentType: String): ScanResultat

    data class ScanResultat(val filnavn: String, val result: Status)
    enum class Status { OK, FOUND, ERROR }
}
```

---

## PDF-verifisering

Magic bytes for PDF: første 4 bytes må være `25 50 44 46` (`%PDF`).

```kotlin
fun erGyldigPdf(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
    bytes[0] == 0x25.toByte() &&  // %
    bytes[1] == 0x50.toByte() &&  // P
    bytes[2] == 0x44.toByte() &&  // D
    bytes[3] == 0x46.toByte()     // F
```

---

## Filstruktur

```
backend/src/main/kotlin/no/nav/ekspertbistand/
├── clamav/
│   └── ClamAvClient.kt          # ny
├── vedlegg/
│   ├── VedleggApi.kt            # ny — multipart-parsing, validering, orchestrering
│   ├── VedleggDb.kt             # ny — lagrings- og hente-operasjoner
│   └── VedleggRouting.kt        # ny — ruter registrert i Application.kt
└── soknad/
    └── Routing.kt               # endret — kall til configureSoknadApiV1() beholder eksisterende ruter
```

```
backend/src/main/resources/db/migration/
└── V5__legg_til_vedlegg.sql
```

---

## Sikkerhetssjekkliste

- [x] TokenX valideres av Ktor-middleware (eksisterende)
- [ ] 🔴 Tilgangskontroll: bruker må tilhøre virksomheten som eier søknaden
- [x] Magic bytes-verifisering av PDF
- [x] ClamAV — blokkér ved `FOUND` og `ERROR`
- [x] Filstørrelse valideres (maks 10 MB)
- [x] Antall filer valideres (maks 5)
- [x] Ingen PII logges (kun `soknadId`, filstørrelse, status)
- [x] `ON DELETE CASCADE` — vedlegg slettes med søknaden

---

## Observability

| Metrikk | Beskrivelse |
|---------|-------------|
| `vedlegg_lastet_opp_total` | Counter — type=SLUTTRAPPORT |
| `clamav_scan_resultat_total` | Counter med label `result=OK|FOUND|ERROR` |
| `clamav_scan_duration_seconds` | Histogram for ClamAV-responstid |

---

## Teststrategi

| Test | Type | Beskrivelse |
|------|------|-------------|
| `ClamAvClientTest` | Integrasjonstest (MockEngine) | `OK`, `FOUND`, `ERROR` responsene |
| `VedleggApiTest` | Enhetstest | Magic bytes-validering, filstørrelse, antall |
| `VedleggRoutingTest` | Integrasjonstest (Ktor testApplication) | Hele flyten med mock ClamAV |
| Tilgangskontroll | Integrasjonstest | Bruker uten tilgang får 403 |

---

## 🔴 Rød sone — skriv selv

- [ ] **Tilgangskontroll i `VedleggApi`** — verifiser at `innloggetBruker` (pid fra TokenX) tilhører virksomheten som eier søknaden. Sikkerhetskritisk — se eksisterende mønster i `SoknadApi.kt`.

## 🟢 Grønn sone — genereres

- [ ] `ClamAvClient.kt` — HTTP-klient mot ClamAV
- [ ] `VedleggDb.kt` — Flyway-migrasjoner og DB-operasjoner
- [ ] `VedleggApi.kt` — multipart-parsing, magic bytes, kall til ClamAV
- [ ] `VedleggRouting.kt` — ruter
- [ ] Nais-manifest-endring (ClamAV outbound)
- [ ] Tester (scaffold)

---

## Åpne spørsmål

- [x] `slettGamleInnsendteSoknader`-jobben sletter vedlegg automatisk via `ON DELETE CASCADE` på `vedlegg.soknad_id`
- [ ] Journalføring til DokArkiv: eget spec-dokument når dette skal implementeres
- [ ] Refusjon-endepunkt og `refusjonskrav`-tabell: eget steg
