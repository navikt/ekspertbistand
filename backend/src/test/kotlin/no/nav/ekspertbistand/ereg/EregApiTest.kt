package no.nav.ekspertbistand.ereg

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.infrastruktur.*
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
                    "poststed": ""
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
            defaultHttpClient = client,
            tokenExchanger = successTokenXTokenExchanger
        )

        application {
            dependencies {
                provide {
                    altinnTilgangerClient
                }
                provide {
                    EregClient(defaultHttpClient = client)
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide<EregService> { EregService(resolve()) }
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
        assertEquals("Testveien 1, 0557 OSLO", body.adresse)
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
            defaultHttpClient = client,
            tokenExchanger = successTokenXTokenExchanger
        )

        application {
            dependencies {
                provide {
                    altinnTilgangerClient
                }
                provide {
                    EregClient(defaultHttpClient = client)
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide<EregService> { EregService(resolve()) }
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
