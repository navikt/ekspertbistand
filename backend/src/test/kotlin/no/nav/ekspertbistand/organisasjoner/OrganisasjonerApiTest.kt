package no.nav.ekspertbistand.organisasjoner

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient.Companion.altinn3Ressursid
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.configureOrganisasjonerApiV1
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import org.skyscreamer.jsonassert.JSONAssert.assertEquals
import org.skyscreamer.jsonassert.JSONCompareMode
import kotlin.test.Test
import kotlin.test.assertEquals

class OrganisasjonerApiTest {
    @Test
    fun `Hent organisasjoner test`() = testApplication {
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = listOf(
                    AltinnTilgangerClientResponse.AltinnTilgang(
                        erSlettet = false,
                        orgnr = "1337",
                        organisasjonsform = "AS",
                        navn = "Olsenbanden AS",
                        underenheter = listOf(
                            AltinnTilgangerClientResponse.AltinnTilgang(
                                erSlettet = false,
                                orgnr = "1338",
                                organisasjonsform = "AS",
                                navn = "Olsenbanden AS",
                                altinn2Tilganger = setOf(),
                                altinn3Tilganger = setOf(altinn3Ressursid),
                                underenheter = listOf()
                            )
                        ),
                        altinn2Tilganger = setOf(),
                        altinn3Tilganger = setOf(altinn3Ressursid),
                    )
                ),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf(altinn3Ressursid),
                    "1338" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("1337", "1338"),
                )
            )
        )

        client = createClient {
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
                provide<TokenIntrospector>(IdentityProvider.TOKEN_X.alias) {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }
            configureServer()
            configureTokenXAuth()
            configureOrganisasjonerApiV1()
        }

        val response = client.get("/api/organisasjoner/v1") {
            header(HttpHeaders.Authorization, "Bearer faketoken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """
           {
              "isError": false,
              "hierarki": [
                {
                  "erSlettet": false,
                  "orgnr": "1337",
                  "organisasjonsform": "AS",
                  "navn": "Olsenbanden AS",
                  "underenheter": [
                    {
                      "erSlettet": false,
                      "orgnr": "1338",
                      "organisasjonsform": "AS",
                      "navn": "Olsenbanden AS",
                      "underenheter": [],
                      "altinn3Tilganger": [
                        "nav_tiltak_ekspertbistand"
                      ],
                      "altinn2Tilganger": []
                    }
                  ],
                  "altinn3Tilganger": [
                    "nav_tiltak_ekspertbistand"
                  ],
                  "altinn2Tilganger": []
                }
              ],
              "orgNrTilTilganger": {
                "1337": [
                  "nav_tiltak_ekspertbistand"
                ],
                "1338": [
                  "nav_tiltak_ekspertbistand"
                ]
              },
              "tilgangTilOrgNr": {
                "nav_tiltak_ekspertbistand": [
                  "1337",
                  "1338"
                ]
              }
            }
        """.trimIndent(), response.bodyAsText(), JSONCompareMode.STRICT)
    }
}