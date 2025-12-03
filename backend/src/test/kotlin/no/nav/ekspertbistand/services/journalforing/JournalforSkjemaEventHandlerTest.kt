package no.nav.ekspertbistand.services.journalforing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostDokument
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.services.IdempotencyGuard
import no.nav.ekspertbistand.skjema.DTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class JournalforSkjemaEventHandlerTest {

    @OptIn(ExperimentalTime::class)
    @Test
    fun `publiserer JournalpostOpprettet bare én gang per event-id`() = runTest {
        TestDatabase().cleanMigrate().use { db ->
            val handler = JournalforSkjemaEventHandler(
                dokgenClient = dokgenClient(),
                dokArkivClient = dokArkivClient(),
                idempotencyGuard = IdempotencyGuard(db.config.jdbcDatabase),
            )

            val event = Event(
                id = 1L,
                data = EventData.SkjemaInnsendt(sampleSkjema(id = "skjema-123")),
            )

            val first = handler.handle(event)
            assertTrue(first is EventHandledResult.Success)

            val queued = EventQueue.poll()
            val journalpostEvent = queued?.eventData as? EventData.JournalpostOpprettet
            assertEquals("skjema-123", journalpostEvent?.skjemaId)
            assertEquals("jp-123", journalpostEvent?.journalpostId)
            EventQueue.finalize(requireNotNull(queued).id)

            val second = handler.handle(event)
            assertTrue(second is EventHandledResult.Success)
            assertNull(EventQueue.poll())
        }
    }

    private fun dokgenClient(): DokgenClient {
        val pdf = "%PDF-1.4\nstub".toByteArray()
        val engine = MockEngine { _ ->
            respond(
                content = pdf,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Pdf.toString())
            )
        }
        return DokgenClient(
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json() }
            },
            baseUrl = "http://dokgen"
        )
    }

    private fun dokArkivClient(): DokArkivClient {
        val response = OpprettJournalpostResponse(
            dokumenter = listOf(OpprettJournalpostDokument(dokumentInfoId = "dok-123")),
            journalpostId = "jp-123",
            journalpostferdigstilt = true,
        )
        val engine = MockEngine { _ ->
            respond(
                content = Json.encodeToString(OpprettJournalpostResponse.serializer(), response),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        return DokArkivClient(
            tokenProvider = object : TokenProvider {
                override suspend fun token(target: String) = TokenResponse.Success("token", 3600)
            },
            httpClient = HttpClient(engine) {
                install(ContentNegotiation) { json() }
            }
        )
    }

    private fun sampleSkjema(id: String) = DTO.Skjema(
        id = id,
        virksomhet = DTO.Virksomhet(
            virksomhetsnummer = "987654321",
            virksomhetsnavn = "Testbedrift AS",
            kontaktperson = DTO.Kontaktperson(
                navn = "Kontakt Person",
                epost = "kontakt@testbedrift.no",
                telefonnummer = "12345678",
            )
        ),
        ansatt = DTO.Ansatt(
            fnr = "01010112345",
            navn = "Ansatt Navn",
        ),
        ekspert = DTO.Ekspert(
            navn = "Ekspert Navn",
            virksomhet = "Ekspertselskap",
            kompetanse = "Ekspertise",
        ),
        behovForBistand = DTO.BehovForBistand(
            begrunnelse = "Behov begrunnelse",
            behov = "Behov",
            estimertKostnad = "9000",
            timer = "12",
            tilrettelegging = "Tilrettelegging tekst",
            startdato = LocalDate(2024, 12, 1),
        ),
        nav = DTO.Nav(
            kontaktperson = "Veileder Navn"
        )
    )
}
