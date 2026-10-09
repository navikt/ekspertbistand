package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.handlers.OpprettSak.Companion.opprettVilkarForSak
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger
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
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class VilkarsvurderingApiTest {

    private val navIdent = "A123456"
    private val annenSaksbehandler = "Z999999"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"

    private val saksbehandlerGrupper = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]"""
    private val beslutterGrupper = """[{ "rolle": "${Role.BESLUTTER.groupId}" }]"""
    private val ingenGrupper = "[]"

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private fun url(sakId: Any) = "/api/saksbehandling/v1/saker/$sakId/vilkarsvurdering"

    private class RecordingAuditLogger : AuditLogger {
        val meldinger = mutableListOf<CefMessage>()
        override fun log(message: CefMessage) {
            meldinger.add(message)
        }

        override fun log(message: String) = error("ikke i bruk")
    }

    // GET

    @Test
    fun `hent uten token gir 401`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get(url(UUID.randomUUID()))

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `hent uten rolle gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db)
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
    }

    @Test
    fun `hent med ugyldig sakId gir 400`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get(url("ikke-en-uuid")) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `hent for ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get(url(UUID.randomUUID())) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `hent for sak på annen enhet gir 403 uten kall mot tilgangsmaskin`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `hent gir 403 når tilgangsmaskin avviser`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = avvistAvTilgangsmaskin)

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("AVVIST_STRENGT_FORTROLIG_ADRESSE", response.body<TilgangAvvistResponse>().kode)
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `hent gir 503 når tilgangsmaskin feiler`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `hent returnerer alle vilkår med vurdering og sporingslogger`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db)
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.update({
                (SaksvilkarTable.sakId eq sakId) and (SaksvilkarTable.vilkarId eq Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD.name)
            }) {
                it[godkjent] = true
                it[notat] = "Bekreftet i Aa-registeret"
                it[vurdertAvIdent] = navIdent
            }
        }
        val oppsett = oppsett(db)

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val vurderinger = response.body<List<VilkarsvurderingDTO>>()
        assertEquals(Vilkar.entries, vurderinger.map { it.vilkar })

        val vurdert = vurderinger.single { it.vilkar == Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD }
        assertEquals(true, vurdert.godkjent)
        assertEquals("Bekreftet i Aa-registeret", vurdert.notat)
        assertEquals(navIdent, vurdert.vurdertAvIdent)
        assertTrue(vurderinger.filter { it.vilkar != Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD }.all { it.godkjent == null })

        val cef = oppsett.audit.meldinger.single().toString()
        assertTrue(cef.contains("suid=$navIdent"), cef)
        assertTrue(cef.contains("duid=$fnr"), cef)
    }

    @Test
    fun `beslutter kan hente vilkårsvurdering`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        val response = oppsett.client.get(url(sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // PATCH

    @Test
    fun `oppdater uten token gir 401`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) { jsonBody(godkjentRequest) }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `beslutter uten saksbehandlerrolle kan ikke oppdatere`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater for ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(UUID.randomUUID())) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `oppdater for sak på annen enhet gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater gir 403 når tilgangsmaskin avviser`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db, tilgangsmaskinSvar = avvistAvTilgangsmaskin)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater når saken er tildelt en annen gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = annenSaksbehandler)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILDELT_SAK, response.body<TilgangAvvistResponse>().kode)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater når saken ikke er tildelt gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = null)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILDELT_SAK, response.body<TilgangAvvistResponse>().kode)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater med ukjent vilkår gir 400`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody("""{ "vilkar": "FINNES_IKKE", "godkjent": true }""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertIngenEndring(db, sakId)
        assertEquals(Vilkar.entries.size, antallVilkarRader(db, sakId))
    }

    @Test
    fun `oppdater med ugyldig body gir 400`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody("""{ "godkjent": "ja" }""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater når saken ikke er under behandling gir 409`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent, status = Saksstatus.TIL_BESLUTNING)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `oppdater legger ikke til vilkår som mangler på saken`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.deleteWhere {
                (SaksvilkarTable.sakId eq sakId) and (vilkarId eq Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD.name)
            }
        }
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(Vilkar.entries.size - 1, antallVilkarRader(db, sakId))
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `oppdater lagrer vurderingen, publiserer event og sporingslogger`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        val sakId = lagreSak(db, soknadId = soknadId, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody("""{ "vilkar": "DELTAKER_HAR_ARBEIDSFORHOLD", "godkjent": true, "notat": "  Bekreftet  " }""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val dto = response.body<VilkarsvurderingDTO>()
        assertEquals(Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD, dto.vilkar)
        assertEquals(true, dto.godkjent)
        assertEquals("Bekreftet", dto.notat)
        assertEquals(navIdent, dto.vurdertAvIdent)
        assertNotNull(dto.vurdertTidspunkt)

        val rad = hentVilkarRad(db, sakId, Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD)
        assertEquals(true, rad.godkjent)
        assertEquals("Bekreftet", rad.notat)
        assertEquals(navIdent, rad.vurdertAvIdent)
        // Postgres lagrer med mikrosekundpresisjon, mens svaret har full presisjon fra klokka.
        val diff = (dto.vurdertTidspunkt!! - rad.vurdertTidspunkt!!).absoluteValue
        assertTrue(diff < 1.milliseconds, "vurdertTidspunkt i svar og database skal være likt, var $diff fra hverandre")
        assertTrue(
            Vilkar.entries.filter { it != Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD }
                .all { hentVilkarRad(db, sakId, it).vurdertAvIdent == null },
            "andre vilkår skal ikke endres",
        )

        val event = assertIs<EventData.VilkarsvurderingOppdatert>(hentEvents(db).single())
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(soknadId.toString(), event.soknadId)
        assertEquals(VilkarsvurderingRequest(Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD, true, "Bekreftet"), event.vurdering)
        assertEquals(navIdent, event.vurdertAvIdent)

        val cef = oppsett.audit.meldinger.single().toString()
        assertTrue(cef.contains("suid=$navIdent"), cef)
        assertTrue(cef.contains("duid=$fnr"), cef)
    }

    @Test
    fun `oppdater med godkjent null nullstiller vurderingen`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody(godkjentRequest)
        }
        val response = oppsett.client.patch(url(sakId)) {
            bearerAuth(gyldigToken)
            jsonBody("""{ "vilkar": "DELTAKER_HAR_ARBEIDSFORHOLD", "godkjent": null, "notat": "" }""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val rad = hentVilkarRad(db, sakId, Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD)
        assertNull(rad.godkjent)
        assertNull(rad.notat)
        assertEquals(2, hentEvents(db).size)
    }

    private val godkjentRequest = """{ "vilkar": "DELTAKER_HAR_ARBEIDSFORHOLD", "godkjent": true }"""

    private val avvistAvTilgangsmaskin: suspend (io.ktor.server.application.ApplicationCall) -> Unit = { call ->
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
    }

    private fun HttpRequestBuilder.jsonBody(body: String) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private class Oppsett(
        val client: HttpClient,
        val audit: RecordingAuditLogger,
        private val tilgangsmaskinTeller: () -> Int,
    ) {
        val tilgangsmaskinKall get() = tilgangsmaskinTeller()
    }

    private fun ApplicationTestBuilder.oppsett(
        db: TestDatabase,
        grupper: String = saksbehandlerGrupper,
        tilgangsmaskinSvar: suspend (io.ktor.server.application.ApplicationCall) -> Unit = {
            it.respond(HttpStatusCode.NoContent)
        },
    ): Oppsett {
        val audit = RecordingAuditLogger()
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
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = audit) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == gyldigToken) mockAzureAdIntrospectionResponse.withNavIdent(navIdent) else null
                    }
                }
            }

            configureAuthentication()
            configureVilkarsvurderingApiV1()
            configureServer()
        }

        return Oppsett(client, audit) { tilgangsmaskinKall }
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

    /** Lagrer en sak med alle vilkår, slik [no.nav.ekspertbistand.event.projections.SakProjection] gjør. */
    private fun lagreSak(
        db: TestDatabase,
        soknadId: UUID = lagreSoknad(db),
        saksbehandlerIdent: String? = null,
        behandlendeEnhet: String? = egenEnhet,
        status: Saksstatus = Saksstatus.UNDER_BEHANDLING,
    ): UUID =
        transaction(db.config.jdbcDatabase) {
            val sakId = SakTable.insert {
                it[this.soknadId] = soknadId
                it[this.status] = status.name
                it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
                it[this.behandlendeEnhet] = behandlendeEnhet
                it[this.saksbehandlerIdent] = saksbehandlerIdent
                it[saksbehandlerNavn] = saksbehandlerIdent?.let { "Navn $it" }
            }[SakTable.sakId]
            opprettVilkarForSak(sakId)
            sakId
        }

    private fun hentVilkarRad(db: TestDatabase, sakId: UUID, vilkar: Vilkar): VilkarsvurderingDTO =
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.selectAll()
                .where { (SaksvilkarTable.sakId eq sakId) and (SaksvilkarTable.vilkarId eq vilkar.name) }
                .single()
                .let {
                    VilkarsvurderingDTO(
                        vilkar = vilkar,
                        godkjent = it[SaksvilkarTable.godkjent],
                        notat = it[SaksvilkarTable.notat],
                        vurdertAvIdent = it[SaksvilkarTable.vurdertAvIdent],
                        vurdertTidspunkt = it[SaksvilkarTable.vurdertTidspunkt],
                    )
                }
        }

    private fun antallVilkarRader(db: TestDatabase, sakId: UUID): Int =
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.selectAll().where { SaksvilkarTable.sakId eq sakId }.count().toInt()
        }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }

    private fun assertIngenEndring(db: TestDatabase, sakId: UUID) {
        assertTrue(
            Vilkar.entries.all { hentVilkarRad(db, sakId, it).vurdertAvIdent == null },
            "ingen vilkår skal være vurdert",
        )
        assertTrue(hentEvents(db).isEmpty(), "ingen event skal publiseres")
    }
}
