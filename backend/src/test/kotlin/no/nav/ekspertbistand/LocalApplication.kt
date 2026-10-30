package no.nav.ekspertbistand

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.engine.mock.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import kotlinx.datetime.TimeZone.Companion.currentSystemDefault
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.ArenaClient
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.clamav.ClamAvClient
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.ereg.EregClient
import no.nav.ekspertbistand.ereg.EregService
import no.nav.ekspertbistand.ereg.configureEregApiV1
import no.nav.ekspertbistand.event.configureEventHandlers
import no.nav.ekspertbistand.event.projections.configureProjectionBuilders
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.internal.configureInternal
import no.nav.ekspertbistand.mocks.LokalPdfKonverterer
import no.nav.ekspertbistand.mocks.startLokalOebs
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.norg.BehandlendeEnhetUtleder
import no.nav.ekspertbistand.norg.KommunenavnCache
import no.nav.ekspertbistand.norg.NorgKlient
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.pdl.GraderingOppslag
import no.nav.ekspertbistand.pdl.PdlApiKlient
import no.nav.ekspertbistand.pdl.PdlGraderingOppslag
import no.nav.ekspertbistand.refusjon.configureRefusjonApiV1
import no.nav.ekspertbistand.saksbehandling.*
import no.nav.ekspertbistand.skjerming.SkjermingOppslag
import no.nav.ekspertbistand.aareg.AaregClient
import no.nav.ekspertbistand.soknad.AaregPdlDeltakerOppslag
import no.nav.ekspertbistand.soknad.DeltakerOppslag
import no.nav.ekspertbistand.soknad.configureSoknadApiV1
import no.nav.ekspertbistand.sokos.KontoregisterClient
import no.nav.ekspertbistand.sokos.configureKontoregisterApiV1
import no.nav.ekspertbistand.statistikk.AdressebeskyttedeBehandlinger
import no.nav.ekspertbistand.statistikk.AdressebeskyttelseOppslag
import no.nav.ekspertbistand.statistikk.PdlAdressebeskyttelseOppslag
import no.nav.ekspertbistand.statistikk.SaksbehandlingsstatistikkSkriver
import no.nav.ekspertbistand.tilgangsmaskin.*
import no.nav.ekspertbistand.tilsagndata.configureTilsagnDataApiV1
import no.nav.ekspertbistand.vedlegg.configureVedleggApiV1
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import kotlin.time.Clock.System.now
import kotlin.time.ExperimentalTime


/**
 * Saksbehandlere som kan logge inn lokalt. Ola og Kari har begge rollene, slik at topartskontrollen kan testes.
 * Per og Lisa har bare én rolle hver, slik at rollesjekkene kan testes. Bare Kari har tilgang til kode 6, kode 7
 * og egne ansatte, og bare hun har Nav Vikafossen, der kode 6-sakene havner.
 * `faketoken` gir den første. Saksbehandling-BFF-en sender `faketoken-<navIdent>` når brukeren er byttet
 * i klienten, se frontend/saksbehandling-server/src/lokal-bruker.ts.
 */
internal data class LokalAnsatt(
    val navIdent: String,
    val fornavn: String,
    val etternavn: String,
    val roller: Set<Role> = setOf(Role.SAKSBEHANDLER, Role.BESLUTTER),
    val tilganger: Set<LokalBeskyttelse> = emptySet(),
    val enheter: List<LokalEnhet> = listOf(LokalEnhet.SYDPOLEN, LokalEnhet.NORDPOLEN),
) {
    val navn get() = "$fornavn $etternavn"
}

internal val lokaleAnsatte = listOf(
    LokalAnsatt(navIdent = "A123456", fornavn = "Ola", etternavn = "Beggedeler"),
    LokalAnsatt(
        navIdent = "B654321",
        fornavn = "Kari",
        etternavn = "Beggedeler",
        tilganger = LokalBeskyttelse.entries.toSet(),
        enheter = LokalEnhet.entries,
    ),
    LokalAnsatt(navIdent = "P123456", fornavn = "Per", etternavn = "Baresaksbehandler", roller = setOf(Role.SAKSBEHANDLER)),
    LokalAnsatt(navIdent = "L654321", fornavn = "Lisa", etternavn = "Barebeslutter", roller = setOf(Role.BESLUTTER)),
)

private fun lokalAnsattForToken(token: String): LokalAnsatt? = when {
    token == "faketoken" -> lokaleAnsatte.first()
    token.startsWith("faketoken-") -> lokaleAnsatte.find { it.navIdent == token.removePrefix("faketoken-") }
    else -> null
}

fun main() {
    val testDb = TestDatabase().cleanMigrate()
    startLokalApplikasjon(testDb.config)
}

/**
 * Starter backend med mockede eksterne tjenester og testdata mot en ferdig migrert database.
 * Brukes av [main] lokalt og av `MockApplication` i dev-gcp.
 */
@OptIn(ExperimentalTime::class)
fun startLokalApplikasjon(dbConfig: DbConfig) {
    val database = dbConfig.jdbcDatabase
    val altinnTilganger = """[ "nav_tiltak_ekspertbistand" ]"""
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
                  "underenheter": ${
                lokaleVirksomheter.joinToString(prefix = "[", postfix = "]") {
                    """{
                      "erSlettet": false,
                      "orgnr": "${it.orgnr}",
                      "organisasjonsform": "BEDR",
                      "navn": "${it.navn}",
                      "underenheter": [],
                      "altinn3Tilganger": $altinnTilganger,
                      "altinn2Tilganger": []
                    }"""
                }
            },
                  "altinn3Tilganger": $altinnTilganger,
                  "altinn2Tilganger": []
                }
              ],
              "orgNrTilTilganger": {
                "123456789": $altinnTilganger,
                ${lokaleVirksomheter.joinToString { """"${it.orgnr}": $altinnTilganger""" }}
              },
              "tilgangTilOrgNr": {
                "nav_tiltak_ekspertbistand": [ "123456789", ${lokaleVirksomheter.joinToString { "\"${it.orgnr}\"" }} ]
              }
            }
            """,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    })
    val mockEregServer = HttpClient(MockEngine { request ->
        val virksomhet = lokalVirksomhet(request.url.encodedPath.substringAfterLast('/'))
        respond(
            // language=JSON
            """{
              "organisasjonsnummer": "${virksomhet.orgnr}",
              "navn": { "sammensattnavn": "${virksomhet.navn}" },
              "organisasjonDetaljer": {
                "forretningsadresser": [
                  {
                    "adresselinje1": "${virksomhet.adresse}",
                    "postnummer": "${virksomhet.postnummer}",
                    "poststed": "${virksomhet.poststed}",
                    "kommunenummer": "${virksomhet.kommunenummer}"
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
    val mockDokumentService = DokumentService(LokalPdfKonverterer(HttpClient(CIO)))
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

    ktorServer {
        dependencies {
            provide<Database> {
                database
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
                    lokalAnsattForToken(it)
                        ?.let { ansatt -> mockAzureAdIntrospectionResponse.withNavIdent(ansatt.navIdent) }
                }
            }
            provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
            provide<EntraProxyClient> {
                EntraProxyClient(
                    tokenProvider = successAzureAdTokenProvider,
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        val path = request.url.encodedPath
                        val ansatt = lokaleAnsatte.find { path.endsWith("/${it.navIdent}") }
                            ?: error("Unhandled ${request.url}")
                        when (path) {
                            "${EntraProxyClient.API_PATH}/${ansatt.navIdent}" -> respond(
                                ansatt.enheter.joinToString(prefix = "[", postfix = "]") {
                                    """{ "enhetnummer": "${it.nummer}", "navn": "${it.navn}" }"""
                                },
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )
                            "${EntraProxyClient.ANSATT_API_PATH}/${ansatt.navIdent}" -> respond(
                                // language=JSON
                                """
                            {
                                "navIdent": "${ansatt.navIdent}",
                                "tIdent": "T${ansatt.navIdent.drop(1)}",
                                "fornavn": "${ansatt.fornavn}",
                                "etternavn": "${ansatt.etternavn}",
                                "enhet": { "enhetnummer": "1234", "navn": "Nav arbeidslivssenter Sydpolen" },
                                "enheter": ${
                                    ansatt.enheter.joinToString(prefix = "[", postfix = "]") {
                                        """{ "enhetsnummer": "${it.nummer}", "navn": "${it.navn}" }"""
                                    }
                                }
                            }
                            """.trimIndent(),
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json")
                            )
                            "${EntraProxyClient.GRUPPER_API_PATH}/${ansatt.navIdent}" -> respond(
                                ansatt.roller.joinToString(prefix = "[", postfix = "]") { """{ "rolle": "${it.groupId}" }""" },
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
                            TokenResponse.Success(accessToken = userToken, expiresInSeconds = 3600)
                    },
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        val ansatt = request.headers[HttpHeaders.Authorization]
                            ?.removePrefix("Bearer ")
                            ?.let(::lokalAnsattForToken)
                        fun avvisning(fnr: String) = lokalBeskyttelse(fnr)
                            ?.takeUnless { ansatt != null && it in ansatt.tilganger }
                            ?.let {
                                TilgangsmaskinAvvisning(
                                    title = it.avvisningskode,
                                    status = 403,
                                    brukerIdent = fnr,
                                    navIdent = ansatt?.navIdent,
                                    begrunnelse = it.begrunnelse,
                                    kanOverstyres = false,
                                )
                            }

                        val body = request.body.toByteArray().decodeToString()
                        if (request.url.encodedPath == "/api/v1/bulk/obo") {
                            val identer = defaultJson.decodeFromString<List<BrukerIdOgRegelsett>>(body)
                            respond(
                                defaultJson.encodeToString(
                                    AggregertBulkRespons(resultater = identer.map {
                                        val avvist = avvisning(it.brukerId)
                                        EnkeltBulkRespons(it.brukerId, if (avvist == null) 204 else 403, avvist)
                                    })
                                ),
                                HttpStatusCode.MultiStatus,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                            )
                        } else {
                            val avvist = avvisning(defaultJson.decodeFromString<String>(body))
                            if (avvist == null) {
                                respond("", HttpStatusCode.NoContent)
                            } else {
                                respond(
                                    defaultJson.encodeToString(avvist),
                                    HttpStatusCode.Forbidden,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.ProblemJson.toString()),
                                )
                            }
                        }
                    }),
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
            provide<ClamAvClient> {
                ClamAvClient(HttpClient(MockEngine {
                    respond(
                        // language=JSON
                        """[{ "Filename": "vedlegg.pdf", "Result": "OK" }]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }))
            }
            provide<KontoregisterClient> {
                KontoregisterClient(
                    tokenProvider = successAzureAdTokenProvider,
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        val orgnr = request.url.encodedPath.substringAfterLast('/')
                        val kontonummer = lokaleVirksomheter.find { it.orgnr == orgnr }?.kontonummer
                        if (kontonummer == null) {
                            respond("", HttpStatusCode.NotFound)
                        } else {
                            respond(
                                // language=JSON
                                """{ "mottaker": "$orgnr", "kontonr": "$kontonummer" }""",
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            )
                        }
                    }),
                )
            }
            provide<NorgKlient> {
                NorgKlient(HttpClient(MockEngine { request ->
                    respond(
                        if (request.url.encodedPath.endsWith("/kodeverk/geografi"))
                            lokaleVirksomheter.distinctBy { it.kommunenummer }
                                .joinToString(prefix = "[", postfix = """, {"navn": "03", "term": "Oslo fylke"}]""") {
                                    """{"navn": "${it.kommunenummer}", "term": "${it.kommunenavn}"}"""
                                }
                        else {
                            val norgRequest = defaultJson.parseToJsonElement(request.body.toByteArray().decodeToString())
                                .jsonObject
                            val enhet =
                                if (norgRequest["diskresjonskode"]?.jsonPrimitive?.content == NorgKlient.DISKRESJONSKODE_ADRESSEBESKYTTET)
                                    LokalEnhet.VIKAFOSSEN
                                else
                                    lokalEnhetForKommune(norgRequest["geografiskOmraade"]?.jsonPrimitive?.content)
                            // language=JSON
                            """
                            [
                                {
                                    "enhetId": "${enhet.nummer}",
                                    "enhetNr": "${enhet.nummer}",
                                    "navn": "${enhet.navn}",
                                    "status": "Aktiv"
                                }
                            ]
                            """.trimIndent()
                        },
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }))
            }
            provide(BehandlendeEnhetService::class)
            provide(BehandlendeEnhetUtleder::class)
            provide<KommunenavnCache> { KommunenavnCache(resolve()) }
            provide<PdlApiKlient> {

                PdlApiKlient(
                    azureAdTokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        (request.body as TextContent).text.let { body ->
                            val ident = defaultJson.parseToJsonElement(body)
                                .jsonObject["variables"]?.jsonObject?.get("ident")?.jsonPrimitive?.content
                            val operationName = defaultJson.parseToJsonElement(body)
                                .jsonObject["operationName"]?.jsonPrimitive?.content
                            if (body.contains("hentGeografiskTilknytning")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "hentGeografiskTilknytning": {
                                                "gtType": "KOMMUNE",
                                                "gtKommune": "0301"
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else if (operationName == "HentPersonNavn") {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "hentPerson": {
                                                "navn": [
                                                    { "fornavn": "Mona", "mellomnavn": null, "etternavn": "Moonlight" }
                                                ]
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else if (body.contains("hentPerson")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "hentPerson": {
                                                "adressebeskyttelse": [
                                                    { "gradering": "${lokalBeskyttelse(ident)?.gradering ?: "UGRADERT"}" }
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
                            if (body.contains("nyStatusSakByGrupperingsid")) {
                                respond(
                                    // language=JSON
                                    """
                                    {
                                        "data": {
                                            "nyStatusSakByGrupperingsid": {
                                                "__typename": "NyStatusSakVellykket",
                                                "id": "1234",
                                                "statuser": []
                                            }
                                        }
                                    }
                                    """.trimIndent(),
                                    HttpStatusCode.OK,
                                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                )
                            } else if (body.contains("nyBeskjed")) {
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
            provide<AaregClient> {
                AaregClient(
                    tokenProvider = resolve<AzureAdTokenProvider>(),
                    defaultHttpClient = HttpClient(MockEngine { request ->
                        val fnr = request.headers["Nav-Personident"].orEmpty()
                        val orgnr = request.headers["Nav-Arbeidsstedident"].orEmpty()
                        // Fødselsnummer som slutter på 00000 har ingen arbeidsforhold.
                        val body = if (fnr.endsWith("00000")) "[]" else
                            // language=JSON
                            """
                            [
                              {
                                "arbeidssted": { "type": "Underenhet", "identer": [{ "type": "ORGANISASJONSNUMMER", "ident": "$orgnr" }] },
                                "ansettelsesperiode": { "startdato": "2020-01-01" }
                              }
                            ]
                            """.trimIndent()
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    })
                )
            }
            provide<DeltakerOppslag> { AaregPdlDeltakerOppslag(resolve(), resolve()) }
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
            provide<SaksbehandlingsstatistikkSkriver> { SaksbehandlingsstatistikkSkriver { } }
            provide<AdressebeskyttedeBehandlinger> {
                object : AdressebeskyttedeBehandlinger {
                    override fun registrer(behandlingIder: Collection<String>) = Unit
                    override fun utenAdressebeskyttelse() = emptyMap<String, String>()
                }
            }
            provide<AdressebeskyttelseOppslag> { PdlAdressebeskyttelseOppslag(resolve()) }
            provide<GraderingOppslag> { PdlGraderingOppslag(resolve()) }
            provide<SkjermingOppslag> { SkjermingOppslag { lokalBeskyttelse(it) == LokalBeskyttelse.SKJERMET } }
        }
        configureServer()
        configureAuthentication()

        // application modules
        configureSoknadApiV1()
        configureVedleggApiV1()
        configureRefusjonApiV1()
        configureOrganisasjonerApiV1()
        configureTilsagnDataApiV1()
        configureEregApiV1()
        configureSaksbehandlerApiV1()
        configureSaksbehandlingSakApiV1()
        configureVilkarsvurderingApiV1()
        configureForelopigVedtakApiV1()
        configureBeslutterApiV1()
        configureKommunefilterApiV1()
        configureSakDokumentApiV1()
        configureKontoregisterApiV1()

        // event manager and event handlers
        configureEventHandlers()
        startLokalOebs(database)

        configureProjectionBuilders()

        configureAppMetrics()

        // internal endpoints and lifecycle hooks
        configureInternal()
        registerShutdownListener()

        lagreLokaleSoknader(database)
        behandleLokaleSaker(database)
    }
}

