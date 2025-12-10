package no.nav.ekspertbistand.norg

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class NorgKlientTest {
    @Test
    fun `Norg returnerer tom liste`() = testApplication {
        setNorgApiRespons(
            resolver = { listOf() },
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val norgKlient = NorgKlient(
            httpClient = client
        )

        val enhet = norgKlient.hentBehandlendeEnhet("42")
        assertNull(enhet)
    }

    @Test
    fun `Norg returnerer en behandlende enhet`() = testApplication {
        setNorgApiRespons(
            resolver = {
                listOf(
                    Norg2Enhet(
                        enhetNr = "1337", status = "Aktiv"
                    )
                )
            },
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val norgKlient = NorgKlient(
            httpClient = client
        )

        val enhet = norgKlient.hentBehandlendeEnhet("42")
        assertEquals("1337", enhet!!.enhetNr)
    }

    @Test
    fun `Norg returnerer en behandlende enhet, men den er nedlagt`() = testApplication {
        setNorgApiRespons(
            resolver = {
                listOf(
                    Norg2Enhet(
                        enhetNr = "1337", status = "nedlagt'"
                    )
                )
            },
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val norgKlient = NorgKlient(
            httpClient = client
        )

        val enhet = norgKlient.hentBehandlendeEnhet("42")
        assertNull(enhet)
    }

    @Test
    fun `Norg returnerer statuskode 500`() = testApplication {
        setNorgApiRespons(
            resolver = {
                throw Exception("Norg api server kræsjer")
            },
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val norgKlient = NorgKlient(
            httpClient = client
        )

        assertThrows<RuntimeException>({ norgKlient.hentBehandlendeEnhet("42") })
    }
}

private fun ApplicationTestBuilder.setNorgApiRespons(
    resolver: () -> List<Norg2Enhet>,
) {
    externalServices {
        hosts(NorgKlient.baseUrl) {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                post("norg2/api/v1/arbeidsfordeling/enheter/bestmatch") {
                    call.respond(resolver.invoke())
                }
            }
        }
    }
}

