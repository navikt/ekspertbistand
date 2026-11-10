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
import no.nav.ekspertbistand.sak.lagreFattetVedtak
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakKildeArenaException
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.nesteSaksnummer
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.vedlegg.VedleggTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            val sakId = transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            RefusjonDb(testDb.config.jdbcDatabase).lagreRefusjonskrav(
                soknadId = soknadId,
                belopOre = 2_240_000,
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF-innhold".toByteArray())),
            )
            transaction(testDb.config.jdbcDatabase) {
                assertEquals(sakId, RefusjonskravTable.selectAll().single()[RefusjonskravTable.sakId])
            }

            with(client.get("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.OK, status)
                val dto = body<RefusjonStatusDto>()
                assertEquals(22_400, dto.belopKroner)
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
                filer = listOf(RefusjonsfilInput("kvittering.pdf", "PDF".toByteArray())),
            )

            with(client.get("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
        }

    @Test
    fun `avviser innsending av refusjonskrav uten Altinn-tilgang`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, ingenTilgang)

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }

            with(client.post("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Forbidden, status)
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(RefusjonskravTable.selectAll().empty())
                assertTrue(VedleggTable.selectAll().empty())
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
            db.lagreRefusjonskrav(soknadA, 100_000, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
            db.lagreRefusjonskrav(soknadB, 100_000, listOf(RefusjonsfilInput("b.pdf", "B".toByteArray())))
            val vedleggIdB = db.finnRefusjonskravStatus(soknadB)!!.vedlegg.first().id

            with(client.get("/api/soknad/v1/$soknadA/refusjon/vedlegg/$vedleggIdB") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }

    @Test
    fun `avviser nytt refusjonskrav når saken allerede har ett`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)
            db.lagreRefusjonskrav(soknadId, 100_000, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))

            with(client.post("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Conflict, status)
            }
            assertFailsWith<RefusjonskravFinnesAlleredeException> {
                db.lagreRefusjonskrav(soknadId, 200_000, listOf(RefusjonsfilInput("b.pdf", "B".toByteArray())))
            }
            transaction(testDb.config.jdbcDatabase) {
                assertEquals(1, RefusjonskravTable.selectAll().count())
                assertEquals(1, VedleggTable.selectAll().count())
            }
        }

    @Test
    fun `avviser refusjonskrav når søknaden ikke er godkjent`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang, SoknadStatus.innsendt)
            }

            with(client.post("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Conflict, status)
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(RefusjonskravTable.selectAll().empty())
            }
        }

    @Test
    fun `lagrer refusjonskrav lik innvilget beløp og avviser krav over`() =
        testApplicationWithDatabase { testDb ->
            val soknadOver = UUID.randomUUID()
            val soknadLik = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadOver, orgnrMedTilgang, innvilgetBelop = 15_000)
                insertDummySoknad(soknadLik, orgnrMedTilgang, innvilgetBelop = 15_000)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)

            val feil = assertFailsWith<BelopOverInnvilgetException> {
                db.lagreRefusjonskrav(soknadOver, 15_000_01, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
            }
            assertEquals(15_000, feil.maksBelopKroner)
            db.lagreRefusjonskrav(soknadLik, 15_000_00, listOf(RefusjonsfilInput("b.pdf", "B".toByteArray())))

            transaction(testDb.config.jdbcDatabase) {
                assertEquals(15_000_00L, RefusjonskravTable.selectAll().single()[RefusjonskravTable.belopOre])
                assertEquals(1, VedleggTable.selectAll().count())
            }
        }

    @Test
    fun `bruker redusert beløp som grense`() =
        testApplicationWithDatabase { testDb ->
            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                val sakId = insertDummySoknad(soknadId, orgnrMedTilgang, innvilgetBelop = null)
                lagreFattetVedtak(sakId, ForelopigUtfall.INNVILGE_REDUSERT, 8_000)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)

            assertEquals(8_000, db.hentMaksBelopKroner(soknadId))
            assertFailsWith<BelopOverInnvilgetException> {
                db.lagreRefusjonskrav(soknadId, 8_001_00, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
            }
        }

    @Test
    fun `avviser refusjonskrav uten innvilget vedtak`() =
        testApplicationWithDatabase { testDb ->
            val utenVedtak = UUID.randomUUID()
            val avslatt = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(utenVedtak, orgnrMedTilgang, innvilgetBelop = null)
                val sakId = insertDummySoknad(avslatt, orgnrMedTilgang, innvilgetBelop = null)
                lagreFattetVedtak(sakId, ForelopigUtfall.AVSLAA, null)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)

            listOf(utenVedtak, avslatt).forEach { soknadId ->
                assertFailsWith<InnvilgetBelopManglerException> { db.hentMaksBelopKroner(soknadId) }
                assertFailsWith<InnvilgetBelopManglerException> {
                    db.lagreRefusjonskrav(soknadId, 100_00, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
                }
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(RefusjonskravTable.selectAll().empty())
            }
        }

    @Test
    fun `avviser refusjonskrav for Arena-sak`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang, kilde = KildeTilBehandling.ARENA)
            }
            val db = RefusjonDb(testDb.config.jdbcDatabase)

            assertFailsWith<SakKildeArenaException> {
                db.lagreRefusjonskrav(soknadId, 100_00, listOf(RefusjonsfilInput("a.pdf", "A".toByteArray())))
            }
            with(client.post("/api/soknad/v1/$soknadId/refusjon") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.Conflict, status)
            }
            transaction(testDb.config.jdbcDatabase) {
                assertTrue(RefusjonskravTable.selectAll().empty())
            }
        }

    @Test
    fun `henter maksbeløp for søknad man har tilgang til`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val soknadId = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(soknadId, orgnrMedTilgang, innvilgetBelop = 45_000)
            }

            with(client.get("/api/soknad/v1/$soknadId/refusjon/maksbelop") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(45_000, body<MaksBelopDto>().maksBelopKroner)
            }
        }

    @Test
    fun `maksbeløp gir 409 for Arena-sak og sak uten innvilget beløp`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, tilgangTil(orgnrMedTilgang))

            val arena = UUID.randomUUID()
            val utenVedtak = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(arena, orgnrMedTilgang, kilde = KildeTilBehandling.ARENA)
                insertDummySoknad(utenVedtak, orgnrMedTilgang, innvilgetBelop = null)
            }

            listOf(arena, utenVedtak).forEach { soknadId ->
                with(client.get("/api/soknad/v1/$soknadId/refusjon/maksbelop") { bearerAuth("faketoken") }) {
                    assertEquals(HttpStatusCode.Conflict, status)
                }
            }
        }

    @Test
    fun `maksbeløp gir 403 uten tilgang før kilde og vedtak sjekkes`() =
        testApplicationWithDatabase { testDb ->
            konfigurer(testDb.config.jdbcDatabase, ingenTilgang)

            val medBelop = UUID.randomUUID()
            val arena = UUID.randomUUID()
            transaction(testDb.config.jdbcDatabase) {
                insertDummySoknad(medBelop, orgnrMedTilgang)
                insertDummySoknad(arena, orgnrMedTilgang, kilde = KildeTilBehandling.ARENA)
            }

            listOf(medBelop, arena).forEach { soknadId ->
                with(client.get("/api/soknad/v1/$soknadId/refusjon/maksbelop") { bearerAuth("faketoken") }) {
                    assertEquals(HttpStatusCode.Forbidden, status)
                }
            }
            with(client.get("/api/soknad/v1/${UUID.randomUUID()}/refusjon/maksbelop") { bearerAuth("faketoken") }) {
                assertEquals(HttpStatusCode.NotFound, status)
            }
        }
}

private fun insertDummySoknad(
    soknadId: UUID,
    vnr: String,
    soknadStatus: SoknadStatus = SoknadStatus.godkjent,
    kilde: KildeTilBehandling = KildeTilBehandling.EKSPERTBISTAND,
    innvilgetBelop: Int? = 24_900,
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
    val sakId = SakTable.insertReturning {
        it[SakTable.soknadId] = soknadId
        it[status] = Saksstatus.OPPRETTET.name
        it[kildeTilBehandling] = kilde.name
        it[saksnummer] = nesteSaksnummer()
    }.single()[SakTable.sakId]
    if (innvilgetBelop != null) {
        lagreFattetVedtak(sakId, ForelopigUtfall.INNVILGE, innvilgetBelop)
    }
    return sakId
}
