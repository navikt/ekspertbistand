package no.nav.ekspertbistand.arena

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.TokenErrorResponse
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockTiltaksgjennomfoering
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArenaClientTest {

    @Test
    fun opprettTiltaksgjennomforing() = testApplication {
        val saksnummer = "202542"
        val opprettEkspertbistand = OpprettEkspertbistand(
            behandlendeEnhetId = "1",
            virksomhetsnummer = "2",
            ansattFnr = "3",
            periodeFom = LocalDate.parse("2025-11-12"),
            journalpostId = 10,
            dokumentId = 11,
        )

        mockTiltaksgjennomfoering(responseProvider = {
            with(it) {
                assertEquals(opprettEkspertbistand.behandlendeEnhetId, behandlendeEnhetId)
                assertEquals(opprettEkspertbistand.virksomhetsnummer, bedriftsnummer)
                assertEquals(opprettEkspertbistand.ansattFnr, personIdent)
                assertEquals(EKSPERTBISTAND_TILTAKSKODE, tiltakskode)
                assertEquals(opprettEkspertbistand.periodeFom, gjennomfoeringsperiode.fom)
                assertEquals(null, gjennomfoeringsperiode.tom)
                assertEquals(opprettEkspertbistand.journalpostId, dokumentreferanse.journalpostId)
                assertEquals(opprettEkspertbistand.dokumentId, dokumentreferanse.dokumentId)
            }
            OpprettTiltaksgjennomfoeringResponse(saksnummer)
        })
        client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val arenaClient = ArenaClient(
            authClient = mockTokenProvider,
            httpClient = client
        )
        arenaClient.opprettTiltaksgjennomfoering(opprettEkspertbistand).let {
            assertEquals(saksnummer, it.saksnummer)
            assertEquals(2025, it.aar)
            assertEquals(42, it.loepenrSak)
        }
    }
}


private val mockTokenProvider = object : TokenProvider {
    override suspend fun token(target: String): TokenResponse {
        return if (target == ArenaClient.targetAudience) TokenResponse.Success(
            "dummytolkien", 3600
        ) else TokenResponse.Error(
            TokenErrorResponse("eroor", "you shall not pass"),
            HttpStatusCode.BadRequest
        )
    }

}
