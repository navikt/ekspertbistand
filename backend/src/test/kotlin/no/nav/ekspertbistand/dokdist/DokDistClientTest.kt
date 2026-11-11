package no.nav.ekspertbistand.dokdist

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockDokDist
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DokDistClientTest {
    @Test
    fun `distribuerer journalpost`() = testApplication {
        mockDokDist { request ->
            assertEquals("123456789", request.journalpostId)
            assertEquals("EKSPERTBISTAND", request.bestillendeFagsystem)
            assertEquals("EKSPERTBISTAND", request.dokumentProdApp)
            assertEquals(Distribusjonstype.VEDTAK, request.distribusjonstype)
            assertEquals(Distribusjonstidspunkt.KJERNETID, request.distribusjonstidspunkt)
            assertEquals(null, request.batchId)
            DistribuerJournalpostResponse("bestilling-1")
        }

        val response = dokDistClient().distribuerJournalpost(
            journalpostId = "123456789",
            distribusjonstype = Distribusjonstype.VEDTAK,
        )

        assertEquals("bestilling-1", response.bestillingsId)
    }

    @Test
    fun `allerede distribuert journalpost (409) behandles som suksess`() = testApplication {
        mockDokDist(status = HttpStatusCode.Conflict) {
            DistribuerJournalpostResponse("eksisterende-bestilling")
        }

        val response = dokDistClient().distribuerJournalpost(
            journalpostId = "123456789",
            distribusjonstype = Distribusjonstype.VEDTAK,
        )

        assertEquals("eksisterende-bestilling", response.bestillingsId)
    }

    @Test
    fun `feilrespons kaster exception`() = testApplication {
        mockDokDist(status = HttpStatusCode.BadRequest) {
            DistribuerJournalpostResponse("ignored")
        }

        assertFailsWith<IllegalStateException> {
            dokDistClient().distribuerJournalpost(
                journalpostId = "123456789",
                distribusjonstype = Distribusjonstype.VEDTAK,
            )
        }
    }

    private fun ApplicationTestBuilder.dokDistClient() = DokDistClient(
        azureAdTokenProvider = mockTokenProvider,
        defaultHttpClient = createClient {
            install(ContentNegotiation) {
                json()
            }
        },
    )
}

private val mockTokenProvider = object : AzureAdTokenProvider {
    override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse {
        return if (target == DokDistClient.targetAudience) TokenResponse.Success(
            "dummytoken", 3600
        ) else TokenResponse.Error(
            TokenErrorResponse("error", "you shall not pass"),
            HttpStatusCode.BadRequest
        )
    }
}
