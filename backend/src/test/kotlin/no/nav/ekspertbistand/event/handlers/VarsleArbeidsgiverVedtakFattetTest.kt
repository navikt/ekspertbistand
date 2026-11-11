package no.nav.ekspertbistand.event.handlers

import com.expediagroup.graphql.client.serialization.types.KotlinxGraphQLResponse
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.graphql.generated.NyStatusSak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.DefaultNyStatusSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.StatusOppdatering
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.UgyldigMerkelapp as NyBeskjedUgyldigMerkelapp

@OptIn(ExperimentalTime::class)
class VarsleArbeidsgiverVedtakFattetTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@VarsleArbeidsgiverVedtakFattetTest.now
    }
    private val soknadId = "00000000-0000-4000-8000-000000000042"
    private val vellykketStatus = {
        NyStatusSakVellykket(id = "sak-1", statuser = listOf(StatusOppdatering(tidspunkt = "2026-11-24T11:00:00+01:00")))
    }

    /** Svarene notifikasjonsplattformen gir, i rekkefølge, og variablene i kallene den fikk. */
    private class Plattform(
        val beskjedSvar: MutableList<() -> NyBeskjedResultat> = mutableListOf(),
        val statusSvar: MutableList<() -> NyStatusSakResultat> = mutableListOf(),
    ) {
        val beskjeder = mutableListOf<JsonObject>()
        val statuser = mutableListOf<JsonObject>()
    }

    private fun event(utfall: VedtakDomain.Utfall = VedtakDomain.Utfall.INNVILGE) = Event(
        7L,
        TestEventData.vedtaksbrevArbeidsgiverJournalfoert.copy(
            soknadId = soknadId,
            utfall = utfall,
            virksomhetsnummer = "912345670",
            virksomhetsnavn = "Fra Eventen AS",
            journalpostId = "1234",
        ),
    )

    private fun ApplicationTestBuilder.handler(database: Database, plattform: Plattform): VarsleArbeidsgiverVedtakFattet {
        mockNotifikasjonsplattform(plattform)
        return VarsleArbeidsgiverVedtakFattet(
            produsentApiKlient = ProdusentApiKlient(successAzureAdTokenProvider, createClient {}),
            database = database,
            clock = clock,
        )
    }

    private fun koen(database: Database): List<EventData> = transaction(database) {
        QueuedEvents.selectAll().orderBy(QueuedEvents.id).map { it.tilQueuedEvent().eventData }
    }

    private fun varsletUtfall(utfall: VedtakDomain.Utfall, beskjedTekst: String, statusTekst: String) =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val plattform = Plattform(mutableListOf({ NyBeskjedVellykket(id = "b-1") }), mutableListOf(vellykketStatus))
            val event = event(utfall)

            assertIs<EventHandledResult.Success>(handler(database, plattform).handle(event))

            val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletVedtakFattet>().single()
            assertEquals(soknadId, kvittering.soknadId)
            assertEquals(event.data.sakId, kvittering.sakId)
            assertEquals(utfall, kvittering.utfall)
            assertEquals("1234", kvittering.journalpostId)
            assertEquals(now, kvittering.tidspunkt)

            val varsel = kvittering.varsel
            val svar = "Fra Eventen AS har fått svar på en søknad om ekspertbistand. " +
                "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
            assertEquals(
                ArbeidsgiverVarselDomain(
                    merkelapp = "Ekspertbistand",
                    mottaker = ArbeidsgiverVarselDomain.Mottaker("nav_tiltak_ekspertbistand"),
                    virksomhetsnummer = "912345670",
                    grupperingsid = soknadId,
                    beskjed = ArbeidsgiverVarselDomain.Beskjed(
                        eksternId = "$soknadId-vedtak",
                        tekst = beskjedTekst,
                        lenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/v2/skjema/$soknadId/vedtak",
                        eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
                            epostTittel = "Nav – angående søknad om ekspertbistand",
                            epostHtmlBody = svar,
                            smsTekst = svar,
                            sendevindu = ArbeidsgiverVarselDomain.Sendevindu.NKS_AAPNINGSTID,
                        ),
                    ),
                    saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
                        status = ArbeidsgiverVarselDomain.Status.FERDIG,
                        statusTekst = statusTekst,
                        nyLenkeTilSak = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/v2/skjema/$soknadId/kvittering",
                    ),
                ),
                varsel,
            )

            val beskjed = plattform.beskjeder.single()
            assertEquals(varsel.merkelapp, beskjed.tekst("merkelapp"))
            assertEquals(varsel.grupperingsid, beskjed.tekst("grupperingsid"))
            assertEquals(varsel.virksomhetsnummer, beskjed.tekst("virksomhetsnummer"))
            assertEquals(varsel.beskjed.eksternId, beskjed.tekst("eksternId"))
            assertEquals(varsel.beskjed.tekst, beskjed.tekst("tekst"))
            assertEquals(varsel.beskjed.lenke, beskjed.tekst("lenke"))
            assertEquals(varsel.mottaker.altinnRessursId, beskjed.objekt("mottaker").objekt("altinnRessurs").tekst("ressursId"))
            val eksternt = beskjed["eksterneVarsler"]!!.jsonArray.single().jsonObject.objekt("altinnressurs")
            assertEquals(varsel.mottaker.altinnRessursId, eksternt.objekt("mottaker").tekst("ressursId"))
            assertEquals(varsel.beskjed.eksterntVarsel.epostTittel, eksternt.tekst("epostTittel"))
            assertEquals(varsel.beskjed.eksterntVarsel.epostHtmlBody, eksternt.tekst("epostHtmlBody"))
            assertEquals(varsel.beskjed.eksterntVarsel.smsTekst, eksternt.tekst("smsTekst"))
            assertEquals(varsel.beskjed.eksterntVarsel.sendevindu.name, eksternt.objekt("sendetidspunkt").tekst("sendevindu"))

            val status = plattform.statuser.single()
            assertEquals(varsel.merkelapp, status.tekst("merkelapp"))
            assertEquals(varsel.grupperingsid, status.tekst("grupperingsid"))
            assertEquals(varsel.saksstatus.status.name, status.tekst("nyStatus"))
            assertEquals(varsel.saksstatus.statusTekst, status.tekst("overstyrStatustekstMed"))
            assertEquals(varsel.saksstatus.nyLenkeTilSak, status.tekst("nyLenkeTilSak"))

            val logg = koen(database).filterIsInstance<EventData.SakOppdatert>().single()
            assertEquals("Arbeidsgiver varslet om vedtak", logg.notat)
            assertEquals(AktorRolle.SYSTEM, logg.utfortAvRolle)
            assertNull(logg.utfortAvIdent)
        }

    @Test
    fun `innvilget vedtak varsles med lenke til vedtakssiden`() = varsletUtfall(
        VedtakDomain.Utfall.INNVILGE,
        "Søknaden er godkjent og ekspertbistand kan nå tas i bruk.",
        "Søknad godkjent",
    )

    @Test
    fun `innvilget med redusert beløp har statusteksten fra vedtakssiden`() = varsletUtfall(
        VedtakDomain.Utfall.INNVILGE_REDUSERT,
        "Søknaden er godkjent og ekspertbistand kan nå tas i bruk.",
        "Søknad godkjent med redusert beløp",
    )

    @Test
    fun `avslag varsles`() = varsletUtfall(
        VedtakDomain.Utfall.AVSLAA,
        "Søknaden om ekspertbistand er avslått.",
        "Søknad avslått",
    )

    @Test
    fun `feil fra opprettNyBeskjed gir ingen kvittering`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(mutableListOf({ NyBeskjedUgyldigMerkelapp("Ugyldig merkelapp") }))

        assertIs<EventHandledResult.TransientError>(handler(database, plattform).handle(event()))

        assertTrue(plattform.statuser.isEmpty())
        assertTrue(koen(database).isEmpty())
    }

    @Test
    fun `feil fra nyStatusSak sender ikke beskjeden på nytt og kvitterer én gang`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(
            mutableListOf({ NyBeskjedVellykket(id = "b-1") }),
            mutableListOf({ throw IllegalStateException("plattformen er nede") }, vellykketStatus),
        )
        val handler = handler(database, plattform)
        val event = event()

        assertIs<EventHandledResult.TransientError>(handler.handle(event))
        assertTrue(koen(database).isEmpty())

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertEquals(1, plattform.beskjeder.size)
        assertEquals(2, plattform.statuser.size)
        assertEquals(1, koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletVedtakFattet>().size)
    }

    @Test
    fun `nytt forsøk etter suksess varsler og kvitterer ikke på nytt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(mutableListOf({ NyBeskjedVellykket(id = "b-1") }), mutableListOf(vellykketStatus))
        val handler = handler(database, plattform)
        val event = event()

        assertIs<EventHandledResult.Success>(handler.handle(event))
        val etterForste = koen(database)

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertEquals(1, plattform.beskjeder.size)
        assertEquals(1, plattform.statuser.size)
        assertEquals(etterForste, koen(database))
    }

    private fun JsonObject.tekst(felt: String) = getValue(felt).jsonPrimitive.content
    private fun JsonObject.objekt(felt: String) = getValue(felt).jsonObject

    private fun ApplicationTestBuilder.mockNotifikasjonsplattform(plattform: Plattform) {
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
                                }
                                polymorphic(NyBeskjedResultat::class) {
                                    subclass(DefaultNyBeskjedResultatImplementation::class)
                                    subclass(NyBeskjedVellykket::class)
                                    subclass(NyBeskjedUgyldigMerkelapp::class)
                                }
                            }
                        }
                    )
                }
                routing {
                    post("/api/graphql") {
                        val body = Json.parseToJsonElement(call.receiveText()).jsonObject
                        val variables = body.getValue("variables").jsonObject
                        when (val operation = body.getValue("operationName").jsonPrimitive.content) {
                            "OpprettNyBeskjed" -> {
                                plattform.beskjeder += variables
                                call.respond(KotlinxGraphQLResponse(OpprettNyBeskjed.Result(plattform.beskjedSvar.removeFirst()())))
                            }

                            "NyStatusSak" -> {
                                plattform.statuser += variables
                                call.respond(KotlinxGraphQLResponse(NyStatusSak.Result(plattform.statusSvar.removeFirst()())))
                            }

                            else -> error("Ukjent mutation: $operation")
                        }
                    }
                }
            }
        }
    }
}
