package no.nav.ekspertbistand

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.event.projections.configureProjectionBuilders
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.saksbehandling.Role
import no.nav.ekspertbistand.saksbehandling.configureSaksbehandlerApiV1
import no.nav.ekspertbistand.saksbehandling.configureSaksbehandlingSakApiV1
import no.nav.ekspertbistand.saksbehandling.configureVilkarsvurderingApiV1
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.UtkastTable
import no.nav.ekspertbistand.soknad.configureSoknadApiV1
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.tilsagndata.configureTilsagnDataApiV1
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime



@OptIn(ExperimentalTime::class)
fun main() {
    val mockAltinnTilgangerServer = HttpClient(MockEngine {
        respond(
            // language=JSON
            """{
              "isError": false,
              "hierarki": [
                {
                  "erSlettet": false,
                  "orgnr": "123456789",
                  "organisasjonsform": "AS",
                  "navn": "Eksempel Bedrift AS",
                  "underenheter": [
                    {
                      "erSlettet": false,
                      "orgnr": "123456780",
                      "organisasjonsform": "BEDR",
                      "navn": "Eksempel Bedrift AS Avd. Oslo",
                      "underenheter": [],
                      "altinn3Tilganger": [
                        "nav_tiltak_ekspertbistand"
                      ],
                      "altinn2Tilganger": []
                    }
                  ],
                  "altinn3Tilganger": [
                    "nav_tiltak_ekspertbistand"
                  ],
                  "altinn2Tilganger": []
                }
              ],
              "orgNrTilTilganger": {
                "123456789": [ "nav_tiltak_ekspertbistand" ],
                "123456780": [ "nav_tiltak_ekspertbistand" ]
              },
              "tilgangTilOrgNr": {
                "nav_tiltak_ekspertbistand": [ "123456789", "123456780" ]
              }
            }
            """,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    })
    val mockEregServer = HttpClient(MockEngine {
        respond(
            // language=JSON
            """{
              "organisasjonsnummer": "123456780",
              "navn": { "sammensattnavn": "Eksempel Bedrift AS Avd. Oslo" },
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "Testveien 1",
                    "postnummer": "0557",
                    "poststed": "Oslo",
                    "kommunenummer": "0301"
                  }
                ]
              }
            }""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json")
        )
    })
    val mockAltinnTilgangerClient = AltinnTilgangerClient(
        defaultHttpClient = mockAltinnTilgangerServer,
        tokenExchanger = successTokenXTokenExchanger
    )
    val mockEregClient = EregClient(defaultHttpClient = mockEregServer)
    val mockDokumentService = DokumentService(StubPdfKonverterer())
    val mockDokArkivClient = DokArkivClient(
        azureAdTokenProvider = successAzureAdTokenProvider,
        defaultHttpClient = HttpClient(MockEngine {
            respond(
                // language=JSON
                """{
                  "dokumenter": [
                    {
                      "dokumentInfoId": "1111"
                    }
                  ],
                  "journalpostId": "2222",
                  "journalpostferdigstilt": true
                }""",
                HttpStatusCode.Created,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        })
    )

    val testDb = TestDatabase().also {
        it.cleanMigrate()
    }

    ktorServer {
        dependencies {
            provide<Database> {
                testDb.config.jdbcDatabase
            }
            provide<TokenXTokenIntrospector> {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<HttpClient> { defaultHttpClient() }
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide<AzureAdTokenIntrospector> {
                MockAzureAdIntrospector {
                    if (it == "faketoken") mockAzureAdIntrospectionResponse.withNavIdent("A123456") else null
                }
            }
            provide<EntraProxyClient> {
                EntraProxyClient(
                    tokenProvider = successAzureAdTokenProvider,
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        when (request.url.encodedPath) {
                            "${EntraProxyClient.API_PATH}/A123456" -> respond(
                                // language=JSON
                                """
                            [
                                { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
                                { "enhetnummer": "5678", "navn": "Nav Avdeling Nordpolen" }
                            ]
                            """.trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )
                            "${EntraProxyClient.ANSATT_API_PATH}/A123456" -> respond(
                                // language=JSON
                                """
                            {
                                "navIdent": "A123456",
                                "tIdent": "T123456",
                                "fornavn": "Ola",
                                "etternavn": "Nordmann",
                                "enhet": { "enhetnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
                                "enheter": [
                                    { "enhetsnummer": "1234", "navn": "Nav Avdeling Sydpolen" },
                                    { "enhetsnummer": "5678", "navn": "Nav Avdeling Nordpolen" }
                                ]
                            }
                            """.trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )
                            "${EntraProxyClient.GRUPPER_API_PATH}/A123456" -> respond(
                                // language=JSON
                                """
                            [
                                { "rolle": "${Role.SAKSBEHANDLER.groupId}" },
                                { "rolle": "${Role.BESLUTTER.groupId}" }
                            ]
                            """.trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )

                            else -> error("Unhandled ${request.url}")
                        }
                    }),
                )
            }
            provide<TilgangsmaskinClient> {
                TilgangsmaskinClient(
                    tokenExchanger = object : AzureAdTokenExchanger {
                        override suspend fun exchange(target: String, userToken: String) =
                            TokenResponse.Success(accessToken = "fake-token", expiresInSeconds = 3600)
                    },
                    defaultHttpClient = HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }),
                )
            }
            provide<ArcSightAuditClient> {
                ArcSightAuditClient(
                    object : AuditLogger {
                        val log = LoggerFactory.getLogger("ArcSightAuditClient")
                        override fun log(message: CefMessage?) {
                            log.info("ArcSightAuditClient: $message")
                        }

                        override fun log(message: String?) {
                            log.info("ArcSightAuditClient: $message")
                        }
                    }
                )
            }
            provide {
                mockAltinnTilgangerClient
            }
            provide {
                mockEregClient
            }
            provide<EregService> { EregService(resolve()) }
            provide { mockDokumentService }
            provide { mockDokArkivClient }
            provide<NorgKlient> {
                NorgKlient(HttpClient(MockEngine {
                    respond(
                        // language=JSON
                        """
                        [
                            {
                                "enhetId": "1234",
                                "enhetNr": "1234",
                                "navn": "Nav Avdeling Sydpolen",
                                "status": "Aktiv"
                            }
                        ]
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }))
            }
            provide(BehandlendeEnhetService::class)
            provide<PdlApiKlient> {

                PdlApiKlient(
                    azureAdTokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        (request.body as TextContent).text.let { body ->
                            if (body.contains("hentPerson")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "hentPerson": {
                                                "adressebeskyttelse": [
                                                    { "gradering": "UGRADERT" }
                                                ]
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else {
                                error("Unhandled ${request.url} with body: $body")
                            }
                        }
                    })
                )
            }
            provide<ProdusentApiKlient> {
                ProdusentApiKlient(
                    azureAdTokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        (request.body as TextContent).text.let { body ->
                            if (body.contains("nyBeskjed")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "nyBeskjed": {
                                                "__typename": "NyBeskjedVellykket",
                                                "id": "1234"
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else if (body.contains("nySak")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "nySak": {
                                              "__typename": "NySakVellykket",
                                              "id": "1234"
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else {
                                error("Unhandled ${request.url} with body: $body")
                            }
                        }
                    })
                )
            }
            provide<ArenaClient> {
                val arenaSeq = generateSequence(1) { it + 1 }.iterator()
                ArenaClient(
                    tokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        val mockId = arenaSeq.next()
                        when (request.url.encodedPath) {
                            ArenaClient.API_PATH -> respond(
                                // language=JSON
                                """
                                {
                                    "saksnummer": "${now().toLocalDateTime(currentSystemDefault()).year}${mockId.toString().padStart(4, '0')}",
                                    "tiltaksgjennomfoeringId": "$mockId"
                                }
                                """.trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            )

                            else -> error("Unhandled ${request.url}")
                        }
                    })
                )
            }
            provide(FagsakIdService::class)
        }
        configureServer()
        configureAuthentication()

        // application modules
        configureSoknadApiV1()
        configureOrganisasjonerApiV1()
        configureTilsagnDataApiV1()
        configureEregApiV1()
        configureSaksbehandlerApiV1()
        configureSaksbehandlingSakApiV1()
        configureVilkarsvurderingApiV1()
        //configureKontoregisterApiV1()

        // event manager and event handlers
        configureEventHandlers()

        configureProjectionBuilders()

        configureAppMetrics()

        // internal endpoints and lifecycle hooks
        configureInternal()
        registerShutdownListener()

        initTestData(testDb)
    }
}

private fun initTestData(testDb: TestDatabase) {
    val godkjentTilsagnData = TilsagnData(
        tilsagnNummer = TilsagnData.TilsagnNummer(
            aar = 2024,
            loepenrSak = 123,
            loepenrTilsagn = 1
        ),
        tilsagnDato = "2024-08-12",
        periode = TilsagnData.Periode(
            fraDato = "2024-09-01",
            tilDato = "2024-12-31"
        ),
        tiltakKode = "EKS",
        tiltakNavn = "Ekspertbistand",
        administrasjonKode = "NAV",
        refusjonfristDato = "2025-01-31",
        tiltakArrangor = TilsagnData.TiltakArrangor(
            arbgiverNavn = "Eksempel Bedrift AS",
            landKode = "NO",
            postAdresse = "Testveien 1",
            postNummer = "0557",
            postSted = "Oslo",
            orgNummerMorselskap = 123456789,
            orgNummer = 123456780,
            kontoNummer = "1234.56.78901",
            maalform = "B",
        ),
        totaltTilskuddbelop = 150000,
        valutaKode = "NOK",
        tilskuddListe = listOf(
            TilsagnData.Tilskudd(
                tilskuddType = "Tilskudd",
                tilskuddBelop = 120000,
                visTilskuddProsent = false,
                tilskuddProsent = null
            ),
            TilsagnData.Tilskudd(
                tilskuddType = "Administrasjon",
                tilskuddBelop = 30000,
                visTilskuddProsent = true,
                tilskuddProsent = 20.0
            )
        ),
        deltaker = TilsagnData.Deltaker(
            fodselsnr = "01020312345",
            fornavn = "Ola",
            etternavn = "Nordmann",
            landKode = "NO",
            postAdresse = "Deltakergata 2",
            postNummer = "0155",
            postSted = "Oslo",
        ),
        antallDeltakere = 1,
        antallTimeverk = 80,
        navEnhet = TilsagnData.NavEnhet(
            navKontor = "0315",
            navKontorNavn = "Nav Oslo",
            postAdresse = "Navgata 1",
            postNummer = "0101",
            postSted = "Oslo",
            telefon = "55553333",
            faks = null,
        ),
        beslutter = TilsagnData.Person(
            fornavn = "Kari",
            etternavn = "Saksen",
        ),
        saksbehandler = TilsagnData.Person(
            fornavn = "Per",
            etternavn = "Handler",
        ),
        kommentar = "Mock-tilsagn for local testing."
    )
    transaction(testDb.config.jdbcDatabase) {
        SoknadTable.insertReturning {
            it[id] = UUID.fromString("f8f48c1f-9a5c-4a75-9d1a-2fb0a3a2eaa1")
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[beliggenhetsadresse] = "Testveien 1, 0557 Oslo"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
            it[status] = "godkjent"
        }.single().tilSoknadDTO().also {
            publishEventQueue(EventData.SoknadInnsendt(it))
            publishEventQueue(EventData.InnsendtSoknadJournalfoert(it, 1, 1, "1234"))
            //EventData.TiltaksgjennomforingOpprettet
            //EventData.SaksbehandlingStartetIArena
            //EventData.TilskuddsbrevMottatt
            //EventData.SoknadAvlystIArena

        }
        SoknadTable.insertReturning {
            it[id] = UUID.fromString("2f3f8f6d-4f7e-4a6b-bb32-7b44c0b3f589")
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[opprettetAv] = "42"
            it[beliggenhetsadresse] = "Testveien 1, 0557 Oslo"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
            it[status] = "avlyst"
        }.single().tilSoknadDTO().also {
            publishEventQueue(EventData.SoknadInnsendt(it))
            publishEventQueue(EventData.InnsendtSoknadJournalfoert(it, 2, 2, "5678"))
            //EventData.TiltaksgjennomforingOpprettet
            //EventData.SaksbehandlingStartetIArena
            //EventData.TilskuddsbrevMottatt
            //EventData.SoknadAvlystIArena
        }
        UtkastTable.insert {
            it[id] = UUID.randomUUID()
            it[virksomhetsnummer] = "123456780"
            it[virksomhetsnavn] = "Eksempel Bedrift AS Avd. Oslo"
            it[opprettetAv] = "42"

            it[kontaktpersonNavn] = "Kontaktperson NN"
            it[kontaktpersonEpost] = "kontaktperson@bedrift.no"
            it[kontaktpersonTelefon] = "415199999"
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = "Asnatt NN"
            it[ekspertNavn] = "Ekspert NN"
            it[ekspertVirksomhet] = "ErgoConsult AS"
            it[ekspertKompetanse] = "Ergoterapeut, autorisasjon HPR 1337"
            it[behovForBistand] = "Arbeidsplassvurdering og ergonomisk veiledning"
            it[behovForBistandBegrunnelse] = "Langvarig skulderplage med 50% sykefravær"
            it[behovForBistandEstimertKostnad] = "9999"
            it[behovForBistandTimer] = "16"
            it[behovForBistandTilrettelegging] = "Høydejustert bord testet, noe bedring"
            it[behovForBistandStartdato] = CurrentDate

            it[navKontaktPerson] = "Navkontaktperson NN"
        }

        insertTilsagndata(UUID.fromString("f8f48c1f-9a5c-4a75-9d1a-2fb0a3a2eaa1"), godkjentTilsagnData)
    }
}

