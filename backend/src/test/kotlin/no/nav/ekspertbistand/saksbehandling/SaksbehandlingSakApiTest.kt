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
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.norg.KommunenavnCache
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.tilgangsmaskin.AggregertBulkRespons
import no.nav.ekspertbistand.tilgangsmaskin.BrukerIdOgRegelsett
import no.nav.ekspertbistand.tilgangsmaskin.EnkeltBulkRespons
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinAvvisning
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.mocks.testAnsattJson
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SaksbehandlingSakApiTest {

    private val navIdent = "A123456"
    private val gyldigToken = "valid-azure-token"
    private val fnr = "12058512345"
    private val innsenderIdent = "98765432109"

    private val saksbehandlerGrupper = """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]"""
    private val beslutterGrupper = """[{ "rolle": "0000-CA-Ekspertbistand_Beslutter" }]"""
    private val beggeRollerGrupper =
        """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }, { "rolle": "0000-CA-Ekspertbistand_Beslutter" }]"""
    private val ingenGrupper = "[]"
    private val kollega = "Z999999"

    private val egenEnhet = "1234"
    private val annenEnhet = "5678"
    private val egneEnheter = """[{ "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""
    private val kommunefilterUrl = "/api/saksbehandling/v1/meg/kommunefilter"

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

        val response = oppsett.client.get("/api/saksbehandling/v1/saker")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `liste uten saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `liste returnerer bare saker på egen enhet, uten fnr`() = testApplicationWithDatabase { db ->
        val eldre = lagreSak(db, lagreSoknad(db, ansattNavn = "Eldre"))
        val nyereSoknad = lagreSoknad(db, ansattNavn = "Nyere")
        val nyere = lagreSak(db, nyereSoknad, saksbehandlerIdent = "Z111111")
        lagreSak(db, lagreSoknad(db, ansattNavn = "Annen Enhet"), behandlendeEnhet = annenEnhet)
        lagreSak(db, lagreSoknad(db, ansattNavn = "Sak Uten Enhet"), behandlendeEnhet = null)
        lagreSoknad(db, ansattNavn = "Uten Sak")
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val raw = response.bodyAsText()
        assertFalse(raw.contains(fnr), "listen skal ikke inneholde fnr")
        assertFalse(raw.contains(innsenderIdent), "listen skal ikke inneholde innsenders ident")

        val saker = response.body<SakerResponse>().saker
        assertEquals(listOf(nyere.toString(), eldre.toString()), saker.map { it.sakId })

        val sak = saker.first()
        assertEquals(Saksstatus.UNDER_BEHANDLING, sak.status)
        assertEquals(KildeTilBehandling.ARENA, sak.kildeTilBehandling)
        assertEquals("Z111111", sak.saksbehandlerIdent)
        assertEquals(egenEnhet, sak.behandlendeEnhet)
        assertEquals(nyereSoknad.toString(), sak.soknad.soknadId)
        assertEquals("Nyere", sak.soknad.ansattNavn)
        assertEquals(SoknadStatus.innsendt, sak.soknad.status)
        assertEquals("123456780", sak.soknad.virksomhet?.virksomhetsnummer)

        assertTrue(oppsett.audit.meldinger.isEmpty(), "listen skal ikke sporingslogges")
    }

    @Test
    fun `liste er tom når saksbehandler ikke har enheter`() = testApplicationWithDatabase { db ->
        lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, enheter = { "[]" })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.body<SakerResponse>().saker.isEmpty())
    }

    @Test
    fun `liste gir 503 når entra-proxy feiler for enheter`() = testApplicationWithDatabase { db ->
        lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, enheter = { error("entra-proxy nede") })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
    }

    @Test
    fun `detalj med ugyldig sakId gir 400`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/ikke-en-uuid") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `detalj uten saksbehandlerrolle gir 403 uten kall mot tilgangsmaskin`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `detalj for ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/${UUID.randomUUID()}") {
            bearerAuth(gyldigToken)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
    }

    @Test
    fun `detalj for sak på annen enhet gir 403 uten kall mot tilgangsmaskin og uten sporingslogg`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), behandlendeEnhet = annenEnhet)
            val oppsett = oppsett(db)

            val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertFalse(response.bodyAsText().contains(fnr))
            assertEquals(IKKE_TILGANG_ENHET, response.body<TilgangAvvistResponse>().kode)
            assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
            assertTrue(oppsett.audit.meldinger.isEmpty())
        }

    @Test
    fun `detalj for sak uten enhet gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), behandlendeEnhet = null)
        val oppsett = oppsett(db)

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
    }

    @Test
    fun `detalj gir 503 når entra-proxy feiler for enheter`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, enheter = { error("entra-proxy nede") })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `detalj med innvilget tilgang returnerer søknad og sporingslogger`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        val sakId = lagreSak(db, soknadId)
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.NoContent) })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val raw = response.bodyAsText()
        assertFalse(raw.contains(innsenderIdent), "innsenders ident skal ikke returneres")

        val sak = response.body<SakDetaljer>()
        assertEquals(sakId.toString(), sak.sakId)
        assertEquals(Saksstatus.UNDER_BEHANDLING, sak.status)
        assertEquals(egenEnhet, sak.behandlendeEnhet)
        assertEquals(soknadId.toString(), sak.soknad.soknadId)
        assertEquals(fnr, sak.soknad.ansatt.fnr)
        assertEquals("Langvarig skulderplage", sak.soknad.behovForBistand.begrunnelse)

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
    fun `detalj avvist med kode 6 sier grunnen med feilId, uten personopplysninger og uten sporingslogg`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db))
            val oppsett = oppsett(db, tilgangsmaskinSvar = { call ->
                call.respondAvvisning("AVVIST_STRENGT_FORTROLIG_ADRESSE")
            })

            val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val raw = response.bodyAsText()
            assertFalse(raw.contains(fnr), "avvisning skal ikke inneholde fnr")
            val avvist = response.body<TilgangAvvistResponse>()
            assertEquals(AVVIST_STRENGT_FORTROLIG_ADRESSE, avvist.kode)
            assertEquals("Du har ikke tilgang til brukere med strengt fortrolig adresse", avvist.begrunnelse)
            assertEquals(response.headers[HttpHeaders.XCorrelationId], assertNotNull(avvist.feilId))
            assertTrue(oppsett.audit.meldinger.isEmpty(), "avvist tilgang skal ikke sporingslogges")
        }

    @Test
    fun `detalj på Vikafossen uten tilgang til enheten sier at grunnen er strengt fortrolig adresse`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), behandlendeEnhet = "2103")
            val oppsett = oppsett(db)

            val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
            val avvist = response.body<TilgangAvvistResponse>()
            assertEquals(AVVIST_STRENGT_FORTROLIG_ADRESSE, avvist.kode)
            assertEquals(response.headers[HttpHeaders.XCorrelationId], assertNotNull(avvist.feilId))
            assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        }

    @Test
    fun `sak på Vikafossen er merket strengt fortrolig for den som har tilgang`() = testApplicationWithDatabase { db ->
        val vikafossen = lagreSak(db, lagreSoknad(db), behandlendeEnhet = "2103")
        val vanlig = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, enheter = {
            """[{ "enhetnummer": "2103", "navn": "Nav Vikafossen" }, { "enhetnummer": "$egenEnhet", "navn": "Nav Test" }]"""
        })

        val saker = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }
            .body<SakerResponse>().saker.associateBy { it.sakId }
        val detalj = oppsett.client.get("/api/saksbehandling/v1/saker/$vikafossen") { bearerAuth(gyldigToken) }
            .body<SakDetaljer>()

        assertEquals(Adressebeskyttelse.STRENGT_FORTROLIG, saker.getValue(vikafossen.toString()).adressebeskyttelse)
        assertNull(saker.getValue(vanlig.toString()).adressebeskyttelse)
        assertEquals(Adressebeskyttelse.STRENGT_FORTROLIG, detalj.adressebeskyttelse)
    }

    @Test
    fun `detalj avvist for skjerming eller fortrolig adresse sender koden videre med feilId`() =
        testApplicationWithDatabase { db ->
            val kodePerFnr = mapOf("11111111111" to "AVVIST_SKJERMING", "22222222222" to "AVVIST_FORTROLIG_ADRESSE")
            val sakPerKode = kodePerFnr.entries.associate { (fnr, kode) ->
                kode to lagreSak(db, lagreSoknad(db, ansattFnr = fnr))
            }
            val oppsett = oppsett(db, tilgangsmaskinSvar = { call ->
                call.respondAvvisning(kodePerFnr.getValue(call.attributes[tilgangsmaskinBody].trim('"')))
            })

            sakPerKode.forEach { (kode, sakId) ->
                val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

                assertEquals(HttpStatusCode.Forbidden, response.status)
                val avvist = response.body<TilgangAvvistResponse>()
                assertEquals(kode, avvist.kode)
                assertEquals("Begrunnelse for $kode", avvist.begrunnelse)
                assertNotNull(avvist.feilId)
            }
        }

    @Test
    fun `detalj gir 503 når tilgangsmaskin feiler`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertFalse(response.bodyAsText().contains(fnr))
        assertTrue(oppsett.audit.meldinger.isEmpty())
    }

    @Test
    fun `POST tildeling publiserer SakTildeltSaksbehandler med ident og navn fra entra-proxy`() =
        testApplicationWithDatabase { db ->
            val soknadId = lagreSoknad(db)
            val sakId = lagreSak(db, soknadId)
            val oppsett = oppsett(db)

            val response = oppsett.tildel(sakId)

            assertEquals(HttpStatusCode.Accepted, response.status)
            val event = assertIs<EventData.SakTildeltSaksbehandler>(hentEvents(db).single())
            assertEquals(sakId.toString(), event.sakId)
            assertEquals(soknadId.toString(), event.soknadId)
            assertEquals(navIdent, event.saksbehandlerIdent)
            assertEquals("Innlogget Saksbehandler", event.saksbehandlerNavn)
            assertNull(hentSakRad(db, sakId)[SakTable.saksbehandlerIdent], "API-et skal ikke skrive til sak")
        }

    @Test
    fun `POST tildeling på sak tildelt en annen publiserer (overtakelse)`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999")
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Accepted, oppsett.tildel(sakId).status)
        assertIs<EventData.SakTildeltSaksbehandler>(hentEvents(db).single())
    }

    @Test
    fun `POST tildeling på sak innlogget allerede har gir 202 uten event`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Accepted, oppsett.tildel(sakId).status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `POST tildeling når innlogget er beslutter på saken publiserer`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), beslutterIdent = navIdent)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Accepted, oppsett.tildel(sakId).status)
        assertIs<EventData.SakTildeltSaksbehandler>(hentEvents(db).single())
    }

    @Test
    fun `POST tildeling på sak som ikke venter på saksbehandler gir 409 SAK_UGYLDIG_STATUS`() =
        testApplicationWithDatabase { db ->
            val oppsett = oppsett(db)
            val saker = listOf(
                Saksstatus.TIL_BESLUTNING,
                Saksstatus.INNVILGET,
                Saksstatus.AVSLATT,
                Saksstatus.AVSLUTTET,
            ).map { lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999", status = it) }

            saker.forEach { sakId ->
                val response = oppsett.tildel(sakId)
                assertEquals(HttpStatusCode.Conflict, response.status)
                assertTrue(response.bodyAsText().contains(SAK_UGYLDIG_STATUS))
            }
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling med bare beslutterrolle gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        assertEquals(HttpStatusCode.Forbidden, oppsett.tildel(sakId).status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `POST tildeling på sak på annen enhet gir 403 uten kall mot tilgangsmaskin`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), behandlendeEnhet = annenEnhet)
            val oppsett = oppsett(db)

            assertEquals(HttpStatusCode.Forbidden, oppsett.tildel(sakId).status)
            assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling når tilgangsmaskin avviser gir 403 uten event`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call ->
            call.respondText(
                """{ "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE", "status": 403, "begrunnelse": "x", "kanOverstyres": false }""",
                ContentType.parse("application/problem+json"),
                HttpStatusCode.Forbidden,
            )
        })

        assertEquals(HttpStatusCode.Forbidden, oppsett.tildel(sakId).status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `POST tildeling når tilgangsmaskin feiler gir 503 uten event`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.InternalServerError) })

        assertEquals(HttpStatusCode.ServiceUnavailable, oppsett.tildel(sakId).status)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `DELETE tildeling på egen sak publiserer SakFrigjort`() = testApplicationWithDatabase { db ->
        val soknadId = lagreSoknad(db)
        val sakId = lagreSak(db, soknadId, saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Accepted, oppsett.frigjoer(sakId).status)
        val event = assertIs<EventData.SakFrigjort>(hentEvents(db).single())
        assertEquals(sakId.toString(), event.sakId)
        assertEquals(soknadId.toString(), event.soknadId)
        assertEquals(navIdent, event.saksbehandlerIdent)
        assertEquals(navIdent, hentSakRad(db, sakId)[SakTable.saksbehandlerIdent], "API-et skal ikke skrive til sak")
    }

    @Test
    fun `DELETE tildeling på sak tildelt en annen gir 403 IKKE_TILDELT_SAK`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999")
        val oppsett = oppsett(db)

        val response = oppsett.frigjoer(sakId)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(IKKE_TILDELT_SAK, response.body<TilgangAvvistResponse>().kode)
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `liste og detalj gir saksbehandlerNavn fra saken uten navneoppslag i entra-proxy`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999")
            val oppsett = oppsett(db)

            val liste = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }
                .body<SakerResponse>().saker
            val detalj = oppsett.hentDetalj(sakId)

            assertEquals("Navn Z999999", liste.single().saksbehandlerNavn)
            assertEquals("Navn Z999999", detalj.saksbehandlerNavn)
            assertEquals(listOf(navIdent), oppsett.ansattOppslag, "bare innlogget skal slås opp, og bare én gang")
        }

    @Test
    fun `kanTildeleMeg i detalj`() = testApplicationWithDatabase { db ->
        val ledig = lagreSak(db, lagreSoknad(db))
        val annen = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999")
        val beslutter = lagreSak(db, lagreSoknad(db), beslutterIdent = navIdent)
        val tilBeslutning = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        val innvilget = lagreSak(db, lagreSoknad(db), status = Saksstatus.INNVILGET)
        val avslatt = lagreSak(db, lagreSoknad(db), status = Saksstatus.AVSLATT)
        val avsluttet = lagreSak(db, lagreSoknad(db), status = Saksstatus.AVSLUTTET)
        val egen = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = navIdent)
        val oppsett = oppsett(db)

        listOf(ledig, annen, beslutter).forEach {
            assertTrue(oppsett.hentDetalj(it).kanTildeleMeg, "forventet kanTildeleMeg for $it")
        }
        listOf(tilBeslutning, innvilget, avslatt, avsluttet, egen).forEach {
            assertFalse(oppsett.hentDetalj(it).kanTildeleMeg, "forventet ikke kanTildeleMeg for $it")
        }
    }

    @Test
    fun `kanTildeleMeg er false i detalj uten saksbehandlerrolle`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        assertFalse(oppsett.hentDetalj(sakId).kanTildeleMeg)
    }

    @Test
    fun `detalj gir siste versjon av foreløpig vedtak, eller null`() = testApplicationWithDatabase { db ->
        val utenVedtak = lagreSak(db, lagreSoknad(db))
        val medVedtak = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        transaction(db.config.jdbcDatabase) {
            listOf(1 to ForelopigUtfall.AVSLAA, 2 to ForelopigUtfall.INNVILGE).forEach { (versjon, utfall) ->
                ForelopigVedtakTable.insert {
                    it[sakId] = medVedtak
                    it[this.versjon] = versjon
                    it[eventId] = versjon.toLong()
                    it[this.utfall] = utfall.name
                    it[innvilgetBelop] = if (utfall == ForelopigUtfall.INNVILGE) 9999 else null
                    it[tiltaksperiodeFra] = if (utfall == ForelopigUtfall.INNVILGE) LocalDate(2026, 11, 22) else null
                    it[tiltaksperiodeTil] = if (utfall == ForelopigUtfall.INNVILGE) LocalDate(2026, 12, 31) else null
                    it[begrunnelseDeltaker] = "Til deltaker $versjon"
                    it[begrunnelseArbeidsgiver] = if (utfall == ForelopigUtfall.AVSLAA) "Til arbeidsgiver" else null
                    it[innstiltAvIdent] = navIdent
                    it[sendtTidspunkt] = Instant.parse("2026-11-0${versjon}T10:00:00Z")
                }
            }
        }
        val oppsett = oppsett(db)

        assertNull(oppsett.hentDetalj(utenVedtak).forelopigVedtak)
        val vedtak = assertNotNull(oppsett.hentDetalj(medVedtak).forelopigVedtak)
        assertEquals(2, vedtak.versjon)
        assertEquals(ForelopigUtfall.INNVILGE, vedtak.utfall)
        assertEquals(9999, vedtak.innvilgetBelop)
        assertEquals(LocalDate(2026, 11, 22), vedtak.tiltaksperiodeFra)
        assertEquals("Til deltaker 2", vedtak.begrunnelseDeltaker)
        assertNull(vedtak.begrunnelseArbeidsgiver)
        assertEquals(navIdent, vedtak.innstiltAvIdent)
    }

    @Test
    fun `kanTildeleMeg i listen er false for saker tildelt en annen, og listen sjekker tilgangsmaskin i bulk`() =
        testApplicationWithDatabase { db ->
            val ledig = lagreSak(db, lagreSoknad(db))
            val annen = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z999999")
            val egen = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = navIdent)
            val avslatt = lagreSak(db, lagreSoknad(db), status = Saksstatus.AVSLATT)
            val tilBeslutning = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
            val innvilget = lagreSak(db, lagreSoknad(db), status = Saksstatus.INNVILGET)
            val oppsett = oppsett(db)

            val saker = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }
                .body<SakerResponse>().saker.associate { it.sakId to it.kanTildeleMeg }

            assertEquals(
                mapOf(
                    ledig.toString() to true,
                    annen.toString() to false,
                    egen.toString() to false,
                    avslatt.toString() to false,
                    tilBeslutning.toString() to false,
                    innvilget.toString() to false,
                ),
                saker,
            )
            assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
            assertEquals(listOf(listOf(BrukerIdOgRegelsett(fnr, "KJERNE_REGELTYPE"))), oppsett.bulkKall)
            assertEquals(listOf(gyldigToken), oppsett.veksledeTokens, "OBO skal bruke innkommende token")
        }

    @Test
    fun `listen skjuler deltaker og arbeidsgiver og sier grunnen når tilgangsmaskin avviser`() = testApplicationWithDatabase { db ->
        val innvilgetFnr = "11111111111"
        val skjermetFnr = "22222222222"
        val kode6Fnr = "33333333333"
        val ukjentFnr = "44444444444"
        val manglerISvaretFnr = "55555555555"
        val kode7Fnr = "66666666666"
        val kode6UtlandFnr = "77777777777"
        val habilitetFnr = "88888888888"
        val innvilget = lagreSak(db, lagreSoknad(db, ansattNavn = "Synlig Deltaker", ansattFnr = innvilgetFnr))
        val skjermet = lagreSak(db, lagreSoknad(db, ansattFnr = skjermetFnr))
        val kode6 = lagreSak(db, lagreSoknad(db, ansattFnr = kode6Fnr))
        val ukjent = lagreSak(db, lagreSoknad(db, ansattFnr = ukjentFnr))
        val manglerISvaret = lagreSak(db, lagreSoknad(db, ansattFnr = manglerISvaretFnr))
        val kode7 = lagreSak(db, lagreSoknad(db, ansattFnr = kode7Fnr))
        val kode6Utland = lagreSak(db, lagreSoknad(db, ansattFnr = kode6UtlandFnr))
        val habilitet = lagreSak(db, lagreSoknad(db, ansattFnr = habilitetFnr))
        val oppsett = oppsett(db, bulkSvar = { call, _ ->
            call.respondBulk(
                listOf(
                    EnkeltBulkRespons(innvilgetFnr, 204),
                    EnkeltBulkRespons(skjermetFnr, 403, TilgangsmaskinAvvisning(title = "AVVIST_SKJERMING")),
                    EnkeltBulkRespons(
                        kode6Fnr, 403, TilgangsmaskinAvvisning(title = "AVVIST_STRENGT_FORTROLIG_ADRESSE"),
                    ),
                    EnkeltBulkRespons(ukjentFnr, 404),
                    EnkeltBulkRespons(kode7Fnr, 403, TilgangsmaskinAvvisning(title = "AVVIST_FORTROLIG_ADRESSE")),
                    EnkeltBulkRespons(
                        kode6UtlandFnr, 403, TilgangsmaskinAvvisning(title = "AVVIST_STRENGT_FORTROLIG_UTLAND"),
                    ),
                    EnkeltBulkRespons(habilitetFnr, 403, TilgangsmaskinAvvisning(title = "AVVIST_HABILITET")),
                )
            )
        })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        val body = response.body<SakerResponse>()
        assertFalse(body.tilgangssjekkFeilet)
        val saker = body.saker.associateBy { UUID.fromString(it.sakId) }

        val synlig = saker.getValue(innvilget)
        assertNull(synlig.deltakerTilgang)
        assertFalse(synlig.skjermet)
        assertEquals("Synlig Deltaker", synlig.soknad.ansattNavn)
        assertEquals("Eksempel Bedrift AS", synlig.soknad.virksomhet?.virksomhetsnavn)
        assertTrue(synlig.kanTildeleMeg)

        mapOf(
            skjermet to AVVIST_SKJERMING,
            kode6 to AVVIST_STRENGT_FORTROLIG_ADRESSE,
            kode6Utland to AVVIST_STRENGT_FORTROLIG_ADRESSE,
            kode7 to AVVIST_FORTROLIG_ADRESSE,
            habilitet to IKKE_TILGANG,
            ukjent to IKKE_TILGANG,
            manglerISvaret to IKKE_TILGANG,
        ).forEach { (sakId, kode) ->
            val sak = saker.getValue(sakId)
            assertEquals(DeltakerTilgang(avvist = true, kode = kode), sak.deltakerTilgang)
            assertNull(sak.soknad.ansattNavn)
            assertNull(sak.soknad.virksomhet)
            assertFalse(sak.kanTildeleMeg)
            assertFalse(sak.kanTildeleMegSomBeslutter)
        }
    }

    @Test
    fun `listen skjuler deltaker og arbeidsgiver på alle saker når tilgangsmaskin feiler`() =
        testApplicationWithDatabase { db ->
            lagreSak(db, lagreSoknad(db))
            val oppsett = oppsett(db, bulkSvar = { call, _ -> call.respond(HttpStatusCode.InternalServerError) })

            val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.body<SakerResponse>()
            assertTrue(body.tilgangssjekkFeilet)
            val sak = body.saker.single()
            assertNull(sak.soknad.ansattNavn)
            assertNull(sak.soknad.virksomhet)
            assertNull(sak.deltakerTilgang)
            assertFalse(sak.kanTildeleMeg)
        }

    @Test
    fun `tom liste kaller ikke tilgangsmaskin`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val body = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }.body<SakerResponse>()

        assertTrue(body.saker.isEmpty())
        assertFalse(body.tilgangssjekkFeilet)
        assertTrue(oppsett.bulkKall.isEmpty())
    }

    @Test
    fun `listen gir kommune med navn fra Norg, og skjuler kommunen når tilgangsmaskin avviser`() =
        testApplicationWithDatabase { db ->
            val avvistFnr = "22222222222"
            val bergen = lagreSak(db, lagreSoknad(db), kommunenummer = "4601")
            val ukjentKommune = lagreSak(db, lagreSoknad(db), kommunenummer = "9999")
            val utenKommune = lagreSak(db, lagreSoknad(db))
            val avvist = lagreSak(db, lagreSoknad(db, ansattFnr = avvistFnr), kommunenummer = "0301")
            val oppsett = oppsett(db, bulkSvar = { call, identer ->
                call.respondBulk(
                    identer.map {
                        if (it == avvistFnr) {
                            EnkeltBulkRespons(it, 403, TilgangsmaskinAvvisning(title = "AVVIST_SKJERMING"))
                        } else {
                            EnkeltBulkRespons(it, 204)
                        }
                    }
                )
            })

            val saker = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }
                .body<SakerResponse>().saker.associateBy { UUID.fromString(it.sakId) }

            assertEquals("4601" to "Bergen", saker.getValue(bergen).let { it.kommunenummer to it.kommunenavn })
            assertEquals("9999" to null, saker.getValue(ukjentKommune).let { it.kommunenummer to it.kommunenavn })
            assertEquals(null to null, saker.getValue(utenKommune).let { it.kommunenummer to it.kommunenavn })
            assertEquals(null to null, saker.getValue(avvist).let { it.kommunenummer to it.kommunenavn })
        }

    @Test
    fun `listen gir kommunenummer uten navn når Norg feiler`() = testApplicationWithDatabase { db ->
        lagreSak(db, lagreSoknad(db), kommunenummer = "4601")
        val oppsett = oppsett(db, geografiSvar = { it.respond(HttpStatusCode.InternalServerError) })

        val response = oppsett.client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val sak = response.body<SakerResponse>().saker.single()
        assertEquals("4601", sak.kommunenummer)
        assertNull(sak.kommunenavn)
    }

    @Test
    fun `kommunefilter er tomt før noe er lagret`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)

        val response = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(KommunefilterResponse(enhet = null, kommuner = emptyList()), response.body())
    }

    @Test
    fun `kommunefilter lagres og hentes med navn, og kommuner Norg ikke kjenner faller bort`() =
        testApplicationWithDatabase { db ->
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            val lagret = oppsett.client.put(kommunefilterUrl) {
                bearerAuth(gyldigToken)
                contentType(ContentType.Application.Json)
                setBody(KommunefilterRequest(enhet = egenEnhet, kommuner = listOf("4601", "9999", "0301", "4601")))
            }
            val hentet = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }

            assertEquals(HttpStatusCode.NoContent, lagret.status)
            assertEquals(
                KommunefilterResponse(
                    enhet = egenEnhet,
                    kommuner = listOf(
                        KommunefilterResponse.Kommune("0301", "Oslo"),
                        KommunefilterResponse.Kommune("4601", "Bergen"),
                    ),
                ),
                hentet.body(),
            )
        }

    @Test
    fun `kommunefilter beholder kommunene uten navn når Norg feiler`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db, geografiSvar = { it.respond(HttpStatusCode.InternalServerError) })
        oppsett.client.put(kommunefilterUrl) {
            bearerAuth(gyldigToken)
            contentType(ContentType.Application.Json)
            setBody(KommunefilterRequest(enhet = egenEnhet, kommuner = listOf("9999")))
        }

        val hentet = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }

        assertEquals(listOf(KommunefilterResponse.Kommune("9999", null)), hentet.body<KommunefilterResponse>().kommuner)
    }

    @Test
    fun `kommunefilter overskrives ved ny lagring`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db)
        listOf(listOf("4601"), emptyList()).forEach { kommuner ->
            oppsett.client.put(kommunefilterUrl) {
                bearerAuth(gyldigToken)
                contentType(ContentType.Application.Json)
                setBody(KommunefilterRequest(enhet = egenEnhet, kommuner = kommuner))
            }
        }

        val hentet = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }

        assertEquals(KommunefilterResponse(enhet = egenEnhet, kommuner = emptyList()), hentet.body())
    }

    @Test
    fun `kommunefilter med enhet innlogget ikke lenger har gir tomt filter`() = testApplicationWithDatabase { db ->
        transaction(db.config.jdbcDatabase) {
            SaksbehandlerKommunefilterTable.insert {
                it[navIdent] = this@SaksbehandlingSakApiTest.navIdent
                it[enhet] = annenEnhet
                it[kommuner] = listOf("4601")
            }
        }
        val oppsett = oppsett(db)

        val hentet = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }

        assertEquals(KommunefilterResponse(enhet = null, kommuner = emptyList()), hentet.body())
    }

    @Test
    fun `kommunefilter avviser enhet innlogget ikke har og kommuner som ikke er fire sifre`() =
        testApplicationWithDatabase { db ->
            val oppsett = oppsett(db)

            listOf(
                KommunefilterRequest(enhet = annenEnhet, kommuner = emptyList()) to "enhet",
                KommunefilterRequest(enhet = egenEnhet, kommuner = listOf("4601", "46")) to "kommuner[1]",
                KommunefilterRequest(enhet = egenEnhet, kommuner = listOf("460a")) to "kommuner[0]",
            ).forEach { (request, felt) ->
                val response = oppsett.client.put(kommunefilterUrl) {
                    bearerAuth(gyldigToken)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                assertEquals(HttpStatusCode.BadRequest, response.status, felt)
                assertTrue(response.bodyAsText().contains("\"felt\":\"$felt\""), response.bodyAsText())
            }
            assertEquals(0, transaction(db.config.jdbcDatabase) { SaksbehandlerKommunefilterTable.selectAll().count() })
        }

    @Test
    fun `kommunefilter uten saksbehandler- eller beslutterrolle gir 403`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db, grupper = ingenGrupper)

        val hent = oppsett.client.get(kommunefilterUrl) { bearerAuth(gyldigToken) }
        val lagre = oppsett.client.put(kommunefilterUrl) {
            bearerAuth(gyldigToken)
            contentType(ContentType.Application.Json)
            setBody(KommunefilterRequest(enhet = egenEnhet, kommuner = emptyList()))
        }

        assertEquals(HttpStatusCode.Forbidden, hent.status)
        assertEquals(HttpStatusCode.Forbidden, lagre.status)
    }

    @Test
    fun `tilTilgangsgrunnlag gir beslutterIdent fra saken`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = "Z111111", beslutterIdent = "Z222222")
        val oppsett = oppsett(db)

        val grunnlag = with(SakTilgangsgrunnlag) { oppsett.hentDetalj(sakId).tilTilgangsgrunnlag() }

        assertEquals("Z111111", grunnlag.saksbehandlerIdent)
        assertEquals("Z222222", grunnlag.beslutterIdent)
    }

    @Test
    fun `POST tildeling beslutter publiserer SakTildeltBeslutter med ident og navn fra entra-proxy`() =
        testApplicationWithDatabase { db ->
            val soknadId = lagreSoknad(db)
            val sakId = lagreSak(db, soknadId, status = Saksstatus.TIL_BESLUTNING)
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            val response = oppsett.tildelBeslutter(sakId)

            assertEquals(HttpStatusCode.Accepted, response.status)
            val event = assertIs<EventData.SakTildeltBeslutter>(hentEvents(db).single())
            assertEquals(sakId.toString(), event.sakId)
            assertEquals(soknadId.toString(), event.soknadId)
            assertEquals(navIdent, event.beslutterIdent)
            assertEquals("Innlogget Saksbehandler", event.beslutterNavn)
            assertNull(hentSakRad(db, sakId)[SakTable.beslutterIdent], "API-et skal ikke skrive til sak")
        }

    @Test
    fun `POST tildeling beslutter på sak med annen beslutter publiserer (overtakelse)`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = "Z888888")
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            assertEquals(HttpStatusCode.Accepted, oppsett.tildelBeslutter(sakId).status)
            assertIs<EventData.SakTildeltBeslutter>(hentEvents(db).single())
        }

    @Test
    fun `POST tildeling beslutter på sak innlogget allerede har gir 202 uten event`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = navIdent)
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            assertEquals(HttpStatusCode.Accepted, oppsett.tildelBeslutter(sakId).status)
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter når innlogget sendte vedtaket gir 403 BESLUTTER_INHABIL`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = navIdent)
            val oppsett = oppsett(db, grupper = beggeRollerGrupper)

            val response = oppsett.tildelBeslutter(sakId)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(BESLUTTER_INHABIL, response.body<TilgangAvvistResponse>().kode)
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter uten foreløpig vedtak gir 403 BESLUTTER_INHABIL`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            val response = oppsett.tildelBeslutter(sakId)

            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals(BESLUTTER_INHABIL, response.body<TilgangAvvistResponse>().kode)
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter på sak som ikke er TIL_BESLUTNING gir 409 SAK_UGYLDIG_STATUS`() =
        testApplicationWithDatabase { db ->
            val saker = (Saksstatus.entries - Saksstatus.TIL_BESLUTNING).map { status ->
                lagreSak(db, lagreSoknad(db), status = status).also {
                    lagreForelopigVedtak(db, it, innstiltAvIdent = kollega)
                }
            }
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            saker.forEach { sakId ->
                val response = oppsett.tildelBeslutter(sakId)
                assertEquals(HttpStatusCode.Conflict, response.status)
                assertTrue(response.bodyAsText().contains(SAK_UGYLDIG_STATUS))
            }
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter med bare saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
        val oppsett = oppsett(db)

        assertEquals(HttpStatusCode.Forbidden, oppsett.tildelBeslutter(sakId).status)
        assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
        assertTrue(hentEvents(db).isEmpty())
    }

    @Test
    fun `POST tildeling beslutter på sak på annen enhet gir 403 uten kall mot tilgangsmaskin`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(
                db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, behandlendeEnhet = annenEnhet,
            )
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(db, grupper = beslutterGrupper)

            assertEquals(HttpStatusCode.Forbidden, oppsett.tildelBeslutter(sakId).status)
            assertTrue(oppsett.tilgangsmaskinKall.isEmpty())
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter når tilgangsmaskin avviser gir 403 uten event`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(db, grupper = beslutterGrupper, tilgangsmaskinSvar = { call ->
                call.respondText(
                    """{ "title": "AVVIST_STRENGT_FORTROLIG_ADRESSE", "status": 403, "begrunnelse": "x", "kanOverstyres": false }""",
                    ContentType.parse("application/problem+json"),
                    HttpStatusCode.Forbidden,
                )
            })

            assertEquals(HttpStatusCode.Forbidden, oppsett.tildelBeslutter(sakId).status)
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter når tilgangsmaskin feiler gir 503 uten event`() =
        testApplicationWithDatabase { db ->
            val sakId = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
            lagreForelopigVedtak(db, sakId, innstiltAvIdent = kollega)
            val oppsett = oppsett(
                db,
                grupper = beslutterGrupper,
                tilgangsmaskinSvar = { call -> call.respond(HttpStatusCode.InternalServerError) },
            )

            assertEquals(HttpStatusCode.ServiceUnavailable, oppsett.tildelBeslutter(sakId).status)
            assertTrue(hentEvents(db).isEmpty())
        }

    @Test
    fun `POST tildeling beslutter på ukjent sak gir 404`() = testApplicationWithDatabase { db ->
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        assertEquals(HttpStatusCode.NotFound, oppsett.tildelBeslutter(UUID.randomUUID()).status)
    }

    @Test
    fun `listen gir oppgavetype, beslutterNavn og tiltaksperiode`() = testApplicationWithDatabase { db ->
        val opprettet = lagreSak(db, lagreSoknad(db), status = Saksstatus.OPPRETTET)
        val tilBeslutning = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = "Z888888")
        lagreForelopigVedtak(
            db, tilBeslutning, innstiltAvIdent = kollega, versjon = 1,
            tiltaksperiodeFra = LocalDate(2026, 1, 1), tiltaksperiodeTil = LocalDate(2026, 2, 1),
        )
        lagreForelopigVedtak(
            db, tilBeslutning, innstiltAvIdent = kollega, versjon = 2,
            tiltaksperiodeFra = LocalDate(2026, 11, 22), tiltaksperiodeTil = LocalDate(2026, 12, 31),
        )
        val innvilget = lagreSak(db, lagreSoknad(db), status = Saksstatus.INNVILGET)
        val oppsett = oppsett(db)

        val saker = oppsett.hentListe().associateBy { it.sakId }

        assertEquals(Oppgavetype.SOKNAD, saker.getValue(opprettet.toString()).oppgavetype)
        assertNull(saker.getValue(opprettet.toString()).tiltaksperiodeFra)
        with(saker.getValue(tilBeslutning.toString())) {
            assertEquals(Oppgavetype.SOKNAD_BESLUTTER, oppgavetype)
            assertEquals("Navn Z888888", beslutterNavn)
            assertEquals(LocalDate(2026, 11, 22), tiltaksperiodeFra)
            assertEquals(LocalDate(2026, 12, 31), tiltaksperiodeTil)
        }
        assertNull(saker.getValue(innvilget.toString()).oppgavetype)
    }

    @Test
    fun `liste og detalj gir SOKNAD_RETUR og sisteRetur etter retur, og vedtak når det er fattet`() =
        testApplicationWithDatabase { db ->
            val utenRetur = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = kollega)
            val medRetur = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = kollega, beslutterIdent = "Z888888")
            val besluttet = lagreSak(
                db, lagreSoknad(db), saksbehandlerIdent = kollega, beslutterIdent = "Z888888",
                status = Saksstatus.INNVILGET,
            )
            transaction(db.config.jdbcDatabase) {
                listOf(1, 2).forEach { versjon ->
                    val forelopigId = lagreForelopigVedtak(db, medRetur, innstiltAvIdent = kollega, versjon = versjon)
                    SakReturTable.insert {
                        it[sakId] = medRetur
                        it[forelopigVedtakId] = forelopigId
                        it[returnertAvIdent] = "Z888888"
                        it[returnertAvNavn] = "Navn Z888888"
                        it[tilSaksbehandlerIdent] = kollega
                        it[aarsaker] = if (versjon == 1) listOf("ANNET") else listOf("FEIL_REGELFORSTAELSE", "FEIL_FAKTA")
                        it[forklaring] = "Forklaring $versjon"
                        it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
                        it[opprettet] = Instant.parse("2026-11-0${versjon + 2}T10:00:00Z")
                    }
                }
                val forelopigId = lagreForelopigVedtak(db, besluttet, innstiltAvIdent = kollega)
                VedtakTable.insert {
                    it[sakId] = besluttet
                    it[forelopigVedtakId] = forelopigId
                    it[utfall] = Saksstatus.INNVILGET.name
                    it[fattetAvIdent] = "Z888888"
                    it[fattetAvNavn] = "Navn Z888888"
                    it[fattetTidspunkt] = Instant.parse("2026-11-05T10:00:00Z")
                    it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
                }
            }
            val oppsett = oppsett(db)

            val saker = oppsett.hentListe().associateBy { it.sakId }
            assertEquals(Oppgavetype.SOKNAD, saker.getValue(utenRetur.toString()).oppgavetype)
            assertEquals(Oppgavetype.SOKNAD_RETUR, saker.getValue(medRetur.toString()).oppgavetype)
            assertTrue(saker.getValue(medRetur.toString()).harOppgaveForMeg)

            with(oppsett.hentDetalj(utenRetur)) {
                assertNull(sisteRetur)
                assertNull(vedtak)
            }
            with(oppsett.hentDetalj(medRetur)) {
                assertEquals(Oppgavetype.SOKNAD_RETUR, oppgavetype)
                val retur = assertNotNull(sisteRetur)
                assertEquals(2, retur.forelopigVedtakVersjon)
                assertEquals(listOf(ReturArsak.FEIL_REGELFORSTAELSE, ReturArsak.FEIL_FAKTA), retur.aarsaker)
                assertEquals("Forklaring 2", retur.forklaring)
                assertEquals("Navn Z888888", retur.returnertAvNavn)
                assertEquals(Instant.parse("2026-11-04T10:00:00Z"), retur.tidspunkt)
                assertNull(vedtak)
            }
            with(assertNotNull(oppsett.hentDetalj(besluttet).vedtak)) {
                assertEquals(Saksstatus.INNVILGET, utfall)
                assertEquals(1, forelopigVedtakVersjon)
                assertEquals("Navn Z888888", fattetAvNavn)
                assertEquals(Instant.parse("2026-11-05T10:00:00Z"), fattetTidspunkt)
            }
        }

    @Test
    fun `kanTildeleMegSomBeslutter i listen`() = testApplicationWithDatabase { db ->
        val ledig = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        lagreForelopigVedtak(db, ledig, innstiltAvIdent = kollega)
        val sendtAvMeg = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        lagreForelopigVedtak(db, sendtAvMeg, innstiltAvIdent = navIdent)
        val tildeltAnnen = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = "Z888888")
        lagreForelopigVedtak(db, tildeltAnnen, innstiltAvIdent = kollega)
        val tildeltInhabil = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = kollega)
        lagreForelopigVedtak(db, tildeltInhabil, innstiltAvIdent = kollega)
        val underBehandling = lagreSak(db, lagreSoknad(db))
        val oppsett = oppsett(db, grupper = beggeRollerGrupper)

        val saker = oppsett.hentListe().associate { it.sakId to it.kanTildeleMegSomBeslutter }

        assertEquals(
            mapOf(
                ledig.toString() to true,
                sendtAvMeg.toString() to false,
                tildeltAnnen.toString() to false,
                tildeltInhabil.toString() to true,
                underBehandling.toString() to false,
            ),
            saker,
        )
    }

    @Test
    fun `kanTildeleMegSomBeslutter i detalj`() = testApplicationWithDatabase { db ->
        val tildeltAnnen = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = "Z888888")
        lagreForelopigVedtak(db, tildeltAnnen, innstiltAvIdent = kollega)
        val sendtAvMeg = lagreSak(db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING)
        lagreForelopigVedtak(db, sendtAvMeg, innstiltAvIdent = navIdent)
        val oppsett = oppsett(db, grupper = beslutterGrupper)

        with(oppsett.hentDetalj(tildeltAnnen)) {
            assertTrue(kanTildeleMegSomBeslutter, "detaljen tillater overtakelse")
            assertEquals("Navn Z888888", beslutterNavn)
            assertEquals(Oppgavetype.SOKNAD_BESLUTTER, oppgavetype)
        }
        assertFalse(oppsett.hentDetalj(sendtAvMeg).kanTildeleMegSomBeslutter)
    }

    @Test
    fun `harOppgaveForMeg for saksbehandler gjelder søknader, også når en kollega har saken`() =
        assertHarOppgaveForMeg(saksbehandlerGrupper, Oppgavetype.SOKNAD)

    @Test
    fun `harOppgaveForMeg for beslutter gjelder saker til beslutning, også når en annen beslutter har saken`() =
        assertHarOppgaveForMeg(beslutterGrupper, Oppgavetype.SOKNAD_BESLUTTER)

    @Test
    fun `harOppgaveForMeg med begge rollene gjelder begge oppgavetypene`() =
        assertHarOppgaveForMeg(beggeRollerGrupper, Oppgavetype.SOKNAD, Oppgavetype.SOKNAD_BESLUTTER)

    private fun assertHarOppgaveForMeg(grupper: String, vararg forventet: Oppgavetype) =
        testApplicationWithDatabase { db ->
            val soknad = lagreSak(db, lagreSoknad(db), saksbehandlerIdent = kollega)
            val beslutning = lagreSak(
                db, lagreSoknad(db), status = Saksstatus.TIL_BESLUTNING, beslutterIdent = "Z888888",
            )
            lagreForelopigVedtak(db, beslutning, innstiltAvIdent = kollega)
            lagreSak(db, lagreSoknad(db), status = Saksstatus.AVSLUTTET)
            val sakPerOppgavetype = mapOf(Oppgavetype.SOKNAD to soknad, Oppgavetype.SOKNAD_BESLUTTER to beslutning)
            val oppsett = oppsett(db, grupper = grupper)

            val faktisk = oppsett.hentListe().filter { it.harOppgaveForMeg }.map { UUID.fromString(it.sakId) }.toSet()

            assertEquals(forventet.map { sakPerOppgavetype.getValue(it) }.toSet(), faktisk)
        }

    private class Oppsett(
        val client: HttpClient,
        val audit: RecordingAuditLogger,
        val tilgangsmaskinKall: List<TilgangsmaskinKall>,
        val bulkKall: List<List<BrukerIdOgRegelsett>>,
        val veksledeTokens: List<String>,
        val ansattOppslag: List<String>,
    )

    private val tilgangsmaskinBody = AttributeKey<String>("tilgangsmaskinBody")

    private val standardGeografi =
        """[{"navn": "0301", "term": "Oslo"}, {"navn": "4601", "term": "Bergen"}, {"navn": "46", "term": "Vestland"}]"""

    private fun ApplicationTestBuilder.oppsett(
        db: TestDatabase,
        grupper: String = saksbehandlerGrupper,
        enheter: (navIdent: String) -> String = { egneEnheter },
        tilgangsmaskinSvar: suspend (io.ktor.server.application.ApplicationCall) -> Unit = {
            it.respond(HttpStatusCode.NoContent)
        },
        bulkSvar: suspend (io.ktor.server.application.ApplicationCall, identer: List<String>) -> Unit = { call, identer ->
            call.respondBulk(identer.map { EnkeltBulkRespons(brukerId = it, status = 204) })
        },
        geografiSvar: suspend (io.ktor.server.application.ApplicationCall) -> Unit = {
            it.respondText(standardGeografi, ContentType.Application.Json)
        },
    ): Oppsett {
        val audit = RecordingAuditLogger()
        val tilgangsmaskinKall = mutableListOf<TilgangsmaskinKall>()
        val bulkKall = mutableListOf<List<BrukerIdOgRegelsett>>()
        val veksledeTokens = mutableListOf<String>()
        val ansattOppslag = mutableListOf<String>()

        mockEntraProxyFull(
            ansattProvider = { ident ->
                ansattOppslag.add(ident)
                testAnsattJson(ident, navn = "Innlogget Saksbehandler")
            },
            enheterProvider = enheter,
            grupperProvider = { grupper },
        )
        externalServices {
            hosts(TilgangsmaskinClient.ingress) {
                routing {
                    post("/api/v1/kjerne") {
                        val body = call.receiveText()
                        call.attributes.put(tilgangsmaskinBody, body)
                        tilgangsmaskinKall.add(
                            TilgangsmaskinKall(
                                body = body,
                                authorization = call.request.headers[HttpHeaders.Authorization],
                            )
                        )
                        tilgangsmaskinSvar(call)
                    }
                    post("/api/v1/bulk/obo") {
                        val specs = defaultJson.decodeFromString<List<BrukerIdOgRegelsett>>(call.receiveText())
                        bulkKall.add(specs)
                        bulkSvar(call, specs.map { it.brukerId })
                    }
                }
            }
            hosts(NorgKlient.baseUrl) {
                routing {
                    get("/norg2/api/v1/kodeverk/geografi") { geografiSvar(call) }
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
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide(TilgangsmaskinClient::class)
                provide(NorgKlient::class)
                provide<KommunenavnCache> { KommunenavnCache(resolve()) }
                provide<ArcSightAuditClient> { ArcSightAuditClient(auditLogger = audit) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == gyldigToken) mockAzureAdIntrospectionResponse.withNavIdent(navIdent) else null
                    }
                }
            }

            configureAuthentication()
            configureSaksbehandlingSakApiV1()
            configureKommunefilterApiV1()
            configureServer()
        }

        return Oppsett(client, audit, tilgangsmaskinKall, bulkKall, veksledeTokens, ansattOppslag)
    }

    private suspend fun io.ktor.server.application.ApplicationCall.respondAvvisning(kode: String) = respondText(
        """
        {
          "title": "$kode",
          "status": 403,
          "brukerIdent": "$fnr",
          "navIdent": "$navIdent",
          "begrunnelse": "Begrunnelse for $kode",
          "kanOverstyres": false
        }
        """.trimIndent(),
        ContentType.parse("application/problem+json"),
        HttpStatusCode.Forbidden,
    )

    private suspend fun io.ktor.server.application.ApplicationCall.respondBulk(resultater: List<EnkeltBulkRespons>) =
        respondText(
            defaultJson.encodeToString(AggregertBulkRespons(ansattId = navIdent, resultater = resultater)),
            ContentType.Application.Json,
            HttpStatusCode.MultiStatus,
        )

    private fun lagreSoknad(db: TestDatabase, ansattNavn: String = "Ansatt NN", ansattFnr: String = fnr): UUID {
        val soknadId = UUID.randomUUID()
        transaction(db.config.jdbcDatabase) {
            SoknadTable.insert {
                it[id] = soknadId
                it[virksomhetsnummer] = "123456780"
                it[virksomhetsnavn] = "Eksempel Bedrift AS"
                it[kontaktpersonNavn] = "Kontaktperson NN"
                it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
                it[kontaktpersonTelefon] = "41519999"
                it[this.ansattFnr] = ansattFnr
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

    private fun lagreSak(
        db: TestDatabase,
        soknadId: UUID,
        saksbehandlerIdent: String? = null,
        behandlendeEnhet: String? = egenEnhet,
        status: Saksstatus = Saksstatus.UNDER_BEHANDLING,
        beslutterIdent: String? = null,
        kommunenummer: String? = null,
    ): UUID =
        transaction(db.config.jdbcDatabase) {
            SakTable.insert {
                it[this.kommunenummer] = kommunenummer
                it[this.soknadId] = soknadId
                it[this.status] = status.name
                it[this.beslutterIdent] = beslutterIdent
                it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
                it[this.behandlendeEnhet] = behandlendeEnhet
                it[this.saksbehandlerIdent] = saksbehandlerIdent
                it[saksbehandlerNavn] = saksbehandlerIdent?.let { "Navn $it" }
                it[beslutterNavn] = beslutterIdent?.let { "Navn $it" }
            }[SakTable.sakId]
        }

    private fun lagreForelopigVedtak(
        db: TestDatabase,
        sakId: UUID,
        innstiltAvIdent: String,
        versjon: Int = 1,
        tiltaksperiodeFra: LocalDate? = LocalDate(2026, 11, 22),
        tiltaksperiodeTil: LocalDate? = LocalDate(2026, 12, 31),
    ): Long = transaction(db.config.jdbcDatabase) {
        ForelopigVedtakTable.insert {
            it[this.sakId] = sakId
            it[this.versjon] = versjon
            it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
            it[utfall] = ForelopigUtfall.INNVILGE.name
            it[innvilgetBelop] = 9999
            it[this.tiltaksperiodeFra] = tiltaksperiodeFra
            it[this.tiltaksperiodeTil] = tiltaksperiodeTil
            it[begrunnelseDeltaker] = "Til deltaker"
            it[this.innstiltAvIdent] = innstiltAvIdent
            it[sendtTidspunkt] = Instant.parse("2026-11-01T10:00:00Z")
        }[ForelopigVedtakTable.id]
    }

    private fun hentSakRad(db: TestDatabase, sakId: UUID) = transaction(db.config.jdbcDatabase) {
        SakTable.selectAll().where { SakTable.sakId eq sakId }.single()
    }

    private fun hentEvents(db: TestDatabase): List<EventData> =
        transaction(db.config.jdbcDatabase) {
            QueuedEvents.selectAll().map { it.tilQueuedEvent().eventData }
        }

    private suspend fun Oppsett.tildel(sakId: UUID) =
        client.post("/api/saksbehandling/v1/saker/$sakId/tildeling") { bearerAuth(gyldigToken) }

    private suspend fun Oppsett.tildelBeslutter(sakId: UUID) =
        client.post("/api/saksbehandling/v1/saker/$sakId/tildeling/beslutter") { bearerAuth(gyldigToken) }

    private suspend fun Oppsett.hentListe() =
        client.get("/api/saksbehandling/v1/saker") { bearerAuth(gyldigToken) }.body<SakerResponse>().saker

    private suspend fun Oppsett.frigjoer(sakId: UUID) =
        client.delete("/api/saksbehandling/v1/saker/$sakId/tildeling") { bearerAuth(gyldigToken) }

    private suspend fun Oppsett.hentDetalj(sakId: UUID) =
        client.get("/api/saksbehandling/v1/saker/$sakId") { bearerAuth(gyldigToken) }.body<SakDetaljer>()
}
