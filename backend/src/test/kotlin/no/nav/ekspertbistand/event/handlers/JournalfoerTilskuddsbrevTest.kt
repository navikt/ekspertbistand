package no.nav.ekspertbistand.event.handlers

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostDokument
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.skjema.DTO
import no.nav.ekspertbistand.skjema.SkjemaStatus
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JournalfoerTilskuddsbrevTest {
    @Test
    fun `handler journalforer og produserer TilskuddsbrevJournalfoert-event`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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

        val handler = application.dependencies.resolve<JournalfoerTilskuddsbrev>()
        val event = Event(
            id = 1L,
            data = EventData.TilskuddsbrevMottatt(
                skjema = sampleSkjema,
                tilsagnbrevId = 1,
                tilsagnData = sampleTilsagnData
            )
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
            val queuedEvent = queued.first()[QueuedEvents.eventData]
            assertIs<EventData.TilskuddsbrevJournalfoert>(queuedEvent)
            assertEquals(9876, queuedEvent.dokumentId)
            assertEquals(1234, queuedEvent.journaldpostId)
            assertEquals(sampleSkjema.id, queuedEvent.skjema.id)
        }
    }


    @Test
    fun `idempotency guard hindrer duplikat ved retry`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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

        val handler = application.dependencies.resolve<JournalfoerTilskuddsbrev>()
        val event = Event(
            id = 2L,
            data = EventData.TilskuddsbrevMottatt(
                skjema = sampleSkjema,
                tilsagnbrevId = 1,
                tilsagnData = sampleTilsagnData
            )
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

private val sampleTilsagnData = TilsagnData(
    tilsagnNummer = TilsagnData.TilsagnNummer(
        1337,
        42,
        43,
    ),
    tilsagnDato = "01.01.2021",
    periode = TilsagnData.Periode(
        fraDato = "01.01.2021",
        tilDato = "01.02.2021"
    ),
    tiltakKode = "42",
    tiltakNavn = "Ekspertbistand",
    administrasjonKode = "etellerannet",
    refusjonfristDato = "10.01.2021",
    tiltakArrangor = TilsagnData.TiltakArrangor(
        arbgiverNavn = "Arrangøren",
        landKode = "1337",
        postAdresse = "et sted",
        postNummer = "1337",
        postSted = "hos naboen",
        orgNummerMorselskap = 43,
        orgNummer = 42,
        kontoNummer = "1234.12.12345",
        maalform = "norsk"
    ),
    totaltTilskuddbelop = 24000,
    valutaKode = "NOK",
    tilskuddListe = listOf(
        TilsagnData.Tilskudd(
            tilskuddType = "ekspertbistand",
            tilskuddBelop = 24000,
            visTilskuddProsent = false,
            tilskuddProsent = null
        )
    ),
    deltaker = TilsagnData.Deltaker(
        fodselsnr = "42",
        fornavn = "navn",
        etternavn = "navnesen",
        landKode = "NO",
        postAdresse = "et sted",
        postNummer = "1234",
        postSted = "hos den andre naboen",
    ),
    antallDeltakere = 1,
    antallTimeverk = 100,
    navEnhet = TilsagnData.NavEnhet(
        navKontorNavn = "kontor1",
        navKontor = "Kontor1",
        postAdresse = "hos den tredje",
        postNummer = "1234",
        postSted = "hos den tredje",
        telefon = "12341234",
        faks = null
    ),
    beslutter = TilsagnData.Person(
        fornavn = "Ole",
        etternavn = "Brum",
    ),
    saksbehandler = TilsagnData.Person(
        fornavn = "Nasse",
        etternavn = "Nøff",
    ),
    kommentar = "Dette var unødvendig mye testdata å skrive"
)

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
    ),
    status = SkjemaStatus.godkjent,
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
            provide(JournalfoerTilskuddsbrev::class)
        }
    }
}


private fun ApplicationTestBuilder.mockDokgen(pdf: ByteArray) {
    externalServices {
        hosts("http://localhost:9000") {
            routing {
                post("/template/tilskuddsbrev/create-pdf") {
                    val contentType = ContentType.Application.Pdf
                    call.respondBytes(pdf, contentType)
                }
            }
        }
    }
}
