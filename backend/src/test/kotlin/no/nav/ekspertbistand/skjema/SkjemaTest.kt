package no.nav.ekspertbistand.skjema

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.TokenExchanger
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.infrastruktur.mockTokenXAuthentication
import no.nav.ekspertbistand.infrastruktur.mockTokenXPrincipal
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SkjemaTest {

    @Test
    fun `CRUD utkast`() = runTest {
        // application setup
        val dbConfig = TestDatabase.initialize()
        testApplication {
            mockAltinnTilganger(
                AltinnTilgangerClientResponse(
                    isError = false,
                    hierarki = emptyList(),
                    orgNrTilTilganger = mapOf(
                        "1337" to setOf("5384:1", "nav_ekspertbistand_soknad")
                    ),
                    tilgangTilOrgNr = mapOf(
                        "5384:1" to setOf("1337"),
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
                configureServer()
                mockTokenXAuthentication(
                    mapOf(
                        "faketoken" to mockTokenXPrincipal.copy(pid = "42")
                    )
                )

                skjemaApiV1(
                    dbConfig,
                    altinnTilgangerClient
                )
            }

            var utkastId : String? = null

            // opprett utkast
            with(
                client.post("/api/skjema/v1") {
                    bearerAuth("faketoken")
                    contentType(ContentType.Application.Json)
                }
            ) {
                assertEquals(HttpStatusCode.Created, status)
                body<DTO.Utkast>().also { skjema ->
                    assert(skjema.id!!.isNotEmpty())
                    assertEquals("42", skjema.opprettetAv)

                    utkastId = skjema.id
                }
            }

            // hent utkast
            with(
                client.get("/api/skjema/v1/$utkastId") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.OK, status)
                body<DTO.Utkast>().also { skjema ->
                    assertEquals(utkastId, skjema.id)
                    assertEquals("42", skjema.opprettetAv)
                }
            }

            // oppdater utkast, orgnr uten tilgang
            with(
                client.patch("/api/skjema/v1/$utkastId") {
                    bearerAuth("faketoken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        DTO.Utkast(
                            organisasjonsnummer = "80085"
                        )
                    )
                }
            ) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }

            // oppdater utkast, orgnr med tilgang
            with(
                client.patch("/api/skjema/v1/$utkastId") {
                    bearerAuth("faketoken")
                    contentType(ContentType.Application.Json)
                    setBody(
                        DTO.Utkast(
                            tittel = "ny tittel",
                            beskrivelse = "ny beskrivelse",
                            organisasjonsnummer = "1337"
                        )
                    )
                }
            ) {
                assertEquals(HttpStatusCode.OK, status)
                body<DTO.Utkast>().also { skjema ->
                    assertEquals(utkastId, skjema.id)
                    assertEquals("42", skjema.opprettetAv)
                    assertEquals("ny tittel", skjema.tittel)
                    assertEquals("1337", skjema.organisasjonsnummer)
                }
            }

            // delete utkast
            with(
                client.delete("/api/skjema/v1/$utkastId") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.NoContent, status)
            }
        }
    }

    @Test
    fun `GET skjema henter mine skjema`() = runTest {
        val dbConfig = TestDatabase.initialize()
        suspendTransaction(dbConfig.database) {
            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[tittel] = "skjema1"
                it[beskrivelse] = "skjema for org jeg har tilgang til"
                it[organisasjonsnummer] = "1337"
                it[opprettetAv] = "42"
            }

            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[tittel] = "skjema2"
                it[beskrivelse] = "skjema for org jeg ikke har tilgang til"
                it[organisasjonsnummer] = "314"
                it[opprettetAv] = "43"
            }
        }

        testApplication {
            mockAltinnTilganger(
                AltinnTilgangerClientResponse(
                    isError = false,
                    hierarki = emptyList(),
                    orgNrTilTilganger = mapOf(
                        "1337" to setOf("5384:1", "nav_ekspertbistand_soknad")
                    ),
                    tilgangTilOrgNr = mapOf(
                        "5384:1" to setOf("1337"),
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
                configureServer()
                mockTokenXAuthentication(
                    mapOf(
                        "faketoken" to mockTokenXPrincipal.copy(pid = "42")
                    )
                )

                skjemaApiV1(
                    dbConfig,
                    altinnTilgangerClient
                )
            }

            var skjemaId: String? = null
            with(
                client.get("/api/skjema/v1") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.OK, status)
                body<List<DTO.Skjema>>().also { skjemas ->
                    assertEquals(1, skjemas.size)
                    assertEquals("skjema1", skjemas[0].tittel)
                    assertEquals("skjema for org jeg har tilgang til", skjemas[0].beskrivelse)
                    assertEquals("1337", skjemas[0].organisasjonsnummer)
                    assertEquals("42", skjemas[0].opprettetAv)

                    skjemaId = skjemas[0].id
                }
            }

            with(
                client.get("/api/skjema/v1/$skjemaId") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.OK, status)
                body<DTO.Skjema>().also { skjema ->
                    assertEquals(skjemaId, skjema.id)
                }
            }

            with(
                client.get("/api/skjema/v1/${UUID.randomUUID()}") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.NotFound, status)
            }

            with(
                client.get("/api/skjema/v1/ikkeeksisterendeid") {
                    bearerAuth("faketoken")
                }
            ) {
                assertEquals(HttpStatusCode.BadRequest, status)
            }

        }
    }

    @Test
    fun `get skjema gir 401 ved ugyldig token`() = runTest {
        val dbConfig = TestDatabase.initialize()
        val altinnTilgangerClient = AltinnTilgangerClient(object : TokenExchanger {
            override suspend fun exchange(
                target: String,
                userToken: String
            ) = fail("call to altinn tilganger not expected for unauthorized user")
        })
        testApplication {
            application {
                mockTokenXAuthentication(mapOf()) // simuler ugyldig token

                skjemaApiV1(dbConfig, altinnTilgangerClient)
            }

            val response = client.get("/api/skjema/v1") {
                header(HttpHeaders.Authorization, "Bearer faketoken")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}

private fun ApplicationTestBuilder.mockAltinnTilganger(
    tilgangerResponse: AltinnTilgangerClientResponse
) {
    externalServices {
        hosts(AltinnTilgangerClient.ingress) {
            install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }

            routing {
                post("altinn-tilganger") {
                    call.respond(tilgangerResponse)
                }
            }
        }
    }
}

