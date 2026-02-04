package no.nav.ekspertbistand.event.handlers

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.ArenaSakTable
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.mockTiltaksgjennomfoering
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class OpprettTiltaksgjennomfoeringForInnsendtSoknadTest {
    private val saksnummer = "202542"

    @Test
    fun `Event prosesseres og sak blir opprettet i Arena`() = testApplicationWithDatabase {
        setupTestApplication()
        mockTiltaksgjennomfoering {
            // language=JSON
            """
            {
                "saksnummer": "$saksnummer",
                "tiltaksgjennomfoeringId": 1337
            }    
            """
        }
        startApplication()

        val handler = OpprettTiltaksgjennomfoeringForInnsendtSoknad(application.dependencies.resolve(), application.dependencies.resolve())
        val event = Event(
            id = 1L, data = EventData.InnsendtSoknadJournalfoert(
                soknad1, 123, 456, "1337"
            )
        )

        assertIs<EventHandledResult.Success>(handler.handle(event))

        val database = application.dependencies.resolve<Database>()
        transaction(database) {
            val lagredeSaker = ArenaSakTable.selectAll()
            assertEquals(1, lagredeSaker.count())
            assertEquals(saksnummer, lagredeSaker.first()[ArenaSakTable.saksnummer])

            val queuedEvents = QueuedEvents.selectAll()
            assertEquals(1, queuedEvents.count())
            assertIs<EventData.TiltaksgjennomforingOpprettet>(queuedEvents.first()[QueuedEvents.eventData])
        }
    }

    @Test
    fun `Event prosesseres, men kall mot arena feiler`() = testApplicationWithDatabase {
        setupTestApplication()
        mockTiltaksgjennomfoering {
            throw RuntimeException("Feil ved oppretting av Arena")
        }
        startApplication()

        val handler = OpprettTiltaksgjennomfoeringForInnsendtSoknad(application.dependencies.resolve(), application.dependencies.resolve())
        val event = Event(
            id = 1L, data = EventData.InnsendtSoknadJournalfoert(
                soknad1, 123, 456, "1337"
            )
        )

        assertIs<EventHandledResult.TransientError>(handler.handle(event))
        val database = application.dependencies.resolve<Database>()
        transaction(database) {
            assertEquals(0, ArenaSakTable.selectAll().count())
        }
    }
}

private val soknad1 = DTO.Soknad(
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
    status = SoknadStatus.innsendt,
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
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide<ArenaClient> { ArenaClient(resolve(), client) }
        }
    }
}
