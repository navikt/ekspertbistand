package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.sak.KildeTilBehandling
import no.nav.ekspertbistand.sak.SakTable
import no.nav.ekspertbistand.sak.Saksstatus
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaksbehandlingSoknadApiTest {

    private val navIdent = "A123456"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"
    private val innsenderIdent = "98765432109"

    private val saksbehandlerGrupper = """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]"""
    private val ingenGrupper = "[]"

    private class RecordingAuditLogger : AuditLogger {
        val meldinger = mutableListOf<CefMessage>()
        override fun log(message: CefMessage) {
            meldinger.add(message)
        }

        override fun log(message: String) = error("ikke i bruk")
    }

    private class TilgangsmaskinKall(val body: String, val authorization: String?)

    @Test
    fun `liste uten token gir 401`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `liste uten saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `liste returnerer søknader med og uten sak, uten fnr`() = testApplicationWithDatabase { db ->
        val utenSak = lagreSoknad(db, ansattNavn = "Uten Sak")
        val medSak = lagreSoknad(db, ansattNavn = "Med Sak")
        lagreSak(db, medSak, saksbehandlerIdent = "Z111111")
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val raw = response.bodyAsText()
        assertFalse(raw.contains(fnr), "listen skal ikke inneholde fnr")
        assertFalse(raw.contains(innsenderIdent), "listen skal ikke inneholde innsenders ident")

        val soknader = response.body<SoknaderResponse>().soknader
        assertEquals(listOf(medSak.toString(), utenSak.toString()), soknader.map { it.soknadId })

        val elementMedSak = soknader.first()
        assertEquals("Med Sak", elementMedSak.ansattNavn)
        assertEquals(SoknadStatus.innsendt, elementMedSak.soknadStatus)
        assertEquals("123456780", elementMedSak.virksomhet.virksomhetsnummer)
        val sak = assertNotNull(elementMedSak.sak)
        assertEquals(Saksstatus.UNDER_BEHANDLING, sak.status)
        assertEquals(KildeTilBehandling.ARENA, sak.kildeTilBehandling)
        assertEquals("Z111111", sak.saksbehandlerIdent)
        assertEquals("1234", sak.behandlendeEnhet)

        assertNull(soknader.last().sak)
        assertTrue(oppsett.audit.meldinger.isEmpty(), "listen skal ikke sporingslogges")
    }

    @Test
    fun `detalj med ugyldig soknadId gir 400`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader/ikke-en-uuid") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `detalj uten saksbehandlerrolle gir 403 uten kall mot tilgangsmaskin`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader/$soknadId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `detalj for ukjent søknad gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader/${UUID.randomUUID()}") {
            bearerAuth(gyldigToken)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
    }

    @Test
    fun `detalj med innvilget tilgang returnerer søknad og sporingslogger`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        lagreSak(db, soknadId)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.NoContent) })

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader/$soknadId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val raw = response.bodyAsText()
        assertFalse(raw.contains(innsenderIdent), "innsenders ident skal ikke returneres")

        val soknad = response.body<SoknadDetaljer>()
        assertEquals(soknadId.toString(), soknad.soknadId)
        assertEquals(fnr, soknad.ansatt.fnr)
        assertEquals("Langvarig skulderplage", soknad.behovForBistand.begrunnelse)
        assertNotNull(soknad.sak)

        val kall = oppsett.tilgangsmaskinKall.single()
        assertEquals("\"$fnr\"", kall.body, "tilgangsmaskin skal sjekke fnr fra databasen")
        assertEquals("Bearer obo-token", kall.authorization)
        assertEquals(listOf(gyldigToken), oppsett.veksledeTokens, "OBO skal bruke innkommende token")

        val cef = oppsett.audit.meldinger.single().toString()
        assertTrue(cef.contains("flexString1=Permit"), cef)
        assertTrue(cef.contains("suid=$navIdent"), cef)
        assertTrue(cef.contains("duid=$fnr"), cef)
    }

    @Test
    fun `detalj med avvist tilgang gir 403 uten personopplysninger og uten sporingslogg`() =
        testApplicationWithDatabase { db ->
            val soknadId = lagreSoknad(db)
            val oppsett = oppsett(db, tilgangsmaskinSvar = { call ->
                call.respondText(
                    """
                    {
                      "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE",
                      "status": 403,
                      "brukerIdent": "$fnr",
                      "navIdent": "$navIdent",
                      "begrunnelse": "Du har ikke tilgang til brukere med strengt fortrolig adresse",
                      "kanOverstyres": false
                    }
                    """.trimIndent(),
                    ContentType.parse("application/problem+json"),
                    HttpStatusCode.Forbidden,
                )
            })

            val response = oppsett.client.get("/api/saksbehandling/v1/soknader/$soknadId") { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val raw = response.bodyAsText()
            assertFalse(raw.contains(fnr), "avvisning skal ikke inneholde fnr")
            val avvist = response.body<TilgangAvvistResponse>()
            assertEquals("AVVIST_STRENGT_FORTROLIG_ADRESSE", avvist.kode)
            assertTrue(oppsett.audit.meldinger.isEmpty(), "avvist tilgang skal ikke sporingslogges")
        }

    @Test
    fun `detalj gir 503 når tilgangsmaskin feiler`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.client.get("/api/saksbehandling/v1/soknader/$soknadId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains(fnr))
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    private class Oppsett(
        val client: HttpClient,
        val audit: RecordingAuditLogger,
        val tilgangsmaskinKall: List<TilgangsmaskinKall>,
        val veksledeTokens: List<String>,
    )

    private fun ApplicationTestBuilder.oppsett(
        db: TestDatabase,
        grupper: String = saksbehandlerGrupper,
        tilgangsmaskinSvar: suspend (io.ktor.server.application.ApplicationCall) -> Unit = {
            it.respond(HttpStatusCode.NoContent)
        },
    ): Oppsett {
        val audit = RecordingAuditLogger()
        val tilgangsmaskinKall = mutableListOf<TilgangsmaskinKall>()
        val veksledeTokens = mutableListOf<String>()

        mockEntraProxyFull(
            ansattProvider = { "{}" },
            enheterProvider = { "[]" },
            grupperProvider = { grupper },
        )
        externalServices {
            hosts(TilgangsmaskinClient.ingress) {
                routing {
                    post("/api/v1/komplett") {
                        tilgangsmaskinKall.add(
                            TilgangsmaskinKall(
                                body = call.receiveText(),
                                authorization = call.request.headers[HttpHeaders.Authorization],
                            )
                        )
                        tilgangsmaskinSvar(call)
                    }
                }
            }
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<AzureAdTokenExchanger> {
                    object : AzureAdTokenExchanger {
                        override suspend fun exchange(target: String, userToken: String): TokenResponse {
                            veksledeTokens.add(userToken)
                            return TokenResponse.Success("obo-token", 3600)
                        }
                    }
                }
                provide<HttpClient> { client }
                provide<Database> { db.config.jdbcDatabase }
                provide(EntraProxyClient::class)
                provide(TilgangsmaskinClient::class)
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = audit) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == gyldigToken) mockAzureAdIntrospectionResponse.withNavIdent(navIdent) else null
                    }
                }
            }

            configureAuthentication()
            configureSaksbehandlingSoknadApiV1()
            configureServer()
        }

        return Oppsett(client, audit, tilgangsmaskinKall, veksledeTokens)
    }

    private fun lagreSoknad(db: TestDatabase, ansattNavn: String = "Ansatt NN"): UUID {
        val soknadId = UUID.randomUUID()
        transaction(db.config.jdbcDatabase) {
            SoknadTable.insert {
                it[id] = soknadId
                it[virksomhetsnummer] = "123456780"
                it[virksomhetsnavn] = "Eksempel Bedrift AS"
                it[kontaktpersonNavn] = "Kontaktperson NN"
                it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
                it[kontaktpersonTelefon] = "41519999"
                it[ansattFnr] = fnr
                it[this.ansattNavn] = ansattNavn
                it[ekspertNavn] = "Ekspert NN"
                it[ekspertVirksomhet] = "ErgoConsult AS"
                it[ekspertKompetanse] = "Ergoterapeut"
                it[behovForBistand] = "Arbeidsplassvurdering"
                it[behovForBistandBegrunnelse] = "Langvarig skulderplage"
                it[behovForBistandEstimertKostnad] = "9999"
                it[behovForBistandTimer] = "16"
                it[behovForBistandTilrettelegging] = "Høydejustert bord"
                it[behovForBistandStartdato] = LocalDate(2026, 11, 22)
                it[navKontaktPerson] = "Navkontaktperson NN"
                it[opprettetAv] = innsenderIdent
                it[status] = SoknadStatus.innsendt.name
            }
        }
        // Sikrer ulik opprettet_tidspunkt slik at sorteringen er deterministisk.
        Thread.sleep(5)
        return soknadId
    }

    private fun lagreSak(db: TestDatabase, soknadId: UUID, saksbehandlerIdent: String? = null) {
        transaction(db.config.jdbcDatabase) {
            SakTable.insert {
                it[this.soknadId] = soknadId
                it[status] = Saksstatus.UNDER_BEHANDLING.name
                it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
                it[behandlendeEnhet] = "1234"
                it[this.saksbehandlerIdent] = saksbehandlerIdent
            }
        }
    }
}
