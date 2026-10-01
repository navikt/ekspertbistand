package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.arena.ArenaBehandlingStatus
import no.nav.ekspertbistand.arena.markerArenaSakUnderBehandling
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.sak.TEST_ANSATT_FNR
import no.nav.ekspertbistand.sak.lagreSaksloggInnslag
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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

    private val grupperJson = """
        [
            { "rolle": "0000-CA-Ekspertbistand_Saksbehandler" },
            { "rolle": "0000-CA-Ekspertbistand_Beslutter" }
        ]
    """.trimIndent()

    @Test
    fun `happy path - GET me returnerer saksbehandlerinfo`() = testApplicationWithDatabase { db ->
        mockEntraProxyFull(
            ansattProvider = { ansattJson },
            enheterProvider = { enheterJson },
            grupperProvider = { grupperJson },
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        TokenIntrospectionResponse(
                            active = true,
                            error = null,
                            other = mapOf("name" to "Tore Tang"),
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
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
    fun `feil ved gruppeoppslag mot entra-proxy gir 401`() = testApplicationWithDatabase { db ->
        mockEntraProxyFull(
            ansattProvider = { ansattJson },
            enheterProvider = { enheterJson },
            grupperProvider = { "dette er ikke gyldig json" },
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
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
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
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

    @OptIn(ExperimentalTime::class)
    @Test
    fun `sakslogg returneres nyeste forst med navn fra entra`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        lagreSaksloggInnslag(
            database, sak.sakId, AktorRolle.SYSTEM, null,
            "Søknad mottatt fra arbeidsgiver", Instant.parse("2026-03-30T10:00:00Z"),
        )
        lagreSaksloggInnslag(
            database, sak.sakId, AktorRolle.SAKSBEHANDLER, "A123456",
            "Sak tildelt", Instant.parse("2026-04-02T06:00:00Z"),
        )
        lagreSaksloggInnslag(
            database, sak.sakId, AktorRolle.BESLUTTER, "B999999",
            "Vedtak fattet", Instant.parse("2026-04-06T12:15:00Z"),
        )
        lagreSaksloggInnslag(
            database, lagreSoknadOgSak(database).sakId, AktorRolle.SYSTEM, null,
            "Annen sak", Instant.parse("2026-04-07T12:15:00Z"),
        )

        val response = saksloggRequest(db, sak.sakId.toString(), ansattProvider = { ident ->
            if (ident == "A123456") ansattJson else "ugyldig json"
        })

        assertEquals(HttpStatusCode.OK, response.status)
        val innslag = response.body<SaksloggResponse>().innslag
        assertEquals(listOf("Vedtak fattet", "Sak tildelt", "Søknad mottatt fra arbeidsgiver"), innslag.map { it.notat })
        assertEquals(listOf("B999999", "Tore Tang", "System"), innslag.map { it.utfortAvNavn })
        assertEquals(
            listOf(AktorRolle.BESLUTTER, AktorRolle.SAKSBEHANDLER, AktorRolle.SYSTEM),
            innslag.map { it.utfortAvRolle },
        )
        assertNull(innslag.last().utfortAvIdent)
    }

    @Test
    fun `sakslogg for sak uten innslag gir tom liste`() = testApplicationWithDatabase { db ->
        val sak = lagreSoknadOgSak(db.config.jdbcDatabase)

        val response = saksloggRequest(db, sak.sakId.toString())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), response.body<SaksloggResponse>().innslag)
    }

    @Test
    fun `sakslogg sjekker tilgang med saksbehandlers token og ansattes fnr`() = testApplicationWithDatabase { db ->
        val sak = lagreSoknadOgSak(db.config.jdbcDatabase)
        var tilgangsrequest: HttpRequestData? = null

        val response = saksloggRequest(db, sak.sakId.toString()) { request ->
            tilgangsrequest = request
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val request = assertNotNull(tilgangsrequest)
        assertEquals("/api/v1/komplett", request.url.encodedPath)
        assertEquals("\"$TEST_ANSATT_FNR\"", (request.body as TextContent).text)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `sakslogg gir 403 når tilgangsmaskin avviser`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        lagreSaksloggInnslag(
            database, sak.sakId, AktorRolle.SYSTEM, null,
            "Søknad mottatt fra arbeidsgiver", Instant.parse("2026-03-30T10:00:00Z"),
        )

        val response = saksloggRequest(db, sak.sakId.toString()) {
            respond(
                content = """{"title":"AVVIST_STRENGT_FORTROLIG_ADRESSE","status":403,"kanOverstyres":false}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "application/problem+json"),
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertFalse(response.bodyAsText().contains("Søknad mottatt"))
    }

    @Test
    fun `sakslogg gir 500 når tilgangsmaskin feiler`() = testApplicationWithDatabase { db ->
        val sak = lagreSoknadOgSak(db.config.jdbcDatabase)

        val response = saksloggRequest(db, sak.sakId.toString()) {
            respond("", HttpStatusCode.InternalServerError)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
    }

    @Test
    fun `sakslogg for ukjent sak gir 404 uten tilgangssjekk`() = testApplicationWithDatabase { db ->
        var tilgangsmaskinKalt = false

        val response = saksloggRequest(db, UUID.randomUUID().toString()) {
            tilgangsmaskinKalt = true
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertFalse(tilgangsmaskinKalt)
    }

    @Test
    fun `sakslogg med ugyldig sakId gir 400`() = testApplicationWithDatabase { db ->
        val response = saksloggRequest(db, "ikke-en-uuid")

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sakslogg uten token gir 401`() = testApplicationWithDatabase { db ->
        val response = saksloggRequest(db, UUID.randomUUID().toString(), token = null)

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private val fakeTokenExchanger = object : AzureAdTokenExchanger {
        override suspend fun exchange(target: String, userToken: String): TokenResponse =
            TokenResponse.Success(accessToken = "fake-token", expiresInSeconds = 3600)
    }

    private fun tilgangsmaskinClient(
        handler: MockRequestHandler = { respond("", HttpStatusCode.NoContent) },
    ) = TilgangsmaskinClient(fakeTokenExchanger, HttpClient(MockEngine(handler)))

    private suspend fun ApplicationTestBuilder.saksloggRequest(
        db: TestDatabase,
        sakId: String,
        token: String? = "valid-azure-token",
        ansattProvider: (String) -> String = { ansattJson },
        tilgangsmaskin: MockRequestHandler = { respond("", HttpStatusCode.NoContent) },
    ): HttpResponse {
        mockEntraProxyFull(
            ansattProvider = ansattProvider,
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient(tilgangsmaskin) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
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

        return client.get("/api/saksbehandling/v1/saker/$sakId/logg") {
            token?.let { bearerAuth(it) }
        }
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
                provide<TilgangsmaskinClient> { tilgangsmaskinClient() }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
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

        return client.get("/api/saksbehandling/v1/soknad/$soknadId/arena-behandling") {
            token?.let { bearerAuth(it) }
        }
    }
}


