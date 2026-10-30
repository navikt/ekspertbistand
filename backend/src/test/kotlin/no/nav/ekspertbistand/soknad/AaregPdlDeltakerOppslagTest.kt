package no.nav.ekspertbistand.soknad

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import no.nav.ekspertbistand.aareg.AaregClient
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.pdl.PdlApiKlient
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AaregPdlDeltakerOppslagTest {

    private val orgnr = "910456900"

    @Test
    fun `treff krever arbeidsforhold med virksomhetsnummeret som ident`() = runTest {
        assertTrue(oppslag(arbeidsforhold(orgnr)).harAktivtArbeidsforhold("01019012345", orgnr))
        assertFalse(oppslag(arbeidsforhold("910456901")).harAktivtArbeidsforhold("01019012345", orgnr))
        assertFalse(oppslag("[]").harAktivtArbeidsforhold("01019012345", orgnr))
        assertFalse(oppslag("""[{ "arbeidssted": null }]""").harAktivtArbeidsforhold("01019012345", orgnr))
    }

    @Test
    fun `404 fra Aareg betyr ingen arbeidsforhold`() = runTest {
        assertFalse(oppslag("{}", HttpStatusCode.NotFound).harAktivtArbeidsforhold("01019012345", orgnr))
    }

    @Test
    fun `andre feil fra Aareg kastes`() = runTest {
        assertFailsWith<Exception> {
            oppslag("{}", HttpStatusCode.InternalServerError).harAktivtArbeidsforhold("01019012345", orgnr)
        }
    }

    private fun arbeidsforhold(ident: String) =
        // language=JSON
        """[{ "arbeidssted": { "type": "Underenhet", "identer": [{ "type": "ORGANISASJONSNUMMER", "ident": "$ident" }] } }]"""

    private fun oppslag(body: String, status: HttpStatusCode = HttpStatusCode.OK): AaregPdlDeltakerOppslag {
        val httpClient = HttpClient(MockEngine {
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { expectSuccess = true }
        return AaregPdlDeltakerOppslag(
            AaregClient(successAzureAdTokenProvider, httpClient),
            PdlApiKlient(successAzureAdTokenProvider, httpClient),
        )
    }
}
