package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatus
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import no.nav.ekspertbistand.vedlegg.registrerSluttrapport
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class SluttrapportVurderingApiTest {

    private val navIdent = "A123456"
    private val annenSaksbehandler = "Z999999"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"

    private val saksbehandlerGrupper = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]"""
    private val beslutterGrupper = """[{ "rolle": "${Role.BESLUTTER.groupId}" }]"""

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private fun url(sakId: Any, sluttrapportId: Any) =
        "/api/saksbehandling/v1/saker/$sakId/sluttrapport/$sluttrapportId/vurdering"

    private val bekreft = """{ "utfall": "BEKREFTET" }"""
    private val krevNy = """{ "utfall": "NY_INNSENDING_KREVES", "begrunnelse": "  Mangler signatur  " }"""

    private data class Lagret(val sakId: UUID, val soknadId: UUID, val sluttrapportId: UUID)

    // Tilgang

    @Test
    fun `uten token gir 401`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)

        val response = oppsett.client.post(url(sak.sakId, sak.sluttrapportId)) { jsonBody(bekreft) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `beslutter uten saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.send(url(UUID.randomUUID(), UUID.randomUUID()), bekreft)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `ugyldig sluttrapportId gir 400`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, "ikke-en-uuid"), bekreft)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `sak på annen enhet gir 403`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `avvist i tilgangsmaskinen gir 403`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.Forbidden, avvistBody) })

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `tilgangsmaskinen feiler gir 503`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `sak tildelt en annen eller ingen gir 403`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        listOf(annenSaksbehandler, null).forEach { ident ->
            val sak = lagreSakMedSluttrapport(db, saksbehandlerIdent = ident)

            val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(IKKE_TILDELT_SAK, response.body<TilgangAvvistResponse>().kode)
            assertIngenEndring(db, sak)
        }
    }

    @Test
    fun `Arena-sak gir 409 SAK_UGYLDIG_KILDE`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db, kilde = KildeTilBehandling.ARENA)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SAK_UGYLDIG_KILDE, response.kode())
        assertIngenEndring(db, sak)
    }

    // Validering

    @Test
    fun `ugyldig body gir 400`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)
        listOf(
            """{ "utfall": "GODKJENT" }""",
            """{ "utfall": "NY_INNSENDING_KREVES" }""",
            """{ "utfall": "NY_INNSENDING_KREVES", "begrunnelse": "   " }""",
            """{ "utfall": "NY_INNSENDING_KREVES", "begrunnelse": "${"a".repeat(MAKS_BEGRUNNELSE_SLUTTRAPPORT + 1)}" }""",
            """{ "utfall": "BEKREFTET", "begrunnelse": "Skal ikke være med" }""",
        ).forEach { body ->
            val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), body)

            assertEquals(HttpStatusCode.BadRequest, response.status, body.take(80))
        }
        assertIngenEndring(db, sak)
    }

    // Tilstand

    @Test
    fun `sak som ikke er innvilget gir 409 SAK_UGYLDIG_STATUS`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db, status = Saksstatus.AVSLUTTET)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SAK_UGYLDIG_STATUS, response.kode())
        assertIngenEndring(db, sak, forventetSakstatus = Saksstatus.AVSLUTTET)
    }

    @Test
    fun `sluttrapport på en annen sak gir 404`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val annen = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, annen.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertIngenEndring(db, sak)
        assertIngenEndring(db, annen)
    }

    @Test
    fun `eldre sluttrapport gir 409 SLUTTRAPPORT_IKKE_NYESTE`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        settSluttrapportStatus(db, sak.sluttrapportId, SluttrapportStatus.NY_INNSENDING_KREVES)
        val nyeste = lagreSluttrapport(db, sak.sakId, sak.soknadId)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SLUTTRAPPORT_IKKE_NYESTE, response.kode())
        assertIngenEndring(db, sak.copy(sluttrapportId = nyeste))
    }

    @Test
    fun `allerede vurdert sluttrapport gir 409 SLUTTRAPPORT_ALLEREDE_VURDERT`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        settSluttrapportStatus(db, sak.sluttrapportId, SluttrapportStatus.BEKREFTET)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), krevNy)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SLUTTRAPPORT_ALLEREDE_VURDERT, response.kode())
        assertIngenVurderingEvent(db, sak)
    }

    // Publisering

    @Test
    fun `bekreftet gir 202 og publiserer SluttrapportBekreftet`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), bekreft)

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = hentEvents(db).filterIsInstance<EventData.SluttrapportBekreftet>().single()
        assertEquals(sak.sluttrapportId.toString(), event.sluttrapportId)
        assertEquals(sak.sakId.toString(), event.sakId)
        assertEquals(sak.soknadId.toString(), event.soknadId)
        assertEquals(navIdent, event.vurdertAvIdent)
        assertEquals("Tore Tang", event.vurdertAvNavn)
        assertIngenEndring(db, sak)
    }

    @Test
    fun `ny innsending kreves gir 202 og publiserer trimmet begrunnelse`() = testApplicationWithDatabase { db ->
        val sak = lagreSakMedSluttrapport(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sak.sakId, sak.sluttrapportId), krevNy)

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = hentEvents(db).filterIsInstance<EventData.SluttrapportNyInnsendingKrevd>().single()
        assertEquals(sak.sluttrapportId.toString(), event.sluttrapportId)
        assertEquals("Mangler signatur", event.begrunnelse)
        assertEquals(navIdent, event.vurdertAvIdent)
        assertEquals("Tore Tang", event.vurdertAvNavn)
        assertIngenEndring(db, sak)
    }

    private val avvistBody = """{ "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE", "status": 403, "brukerIdent": "$fnr", "navIdent": "$navIdent", "begrunnelse": "Nei", "kanOverstyres": false }"""

    private fun HttpRequestBuilder.jsonBody(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.kode(): String? =
        Json.parseToJsonElement(bodyAsText()).jsonObject["kode"]?.jsonPrimitive?.content

    private class Oppsett(
        val client: HttpClient,
        private val token: String,
        private val tilgangsmaskinTeller: () -> Int,
    ) {
        val tilgangsmaskinKall get() = tilgangsmaskinTeller()

        suspend fun send(url: String, body: String): HttpResponse = client.post(url) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private fun ApplicationTestBuilder.oppsett(
        db: TestDatabase,
        grupper: String = saksbehandlerGrupper,
        tilgangsmaskinSvar: suspend (io.ktor.server.application.ApplicationCall) -> Unit = {
            it.respond(HttpStatusCode.NoContent)
        },
    ): Oppsett {
        var tilgangsmaskinKall = 0

        mockEntraProxyFull(
            enheterProvider = { egneEnheter },
            grupperProvider = { grupper },
        )
        externalServices {
            hosts(TilgangsmaskinClient.ingress) {
                routing {
                    post("/api/v1/kjerne") {
                        tilgangsmaskinKall++
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
                        override suspend fun exchange(target: String, userToken: String) =
                            TokenResponse.Success("obo-token", 3600)
                    }
                }
                provide<HttpClient> { client }
                provide<Database> { db.config.jdbcDatabase }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide(TilgangsmaskinClient::class)
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = NoopAuditLogger) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == gyldigToken) mockAzureAdIntrospectionResponse.withNavIdent(navIdent) else null
                    }
                }
            }

            configureAuthentication()
            configureSluttrapportVurderingApiV1()
            configureServer()
        }

        return Oppsett(client, gyldigToken) { tilgangsmaskinKall }
    }

    private object NoopAuditLogger : no.nav.common.audit_log.log.AuditLogger {
        override fun log(message: no.nav.common.audit_log.cef.CefMessage) = Unit
        override fun log(message: String) = Unit
    }

    private fun lagreSoknad(db: TestDatabase): UUID {
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
                it[ansattNavn] = "Ansatt NN"
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
                it[opprettetAv] = "98765432109"
                it[status] = SoknadStatus.innsendt.name
            }
        }
        return soknadId
    }

    /** Innvilget sak, tildelt innlogget, med én sluttrapport som venter på vurdering. */
    private fun lagreSakMedSluttrapport(
        db: TestDatabase,
        saksbehandlerIdent: String? = navIdent,
        behandlendeEnhet: String? = egenEnhet,
        status: Saksstatus = Saksstatus.INNVILGET,
        kilde: KildeTilBehandling = KildeTilBehandling.EKSPERTBISTAND,
    ): Lagret {
        val soknadId = lagreSoknad(db)
        val sakId = transaction(db.config.jdbcDatabase) {
            SakTable.insert {
                it[this.soknadId] = soknadId
                it[this.status] = status.name
                it[kildeTilBehandling] = kilde.name
                it[saksnummer] = nesteSaksnummer()
                it[this.behandlendeEnhet] = behandlendeEnhet
                it[this.saksbehandlerIdent] = saksbehandlerIdent
                it[saksbehandlerNavn] = saksbehandlerIdent?.let { "Navn $it" }
            }[SakTable.sakId]
        }
        return Lagret(sakId, soknadId, lagreSluttrapport(db, sakId, soknadId))
    }

    private fun lagreSluttrapport(db: TestDatabase, sakId: UUID, soknadId: UUID): UUID =
        transaction(db.config.jdbcDatabase) {
            registrerSluttrapport(sakId, soknadId, "123456780", "98765432109", "sluttrapport.pdf", byteArrayOf(1))
            SluttrapportTable.selectAll()
                .where { SluttrapportTable.sakId eq sakId }
                .orderBy(SluttrapportTable.opprettet, SortOrder.DESC)
                .first()[SluttrapportTable.id]
        }

    private fun settSluttrapportStatus(db: TestDatabase, sluttrapportId: UUID, status: SluttrapportStatus) =
        transaction(db.config.jdbcDatabase) {
            SluttrapportTable.update({ SluttrapportTable.id eq sluttrapportId }) { it[this.status] = status.name }
        }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }

    /** Lagringen gjøres av handleren, så endepunktet skal ikke endre `sak` eller `sluttrapport`. */
    private fun assertIngenEndring(
        db: TestDatabase,
        sak: Lagret,
        forventetSakstatus: Saksstatus = Saksstatus.INNVILGET,
    ) {
        transaction(db.config.jdbcDatabase) {
            assertEquals(
                forventetSakstatus.name,
                SakTable.selectAll().where { SakTable.sakId eq sak.sakId }.single()[SakTable.status],
            )
            val rad = SluttrapportTable.selectAll().where { SluttrapportTable.id eq sak.sluttrapportId }.single()
            assertEquals(SluttrapportStatus.MOTTATT.name, rad[SluttrapportTable.status])
            assertNull(rad[SluttrapportTable.vurdertAvIdent])
            assertNull(rad[SluttrapportTable.begrunnelse])
        }
    }

    private fun assertIngenVurderingEvent(db: TestDatabase, sak: Lagret) =
        assertTrue(
            hentEvents(db).none {
                (it is EventData.SluttrapportBekreftet && it.sakId == sak.sakId.toString()) ||
                    (it is EventData.SluttrapportNyInnsendingKrevd && it.sakId == sak.sakId.toString())
            }
        )
}
