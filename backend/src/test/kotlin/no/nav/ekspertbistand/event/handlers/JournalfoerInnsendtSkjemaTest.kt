package no.nav.ekspertbistand.event.handlers

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostDokument
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData.sampleSoknad
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.mocks.mockEreg
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.BehandlendeEnhetUtleder
import no.nav.ekspertbistand.norg.Norg2Enhet
import no.nav.ekspertbistand.norg.Norg2Request
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.saksbehandling.SLADDET_NAVN
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class JournalfoerInnsendtSoknadTest {
    @Test
    fun `handler journalforer og produserer JournalpostOpprettet-event`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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
        var pdfHtml: String? = null
        setupApplication(database) { pdfHtml = it }
        startApplication()

        val handler = application.dependencies.resolve<JournalfoerInnsendtSoknad>()
        val event = Event(
            id = 1L,
            data = EventData.SoknadInnsendt(sampleSoknad)
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
            val queuedEvent = queued.first()[QueuedEvents.eventData]
            assertIs<EventData.InnsendtSoknadJournalfoert>(queuedEvent)
            assertEquals(9876, queuedEvent.dokumentId)
            assertEquals(1234, queuedEvent.journaldpostId)
            assertEquals("4242", queuedEvent.behandlendeEnhetId)
            assertEquals("0301", queuedEvent.virksomhetKommunenummer)
            assertEquals("0301", capturedNorgRequest?.geografiskOmraade)
            assertEquals(NorgKlient.DISKRESJONSKODE_ANY, capturedNorgRequest?.diskresjonskode)
        }
        assertContains(requireNotNull(pdfHtml), sampleSoknad.ansatt.navn)
        assertContains(requireNotNull(pdfHtml), sampleSoknad.ansatt.fnr)

    }

    @Test
    fun `adressebeskyttet arbeidstaker bruker pdl geotilknytning`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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
        var pdfHtml: String? = null
        setupApplication(database) { pdfHtml = it }
        startApplication()

        val handler = application.dependencies.resolve<JournalfoerInnsendtSoknad>()
        val event = Event(
            id = 3L,
            data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString()))
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
            val queuedEvent = queued.first()[QueuedEvents.eventData]
            assertIs<EventData.InnsendtSoknadJournalfoert>(queuedEvent)
            assertEquals("9999", queuedEvent.behandlendeEnhetId)
            assertNull(queuedEvent.virksomhetKommunenummer)
            assertEquals("1101", capturedNorgRequest?.geografiskOmraade)
            assertEquals(NorgKlient.DISKRESJONSKODE_ADRESSEBESKYTTET, capturedNorgRequest?.diskresjonskode)
        }
        assertSladdet(requireNotNull(pdfHtml))

    }

    @Test
    fun `fortrolig arbeidstaker rutes paa virksomhet og sladdes i soknaden`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        mockDokArkiv {
            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("9876")),
                journalpostId = "1234",
                journalpostferdigstilt = true,
            )
        }
        mockPdl(adressebeskyttelse = AdressebeskyttelseGradering.FORTROLIG)
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
        var pdfHtml: String? = null
        setupApplication(database) { pdfHtml = it }
        startApplication()

        val handler = application.dependencies.resolve<JournalfoerInnsendtSoknad>()
        val event = Event(
            id = 5L,
            data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString()))
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)

        transaction(database) {
            val queuedEvent = QueuedEvents.selectAll().single()[QueuedEvents.eventData]
            assertIs<EventData.InnsendtSoknadJournalfoert>(queuedEvent)
            assertEquals("0301", queuedEvent.virksomhetKommunenummer)
            assertEquals(sampleSoknad.ansatt, queuedEvent.soknad.ansatt, "eventen skal ha usladdet søknad")
        }
        assertSladdet(requireNotNull(pdfHtml))
    }

    @Test
    fun `idempotency guard hindrer duplikat ved retry`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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

        val handler = application.dependencies.resolve<JournalfoerInnsendtSoknad>()
        val event = Event(
            id = 2L,
            data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString()))
        )

        repeat(2) {
            handler.handle(event)
        }

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
        }
    }

    @Test
    fun `fallback behandlende enhet blir lagret i event`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
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

        val handler = application.dependencies.resolve<JournalfoerInnsendtSoknad>()
        val event = Event(
            id = 4L,
            data = EventData.SoknadInnsendt(sampleSoknad.copy(id = UUID.randomUUID().toString()))
        )

        val result = handler.handle(event)
        assertIs<EventHandledResult.Success>(result)

        transaction(database) {
            val queued = QueuedEvents.selectAll().toList()
            assertEquals(1, queued.size)
            val queuedEvent = queued.first()[QueuedEvents.eventData]
            assertIs<EventData.InnsendtSoknadJournalfoert>(queuedEvent)
            assertEquals(BehandlendeEnhetService.NAV_ARBEIDSLIVSSENTER_OSLO, queuedEvent.behandlendeEnhetId)

        }
    }
}

private fun assertSladdet(html: String) {
    assertFalse(html.contains(sampleSoknad.ansatt.navn), "deltakers navn skal sladdes")
    assertFalse(html.contains(sampleSoknad.ansatt.fnr), "deltakers fnr skal sladdes")
    assertContains(html, SLADDET_NAVN)
}

private fun ApplicationTestBuilder.setupApplication(
    database: Database,
    onPdfHtml: (String) -> Unit = {},
) {
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
            provide(BehandlendeEnhetUtleder::class)
            provide { DokumentService(StubPdfKonverterer(onConvert = onPdfHtml)) }
            provide(DokArkivClient::class)
            provide(JournalfoerInnsendtSoknad::class)
            provide(FagsakIdService::class)
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
