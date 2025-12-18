package no.nav.ekspertbistand.dokarkiv

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.mocks.mockEreg
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.Norg2Enhet
import no.nav.ekspertbistand.norg.Norg2Request
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.skjema.DTO
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkjemaInnsendtHandlerTest {
    @Test
    fun `handler journalforer og produserer JournalpostOpprettet-event`() = testApplication {
        TestDatabase().cleanMigrate().use {
            val database = it.config.jdbcDatabase
            mockDokgen("%PDF-mock".toByteArray())
            mockDokArkiv {
                OpprettJournalpostResponse(
                    dokumenter = listOf(OpprettJournalpostDokument("9876")),
                    journalpostId = "1234",
                    journalpostferdigstilt = true,
                )
            }
            mockPdl(adressebeskyttelse = AdressebeskyttelseGradering.UGRADERT)
            mockEreg { _ ->
                """
            {
              "organisasjonsnummer": "987654321",
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  { "kommunenummer": "0301" }
                ]
              }
            }
            """.trimIndent()
            }
            var capturedNorgRequest: Norg2Request? = null
            mockNorg(behandlendeEnhet = "4242") {
                capturedNorgRequest = it
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
                assertEquals("4242", queuedEvent.behandlendeEnhetId)
                assertEquals("0301", capturedNorgRequest?.geografiskOmraade)
                assertEquals(NorgKlient.DISKRESJONSKODE_ANY, capturedNorgRequest?.diskresjonskode)
            }
        }
    }

    @Test
    fun `adressebeskyttet arbeidstaker bruker pdl geotilknytning`() = testApplication {
        TestDatabase().cleanMigrate().use {
            val database = it.config.jdbcDatabase
            mockDokgen("%PDF-mock".toByteArray())
            mockDokArkiv {
                OpprettJournalpostResponse(
                    dokumenter = listOf(OpprettJournalpostDokument("9876")),
                    journalpostId = "1234",
                    journalpostferdigstilt = true,
                )
            }
            mockPdl(
                adressebeskyttelse = AdressebeskyttelseGradering.STRENGT_FORTROLIG,
                geografiskTilknytning = "1101",
                gtType = GtType.KOMMUNE,
            )
            var capturedNorgRequest: Norg2Request? = null
            mockNorg(behandlendeEnhet = "9999") {
                capturedNorgRequest = it
            }
            setupApplication(database)
            startApplication()

            val handler = application.dependencies.resolve<SkjemaInnsendtHandler>()
            val event = Event(
                id = 3L,
                data = EventData.SkjemaInnsendt(sampleSkjema.copy(id = UUID.randomUUID().toString()))
            )

            val result = handler.handle(event)
            assertTrue(result is EventHandledResult.Success)

            transaction(database) {
                val queued = QueuedEvents.selectAll().toList()
                assertEquals(1, queued.size)
                val queuedEvent = queued.first()[QueuedEvents.eventData]
                assertTrue(queuedEvent is EventData.JournalpostOpprettet)
                assertEquals("9999", queuedEvent.behandlendeEnhetId)
                assertEquals("1101", capturedNorgRequest?.geografiskOmraade)
                assertEquals(NorgKlient.DISKRESJONSKODE_ADRESSEBESKYTTET, capturedNorgRequest?.diskresjonskode)
            }
        }
    }

    @Test
    fun `idempotency guard hindrer duplikat ved retry`() = testApplication {
        TestDatabase().cleanMigrate().use {
            val database = it.config.jdbcDatabase
            mockDokgen("%PDF-mock".toByteArray())
            mockDokArkiv {
                OpprettJournalpostResponse(
                    dokumenter = listOf(OpprettJournalpostDokument("9876")),
                    journalpostId = "1234",
                    journalpostferdigstilt = true,
                )
            }
            mockPdl(adressebeskyttelse = AdressebeskyttelseGradering.UGRADERT)
            mockEreg { _ ->
                """
            {
              "organisasjonsnummer": "987654321",
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  { "kommunenummer": "0301" }
                ]
              }
            }
            """.trimIndent()
            }
            mockNorg(behandlendeEnhet = "4242")
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

    @Test
    fun `fallback behandlende enhet blir lagret i event`() = testApplication {
        TestDatabase().cleanMigrate().use {
            val database = it.config.jdbcDatabase
            mockDokgen("%PDF-mock".toByteArray())
            mockDokArkiv {
                OpprettJournalpostResponse(
                    dokumenter = listOf(OpprettJournalpostDokument("9876")),
                    journalpostId = "1234",
                    journalpostferdigstilt = true,
                )
            }
            mockPdl(adressebeskyttelse = AdressebeskyttelseGradering.UGRADERT)
            mockEreg { _ ->
                """
            {
              "organisasjonsnummer": "987654321",
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  { "kommunenummer": "0301" }
                ]
              }
            }
            """.trimIndent()
            }
            mockNorg(behandlendeEnhet = null)
            setupApplication(database)
            startApplication()

            val handler = application.dependencies.resolve<SkjemaInnsendtHandler>()
            val event = Event(
                id = 4L,
                data = EventData.SkjemaInnsendt(sampleSkjema.copy(id = UUID.randomUUID().toString()))
            )

            val result = handler.handle(event)
            assertTrue(result is EventHandledResult.Success)

            transaction(database) {
                val queued = QueuedEvents.selectAll().toList()
                assertEquals(1, queued.size)
                val queuedEvent = queued.first()[QueuedEvents.eventData]
                assertTrue(queuedEvent is EventData.JournalpostOpprettet)
                assertEquals(BehandlendeEnhetService.NAV_ARBEIDSLIVSSENTER_OSLO, queuedEvent.behandlendeEnhetId)
            }
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
    application {
        dependencies {
            provide { database }
            provide<HttpClient> { this@setupApplication.createClient {} }
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide(EregClient::class)
            provide(NorgKlient::class)
            provide(BehandlendeEnhetService::class)
            provide(PdlApiKlient::class)
            provide(DokgenClient::class)
            provide(DokArkivClient::class)
            provide<IdempotencyGuard> { IdempotencyGuard(resolve()) }
            provide {
                SkjemaInnsendtHandler(
                    resolve(),
                    resolve(),
                    resolve(),
                    resolve(),
                    resolve(),
                    resolve<Database>(),
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

private fun ApplicationTestBuilder.mockPdl(
    adressebeskyttelse: AdressebeskyttelseGradering,
    geografiskTilknytning: String = "0301",
    gtType: GtType = GtType.KOMMUNE,
) {
    externalServices {
        hosts(PdlApiKlient.baseUrl) {
            routing {
                post("/graphql") {
                    val body = call.receiveText()
                    when {
                        body.contains("hentPerson") -> call.respondText(
                            """
                            {
                              "data": {
                                "hentPerson": {
                                  "adressebeskyttelse": [
                                    { "gradering": "$adressebeskyttelse" }
                                  ]
                                }
                              }
                            }
                            """.trimIndent(),
                            ContentType.Application.Json
                        )

                        body.contains("hentGeografiskTilknytning") -> call.respondText(
                            """
                            {
                              "data": {
                                "hentGeografiskTilknytning": {
                                  "gtType": "${gtType.name}",
                                  "gtLand": ${if (gtType == GtType.UTLAND) "\"$geografiskTilknytning\"" else "null"},
                                  "gtKommune": ${if (gtType == GtType.KOMMUNE || gtType == GtType.BYDEL) "\"$geografiskTilknytning\"" else "null"},
                                  "gtBydel": ${if (gtType == GtType.BYDEL) "\"$geografiskTilknytning\"" else "null"}
                                }
                              }
                            }
                            """.trimIndent(),
                            ContentType.Application.Json
                        )

                        else -> error("Ukjent query mot PDL i test")
                    }
                }
            }
        }
    }
}

private fun ApplicationTestBuilder.mockNorg(
    behandlendeEnhet: String?,
    captureRequest: (Norg2Request) -> Unit = {},
) {
    externalServices {
        hosts(NorgKlient.baseUrl) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                post("norg2/api/v1/arbeidsfordeling/enheter/bestmatch") {
                    val body = call.receive<Norg2Request>()
                    captureRequest(body)
                    call.respond(
                        behandlendeEnhet?.let {
                            listOf(
                                Norg2Enhet(
                                    enhetNr = it,
                                    status = "Aktiv",
                                )
                            )
                        } ?: emptyList()
                    )
                }
            }
        }
    }
}
