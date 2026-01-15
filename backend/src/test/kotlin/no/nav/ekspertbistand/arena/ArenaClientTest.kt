package no.nav.ekspertbistand.arena

import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockTiltaksgjennomfoering
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import kotlin.test.assertEquals

class ArenaClientTest {

    @Test
    fun opprettTiltaksgjennomforing() = testApplication {
        val saksnummer = "202542"
        val tiltakgjennomforingId = 1337
        val opprettEkspertbistand = OpprettEkspertbistand(
            behandlendeEnhetId = "1",
            virksomhetsnummer = "2",
            ansattFnr = "3",
            periodeFom = LocalDate.parse("2025-11-12"),
            journalpostId = 10,
            dokumentId = 11,
        )

        val requestAssertions = mutableListOf<() -> Unit>()
        mockTiltaksgjennomfoering(responseProvider = {
            requestAssertions.add {
                JSONAssert.assertEquals(
                    """
                    {
                      "bedriftsnummer": "${opprettEkspertbistand.virksomhetsnummer}",
                      "tiltaksgjennomfoering": {
                        "tiltaksvariant": "EKSPEBIST",
                        "behandlendeEnhetId": "${opprettEkspertbistand.behandlendeEnhetId}",
                        "gjennomfoeringsperiode": {
                          "fom": "${opprettEkspertbistand.periodeFom}",
                          "tom": null
                        },
                        "person": {
                          "ident": "${opprettEkspertbistand.ansattFnr}"
                        }
                      },
                      "dokumentreferanse": {
                        "journalpostId": ${opprettEkspertbistand.journalpostId},
                        "dokumentId": ${opprettEkspertbistand.dokumentId}
                      }
                    }
                    """,
                    it,
                    JSONCompareMode.STRICT
                )
            }

            // language=JSON
            """
            {
                "saksnummer": "$saksnummer",
                "tiltakgjennomforingId": $tiltakgjennomforingId
            }    
            """
        })
        val arenaClient = ArenaClient(
            tokenProvider = mockTokenProvider,
            defaultHttpClient = client
        )
        arenaClient.opprettTiltaksgjennomfoering(opprettEkspertbistand).let {
            requestAssertions.forEach { assertion -> assertion() } // run assertions on the request payload
            assertEquals(saksnummer, it.saksnummer)
            assertEquals(2025, it.saksnummer.aar)
            assertEquals(42, it.saksnummer.loepenrSak)
            assertEquals(1337, it.tiltakgjennomforingId)
        }
    }
}


private val mockTokenProvider = object : AzureAdTokenProvider {
    override suspend fun token(target: String, additionalParameters: Map<String, String>): TokenResponse {
        return if (target == ArenaClient.targetAudience) TokenResponse.Success(
            "dummytolkien", 3600
        ) else TokenResponse.Error(
            TokenErrorResponse("eroor", "you shall not pass"),
            HttpStatusCode.BadRequest
        )
    }

}
