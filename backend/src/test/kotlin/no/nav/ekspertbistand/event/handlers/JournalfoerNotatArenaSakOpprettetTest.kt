package no.nav.ekspertbistand.event.handlers

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.dokarkiv.*
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import org.jetbrains.exposed.v1.jdbc.Database
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class JournalfoerNotatArenaSakOpprettetTest {
    @Test
    fun `handler journalforer notat om saksnummer`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokgen("%PDF-mock".toByteArray())
        val requestAssertions = mutableListOf<() -> Unit>()
        mockDokArkiv { request ->
            requestAssertions.add {
                assertEquals("Saken er opprettet i Arena under saksnummer: 1337", request.tittel)
                assertEquals("1234", request.bruker.id)
                assertEquals("ORGNR", request.bruker.idType)
                assertNull(request.avsenderMottaker)
                assertIs<Sak.FagSak>(request.sak)
                assertEquals("1", request.sak.fagsakId)
            }

            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("9876")),
                journalpostId = "1234",
                journalpostferdigstilt = true,
            )
        }
        setupApplication(database)
        startApplication()

        val handler = application.dependencies.resolve<JournalfoerNotatArenaSakOpprettet>()
        val event = Event(
            id = 1L,
            data = EventData.TiltaksgjennomforingOpprettet(
                soknad = sampleSoknad,
                saksnummer = "1337",
                tiltaksgjennomfoeringId = 42,
            )
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)
        requestAssertions.forEach { assertion -> assertion() } // run assertions on the request payload
    }
}

private val sampleSoknad = DTO.Soknad(
    id = UUID.randomUUID().toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = "1234",
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
    ),
    status = SoknadStatus.godkjent,
)

private fun ApplicationTestBuilder.setupApplication(database: Database) {
    application {
        dependencies {
            provide { database }
            provide<HttpClient> { this@setupApplication.createClient {} }
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide(DokgenClient::class)
            provide(DokArkivClient::class)
            provide(FagsakIdService::class)
            provide(JournalfoerNotatArenaSakOpprettet::class)
        }
    }
}


private fun ApplicationTestBuilder.mockDokgen(pdf: ByteArray) {
    externalServices {
        hosts("http://localhost:9000") {
            routing {
                post("/template/arenaNotat/create-pdf") {
                    val contentType = ContentType.Application.Pdf
                    call.respondBytes(pdf, contentType)
                }
            }
        }
    }
}
