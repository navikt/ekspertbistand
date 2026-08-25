package no.nav.ekspertbistand.ereg

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EregClientTest {
    @Test
    fun `hentPostAdresse henter og parser adresser`() = runTest {
        val orgnr = "910825226"
        val responseJson = """
            {
              "organisasjonsnummer": "$orgnr",
              "navn": { "sammensattnavn": "Test Org" },
              "organisasjonDetaljer": {
                "postadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "adresselinje2": "C/O NAV",
                    "postnummer": "0557",
                    "poststed": "Oslo",
                    "landkode": "NO"
                  }
                ],
                "forretningsadresser": [
                  { "adresselinje1": "Forretning 1", "postnummer": "0456" }
                ]
              }
            }
        """.trimIndent()

        var capturedRequest: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = responseJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val eregClient = EregClient(defaultHttpClient = client)

        val postadresser = eregClient.hentPostAdresse(orgnr)
        assertEquals(1, postadresser.size)
        val adresse = postadresser.first()
        assertEquals("Testveien 1", adresse.adresselinje1)
        assertEquals("0557", adresse.postnummer)
        assertEquals("Oslo", adresse.poststed)

        val forretningsadresser = eregClient.hentForretningsadresse(orgnr)
        assertEquals(1, forretningsadresser.size)
        assertEquals("Forretning 1", forretningsadresser.first().adresselinje1)

        val organisasjon = eregClient.hentOrganisasjon(orgnr)
        assertEquals(orgnr, organisasjon.organisasjonsnummer)
        assertEquals("Forretning 1", organisasjon.organisasjonDetaljer?.forretningsadresser?.first()?.adresselinje1)

        val request = capturedRequest
        assertNotNull(request, "request should be captured")
        assertEquals("/v2/organisasjon/$orgnr", request.url.fullPath)
        assertEquals(ContentType.Application.Json, request.headers[HttpHeaders.Accept]?.let { ContentType.parse(it) })
        assertEquals(
            ContentType.Application.Json,
            request.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) })
    }

    @Test
    fun `finnOrganisasjon soeker og parser treff`() = runTest {
        val responseJson = """
            {
  "totalAntallTreff": 10,
  "organisasjonSammendrag": [
    {
      "organisasjonsnummer": "993218804",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV ÅS",
      "navnelinje1": "NAV ÅS",
      "juridiskEnhetOrganisasjonsnummer": "974652250",
      "adresselinje1": "Moerveien 2",
      "postnummer": "1430",
      "poststed": "ÅS",
      "kommunenummer": "0214",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0214>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "991370307",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV OS",
      "navnelinje1": "NAV OS",
      "juridiskEnhetOrganisasjonsnummer": "974652358",
      "adresselinje1": "Bjørnegården",
      "postnummer": "5200",
      "poststed": "OS",
      "kommunenummer": "1243",
      "kommunenavn": "<Fant ingen gyldig term for kode: 1243>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "994109790",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV BØ",
      "navnelinje1": "NAV BØ",
      "juridiskEnhetOrganisasjonsnummer": "975605779",
      "adresselinje1": "Forøy",
      "postnummer": "8475",
      "poststed": "STRAUMSJØEN",
      "kommunenummer": "1867",
      "kommunenavn": "Bø",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "994477072",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV HÅ",
      "navnelinje1": "NAV HÅ",
      "juridiskEnhetOrganisasjonsnummer": "974652285",
      "adresselinje1": "Jardarvegen 3A",
      "postnummer": "4365",
      "poststed": "NÆRBØ",
      "kommunenummer": "1119",
      "kommunenavn": "Hå",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "993157775",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV ÅL",
      "navnelinje1": "NAV ÅL",
      "juridiskEnhetOrganisasjonsnummer": "974237059",
      "adresselinje1": "Torget 1",
      "postnummer": "3570",
      "poststed": "ÅL",
      "kommunenummer": "0619",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0619>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "993622680",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV RE",
      "navnelinje1": "NAV RE",
      "juridiskEnhetOrganisasjonsnummer": "974761750",
      "adresselinje1": "Revetalgata 5",
      "postnummer": "3174",
      "poststed": "REVETAL",
      "kommunenummer": "0716",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0716>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "993157481",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV GOL",
      "navnelinje1": "NAV GOL",
      "juridiskEnhetOrganisasjonsnummer": "974237059",
      "adresselinje1": "Gamlevegen 4",
      "postnummer": "3550",
      "poststed": "GOL",
      "kommunenummer": "0617",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0617>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "993156523",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV NES",
      "navnelinje1": "NAV NES",
      "juridiskEnhetOrganisasjonsnummer": "974237059",
      "adresselinje1": "Jordeslykkja 6",
      "postnummer": "3540",
      "poststed": "NESBYEN",
      "kommunenummer": "0616",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0616>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "994672304",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV FET",
      "navnelinje1": "NAV FET",
      "juridiskEnhetOrganisasjonsnummer": "974652250",
      "adresselinje1": "Kirkeveien 85",
      "postnummer": "1900",
      "poststed": "FETSUND",
      "kommunenummer": "0227",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0227>",
      "landkode": "NO",
      "opphoert": false
    },
    {
      "organisasjonsnummer": "994477978",
      "enhetstype": "BEDR",
      "sammensattnavn": "NAV NES",
      "navnelinje1": "NAV NES",
      "juridiskEnhetOrganisasjonsnummer": "974652250",
      "adresselinje1": "Pakkhusgata 1D",
      "postnummer": "2150",
      "poststed": "ÅRNES",
      "kommunenummer": "0236",
      "kommunenavn": "<Fant ingen gyldig term for kode: 0236>",
      "landkode": "NO",
      "opphoert": false
    }
  ]
}
        """.trimIndent()

        var capturedRequest: HttpRequestData? = null
        val mockEngine = MockEngine { request ->
            capturedRequest = request
            respond(
                content = responseJson,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val eregClient = EregClient(defaultHttpClient = client)

        val resultat = eregClient.finnOrganisasjon("NAV")
        assertEquals(10, resultat.totalAntallTreff)
        assertEquals(10, resultat.organisasjonSammendrag.size)
        assertEquals("993218804", resultat.organisasjonSammendrag.first().organisasjonsnummer)
        assertEquals("NAV ÅS", resultat.organisasjonSammendrag.first().sammensattnavn)
        assertEquals("BEDR", resultat.organisasjonSammendrag.first().enhetstype)

        val request = capturedRequest
        assertNotNull(request, "request should be captured")
        assertEquals("/v2/organisasjon/finn", request.url.encodedPath)
        assertEquals("NAV", request.url.parameters["organisasjonsnavn"])
    }
}
