package no.nav.ekspertbistand.event.handlers

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostDokument
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData.sampleSoknad
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.OpprettJournalpostRequest
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.mocks.mockDokArkiv
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JournalfoerSoknadKopiDeltakerTest {

    @Test
    fun `journalforer kopi som utgaaende paa deltakeren og publiserer event`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val requests = mutableListOf<OpprettJournalpostRequest>()
        mockDokArkiv { request ->
            requests += request
            ferdigstiltResponse
        }
        setupApplication(database)
        startApplication()

        val soknad = sampleSoknad.copy(id = UUID.randomUUID().toString())
        val result = application.dependencies.resolve<JournalfoerSoknadKopiDeltaker>()
            .handle(Event(id = 1L, data = EventData.SoknadInnsendt(soknad)))

        assertIs<EventHandledResult.Success>(result)
        val request = requests.single()
        assertEquals("UTGAAENDE", request.journalposttype)
        assertEquals(soknad.ansatt.fnr, request.bruker.id)
        assertEquals("FNR", request.bruker.idType)
        assertEquals(soknad.ansatt.fnr, request.avsenderMottaker?.id)
        assertEquals("FNR", request.avsenderMottaker?.idType)
        assertEquals("${soknad.id}-kopi-deltaker", request.eksternReferanseId)
        assertEquals("Kopi av søknad om tilskudd til ekspertbistand", request.tittel)
        assertEquals("NAV_NO", request.kanal)

        transaction(database) {
            val queuedEvent = QueuedEvents.selectAll().single()[QueuedEvents.eventData]
            assertIs<EventData.SoknadKopiDeltakerJournalfoert>(queuedEvent)
            assertEquals(1234, queuedEvent.journalpostId)
            assertEquals(soknad.id, queuedEvent.soknad.id)
        }
    }

    @Test
    fun `idempotency guard hindrer duplikat ved retry`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        var kall = 0
        mockDokArkiv {
            kall++
            ferdigstiltResponse
        }
        setupApplication(database)
        startApplication()

        val handler = application.dependencies.resolve<JournalfoerSoknadKopiDeltaker>()
        val event = Event(
            id = 2L,
            data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString())),
        )

        repeat(2) { handler.handle(event) }

        assertEquals(1, kall)
        transaction(database) {
            assertEquals(1, QueuedEvents.selectAll().count())
        }
    }

    @Test
    fun `bruker eksisterende journalpost naar dokarkiv svarer 409`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokArkiv(status = HttpStatusCode.Conflict) { ferdigstiltResponse }
        setupApplication(database)
        startApplication()

        val result = application.dependencies.resolve<JournalfoerSoknadKopiDeltaker>().handle(
            Event(id = 3L, data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString())))
        )

        assertIs<EventHandledResult.Success>(result)
        transaction(database) {
            val queuedEvent = QueuedEvents.selectAll().single()[QueuedEvents.eventData]
            assertIs<EventData.SoknadKopiDeltakerJournalfoert>(queuedEvent)
            assertEquals(1234, queuedEvent.journalpostId)
        }
    }

    @Test
    fun `gir transient feil naar journalposten ikke er ferdigstilt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokArkiv { ferdigstiltResponse.copy(journalpostferdigstilt = false) }
        setupApplication(database)
        startApplication()

        val result = application.dependencies.resolve<JournalfoerSoknadKopiDeltaker>().handle(
            Event(id = 4L, data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString())))
        )

        assertIs<EventHandledResult.TransientError>(result)
        transaction(database) {
            assertEquals(0, QueuedEvents.selectAll().count())
        }
    }
}

private val ferdigstiltResponse = OpprettJournalpostResponse(
    dokumenter = listOf(OpprettJournalpostDokument("9876")),
    journalpostId = "1234",
    journalpostferdigstilt = true,
)

private fun ApplicationTestBuilder.setupApplication(database: Database) {
    application {
        dependencies {
            provide { database }
            provide<HttpClient> { this@setupApplication.createClient {} }
            provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
            provide { DokumentService(StubPdfKonverterer()) }
            provide(DokArkivClient::class)
            provide(FagsakIdService::class)
            provide(JournalfoerSoknadKopiDeltaker::class)
        }
    }
}
