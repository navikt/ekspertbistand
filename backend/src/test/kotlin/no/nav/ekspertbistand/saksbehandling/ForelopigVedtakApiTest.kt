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
import no.nav.ekspertbistand.event.handlers.OpprettSak.Companion.opprettVilkarForSak
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ForelopigVedtakApiTest {

    private val navIdent = "A123456"
    private val annenSaksbehandler = "Z999999"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"

    private val saksbehandlerGrupper = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]"""
    private val beslutterGrupper = """[{ "rolle": "${Role.BESLUTTER.groupId}" }]"""

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private val sats = gjeldendeMaksSats(LocalDate(2026, 11, 22))!!.belop

    private fun url(sakId: Any) = "/api/saksbehandling/v1/saker/$sakId/forelopig-vedtak"

    private val innvilge = """
        { "utfall": "INNVILGE", "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31",
          "begrunnelseDeltaker": "  Begrunnelse til deltaker  " }
    """.trimIndent()

    private fun redusert(belop: Int, fra: String = "2026-11-22", til: String = "2026-12-31") = """
        { "utfall": "INNVILGE_REDUSERT", "innvilgetBelop": $belop,
          "tiltaksperiodeFra": "$fra", "tiltaksperiodeTil": "$til",
          "begrunnelseDeltaker": "Til deltaker", "begrunnelseArbeidsgiver": "Til arbeidsgiver" }
    """.trimIndent()

    private val avslaa = """
        { "utfall": "AVSLAA", "begrunnelseDeltaker": "Til deltaker", "begrunnelseArbeidsgiver": "Til arbeidsgiver" }
    """.trimIndent()

    // Tilgang

    @Test
    fun `uten token gir 401`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.client.post(url(sakId)) { jsonBody(innvilge) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `beslutter uten saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.send(url(UUID.randomUUID()), innvilge)

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `sak på annen enhet gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `avvist i tilgangsmaskinen gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.Forbidden, avvistBody) })

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `tilgangsmaskinen feiler gir 503`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `sak tildelt en annen eller ingen gir 403`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        listOf(annenSaksbehandler, null).forEach { ident ->
            val sakId = lagreVurdertSak(db, saksbehandlerIdent = ident)

            val response = oppsett.send(url(sakId), innvilge)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(IKKE_TILDELT_SAK, response.body<TilgangAvvistResponse>().kode)
            assertIngenEndring(db, sakId)
        }
    }

    @Test
    fun `Arena-sak gir 409 SAK_UGYLDIG_KILDE`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, kilde = KildeTilBehandling.ARENA)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SAK_UGYLDIG_KILDE, response.kode())
        assertIngenEndring(db, sakId)
    }

    // Publisering

    @Test
    fun `innvilge publiserer event med søknadssummen og ident fra token`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db, soknadssum = "9999")
        val sakId = lagreVurdertSak(db, soknadId = soknadId)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = assertIs<EventData.ForelopigVedtakSendtTilBeslutter>(hentEvents(db).single())
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(soknadId.toString(), event.soknadId)
        assertEquals(ForelopigUtfall.INNVILGE, event.utfall)
        assertEquals(9999, event.innvilgetBelop)
        assertEquals(LocalDate(2026, 11, 22), event.tiltaksperiodeFra)
        assertEquals(LocalDate(2026, 12, 31), event.tiltaksperiodeTil)
        assertEquals("Begrunnelse til deltaker", event.begrunnelseDeltaker)
        assertNull(event.begrunnelseArbeidsgiver)
        assertEquals(navIdent, event.innstiltAvIdent)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `innvilge redusert publiserer event med beløpet`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), redusert(5000))

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = assertIs<EventData.ForelopigVedtakSendtTilBeslutter>(hentEvents(db).single())
        assertEquals(ForelopigUtfall.INNVILGE_REDUSERT, event.utfall)
        assertEquals(5000, event.innvilgetBelop)
        assertEquals("Til arbeidsgiver", event.begrunnelseArbeidsgiver)
    }

    @Test
    fun `avslå publiserer event uten beløp og periode`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), avslaa)

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = assertIs<EventData.ForelopigVedtakSendtTilBeslutter>(hentEvents(db).single())
        assertEquals(ForelopigUtfall.AVSLAA, event.utfall)
        assertNull(event.innvilgetBelop)
        assertNull(event.tiltaksperiodeFra)
        assertNull(event.tiltaksperiodeTil)
    }

    @Test
    fun `innvilge når ett vilkår ikke er oppfylt gir 202`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        vurder(db, sakId, Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD, false)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    @Test
    fun `innvilge med søknadssum lik satsen gir 202`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, soknadId = lagreSoknad(db, soknadssum = "$sats"))
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(sats, assertIs<EventData.ForelopigVedtakSendtTilBeslutter>(hentEvents(db).single()).innvilgetBelop)
    }

    // Feltregler (400)

    @Test
    fun `brudd på feltreglene gir 400`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        val ugyldige = mapOf(
            "blank begrunnelse til deltaker" to """{ "utfall": "AVSLAA", "begrunnelseDeltaker": "  ", "begrunnelseArbeidsgiver": "x" }""",
            "innvilge uten periode" to """{ "utfall": "INNVILGE", "begrunnelseDeltaker": "x" }""",
            "innvilge med beløp" to """{ "utfall": "INNVILGE", "innvilgetBelop": 100, "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31", "begrunnelseDeltaker": "x" }""",
            "innvilge med begrunnelse til arbeidsgiver" to """{ "utfall": "INNVILGE", "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31", "begrunnelseDeltaker": "x", "begrunnelseArbeidsgiver": "y" }""",
            "redusert uten beløp" to """{ "utfall": "INNVILGE_REDUSERT", "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31", "begrunnelseDeltaker": "x", "begrunnelseArbeidsgiver": "y" }""",
            "redusert uten begrunnelse til arbeidsgiver" to """{ "utfall": "INNVILGE_REDUSERT", "innvilgetBelop": 100, "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31", "begrunnelseDeltaker": "x" }""",
            "redusert uten periode" to """{ "utfall": "INNVILGE_REDUSERT", "innvilgetBelop": 100, "begrunnelseDeltaker": "x", "begrunnelseArbeidsgiver": "y" }""",
            "redusert med beløp 0" to redusert(0),
            "redusert over satsen" to redusert(sats + 1),
            "avslå uten begrunnelse til arbeidsgiver" to """{ "utfall": "AVSLAA", "begrunnelseDeltaker": "x" }""",
            "avslå med beløp" to """{ "utfall": "AVSLAA", "innvilgetBelop": 100, "begrunnelseDeltaker": "x", "begrunnelseArbeidsgiver": "y" }""",
            "avslå med periode" to """{ "utfall": "AVSLAA", "tiltaksperiodeFra": "2026-11-22", "tiltaksperiodeTil": "2026-12-31", "begrunnelseDeltaker": "x", "begrunnelseArbeidsgiver": "y" }""",
            "sluttdato før startdato" to redusert(100, fra = "2026-12-31", til = "2026-11-22"),
            "periode over årsskiftet" to redusert(100, fra = "2026-12-31", til = "2027-01-01"),
            "startdato før første sats" to redusert(100, fra = "2025-11-22", til = "2025-12-31"),
            "tekst som bryter valider" to """{ "utfall": "AVSLAA", "begrunnelseDeltaker": "<script>", "begrunnelseArbeidsgiver": "y" }""",
            "ukjent utfall" to """{ "utfall": "KANSKJE", "begrunnelseDeltaker": "x" }""",
        )

        ugyldige.forEach { (navn, body) ->
            val sakId = lagreVurdertSak(db)
            val response = oppsett.send(url(sakId), body)

            assertEquals(HttpStatusCode.BadRequest, response.status, navn)
            assertIngenEndring(db, sakId)
            assertIngenEvent(db, sakId)
        }
    }

    @Test
    fun `startdato før første sats gir 400 på tiltaksperiodeFra`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), redusert(100, fra = "2025-11-22", til = "2025-12-31"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("tiltaksperiodeFra"), response.bodyAsText())
    }

    @Test
    fun `redusert beløp lik søknadssummen gir 400`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, soknadId = lagreSoknad(db, soknadssum = "9999"))
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), redusert(9999))

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertIngenEndring(db, sakId)
    }

    // Tilstand (409)

    @Test
    fun `sak som ikke er under behandling gir 409 SAK_UGYLDIG_STATUS`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        Saksstatus.entries.filter { it != Saksstatus.UNDER_BEHANDLING }.forEach { status ->
            val sakId = lagreVurdertSak(db, status = status)

            val response = oppsett.send(url(sakId), innvilge)

            assertEquals(HttpStatusCode.Conflict, response.status, status.name)
            assertEquals(SAK_UGYLDIG_STATUS, response.kode())
            assertIngenEndring(db, sakId, forventetStatus = status)
            assertIngenEvent(db, sakId)
        }
    }

    @Test
    fun `ett vilkår ikke vurdert gir 409 VILKAR_IKKE_VURDERT`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db)
        vurder(db, sakId, Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD, null)
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), avslaa)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(VILKAR_IKKE_VURDERT, response.kode())
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `innvilge med søknadssum over satsen gir 409 BELOP_OVER_SATS`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, soknadId = lagreSoknad(db, soknadssum = "${sats + 1}"))
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(BELOP_OVER_SATS, response.kode())
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `søknadssum som ikke er et heltall gir 409 UGYLDIG_SOKNADSSUM`() = testApplicationWithDatabase { db ->
        val sakId = lagreVurdertSak(db, soknadId = lagreSoknad(db, soknadssum = "ca. 10 000"))
        val oppsett = oppsett(db)

        val response = oppsett.send(url(sakId), innvilge)

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(UGYLDIG_SOKNADSSUM, response.kode())
        assertIngenEndring(db, sakId)
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
            configureForelopigVedtakApiV1()
            configureServer()
        }

        return Oppsett(client, gyldigToken) { tilgangsmaskinKall }
    }

    private object NoopAuditLogger : no.nav.common.audit_log.log.AuditLogger {
        override fun log(message: no.nav.common.audit_log.cef.CefMessage) = Unit
        override fun log(message: String) = Unit
    }

    private fun lagreSoknad(db: TestDatabase, soknadssum: String = "9999"): UUID {
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
                it[behovForBistandEstimertKostnad] = soknadssum
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

    /** Sak under behandling, tildelt innlogget, med alle vilkår vurdert som oppfylt. */
    private fun lagreVurdertSak(
        db: TestDatabase,
        soknadId: UUID = lagreSoknad(db),
        saksbehandlerIdent: String? = navIdent,
        behandlendeEnhet: String? = egenEnhet,
        status: Saksstatus = Saksstatus.UNDER_BEHANDLING,
        kilde: KildeTilBehandling = KildeTilBehandling.EKSPERTBISTAND,
    ): UUID =
        transaction(db.config.jdbcDatabase) {
            val sakId = SakTable.insert {
                it[this.soknadId] = soknadId
                it[this.status] = status.name
                it[kildeTilBehandling] = kilde.name
                it[saksnummer] = nesteSaksnummer()
                it[this.behandlendeEnhet] = behandlendeEnhet
                it[this.saksbehandlerIdent] = saksbehandlerIdent
                it[saksbehandlerNavn] = saksbehandlerIdent?.let { "Navn $it" }
            }[SakTable.sakId]
            opprettVilkarForSak(sakId)
            SaksvilkarTable.update({ SaksvilkarTable.sakId eq sakId }) {
                it[godkjent] = true
                it[vurdertAvIdent] = navIdent
            }
            sakId
        }

    private fun vurder(db: TestDatabase, sakId: UUID, vilkar: Vilkar, godkjent: Boolean?) =
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.update({ (SaksvilkarTable.sakId eq sakId) and (SaksvilkarTable.vilkarId eq vilkar.name) }) {
                it[SaksvilkarTable.godkjent] = godkjent
            }
        }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }

    /** Lagringen gjøres av handleren, så endepunktet skal ikke endre `sak` eller `forelopig_vedtak`. */
    private fun assertIngenEndring(
        db: TestDatabase,
        sakId: UUID,
        forventetStatus: Saksstatus = Saksstatus.UNDER_BEHANDLING,
    ) {
        transaction(db.config.jdbcDatabase) {
            val sak = SakTable.selectAll().where { SakTable.sakId eq sakId }.single()
            assertEquals(forventetStatus.name, sak[SakTable.status])
            assertNull(sak[SakTable.foreslattUtfall])
            assertTrue(ForelopigVedtakTable.selectAll().where { ForelopigVedtakTable.sakId eq sakId }.empty())
        }
    }

    private fun assertIngenEvent(db: TestDatabase, sakId: UUID) =
        assertTrue(
            hentEvents(db).none { it is EventData.ForelopigVedtakSendtTilBeslutter && it.sakId == sakId.toString() }
        )
}
