package no.nav.ekspertbistand.ereg

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.testing.testApplication
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient.Companion.altinn3Ressursid
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.infrastruktur.IdentityProvider
import no.nav.ekspertbistand.infrastruktur.MockTokenIntrospector
import no.nav.ekspertbistand.infrastruktur.TokenExchanger
import no.nav.ekspertbistand.infrastruktur.TokenIntrospector
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.infrastruktur.configureTokenXAuth
import no.nav.ekspertbistand.infrastruktur.mockIntrospectionResponse
import no.nav.ekspertbistand.infrastruktur.withPid
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.mocks.mockEreg
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EregApiTest {
    private val orgnr = "133700000"

    @Test
    fun `returnerer adresse for virksomhet man har tilgang til`() = testApplication {
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(orgnr to setOf(altinn3Ressursid)),
                tilgangTilOrgNr = mapOf(altinn3Ressursid to setOf(orgnr)),
            )
        )
        mockEreg {
            """
            {
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo"
                  }
                ]
              }
            }
            """.trimIndent()
        }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val altinnTilgangerClient = AltinnTilgangerClient(
            httpClient = client,
            authClient = object : TokenExchanger {
                override suspend fun exchange(target: String, userToken: String): TokenResponse =
                    TokenResponse.Success("dummy", 3600)
            }
        )

        application {
            dependencies {
                provide {
                    altinnTilgangerClient
                }
                provide {
                    EregClient(httpClient = client)
                }
                provide<TokenIntrospector>(IdentityProvider.TOKEN_X.alias) {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureTokenXAuth()
            configureEregApiV1()
            configureServer()
        }

        val response = client.get("/api/ereg/$orgnr/adresse") {
            bearerAuth("faketoken")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AdresseResponse>()
        assertEquals("Testveien 1, 0557 Oslo", body.adresse)
    }

    @Test
    fun `gir forbidden hvis man ikke har tilgang`() = testApplication {
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = emptyMap(),
                tilgangTilOrgNr = emptyMap(),
            )
        )
        mockEreg { "{}" }

        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val altinnTilgangerClient = AltinnTilgangerClient(
            httpClient = client,
            authClient = object : TokenExchanger {
                override suspend fun exchange(target: String, userToken: String): TokenResponse =
                    TokenResponse.Success("dummy", 3600)
            }
        )

        application {
            dependencies {
                provide {
                    altinnTilgangerClient
                }
                provide {
                    EregClient(httpClient = client)
                }
                provide<TokenIntrospector>(IdentityProvider.TOKEN_X.alias) {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureTokenXAuth()
            configureEregApiV1()
        }

        val response = client.get("/api/ereg/$orgnr/adresse") {
            bearerAuth("faketoken")
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
