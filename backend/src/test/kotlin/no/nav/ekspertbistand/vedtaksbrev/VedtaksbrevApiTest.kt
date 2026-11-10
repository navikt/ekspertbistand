package no.nav.ekspertbistand.vedtaksbrev

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
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.domain.SakDomain
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SLADDET_NAVN
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.nesteSaksnummer
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.eq
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class VedtaksbrevApiTest {

    private val orgnr = "987654321"
    private val journalfoert = TestEventData.vedtaksbrevArbeidsgiverJournalfoert

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
        onConvert: (String) -> Unit = {},
    ) {
        mockAltinnTilganger(altinnResponse)
        client = createClient {
            install(ContentNegotiation) { json() }
        }
        val altinnTilgangerClient = AltinnTilgangerClient(
            defaultHttpClient = client,
            tokenExchanger = successTokenXTokenExchanger,
        )
        application {
            dependencies {
                provide { jdbcDatabase }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide { altinnTilgangerClient }
                provide { DokumentService(StubPdfKonverterer(onConvert = onConvert)) }
            }
            configureAuthentication()
            configureServer()
            configureVedtaksbrevApiV1()
        }
    }

    @Test
    fun `innvilget gir vedtaket`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = settInnVedtaksbrev(database, journalfoert)

        with(client.get("/api/soknad/v1/$soknadId/vedtak") { bearerAuth("faketoken") }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(
                VedtaksbrevDto(
                    utfall = VedtaksbrevUtfall.INNVILGET,
                    vedtaksdato = "4. april 2026",
                    innvilgetBelop = "9\u00A0000",
                    soknadssum = "9\u00A0000",
                    tiltaksperiode = VedtaksbrevDto.Periode(fra = "1. april 2026", til = "30. juni 2026"),
                    refusjonsfrist = "30. august 2026",
                    deltakerNavn = "Ansatt Navn",
                    virksomhetsnavn = "Testbedrift AS",
                ),
                body<VedtaksbrevDto>(),
            )
        }
        assertIngenVedtaksbrevVist(database)
    }

    @Test
    fun `redusert belop gir vedtaket`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = settInnVedtaksbrev(
            database,
            journalfoert.copy(
                utfall = VedtakDomain.Utfall.INNVILGE_REDUSERT,
                vedtaksbrev = journalfoert.vedtaksbrev.copy(innvilgetBelop = "6\u00A0000"),
            ),
        )

        with(client.get("/api/soknad/v1/$soknadId/vedtak") { bearerAuth("faketoken") }) {
            assertEquals(HttpStatusCode.OK, status)
            val dto = body<VedtaksbrevDto>()
            assertEquals(VedtaksbrevUtfall.INNVILGET_REDUSERT, dto.utfall)
            assertEquals("6\u00A0000", dto.innvilgetBelop)
            assertEquals("9\u00A0000", dto.soknadssum)
            assertEquals("30. august 2026", dto.refusjonsfrist)
        }
    }

    @Test
    fun `avslag gir vedtaket uten belop og refusjonsfrist`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = settInnVedtaksbrev(
            database,
            journalfoert.copy(
                utfall = VedtakDomain.Utfall.AVSLAA,
                vedtaksbrev = journalfoert.vedtaksbrev.copy(
                    innvilgetBelop = null,
                    refusjonsfrist = null,
                    begrunnelse = "Begrunnelse",
                ),
            ),
        )

        with(client.get("/api/soknad/v1/$soknadId/vedtak") { bearerAuth("faketoken") }) {
            assertEquals(HttpStatusCode.OK, status)
            val dto = body<VedtaksbrevDto>()
            assertEquals(VedtaksbrevUtfall.AVSLATT, dto.utfall)
            assertNull(dto.innvilgetBelop)
            assertNull(dto.refusjonsfrist)
            assertEquals("9\u00A0000", dto.soknadssum)
        }
    }

    @Test
    fun `ukjent soknad gir 404`() = testApplicationWithDatabase { testDb ->
        konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnr))

        listOf("vedtak", "vedtak/brev-pdf").forEach { sti ->
            with(client.get("/api/soknad/v1/${UUID.randomUUID()}/$sti") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status, sti)
            }
        }
    }

    @Test
    fun `utlopt tilgang for arbeidsgiver gir 404`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = settInnVedtaksbrev(database, journalfoert, tilgangUtlopt = true)

        listOf("vedtak", "vedtak/brev-pdf").forEach { sti ->
            with(client.get("/api/soknad/v1/$soknadId/$sti") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status, sti)
            }
        }
        assertIngenVedtaksbrevVist(database)
    }

    @Test
    fun `uten altinn-tilgang gir 403 ogsa uten vedtak`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, ingenTilgang)
        val medVedtak = settInnVedtaksbrev(database, journalfoert)
        val utenVedtak = transaction(database) { settInnSoknadMedSak(orgnr, tilgangUtlopt = false) }.first

        listOf(medVedtak, utenVedtak).forEach { soknadId ->
            listOf("vedtak", "vedtak/brev-pdf").forEach { sti ->
                with(client.get("/api/soknad/v1/$soknadId/$sti") { bearerAuth("faketoken") }) {
                    assertEquals(HttpStatusCode.Forbidden, status, sti)
                }
            }
        }
        assertIngenVedtaksbrevVist(database)
    }

    @Test
    fun `soknad uten vedtaksbrev gir 404`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = transaction(database) { settInnSoknadMedSak(orgnr, tilgangUtlopt = false) }.first

        listOf("vedtak", "vedtak/brev-pdf").forEach { sti ->
            with(client.get("/api/soknad/v1/$soknadId/$sti") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status, sti)
                assertEquals("vedtak ikke funnet", bodyAsText(), sti)
            }
        }
        assertIngenVedtaksbrevVist(database)
    }

    @Test
    fun `brev-pdf gir pdf og publiserer VedtaksbrevVist`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        konfigurer(database, tilgangTil(orgnr))
        val soknadId = settInnVedtaksbrev(database, journalfoert)
        val sakId = transaction(database) {
            SakTable.selectAll().where { SakTable.soknadId eq soknadId }.single()[SakTable.sakId]
        }

        with(client.get("/api/soknad/v1/$soknadId/vedtak/brev-pdf") { bearerAuth("faketoken") }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Application.Pdf, contentType()?.withoutParameters())
            assertTrue(headers[HttpHeaders.ContentDisposition]!!.startsWith("attachment"))
            assertEquals("%PDF-mock", bodyAsText())
        }

        val vist = transaction(database) {
            QueuedEvents.selectAll().map { it[QueuedEvents.eventData] }.filterIsInstance<EventData.VedtaksbrevVist>()
        }.single()
        assertEquals(sakId.toString(), vist.sakId)
        assertEquals(soknadId.toString(), vist.soknadId)
        assertEquals(VedtakDomain.Utfall.INNVILGE, vist.utfall)
        assertEquals(orgnr, vist.virksomhetsnummer)
        assertEquals("42", vist.vistTil)
    }

    @Test
    fun `brev-pdf sladder ikke deltaker med kode 6`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        val html = StringBuilder()
        konfigurer(database, tilgangTil(orgnr), onConvert = { html.append(it) })
        val soknadId = settInnVedtaksbrev(
            database,
            journalfoert.copy(adressebeskyttelse = SakDomain.Adressebeskyttelse.STRENGT_FORTROLIG),
        )

        with(client.get("/api/soknad/v1/$soknadId/vedtak/brev-pdf") { bearerAuth("faketoken") }) {
            assertEquals(HttpStatusCode.OK, status)
        }
        assertTrue(html.contains("Ansatt Navn"))
        assertFalse(html.contains(SLADDET_NAVN))
    }

    private fun assertIngenVedtaksbrevVist(database: Database) = transaction(database) {
        assertTrue(
            QueuedEvents.selectAll().map { it[QueuedEvents.eventData] }
                .filterIsInstance<EventData.VedtaksbrevVist>()
                .isEmpty()
        )
    }

    private fun settInnVedtaksbrev(
        database: Database,
        data: EventData.VedtaksbrevArbeidsgiverJournalfoert,
        tilgangUtlopt: Boolean = false,
    ): UUID {
        val (soknadId, sakId) = transaction(database) { settInnSoknadMedSak(data.virksomhetsnummer, tilgangUtlopt) }
        VedtaksbrevDb(database).lagre(
            eventId = 1,
            data = data.copy(sakId = sakId.toString(), soknadId = soknadId.toString()),
        )
        return soknadId
    }

    private fun settInnSoknadMedSak(vnr: String, tilgangUtlopt: Boolean): Pair<UUID, UUID> {
        val soknadId = UUID.randomUUID()
        SoknadTable.insert {
            it[id] = soknadId
            it[virksomhetsnavn] = "Testbedrift AS"
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
            it[status] = "godkjent"
            if (tilgangUtlopt) it[arbeidsgiverTilgangTil] = Clock.System.now() - 1.days
        }
        val sakId = SakTable.insertReturning {
            it[SakTable.soknadId] = soknadId
            it[status] = Saksstatus.INNVILGET.name
            it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
            it[saksnummer] = nesteSaksnummer()
        }.single()[SakTable.sakId]
        return soknadId to sakId
    }
}
