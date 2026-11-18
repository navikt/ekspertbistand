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
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEreg
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** PATCH av ekspertens virksomhet, se specifications/innsending_fra_utkast.md (5). */
@OptIn(ExperimentalTime::class)
class EkspertVirksomhetPatchTest {

    private val innloggetFnr = gyldigFnr("010170123")
    private val lagretOrgnr = "910825226"
    private val nyttOrgnr = "974760673"

    /** Svaret fra Ereg for hvert orgnr. Null gir 404, og et kast gir 500. */
    private var eregNavn: (String) -> String? = { mapOf(nyttOrgnr to "Ny Ekspert AS")[it] }
    private var antallEregKall = 0
    private lateinit var db: Database

    @Test
    fun `nytt orgnr gir navn fra Ereg og ignorerer navnet fra klienten`() = patchTest {
        val utkastId = opprettUtkast()

        with(patch(utkastId, ekspert(nyttOrgnr, virksomhet = "Jukset AS (974760673)", virksomhetNavn = "Jukset AS"))) {
            assertEquals(HttpStatusCode.OK, status)
            val ekspert = body<DTO.Utkast>().ekspert!!
            assertEquals(nyttOrgnr, ekspert.virksomhetOrgnr)
            assertEquals("Ny Ekspert AS", ekspert.virksomhetNavn)
            assertEquals("Ny Ekspert AS ($nyttOrgnr)", ekspert.virksomhet)
            assertEquals("Egon Olsen", ekspert.navn)
        }
        assertEquals(1, antallEregKall)

        with(rad(utkastId)) {
            assertEquals(nyttOrgnr, this[UtkastTable.ekspertVirksomhetOrgnr])
            assertEquals("Ny Ekspert AS", this[UtkastTable.ekspertVirksomhetNavn])
            assertEquals("Ny Ekspert AS ($nyttOrgnr)", this[UtkastTable.ekspertVirksomhet])
        }
    }

    @Test
    fun `uendret orgnr gir ikke oppslag og beholder navnet`() = patchTest {
        val utkastId = opprettUtkast()

        with(patch(utkastId, ekspert(lagretOrgnr, virksomhet = "Annet AS", virksomhetNavn = "Annet AS", navn = "Ny Ekspert"))) {
            assertEquals(HttpStatusCode.OK, status)
            val ekspert = body<DTO.Utkast>().ekspert!!
            assertEquals("Ny Ekspert", ekspert.navn)
            assertEquals("Lagret Ekspert AS", ekspert.virksomhetNavn)
            assertEquals("Lagret Ekspert AS ($lagretOrgnr)", ekspert.virksomhet)
        }
        assertEquals(0, antallEregKall)
    }

    @Test
    fun `orgnr null eller blankt nullstiller ekspertens virksomhet`() = patchTest {
        for (orgnr in listOf(null, "")) {
            val utkastId = opprettUtkast()

            assertEquals(HttpStatusCode.OK, patch(utkastId, ekspert(orgnr, virksomhet = "Annet AS")).status)

            with(rad(utkastId)) {
                assertNull(this[UtkastTable.ekspertVirksomhetOrgnr])
                assertNull(this[UtkastTable.ekspertVirksomhetNavn])
                assertNull(this[UtkastTable.ekspertVirksomhet])
                assertEquals("Egon Olsen", this[UtkastTable.ekspertNavn])
            }
        }
        assertEquals(0, antallEregKall)
    }

    @Test
    fun `orgnr som ikke er 9 siffer gir 400 uten oppslag`() = patchTest {
        val utkastId = opprettUtkast()

        with(patch(utkastId, ekspert("12345678", navn = "Ny Ekspert"))) {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("ekspert.virksomhetOrgnr", json()["felt"]?.jsonPrimitive?.content)
        }
        assertEquals(0, antallEregKall)
        assertUendret(utkastId)
    }

    @Test
    fun `orgnr som Ereg ikke kjenner gir 400`() = patchTest {
        val utkastId = opprettUtkast()
        eregNavn = { null }

        with(patch(utkastId, ekspert(nyttOrgnr, navn = "Ny Ekspert"))) {
            assertEquals(HttpStatusCode.BadRequest, status)
            assertEquals("ekspert.virksomhetOrgnr", json()["felt"]?.jsonPrimitive?.content)
        }
        assertUendret(utkastId)
    }

    @Test
    fun `feil mot Ereg gir 503 og endrer ikke utkastet`() = patchTest {
        val utkastId = opprettUtkast()
        eregNavn = { error("Ereg nede") }

        with(patch(utkastId, ekspert(nyttOrgnr, navn = "Ny Ekspert"))) {
            assertEquals(HttpStatusCode.ServiceUnavailable, status)
            assertEquals(OPPSLAG_FEILET, json()["kode"]?.jsonPrimitive?.content)
        }
        assertUendret(utkastId)
    }

    @Test
    fun `PATCH uten ekspert endrer ikke ekspertens virksomhet`() = patchTest {
        val utkastId = opprettUtkast()

        assertEquals(HttpStatusCode.OK, patch(utkastId, DTO.Utkast(nav = DTO.Nav(kontaktperson = "Navn Navnesen"))).status)

        assertEquals(0, antallEregKall)
        assertUendret(utkastId)
    }

    private fun ekspert(
        orgnr: String?,
        virksomhet: String = "",
        virksomhetNavn: String? = null,
        navn: String = "Egon Olsen",
    ) = DTO.Utkast(
        ekspert = DTO.Ekspert(
            navn = navn,
            virksomhet = virksomhet,
            virksomhetNavn = virksomhetNavn,
            virksomhetOrgnr = orgnr,
        )
    )

    private fun opprettUtkast(): String =
        transaction(db) {
            UtkastTable.insertAndGetId {
                it[opprettetAv] = innloggetFnr
                it[virksomhetsnummer] = "910456900"
                it[virksomhetsnavn] = "Testbedrift AS"
                it[ansattFnr] = gyldigFnr("010190123")
                it[ansattNavn] = "Mona Moonlight"
                it[ansattBekreftetTidspunkt] = Clock.System.now()
                it[ekspertNavn] = "Egon Olsen"
                it[ekspertVirksomhetOrgnr] = lagretOrgnr
                it[ekspertVirksomhetNavn] = "Lagret Ekspert AS"
                it[ekspertVirksomhet] = "Lagret Ekspert AS ($lagretOrgnr)"
            }.value.toString()
        }

    private fun rad(utkastId: String): ResultRow =
        transaction(db) { UtkastTable.selectAll().where { UtkastTable.id eq UUID.fromString(utkastId) }.single() }

    private fun assertUendret(utkastId: String) = with(rad(utkastId)) {
        assertEquals("Egon Olsen", this[UtkastTable.ekspertNavn])
        assertEquals(lagretOrgnr, this[UtkastTable.ekspertVirksomhetOrgnr])
        assertEquals("Lagret Ekspert AS", this[UtkastTable.ekspertVirksomhetNavn])
        assertEquals("Lagret Ekspert AS ($lagretOrgnr)", this[UtkastTable.ekspertVirksomhet])
    }

    private suspend fun ApplicationTestBuilder.patch(utkastId: String, utkast: DTO.Utkast) =
        client.patch("/api/soknad/v1/$utkastId") {
            bearerAuth("faketoken")
            contentType(ContentType.Application.Json)
            setBody(utkast)
        }

    private suspend fun HttpResponse.json() = defaultJson.parseToJsonElement(bodyAsText()).jsonObject

    private fun patchTest(block: suspend ApplicationTestBuilder.() -> Unit) = testApplicationWithDatabase { testDb ->
        mockEreg { orgnr ->
            antallEregKall++
            eregNavn(orgnr)?.let { """{ "navn": { "sammensattnavn": "$it" } }""" }
        }
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
        application {
            dependencies {
                provide { testDb.config.jdbcDatabase }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid(innloggetFnr) else null
                    }
                }
                provide<EregService> { eregService }
                provide<DeltakerOppslag> { FakeDeltakerOppslag() }
                provide { altinnTilgangerClient }
            }

            configureAuthentication()
            configureServer()
            configureSoknadApiV1()
        }

        db = testDb.config.jdbcDatabase
        block()
    }
}
