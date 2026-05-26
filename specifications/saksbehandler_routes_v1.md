# Saksbehandler Routes v1 — Autentiserte ruter for innlogget saksbehandler

## Mål

Legge til Azure AD-autentisering for saksbehandler-ruter og implementere det første endepunktet `GET /api/saksbehandler/v1/me` som returnerer ansattdetaljer, enheter og roller for den innloggede saksbehandleren.

Roller utledes fra `groups`-claimet i Azure AD-tokenet og mappes til en intern `Role`-enum.

## Referanser

- [Entra ID reference (NAIS doc)](https://doc.nais.io/auth/entra-id/reference/) — claims, tenants, token validation, og troubleshooting

## Bakgrunn

Eksisterende ruter bruker TokenX (`TOKENX_PROVIDER`) for innlogging av arbeidsgivere/brukere. Saksbehandler-rutene skal bruke Azure AD-autentisering (Entra ID) for NAV-ansatte. Tokenet introspekteres via `NAIS_TOKEN_INTROSPECTION_ENDPOINT` og relevante claims (`NAVident`, `groups`) ekstraheres.

## NAIS-konfigurasjon

### dev-gcp-backend.yaml

Legg til `tenant` og `claims.groups` under eksisterende `azure.application`:

```yaml
azure:
  application:
    enabled: true
    tenant: trygdeetaten.no
    claims:
      groups:
        # saksbehandler-gruppe
        - id: ""
        # Beslutter-gruppe
        - id: ""
        # Fortrolig adresse gruppe
        - id: ""
        # Strengt fortrolig adresse gruppe
        - id: ""
```

### prod-gcp-backend.yaml

Legg til `tenant` og `claims.groups` under eksisterende `azure.application`. Gruppe-IDer i prod vil avvike fra dev — la verdiene stå tomme inntil prod-IDer er bekreftet:

```yaml
azure:
  application:
    enabled: true
    tenant: nav.no
    claims:
      groups:
        # saksbehandler-gruppe
        - id: ""
        # Beslutter-gruppe
        - id: ""
        # Fortrolig adresse gruppe
        - id: ""
        # Strengt fortrolig adresse gruppe
        - id: ""
```

## Implementasjonsplan

### 1. Azure AD-autentisering — `configureAzureAdAuth()`

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/infrastruktur/Auth.kt`

Opprett en ny `AzureAdPrincipal` data class og `configureAzureAdAuth()` etter mønsteret fra eksisterende `configureTokenXAuth()`.

```kotlin
data class AzureAdPrincipal(
    val navIdent: String,
    val groups: List<String>,
)

const val AZURE_AD_PROVIDER = "AZURE_AD"

suspend fun Application.configureAzureAdAuth() {
    val introspector = // se steg nedenfor
    
    install(Authentication) {
        bearer(AZURE_AD_PROVIDER) {
            authenticate { credentials ->
                val introspection = introspector.introspect(credentials.token)
                
                with(introspection) {
                    if (!active) return@authenticate null
                    
                    val navIdent = other["NAVident"] as? String
                        ?: return@authenticate null
                    val groups = (other["groups"] as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()
                    
                    AzureAdPrincipal(
                        navIdent = navIdent,
                        groups = groups,
                    )
                }
            }
        }
    }
}
```

**Viktig:** `Authentication`-pluginet er allerede installert i `configureTokenXAuth()`. Ktor tillater flere `install(Authentication)`-kall som merger providers. Men verifiser at dette fungerer — alternativt flytt begge providerne inn i ett felles `install(Authentication)`-kall i en ny felles `configureAuth()`-funksjon.

**Introspection:** Eksisterende `TokenXTokenIntrospector` bruker `identity_provider = "tokenx"`. For Azure AD trengs en tilsvarende introspector som sender `identity_provider = "azuread"` til `NAIS_TOKEN_INTROSPECTION_ENDPOINT`. Vurder:
- Opprett `AzureAdTokenIntrospector`-interface (etter mønster fra `TokenXTokenIntrospector`) som extender `TokenIntrospector`
- La `AzureAdAuthClient` implementere dette interfacet (den extender allerede `AuthClient` som har `introspect()`)
- Registrer i DI: `provide<AzureAdTokenIntrospector>(AzureAdAuthClient::class)`

### 2. Role-enum og group-mapping

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandler/Roles.kt`

```kotlin
package no.nav.ekspertbistand.saksbehandling

import no.nav.ekspertbistand.infrastruktur.basedOnEnv

enum class Role(val groupId: String) {
    SAKSBEHANDLER(basedOnEnv(
        prod = "",   // prod group ID TBD
        dev = "fbfba82d-13da-43ad-a2f2-d7f21cb95f42",
        other = "test-saksbehandler-group-id",
    )),
    BESLUTTER(basedOnEnv(
        prod = "",
        dev = "fbfea82d-13da-43ad-a2f2-d7f21cb95f12",
        other = "test-beslutter-group-id",
    )),
    FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "2e1dc582-f762-4510-a660-88bf68fb7128",
        other = "test-fortrolig-group-id",
    )),
    STRENGT_FORTROLIG_ADRESSE(basedOnEnv(
        prod = "",
        dev = "a1b518e2-3947-478d-8929-e5d685c47cac",
        other = "test-strengt-fortrolig-group-id",
    ));

    companion object {
        fun fromGroups(groups: List<String>): Set<Role> =
            entries.filter { it.groupId in groups }.toSet()
    }
}
```

### 3. Response DTO — `SaksbehandlerInfo`

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandler/SaksbehandlerInfo.kt`

```kotlin
package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.entraproxy.Enhet
import no.nav.ekspertbistand.entraproxy.UtvidetAnsatt

@Serializable
data class SaksbehandlerInfo(
    val navIdent: String,
    val visningNavn: String? = null,
    val fornavn: String? = null,
    val etternavn: String? = null,
    val epost: String? = null,
    val enhet: Enhet,
    val tident: String,
    val enheter: List<Enhet>,
    val roller: Set<Role>,
)
```

`SaksbehandlerInfo` kombinerer data fra `UtvidetAnsatt` (ansattdetaljer), `hentEnheter` (enheter saksbehandleren har tilgang til) og rollemapping fra tokenet.

### 4. Routing — `configureSaksbehandlerApiV1()`

Plassering: `backend/src/main/kotlin/no/nav/ekspertbistand/saksbehandler/Routing.kt`

```kotlin
package no.nav.ekspertbistand.saksbehandling

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal

suspend fun Application.configureSaksbehandlerApiV1() {
    val entraProxyClient = dependencies.resolve<EntraProxyClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandler/v1") {
                get("/me") {
                    val principal = call.principal<AzureAdPrincipal>()!!
                    val navIdent = principal.navIdent
                    val roles = Role.fromGroups(principal.groups)

                    val ansatt = entraProxyClient.hentAnsatt(navIdent)
                    val enheter = entraProxyClient.hentEnheter(navIdent)

                    call.respond(
                        SaksbehandlerInfo(
                            navIdent = ansatt.navIdent,
                            visningNavn = ansatt.visningNavn,
                            fornavn = ansatt.fornavn,
                            etternavn = ansatt.etternavn,
                            epost = ansatt.epost,
                            enhet = ansatt.enhet,
                            tident = ansatt.tident,
                            enheter = enheter,
                            roller = roles,
                        )
                    )
                }
            }
        }
    }
}
```

### 5. Registrering i Application.kt

I `main()`:
- Legg til `provide<AzureAdTokenIntrospector>(AzureAdAuthClient::class)` i `dependencies`-blokken
- Kall `configureAzureAdAuth()` etter `configureTokenXAuth()`
- Kall `configureSaksbehandlerApiV1()` i module-konfigurasjon

```kotlin
// i main()
configureTokenXAuth()
configureAzureAdAuth()

// modules
configureSaksbehandlerApiV1()
```

### 6. Tester

Plassering: `backend/src/test/kotlin/no/nav/ekspertbistand/saksbehandler/`

#### 6a. RolesTest.kt

```kotlin
@Test
fun `mapper groups til roller`() {
    val groups = listOf("fbfba82d-13da-43ad-a2f2-d7f21cb95f42", "fbfea82d-13da-43ad-a2f2-d7f21cb95f12")
    val roles = Role.fromGroups(groups)
    assertEquals(setOf(Role.SAKSBEHANDLER, Role.BESLUTTER), roles)
}

@Test
fun `ukjente groups ignoreres`() {
    val roles = Role.fromGroups(listOf("unknown-group-id"))
    assertEquals(emptySet(), roles)
}

@Test
fun `tom groups gir tomme roller`() {
    val roles = Role.fromGroups(emptyList())
    assertEquals(emptySet(), roles)
}
```

#### 6b. SaksbehandlerRoutingTest.kt

Test med `testApplication` og mock av Azure AD-autentisering:

**Mock Azure AD auth:** Utvid `AuthMock.kt` med en `MockAzureAdIntrospector` som returnerer en `TokenIntrospectionResponse` med `NAVident` og `groups` i `other`-mappet. Alternativt opprett mock-hjelpere i testfilen.

**Test 1: Happy path — GET /api/saksbehandler/v1/me**
- Mock introspector returnerer aktiv token med `NAVident` og gyldige `groups`
- Mock `EntraProxyClient` (via `externalServices` / EntraProxyMock) returnerer ansattdetaljer og enheter
- Verifiser at responsen inneholder alle felter fra `SaksbehandlerInfo`

**Test 2: Uautentisert request**
- Ingen bearer token → HTTP 401

**Test 3: Inaktiv/ugyldig token**
- Introspector returnerer `active = false` → HTTP 401

**Test 4: Token uten NAVident**
- Introspector returnerer aktiv token men uten `NAVident` i claims → HTTP 401

**Test 5: Token uten groups**
- Gyldige claims men `groups` mangler → Responsen har tom `roller`-liste

Bruk eksisterende testinfrastruktur (`externalServices`, `mockEntraProxy`, `mockTokenProvider`). Ingen nye testbiblioteker.

## API-endepunkt

**GET** `/api/saksbehandler/v1/me`

### Request

Headers:
- `Authorization: Bearer <azure-ad-token>` — Azure AD token for innlogget saksbehandler (login eller OBO-flow)

### Response

`200 OK`:
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
  "tident": "T123456",
  "enheter": [
    { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
    { "enhetnummer": "5678", "navn": "Nav Arbeid og Ytelser" }
  ],
  "roller": ["SAKSBEHANDLER", "BESLUTTER"]
}
```

`401 Unauthorized` — manglende, ugyldig, eller inaktivt token; manglende `NAVident`-claim.

## Filstruktur — nye/endrede filer

```
backend/src/main/kotlin/no/nav/ekspertbistand/
├── infrastruktur/
│   └── Auth.kt                         (endret — legg til AzureAdPrincipal, AzureAdTokenIntrospector, configureAzureAdAuth)
├── saksbehandler/
│   ├── Roles.kt                        (ny — Role enum med group-mapping)
│   ├── SaksbehandlerInfo.kt            (ny — response DTO)
│   └── Routing.kt                      (ny — configureSaksbehandlerApiV1)
├── Application.kt                      (endret — DI + configureAzureAdAuth + configureSaksbehandlerApiV1)

nais/
├── dev-gcp-backend.yaml                (endret — azure.application.tenant + claims.groups)
├── prod-gcp-backend.yaml               (endret — azure.application.tenant + claims.groups med tomme IDer)

backend/src/test/kotlin/no/nav/ekspertbistand/
├── infrastruktur/
│   └── AuthMock.kt                     (endret — legg til MockAzureAdIntrospector)
├── saksbehandler/
│   ├── RolesTest.kt                    (ny)
│   └── SaksbehandlerRoutingTest.kt     (ny)
```

## Acceptance Criteria

- [ ] Azure AD-autentisering konfigurert med `bearer(AZURE_AD_PROVIDER)` som introspekterer token og ekstraherer `NAVident` og `groups`
- [ ] `AzureAdPrincipal` data class med `navIdent: String` og `groups: List<String>`
- [ ] `AzureAdTokenIntrospector` interface registrert i DI, implementert av `AzureAdAuthClient`
- [ ] `Role` enum med `SAKSBEHANDLER`, `BESLUTTER`, `FORTROLIG_ADRESSE`, `STRENGT_FORTROLIG_ADRESSE`
- [ ] `Role.fromGroups(groups)` mapper Azure AD group-IDer til `Set<Role>` basert på miljø (dev/prod/other)
- [ ] `GET /api/saksbehandler/v1/me` returnerer `SaksbehandlerInfo` med ansattdetaljer, enheter og roller
- [ ] Ruten er beskyttet med `authenticate(AZURE_AD_PROVIDER)`
- [ ] NAIS dev-konfigurasjon oppdatert med `tenant: trygdeetaten.no` og 4 group-IDer
- [ ] NAIS prod-konfigurasjon oppdatert med `tenant: nav.no` og tomme group-IDer (placeholder)
- [ ] `configureAzureAdAuth()` kalles i `Application.kt`
- [ ] `configureSaksbehandlerApiV1()` kalles i `Application.kt`
- [ ] Tester: Role-mapping (happy path, ukjente groups, tom liste)
- [ ] Tester: Routing (happy path, 401 uten token, 401 inaktivt token, 401 uten NAVident, tom roller ved manglende groups)
- [ ] Ingen nye testbiblioteker — bruk eksisterende `externalServices`, `EntraProxyMock`, `AuthMock`-infrastruktur
- [ ] `SaksbehandlerInfo` og `Role` er `@Serializable`
