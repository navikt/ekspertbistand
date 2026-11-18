package no.nav.ekspertbistand.soknad

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ValiderDeltakerTest {

    private val innloggetFnr = gyldigFnr("010170123")
    private val annenBrukerFnr = gyldigFnr("010170456")
    private val deltakerFnr = gyldigFnr("010190123")
    private val orgnr = "910456900"
    private val annetOrgnr = "910456901"
    private val orgnrUtenTilgang = "910456999"
    /** Har tilgang, men finnes ikke i hierarkiet, så Altinn gir ikke noe navn. */
    private val orgnrUtenNavn = "910456902"
    private val startFnr = gyldigFnr("070770123")
    private lateinit var db: Database

    @Test
    fun `treff gir 200 med navn og bekreftelse`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        with(validerDeltaker(utkastId, deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.OK, status)
            val utkast = body<DTO.Utkast>()
            assertTrue(utkast.deltakerBekreftet)
            assertEquals(DTO.Ansatt(deltakerFnr, "Mona Moonlight"), utkast.ansatt)
            assertEquals(orgnr, utkast.virksomhet?.virksomhetsnummer)
            assertEquals("Testbedrift AS", utkast.virksomhet?.virksomhetsnavn)
        }
        assertEquals(1, oppslag.antallArbeidsforholdOppslag)
        assertEquals(1, oppslag.antallNavneoppslag)

        with(hentUtkast(utkastId)) {
            assertTrue(deltakerBekreftet)
            assertEquals("Mona Moonlight", ansatt?.navn)
        }
    }

    @Test
    fun `ingen arbeidsforhold gir 422 og endrer ikke utkastet`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()
        assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, deltakerFnr, orgnr).status)

        oppslag.arbeidsforhold = { _, _ -> false }
        val annenDeltaker = gyldigFnr("020290123")
        with(validerDeltaker(utkastId, annenDeltaker, orgnr)) {
            assertEquals(HttpStatusCode.UnprocessableEntity, status)
            assertEquals(INGEN_ARBEIDSFORHOLD, kode())
        }
        assertEquals(1, oppslag.antallNavneoppslag)

        with(hentUtkast(utkastId)) {
            assertEquals(DTO.Ansatt(deltakerFnr, "Mona Moonlight"), ansatt)
            assertEquals(orgnr, virksomhet?.virksomhetsnummer)
        }
    }

    @Test
    fun `sjette ulike fodselsnummer gir 429, men samme fodselsnummer mot flere virksomheter gaar`() =
        validerDeltakerTest { oppslag, _ ->
            val utkastId = opprettUtkast()
            val fnr = listOf("010190123", "020290123", "030390123", "040490123", "050590123", "060690123")
                .map { gyldigFnr(it) }

            fnr.take(5).forEach { assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, it, orgnr).status) }
            assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, fnr[0], annetOrgnr).status)

            with(validerDeltaker(utkastId, fnr[5], orgnr)) {
                assertEquals(HttpStatusCode.TooManyRequests, status)
                assertEquals(FOR_MANGE_OPPSLAG, kode())
            }
            assertEquals(6, oppslag.antallArbeidsforholdOppslag)
        }

    @Test
    fun `uendret og bekreftet deltaker gir 200 uten nytt oppslag`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()
        assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, deltakerFnr, orgnr).status)

        with(validerDeltaker(utkastId, deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(body<DTO.Utkast>().deltakerBekreftet)
        }
        assertEquals(1, oppslag.antallArbeidsforholdOppslag)
        assertEquals(1, oppslag.antallNavneoppslag)
    }

    @Test
    fun `feil mot Aareg eller PDL gir 503 og endrer ikke utkastet`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()
        assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, deltakerFnr, orgnr).status)
        val annenDeltaker = gyldigFnr("020290123")

        oppslag.arbeidsforhold = { _, _ -> error("Aareg nede") }
        with(validerDeltaker(utkastId, annenDeltaker, orgnr)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, status)
            assertEquals(OPPSLAG_FEILET, kode())
        }

        oppslag.arbeidsforhold = { _, _ -> true }
        oppslag.navn = { error("PDL nede") }
        with(validerDeltaker(utkastId, annenDeltaker, orgnr)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, status)
            assertEquals(OPPSLAG_FEILET, kode())
        }

        with(hentUtkast(utkastId)) {
            assertTrue(deltakerBekreftet)
            assertEquals(DTO.Ansatt(deltakerFnr, "Mona Moonlight"), ansatt)
        }
    }

    @Test
    fun `ugyldig input gir 400 uten oppslag`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        with(validerDeltaker(utkastId, "12345678910", orgnr)) {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("fnr", json()["felt"]?.jsonPrimitive?.content)
        }
        with(validerDeltaker(utkastId, deltakerFnr, "12345")) {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("virksomhetsnummer", json()["felt"]?.jsonPrimitive?.content)
        }
        assertEquals(0, oppslag.antallArbeidsforholdOppslag)
    }

    @Test
    fun `eget fodselsnummer valideres som andre`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        with(validerDeltaker(utkastId, innloggetFnr, orgnr)) {
            assertEquals(HttpStatusCode.OK, status)
            assertTrue(body<DTO.Utkast>().deltakerBekreftet)
        }
        assertEquals(1, oppslag.antallArbeidsforholdOppslag)
    }

    @Test
    fun `manglende Altinn-tilgang gir 403 uten oppslag`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        assertEquals(HttpStatusCode.Forbidden, validerDeltaker(utkastId, deltakerFnr, orgnrUtenTilgang).status)
        assertEquals(0, oppslag.antallArbeidsforholdOppslag)
    }

    @Test
    fun `utkast opprettet av en annen bruker gir 403`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast(opprettetAv = annenBrukerFnr)

        assertEquals(HttpStatusCode.Forbidden, validerDeltaker(utkastId, deltakerFnr, orgnr).status)
        assertEquals(0, oppslag.antallArbeidsforholdOppslag)
    }

    @Test
    fun `utkast som ikke finnes gir 409`() = validerDeltakerTest { _, _ ->
        assertEquals(HttpStatusCode.Conflict, validerDeltaker(UUID.randomUUID().toString(), deltakerFnr, orgnr).status)
    }

    @Test
    fun `PATCH ignorerer deltaker og virksomhet og beholder bekreftelsen`() =
        validerDeltakerTest { _, _ ->
            val utkastId = opprettUtkast()
            assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, deltakerFnr, orgnr).status)

            val endring = DTO.Utkast(
                ansatt = DTO.Ansatt(gyldigFnr("020290123"), "Skrevet Selv"),
                virksomhet = virksomhet(annetOrgnr, virksomhetsnavn = "Nytt Navn AS"),
            )
            with(patch(utkastId, endring)) {
                assertEquals(HttpStatusCode.OK, status)
                val utkast = body<DTO.Utkast>()
                assertTrue(utkast.deltakerBekreftet)
                assertEquals(deltakerFnr, utkast.ansatt?.fnr)
                assertEquals("Mona Moonlight", utkast.ansatt?.navn)
                assertEquals(orgnr, utkast.virksomhet?.virksomhetsnummer)
                assertEquals("Testbedrift AS", utkast.virksomhet?.virksomhetsnavn)
                assertEquals("Ny Kontakt", utkast.virksomhet?.kontaktperson?.navn)
            }
        }

    @Test
    fun `virksomhetsnavnet hentes fra Altinn-tilgangene`() = validerDeltakerTest { _, _ ->
        val utkastId = opprettUtkast()

        with(validerDeltaker(utkastId, deltakerFnr, annetOrgnr)) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Annen Bedrift AS", body<DTO.Utkast>().virksomhet?.virksomhetsnavn)
        }
    }

    @Test
    fun `virksomhet uten navn i Altinn-tilgangene gir 403 uten oppslag`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        assertEquals(HttpStatusCode.Forbidden, validerDeltaker(utkastId, deltakerFnr, orgnrUtenNavn).status)
        assertEquals(HttpStatusCode.Forbidden, postUtkast(deltakerFnr, orgnrUtenNavn).status)
        assertEquals(0, oppslag.antallArbeidsforholdOppslag)
        assertEquals(1, antallUtkast())
    }

    @Test
    fun `POST oppretter utkast med bekreftet deltaker`() = validerDeltakerTest { oppslag, _ ->
        val utkast = with(postUtkast(deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.Created, status)
            body<DTO.Utkast>()
        }

        assertTrue(utkast.deltakerBekreftet)
        assertEquals(DTO.Ansatt(deltakerFnr, "Mona Moonlight"), utkast.ansatt)
        assertEquals(orgnr, utkast.virksomhet?.virksomhetsnummer)
        assertEquals("Testbedrift AS", utkast.virksomhet?.virksomhetsnavn)
        assertEquals(innloggetFnr, utkast.opprettetAv)
        assertEquals(1, oppslag.antallArbeidsforholdOppslag)
        assertEquals(1, oppslag.antallNavneoppslag)

        assertEquals(utkast, hentUtkast(utkast.id!!))
        assertEquals(1, antallUtkast())
    }

    @Test
    fun `POST uten arbeidsforhold gir 422 og lagrer ikke utkast`() = validerDeltakerTest { oppslag, _ ->
        oppslag.arbeidsforhold = { _, _ -> false }

        with(postUtkast(deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.UnprocessableEntity, status)
            assertEquals(INGEN_ARBEIDSFORHOLD, kode())
        }
        assertEquals(0, antallUtkast())
    }

    @Test
    fun `POST med ugyldig input eller uten tilgang lagrer ikke utkast`() =
        validerDeltakerTest { oppslag, _ ->
            with(postUtkast("12345678910", orgnr)) {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertEquals("fnr", json()["felt"]?.jsonPrimitive?.content)
            }
            with(postUtkast(deltakerFnr, "12345")) {
                assertEquals(HttpStatusCode.BadRequest, status)
                assertEquals("virksomhetsnummer", json()["felt"]?.jsonPrimitive?.content)
            }
            assertEquals(HttpStatusCode.Forbidden, postUtkast(deltakerFnr, orgnrUtenTilgang).status)

            assertEquals(0, oppslag.antallArbeidsforholdOppslag)
            assertEquals(0, antallUtkast())
        }

    @Test
    fun `POST gir 503 og lagrer ikke utkast naar Aareg eller PDL feiler`() = validerDeltakerTest { oppslag, _ ->
        oppslag.arbeidsforhold = { _, _ -> error("Aareg nede") }
        with(postUtkast(deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, status)
            assertEquals(OPPSLAG_FEILET, kode())
        }

        oppslag.arbeidsforhold = { _, _ -> true }
        oppslag.navn = { error("PDL nede") }
        with(postUtkast(deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.ServiceUnavailable, status)
            assertEquals(OPPSLAG_FEILET, kode())
        }

        assertEquals(0, antallUtkast())
    }

    @Test
    fun `POST deler grensen for ulike fodselsnummer med valider-deltaker`() = validerDeltakerTest { _, _ ->
        val utkastId = opprettUtkast()
        val fnr = listOf("010190123", "020290123", "030390123", "040490123", "050590123", "060690123")
            .map { gyldigFnr(it) }

        fnr.take(3).forEach { assertEquals(HttpStatusCode.Created, postUtkast(it, orgnr).status) }
        fnr.drop(3).take(2).forEach { assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, it, orgnr).status) }

        with(postUtkast(fnr[5], orgnr)) {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertEquals(FOR_MANGE_OPPSLAG, kode())
        }
        assertEquals(4, antallUtkast())
    }

    private fun virksomhet(virksomhetsnummer: String, virksomhetsnavn: String = "Testbedrift AS") = DTO.Virksomhet(
        virksomhetsnummer = virksomhetsnummer,
        virksomhetsnavn = virksomhetsnavn,
        kontaktperson = DTO.Kontaktperson(navn = "Ny Kontakt", epost = "kari@test.no", telefonnummer = "99999999"),
    )

    private suspend fun ApplicationTestBuilder.patch(utkastId: String, utkast: DTO.Utkast) =
        client.patch("/api/soknad/v1/$utkastId") {
            bearerAuth("faketoken")
            contentType(ContentType.Application.Json)
            setBody(utkast)
        }

    /** Setter inn et utkast med en bekreftet deltaker, uten å gå via POST og oppslagene. */
    private fun opprettUtkast(opprettetAv: String = innloggetFnr): String =
        transaction(db) {
            UtkastTable.insertAndGetId {
                it[UtkastTable.opprettetAv] = opprettetAv
                it[virksomhetsnummer] = orgnr
                it[virksomhetsnavn] = "Testbedrift AS"
                it[ansattFnr] = startFnr
                it[ansattNavn] = "Start Startesen"
                it[ansattBekreftetTidspunkt] = Clock.System.now()
            }.value.toString()
        }

    private fun antallUtkast(): Long = transaction(db) { UtkastTable.selectAll().count() }

    private suspend fun ApplicationTestBuilder.postUtkast(fnr: String, virksomhetsnummer: String) =
        client.post("/api/soknad/v1") {
            bearerAuth("faketoken")
            contentType(ContentType.Application.Json)
            setBody(ValiderDeltakerRequest(fnr, virksomhetsnummer))
        }

    private suspend fun ApplicationTestBuilder.hentUtkast(utkastId: String): DTO.Utkast =
        client.get("/api/soknad/v1/$utkastId") { bearerAuth("faketoken") }.body()

    private suspend fun ApplicationTestBuilder.validerDeltaker(
        utkastId: String,
        fnr: String,
        virksomhetsnummer: String,
    ) = client.post("/api/soknad/v1/$utkastId/valider-deltaker") {
        bearerAuth("faketoken")
        contentType(ContentType.Application.Json)
        setBody(ValiderDeltakerRequest(fnr, virksomhetsnummer))
    }

    private suspend fun HttpResponse.json() = defaultJson.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.kode() = json()["kode"]?.jsonPrimitive?.content

    private fun altinnTilgang(
        orgnr: String,
        navn: String,
        underenheter: List<AltinnTilgangerClientResponse.AltinnTilgang> = emptyList(),
    ) = AltinnTilgangerClientResponse.AltinnTilgang(
        erSlettet = false,
        orgnr = orgnr,
        organisasjonsform = "BEDR",
        navn = navn,
        underenheter = underenheter,
        altinn3Tilganger = setOf(altinn3Ressursid),
        altinn2Tilganger = emptySet(),
    )

    private fun validerDeltakerTest(
        block: suspend ApplicationTestBuilder.(FakeDeltakerOppslag, Database) -> Unit,
    ) = testApplicationWithDatabase { testDb ->
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = listOf(
                    altinnTilgang("910456800", "Hovedenhet AS", underenheter = listOf(altinnTilgang(orgnr, "Testbedrift AS"))),
                    altinnTilgang(annetOrgnr, "Annen Bedrift AS"),
                ),
                orgNrTilTilganger = mapOf(
                    orgnr to setOf(altinn3Ressursid),
                    annetOrgnr to setOf(altinn3Ressursid),
                    orgnrUtenNavn to setOf(altinn3Ressursid),
                ),
                tilgangTilOrgNr = mapOf(altinn3Ressursid to setOf(orgnr, annetOrgnr, orgnrUtenNavn)),
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
        val eregService = EregService(EregClient(defaultHttpClient = client))
        val oppslag = FakeDeltakerOppslag()
        application {
            dependencies {
                provide { testDb.config.jdbcDatabase }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        when (it) {
                            "faketoken" -> mockIntrospectionResponse.withPid(innloggetFnr)
                            "annentoken" -> mockIntrospectionResponse.withPid(annenBrukerFnr)
                            else -> null
                        }
                    }
                }
                provide<EregService> { eregService }
                provide<DeltakerOppslag> { oppslag }
                provide { altinnTilgangerClient }
            }

            configureAuthentication()
            configureServer()
            configureSoknadApiV1()
        }

        db = testDb.config.jdbcDatabase
        block(oppslag, db)
    }
}
