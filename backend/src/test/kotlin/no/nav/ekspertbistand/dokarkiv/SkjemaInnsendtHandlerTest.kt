package no.nav.ekspertbistand.dokarkiv

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.*
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.IdempotencyGuard
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.IdentityProvider
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.TokenProvider
import no.nav.ekspertbistand.infrastruktur.TokenResponse
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkjemaInnsendtHandlerTest {
    @Test
    fun `handler journalforer og produserer JournalpostOpprettet-event`() = testApplication {
        val database = TestDatabase().cleanMigrate().config.jdbcDatabase
        mockDokgen("%PDF-mock".toByteArray())
        mockDokArkiv {
            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("9876")),
                journalpostId = "1234",
                journalpostferdigstilt = true,
            )
        }
        setupApplication(database)
        startApplication()

        val handler = application.dependencies.resolve<SkjemaInnsendtHandler>()
        val event = Event(
            id = 1L,
            data = EventData.SkjemaInnsendt(sampleSkjema)
        )

        val result = handler.handle(event)
        assertTrue(result is EventHandledResult.Success)

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
            val queuedEvent = queued.first()[QueuedEvents.eventData]
            assertTrue(queuedEvent is EventData.JournalpostOpprettet)
            assertEquals(9876, queuedEvent.dokumentId)
            assertEquals(1234, queuedEvent.journaldpostId)
            assertEquals(BehandlendeEnhetService.NAV_ARBEIDSLIVSSENTER_OSLO, queuedEvent.behandlendeEnhetId)
        }
    }

    @Test
    fun `idempotency guard hindrer duplikat ved retry`() = testApplication {
        val database = TestDatabase().cleanMigrate().config.jdbcDatabase
        mockDokgen("%PDF-mock".toByteArray())
        mockDokArkiv {
            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("9876")),
                journalpostId = "1234",
                journalpostferdigstilt = true,
            )
        }
        setupApplication(database)
        startApplication()

        val handler = application.dependencies.resolve<SkjemaInnsendtHandler>()
        val event = Event(
            id = 2L,
            data = EventData.SkjemaInnsendt(sampleSkjema.copy(id = UUID.randomUUID().toString()))
        )

        repeat(2) {
            handler.handle(event)
        }

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
        }
    }
}

private val sampleSkjema = DTO.Skjema(
    id = UUID.randomUUID().toString(),
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

private fun ApplicationTestBuilder.setupApplication(database: Database) {
    val client = createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }
    application {
        dependencies {
            provide { database }
            provide<TokenProvider>(IdentityProvider.AZURE_AD.alias) {
                object : TokenProvider {
                    override suspend fun token(target: String): TokenResponse {
                        return TokenResponse.Success(
                            accessToken = "token",
                            expiresInSeconds = 3600
                        )
                    }
                }
            }
            provide<TokenProvider> {
                object : TokenProvider {
                    override suspend fun token(target: String): TokenResponse {
                        return TokenResponse.Success(
                            accessToken = "token",
                            expiresInSeconds = 3600
                        )
                    }
                }
            }
            provide { DokgenClient(client, baseUrl = "http://localhost:9000") }
            provide { DokArkivClient(resolve(), client) }
            provide<IdempotencyGuard> { IdempotencyGuard(resolve()) }
            provide {
                SkjemaInnsendtHandler(
                    resolve(),
                    resolve(),
                    resolve(),
                    resolve(),
                )
            }
        }
    }
}

private fun ApplicationTestBuilder.mockDokgen(pdf: ByteArray) {
    externalServices {
        hosts("http://localhost:9000") {
            routing {
                post("/template/soknad/create-pdf") {
                    val contentType = ContentType.Application.Pdf
                    call.respondBytes(pdf, contentType)
                }
            }
        }
    }
}
