package no.nav.ekspertbistand.skjema

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.module
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

class SkjemaTest {

    @Test
    fun `CRUD utkast`() = testApplication {

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

        val testDb = TestDatabase().clean()
        application {
            dependencies {
                provide {
                    testDb.config
                }
                provide<TokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            module()
        }

        var utkastId: String? = null

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
                        virksomhet = DTO.Virksomhet(
                            virksomhetsnummer = "314",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "donald@duck.co",
                                telefon = "12345678",
                            )
                        )
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
                        virksomhet = DTO.Virksomhet(
                            virksomhetsnummer = "1337",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "donald@duck.co",
                                telefon = "12345678",
                            )
                        )
                    )
                )
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Utkast>().also { skjema ->
                assertEquals(utkastId, skjema.id)
                assertEquals("42", skjema.opprettetAv)
                assertEquals("1337", skjema.virksomhet!!.virksomhetsnummer)
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

    @Test
    fun `send inn skjema`() = testApplication {
        val testDb = TestDatabase().clean()
        val eksisterendeSkjemaId = UUID.randomUUID()
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
            dependencies {
                provide {
                    testDb.config
                }
                provide<TokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            module()

            transaction(testDb.config.jdbcDatabase) {
                SkjemaTable.insert {
                    it[id] = eksisterendeSkjemaId
                    it[virksomhetsnummer] = "1337"
                    it[opprettetAv] = "42"

                    it[virksomhetsnummer] = ""
                    it[kontaktpersonNavn] = ""
                    it[kontaktpersonEpost] = ""
                    it[kontaktpersonTelefon] = ""
                    it[ansattFodselsnummer] = ""
                    it[ansattNavn] = ""
                    it[ekspertNavn] = ""
                    it[ekspertVirksomhet] = ""
                    it[ekspertKompetanse] = ""
                    it[ekspertProblemstilling] = ""
                    it[tiltakForTilrettelegging] = ""
                    it[bestillingKostnad] = ""
                    it[bestillingStartDato] = ""
                    it[navKontakt] = ""
                }
            }
        }


        // put skjema på id som ikke er uuid gir 400
        with(
            client.put("/api/skjema/v1/ikke-uuid") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        // put skjema på id som ikke finnes gir 409
        with(
            client.put("/api/skjema/v1/${UUID.randomUUID()}") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.Conflict, status)
        }

        // put skjema på id er allerede er sendt inn gir 409
        with(
            client.put("/api/skjema/v1/$eksisterendeSkjemaId") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.Conflict, status)
        }

        val eksisterendeUtkast = transaction(testDb.config.jdbcDatabase) {
            UtkastTable.insertReturning {
                it[virksomhetsnummer] = "1337"
                it[opprettetAv] = "T2000"
            }.single().tilUtkastDTO()
        }

        // put med ugyldig payload gir 400
        with(
            client.put("/api/skjema/v1/${eksisterendeUtkast.id}") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
                setBody(
                    """
                            {
                              "foo": "bar"
                            }
                        """.trimIndent()
                )
            }
        ) {
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        // put med gyldig payload gir 200 og skjema i retur
        with(
            client.put("/api/skjema/v1/${eksisterendeUtkast.id}") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
                setBody(
                    // TODO: angi alle felter
                    DTO.Skjema(
                        virksomhet = DTO.Virksomhet(
                            virksomhetsnummer = "1337",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "Donald@duck.co",
                                telefon = "12345678"
                            )
                        ),
                        ansatt = DTO.Ansatt(
                            fodselsnummer = "12345678910",
                            navn = "Ole Olsen"
                        ),
                        ekspert = DTO.Ekspert(
                            navn = "Egon Olsen",
                            virksomhet = "Olsenbanden AS",
                            kompetanse = "Bankran",
                            problemstilling = "Hvordan gjennomføre et bankran?" // max 5000 chars
                        ),
                        tiltak = DTO.Tiltak(
                            forTilrettelegging = "Tilrettelegging på arbeidsplassen"
                        ),
                        bestilling = DTO.Bestilling(
                            kostnad = "42",
                            startDato = "2024-10-10"
                        ),
                        nav = DTO.Nav(
                            kontakt = "Navn Navnesen"
                        ),
                    )
                )
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Skjema>().also { skjema ->
                assertEquals(eksisterendeUtkast.id, skjema.id)
                assertEquals("42", skjema.opprettetAv)
                assertEquals("1337", skjema.virksomhet.virksomhetsnummer)

                // opprettetAv skal være den som sender inn skjema, ikke den som opprettet utkast
                assertNotEquals(eksisterendeUtkast.opprettetAv, skjema.opprettetAv)
                // opprettetTidspunkt skal være nytt
                assertNotEquals(eksisterendeUtkast.opprettetTidspunkt, skjema.opprettetTidspunkt)
            }
        }

    }

    @Test
    fun `GET skjema henter mine skjema`() = testApplication {
        val testDb = TestDatabase().clean()
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
            dependencies {
                provide {
                    testDb.config
                }
                provide<TokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            module()
        }

        transaction(testDb.config.jdbcDatabase) {
            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[opprettetAv] = "42"
                it[tiltakForTilrettelegging] = "skjema for org jeg har tilgang til"

                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFodselsnummer] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[ekspertProblemstilling] = ""
                it[bestillingKostnad] = ""
                it[bestillingStartDato] = ""
                it[navKontakt] = ""
            }

            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "314"
                it[opprettetAv] = "43"
                it[tiltakForTilrettelegging] = "skjema for org jeg ikke har tilgang til"

                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFodselsnummer] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[ekspertProblemstilling] = ""
                it[bestillingKostnad] = ""
                it[bestillingStartDato] = ""
                it[navKontakt] = ""
            }
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
                assertEquals("skjema for org jeg har tilgang til", skjemas[0].tiltak.forTilrettelegging)
                assertEquals("1337", skjemas[0].virksomhet.virksomhetsnummer)
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

    @Test
    fun `get skjema gir 401 ved ugyldig token`() = testApplication {
        val testDb = TestDatabase().clean()
        val altinnTilgangerClient = AltinnTilgangerClient(object : TokenExchanger {
            override suspend fun exchange(
                target: String,
                userToken: String
            ) = fail("call to altinn tilganger not expected for unauthorized user")
        })
        application {
            dependencies {
                provide {
                    testDb.config
                }
                provide<TokenIntrospector> {
                    MockTokenIntrospector {
                        null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            module()
        }

        val response = client.get("/api/skjema/v1") {
            header(HttpHeaders.Authorization, "Bearer faketoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
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
