package no.nav.ekspertbistand.saksbehandling

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

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
    fun `oppsett gir standardtekster og maks satser`() = testApplicationWithDatabase { db ->
        val client = oppsett(db, """[{ "rolle": "${Role.SAKSBEHANDLER.groupId}" }]""")

        val response = client.get(url) { bearerAuth(gyldigToken) }

        assertEquals(HttpStatusCode.OK, response.status)
        val oppsett = response.body<ForelopigVedtakOppsett>()
        assertEquals(lastStandardtekster(), oppsett.standardtekster)
        assertEquals(MAKS_SATSER, oppsett.maksSatser)
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
