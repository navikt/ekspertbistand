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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.*
import kotlin.test.*

class ValiderDeltakerTest {

    private val innloggetFnr = gyldigFnr("010170123")
    private val annenBrukerFnr = gyldigFnr("010170456")
    private val deltakerFnr = gyldigFnr("010190123")
    private val orgnr = "910456900"
    private val annetOrgnr = "910456901"
    private val orgnrUtenTilgang = "910456999"

    @Test
    fun `treff gir 200 med navn og bekreftelse`() = validerDeltakerTest { oppslag, _ ->
        val utkastId = opprettUtkast()

        with(validerDeltaker(utkastId, deltakerFnr, orgnr)) {
            assertEquals(HttpStatusCode.OK, status)
            val utkast = body<DTO.Utkast>()
            assertTrue(utkast.deltakerBekreftet)
            assertEquals(DTO.Ansatt(deltakerFnr, "Mona Moonlight"), utkast.ansatt)
            assertEquals(orgnr, utkast.virksomhet?.virksomhetsnummer)
        }
        assertEquals(1, oppslag.antallArbeidsforholdOppslag)
        assertEquals(1, oppslag.antallNavneoppslag)

        with(hentUtkast(utkastId)) {
            assertTrue(deltakerBekreftet)
            assertEquals("Mona Moonlight", ansatt?.navn)
        }
    }

    @Test
    fun `ingen arbeidsforhold gir 422 og nullstiller navn og bekreftelse`() = validerDeltakerTest { oppslag, _ ->
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
            assertFalse(deltakerBekreftet)
            assertEquals(DTO.Ansatt(annenDeltaker, ""), ansatt)
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
        val utkastId = opprettUtkast(token = "annentoken")

        assertEquals(HttpStatusCode.Forbidden, validerDeltaker(utkastId, deltakerFnr, orgnr).status)
        assertEquals(0, oppslag.antallArbeidsforholdOppslag)
    }

    @Test
    fun `utkast som ikke finnes gir 409`() = validerDeltakerTest { _, _ ->
        assertEquals(HttpStatusCode.Conflict, validerDeltaker(UUID.randomUUID().toString(), deltakerFnr, orgnr).status)
    }

    @Test
    fun `tilUtkastDTO leser bekreftelsen fra databasen`() = validerDeltakerTest { _, db ->
        val utkastId = opprettUtkast()
        assertEquals(HttpStatusCode.OK, validerDeltaker(utkastId, deltakerFnr, orgnr).status)

        transaction(db) {
            UtkastTable.update({ UtkastTable.id eq UUID.fromString(utkastId) }) {
                it[ansattBekreftetTidspunkt] = null
            }
        }
        assertFalse(hentUtkast(utkastId).deltakerBekreftet)
    }

    private suspend fun ApplicationTestBuilder.opprettUtkast(token: String = "faketoken"): String =
        client.post("/api/soknad/v1") { bearerAuth(token) }.body<DTO.Utkast>().id!!

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

    private fun validerDeltakerTest(
        block: suspend ApplicationTestBuilder.(FakeDeltakerOppslag, org.jetbrains.exposed.v1.jdbc.Database) -> Unit,
    ) = testApplicationWithDatabase { testDb ->
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(
                    orgnr to setOf(altinn3Ressursid),
                    annetOrgnr to setOf(altinn3Ressursid),
                ),
                tilgangTilOrgNr = mapOf(altinn3Ressursid to setOf(orgnr, annetOrgnr)),
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

        block(oppslag, testDb.config.jdbcDatabase)
    }
}
