package no.nav.ekspertbistand.sokos

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.altinn.AltinnTilgangerClientResponse
import no.nav.ekspertbistand.altinn3Ressursid
import no.nav.ekspertbistand.configureServer
import no.nav.ekspertbistand.entraproxy.EntraBerikelseCache
import no.nav.ekspertbistand.entraproxy.EntraProxyClient
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.mocks.mockAltinnTilganger
import no.nav.ekspertbistand.mocks.mockEntraProxyFull
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class KontoregisterApiTest {
    private val orgnr = "910825226"

    private fun tilgangTil(vararg orgnre: String) = AltinnTilgangerClientResponse(
        isError = false,
        hierarki = emptyList(),
        orgNrTilTilganger = orgnre.associateWith { setOf(altinn3Ressursid) },
        tilgangTilOrgNr = mapOf(altinn3Ressursid to orgnre.toSet()),
    )

    private fun kontoregisterClient(response: (String) -> Pair<HttpStatusCode, String>): KontoregisterClient {
        val mockEngine = MockEngine { request ->
            val org = request.url.rawSegments.last()
            val (status, body) = response(org)
            if (status == HttpStatusCode.NotFound) {
                respondError(status = status, content = body)
            } else {
                respond(
                    content = body,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }

        return KontoregisterClient(
            defaultHttpClient = client,
            tokenProvider = successAzureAdTokenProvider,
        )
    }

    @Test
    fun `saksbehandler med riktig rolle faar kontonummer`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        mockEntraProxyFull(grupperProvider = { """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]""" })

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/$orgnr/kontonummer") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("12345678901", response.body<KontonummerResponse>().kontonummer)
    }

    @Test
    fun `beslutter med riktig rolle faar kontonummer`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        mockEntraProxyFull(grupperProvider = { """[{ "rolle": "0000-CA-Ekspertbistand_Beslutter" }]""" })

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/$orgnr/kontonummer") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("12345678901", response.body<KontonummerResponse>().kontonummer)
    }

    @Test
    fun `saksbehandler uten saksbehandler-rolle gir 403`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        mockEntraProxyFull(grupperProvider = { "[]" })

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/$orgnr/kontonummer") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `uautentisert request mot saksbehandling-endepunkt gir 401`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenIntrospector> { MockAzureAdIntrospector { null } }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/$orgnr/kontonummer")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `manglende kontonummer gir 404`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.NotFound to "ikke funnet"
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        mockEntraProxyFull(grupperProvider = { """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]""" })

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/$orgnr/kontonummer") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `ugyldig orgnr mot saksbehandling-endepunkt gir 400`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        mockEntraProxyFull(grupperProvider = { """[{ "rolle": "0000-CA-Ekspertbistand_Saksbehandler" }]""" })

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<AzureAdTokenProvider> { successAzureAdTokenProvider }
                provide<HttpClient> { client }
                provide(EntraProxyClient::class)
                provide<EntraBerikelseCache> { EntraBerikelseCache(resolve()) }
                provide<AzureAdTokenIntrospector> {
                    MockAzureAdIntrospector {
                        if (it == "valid-azure-token") {
                            mockAzureAdIntrospectionResponse
                                .withNavIdent("A123456")
                        } else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/saksbehandling/v1/virksomhet/ikke-et-orgnr/kontonummer") {
            bearerAuth("valid-azure-token")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `autentisert bruker faar finnes true naar kontonummer eksisterer`() = testApplication {
        mockAltinnTilganger(tilgangTil(orgnr))
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/soknad/v1/virksomhet/kontonummer-finnes/$orgnr") {
            bearerAuth("faketoken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(true, response.body<KontonummerFinnesResponse>().finnes)
    }

    @Test
    fun `autentisert bruker faar finnes false når kontonummer ikke eksisterer`() = testApplication {
        mockAltinnTilganger(tilgangTil(orgnr))
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.NotFound to "ikke funnet"
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/soknad/v1/virksomhet/kontonummer-finnes/$orgnr") {
            bearerAuth("faketoken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(false, response.body<KontonummerFinnesResponse>().finnes)
    }

    @Test
    fun `bruker uten Altinn-tilgang til orgnr faar 403 og ingen oppslag`() = testApplication {
        mockAltinnTilganger(tilgangTil("999999999"))
        var oppslag = 0
        val kontoregisterClient = kontoregisterClient {
            oppslag++
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/soknad/v1/virksomhet/kontonummer-finnes/$orgnr") {
            bearerAuth("faketoken")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, oppslag)
    }

    @Test
    fun `uautentisert request mot finnes-endepunkt gir 401`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<TokenXTokenIntrospector> { MockTokenIntrospector { null } }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/soknad/v1/virksomhet/kontonummer-finnes/$orgnr")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `ugyldig orgnr mot finnes-endepunkt gir 400`() = testApplication {
        val kontoregisterClient = kontoregisterClient {
            HttpStatusCode.OK to """{"mottaker": "$orgnr", "kontonr": "12345678901"}"""
        }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        application {
            dependencies {
                provide { kontoregisterClient }
                provide { AltinnTilgangerClient(defaultHttpClient = client, tokenExchanger = successTokenXTokenExchanger) }
                provide<TokenXTokenIntrospector> {
                    MockTokenIntrospector {
                        if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                    }
                }
            }

            configureAuthentication()
            configureKontoregisterApiV1()
            configureServer()
        }

        val response = client.get("/api/soknad/v1/virksomhet/kontonummer-finnes/123") {
            bearerAuth("faketoken")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
