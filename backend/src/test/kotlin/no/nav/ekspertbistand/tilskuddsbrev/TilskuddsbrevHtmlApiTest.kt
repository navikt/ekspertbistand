package no.nav.ekspertbistand.tilskuddsbrev

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.utils.io.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient.Companion.altinn3Ressursid
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.tilsagndata.TilskuddsbrevHtml
import no.nav.ekspertbistand.tilsagndata.configureTilsagnDataApiV1
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TilskuddsbrevHtmlApiTest {
    @Test
    fun `Hent tilskuddsbrev html`() = testApplicationWithDatabase { testDb ->
        mockAltinnTilganger(
            AltinnTilgangerClientResponse(
                isError = false,
                hierarki = emptyList(),
                orgNrTilTilganger = mapOf(
                    "1337" to setOf(altinn3Ressursid)
                ),
                tilgangTilOrgNr = mapOf(
                    altinn3Ressursid to setOf("1337"),
                )
            )
        )
        client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val altinnTilgangerClient = AltinnTilgangerClient(
            defaultHttpClient = client,
            tokenExchanger = successTokenXTokenExchanger
        )
        val dokgenClient = DokgenClient(
            defaultHttpClient = HttpClient(MockEngine {
                respond(
                    content = ByteReadChannel("<html>Mock tilskuddsbrev</html>"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                )
            }),
        )
        application {
            dependencies {
                provide {
                    testDb.config.jdbcDatabase
                }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
                provide {
                    altinnTilgangerClient
                }
                provide {
                    dokgenClient
                }
            }

            configureTokenXAuth()
            configureServer()
            configureTilsagnDataApiV1()
        }

        val skjemaId = UUID.randomUUID()

        transaction(testDb.config.jdbcDatabase) {
            insertTilsagndata(skjemaId, sampleTilsagnData)
        }

        with(client.get("/api/tilsagndata/v1/$skjemaId/tilskuddsbrev-html") {
            bearerAuth("faketoken")
        }) {
            assertEquals(HttpStatusCode.OK, status)
            body<List<TilskuddsbrevHtml>>().also { htmlList ->
                assertEquals(1, htmlList.size)
                assertEquals("1337-42-43", htmlList.first().tilsagnNummer)
                assertEquals("<html>Mock tilskuddsbrev</html>", htmlList.first().html)
            }
        }
    }

    private val sampleTilsagnData = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(
            1337,
            42,
            43,
        ),
        tilsagnDato = "01.01.2021",
        periode = TilsagnData.Periode(
            fraDato = "01.01.2021",
            tilDato = "01.02.2021"
        ),
        tiltakKode = "42",
        tiltakNavn = "Ekspertbistand",
        administrasjonKode = "etellerannet",
        refusjonfristDato = "10.01.2021",
        tiltakArrangor = TilsagnData.TiltakArrangor(
            arbgiverNavn = "Arrangøren",
            landKode = "1337",
            postAdresse = "et sted",
            postNummer = "1337",
            postSted = "hos naboen",
            orgNummerMorselskap = 43,
            orgNummer = 42,
            kontoNummer = "1234.12.12345",
            maalform = "norsk"
        ),
        totaltTilskuddbelop = 24000,
        valutaKode = "NOK",
        tilskuddListe = listOf(
            TilsagnData.Tilskudd(
                tilskuddType = "ekspertbistand",
                tilskuddBelop = 24000,
                visTilskuddProsent = false,
                tilskuddProsent = null
            )
        ),
        deltaker = TilsagnData.Deltaker(
            fodselsnr = "42",
            fornavn = "navn",
            etternavn = "navnesen",
            landKode = "NO",
            postAdresse = "et sted",
            postNummer = "1234",
            postSted = "hos den andre naboen",
        ),
        antallDeltakere = 1,
        antallTimeverk = 100,
        navEnhet = TilsagnData.NavEnhet(
            navKontorNavn = "kontor1",
            navKontor = "Kontor1",
            postAdresse = "hos den tredje",
            postNummer = "1234",
            postSted = "hos den tredje",
            telefon = "12341234",
            faks = null
        ),
        beslutter = TilsagnData.Person(
            fornavn = "Ole",
            etternavn = "Brum",
        ),
        saksbehandler = TilsagnData.Person(
            fornavn = "Nasse",
            etternavn = "Nøff",
        ),
        kommentar = "Dette var unødvendig mye testdata å skrive"
    )
}
