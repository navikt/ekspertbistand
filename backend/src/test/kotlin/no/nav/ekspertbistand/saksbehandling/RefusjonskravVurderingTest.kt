package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.handlers.LagreRefusjonskravNyInnsendingKrevd
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.refusjon.*
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreFattetVedtak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.vedlegg.SluttrapportStatus
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import no.nav.ekspertbistand.vedlegg.VedleggTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class RefusjonskravVurderingTest {
    private val ident = "A123456"
    private val arsaker = listOf(RefusjonskravArsak.DOKUMENT_IKKE_LESELIG)
    private val vurderingJson = """{"arsaker":["DOKUMENT_IKKE_LESELIG"]}"""

    private fun opprett(database: Database, sluttrapport: SluttrapportStatus? = SluttrapportStatus.BEKREFTET): Pair<LagretSak, UUID> {
        val sak = lagreSoknadOgSak(database, behandlendeEnhet = "1234")
        transaction(database) {
            SakTable.update({ SakTable.sakId eq sak.sakId }) {
                it[status] = Saksstatus.INNVILGET.name
                it[saksbehandlerIdent] = ident
                it[saksbehandlerNavn] = "Saksbehandler"
            }
            SoknadTable.update({ SoknadTable.id eq sak.soknadId }) { it[status] = SoknadStatus.godkjent.name }
            lagreFattetVedtak(sak.sakId, ForelopigUtfall.INNVILGE, 1000)
            if (sluttrapport != null) SluttrapportTable.insert {
                it[sakId] = sak.sakId
                it[status] = sluttrapport.name
            }
        }
        return sak to sendInn(database, sak)
    }

    private fun sendInn(database: Database, sak: LagretSak): UUID = RefusjonDb(database).lagreRefusjonskrav(
        sak.soknadId, "987654321", "42", 10000, listOf(RefusjonsfilInput("faktura.pdf", byteArrayOf(1))),
    )

    private fun publiser(database: Database, sak: LagretSak, kravId: UUID) = transaction(database) {
        publiserRefusjonskravVurdering(sak.sakId, kravId, arsaker, ident, "Saksbehandler", Clock.System.now())
    }

    private fun vurdering(database: Database): EventData.RefusjonskravNyInnsendingKrevd = transaction(database) {
        QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
            .filterIsInstance<EventData.RefusjonskravNyInnsendingKrevd>().last()
    }

    @Test
    fun `nyeste krav oppslag kan analyseres med ny indeks`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, _) = opprett(database)
        val plan = transaction(database) {
            exec(
                "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM refusjonskrav WHERE sak_id = ? ORDER BY opprettet DESC, id DESC LIMIT 1",
                args = listOf(RefusjonskravTable.sakId.columnType to sak.sakId),
                explicitStatementType = StatementType.SELECT,
            ) { result ->
                buildList { while (result.next()) add(result.getString(1)) }
            }
        }
        assertTrue(assertNotNull(plan).any { it.contains("Execution Time") })
    }

    @Test
    fun `vurdering lagres av handler og ny innsending beholder historikk og filer`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        assertEquals(HttpStatusCode.Accepted, publiser(database, sak, kravId).status)
        val event = vurdering(database)
        assertEquals(sak.soknadId.toString(), event.aggregateRootId)
        assertEquals(refusjonskravBeskjed(arsaker), event.begrunnelse)
        assertEquals(RefusjonskravStatuskode.MOTTATT, RefusjonDb(database).finnRefusjonskravStatusForSak(sak.sakId)?.status)

        val handler = LagreRefusjonskravNyInnsendingKrevd(database)
        assertIs<EventHandledResult.Success>(handler.handle(Event(100L, event)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(100L, event)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(101L, event)))
        val lagret = assertNotNull(RefusjonDb(database).finnRefusjonskravStatusForSak(sak.sakId))
        assertEquals(RefusjonskravStatuskode.NY_INNSENDING_KREVES, lagret.status)
        assertEquals(ident, lagret.vurdertAvIdent)
        assertEquals(event.begrunnelse, lagret.begrunnelse)
        transaction(database) {
            assertNull(hentMottatteInnsendinger(listOf(sak.sakId))[sak.sakId]?.refusjonskrav)
            assertTrue(harRefusjonskrav(sak.sakId))
            assertEquals(1, QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
                .filterIsInstance<EventData.SakOppdatert>().count { it.utfortAvRolle == AktorRolle.SAKSBEHANDLER })
        }
        val nyId = sendInn(database, sak)
        assertNotEquals(kravId, nyId)
        val ny = assertNotNull(RefusjonDb(database).finnRefusjonskravStatusForSak(sak.sakId))
        assertEquals(nyId, ny.refusjonskravId)
        assertEquals(RefusjonskravStatuskode.MOTTATT, ny.status)
        assertNull(ny.begrunnelse)
        transaction(database) {
            assertEquals(2, RefusjonskravTable.selectAll().count())
            assertEquals(2, VedleggTable.selectAll().count())
            assertNotNull(hentMottatteInnsendinger(listOf(sak.sakId))[sak.sakId]?.refusjonskrav)
        }
        assertEquals(REFUSJONSKRAV_IKKE_NYESTE, publiser(database, sak, kravId).kode)
        assertFailsWith<RefusjonskravFinnesAlleredeException> { sendInn(database, sak) }
    }

    @Test
    fun `bare nyeste sluttrapport teller`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        transaction(database) { SluttrapportTable.insert {
            it[sakId] = sak.sakId
            it[status] = SluttrapportStatus.MOTTATT.name
            it[opprettet] = Clock.System.now()
        } }
        assertEquals(SLUTTRAPPORT_IKKE_BEKREFTET, publiser(database, sak, kravId).kode)
    }

    @Test
    fun `manglende og ubekreftede sluttrapporter sperrer kontrollen`() {
        listOf(null, SluttrapportStatus.MOTTATT, SluttrapportStatus.NY_INNSENDING_KREVES).forEach { status ->
            testApplicationWithDatabase { db ->
                val (sak, kravId) = opprett(db.config.jdbcDatabase, status)
                assertEquals(SLUTTRAPPORT_IKKE_BEKREFTET, publiser(db.config.jdbcDatabase, sak, kravId).kode)
            }
        }
    }

    @Test
    fun `saksvakter sjekkes under lås og krav må tilhøre saken`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        val (annen, _) = opprett(database)
        assertEquals(HttpStatusCode.NotFound, publiser(database, annen, kravId).status)
        transaction(database) { SakTable.update({ SakTable.sakId eq sak.sakId }) {
            it[saksbehandlerIdent] = "Z999999"
        } }
        assertEquals(HttpStatusCode.Forbidden, publiser(database, sak, kravId).status)
        transaction(database) { SakTable.update({ SakTable.sakId eq sak.sakId }) {
            it[saksbehandlerIdent] = ident
            it[status] = Saksstatus.UNDER_BEHANDLING.name
        } }
        assertEquals(SAK_UGYLDIG_STATUS, publiser(database, sak, kravId).kode)
        transaction(database) { SakTable.update({ SakTable.sakId eq sak.sakId }) {
            it[status] = Saksstatus.INNVILGET.name
            it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
        } }
        assertEquals(SAK_UGYLDIG_KILDE, publiser(database, sak, kravId).kode)
    }

    @Test
    fun `samtidige nye innsendinger gir bare ett nytt krav`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        publiser(database, sak, kravId)
        LagreRefusjonskravNyInnsendingKrevd(database).handle(Event(100L, vurdering(database)))
        val resultater = coroutineScope {
            (1..2).map { async(kotlinx.coroutines.Dispatchers.IO) {
                try { sendInn(database, sak); true } catch (_: RefusjonskravFinnesAlleredeException) { false }
            } }.awaitAll()
        }
        assertEquals(1, resultater.count { it })
        transaction(database) { assertEquals(2, RefusjonskravTable.selectAll().count()) }
    }

    @Test
    fun `ugyldige eventreferanser er handlerfeil`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        publiser(database, sak, kravId)
        val event = vurdering(database).copy(refusjonskravId = UUID.randomUUID().toString())
        assertFalse(LagreRefusjonskravNyInnsendingKrevd(database).handle(Event(100L, event)) is EventHandledResult.Success)
        assertEquals(RefusjonskravStatuskode.MOTTATT, RefusjonDb(database).finnRefusjonskravStatusForSak(sak.sakId)?.status)
    }

    @Test
    fun `samtidig behandling av samme event gir bare en saksloggpost`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, kravId) = opprett(database)
        publiser(database, sak, kravId)
        val event = Event(100L, vurdering(database))
        val handler = LagreRefusjonskravNyInnsendingKrevd(database)
        val resultater = coroutineScope {
            (1..2).map { async(kotlinx.coroutines.Dispatchers.IO) { handler.handle(event) } }.awaitAll()
        }
        resultater.forEach { assertIs<EventHandledResult.Success>(it) }
        transaction(database) {
            assertEquals(1, QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
                .filterIsInstance<EventData.SakOppdatert>().count { it.utfortAvRolle == AktorRolle.SAKSBEHANDLER })
        }
    }

    @Test
    fun `bare nyeste krav med mottatt status gir oppgave`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val (sak, _) = opprett(database)
        transaction(database) {
            RefusjonskravTable.insert {
                it[sakId] = sak.sakId
                it[belopOre] = 10000
                it[status] = RefusjonskravStatuskode.NY_INNSENDING_KREVES.name
                it[opprettet] = Clock.System.now()
            }
            assertNull(hentMottatteInnsendinger(listOf(sak.sakId))[sak.sakId]?.refusjonskrav)
        }
    }

    @Test
    fun `arsaker valideres og annet bruker den avtalte teksten`() {
        assertFailsWith<UgyldigInputException> { validerRefusjonskravVurdering(RefusjonskravVurderingRequest(emptyList())) }
        assertFailsWith<UgyldigInputException> { validerRefusjonskravVurdering(RefusjonskravVurderingRequest(arsaker + arsaker)) }
        assertFailsWith<kotlinx.serialization.SerializationException> {
            Json.decodeFromString<RefusjonskravVurderingRequest>("""{"arsaker":["UKJENT"]}""")
        }
        RefusjonskravArsak.entries.forEach { assertEquals(it, it.tilDomain().tilArsak()) }
        assertEquals(RefusjonskravArsak.ANNET.kontrolltekst, refusjonskravBeskjed(listOf(RefusjonskravArsak.ANNET)))
        assertEquals(listOf(RefusjonskravArsak.BELOP_MAA_JUSTERES, RefusjonskravArsak.ANNET),
            validerRefusjonskravVurdering(RefusjonskravVurderingRequest(listOf(RefusjonskravArsak.ANNET, RefusjonskravArsak.BELOP_MAA_JUSTERES))))
    }

    private fun ApplicationTestBuilder.oppsett(
        database: Database, rolle: Role = Role.SAKSBEHANDLER, tilgangStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ): HttpClient {
        mockEntraProxyFull(
            enheterProvider = { """[{"enhetnummer":"1234","navn":"Nav Test"}]""" },
            grupperProvider = { """[{"rolle":"${rolle.groupId}"}]""" },
        )
        externalServices { hosts(TilgangsmaskinClient.ingress) { routing {
            post("/api/v1/kjerne") { call.respond(tilgangStatus) }
        } } }
        val httpClient = createClient { install(ContentNegotiation) { json() } }
        application {
            dependencies {
                provide<Database> { database }
                provide<HttpClient> { httpClient }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<AzureAdTokenExchanger> { object : AzureAdTokenExchanger {
                    override suspend fun exchange(target: String, userToken: String) = TokenResponse.Success("obo-token", 3600)
                } }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide(TilgangsmaskinClient::class)
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = object : no.nav.common.audit_log.log.AuditLogger {
                    override fun log(message: no.nav.common.audit_log.cef.CefMessage) = Unit
                    override fun log(message: String) = Unit
                }) }
                provide<AzureAdTokenIntrospector> { MockAzureAdIntrospector {
                    if (it == "test-token") mockAzureAdIntrospectionResponse.withNavIdent(ident) else null
                } }
            }
            configureAuthentication()
            configureServer()
            configureRefusjonskravVurderingApiV1()
        }
        return httpClient
    }

    @Test
    fun `api krever autentisering og godtar gyldig dokumentkontroll`() = testApplicationWithDatabase { db ->
        val (sak, kravId) = opprett(db.config.jdbcDatabase)
        val client = oppsett(db.config.jdbcDatabase)
        val url = "/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/$kravId/vurdering"
        assertEquals(HttpStatusCode.Unauthorized, client.post(url) { contentType(ContentType.Application.Json); setBody(vurderingJson) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post(url) {
            bearerAuth("test-token"); contentType(ContentType.Application.Json); setBody("""{"arsaker":[]}""")
        }.status)
        assertEquals(HttpStatusCode.Accepted, client.post(url) {
            bearerAuth("test-token"); contentType(ContentType.Application.Json); setBody(vurderingJson)
        }.status)
        assertEquals(ident, vurdering(db.config.jdbcDatabase).vurdertAvIdent)
    }

    @Test
    fun `beslutter kan ikke kreve ny innsending`() = testApplicationWithDatabase { db ->
        val (sak, kravId) = opprett(db.config.jdbcDatabase)
        val client = oppsett(db.config.jdbcDatabase, Role.BESLUTTER)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/$kravId/vurdering") {
            bearerAuth("test-token"); contentType(ContentType.Application.Json); setBody(vurderingJson)
        }.status)
    }

    @Test
    fun `feil i tilgangsmaskinen sperrer api`() = testApplicationWithDatabase { db ->
        val (sak, kravId) = opprett(db.config.jdbcDatabase)
        val client = oppsett(db.config.jdbcDatabase, tilgangStatus = HttpStatusCode.InternalServerError)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.post("/api/saksbehandling/v1/saker/${sak.sakId}/refusjon/$kravId/vurdering") {
            bearerAuth("test-token"); contentType(ContentType.Application.Json); setBody(vurderingJson)
        }.status)
    }
}
