package no.nav.ekspertbistand.soknad

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
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
import no.nav.ekspertbistand.saksbehandling.MaksSats
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
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
                hierarki = listOf(
                    AltinnTilgangerClientResponse.AltinnTilgang(
                        erSlettet = false,
                        orgnr = "910825226",
                        organisasjonsform = "BEDR",
                        navn = "Testbedrift AS",
                        underenheter = emptyList(),
                        altinn3Tilganger = setOf(altinn3Ressursid),
                        altinn2Tilganger = emptySet(),
                    )
                ),
                orgNrTilTilganger = mapOf(
                    "910825226" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("910825226"),
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
                            "faketoken" -> mockIntrospectionResponse.withPid("42")
                            "lowtoken" -> mockIntrospectionResponse.withAcr("idporten-loa-substantial")
                            else -> null
                        }
                    }
                }
                provide<EregService> { EregService(eregClient) }
                provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                provide {
                    altinnTilgangerClient
                }
            }

            configureAuthentication()
            configureServer()
            configureSoknadApiV1()
        }

        val maksSatsUrl = "/api/soknad/v1/maks-sats"
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("$maksSatsUrl?startdato=2026-01-01").status,
        )
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.get("$maksSatsUrl?startdato=2026-01-01") { bearerAuth("lowtoken") }.status,
        )
        for (dato in listOf("2026-01-01", "2027-01-01")) {
            val response = client.get("$maksSatsUrl?startdato=$dato") { bearerAuth("faketoken") }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(MaksSats(LocalDate(2026, 1, 1), 24_900), response.body<MaksSats>())
        }
        for (dato in listOf(null, "abc", "2026-13-01", "2025-12-31")) {
            val query = dato?.let { "?startdato=$it" } ?: ""
            assertEquals(
                HttpStatusCode.BadRequest,
                client.get("$maksSatsUrl$query") { bearerAuth("faketoken") }.status,
            )
        }

        var utkastId: String? = null
        val deltakerFnr = gyldigFnr("010190123")

        // opprett utkast, krever bekreftet deltaker
        with(
            client.post("/api/soknad/v1") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
                setBody(ValiderDeltakerRequest(deltakerFnr, "910825226"))
            }
        ) {
            assertEquals(HttpStatusCode.Created, status)
            body<DTO.Utkast>().also { utkast ->
                assert(utkast.id!!.isNotEmpty())
                assertEquals("42", utkast.opprettetAv)
                assertTrue(utkast.deltakerBekreftet)
                assertEquals(deltakerFnr, utkast.ansatt?.fnr)
                assertEquals("910825226", utkast.virksomhet?.virksomhetsnummer)
                assertEquals("Testbedrift AS", utkast.virksomhet?.virksomhetsnavn)

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

        // oppdater utkast, virksomhetsnummer settes bare av valider-deltaker
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
                assertEquals("910825226", utkast.virksomhet?.virksomhetsnummer, "PATCH endrer ikke virksomhetsnummer")
                assertEquals("Testbedrift AS", utkast.virksomhet?.virksomhetsnavn)
                assertEquals("Donald Duck", utkast.virksomhet?.kontaktperson?.navn)
            }
        }

        // delete utkast
        with(
            client.delete("/api/soknad/v1/$utkastId") {
                bearerAuth("faketoken")
            }
        ) {
            assertEquals(HttpStatusCode.NoContent, status)
            assertEquals("", bodyAsText())
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
    fun `send inn soknad kopierer det lagrede utkastet`() = testApplicationWithDatabase { testDb ->
        val medAdresse = "910825226"
        val utenAdresse = "974760673"
        val eksisterendeSoknadId = UUID.randomUUID()
        val startdato = Clock.System.now().toLocalDateTime(TimeZone.of("Europe/Oslo")).date.plus(7, DateTimeUnit.DAY)

        mockEreg { orgnr ->
            if (orgnr != medAdresse) null
            else """
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
        // innsending sjekker bare hvem som opprettet utkastet, ikke Altinn-tilgangen
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = emptyMap(),
                tilgangTilOrgNr = emptyMap(),
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
                provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                provide {
                    altinnTilgangerClient
                }
            }

            configureAuthentication()
            configureServer()
            configureSoknadApiV1()
        }

        fun lagreUtkast(virksomhetsnummer: String, komplett: Boolean = true): UUID =
            transaction(testDb.config.jdbcDatabase) {
                UtkastTable.insertReturning {
                    it[UtkastTable.virksomhetsnummer] = virksomhetsnummer
                    it[virksomhetsnavn] = "Navn fra Altinn AS"
                    it[ansattFnr] = "12345678910"
                    it[ansattNavn] = "Mona Moonlight"
                    it[ansattBekreftetTidspunkt] = Clock.System.now()
                    it[opprettetAv] = "42"
                    if (komplett) {
                        it[kontaktpersonNavn] = "Donald Duck"
                        it[kontaktpersonEpost] = "donald@duck.co"
                        it[kontaktpersonTelefon] = "12345678"
                        it[ekspertNavn] = "Egon Olsen"
                        it[ekspertVirksomhet] = "Olsenbanden AS (987654321)"
                        it[ekspertVirksomhetNavn] = "Olsenbanden AS"
                        it[ekspertVirksomhetOrgnr] = "987654321"
                        it[ekspertGodkjentUtdanningEllerAutorisasjon] = encodeStringList(listOf("psykolog"))
                        it[ekspertRelevantKompetanse] = encodeStringList(listOf("ergonomi"))
                        it[behovForBistand] = "Tilrettelegging"
                        it[behovForBistandBegrunnelse] = "Tilrettelegging på arbeidsplassen"
                        it[behovForBistandEstimertKostnad] = "24900"
                        it[behovForBistandTimer] = "16"
                        it[behovForBistandTilrettelegging] = "Spesialtilpasset kontor"
                        it[behovForBistandStartdato] = startdato
                        it[navKontaktPerson] = "Navn Navnesen"
                    }
                }.single()[UtkastTable.id].value
            }

        suspend fun sendInn(id: Any) = client.put("/api/soknad/v1/$id") { bearerAuth("faketoken") }

        fun utkastFinnes(id: UUID) = transaction(testDb.config.jdbcDatabase) {
            !UtkastTable.selectAll().where { UtkastTable.id eq id }.empty()
        }

        fun innsendteEventer() = transaction(testDb.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }.filterIsInstance<EventData.SoknadInnsendt>()
        }

        transaction(testDb.config.jdbcDatabase) {
            SoknadTable.insert {
                it[id] = eksisterendeSoknadId
                it[virksomhetsnavn] = "foo bar AS"
                it[virksomhetsnummer] = medAdresse
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

        assertEquals(HttpStatusCode.BadRequest, sendInn("ikke-uuid").status)
        assertEquals(HttpStatusCode.Conflict, sendInn(UUID.randomUUID()).status, "utkastet finnes ikke")
        assertEquals(HttpStatusCode.Conflict, sendInn(eksisterendeSoknadId).status, "allerede sendt inn")

        // ufullstendig utkast gir 400 med feltsti, og utkastet beholdes
        val ufullstendig = lagreUtkast(medAdresse, komplett = false)
        with(sendInn(ufullstendig)) {
            assertEquals(HttpStatusCode.BadRequest, status)
            val feil = body<Map<String, String>>()
            assertEquals(UGYLDIG_INPUT, feil["kode"])
            assertEquals("virksomhet.kontaktperson.navn", feil["felt"])
        }
        assertTrue(utkastFinnes(ufullstendig))
        assertTrue(innsendteEventer().isEmpty())

        // komplett utkast sendes inn, og body ignoreres
        val komplett = lagreUtkast(medAdresse)
        with(
            client.put("/api/soknad/v1/$komplett") {
                bearerAuth("faketoken")
                contentType(ContentType.Application.Json)
                setBody("""{ "virksomhet": { "virksomhetsnavn": "Fra body AS" }, "ansatt": { "navn": "Fra body" } }""")
            }
        ) {
            assertEquals(HttpStatusCode.OK, status)
            body<DTO.Soknad>().also { soknad ->
                assertEquals(komplett.toString(), soknad.id)
                assertEquals("42", soknad.opprettetAv)
                assertEquals(SoknadStatus.innsendt, soknad.status)
                assertEquals(medAdresse, soknad.virksomhet.virksomhetsnummer)
                assertEquals("Navn fra Altinn AS", soknad.virksomhet.virksomhetsnavn)
                assertEquals("Donald Duck", soknad.virksomhet.kontaktperson.navn)
                assertEquals("Testveien 1, 0557 Oslo", soknad.virksomhet.beliggenhetsadresse)
                assertEquals("12345678910", soknad.ansatt.fnr)
                assertEquals("Mona Moonlight", soknad.ansatt.navn)
                assertEquals("Egon Olsen", soknad.ekspert.navn)
                assertEquals("Olsenbanden AS (987654321)", soknad.ekspert.virksomhet)
                assertEquals("Olsenbanden AS", soknad.ekspert.virksomhetNavn)
                assertEquals("987654321", soknad.ekspert.virksomhetOrgnr)
                assertEquals(listOf("psykolog"), soknad.ekspert.godkjentUtdanningEllerAutorisasjon)
                assertEquals(listOf("ergonomi"), soknad.ekspert.relevantKompetanse)
                assertEquals("24900", soknad.behovForBistand.estimertKostnad)
                assertEquals(startdato, soknad.behovForBistand.startdato)
                assertEquals("Navn Navnesen", soknad.nav.kontaktperson)
            }
        }
        assertFalse(utkastFinnes(komplett))
        innsendteEventer().single().also { event ->
            assertEquals(komplett.toString(), event.soknad.id)
            assertEquals("Navn fra Altinn AS", event.soknad.virksomhet.virksomhetsnavn)
        }

        // samme utkast kan ikke sendes inn to ganger
        assertEquals(HttpStatusCode.Conflict, sendInn(komplett).status)
        assertEquals(1, innsendteEventer().size)

        // mangler adressen i Ereg, lagres den som null
        val komplettUtenAdresse = lagreUtkast(utenAdresse)
        with(sendInn(komplettUtenAdresse)) {
            assertEquals(HttpStatusCode.OK, status)
            assertNull(body<DTO.Soknad>().virksomhet.beliggenhetsadresse)
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
                provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                provide {
                    altinnTilgangerClient
                }
            }

            configureAuthentication()
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
                provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                provide {
                    altinnTilgangerClient
                }
            }

            configureAuthentication()
            configureServer()
            configureSoknadApiV1()
        }

        val response = client.get("/api/soknad/v1") {
            header(HttpHeaders.Authorization, "Bearer faketoken")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `utkast returneres kun til person som har opprettet utkastet`() =
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
                    provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                }

                configureAuthentication()
                configureServer()
                configureSoknadApiV1()
            }

            transaction(testDb.config.jdbcDatabase)
            {
                UtkastTable.insert {
                    it[id] = UUID.randomUUID()
                    it[virksomhetsnummer] = "1337"
                    it[virksomhetsnavn] = "eget AS"
                    it[opprettetAv] = "42"
                    it[behovForBistand] = "eget utkast"
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
                    it[ansattBekreftetTidspunkt] = Clock.System.now()
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
                    it[ansattBekreftetTidspunkt] = Clock.System.now()
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
                    // utkastet til pid 44 for 1337 vises ikke, selv om innlogget har tilgang til 1337
                    assertEquals(1, soknader.size)
                    assertEquals("42", soknader.single().opprettetAv)
                    assertEquals("eget AS", soknader.single().virksomhet?.virksomhetsnavn)
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
                assertTrue(body<List<DTO.Utkast>>().isEmpty())
            }
        }

    @Test
    fun `kan ikke se, endre, slette eller sende inn andres utkast, heller ikke i egen virksomhet`() =
        testApplicationWithDatabase { testDb ->
            mockEreg { """{ "organisasjonDetaljer": { "forretningsadresser": [] } }""" }
            // innlogget (pid 43) har tilgang til 1337, men utkastene er opprettet av pid 42
            mockAltinnTilganger(
                AltinnTilgangerClientResponse(
                    isError = false,
                    hierarki = emptyList(),
                    orgNrTilTilganger = mapOf("1337" to setOf(altinn3Ressursid)),
                    tilgangTilOrgNr = mapOf(altinn3Ressursid to setOf("1337")),
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
                    provide { testDb.config.jdbcDatabase }
                    provide<TokenXTokenIntrospector> {
                        MockTokenIntrospector {
                            if (it == "angriper") mockIntrospectionResponse.withPid("43") else null
                        }
                    }
                    provide { altinnTilgangerClient }
                    provide<EregService> { EregService(eregClient) }
                    provide<DeltakerOppslag> { bekreftendeDeltakerOppslag }
                }
                configureAuthentication()
                configureServer()
                configureSoknadApiV1()
            }

            val utkastId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                UtkastTable.insert {
                    it[id] = utkastId
                    it[virksomhetsnummer] = "1337"
                    it[virksomhetsnavn] = "Offer AS"
                    it[ansattFnr] = "12345678910"
                    it[ansattNavn] = "Ole Olsen"
                    it[ansattBekreftetTidspunkt] = Clock.System.now()
                    it[opprettetAv] = "42"
                }
            }

            val egenVirksomhet = DTO.Virksomhet(
                virksomhetsnummer = "1337",
                virksomhetsnavn = "Angriper AS",
                kontaktperson = DTO.Kontaktperson(
                    navn = "Donald Duck",
                    epost = "donald@duck.co",
                    telefonnummer = "12345678",
                ),
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client.get("/api/soknad/v1/$utkastId") { bearerAuth("angriper") }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client.patch("/api/soknad/v1/$utkastId") {
                    bearerAuth("angriper")
                    contentType(ContentType.Application.Json)
                    setBody(DTO.Utkast(virksomhet = egenVirksomhet))
                }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client.put("/api/soknad/v1/$utkastId") { bearerAuth("angriper") }.status,
            )
            assertEquals(
                HttpStatusCode.Forbidden,
                client.delete("/api/soknad/v1/$utkastId") { bearerAuth("angriper") }.status,
            )

            transaction(testDb.config.jdbcDatabase) {
                val rader = UtkastTable.selectAll().associateBy { it[UtkastTable.id].value }
                assertEquals("Offer AS", rader[utkastId]!![UtkastTable.virksomhetsnavn])
                assertTrue(SoknadTable.selectAll().empty())
            }
        }

}
