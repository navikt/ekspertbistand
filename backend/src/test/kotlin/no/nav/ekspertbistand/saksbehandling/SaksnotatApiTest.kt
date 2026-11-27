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
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SaksnotatApiTest {

    private val navIdent = "A123456"
    private val kollega = "Z999999"
    private val gyldigToken = "valid-azure-token"

    private val saksbehandlerGrupper = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]"""
    private val beslutterGrupper = """[{ "rolle": "${Role.BESLUTTER.groupId}" }]"""
    private val begge = """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }, { "rolle": "${Role.BESLUTTER.groupId}" }]"""
    private val ingenRoller = "[]"

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""

    private fun notater(sakId: Any) = "/api/saksbehandling/v1/saker/$sakId/notater"
    private fun notat(sakId: Any, notatId: Any) = "/api/saksbehandling/v1/saker/$sakId/notater/$notatId"

    // GET

    @Test
    fun `GET gir notatene på saken nyeste først og ikke notater fra andre saker`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val annenSak = lagreSak(db)
        val eldst = lagreNotat(db, sak, navIdent, "Første", Instant.parse("2026-04-02T08:00:00Z"))
        val nyest = lagreNotat(db, sak, kollega, "Andre", Instant.parse("2026-04-02T09:00:00Z"))
        lagreNotat(db, annenSak, navIdent, "Annen sak", Instant.parse("2026-04-02T10:00:00Z"))
        val oppsett = oppsett(db)

        val response = oppsett.client.get(notater(sak.sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val svar = response.body<SaksnotaterResponse>().notater
        assertEquals(listOf(nyest.toString(), eldst.toString()), svar.map { it.notatId })
        assertEquals(kollega, svar.first().skrevetAvIdent)
        assertEquals("Navn $kollega", svar.first().skrevetAvNavn)
        assertEquals(AktorRolle.SAKSBEHANDLER, svar.first().skrevetAvRolle)
        assertEquals("2026-04-02T09:00:00Z", svar.first().opprettet)
        assertEquals(null, svar.first().endret)
    }

    @Test
    fun `GET på Arena-sak gir tom liste`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db, kilde = KildeTilBehandling.ARENA)
        lagreNotat(db, sak, navIdent, "Skal ikke vises", Instant.parse("2026-04-02T08:00:00Z"))
        val oppsett = oppsett(db)

        val response = oppsett.client.get(notater(sak.sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList(), response.body<SaksnotaterResponse>().notater)
    }

    // POST

    @Test
    fun `POST publiserer SaksnotatOpprettet med trimmet tekst og svarer 202 med notatId`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "  Ringte arbeidsgiver  " }""")

        assertEquals(HttpStatusCode.Accepted, response.status)
        val notatId = response.body<SaksnotatOpprettetResponse>().notatId
        val event = hentEvents(db).filterIsInstance<EventData.SaksnotatOpprettet>().single()
        assertEquals(notatId, event.notatId)
        assertEquals(sak.sakId.toString(), event.sakId)
        assertEquals(sak.soknadId.toString(), event.soknadId)
        assertEquals("Ringte arbeidsgiver", event.tekst)
        assertEquals(AktorRolle.SAKSBEHANDLER, event.skrevetAvRolle)
        assertEquals(navIdent, event.skrevetAvIdent)
        assertEquals("Tore Tang", event.skrevetAvNavn)
    }

    @Test
    fun `POST fra beslutter på saken bruker rollen BESLUTTER`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db, beslutterIdent = navIdent)
        val oppsett = oppsett(db, grupper = begge)

        val response = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Ser på saken" }""")

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(
            AktorRolle.BESLUTTER,
            hentEvents(db).filterIsInstance<EventData.SaksnotatOpprettet>().single().skrevetAvRolle,
        )
    }

    @Test
    fun `POST fra beslutter som ikke er tildelt saken bruker rollen BESLUTTER`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        val response = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Ser på saken" }""")

        assertEquals(HttpStatusCode.Accepted, response.status)
        assertEquals(
            AktorRolle.BESLUTTER,
            hentEvents(db).filterIsInstance<EventData.SaksnotatOpprettet>().single().skrevetAvRolle,
        )
    }

    @Test
    fun `POST med tom tekst eller over 1000 tegn gir 400, 1000 tegn går gjennom`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db)

        val tom = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "   " }""")
        val forLang = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "${"x".repeat(1001)}" }""")
        val ulovlig = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "<script>" }""")

        assertEquals(HttpStatusCode.BadRequest, tom.status)
        assertEquals(UGYLDIG_INPUT, tom.kode())
        assertEquals(HttpStatusCode.BadRequest, forLang.status)
        assertEquals(UGYLDIG_INPUT, forLang.kode())
        assertEquals(HttpStatusCode.BadRequest, ulovlig.status)
        assertEquals(UGYLDIG_INPUT, ulovlig.kode())
        assertTrue(hentEvents(db).none { it is EventData.SaksnotatOpprettet })

        val maks = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "${"x".repeat(1000)}" }""")

        assertEquals(HttpStatusCode.Accepted, maks.status)
        assertEquals(1, hentEvents(db).filterIsInstance<EventData.SaksnotatOpprettet>().size)
    }

    @Test
    fun `POST, PUT og DELETE på Arena-sak gir 409 uten event`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db, kilde = KildeTilBehandling.ARENA)
        val notatId = lagreNotat(db, sak, navIdent, "Notat", Instant.parse("2026-04-02T08:00:00Z"))
        val oppsett = oppsett(db)

        val svar = listOf(
            oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Notat" }"""),
            oppsett.send(HttpMethod.Put, notat(sak.sakId, notatId), """{ "tekst": "Notat" }"""),
            oppsett.send(HttpMethod.Delete, notat(sak.sakId, notatId)),
        )

        svar.forEach {
            assertEquals(HttpStatusCode.Conflict, it.status)
            assertEquals(SAK_UGYLDIG_KILDE, it.kode())
        }
        assertEquals(emptyList(), hentEvents(db))
    }

    // Tilgang

    @Test
    fun `uten rolle gir 403 uten event`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db, grupper = ingenRoller)

        val response = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Notat" }""")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppsett.tilgangsmaskinKall)
        assertEquals(emptyList(), hentEvents(db))
    }

    @Test
    fun `sak på annen enhet gir 403 uten event`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db, behandlendeEnhet = annenEnhet)
        val oppsett = oppsett(db)

        val post = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Notat" }""")
        val get = oppsett.client.get(notater(sak.sakId)) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, post.status)
        assertEquals(IKKE_TILGANG_ENHET, post.body<TilgangAvvistResponse>().kode)
        assertEquals(HttpStatusCode.Forbidden, get.status)
        assertEquals(emptyList(), hentEvents(db))
    }

    @Test
    fun `avvist i tilgangsmaskinen gir 403 uten event`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { it.respond(HttpStatusCode.Forbidden, avvistBody) })

        val response = oppsett.send(HttpMethod.Post, notater(sak.sakId), """{ "tekst": "Notat" }""")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(emptyList(), hentEvents(db))
    }

    @Test
    fun `ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get(notater(UUID.randomUUID())) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // PUT og DELETE

    @Test
    fun `PUT og DELETE på eget notat publiserer SaksnotatEndret og SaksnotatSlettet`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val notatId = lagreNotat(db, sak, navIdent, "Gammel", Instant.parse("2026-04-02T08:00:00Z"))
        val oppsett = oppsett(db)

        val put = oppsett.send(HttpMethod.Put, notat(sak.sakId, notatId), """{ "tekst": " Ny tekst " }""")
        val delete = oppsett.send(HttpMethod.Delete, notat(sak.sakId, notatId))

        assertEquals(HttpStatusCode.Accepted, put.status)
        assertEquals(HttpStatusCode.Accepted, delete.status)
        val endret = hentEvents(db).filterIsInstance<EventData.SaksnotatEndret>().single()
        assertEquals(notatId.toString(), endret.notatId)
        assertEquals(sak.sakId.toString(), endret.sakId)
        assertEquals(sak.soknadId.toString(), endret.soknadId)
        assertEquals("Ny tekst", endret.tekst)
        assertEquals(AktorRolle.SAKSBEHANDLER, endret.endretAvRolle)
        assertEquals(navIdent, endret.endretAvIdent)
        val slettet = hentEvents(db).filterIsInstance<EventData.SaksnotatSlettet>().single()
        assertEquals(notatId.toString(), slettet.notatId)
        assertEquals(AktorRolle.SAKSBEHANDLER, slettet.slettetAvRolle)
        assertEquals(navIdent, slettet.slettetAvIdent)
    }

    @Test
    fun `PUT og DELETE på en annens notat gir 403 IKKE_EGET_NOTAT uten event`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val notatId = lagreNotat(db, sak, kollega, "Kollegas notat", Instant.parse("2026-04-02T08:00:00Z"))
        val oppsett = oppsett(db)

        val put = oppsett.send(HttpMethod.Put, notat(sak.sakId, notatId), """{ "tekst": "Overskrevet" }""")
        val delete = oppsett.send(HttpMethod.Delete, notat(sak.sakId, notatId))

        assertEquals(HttpStatusCode.Forbidden, put.status)
        assertEquals(IKKE_EGET_NOTAT, put.body<TilgangAvvistResponse>().kode)
        assertEquals(HttpStatusCode.Forbidden, delete.status)
        assertEquals(IKKE_EGET_NOTAT, delete.body<TilgangAvvistResponse>().kode)
        assertEquals(emptyList(), hentEvents(db))
    }

    @Test
    fun `PUT og DELETE på ukjent notat eller notat fra annen sak gir 404`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val annenSak = lagreSak(db)
        val annenSaksNotat = lagreNotat(db, annenSak, navIdent, "Annen sak", Instant.parse("2026-04-02T08:00:00Z"))
        val oppsett = oppsett(db)

        val svar = listOf(
            oppsett.send(HttpMethod.Put, notat(sak.sakId, UUID.randomUUID()), """{ "tekst": "x" }"""),
            oppsett.send(HttpMethod.Delete, notat(sak.sakId, UUID.randomUUID())),
            oppsett.send(HttpMethod.Put, notat(sak.sakId, annenSaksNotat), """{ "tekst": "x" }"""),
            oppsett.send(HttpMethod.Delete, notat(sak.sakId, annenSaksNotat)),
        )

        svar.forEach { assertEquals(HttpStatusCode.NotFound, it.status) }
        assertEquals(emptyList(), hentEvents(db))
    }

    @Test
    fun `ugyldig notatId gir 400`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db)
        val oppsett = oppsett(db)

        val response = oppsett.send(HttpMethod.Delete, notat(sak.sakId, "ikke-en-uuid"))

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // Hjelpere

    private val avvistBody = """{ "title": "AVVIST_SKJERMING", "status": 403, "navIdent": "$navIdent", "begrunnelse": "Nei", "kanOverstyres": false }"""

    private suspend fun HttpResponse.kode(): String? =
        Json.parseToJsonElement(bodyAsText()).jsonObject["kode"]?.jsonPrimitive?.content

    private class Oppsett(
        val client: HttpClient,
        private val token: String,
        private val tilgangsmaskinTeller: () -> Int,
    ) {
        val tilgangsmaskinKall get() = tilgangsmaskinTeller()

        suspend fun send(metode: HttpMethod, url: String, body: String? = null): HttpResponse =
            client.request(url) {
                method = metode
                bearerAuth(token)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
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
            configureSaksnotatApiV1()
            configureServer()
        }

        return Oppsett(client, gyldigToken) { tilgangsmaskinKall }
    }

    private object NoopAuditLogger : no.nav.common.audit_log.log.AuditLogger {
        override fun log(message: no.nav.common.audit_log.cef.CefMessage) = Unit
        override fun log(message: String) = Unit
    }

    private fun lagreSak(
        db: TestDatabase,
        behandlendeEnhet: String = egenEnhet,
        kilde: KildeTilBehandling = KildeTilBehandling.EKSPERTBISTAND,
        beslutterIdent: String? = null,
    ): LagretSak {
        val sak = lagreSoknadOgSak(db.config.jdbcDatabase, behandlendeEnhet = behandlendeEnhet)
        transaction(db.config.jdbcDatabase) {
            SakTable.update({ SakTable.sakId eq sak.sakId }) {
                it[kildeTilBehandling] = kilde.name
                it[this.beslutterIdent] = beslutterIdent
                it[beslutterNavn] = beslutterIdent?.let { ident -> "Navn $ident" }
            }
        }
        return sak
    }

    private fun lagreNotat(db: TestDatabase, sak: LagretSak, ident: String, tekst: String, tidspunkt: Instant): UUID {
        val notatId = UUID.randomUUID()
        transaction(db.config.jdbcDatabase) {
            lagreSaksnotat(notatId, sak.sakId, tekst, AktorRolle.SAKSBEHANDLER, ident, "Navn $ident", tidspunkt)
        }
        return notatId
    }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }
}
