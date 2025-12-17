package no.nav.ekspertbistand.pdl

import com.expediagroup.graphql.client.serialization.types.KotlinxGraphQLResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.pdl.graphql.generated.HentGeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.HentPerson
import no.nav.ekspertbistand.pdl.graphql.generated.enums.AdressebeskyttelseGradering
import no.nav.ekspertbistand.pdl.graphql.generated.enums.GtType
import no.nav.ekspertbistand.pdl.graphql.generated.hentgeografisktilknytning.GeografiskTilknytning
import no.nav.ekspertbistand.pdl.graphql.generated.hentperson.Adressebeskyttelse
import no.nav.ekspertbistand.pdl.graphql.generated.hentperson.Folkeregistermetadata
import no.nav.ekspertbistand.pdl.graphql.generated.hentperson.Person
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class PdlApiKlientTest {

    @Test
    fun `HentAdressebeskyttelse fra PDL returnerer tom liste`() = testApplication {
        setupTestApplication()
        setPdlApiRespons(
            hentPerson = { Person(adressebeskyttelse = listOf()) },
            hentGeografiskTilknytning = { GeografiskTilknytning() }
        )
        startApplication()

        val pdlKlient = PdlApiKlient(
            "fakeToken",
            tokenExchanger = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        val person = pdlKlient.hentAdressebeskyttelse("42")
        assertTrue { person.adressebeskyttelse.isEmpty() }
    }

    @Test
    fun `HentAdressebeskyttelse fra PDL returnerer Kode 7`() = testApplication {
        setupTestApplication()
        setPdlApiRespons(
            hentPerson = {
                Person(
                    adressebeskyttelse = listOf(
                        Adressebeskyttelse(
                            gradering = AdressebeskyttelseGradering.FORTROLIG,
                            folkeregistermetadata = Folkeregistermetadata(
                                gyldighetstidspunkt = "01.01.2020",
                                opphoerstidspunkt = "01.01.2030"
                            )
                        )
                    )
                )
            },
            hentGeografiskTilknytning = { GeografiskTilknytning() }
        )
        startApplication()

        val pdlKlient = PdlApiKlient(
            "fakeToken",
            tokenExchanger = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        val person = pdlKlient.hentAdressebeskyttelse("42")
        assertEquals(person.adressebeskyttelse.size, 1)
        assertEquals(AdressebeskyttelseGradering.FORTROLIG, person.adressebeskyttelse.first().gradering)
    }

    @Test
    fun `HentGeografiskTilknytning fra PDL returnerer korrekt`() = testApplication {
        setupTestApplication()
        setPdlApiRespons(
            hentPerson = { Person(adressebeskyttelse = listOf()) },
            hentGeografiskTilknytning = {
                GeografiskTilknytning(
                    gtType = GtType.KOMMUNE,
                    gtLand = "Norge",
                    gtKommune = "Oslo",
                    gtBydel = "Sagene"
                )
            },
        )
        startApplication()

        val pdlKlient = PdlApiKlient(
            "fakeToken",
            tokenExchanger = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        val tilknytning = pdlKlient.hentGeografiskTilknytning("42")
        assertTrue { tilknytning.gtLand == "Norge" }
        assertTrue { tilknytning.gtKommune == "Oslo" }
        assertTrue { tilknytning.gtBydel == "Sagene" }
        assertTrue { tilknytning.gtType == GtType.KOMMUNE }
    }
}


private fun ApplicationTestBuilder.setupTestApplication() {
    application {
        dependencies {
            provide<TokenXTokenIntrospector> {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<TokenXTokenExchanger> {
                successTokenXTokenExchanger
            }
        }
        configureTokenXAuth()
    }
}


private fun ApplicationTestBuilder.setPdlApiRespons(
    hentPerson: () -> Person,
    hentGeografiskTilknytning: () -> GeografiskTilknytning,
) {
    externalServices {
        hosts(PdlApiKlient.baseUrl) {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                post("/graphql") {
                    val json = parseToJsonElement(call.receiveText()) as JsonObject
                    when (val operation = json["operationName"]!!.jsonPrimitive.content) {
                        "HentPerson" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    HentPerson.Result(
                                        hentPerson.invoke()
                                    )
                                )
                            )
                        }

                        "HentGeografiskTilknytning" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    HentGeografiskTilknytning.Result(
                                        hentGeografiskTilknytning.invoke()
                                    )
                                )
                            )
                        }

                        else -> error("Ukjent query: $operation")
                    }
                }
            }
        }
    }
}