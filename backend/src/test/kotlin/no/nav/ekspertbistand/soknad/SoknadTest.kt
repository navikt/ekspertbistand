package no.nav.ekspertbistand.soknad

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.mocks.mockEreg
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*

class SoknadTest {

    @Test
    fun `CRUD utkast`() = testApplicationWithDatabase { testDb ->
        mockEreg {
            """
            {
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo"
                  }
                ]
              }
            }
            """.trimIndent()
        }
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
        val eregClient = EregClient(defaultHttpClient = client)
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
                provide<EregService> { EregService(eregClient) }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSoknadApiV1()
        }

        var utkastId: String? = null

        // opprett utkast
        with(
            client.post("/api/soknad/v1") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.Created, status)
            body<DTO.Utkast>().also { utkast ->
                assert(utkast.id!!.isNotEmpty())
                assertEquals("42", utkast.opprettetAv)

                utkastId = utkast.id
            }
        }

        // hent utkast
        with(
            client.get("/api/soknad/v1/$utkastId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Utkast>().also { utkast ->
                assertEquals(utkastId, utkast.id)
                assertEquals("42", utkast.opprettetAv)
            }
        }

        // oppdater utkast, orgnr uten tilgang
        with(
            client.patch("/api/soknad/v1/$utkastId") {
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
            client.patch("/api/soknad/v1/$utkastId") {
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
            body<DTO.Utkast>().also { utkast ->
                assertEquals(utkastId, utkast.id)
                assertEquals("42", utkast.opprettetAv)
                assertEquals("1337", utkast.virksomhet!!.virksomhetsnummer)
            }
        }

        // delete utkast
        with(
            client.delete("/api/soknad/v1/$utkastId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.NoContent, status)
        }
        with(
            client.get("/api/soknad/v1/$utkastId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.NotFound, status)
        }

    }

    @Test
    fun `send inn soknad`() = testApplicationWithDatabase { testDb ->
        val eksisterendeSoknadId = UUID.randomUUID()
        mockEreg {
            """
            {
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo"
                  }
                ]
              }
            }
            """.trimIndent()
        }
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
        val eregClient = EregClient(defaultHttpClient = client)

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
                provide<EregService> { EregService(eregClient) }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSoknadApiV1()

            transaction(testDb.config.jdbcDatabase) {
                SoknadTable.insert {
                    it[id] = eksisterendeSoknadId
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
                    it[beliggenhetsadresse] = ""
                    it[status] = SoknadStatus.innsendt.toString()
                }
            }
        }


        // put soknad på id som ikke er uuid gir 400
        with(
            client.put("/api/soknad/v1/ikke-uuid") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.BadRequest, status)
        }

        // put soknad på id som ikke finnes gir 409
        with(
            client.put("/api/soknad/v1/${UUID.randomUUID()}") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
            }
        ) {
            assertEquals(HttpStatusCode.Conflict, status)
        }

        // put soknad på id er allerede er sendt inn gir 409
        with(
            client.put("/api/soknad/v1/$eksisterendeSoknadId") {
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
            client.put("/api/soknad/v1/${eksisterendeUtkast.id}") {
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

        // put med gyldig payload gir 200 og soknad i retur
        with(
            client.put("/api/soknad/v1/${eksisterendeUtkast.id}") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
                setBody(
                    // TODO: angi alle felter
                    DTO.Soknad(
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
                        status = SoknadStatus.innsendt,
                    )
                )
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Soknad>().also { soknad ->
                assertEquals(eksisterendeUtkast.id, soknad.id)
                assertEquals("42", soknad.opprettetAv)
                assertEquals("1337", soknad.virksomhet.virksomhetsnummer)
                assertEquals("Testveien 1, 0557 Oslo", soknad.virksomhet.beliggenhetsadresse)

                // opprettetAv skal være den som sender inn søknad, ikke den som opprettet utkast
                assertNotEquals(eksisterendeUtkast.opprettetAv, soknad.opprettetAv)
                // opprettetTidspunkt skal være nytt
                assertNotEquals(eksisterendeUtkast.opprettetTidspunkt, soknad.opprettetTidspunkt)
            }

            // SoknadInnsendt event skal være lagt til i QueuedEvents
            val events = transaction(testDb.config.jdbcDatabase) {
                QueuedEvents.selectAll().map { it.tilQueuedEvent() }
            }

            assertEquals(1, events.count())
            assertIs<EventData.SoknadInnsendt>(events.first().eventData)
        }
    }

    @Test
    fun `GET soknad henter mine søknader`() = testApplicationWithDatabase { testDb ->
        mockEreg {
            """
            {
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo"
                  }
                ]
              }
            }
            """.trimIndent()
        }
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
        val eregClient = EregClient(defaultHttpClient = client)

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
                provide<EregService> { EregService(eregClient) }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSoknadApiV1()
        }

        transaction(testDb.config.jdbcDatabase) {
            SoknadTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "innsendt soknad for org jeg har tilgang til"
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
                it[beliggenhetsadresse] = ""
                it[status] = SoknadStatus.innsendt.toString()
            }

            SoknadTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "1337"
                it[virksomhetsnavn] = " foo bar AS"
                it[opprettetAv] = "42"
                it[behovForBistand] = "godkjent soknad for org jeg har tilgang til"
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
                it[beliggenhetsadresse] = ""
                it[status] = SoknadStatus.godkjent.toString()
            }

            SoknadTable.insert {
                it[id] = UUID.randomUUID()
                it[virksomhetsnummer] = "314"
                it[virksomhetsnavn] = "andeby AS"
                it[opprettetAv] = "43"
                it[behovForBistand] = "soknad for org jeg ikke har tilgang til"
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
                it[beliggenhetsadresse] = ""
                it[status] = SoknadStatus.innsendt.toString()
            }
        }

        var soknadId: String? = null
        with(
            client.get("/api/soknad/v1") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<List<DTO.Soknad>>().also { soknader ->
                assertEquals(2, soknader.size)
                val innsendtSoknad = soknader.first { it.status == SoknadStatus.innsendt }
                assertEquals("innsendt soknad for org jeg har tilgang til", innsendtSoknad.behovForBistand.behov)
                assertEquals("1337", innsendtSoknad.virksomhet.virksomhetsnummer)
                assertEquals("42", innsendtSoknad.opprettetAv)

                val godkjentSoknad = soknader.first { it.status == SoknadStatus.godkjent }
                assertEquals("godkjent soknad for org jeg har tilgang til", godkjentSoknad.behovForBistand.behov)
                assertEquals("1337", godkjentSoknad.virksomhet.virksomhetsnummer)
                assertEquals("42", godkjentSoknad.opprettetAv)

                soknadId = innsendtSoknad.id
            }
        }

        with(
            client.get("/api/soknad/v1/$soknadId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Soknad>().also { soknad ->
                assertEquals(soknadId, soknad.id)
            }
        }

        with(
            client.get("/api/soknad/v1/${UUID.randomUUID()}") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.NotFound, status)
        }

        with(
            client.get("/api/soknad/v1/ikkeeksisterendeid") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.BadRequest, status)
        }
    }

    @Test
    fun `get soknad gir 401 ved ugyldig token`() = testApplicationWithDatabase { testDb ->
        val altinnTilgangerClient = AltinnTilgangerClient(
            object : TokenXTokenExchanger {
                override suspend fun exchange(
                    target: String,
                    userToken: String
                ) = fail("call to altinn tilganger not expected for unauthorized user")
            },
            defaultHttpClient = createClient { }
        )
        val eregClient = EregClient(defaultHttpClient = client)
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
                provide<EregService> { EregService(eregClient) }
                provide {
                    altinnTilgangerClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureSoknadApiV1()
        }

        val response = client.get("/api/soknad/v1") {
            header(HttpHeaders.Authorization, "Bearer faketoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `utkast uten orgnr returneres kun til person som har opprettet utkastet`() =
        testApplicationWithDatabase { testDb ->
            mockEreg {
                """
            {
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo"
                  }
                ]
              }
            }
            """.trimIndent()
            }
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
            val eregClient = EregClient(defaultHttpClient = client)

            application {
                dependencies {
                    provide {
                        testDb.config.jdbcDatabase
                    }
                    provide<TokenXTokenIntrospector> {
                        MockTokenIntrospector {
                            when (it) {
                                "faketoken" -> {
                                    mockIntrospectionResponse.withPid("42")
                                }

                                "faketoken2" -> {
                                    mockIntrospectionResponse.withPid("43")
                                }

                                else -> {
                                    null
                                }
                            }
                        }
                    }
                    provide {
                        altinnTilgangerClient
                    }
                    provide<EregService> { EregService(eregClient) }
                }

                configureTokenXAuth()
                configureServer()
                configureSoknadApiV1()
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
                client.get("/api/soknad/v1?status=${SoknadStatusQueryParam.utkast.name}")
                {
                    bearerAuth("faketoken")
                }
            )
            {
                assertEquals(HttpStatusCode.OK, status)
                body<List<DTO.Utkast>>().also { soknader ->
                    assertEquals(2, soknader.size)
                    val soknadUtenVirksomhet = soknader.find { it.virksomhet == null }
                    val soknadMedVirksomhet = soknader.find { it.virksomhet != null }
                    assertEquals("42", soknadUtenVirksomhet!!.opprettetAv)
                    assertEquals("1337", soknadMedVirksomhet!!.virksomhet!!.virksomhetsnummer)
                }
            }

            with(
                client.get("/api/soknad/v1?status=${SoknadStatusQueryParam.utkast.name}")
                {
                    bearerAuth("faketoken2")
                }
            )
            {
                assertEquals(HttpStatusCode.OK, status)
                body<List<DTO.Utkast>>().also { soknader ->
                    assertEquals(1, soknader.size)
                    val soknadMedVirksomhet = soknader.find { it.virksomhet != null }
                    assertEquals("1337", soknadMedVirksomhet!!.virksomhet!!.virksomhetsnummer)
                }
            }
        }

}
