package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.handlers.settStatus
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.oebs.integration.NavEnhetNummer
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ForelopigVedtakOppsettTest {

    private val navIdent = "A123456"
    private val gyldigToken = "valid-azure-token"
    private val url = "/api/saksbehandling/v1/forelopig-vedtak/oppsett"

    @Test
    fun `standardtekstene har bare tekst til arbeidsgiver for redusert beløp`() {
        val tekster = lastStandardtekster()

        assertEquals(setOf(ForelopigUtfall.INNVILGE_REDUSERT), tekster.keys)
        assertTrue(tekster.values.all { it.arbeidsgiver.isNotBlank() })
    }

    @Test
    fun `manglende ressurs stopper oppstart`() {
        assertThrows<IllegalArgumentException> { lastStandardtekster("/finnes/ikke.json") }
    }

    @Test
    fun `oppsett gir standardtekster, maks satser og kostnadssteder`() = testApplicationWithDatabase { db ->
        val client = oppsett(db, """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]""")

        val response = client.get(url) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val oppsett = response.body<ForelopigVedtakOppsett>()
        assertEquals(lastStandardtekster(), oppsett.standardtekster)
        assertEquals(MAKS_SATSER, oppsett.maksSatser)
        assertEquals(KOSTNADSSTEDER, oppsett.kostnadssteder)
        assertEquals(emptyList(), oppsett.sistBrukteKostnadssteder)
    }

    @Test
    fun `sist brukte er de tre siste ulike kostnadsstedene saksbehandleren har brukt`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        listOf(
            navIdent to "0291",
            navIdent to "0391",
            navIdent to "0291",
            navIdent to "9999",
            navIdent to "0491",
            navIdent to "0691",
            "Z999999" to "1091",
        ).forEachIndexed { i, (ident, kostnadssted) -> innstill(database, ident, kostnadssted, i) }
        val client = oppsett(db, """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]""")

        val oppsett = client.get(url) { bearerAuth(gyldigToken) }.body<ForelopigVedtakOppsett>()

        assertEquals(listOf("0691", "0491", "0291"), oppsett.sistBrukteKostnadssteder.map { it.enhetsnummer })
        assertEquals("Nav arbeidslivssenter Vest-Viken", oppsett.sistBrukteKostnadssteder.first().navn)
    }

    @Test
    fun `kostnadsstedene har unike enhetsnumre som OeBS godtar`() {
        assertEquals(KOSTNADSSTEDER.size, KOSTNADSSTEDER.map { it.enhetsnummer }.toSet().size)
        KOSTNADSSTEDER.forEach { NavEnhetNummer(it.enhetsnummer) }
    }

    private fun innstill(database: Database, ident: String, kostnadssted: String, nr: Int) {
        val sak = lagreSoknadOgSak(database)
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
        transaction(database) {
            lagreForelopigVedtak(
                EventData.ForelopigVedtakSendtTilBeslutter(
                    sakId = sak.sakId.toString(),
                    soknadId = sak.soknadId.toString(),
                    utfall = ForelopigUtfall.INNVILGE,
                    innvilgetBelop = 9999,
                    tiltaksperiodeFra = LocalDate(2026, 11, 22),
                    tiltaksperiodeTil = LocalDate(2026, 12, 31),
                    begrunnelseDeltaker = "Til deltaker",
                    begrunnelseArbeidsgiver = null,
                    innstiltAvIdent = ident,
                    tidspunkt = Instant.parse("2026-11-23T08:00:00Z") + nr.minutes,
                    kostnadssted = kostnadssted,
                ),
                eventId = 100L + nr,
            )
        }
    }

    @Test
    fun `oppsett uten saksbehandlerrolle gir 403`() = testApplicationWithDatabase { db ->
        val client = oppsett(db, """[{ "rolle": "${Role.BESLUTTER.groupId}" }]""")

        val response = client.get(url) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private fun ApplicationTestBuilder.oppsett(db: TestDatabase, grupper: String): HttpClient {
        mockEntraProxyFull(
            enheterProvider = { """[{ "enhetnummer": "1234", "navn": "Nav Test" }]""" },
            grupperProvider = { grupper },
        )
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
        return client
    }
}
