package no.nav.ekspertbistand.dokarkiv

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.OpprettJournalpostRequest
import no.nav.ekspertbistand.mocks.mockDokArkiv
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DokArkivClientTest {
    @Test
    fun opprettJournalpost() = testApplication {
        val dokumentPdfAsBytes = ByteArray(10) { 0x1F.toByte() }

        mockDokArkiv { request: OpprettJournalpostRequest ->
            assertEquals("Søknad om tilskudd til ekspertbistand", request.tittel)
            assertEquals("123456789", request.bruker.id)
            assertEquals("ORGNR", request.bruker.idType)
            assertEquals("123456789", request.avsenderMottaker.id)
            assertEquals("ORGNR", request.avsenderMottaker.idType)
            assertEquals("ekstern-ref-123-innsendt-skjema", request.eksternReferanseId)
            assertEquals("9999", request.journalfoerendeEnhet)
            assertEquals("INNGAAENDE", request.journalposttype)
            assertEquals("NAV_NO", request.kanal)
            assertEquals("TIL", request.tema)
            // Se https://kodeverk.ansatt.nav.no/kodeverk/Behandlingstema/21824
            assertEquals("ab0423", request.behandlingstema)
            assertEquals("GENERELL_SAK", request.sak.sakstype)

            val dokument = request.dokumenter.single()
            assertEquals("Søknad om tilskudd til ekspertbistand", dokument.tittel)
            assertEquals("5384", dokument.brevkode)
            val dokumentVariant = dokument.dokumentvarianter.single()
            assertEquals(encodeToBase64(dokumentPdfAsBytes), dokumentVariant.fysiskDokument)
            assertEquals("PDFA", dokumentVariant.filtype)
            assertEquals("ARKIV", dokumentVariant.variantformat)

            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("DOC001")),
                journalpostId = "DOK123456",
                journalpostferdigstilt = true
            )
        }

        client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }

        val dokArkivClient = DokArkivClient(
            authClient = mockTokenProvider,
            httpClient = client
        )

        dokArkivClient.opprettOgFerdigstillJournalpost(
            tittel = "Søknad om tilskudd til ekspertbistand",
            virksomhetsnummer = "123456789",
            eksternReferanseId = "ekstern-ref-123",
            dokumentPdfAsBytes = dokumentPdfAsBytes
        ).let {
            assertEquals("DOK123456", it.journalpostId)
        }
    }
}

private val mockTokenProvider = object : TokenProvider {
    override suspend fun token(target: String): TokenResponse {
        return if (target == DokArkivClient.targetAudience) TokenResponse.Success(
            "dummytoken", 3600
        ) else TokenResponse.Error(
            TokenErrorResponse("error", "you shall not pass"),
            HttpStatusCode.BadRequest
        )
    }
}
