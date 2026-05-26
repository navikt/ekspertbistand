package no.nav.ekspertbistand.saksbehandler

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SaksbehandlerRoutingTest {

    private val ansattJson = """
        {
            "navIdent": "A123456",
            "visningNavn": "Tore Tang",
            "fornavn": "Tore",
            "etternavn": "Tang",
            "epost": "tore.tang@nav.no",
            "enhet": { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
            "tident": "T123456"
        }
    """.trimIndent()

    private val enheterJson = """
        [
            { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
            { "enhetnummer": "5678", "navn": "Nav Arbeid og Ytelser" }
        ]
    """.trimIndent()

    @Test
    fun `happy path - GET me returnerer saksbehandlerinfo`() = testApplication {
        mockEntraProxyFull(
            ansattProvider = { ansattJson },
            enheterProvider = { enheterJson },
        )

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                                .withGroups(listOf("test-saksbehandler-group-id", "test-beslutter-group-id"))
                        } else null
                    }
                }
            }

            configureAzureAdAuth()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandler/v1/me") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SaksbehandlerInfo>()
        assertEquals("A123456", body.navIdent)
        assertEquals("Tore Tang", body.visningNavn)
        assertEquals("Tore", body.fornavn)
        assertEquals("Tang", body.etternavn)
        assertEquals("tore.tang@nav.no", body.epost)
        assertEquals("T123456", body.tident)
        assertEquals(2, body.enheter.size)
        assertEquals(setOf(Role.SAKSBEHANDLER, Role.BESLUTTER), body.roller)
    }

    @Test
    fun `uautentisert request gir 401`() = testApplication {
        mockEntraProxyFull(
            ansattProvider = { "{}" },
            enheterProvider = { "[]" },
        )

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector { null }
                }
            }

            configureAzureAdAuth()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandler/v1/me")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `inaktivt token gir 401`() = testApplication {
        mockEntraProxyFull(
            ansattProvider = { "{}" },
            enheterProvider = { "[]" },
        )

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        TokenIntrospectionResponse(active = false, error = null)
                    }
                }
            }

            configureAzureAdAuth()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandler/v1/me") {
            bearerAuth("inactive-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `token uten NAVident gir 401`() = testApplication {
        mockEntraProxyFull(
            ansattProvider = { "{}" },
            enheterProvider = { "[]" },
        )

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        TokenIntrospectionResponse(
                            active = true,
                            error = null,
                            other = mapOf("groups" to listOf("test-saksbehandler-group-id")),
                        )
                    }
                }
            }

            configureAzureAdAuth()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandler/v1/me") {
            bearerAuth("no-navident-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `token uten groups gir tom roller-liste`() = testApplication {
        mockEntraProxyFull(
            ansattProvider = { ansattJson },
            enheterProvider = { enheterJson },
        )

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "no-groups-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAzureAdAuth()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandler/v1/me") {
            bearerAuth("no-groups-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<SaksbehandlerInfo>()
        assertEquals(emptySet(), body.roller)
    }
}


