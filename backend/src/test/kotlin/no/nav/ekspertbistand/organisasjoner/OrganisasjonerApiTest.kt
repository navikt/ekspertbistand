package no.nav.ekspertbistand.organisasjoner

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.configureBaseSetup
import no.nav.ekspertbistand.configureOrganisasjonerApiV1
import no.nav.ekspertbistand.infrastruktur.MockTokenIntrospector
import no.nav.ekspertbistand.infrastruktur.TokenExchanger
import no.nav.ekspertbistand.infrastruktur.TokenIntrospector
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.infrastruktur.mockIntrospectionResponse
import no.nav.ekspertbistand.infrastruktur.withPid
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
                                altinn2Tilganger = setOf("5384:1"),
                                altinn3Tilganger = setOf("nav_ekspertbistand_soknad"),
                                underenheter = listOf()
                            )
                        ),
                        altinn2Tilganger = setOf("5384:1"),
                        altinn3Tilganger = setOf("nav_ekspertbistand_soknad"),
                    )
                ),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf("5384:1", "nav_ekspertbistand_soknad"),
                    "1338" to setOf("5384:1", "nav_ekspertbistand_soknad")
                ),
                tilgangTilOrgNr = mapOf(
                    "5384:1" to setOf("1337", "1338"),
                    "nav_ekspertbistand_soknad" to setOf("1337"),
                )
            )
        )

        client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val altinnTilgangerClient = AltinnTilgangerClient(
            httpClient = client,
            authClient = object : TokenExchanger {
                override suspend fun exchange(
                    target: String,
                    userToken: String
                ): TokenResponse = TokenResponse.Success("dummy", 3600)
            }
        )

        application {
            dependencies {
                provide<TokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }
            configureBaseSetup()
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
                        "nav_ekspertbistand_soknad"
                      ],
                      "altinn2Tilganger": [
                        "5384:1"
                      ]
                    }
                  ],
                  "altinn3Tilganger": [
                    "nav_ekspertbistand_soknad"
                  ],
                  "altinn2Tilganger": [
                    "5384:1"
                  ]
                }
              ],
              "orgNrTilTilganger": {
                "1337": [
                  "5384:1",
                  "nav_ekspertbistand_soknad"
                ],
                "1338": [
                  "5384:1",
                  "nav_ekspertbistand_soknad"
                ]
              },
              "tilgangTilOrgNr": {
                "5384:1": [
                  "1337",
                  "1338"
                ],
                "nav_ekspertbistand_soknad": [
                  "1337"
                ]
              }
            }
        """.trimIndent(), response.bodyAsText(), JSONCompareMode.STRICT)
    }
}