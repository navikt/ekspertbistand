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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BeslutterApiTest {

    private val navIdent = "B123456"
    private val saksbehandler = "Z123456"
    private val annenBeslutter = "B999999"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"

    private val beslutterGrupper = """[{ "rolle": "${Role.BESLUTTER.groupId}" }]"""
    private val saksbehandlerGrupper = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]"""

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private fun vedtakUrl(sakId: Any) = "/api/saksbehandling/v1/saker/$sakId/vedtak"
    private fun returUrl(sakId: Any) = "/api/saksbehandling/v1/saker/$sakId/retur"

    private fun vedtak(versjon: Int = 1) = """{ "forelopigVedtakVersjon": $versjon }"""

    private fun retur(
        versjon: Int = 1,
        aarsaker: String = """["FEIL_FAKTA", "ANNET"]""",
        forklaring: String = "  Sjekk perioden  ",
    ) = """{ "forelopigVedtakVersjon": $versjon, "aarsaker": $aarsaker, "forklaring": ${Json.encodeToString(forklaring)} }"""

    // Vellykket

    @Test
    fun `vedtak gir 202 og publiserer VedtakFattet uten å endre saken`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, utfall = ForelopigUtfall.INNVILGE_REDUSERT)
        val oppsett = oppsett(db)

        val response = oppsett.send(vedtakUrl(sakId), vedtak())

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = assertIs<EventData.VedtakFattet>(hentEvents(db).single())
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(1, event.forelopigVedtakVersjon)
        assertEquals(Saksstatus.INNVILGET, event.utfall)
        assertEquals(navIdent, event.fattetAvIdent)
        assertTrue(event.fattetAvNavn.isNotBlank())
        assertIngenEndring(db, sakId)
    }

    @Test
    fun `vedtak på avslag gir utfall AVSLATT`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, utfall = ForelopigUtfall.AVSLAA)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Accepted, oppsett.send(vedtakUrl(sakId), vedtak()).status)

        assertEquals(Saksstatus.AVSLATT, assertIs<EventData.VedtakFattet>(hentEvents(db).single()).utfall)
    }

    @Test
    fun `retur gir 202 og publiserer ForelopigVedtakReturnert med trimmet forklaring`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(returUrl(sakId), retur())

        assertEquals(HttpStatusCode.Accepted, response.status)
        val event = assertIs<EventData.ForelopigVedtakReturnert>(hentEvents(db).single())
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(1, event.forelopigVedtakVersjon)
        assertEquals(setOf(ReturArsak.FEIL_FAKTA, ReturArsak.ANNET), event.aarsaker)
        assertEquals("Sjekk perioden", event.forklaring)
        assertEquals(navIdent, event.returnertAvIdent)
        assertIngenEndring(db, sakId)
    }

    // Tilgang

    @Test
    fun `uten token gir 401`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db)

        val response = oppsett.client.post(vedtakUrl(sakId)) {
            contentType(ContentType.Application.Json)
            setBody(vedtak())
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `saksbehandlerrolle gir 403 på begge endepunkter`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db, grupper = saksbehandlerGrupper)

        assertEquals(HttpStatusCode.Forbidden, oppsett.send(vedtakUrl(sakId), vedtak()).status)
        assertEquals(HttpStatusCode.Forbidden, oppsett.send(returUrl(sakId), retur()).status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.NotFound, oppsett.send(vedtakUrl(UUID.randomUUID()), vedtak()).status)
    }

    @Test
    fun `sak på annen enhet gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val response = oppsett.send(vedtakUrl(sakId), vedtak())

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `avvist i tilgangsmaskinen gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.Forbidden, avvistBody) })

        assertEquals(HttpStatusCode.Forbidden, oppsett.send(vedtakUrl(sakId), vedtak()).status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `beslutter som ikke er tildelt gir 403 IKKE_TILDELT_SAK`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, beslutterIdent = annenBeslutter)
        val oppsett = oppsett(db)

        val vedtakResponse = oppsett.send(vedtakUrl(sakId), vedtak())
        val returResponse = oppsett.send(returUrl(sakId), retur())

        assertEquals(HttpStatusCode.Forbidden, vedtakResponse.status)
        assertEquals(IKKE_TILDELT_SAK, vedtakResponse.body<TilgangAvvistResponse>().kode)
        assertEquals(HttpStatusCode.Forbidden, returResponse.status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `vedtak fra den som sendte det foreløpige vedtaket gir 403 BESLUTTER_INHABIL, retur gir 202`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSakTilBeslutning(db, innstiltAvIdent = navIdent)
            val oppsett = oppsett(db)

            val vedtakResponse = oppsett.send(vedtakUrl(sakId), vedtak())
            assertEquals(HttpStatusCode.Forbidden, vedtakResponse.status)
            assertEquals(BESLUTTER_INHABIL, vedtakResponse.body<TilgangAvvistResponse>().kode)
            assertTrue(hentEvents(db).isEmpty())

            assertEquals(HttpStatusCode.Accepted, oppsett.send(returUrl(sakId), retur()).status)
            assertIs<EventData.ForelopigVedtakReturnert>(hentEvents(db).single())
        }

    // Tilstand (409)

    @Test
    fun `status ulik TIL_BESLUTNING gir 409 SAK_UGYLDIG_STATUS`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        Saksstatus.entries.filter { it != Saksstatus.TIL_BESLUTNING }.forEach { status ->
            val sakId = lagreSakTilBeslutning(db, status = status)

            val vedtakResponse = oppsett.send(vedtakUrl(sakId), vedtak())
            val returResponse = oppsett.send(returUrl(sakId), retur())

            assertEquals(HttpStatusCode.Conflict, vedtakResponse.status, status.name)
            assertEquals(SAK_UGYLDIG_STATUS, vedtakResponse.kode())
            assertEquals(HttpStatusCode.Conflict, returResponse.status, status.name)
            assertEquals(SAK_UGYLDIG_STATUS, returResponse.kode())
        }
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `kilde ARENA gir 409 SAK_UGYLDIG_KILDE`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, kilde = KildeTilBehandling.ARENA)
        val oppsett = oppsett(db)

        val response = oppsett.send(vedtakUrl(sakId), vedtak())

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(SAK_UGYLDIG_KILDE, response.kode())
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `gammel versjon gir 409 FORELOPIG_VEDTAK_ENDRET`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db, versjoner = 2)
        val oppsett = oppsett(db)

        val vedtakResponse = oppsett.send(vedtakUrl(sakId), vedtak(versjon = 1))
        val returResponse = oppsett.send(returUrl(sakId), retur(versjon = 1))

        assertEquals(HttpStatusCode.Conflict, vedtakResponse.status)
        assertEquals(FORELOPIG_VEDTAK_ENDRET, vedtakResponse.kode())
        assertEquals(HttpStatusCode.Conflict, returResponse.status)
        assertEquals(FORELOPIG_VEDTAK_ENDRET, returResponse.kode())
        assertTrue(hentEvents(db).isEmpty())

        assertEquals(HttpStatusCode.Accepted, oppsett.send(vedtakUrl(sakId), vedtak(versjon = 2)).status)
    }

    // Validering (400)

    @Test
    fun `retur uten årsak, med blank eller for lang forklaring gir 400`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db)

        listOf(
            retur(aarsaker = "[]"),
            retur(forklaring = "   "),
            retur(forklaring = "x".repeat(MAKS_FORKLARING_LENGDE + 1)),
            retur(aarsaker = """["UKJENT"]"""),
            """{ "forelopigVedtakVersjon": 1 }""",
        ).forEach { body ->
            assertEquals(HttpStatusCode.BadRequest, oppsett.send(returUrl(sakId), body).status, body.take(80))
        }
        assertTrue(hentEvents(db).isEmpty())

        val maks = retur(forklaring = "x".repeat(MAKS_FORKLARING_LENGDE))
        assertEquals(HttpStatusCode.Accepted, oppsett.send(returUrl(sakId), maks).status)
    }

    @Test
    fun `vedtak uten versjon gir 400`() = testApplicationWithDatabase { db ->
        val sakId = lagreSakTilBeslutning(db)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.BadRequest, oppsett.send(vedtakUrl(sakId), "{}").status)
        assertTrue(hentEvents(db).isEmpty())
    }

    private val avvistBody = """{ "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE", "status": 403, "brukerIdent": "$fnr", "navIdent": "$navIdent", "begrunnelse": "Nei", "kanOverstyres": false }"""

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
        grupper: String = beslutterGrupper,
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
            configureBeslutterApiV1()
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

    /** Sak tildelt [saksbehandler] og [beslutterIdent], med [versjoner] foreløpige vedtak fra [innstiltAvIdent]. */
    private fun lagreSakTilBeslutning(
        db: TestDatabase,
        status: Saksstatus = Saksstatus.TIL_BESLUTNING,
        utfall: ForelopigUtfall = ForelopigUtfall.INNVILGE,
        beslutterIdent: String = navIdent,
        innstiltAvIdent: String = saksbehandler,
        behandlendeEnhet: String = egenEnhet,
        kilde: KildeTilBehandling = KildeTilBehandling.EKSPERTBISTAND,
        versjoner: Int = 1,
    ): UUID = transaction(db.config.jdbcDatabase) {
        val sakId = SakTable.insert {
            it[soknadId] = lagreSoknad(db)
            it[this.status] = status.name
            it[kildeTilBehandling] = kilde.name
            it[saksnummer] = nesteSaksnummer()
            it[this.behandlendeEnhet] = behandlendeEnhet
            it[saksbehandlerIdent] = saksbehandler
            it[saksbehandlerNavn] = "Navn $saksbehandler"
            it[this.beslutterIdent] = beslutterIdent
            it[beslutterNavn] = "Navn $beslutterIdent"
            it[foreslattUtfall] = utfall.saksstatus.name
        }[SakTable.sakId]
        (1..versjoner).forEach { versjon ->
            ForelopigVedtakTable.insert {
                it[this.sakId] = sakId
                it[this.versjon] = versjon
                it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
                it[this.utfall] = utfall.name
                it[innvilgetBelop] = if (utfall == ForelopigUtfall.INNVILGE_REDUSERT) 5000 else null
                it[tiltaksperiodeFra] = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 11, 22)
                it[tiltaksperiodeTil] = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 12, 31)
                it[begrunnelseDeltaker] = "Til deltaker"
                it[begrunnelseArbeidsgiver] = if (utfall == ForelopigUtfall.INNVILGE) null else "Til arbeidsgiver"
                it[this.innstiltAvIdent] = innstiltAvIdent
                it[sendtTidspunkt] = Instant.parse("2026-11-23T08:00:00Z")
            }
        }
        sakId
    }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }

    /** Lagringen gjøres av handlerne, så endepunktene skal ikke endre `sak`, `vedtak` eller `sak_retur`. */
    private fun assertIngenEndring(db: TestDatabase, sakId: UUID) = transaction(db.config.jdbcDatabase) {
        val sak = SakTable.selectAll().where { SakTable.sakId eq sakId }.single()
        assertEquals(Saksstatus.TIL_BESLUTNING.name, sak[SakTable.status])
        assertTrue(VedtakTable.selectAll().where { VedtakTable.sakId eq sakId }.empty())
        assertTrue(SakReturTable.selectAll().where { SakReturTable.sakId eq sakId }.empty())
    }
}
