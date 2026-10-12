package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.refusjon.RefusjonDb
import no.nav.ekspertbistand.refusjon.RefusjonStatusDto
import no.nav.ekspertbistand.refusjon.RefusjonskravTable
import no.nav.ekspertbistand.refusjon.RefusjonsfilInput
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatusDto
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import no.nav.ekspertbistand.vedlegg.VedleggDb
import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.VedleggType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SakDokumentApiTest {

    private val gyldigToken = "valid-azure-token"
    private val navIdent = "A123456"
    private val saksbehandlerGrupper = """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]"""
    private val beslutterGrupper = """[{ "rolle": "0000-CA-Ekspertbistand_Beslutter" }]"""
    private val ingenGrupper = "[]"
    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private val sluttrapportPdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2d, 0x31)
    private val refusjonPdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2d, 0x32)

    private class RecordingAuditLogger : AuditLogger {
        override fun log(message: CefMessage) = Unit
        override fun log(message: String) = error("ikke i bruk")
    }

    @Test
    fun `saksbehandler kan lese metadata og filer for begge dokumenttypene`() =
        testApplicationWithDatabase { db ->
            assertDokumentruterTilgjengelige(db, saksbehandlerGrupper)
        }

    @Test
    fun `beslutter kan lese metadata og filer for begge dokumenttypene`() =
        testApplicationWithDatabase { db ->
            assertDokumentruterTilgjengelige(db, beslutterGrupper)
        }

    @Test
    fun `mangler token gir 401 og manglende rolle gir 403`() = testApplicationWithDatabase { db ->
        val sak = godkjentSak(db)
        val oppsett = oppsett(db, grupper = ingenGrupper)

        assertEquals(
            HttpStatusCode.Unauthorized,
            oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport").status,
        )

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
            bearerAuth(gyldigToken)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `ugyldig og ukjent sak gir feil og manglende dokumenter gir 204 eller 404`() =
        testApplicationWithDatabase { db ->
            val sak = godkjentSak(db)
            val oppsett = oppsett(db)

            val ugyldig = oppsett.client.get("/api/saksbehandling/v1/saker/ikke-en-uuid/sluttrapport") {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.BadRequest, ugyldig.status)

            val ukjent = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${UUID.randomUUID()}/refusjon",
            ) {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.NotFound, ukjent.status)

            val sluttrapportMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NoContent, sluttrapportMetadata.status)

            val refusjonMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NoContent, refusjonMetadata.status)

            val manglendeSluttrapport =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport/fil") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NotFound, manglendeSluttrapport.status)

            val manglendeRefusjonsvedlegg = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/${UUID.randomUUID()}",
            ) {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.NotFound, manglendeRefusjonsvedlegg.status)
        }

    @Test
    fun `andre og manglende enheter gir 403 for metadata og filer for begge dokumenttypene`() =
        testApplicationWithDatabase { db ->
            val annenEnhetSak = godkjentSak(db, behandlendeEnhet = annenEnhet)
            val utenEnhetSak = godkjentSak(db, behandlendeEnhet = null)
            val vedleggDb = VedleggDb(db.config.jdbcDatabase)
            val refusjonDb = RefusjonDb(db.config.jdbcDatabase)
            val vedleggPerSak = listOf(annenEnhetSak, utenEnhetSak).associate { sak ->
                vedleggDb.lagreSluttrapport(sak.soknadId, "sluttrapport.pdf", sluttrapportPdf)
                refusjonDb.lagreRefusjonskrav(
                    sak.soknadId,
                    100_00,
                    "utgifter",
                    listOf(RefusjonsfilInput("kvittering.pdf", refusjonPdf)),
                )
                sak.sakId to refusjonDb.finnRefusjonskravStatusForSak(sak.sakId)!!.vedlegg.single().id
            }
            val oppsett = oppsett(db)

            val ruter: (UUID) -> List<String> = { sakId ->
                listOf(
                    "/api/saksbehandling/v1/saker/$sakId/sluttrapport",
                    "/api/saksbehandling/v1/saker/$sakId/sluttrapport/fil",
                    "/api/saksbehandling/v1/saker/$sakId/refusjon",
                    "/api/saksbehandling/v1/saker/$sakId/refusjon/vedlegg/${vedleggPerSak.getValue(sakId)}",
                )
            }

            (ruter(annenEnhetSak.sakId) + ruter(utenEnhetSak.sakId)).forEach { path ->
                val response = oppsett.client.get(path) { bearerAuth(gyldigToken) }
                assertEquals(HttpStatusCode.Forbidden, response.status)
            }
        }

    @Test
    fun `metadata velger nyeste innsending og eldre refusjonsvedlegg kan ikke lastes ned`() =
        testApplicationWithDatabase { db ->
            val sak = godkjentSak(db)
            val vedleggDb = VedleggDb(db.config.jdbcDatabase)
            val refusjonDb = RefusjonDb(db.config.jdbcDatabase)

            vedleggDb.lagreSluttrapport(sak.soknadId, "eldre-sluttrapport.pdf", sluttrapportPdf)
            val eldreRefusjonId = refusjonDb.lagreRefusjonskrav(
                soknadId = sak.soknadId,
                belopOre = 100_00,
                utgifter = "eldre utgift",
                filer = listOf(RefusjonsfilInput("eldre-refusjon.pdf", refusjonPdf)),
            )
            Thread.sleep(20)
            vedleggDb.lagreSluttrapport(sak.soknadId, "nyeste-sluttrapport.pdf", refusjonPdf)
            val nyesteRefusjonId = refusjonDb.lagreRefusjonskrav(
                soknadId = sak.soknadId,
                belopOre = 200_00,
                utgifter = "nyeste utgift",
                filer = listOf(RefusjonsfilInput("nyeste-refusjon.pdf", sluttrapportPdf)),
            )

            val oppsett = oppsett(db)
            val sluttrapportMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.OK, sluttrapportMetadata.status)
            assertNoStoreAndNosniff(sluttrapportMetadata)
            val sluttrapportStatus = sluttrapportMetadata.body<SluttrapportStatusDto>()
            assertEquals("nyeste-sluttrapport.pdf", sluttrapportStatus.filnavn)
            assertNotNull(sluttrapportStatus.lastetOpp)

            val refusjonMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.OK, refusjonMetadata.status)
            assertNoStoreAndNosniff(refusjonMetadata)
            val refusjonStatus = refusjonMetadata.body<RefusjonStatusDto>()
            assertEquals(200L, refusjonStatus.belopKroner)
            assertEquals("nyeste utgift", refusjonStatus.utgifter)
            assertEquals("nyeste-refusjon.pdf", refusjonStatus.vedlegg.single().filnavn)

            val eldreVedleggId = transaction(db.config.jdbcDatabase) {
                VedleggTable.select(VedleggTable.id)
                    .where { VedleggTable.refusjonskravId eq eldreRefusjonId }
                    .single()[VedleggTable.id].value
            }

            val nyesteVedleggId = refusjonStatus.vedlegg.single().id
            val nyesteNedlasting = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/$nyesteVedleggId",
            ) {
                bearerAuth(gyldigToken)
            }
            assertPdfResponse(nyesteNedlasting, "attachment", "nyeste-refusjon.pdf", sluttrapportPdf)

            val eldreVedleggResponse = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/$eldreVedleggId",
            ) {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.NotFound, eldreVedleggResponse.status)

            val nyesteSluttrapportFil =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport/fil") {
                    bearerAuth(gyldigToken)
                }
            assertPdfResponse(nyesteSluttrapportFil, "attachment", "nyeste-sluttrapport.pdf", refusjonPdf)
        }

    @Test
    fun `legacy parent uten sakId og vedlegg fra annen sak blir ikke tilgjengelig`() =
        testApplicationWithDatabase { db ->
            val sak = godkjentSak(db)
            val annenSak = godkjentSak(db)
            val annenSaksVedleggId = RefusjonDb(db.config.jdbcDatabase).lagreRefusjonskrav(
                soknadId = annenSak.soknadId,
                belopOre = 123_00,
                utgifter = "annen sak",
                filer = listOf(RefusjonsfilInput("annen-sak.pdf", refusjonPdf)),
            )
            val legacyIds = lagreLegacyDokumenter(db, sak)
            val oppsett = oppsett(db)

            val sluttrapportMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NoContent, sluttrapportMetadata.status)

            val sluttrapportFil =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport/fil") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NotFound, sluttrapportFil.status)

            val refusjonMetadata =
                oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
                    bearerAuth(gyldigToken)
                }
            assertEquals(HttpStatusCode.NoContent, refusjonMetadata.status)

            val legacyRefusjonsvedlegg = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/${legacyIds.refusjonVedleggId}",
            ) {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.NotFound, legacyRefusjonsvedlegg.status)

            val annenSaksVedlegg = oppsett.client.get(
                "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/$annenSaksVedleggId",
            ) {
                bearerAuth(gyldigToken)
            }
            assertEquals(HttpStatusCode.NotFound, annenSaksVedlegg.status)
        }

    @Test
    fun `tilgangsmaskin avslag gir 403`() = testApplicationWithDatabase { db ->
        val sak = godkjentSak(db)
        val oppsett = oppsett(db) { call ->
            call.respondText(
                """{"title":"AVVIST","status":403,"begrunnelse":"ikke tilgjengelig","kanOverstyres":false}""",
                ContentType.parse("application/problem+json"),
                HttpStatusCode.Forbidden,
            )
        }

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport") {
            bearerAuth(gyldigToken)
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `tilgangsmaskin utilgjengelig gir 503`() = testApplicationWithDatabase { db ->
        val sak = godkjentSak(db)
        val oppsett = oppsett(db) { call -> call.respond(HttpStatusCode.InternalServerError) }

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
            bearerAuth(gyldigToken)
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    private suspend fun ApplicationTestBuilder.assertDokumentruterTilgjengelige(
        db: TestDatabase,
        grupper: String,
    ) {
        val sak = godkjentSak(db)
        VedleggDb(db.config.jdbcDatabase).lagreSluttrapport(
            sak.soknadId,
            "sluttrapport.pdf",
            sluttrapportPdf,
        )
        val refusjonskravId = RefusjonDb(db.config.jdbcDatabase).lagreRefusjonskrav(
            soknadId = sak.soknadId,
            belopOre = 543_00,
            utgifter = "reiseutgifter",
            filer = listOf(RefusjonsfilInput("kvittering.pdf", refusjonPdf)),
        )
        val oppsett = oppsett(db, grupper = grupper)

        val sluttrapportMetadata =
            oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport") {
                bearerAuth(gyldigToken)
            }
        assertEquals(HttpStatusCode.OK, sluttrapportMetadata.status)
        assertNoStoreAndNosniff(sluttrapportMetadata)
        assertEquals("sluttrapport.pdf", sluttrapportMetadata.body<SluttrapportStatusDto>().filnavn)

        val sluttrapportFil =
            oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport/fil") {
                bearerAuth(gyldigToken)
            }
        assertPdfResponse(sluttrapportFil, "attachment", "sluttrapport.pdf", sluttrapportPdf)

        val refusjonMetadata =
            oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon") {
                bearerAuth(gyldigToken)
            }
        assertEquals(HttpStatusCode.OK, refusjonMetadata.status)
        assertNoStoreAndNosniff(refusjonMetadata)
        val refusjonStatus = refusjonMetadata.body<RefusjonStatusDto>()
        assertEquals(543L, refusjonStatus.belopKroner)
        assertEquals("reiseutgifter", refusjonStatus.utgifter)
        assertEquals("kvittering.pdf", refusjonStatus.vedlegg.single().filnavn)

        val vedleggId = refusjonStatus.vedlegg.single().id
        assertNotNull(refusjonskravId)
        val refusjonFil = oppsett.client.get(
            "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/$vedleggId",
        ) {
            bearerAuth(gyldigToken)
        }
        assertPdfResponse(refusjonFil, "attachment", "kvittering.pdf", refusjonPdf)

        val forhandsvisning = oppsett.client.get(
            "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/vedlegg/$vedleggId?visning=forhandsvisning",
        ) {
            bearerAuth(gyldigToken)
        }
        assertPdfResponse(forhandsvisning, "inline", "kvittering.pdf", refusjonPdf)

        val sluttrapportForhandsvisning =
            oppsett.client.get("/api/saksbehandling/v1/saker/${sak.sakId}/sluttrapport/fil?visning=forhandsvisning") {
                bearerAuth(gyldigToken)
            }
        assertPdfResponse(sluttrapportForhandsvisning, "inline", "sluttrapport.pdf", sluttrapportPdf)
    }

    private data class LegacyIds(val refusjonVedleggId: UUID)

    private fun lagreLegacyDokumenter(db: TestDatabase, sak: LagretSak): LegacyIds =
        transaction(db.config.jdbcDatabase) {
            val legacySluttrapportId = SluttrapportTable.insertReturning(listOf(SluttrapportTable.id)) {
                it[sakId] = null
                it[status] = "MOTTATT"
            }.single()[SluttrapportTable.id]
            VedleggTable.insert {
                it[soknadId] = sak.soknadId
                it[type] = VedleggType.SLUTTRAPPORT.name
                it[filnavn] = "legacy-sluttrapport.pdf"
                it[innhold] = sluttrapportPdf
                it[storrelse] = sluttrapportPdf.size
                it[sluttrapportId] = legacySluttrapportId
            }

            val legacyRefusjonskravId = RefusjonskravTable.insertReturning(
                listOf(RefusjonskravTable.id),
            ) {
                it[soknadId] = sak.soknadId
                it[sakId] = null
                it[belopOre] = 100_00
                it[utgifter] = "legacy refusjon"
                it[status] = "MOTTATT"
            }.single()[RefusjonskravTable.id].value
            val refusjonVedleggId = VedleggTable.insertReturning(listOf(VedleggTable.id)) {
                it[soknadId] = sak.soknadId
                it[type] = VedleggType.REFUSJONSDOKUMENTASJON.name
                it[filnavn] = "legacy-refusjon.pdf"
                it[innhold] = refusjonPdf
                it[storrelse] = refusjonPdf.size
                it[refusjonskravId] = legacyRefusjonskravId
            }.single()[VedleggTable.id].value

            LegacyIds(refusjonVedleggId)
        }

    private fun godkjentSak(db: TestDatabase, behandlendeEnhet: String? = egenEnhet): LagretSak {
        val sak = lagreSoknadOgSak(db.config.jdbcDatabase)
        transaction(db.config.jdbcDatabase) {
            SoknadTable.update({ SoknadTable.id eq sak.soknadId }) {
                it[status] = "godkjent"
            }
            SakTable.update({ SakTable.sakId eq sak.sakId }) {
                it[SakTable.behandlendeEnhet] = behandlendeEnhet
            }
        }
        return sak
    }

    private fun ApplicationTestBuilder.oppsett(
        db: TestDatabase,
        grupper: String = saksbehandlerGrupper,
        tilgangsmaskinSvar: suspend (ApplicationCall) -> Unit = {
            it.respond(HttpStatusCode.NoContent)
        },
    ): Oppsett {
        mockEntraProxyFull(
            enheterProvider = { egneEnheter },
            grupperProvider = { grupper },
        )
        externalServices {
            hosts(TilgangsmaskinClient.ingress) {
                routing {
                    post("/api/v1/kjerne") {
                        tilgangsmaskinSvar(call)
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        val audit = RecordingAuditLogger()

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<AzureAdTokenExchanger> {
                    object : AzureAdTokenExchanger {
                        override suspend fun exchange(target: String, userToken: String): TokenResponse {
                            return TokenResponse.Success("obo-token", 3600)
                        }
                    }
                }
                provide<HttpClient> { client }
                provide<Database> { db.config.jdbcDatabase }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide(TilgangsmaskinClient::class)
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = audit) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == gyldigToken) mockAzureAdIntrospectionResponse.withNavIdent(navIdent) else null
                    }
                }
            }

            configureAuthentication()
            configureSakDokumentApiV1()
            configureServer()
        }
        return Oppsett(client)
    }

    private class Oppsett(val client: HttpClient)

    private fun assertNoStoreAndNosniff(response: HttpResponse) {
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
    }

    private suspend fun assertPdfResponse(
        response: HttpResponse,
        disposition: String,
        filnavn: String,
        forventetInnhold: ByteArray,
    ) {
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Pdf.toString(), response.headers[HttpHeaders.ContentType])
        assertNoStoreAndNosniff(response)
        val contentDisposition = assertNotNull(response.headers[HttpHeaders.ContentDisposition])
        assertTrue(contentDisposition.startsWith("$disposition;", ignoreCase = true))
        assertTrue(contentDisposition.contains(filnavn))
        assertTrue(response.bodyAsBytes().contentEquals(forventetInnhold))
    }
}
