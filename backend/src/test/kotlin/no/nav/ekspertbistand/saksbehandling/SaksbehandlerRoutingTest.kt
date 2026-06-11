package no.nav.ekspertbistand.saksbehandling

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

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/meg") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<InnloggetAnsattResponse>()
        assertEquals("A123456", body.id)
        assertEquals("Tore Tang", body.navn)
        assertEquals("tore.tang@nav.no", body.epost)
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

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/meg")

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

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/meg") {
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

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/meg") {
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

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/meg") {
            bearerAuth("no-groups-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<InnloggetAnsattResponse>()
        assertEquals(emptySet(), body.roller)
    }
}


