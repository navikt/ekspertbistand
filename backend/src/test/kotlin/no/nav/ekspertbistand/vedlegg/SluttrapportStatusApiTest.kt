package no.nav.ekspertbistand.vedlegg

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
import no.nav.ekspertbistand.sak.KildeTilBehandling
import no.nav.ekspertbistand.sak.SakTable
import no.nav.ekspertbistand.sak.Saksstatus
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SluttrapportStatusApiTest {

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
            configureVedleggApiV1()
        }
    }

    @Test
    fun `henter sluttrapport-metadata uten filinnhold for søknad man har tilgang til`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            val sakId = transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            VedleggDb(testDb.config.jdbcDatabase).lagreSluttrapport(
                soknadId = soknadId,
                filnavn = "sluttrapport.pdf",
                innhold = "HEMMELIG-PDF-INNHOLD".toByteArray(),
            )
            transaction(testDb.config.jdbcDatabase) {
                val sluttrapport = SluttrapportTable.selectAll().single()
                assertEquals(sakId, sluttrapport[SluttrapportTable.sakId])
                assertEquals(
                    sluttrapport[SluttrapportTable.id],
                    VedleggTable.selectAll().single()[VedleggTable.sluttrapportId],
                )
            }

            with(client.get("/api/soknad/v1/$soknadId/sluttrapport") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.OK, status)
                val dto = body<SluttrapportStatusDto>()
                assertEquals("sluttrapport.pdf", dto.filnavn)
                // Filinnhold skal aldri lekke i status-responsen (personsensitivt)
                assertFalse(bodyAsText().contains("HEMMELIG-PDF-INNHOLD"))
            }
        }

    @Test
    fun `returnerer 204 når det ikke finnes sluttrapport`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }

            with(client.get("/api/soknad/v1/$soknadId/sluttrapport") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NoContent, status)
            }
        }

    @Test
    fun `returnerer 404 når søknaden ikke finnes`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            with(client.get("/api/soknad/v1/${UUID.randomUUID()}/sluttrapport") { bearerAuth("faketoken") }) {
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
            VedleggDb(testDb.config.jdbcDatabase).lagreSluttrapport(
                soknadId = soknadId,
                filnavn = "sluttrapport.pdf",
                innhold = "PDF".toByteArray(),
            )

            with(client.get("/api/soknad/v1/$soknadId/sluttrapport") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }

    @Test
    fun `avviser innsending av sluttrapport uten Altinn-tilgang`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, ingenTilgang)

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }

            with(client.post("/api/soknad/v1/$soknadId/sluttrapport") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(SluttrapportTable.selectAll().empty())
                assertTrue(VedleggTable.selectAll().empty())
            }
        }

    @Test
    fun `avviser sluttrapport når søknaden ikke er godkjent`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang, SoknadStatus.innsendt)
            }

            with(client.post("/api/soknad/v1/$soknadId/sluttrapport") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Conflict, status)
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(SluttrapportTable.selectAll().empty())
                assertTrue(VedleggTable.selectAll().empty())
            }
        }
}

private fun insertDummySoknad(
    soknadId: UUID,
    vnr: String,
    soknadStatus: SoknadStatus = SoknadStatus.godkjent,
): UUID {
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
        it[status] = soknadStatus.name
    }
    return SakTable.insertReturning {
        it[SakTable.soknadId] = soknadId
        it[status] = Saksstatus.OPPRETTET.name
        it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
    }.single()[SakTable.sakId]
}
