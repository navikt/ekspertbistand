package no.nav.ekspertbistand.notifikasjon

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.nav.ekspertbistand.infrastruktur.AzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.notifikasjon.graphql.generated.enums.SaksStatus
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DuplikatEksternIdOgMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.*
import kotlin.test.Test
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMottaker as NyBeskjedUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UkjentProdusent as NyBeskjedUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMerkelapp as NySakUgyldigMerkelapp
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UgyldigMottaker as NySakUgyldigMottaker
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentProdusent as NySakUkjentProdusent
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.UkjentRolle as NySakUkjentRolle

class ProdusentApiKlientTest {

    private val skjemaId = "42"
    private val virksomhetsnummer = "1337"
    private val tidspunkt = "2026-01-01T10:15:30+01:00"

    @Test
    fun `Ny Sak`() = testApplication {
        setupApplication()
        setProdusentApiResultat {
            //language=json
            """{
                "data": {
                  "nySak": {
                    "__typename": "NySakVellykket",
                    "id": "$skjemaId"
                    }
                },
                "errors": []
                }
            """.trimIndent()
        }
        startApplication()

        val klient = ProdusentApiKlient(
            azureAdTokenProvider = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        klient.opprettNySak( // kaster exception dersom det feiler
            skjemaId = skjemaId,
            virksomhetsnummer = virksomhetsnummer,
            tittel = "ny sak",
            lenke = "http://foo.bar"
        )
    }

    @Test
    fun NyBeskjed() = testApplication {
        setupApplication()
        setProdusentApiResultat {
            //language=json
            """{
                "data": {
                  "nyBeskjed": {
                    "__typename": "NyBeskjedVellykket",
                    "id": "$skjemaId"
                    }
                },
                "errors": []
                }
            """.trimIndent()
        }
        startApplication()

        val klient = ProdusentApiKlient(
            azureAdTokenProvider = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        klient.opprettNyBeskjed(
            // kaster exception dersom det feiler
            skjemaId = skjemaId,
            virksomhetsnummer = virksomhetsnummer,
            lenke = "http://foo.bar",
            tekst = "ny beskjed",
        )
    }

    @Test
    fun NyStatusSak() = testApplication {
        setupApplication()
        setProdusentApiResultat {
            //language=json
            """{
                "data": {
                  "nyStatusSakByGrupperingsid": {
                    "__typename": "NyStatusSakVellykket",
                    "id": "$skjemaId",
                    "statuser": [{
                      "status": "MOTTATT",
                      "tidspunkt": "$tidspunkt",
                      "overstyrStatusTekstMed": "ny status"
                    }]
                },
                "errors": []
                }
            }
            """.trimIndent()
        }
        startApplication()

        val klient = ProdusentApiKlient(
            azureAdTokenProvider = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        klient.nyStatusSak(
            // kaster exception dersom det feiler
            skjemaId = skjemaId,
            status = SaksStatus.MOTTATT,
            statusTekst = "ny status",
            tidspunkt = tidspunkt,
        )
    }

    private fun ApplicationTestBuilder.setupApplication() {
        application {
            dependencies {
                provide<AzureAdTokenProvider> {
                    successAzureAdTokenProvider
                }
            }
        }
    }

    @Test
    fun HardDelete() = testApplication {
        setupApplication()
        setProdusentApiResultat {
            //language=json
            """{
                "data": {
                  "hardDeleteSakByGrupperingsid": {
                    "__typename": "HardDeleteSakVellykket",
                    "id": "$skjemaId"
                    }
                },
                "errors": []
                }
            """.trimIndent()
        }
        startApplication()

        val klient = ProdusentApiKlient(
            azureAdTokenProvider = application.dependencies.resolve(),
            defaultHttpClient = client
        )

        klient.hardDeleteSak(
            // kaster exception dersom det feiler
            skjemaId = skjemaId,
        )
    }

    private fun ApplicationTestBuilder.setProdusentApiResultat(
        resultat: () -> String
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
                        call.respond(
                            resultat()
                        )
                    }
                }
            }
        }
    }
}
