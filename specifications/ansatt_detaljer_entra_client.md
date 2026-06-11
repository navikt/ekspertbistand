# EntraProxyClient — Hent informasjon om ansatt ved bruk av NavIdent

## Mål

Utvide eksisterende `EntraProxyClient` med en ny suspend-funksjon for å hente utvidet informasjon om en ansatt. Input er ansattens `navIdent`. Returnerer `UtvidetAnsatt` med navn, e-post, enhet og t-ident.

## API-endepunkt

**GET** `/api/v1/ansatt/{navIdent}`

Server: `https://entraproxy.intern.nav.no` (prod) / `https://entraproxy.intern.dev.nav.no` (dev)

### Request

Headers:
- `Authorization: Bearer <token>` — Azure AD token (required, CC-flow)

Path:
- `navIdent` — ansattens NAV-ident (f.eks. `A123456`)

### Response

`200 OK` — `UtvidetAnsatt`:
```json
{
  "navIdent": "A123456",
  "visningNavn": "Tore Tang",
  "fornavn": "Tore",
  "etternavn": "Tang",
  "epost": "tore.tang@nav.no",
  "enhet": {
    "enhetnummer": "1234",
    "navn": "Nav Avdeling Sydpolen"
  },
  "tIdent": "T123456"
}
```

Nullable felter: `visningNavn`, `fornavn`, `etternavn`, `epost`. Feltet `enhet` er av eksisterende type `Enhet`. `navIdent` og `tIdent` er non-nullable.

## Implementasjonsplan

### 1. Ny DTO: UtvidetAnsatt

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/entraproxy/EntraProxyClient.kt` (samme fil som `Enhet`)

`@Serializable` data class:

```kotlin
@Serializable
data class UtvidetAnsatt(
    val navIdent: String,
    val visningNavn: String? = null,
    val fornavn: String? = null,
    val etternavn: String? = null,
    val epost: String? = null,
    val enhet: Enhet,
    val tIdent: String,
)
```

Nullable felter har default `null` slik at de håndteres korrekt ved deserialisering med `defaultJson` (`ignoreUnknownKeys = true`).

### 2. Ny suspend-funksjon i EntraProxyClient

Legg til ny konstant og funksjon i eksisterende `EntraProxyClient`:

```kotlin
companion object {
    // eksisterende:
    const val API_PATH = "/api/v1/enhet/ansatt"
    // ny:
    const val ANSATT_API_PATH = "/api/v1/ansatt"
}
```

```kotlin
suspend fun hentAnsatt(navIdent: String): UtvidetAnsatt =
    httpClient.get {
        url {
            takeFrom(ingress)
            path("$ANSATT_API_PATH/$navIdent")
        }
        accept(ContentType.Application.Json)
        bearerAuth(
            tokenProvider.token(targetAudience).fold(
                { it.accessToken },
                { throw Exception("Failed to get token: ${it.error}") }
            )
        )
    }.body()
```

Følg nøyaktig samme mønster som `hentEnheter`.

### 3. Utvid EntraProxyMock

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/mocks/EntraProxyMock.kt`

Legg til en ny route i `externalServices`-blokken for `ANSATT_API_PATH`:

```kotlin
get("${EntraProxyClient.ANSATT_API_PATH}/{navIdent}") {
    val navIdent = call.parameters["navIdent"]!!
    val response = ansattResponseProvider(navIdent)
    call.respondText(response, contentType = ContentType.Application.Json)
}
```

Vurder å utvide `mockEntraProxy` med en valgfri `ansattResponseProvider`-parameter, eller lag en separat `mockEntraProxyAnsatt`-funksjon. Begge tilnærminger er akseptable — velg den som holder testene lesbare.

### 4. Tester

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/entraproxy/EntraProxyClientTest.kt`

Legg til tester i eksisterende testklasse. Følg mønsteret fra `hentEnheter`-testene:

**Test 1: Happy path**
```kotlin
@Test
fun `henter ansattdetaljer for navIdent`() = testApplication {
    val navIdent = "A123456"
    // mock med full UtvidetAnsatt JSON-respons
    // verifiser alle felter inkludert nested Enhet
}
```

**Test 2: Nullable felter**
```kotlin
@Test
fun `haandterer null-felter i ansatt-respons`() = testApplication {
    // mock med respons der visningNavn, fornavn, etternavn, epost er null
    // verifiser at UtvidetAnsatt deserialiseres korrekt med null-verdier
}
```

**Test 3: Ukjente felter**
```kotlin
@Test
fun `haandterer ukjente felter i ansatt-respons`() = testApplication {
    // mock med ekstra felter i JSON
    // verifiser at ignoreUnknownKeys fungerer
}
```

**Test 4: Token-feil**
```kotlin
@Test
fun `feiler ved ugyldig token for hentAnsatt`() = testApplication {
    // bruk failingTokenProvider (se eksisterende test)
    // verifiser at Exception kastes med "Failed to get token"
}
```

Bruk eksisterende `mockTokenProvider` fra testfilen. Ingen nye testbiblioteker.

### 5. Ingen endringer nødvendig

Følgende krever **ingen** endringer:
- **DI-registrering** (`Application.kt`): `EntraProxyClient` er allerede registrert
- **NAIS-konfigurasjon**: `entraproxy.intern.nav.no` / `entraproxy.intern.dev.nav.no` er allerede i `accessPolicy.outbound.external`
- **Token audience**: Samme `targetAudience` brukes — ingen ny scope nødvendig

## Acceptance Criteria

- [ ] `UtvidetAnsatt` data class er `@Serializable` med nullable felter (`visningNavn`, `fornavn`, `etternavn`, `epost`) og non-nullable felter (`navIdent`, `tIdent`, `enhet`)
- [ ] `UtvidetAnsatt` gjenbruker eksisterende `Enhet`-type for `enhet`-feltet
- [ ] Ny `suspend fun hentAnsatt(navIdent: String): UtvidetAnsatt` i `EntraProxyClient`
- [ ] Kaller `GET /api/v1/ansatt/{navIdent}` med bearer token
- [ ] Følger nøyaktig samme mønster som `hentEnheter` (url-bygging, auth, feilhåndtering)
- [ ] `ANSATT_API_PATH` konstant i companion object
- [ ] Mock utvidet i `EntraProxyMock.kt` for ny route
- [ ] Tester: happy path, nullable felter, ukjente felter, token-feil
- [ ] Ingen nye avhengigheter eller biblioteker
- [ ] Ingen endringer i `Application.kt` eller NAIS-konfigurasjon (allerede på plass)
