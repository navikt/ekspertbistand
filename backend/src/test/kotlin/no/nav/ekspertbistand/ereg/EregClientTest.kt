package no.nav.ekspertbistand.ereg

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import no.nav.ekspertbistand.mocks.mockEreg
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import kotlin.test.assertEquals

class EregClientTest {
    @Test
    fun `hentPostAdresse ignores unknown fields and returns postadresser`() = testApplication {
        mockEreg { orgnr ->
            """
            {
              "organisasjonsnummer": "$orgnr",
              "organisasjonDetaljer": {
                "postadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "adresselinje2": "C/O NAV",
                    "postnummer": "0557",
                    "poststed": "Oslo",
                    "landkode": "NO",
                    "unknownField": "skal ignoreres"
                  }
                ],
                "ukjentNivaa": "ignored"
              },
              "ukjentRot": "ignored"
            }
            """.trimIndent()
        }

        client = createClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                    }
                )
            }
        }

        val eregClient = EregClient(httpClient = client)

        val postadresser = eregClient.hentPostAdresse("910825226")
        assertEquals(1, postadresser.size)
        val adresse = postadresser.first()
        assertEquals("Testveien 1", adresse.adresselinje1)
        assertEquals("0557", adresse.postnummer)
        assertEquals("Oslo", adresse.poststed)
    }
}
