package no.nav.ekspertbistand.refusjon

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RefusjonStatusApiTest {

    private val orgnrMedTilgang = "1337"

    private fun tilgangTil(orgnr: String) = AltinnTilgangerClientResponse(
        isError = false,
        hierarki = emptyList(),
        orgNrTilTilganger = mapOf(orgnr to setOf(altinn3Ressursid)),
        tilgangTilOrgNr = mapOf(altinn3Ressursid to setOf(orgnr)),
    )

    private val ingenTilgang = AltinnTilgangerClientResponse(
        isError = false,
        hierarki = emptyList(),
        orgNrTilTilganger = emptyMap(),
        tilgangTilOrgNr = emptyMap(),
    )

    private fun ApplicationTestBuilder.konfigurer(
        jdbcDatabase: Database,
        altinnResponse: AltinnTilgangerClientResponse,
    ) {
        mockAltinnTilganger(altinnResponse)
        client = createClient {
            install(ContentNegotiation) { json() }
        }
        val altinnTilgangerClient = AltinnTilgangerClient(
            defaultHttpClient = client,
            tokenExchanger = successTokenXTokenExchanger,
        )
        val clamAvClient = ClamAvClient(defaultHttpClient = client)
        application {
            dependencies {
                provide { jdbcDatabase }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide { altinnTilgangerClient }
                provide { clamAvClient }
            }
            configureAuthentication()
            configureServer()
            configureRefusjonApiV1()
        }
    }

    @Test
    fun `henter refusjonstatus med vedlegg for søknad man har tilgang til`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            RefusjonDb(testDb.config.jdbcDatabase).lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = 2_240_000,
                utgifter = "Utgifter til ekspertbistand",
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF-innhold".toByteArray())),
            )

            with(client.get("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.OK, status)
                val dto = body<RefusjonStatusDto>()
                assertEquals(22_400, dto.belopKroner)
                assertEquals("Utgifter til ekspertbistand", dto.utgifter)
                assertEquals(null, dto.kontonummer)
                assertEquals(1, dto.vedlegg.size)
                assertEquals("kvittering.pdf", dto.vedlegg.first().filnavn)
            }
        }

    @Test
    fun `returnerer 204 når det ikke finnes refusjonskrav`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }

            with(client.get("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NoContent, status)
            }
        }

    @Test
    fun `returnerer 404 når søknaden ikke finnes`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            with(client.get("/api/soknad/v1/${UUID.randomUUID()}/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun `returnerer 403 uten tilgang til virksomheten`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, ingenTilgang)

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            RefusjonDb(testDb.config.jdbcDatabase).lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = 100_000,
                utgifter = "Utgifter",
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF".toByteArray())),
            )

            with(client.get("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }

    @Test
    fun `laster ned vedlegg for søknad man har tilgang til`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)
            db.lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = 100_000,
                utgifter = "Utgifter",
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF-innhold".toByteArray())),
            )
            val vedleggId = db.finnRefusjonskravStatus(soknadId)!!.vedlegg.first().id

            with(client.get("/api/soknad/v1/$soknadId/refusjon/vedlegg/$vedleggId") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Application.Pdf, contentType()?.withoutParameters())
                val disposition = headers[HttpHeaders.ContentDisposition]
                assertNotNull(disposition)
                assertTrue(disposition.contains("attachment"))
                assertTrue(disposition.contains("kvittering.pdf"))
                assertEquals("PDF-innhold", body<ByteArray>().decodeToString())
            }
        }

    @Test
    fun `returnerer 403 ved nedlasting uten tilgang`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, ingenTilgang)

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)
            db.lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = 100_000,
                utgifter = "Utgifter",
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF".toByteArray())),
            )
            val vedleggId = db.finnRefusjonskravStatus(soknadId)!!.vedlegg.first().id

            with(client.get("/api/soknad/v1/$soknadId/refusjon/vedlegg/$vedleggId") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }

    @Test
    fun `kan ikke laste ned vedlegg fra en annen søknad (IDOR)`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadA = UUID.randomUUID()
            val soknadB = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadA, orgnrMedTilgang)
                insertDummySoknad(soknadB, orgnrMedTilgang)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)
            db.lagreRefusjonskrav(soknadA, 100_000, "A", listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
            db.lagreRefusjonskrav(soknadB, 100_000, "B", listOf(RefusjonsfilInput("b.pdf", "B".toByteArray())))
            val vedleggIdB = db.finnRefusjonskravStatus(soknadB)!!.vedlegg.first().id

            // Bruker vedleggId fra søknad B, men på søknad A sin sti
            with(client.get("/api/soknad/v1/$soknadA/refusjon/vedlegg/$vedleggIdB") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }
}

private fun insertDummySoknad(soknadId: UUID, vnr: String) {
    SoknadTable.insert {
        it[id] = soknadId
        it[virksomhetsnavn] = "foo"
        it[virksomhetsnummer] = vnr
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
