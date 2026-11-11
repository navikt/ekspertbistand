package no.nav.ekspertbistand.event.handlers

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.dokdist.DistribuerJournalpostRequest
import no.nav.ekspertbistand.dokdist.DistribuerJournalpostResponse
import no.nav.ekspertbistand.dokdist.Distribusjonstidspunkt
import no.nav.ekspertbistand.dokdist.Distribusjonstype
import no.nav.ekspertbistand.dokdist.DokDistClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData.sampleSoknad
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.mockDokDist
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DistribuerSoknadKopiDeltakerTest {

    @Test
    fun `distribuerer journalposten og publiserer event med bestillingsId`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val requests = mutableListOf<DistribuerJournalpostRequest>()
        mockDokDist { request ->
            requests += request
            DistribuerJournalpostResponse(bestillingsId = "bestilling-1")
        }
        setupApplication(database)
        startApplication()

        val soknad = sampleSoknad.copy(id = UUID.randomUUID().toString())
        val result = application.dependencies.resolve<DistribuerSoknadKopiDeltaker>()
            .handle(journalfoertEvent(id = 1L, soknadId = soknad.id!!))

        assertIs<EventHandledResult.Success>(result)
        val request = requests.single()
        assertEquals("1234", request.journalpostId)
        assertEquals(Distribusjonstype.ANNET, request.distribusjonstype)
        assertEquals(Distribusjonstidspunkt.KJERNETID, request.distribusjonstidspunkt)
        assertEquals("EKSPERTBISTAND", request.bestillendeFagsystem)
        assertEquals("EKSPERTBISTAND", request.dokumentProdApp)

        transaction(database) {
            val queuedEvent = QueuedEvents.selectAll().single()[QueuedEvents.eventData]
            assertIs<EventData.SoknadKopiDeltakerDistribuert>(queuedEvent)
            assertEquals(1234, queuedEvent.journalpostId)
            assertEquals("bestilling-1", queuedEvent.bestillingsId)
            assertEquals(soknad.id, queuedEvent.soknad.id)
        }
    }

    @Test
    fun `idempotency guard hindrer dobbel distribusjon ved retry`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        var kall = 0
        mockDokDist {
            kall++
            DistribuerJournalpostResponse(bestillingsId = "bestilling-1")
        }
        setupApplication(database)
        startApplication()

        val handler = application.dependencies.resolve<DistribuerSoknadKopiDeltaker>()
        val event = journalfoertEvent(id = 2L)

        repeat(2) { handler.handle(event) }

        assertEquals(1, kall)
        transaction(database) {
            assertEquals(1, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `409 fra dokdist regnes som suksess`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokDist(status = HttpStatusCode.Conflict) {
            DistribuerJournalpostResponse(bestillingsId = "eksisterende-bestilling")
        }
        setupApplication(database)
        startApplication()

        val result = application.dependencies.resolve<DistribuerSoknadKopiDeltaker>()
            .handle(journalfoertEvent(id = 3L))

        assertIs<EventHandledResult.Success>(result)
        transaction(database) {
            val queuedEvent = QueuedEvents.selectAll().single()[QueuedEvents.eventData]
            assertIs<EventData.SoknadKopiDeltakerDistribuert>(queuedEvent)
            assertEquals("eksisterende-bestilling", queuedEvent.bestillingsId)
        }
    }

    @Test
    fun `feil fra dokdist gir transient feil`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokDist(status = HttpStatusCode.Gone) {
            DistribuerJournalpostResponse(bestillingsId = "ignoreres")
        }
        setupApplication(database)
        startApplication()

        val result = application.dependencies.resolve<DistribuerSoknadKopiDeltaker>()
            .handle(journalfoertEvent(id = 4L))

        assertIs<EventHandledResult.TransientError>(result)
        transaction(database) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }
}

private fun journalfoertEvent(id: Long, soknadId: String = UUID.randomUUID().toString()) = Event(
    id = id,
    data = EventData.SoknadKopiDeltakerJournalfoert(
        soknad = sampleSoknad.copy(id = soknadId),
        journalpostId = 1234,
    ),
)

private fun ApplicationTestBuilder.setupApplication(database: Database) {
    application {
        dependencies {
            provide { database }
            provide<HttpClient> { this@setupApplication.createClient {} }
            provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
            provide(DokDistClient::class)
            provide(DistribuerSoknadKopiDeltaker::class)
        }
    }
}
