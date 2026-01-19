package no.nav.ekspertbistand.skjema

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient.Companion.altinn3Ressursid
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*

class SkjemaTest {

    @Test
    fun `CRUD utkast`() = testApplicationWithDatabase { testDb ->
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("1337"),
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
                provide {
                    testDb.config.jdbcDatabase
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSkjemaApiV1()
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
                            virksomhetsnavn = "Andeby AS",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "donald@duck.co",
                                telefonnummer = "12345678",
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
                            virksomhetsnavn = "foo bar AS",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "donald@duck.co",
                                telefonnummer = "12345678",
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
        with(
            client.get("/api/skjema/v1/$utkastId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.NotFound, status)
        }

    }

    @Test
    fun `send inn skjema`() = testApplicationWithDatabase { testDb ->
        val eksisterendeSkjemaId = UUID.randomUUID()
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("1337"),
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
                provide {
                    testDb.config.jdbcDatabase
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSkjemaApiV1()

            transaction(testDb.config.jdbcDatabase) {
                SkjemaTable.insert {
                    it[id] = eksisterendeSkjemaId
                    it[virksomhetsnavn] = "foo bar AS"
                    it[virksomhetsnummer] = "1337"
                    it[opprettetAv] = "42"

                    it[kontaktpersonNavn] = ""
                    it[kontaktpersonEpost] = ""
                    it[kontaktpersonTelefon] = ""
                    it[ansattFnr] = ""
                    it[ansattNavn] = ""
                    it[ekspertNavn] = ""
                    it[ekspertVirksomhet] = ""
                    it[ekspertKompetanse] = ""
                    it[behovForBistand] = ""
                    it[behovForBistandBegrunnelse] = ""
                    it[behovForBistandTilrettelegging] = ""
                    it[behovForBistandEstimertKostnad] = ""
                    it[behovForBistandTimer] = ""
                    it[behovForBistandStartdato] = CurrentDate
                    it[navKontaktPerson] = ""
                    it[status] = SkjemaStatus.innsendt.toString()
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
                it[virksomhetsnavn] = "foo bar AS"
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
                            virksomhetsnavn = "foo bar AS",
                            kontaktperson = DTO.Kontaktperson(
                                navn = "Donald Duck",
                                epost = "Donald@duck.co",
                                telefonnummer = "12345678"
                            )
                        ),
                        ansatt = DTO.Ansatt(
                            fnr = "12345678910",
                            navn = "Ole Olsen"
                        ),
                        ekspert = DTO.Ekspert(
                            navn = "Egon Olsen",
                            virksomhet = "Olsenbanden AS",
                            kompetanse = "Bankran",
                        ),
                        behovForBistand = DTO.BehovForBistand(
                            behov = "Tilrettelegging",
                            begrunnelse = "Tilrettelegging på arbeidsplassen",
                            estimertKostnad = "4200",
                            timer = "16",
                            tilrettelegging = "Spesialtilpasset kontor",
                            startdato = LocalDate.parse("2024-11-15")
                        ),
                        nav = DTO.Nav(
                            kontaktperson = "Navn Navnesen"
                        ),
                        status = SkjemaStatus.innsendt,
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

            // SkjeamInnsendt event skal være lagt til i QueuedEvents
            val events = transaction(testDb.config.jdbcDatabase) {
                QueuedEvents.selectAll().map { it.tilQueuedEvent() }
            }

            assertEquals(1, events.count())
            assertIs<EventData.SkjemaInnsendt>(events.first().eventData)
        }
    }

    @Test
    fun `GET skjema henter mine skjema`() = testApplicationWithDatabase { testDb ->
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("1337"),
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
                provide {
                    testDb.config.jdbcDatabase
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSkjemaApiV1()
        }

        transaction(testDb.config.jdbcDatabase) {
            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt skjema for org jeg har tilgang til"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "42"
                it[behovForBistandTimer] = "9"
                it[behovForBistandStartdato] = CurrentDate

                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[navKontaktPerson] = ""
                it[status] = SkjemaStatus.innsendt.toString()
            }

            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "godkjent skjema for org jeg har tilgang til"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "42"
                it[behovForBistandTimer] = "9"
                it[behovForBistandStartdato] = CurrentDate

                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[navKontaktPerson] = ""
                it[status] = SkjemaStatus.godkjent.toString()
            }

            SkjemaTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "314"
                it[virksomhetsnavn] = "andeby AS"
                it[opprettetAv] = "43"
                it[behovForBistand] = "skjema for org jeg ikke har tilgang til"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "42"
                it[behovForBistandTimer] = "9"
                it[behovForBistandStartdato] = CurrentDate

                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = ""
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[navKontaktPerson] = ""
                it[status] = SkjemaStatus.innsendt.toString()
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
                assertEquals(2, skjemas.size)
                val innsendtSkjema = skjemas.first { it.status == SkjemaStatus.innsendt }
                assertEquals("innsendt skjema for org jeg har tilgang til", innsendtSkjema.behovForBistand.behov)
                assertEquals("1337", innsendtSkjema.virksomhet.virksomhetsnummer)
                assertEquals("42", innsendtSkjema.opprettetAv)

                val godkjentSkjema = skjemas.first { it.status == SkjemaStatus.godkjent }
                assertEquals("godkjent skjema for org jeg har tilgang til", godkjentSkjema.behovForBistand.behov)
                assertEquals("1337", godkjentSkjema.virksomhet.virksomhetsnummer)
                assertEquals("42", godkjentSkjema.opprettetAv)

                skjemaId = innsendtSkjema.id
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
    fun `get skjema gir 401 ved ugyldig token`() = testApplicationWithDatabase { testDb ->
        val altinnTilgangerClient = AltinnTilgangerClient(
            object : TokenXTokenExchanger {
                override suspend fun exchange(
                    target: String,
                    userToken: String
                ) = fail("call to altinn tilganger not expected for unauthorized user")
            },
            defaultHttpClient = createClient { }
        )
        application {
            dependencies {
                provide {
                    testDb.config.jdbcDatabase
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSkjemaApiV1()
        }

        val response = client.get("/api/skjema/v1") {
            header(HttpHeaders.Authorization, "Bearer faketoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `utkast uten orgnr returneres kun til person som har opprettet utkastet`() =
        testApplicationWithDatabase { testDb ->
            mockAltinnTilganger(
                AltinnTilgangerClientResponse(
                    isError = false,
                    hierarki = emptyList(),
                    orgNrTilTilganger = mapOf(
                        "1337" to setOf(altinn3Ressursid)
                    ),
                    tilgangTilOrgNr = mapOf(
                        altinn3Ressursid to setOf("1337"),
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
                    provide {
                        testDb.config.jdbcDatabase
                    }
                    provide<TokenXTokenIntrospector> {
                        MockTokenIntrospector {
                            if (it == "faketoken") {
                                mockIntrospectionResponse.withPid("42")
                            } else if (it == "faketoken2") {
                                mockIntrospectionResponse.withPid("43")
                            } else {
                                null
                            }
                        }
                    }
                    provide {
                        altinnTilgangerClient
                    }
                }

                configureTokenXAuth()
                configureServer()
                configureSkjemaApiV1()
            }

            transaction(testDb.config.jdbcDatabase)
            {
                UtkastTable.insert {
                    it[id] = UUID.randomUUID()
                    it[virksomhetsnummer] = null
                    it[virksomhetsnavn] = null
                    it[opprettetAv] = "42"
                    it[behovForBistand] = "utkast som ikke har virksomhet"
                    it[behovForBistandTilrettelegging] = ""
                    it[behovForBistandBegrunnelse] = ""
                    it[behovForBistandEstimertKostnad] = "42"
                    it[behovForBistandTimer] = "9"
                    it[behovForBistandStartdato] = CurrentDate

                    it[kontaktpersonNavn] = null
                    it[kontaktpersonEpost] = null
                    it[kontaktpersonTelefon] = null
                    it[ansattFnr] = ""
                    it[ansattNavn] = ""
                    it[ekspertNavn] = ""
                    it[ekspertVirksomhet] = ""
                    it[ekspertKompetanse] = ""
                    it[navKontaktPerson] = ""
                }

                UtkastTable.insert {
                    it[id] = UUID.randomUUID()
                    it[virksomhetsnummer] = "1337"
                    it[virksomhetsnavn] = "andeby AS"
                    it[opprettetAv] = "44"
                    it[behovForBistand] = "utkast som har virksomhet "
                    it[behovForBistandTilrettelegging] = ""
                    it[behovForBistandBegrunnelse] = ""
                    it[behovForBistandEstimertKostnad] = ""
                    it[behovForBistandTimer] = "9"
                    it[behovForBistandStartdato] = CurrentDate

                    it[kontaktpersonNavn] = ""
                    it[kontaktpersonEpost] = ""
                    it[kontaktpersonTelefon] = ""
                    it[ansattFnr] = ""
                    it[ansattNavn] = ""
                    it[ekspertNavn] = ""
                    it[ekspertVirksomhet] = ""
                    it[ekspertKompetanse] = ""
                    it[navKontaktPerson] = ""
                }
            }

            with(
                client.get("/api/skjema/v1?status=${SkjemaStatusQueryParam.utkast.name}")
                {
                    bearerAuth("faketoken")
                }
            )
            {
                assertEquals(HttpStatusCode.OK, status)
                body<List<DTO.Utkast>>().also { skjemas ->
                    assertEquals(2, skjemas.size)
                    val skjemaUtenVirksomhet = skjemas.find { it.virksomhet == null }
                    val skjemaMedVirksomhet = skjemas.find { it.virksomhet != null }
                    assertEquals("42", skjemaUtenVirksomhet!!.opprettetAv)
                    assertEquals("1337", skjemaMedVirksomhet!!.virksomhet!!.virksomhetsnummer)
                }
            }

            with(
                client.get("/api/skjema/v1?status=${SkjemaStatusQueryParam.utkast.name}")
                {
                    bearerAuth("faketoken2")
                }
            )
            {
                assertEquals(HttpStatusCode.OK, status)
                body<List<DTO.Utkast>>().also { skjemas ->
                    assertEquals(1, skjemas.size)
                    val skjemaMedVirksomhet = skjemas.find { it.virksomhet != null }
                    assertEquals("1337", skjemaMedVirksomhet!!.virksomhet!!.virksomhetsnummer)
                }
            }
        }

}
