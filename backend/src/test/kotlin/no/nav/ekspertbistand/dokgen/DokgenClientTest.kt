package no.nav.ekspertbistand.dokgen

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.SkjemaStatus
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DokgenClientTest {

    @Test
    fun `lager soknad-pdf med korrekt payload`() = runBlocking {
        val pdf = "%PDF-mock".toByteArray()
        var captured = CapturedRequest()

        val client = dokgenClient(pdf) { captured = it }
        val payload = sampleSkjema()

        val response = client.genererSoknadPdf(payload)

        assertContentEquals(pdf, response)
        assertEquals("/template/soknad/create-pdf", captured.path)

        val body = requireNotNull(captured.body) { "Request body was not captured" }
        val jsonBody = Json.parseToJsonElement(body).jsonObject

        assertEquals("987654321", jsonBody["virksomhet"]!!.jsonObject["virksomhetsnummer"]!!.jsonPrimitive.content)
        val behov = jsonBody["behovForBistand"]!!.jsonObject
        assertTrue(behov["timer"]!!.jsonPrimitive.isString)
        assertEquals("12", behov["timer"]!!.jsonPrimitive.content)
        assertEquals("9000", behov["estimertKostnad"]!!.jsonPrimitive.content)
    }

    @Test
    fun `lager tilskuddbrev-pdf med korrekt payload`() = runBlocking {
        val pdf = "%PDF-mock".toByteArray()
        var captured = CapturedRequest()

        val client = dokgenClient(pdf) { captured = it }
        val payload = sampleTilskuddsbrev()

        val response = client.genererTilskuddsbrevPdf(payload)

        assertContentEquals(pdf, response)
        assertEquals("/template/tilskuddsbrev/create-pdf", captured.path)

        val body = requireNotNull(captured.body) { "Request body was not captured" }
        val jsonBody = Json.parseToJsonElement(body).jsonObject

        assertEquals("1337", jsonBody["tilsagnNummer"]!!.jsonObject["aar"]!!.jsonPrimitive.content)
        assertEquals("Ekspertbistand", jsonBody["tiltakNavn"]!!.jsonPrimitive.content)
    }

    private fun dokgenClient(pdf: ByteArray, capture: (CapturedRequest) -> Unit): DokgenClient {
        val engine = MockEngine { request ->
            capture(
                CapturedRequest(
                    path = request.url.fullPath,
                    body = (request.body as TextContent).text
                )
            )
            respond(
                content = pdf,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
            )
        }

        return DokgenClient(
            defaultHttpClient = HttpClient(engine) {},
        )
    }

    private fun sampleTilskuddsbrev() = TilsagnData(
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

    private fun sampleSkjema() = DTO.Skjema(
        id = "42",
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Kontakt Person",
                epost = "kontakt@testbedrift.no",
                telefonnummer = "12345678",
            )
        ),
        ansatt = DTO.Ansatt(
            fnr = "01010112345",
            navn = "Ansatt Navn",
        ),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            kompetanse = "Ekspertise",
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2024, 12, 1),
        ),
        nav = DTO.Nav(
            kontaktperson = "Veileder Navn"
        ),
        status = SkjemaStatus.innsendt,
    )

    private data class CapturedRequest(
        val path: String? = null,
        val body: String? = null,
    )
}
