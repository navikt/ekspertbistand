package no.nav.ekspertbistand.services.notifikasjon

import com.expediagroup.graphql.client.serialization.types.KotlinxGraphQLResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.services.IdempotencyGuard
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.OpprettNySak
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.DuplikatEksternIdOgMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedResultat
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.*
import no.nav.ekspertbistand.skjema.DTO
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMottaker as NyBeskjedUgyldigMottaker
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UgyldigMerkelapp as NySakUgyldigMerkelapp
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UgyldigMottaker as NySakUgyldigMottaker
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UkjentProdusent as NySakUkjentProdusent
import no.nav.ekspertbistand.services.notifikasjon.graphql.generated.opprettnysak.UkjentRolle as NySakUkjentRolle

class OpprettNySakEventHandlerTest {

    @Test
    fun `Event prosesseres og sak med beskjed opprettes korrekt`() = testApplication {
        setupTestApplication()
        setProdusentApiResultat(
            mutableListOf({ NySakVellykket(id = "sak-123") }),
            mutableListOf({ NyBeskjedVellykket(id = "beskjed-456") })
        )
        startApplication()

        val handler = application.dependencies.resolve<OpprettNySakEventHandler>()

        val event = Event(
            id = 1L,
            data = EventData.SkjemaInnsendt(
                skjema = skjema1
            )
        )
        assertTrue(handler.handle(event) is EventHandledResult.Success)
    }

    @Test
    fun `Event prosesseres, men sak gir ugyldig merkelapp`() = testApplication {
        setupTestApplication()
        setProdusentApiResultat(
            mutableListOf({ NySakUgyldigMerkelapp("ugyldig merkelapp") }),
            mutableListOf({ NyBeskjedUgyldigMerkelapp("Ugyldig merkelapp") })
        )
        startApplication()
        val handler = application.dependencies.resolve<OpprettNySakEventHandler>()

        val event = Event(
            id = 1L,
            data = EventData.SkjemaInnsendt(
                skjema = skjema1
            )
        )
        assertTrue(handler.handle(event) is EventHandledResult.TransientError)
    }

    @Test
    fun `Idempotens sjekk`() =
        testApplication {
            setupTestApplication()
            setProdusentApiResultat(
                mutableListOf({ NySakVellykket(id = "sak-123") }),
                mutableListOf({ throw Exception("Test feil") }, { NyBeskjedVellykket(id = "beskjed-456") })
            )
            startApplication()
            val handler = application.dependencies.resolve<OpprettNySakEventHandler>()

            val event = Event(
                id = 1L,
                data = EventData.SkjemaInnsendt(
                    skjema = skjema1
                )
            )
            assertTrue(handler.handle(event) is EventHandledResult.TransientError) // Sak velykket, beskjed feilet
            assertTrue(handler.handle(event) is EventHandledResult.Success) // Sak guardet, beskjed velykket
        }
}


private val skjema1 = DTO.Skjema(
    id = UUID.randomUUID().toString(),
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = "1337",
        virksomhetsnavn = "foo bar AS",
        kontaktperson = DTO.Kontaktperson(
            navn = "Donald Duck",
            epost = "Donald@duck.co",
            telefonnummer = "12345678"
        )
    ),
    ansatt = DTO.Ansatt(
        fnr = "12345678910",
        navn = "Ole Olsen"
    ),
    ekspert = DTO.Ekspert(
        navn = "Egon Olsen",
        virksomhet = "Olsenbanden AS",
        kompetanse = "Bankran",
    ),
    behovForBistand = DTO.BehovForBistand(
        behov = "Tilrettelegging",
        begrunnelse = "Tilrettelegging på arbeidsplassen",
        estimertKostnad = 4200,
        tilrettelegging = "Spesialtilpasset kontor",
        startdato = LocalDate.parse("2024-11-15")
    ),
    nav = DTO.Nav(
        kontaktperson = "Navn Navnesen"
    ),
)


private fun ApplicationTestBuilder.setupTestApplication() {
    val client = createClient {
        install(ClientContentNegotiation) {
            json()
        }
    }
    val db = TestDatabase().cleanMigrate()
    application {
        dependencies {
            provide { db.config }
            provide<TokenIntrospector> {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<TokenProvider> {
                object : TokenProvider {
                    override suspend fun token(target: String): TokenResponse {
                        return TokenResponse.Success(
                            accessToken = "faketoken",
                            expiresInSeconds = 3600
                        )
                    }
                }
            }
            provide<IdempotencyGuard>(IdempotencyGuard::class)
        }
        configureDatabase()
        configureTokenXAuth()
        configureOpprettNySakEventHandler(client)
    }
}

private fun ApplicationTestBuilder.setProdusentApiResultat(
    nySakResultat: MutableList<() -> NySakResultat>,
    nyBeskjedResultat: MutableList<() -> NyBeskjedResultat>,
) {
    externalServices {
        hosts("http://notifikasjon-produsent-api.fager") {
            install(ServerContentNegotiation) {
                json(
                    Json {
                        serializersModule = SerializersModule {
                            classDiscriminator = "__typename"
                            polymorphic(NySakResultat::class) {
                                subclass(DefaultNySakResultatImplementation::class)
                                subclass(DuplikatGrupperingsid::class)
                                subclass(DuplikatGrupperingsidEtterDelete::class)
                                subclass(NySakVellykket::class)
                                subclass(NySakUgyldigMerkelapp::class)
                                subclass(NySakUgyldigMottaker::class)
                                subclass(NySakUkjentProdusent::class)
                                subclass(NySakUkjentRolle::class)
                            }
                            polymorphic(NyBeskjedResultat::class) {
                                subclass(DefaultNyBeskjedResultatImplementation::class)
                                subclass(DefaultNyBeskjedResultatImplementation::class)
                                subclass(DuplikatEksternIdOgMerkelapp::class)
                                subclass(NyBeskjedVellykket::class)
                                subclass(NyBeskjedUgyldigMerkelapp::class)
                                subclass(NyBeskjedUgyldigMottaker::class)
                                subclass(NyBeskjedUkjentProdusent::class)
                            }
                        }
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                )
            }
            routing {
                post("/api/graphql") {
                    val json = parseToJsonElement(call.receiveText()) as JsonObject
                    val operation = json["operationName"]!!.jsonPrimitive.content
                    when (operation) {
                        "OpprettNySak" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    OpprettNySak.Result(
                                        nySakResultat.removeFirst().invoke()
                                    )
                                )
                            )
                        }

                        "OpprettNyBeskjed" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    OpprettNyBeskjed.Result(
                                        nyBeskjedResultat.removeFirst().invoke()
                                    )
                                )
                            )
                        }

                        else -> error("Ukjent mutation: $operation")
                    }
                }
            }
        }
    }
}
