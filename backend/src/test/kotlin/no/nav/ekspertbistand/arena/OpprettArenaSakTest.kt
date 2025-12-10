package no.nav.ekspertbistand.arena

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockTiltaksgjennomfoering
import no.nav.ekspertbistand.skjema.DTO
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class OpprettArenaSakTest {
    private val saksnummer = "202542"

    @Test
    fun `Event prosesseres og sak blir opprettet i Arena`() = testApplication {
        setupTestApplication()
        mockTiltaksgjennomfoering({
            // language=JSON
            """
            {
                "saksnummer": "$saksnummer"
            }    
            """
        })
        startApplication()

        val handler = OpprettArenaSak(application.dependencies.resolve(), application.dependencies.resolve())
        val event = Event(
            id = 1L, data = EventData.JournalpostOpprettet(
                skjema1, 123, 456, "1337"
            )
        )

        assertTrue(handler.handle(event) is EventHandledResult.Success)
    }
}

private val skjema1 = DTO.Skjema(
    id = UUID.randomUUID().toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = "1337", virksomhetsnavn = "foo bar AS", kontaktperson = DTO.Kontaktperson(
            navn = "Donald Duck", epost = "Donald@duck.co", telefonnummer = "12345678"
        )
    ),
    ansatt = DTO.Ansatt(
        fnr = "12345678910", navn = "Ole Olsen"
    ),
    ekspert = DTO.Ekspert(
        navn = "Egon Olsen",
        virksomhet = "Olsenbanden AS",
        kompetanse = "Bankran",
    ),
    behovForBistand = DTO.BehovForBistand(
        behov = "Tilrettelegging",
        begrunnelse = "Tilrettelegging på arbeidsplassen",
        estimertKostnad = "4200",
        timer = "16",
        tilrettelegging = "Spesialtilpasset kontor",
        startdato = LocalDate.parse("2024-11-15")
    ),
    nav = DTO.Nav(
        kontaktperson = "Navn Navnesen"
    ),
)


private fun ApplicationTestBuilder.setupTestApplication() {
    val client = createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }
    val db = TestDatabase().cleanMigrate()
    application {
        dependencies {
            provide { db.config.jdbcDatabase }
            provide<TokenProvider> {
                object : TokenProvider {
                    override suspend fun token(target: String): TokenResponse {
                        return TokenResponse.Success(
                            accessToken = "faketoken", expiresInSeconds = 3600
                        )
                    }
                }
            }
            provide<ArenaClient> { ArenaClient(resolve(), client) }
        }
    }
}
