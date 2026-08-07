package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.arena.ArenaBehandlingStatus
import no.nav.ekspertbistand.arena.markerArenaSakUnderBehandling
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SaksbehandlerRoutingTest {

    private val ansattJson = """
        {
            "navIdent": "A123456",
            "visningNavn": "Tore Tang",
            "fornavn": "Tore",
            "etternavn": "Tang",
            "epost": "tore.tang@nav.no",
            "enhet": { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
            "tIdent": "T123456"
        }
    """.trimIndent()

    private val enheterJson = """
        [
            { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
            { "enhetnummer": "5678", "navn": "Nav Arbeid og Ytelser" }
        ]
    """.trimIndent()

    @Test
    fun `happy path - GET me returnerer saksbehandlerinfo`() = testApplicationWithDatabase { db ->
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
                provide<Database> { db.config.jdbcDatabase }
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
    fun `uautentisert request gir 401`() = testApplicationWithDatabase { db ->
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
                provide<Database> { db.config.jdbcDatabase }
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
    fun `inaktivt token gir 401`() = testApplicationWithDatabase { db ->
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
                provide<Database> { db.config.jdbcDatabase }
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
    fun `token uten NAVident gir 401`() = testApplicationWithDatabase { db ->
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
                provide<Database> { db.config.jdbcDatabase }
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
    fun `token uten groups gir tom roller-liste`() = testApplicationWithDatabase { db ->
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
                provide<Database> { db.config.jdbcDatabase }
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

    @Test
    fun `20 - markert sak gir underBehandlingIArena true`() = testApplicationWithDatabase { db ->
        val soknadId = UUID.randomUUID()
        transaction(db.config.jdbcDatabase) {
            markerArenaSakUnderBehandling(
                sakId = 13769058,
                saksnummer = "2026202",
                soknadId = soknadId,
                brukeridAnsvarlig = "K123456",
                aetatenhetAnsvarlig = "1899",
                sakstatuskode = "AKTIV",
            )
        }

        val response = arenaBehandlingRequest(db, soknadId.toString())

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ArenaBehandlingStatus>()
        assertEquals(true, body.underBehandlingIArena)
        assertEquals("K123456", body.brukeridAnsvarlig)
        assertEquals("1899", body.aetatenhetAnsvarlig)
        assertNotNull(body.observertAt)
    }

    @Test
    fun `21 - umarkert sak gir underBehandlingIArena false`() = testApplicationWithDatabase { db ->
        val response = arenaBehandlingRequest(db, UUID.randomUUID().toString())

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ArenaBehandlingStatus>()
        assertEquals(false, body.underBehandlingIArena)
        assertNull(body.brukeridAnsvarlig)
    }

    @Test
    fun `22 - arena-behandling uten token gir 401`() = testApplicationWithDatabase { db ->
        val response = arenaBehandlingRequest(db, UUID.randomUUID().toString(), token = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `ugyldig soknadId gir 400`() = testApplicationWithDatabase { db ->
        val response = arenaBehandlingRequest(db, "ikke-en-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private suspend fun ApplicationTestBuilder.arenaBehandlingRequest(
        db: TestDatabase,
        soknadId: String,
        token: String? = "valid-azure-token",
    ): HttpResponse {
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
                provide<Database> { db.config.jdbcDatabase }
                provide(EntraProxyClient::class)
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                                .withGroups(listOf("test-saksbehandler-group-id"))
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureSaksbehandlerApiV1()
            configureServer()
        }

        return client.get("/api/saksbehandling/v1/soknad/$soknadId/arena-behandling") {
            token?.let { bearerAuth(it) }
        }
    }
}


