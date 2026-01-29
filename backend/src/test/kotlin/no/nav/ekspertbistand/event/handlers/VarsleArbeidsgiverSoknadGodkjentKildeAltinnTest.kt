package no.nav.ekspertbistand.event.handlers

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
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.*
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.graphql.generated.NyStatusSak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNySak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.DefaultNyStatusSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.StatusOppdatering
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.*
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakVellykket
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.test.Test
import kotlin.test.assertIs
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.UgyldigMerkelapp as NySakStatusUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.UkjentProdusent as NySakStatusUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent

class VarsleArbeidsgiverSoknadGodkjentKildeAltinnTest {
    private val tidspunkt = "2026-01-01T10:15:30+01:00"

    @Test
    fun `Event prosesseres og sak med beskjed opprettes korrekt`() = testApplicationWithDatabase {
        setupTestApplication(it.config.jdbcDatabase)
        setProdusentApiResultat(
            mutableListOf({ NySakVellykket(id = "sak-123") }),
            mutableListOf({ NyBeskjedVellykket(id = "beskjed-456") }),
            mutableListOf({
                NyStatusSakVellykket(
                    id = "sak-123", statuser = listOf(
                        StatusOppdatering(tidspunkt = tidspunkt)
                    )
                )
            }),
        )
        startApplication()

        val handler = application.dependencies.resolve<VarsleArbeidsgiverSoknadGodkjentKildeAltinn>()

        val event = Event(
            id = 1L,
            data = EventData.TilskuddsbrevJournalfoertKildeAltinn(
                dokumentId = 1,
                journaldpostId = 1,
                tilsagnData = sampleTilskuddsbrev()
            )
        )
        assertIs<EventHandledResult.Success>(handler.handle(event))
    }

    @Test
    fun `Idempotens sjekk`() = testApplicationWithDatabase {
            setupTestApplication(it.config.jdbcDatabase)
            setProdusentApiResultat(
                mutableListOf({ NySakVellykket(id = "sak-456") }),
                mutableListOf({ NyBeskjedVellykket(id = "beskjed-456") }),
                mutableListOf(
                    { throw Exception("Test feil") },
                    {
                        NyStatusSakVellykket(
                            id = "sak-123", statuser = listOf(
                                StatusOppdatering(tidspunkt = tidspunkt)
                            )
                        )
                    }
                ),
            )
            startApplication()
            val handler = application.dependencies.resolve<VarsleArbeidsgiverSoknadGodkjentKildeAltinn>()

            val event = Event(
                id = 1L,
                data = EventData.TilskuddsbrevJournalfoertKildeAltinn(
                    dokumentId = 1,
                    journaldpostId = 1,
                    tilsagnData = sampleTilskuddsbrev()
                )
            )
            assertIs<EventHandledResult.TransientError>(handler.handle(event)) // Beskjed vellykket, Sakstatus feilet
            assertIs<EventHandledResult.Success>(handler.handle(event)) // beskjed guardet, sakstatus velykket
        }
}


private fun ApplicationTestBuilder.setupTestApplication(database: Database) {
    val client = createClient { }
    application {
        dependencies {
            provide { database }
            provide<TokenXTokenIntrospector> {
                MockTokenIntrospector {
                    if (it == "faketoken") mockIntrospectionResponse.withPid("42") else null
                }
            }
            provide<AzureAdTokenProvider> {
                successAzureAdTokenProvider
            }
            provide<ProdusentApiKlient> { ProdusentApiKlient(resolve<AzureAdTokenProvider>(), client) }
            provide(VarsleArbeidsgiverSoknadGodkjentKildeAltinn::class)
        }
        configureTokenXAuth()
    }
}

private fun ApplicationTestBuilder.setProdusentApiResultat(
    nySakResultat: MutableList<() -> NySakResultat>,
    nyBeskjedResultat: MutableList<() -> NyBeskjedResultat>,
    nyStatusSakResultat: MutableList<() -> NyStatusSakResultat>,
) {
    externalServices {
        hosts("http://notifikasjon-produsent-api.fager") {
            install(ServerContentNegotiation) {
                json(
                    Json {
                        serializersModule = SerializersModule {
                            classDiscriminator = "__typename"
                            polymorphic(NyStatusSakResultat::class) {
                                subclass(DefaultNyStatusSakResultatImplementation::class)
                                subclass(NyStatusSakVellykket::class)
                                subclass(NySakStatusUgyldigMerkelapp::class)
                                subclass(NySakStatusUkjentProdusent::class)
                            }
                            polymorphic(NyBeskjedResultat::class) {
                                subclass(DefaultNyBeskjedResultatImplementation::class)
                                subclass(DefaultNyBeskjedResultatImplementation::class)
                                subclass(DuplikatEksternIdOgMerkelapp::class)
                                subclass(NyBeskjedVellykket::class)
                                subclass(NyBeskjedUgyldigMerkelapp::class)
                                subclass(UgyldigMottaker::class)
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
                    when (val operation = json["operationName"]!!.jsonPrimitive.content) {
                        "OpprettNySak" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    OpprettNySak.Result(
                                        nySakResultat.removeFirst().invoke()
                                    )
                                )
                            )
                        }

                        "NyStatusSak" -> {
                            call.respond(
                                KotlinxGraphQLResponse(
                                    NyStatusSak.Result(
                                        nyStatusSakResultat.removeFirst().invoke()
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

private fun sampleTilskuddsbrev() = TilsagnData(
    tilsagnNummer = TilsagnData.TilsagnNummer(
        1337,
        42,
        43,
    ),
    tilsagnDato = "01.01.2021",
    periode = TilsagnData.Periode(
        fraDato = "01.01.2021",
        tilDato = "01.02.2021"
    ),
    tiltakKode = "42",
    tiltakNavn = "Ekspertbistand",
    administrasjonKode = "etellerannet",
    refusjonfristDato = "10.01.2021",
    tiltakArrangor = TilsagnData.TiltakArrangor(
        arbgiverNavn = "Arrangøren",
        landKode = "1337",
        postAdresse = "et sted",
        postNummer = "1337",
        postSted = "hos naboen",
        orgNummerMorselskap = 43,
        orgNummer = 42,
        kontoNummer = "1234.12.12345",
        maalform = "norsk"
    ),
    totaltTilskuddbelop = 24000,
    valutaKode = "NOK",
    tilskuddListe = listOf(
        TilsagnData.Tilskudd(
            tilskuddType = "ekspertbistand",
            tilskuddBelop = 24000,
            visTilskuddProsent = false,
            tilskuddProsent = null
        )
    ),
    deltaker = TilsagnData.Deltaker(
        fodselsnr = "01010101010",
        fornavn = "navn",
        etternavn = "navnesen",
        landKode = "NO",
        postAdresse = "et sted",
        postNummer = "1234",
        postSted = "hos den andre naboen",
    ),
    antallDeltakere = 1,
    antallTimeverk = 100,
    navEnhet = TilsagnData.NavEnhet(
        navKontorNavn = "kontor1",
        navKontor = "Kontor1",
        postAdresse = "hos den tredje",
        postNummer = "1234",
        postSted = "hos den tredje",
        telefon = "12341234",
        faks = null
    ),
    beslutter = TilsagnData.Person(
        fornavn = "Ole",
        etternavn = "Brum",
    ),
    saksbehandler = TilsagnData.Person(
        fornavn = "Nasse",
        etternavn = "Nøff",
    ),
    kommentar = "Dette var unødvendig mye testdata å skrive"
)