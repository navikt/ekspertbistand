package no.nav.ekspertbistand.norg

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import kotlin.test.Test
import kotlin.test.assertEquals
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class BehandlendeEnhetTest {
    @Test
    fun `Behandlende enhet for adressebeskyttet arbeidstaker`() = testApplication {
        setNorgApiRespons(
            strengtFortroligResponse = {
                listOf(
                    Norg2Enhet(
                        enhetNr = "1337",
                        status = "Aktiv"
                    )
                )
            }
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val service = BehandlendeEnhetService(
            NorgKlient(
                httpClient = client
            )
        )

        val enhet = service.hentBehandlendeEnhet(AdressebeskyttelseGradering.STRENGT_FORTROLIG, "42")
        assertEquals("1337", enhet)
    }

    @Test
    fun `Behandlende enhet for adressebeskyttet arbeidstaker fallback`() = testApplication {
        setNorgApiRespons() // setter tom respons
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val service = BehandlendeEnhetService(
            NorgKlient(
                httpClient = client
            )
        )

        val enhet = service.hentBehandlendeEnhet(AdressebeskyttelseGradering.STRENGT_FORTROLIG, "42")
        assertEquals(BehandlendeEnhetService.NAV_VIKAFOSSEN, enhet)
    }

    @Test
    fun `Behandlende enhet for ugradert arbeidstaker`() = testApplication {
        setNorgApiRespons(
            ugradertResponse = {
                listOf(
                    Norg2Enhet(
                        enhetNr = "1337",
                        status = "Aktiv"
                    )
                )
            }
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val service = BehandlendeEnhetService(
            NorgKlient(
                httpClient = client
            )
        )

        val enhet = service.hentBehandlendeEnhet(AdressebeskyttelseGradering.UGRADERT, "42")
        assertEquals("1337", enhet)
    }

    @Test
    fun `Behandlende enhet for ugradert arbeidstaker der enhet har ulik id i arena og norg`() = testApplication {
        setNorgApiRespons(
            ugradertResponse = {
                listOf(
                    Norg2Enhet(
                        enhetNr = BehandlendeEnhetService.NAV_ARBEIDSLIVSSENTER_NORDLAND_NORG,
                        status = "Aktiv"
                    )
                )
            }
        )
        client = createClient {
            install(ClientContentNegotiation) {
                json()
            }
        }

        val service = BehandlendeEnhetService(
            NorgKlient(
                httpClient = client
            )
        )

        val enhet = service.hentBehandlendeEnhet(AdressebeskyttelseGradering.UGRADERT, "42")
        assertEquals(BehandlendeEnhetService.NAV_ARBEIDSLIVSSENTER_NORDLAND_ARENA, enhet)
    }

}

private fun ApplicationTestBuilder.setNorgApiRespons(
    ugradertResponse: () -> List<Norg2Enhet> = { listOf() },
    strengtFortroligResponse: () -> List<Norg2Enhet> = { listOf() },
) {
    externalServices {
        hosts(NorgKlient.baseUrl) {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                post("norg2/api/v1/arbeidsfordeling/enheter/bestmatch") {
                    val body = call.receive<Norg2Request>()
                    when (body.diskresjonskode) {
                        NorgKlient.DISKRESJONSKODE_ADRESSEBESKYTTET -> call.respond(strengtFortroligResponse.invoke())
                        NorgKlient.DISKRESJONSKODE_ANY -> call.respond(ugradertResponse.invoke())
                        else -> throw Exception("Testen er ikke konfiguert for andre diskresjonskoder")
                    }

                }
            }
        }
    }
}