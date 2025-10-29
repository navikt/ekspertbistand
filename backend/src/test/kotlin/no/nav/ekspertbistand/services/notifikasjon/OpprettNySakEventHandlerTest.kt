package no.nav.ekspertbistand.services.notifikasjon

import com.expediagroup.graphql.client.serialization.types.KotlinxGraphQLResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.parseToJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
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
import kotlin.test.Test
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
    fun `Event prosesseres og sak med beskjed opprettes`() = testApplication {
        produsentApi(NySakVellykket(id = "sak-123"), NyBeskjedVellykket(id = "beskjed-456"))
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

        startApplication()
        val handler = application.dependencies.resolve<OpprettNySakEventHandler>()

        val event = Event(
            id = 1L,
            data = EventData.SkjemaInnsendt(
                skjema = skjema1
            )
        )
        handler.handle2(event)
    }
}

private val skjema1 = DTO.Skjema(
    virksomhet = DTO.Virksomhet(
        virksomhetsnummer = "1337",
        kontaktperson = DTO.Kontaktperson(
            navn = "Donald Duck",
            epost = "Donald@duck.co",
            telefon = "12345678"
        )
    ),
    ansatt = DTO.Ansatt(
        fodselsnummer = "12345678910",
        navn = "Ole Olsen"
    ),
    ekspert = DTO.Ekspert(
        navn = "Egon Olsen",
        virksomhet = "Olsenbanden AS",
        kompetanse = "Bankran",
        problemstilling = "Hvordan gjennomføre et bankran?" // max 5000 chars
    ),
    tiltak = DTO.Tiltak(
        forTilrettelegging = "Tilrettelegging på arbeidsplassen"
    ),
    bestilling = DTO.Bestilling(
        kostnad = "42",
        startDato = "2024-10-10"
    ),
    nav = DTO.Nav(
        kontakt = "Navn Navnesen"
    ),
)

private fun ApplicationTestBuilder.produsentApi(
    nySakResultat: NySakResultat,
    nyBeskjedResultat: NyBeskjedResultat
) {
    externalServices {
        hosts("https://notifikasjonsplatform.com") {
            install(ServerContentNegotiation) {
                json(
                    Json {
                        serializersModule = SerializersModule {
                            classDiscriminator = "type"
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
                                subclass(DuplikatEksternIdOgMerkelapp::class)
                                subclass(NyBeskjedVellykket::class)
                                subclass(NyBeskjedUgyldigMerkelapp::class)
                                subclass(NyBeskjedUgyldigMottaker::class)
                                subclass(NyBeskjedUkjentProdusent::class)
                            }
                        }
                    }
                )
            }
            routing {
                post("/api") {
                    val json = parseToJsonElement(call.receiveText()) as JsonObject
                    val operation = json["operationName"]!!.jsonPrimitive.content
                    when (operation) {
                        "OpprettNySak" -> {
                            call.respond(KotlinxGraphQLResponse(OpprettNySak.Result(nySakResultat)))
                        }

                        "OpprettNyBeskjed" -> {
                            call.respond(KotlinxGraphQLResponse(OpprettNyBeskjed.Result(nyBeskjedResultat)))
                        }

                        else -> error("Ukjent mutation: $operation")
                    }

                }
            }
        }
    }
}
