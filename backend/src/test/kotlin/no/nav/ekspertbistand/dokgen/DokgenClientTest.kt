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
import no.nav.ekspertbistand.skjema.DTO
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
        )
    )

    private data class CapturedRequest(
        val path: String? = null,
        val body: String? = null,
    )
}
