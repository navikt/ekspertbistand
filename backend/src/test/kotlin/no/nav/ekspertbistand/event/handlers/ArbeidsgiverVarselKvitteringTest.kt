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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.domain.ArbeidsgiverVarselDomain
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import no.nav.ekspertbistand.notifikasjon.graphql.generated.NyStatusSak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNyBeskjed
import no.nav.ekspertbistand.notifikasjon.graphql.generated.OpprettNySak
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.DefaultNyStatusSakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.NyStatusSakVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.nystatussak.StatusOppdatering
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.DefaultNyBeskjedResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnybeskjed.NyBeskjedVellykket
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.DefaultNySakResultatImplementation
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakResultat
import no.nav.ekspertbistand.notifikasjon.graphql.generated.opprettnysak.NySakVellykket
import no.nav.ekspertbistand.tilsagndata.insertTilsagndata
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Kvitteringene fra handlerne som varsler arbeidsgiver om søknaden. Se
 * `specifications/kvittering_varsel_arbeidsgiver_soknad.md`.
 */
@OptIn(ExperimentalTime::class)
class ArbeidsgiverVarselKvitteringTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@ArbeidsgiverVarselKvitteringTest.now
    }
    private val soknadId = "00000000-0000-4000-8000-000000000042"
    private val soknad = TestEventData.sampleSoknad.copy(id = soknadId)
    private val kvitteringsLenke = "https://arbeidsgiver.intern.dev.nav.no/ekspertbistand/skjema/$soknadId/kvittering"
    private val svar = "Testbedrift AS har fått svar på en søknad om ekspertbistand. " +
        "Logg inn på Min side – arbeidsgiver på Nav sine sider for å se det."
    private val eksterntVarsel = ArbeidsgiverVarselDomain.EksterntVarsel(
        epostTittel = "Nav – angående søknad om ekspertbistand",
        epostHtmlBody = svar,
        smsTekst = svar,
        sendevindu = ArbeidsgiverVarselDomain.Sendevindu.NKS_AAPNINGSTID,
    )

    private val vellykketSak = { NySakVellykket(id = "sak-1") }
    private val vellykketBeskjed = { NyBeskjedVellykket(id = "beskjed-1") }
    private val vellykketStatus = {
        NyStatusSakVellykket(id = "sak-1", statuser = listOf(StatusOppdatering(tidspunkt = "2026-11-24T11:00:00+01:00")))
    }
    private val nede: () -> Nothing = { throw IllegalStateException("plattformen er nede") }

    /** Svarene notifikasjonsplattformen gir, i rekkefølge, og variablene i kallene den fikk. */
    private class Plattform(
        val sakSvar: MutableList<() -> NySakResultat> = mutableListOf(),
        val beskjedSvar: MutableList<() -> NyBeskjedResultat> = mutableListOf(),
        val statusSvar: MutableList<() -> NyStatusSakResultat> = mutableListOf(),
    ) {
        val saker = mutableListOf<JsonObject>()
        val beskjeder = mutableListOf<JsonObject>()
        val statuser = mutableListOf<JsonObject>()
    }

    private fun mottattIArena() = Event(
        1L,
        EventData.TiltaksgjennomforingOpprettet(soknad = soknad, saksnummer = "2026202", tiltaksgjennomfoeringId = 1),
    )

    private fun sakOpprettet() = Event(
        2L,
        EventData.SakOpprettet(sakId = "11111111-1111-1111-1111-111111111111", soknad = soknad),
    )

    private fun godkjent() = Event(
        3L,
        EventData.TilsagnsdataLagret(soknad = soknad, tilsagnData = TestEventData.sampleTilsagnData),
    )

    private fun avlyst() = Event(
        4L,
        EventData.SoknadAvlystIArena(
            soknad = soknad,
            tiltaksgjennomforingEndret = TestEventData.sampleTiltaksgjennomforingEndret,
        ),
    )

    private fun ApplicationTestBuilder.klient(plattform: Plattform): ProdusentApiKlient {
        mockNotifikasjonsplattform(plattform)
        return ProdusentApiKlient(successAzureAdTokenProvider, createClient {})
    }

    private fun koen(database: Database): List<EventData> = transaction(database) {
        QueuedEvents.selectAll().orderBy(QueuedEvents.id).map { it.tilQueuedEvent().eventData }
    }

    private fun mottattVarsel() = ArbeidsgiverVarselDomain(
        merkelapp = "Ekspertbistand",
        mottaker = ArbeidsgiverVarselDomain.Mottaker("nav_tiltak_ekspertbistand"),
        virksomhetsnummer = "987654321",
        grupperingsid = soknadId,
        sak = ArbeidsgiverVarselDomain.Sak(
            tittel = "Ekspertbistand Ansatt Navn f. 01.01.01",
            lenke = kvitteringsLenke,
        ),
        beskjed = ArbeidsgiverVarselDomain.Beskjed(
            eksternId = "$soknadId-soknad-mottatt",
            tekst = "Nav har mottatt deres søknad om ekspertbistand.",
            lenke = kvitteringsLenke,
            eksterntVarsel = null,
        ),
        saksstatus = null,
    )

    @Test
    fun `mottatt i Arena kvitterer sak og beskjed slik de ble sendt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(mutableListOf(vellykketSak), mutableListOf(vellykketBeskjed))
        val handler = VarsleArbeidsgiverSoknadMottattBehandlesIArena(klient(plattform), database, clock)

        assertIs<EventHandledResult.Success>(handler.handle(mottattIArena()))

        val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletSoknadMottatt>().single()
        assertEquals(soknadId, kvittering.soknadId)
        assertEquals(now, kvittering.tidspunkt)
        assertEquals(mottattVarsel(), kvittering.varsel)
        assertSendtSom(kvittering.varsel, plattform)
    }

    @Test
    fun `sak opprettet kvitterer sak og beskjed slik de ble sendt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(mutableListOf(vellykketSak), mutableListOf(vellykketBeskjed))
        val handler = VarsleArbeidsgiverSakOpprettet(klient(plattform), database, clock)

        assertIs<EventHandledResult.Success>(handler.handle(sakOpprettet()))

        val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletSoknadMottatt>().single()
        assertEquals(soknadId, kvittering.soknadId)
        assertEquals(now, kvittering.tidspunkt)
        assertEquals(mottattVarsel(), kvittering.varsel)
        assertSendtSom(kvittering.varsel, plattform)
    }

    @Test
    fun `godkjent kvitterer tilsagnsnummer, beskjed og status slik de ble sendt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(beskjedSvar = mutableListOf(vellykketBeskjed), statusSvar = mutableListOf(vellykketStatus))
        val handler = VarsleArbeidsgiverSoknadGodkjent(klient(plattform), database, clock)

        assertIs<EventHandledResult.Success>(handler.handle(godkjent()))

        val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletSoknadGodkjent>().single()
        assertEquals(soknadId, kvittering.soknadId)
        assertEquals("1337:42:43", kvittering.tilsagnNummer)
        assertEquals(now, kvittering.tidspunkt)
        assertEquals(
            ArbeidsgiverVarselDomain(
                merkelapp = "Ekspertbistand",
                mottaker = ArbeidsgiverVarselDomain.Mottaker("nav_tiltak_ekspertbistand"),
                virksomhetsnummer = "987654321",
                grupperingsid = soknadId,
                sak = null,
                beskjed = ArbeidsgiverVarselDomain.Beskjed(
                    eksternId = "$soknadId-godkjent-1337:42:43",
                    tekst = "Søknaden er godkjent og ekspertbistand kan nå tas i bruk.",
                    lenke = kvitteringsLenke,
                    eksterntVarsel = eksterntVarsel,
                ),
                saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
                    status = ArbeidsgiverVarselDomain.Status.FERDIG,
                    statusTekst = "Søknad godkjent",
                    nyLenkeTilSak = null,
                ),
            ),
            kvittering.varsel,
        )
        assertSendtSom(kvittering.varsel, plattform)
    }

    @Test
    fun `godkjent på nytt kvitterer teksten for andre tilsagn`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        transaction(database) {
            insertTilsagndata(UUID.fromString(soknadId), TestEventData.sampleTilsagnData)
            insertTilsagndata(UUID.fromString(soknadId), TestEventData.sampleTilsagnData)
        }
        val plattform = Plattform(beskjedSvar = mutableListOf(vellykketBeskjed), statusSvar = mutableListOf(vellykketStatus))
        val handler = VarsleArbeidsgiverSoknadGodkjent(klient(plattform), database, clock)

        assertIs<EventHandledResult.Success>(handler.handle(godkjent()))

        val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletSoknadGodkjent>().single()
        assertEquals("Søknaden er godkjent på nytt, se oppdaterte opplysninger.", kvittering.varsel.beskjed.tekst)
        assertSendtSom(kvittering.varsel, plattform)
    }

    @Test
    fun `avlyst kvitterer beskjed og status slik de ble sendt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(beskjedSvar = mutableListOf(vellykketBeskjed), statusSvar = mutableListOf(vellykketStatus))
        val handler = VarsleArbeidsgiverSoknadAvlyst(klient(plattform), database, clock)

        assertIs<EventHandledResult.Success>(handler.handle(avlyst()))

        val kvittering = koen(database).filterIsInstance<EventData.ArbeidsgiverVarsletSoknadAvlyst>().single()
        assertEquals(soknadId, kvittering.soknadId)
        assertEquals(now, kvittering.tidspunkt)
        assertEquals(
            ArbeidsgiverVarselDomain(
                merkelapp = "Ekspertbistand",
                mottaker = ArbeidsgiverVarselDomain.Mottaker("nav_tiltak_ekspertbistand"),
                virksomhetsnummer = "987654321",
                grupperingsid = soknadId,
                sak = null,
                beskjed = ArbeidsgiverVarselDomain.Beskjed(
                    eksternId = "$soknadId-soknad-avlyst",
                    tekst = "Søknaden om ekspertbistand trukket eller avslått.",
                    lenke = kvitteringsLenke,
                    eksterntVarsel = eksterntVarsel,
                ),
                saksstatus = ArbeidsgiverVarselDomain.Saksstatus(
                    status = ArbeidsgiverVarselDomain.Status.FERDIG,
                    statusTekst = "Søknad trukket eller avslått",
                    nyLenkeTilSak = null,
                ),
            ),
            kvittering.varsel,
        )
        assertSendtSom(kvittering.varsel, plattform)
    }

    @Test
    fun `mottatt i Arena kvitterer ikke når plattformen feiler, og kvitterer én gang ved nytt forsøk`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val plattform = Plattform(mutableListOf(vellykketSak), mutableListOf(nede, vellykketBeskjed))
            val handler = VarsleArbeidsgiverSoknadMottattBehandlesIArena(klient(plattform), database, clock)

            feilerSaNyttForsok(database, plattform, handler, mottattIArena())
            assertEquals(1, plattform.saker.size)
        }

    @Test
    fun `sak opprettet kvitterer ikke når plattformen feiler, og kvitterer én gang ved nytt forsøk`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val plattform = Plattform(mutableListOf(nede, vellykketSak), mutableListOf(vellykketBeskjed))
            val handler = VarsleArbeidsgiverSakOpprettet(klient(plattform), database, clock)

            feilerSaNyttForsok(database, plattform, handler, sakOpprettet())
            assertEquals(1, plattform.beskjeder.size)
        }

    @Test
    fun `godkjent kvitterer ikke når plattformen feiler, og kvitterer én gang ved nytt forsøk`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val plattform = Plattform(
                beskjedSvar = mutableListOf(vellykketBeskjed),
                statusSvar = mutableListOf(nede, vellykketStatus),
            )
            val handler = VarsleArbeidsgiverSoknadGodkjent(klient(plattform), database, clock)

            feilerSaNyttForsok(database, plattform, handler, godkjent())
            assertEquals(1, plattform.beskjeder.size)
        }

    @Test
    fun `avlyst kvitterer ikke når plattformen feiler, og kvitterer én gang ved nytt forsøk`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val plattform = Plattform(
                beskjedSvar = mutableListOf(nede, vellykketBeskjed),
                statusSvar = mutableListOf(vellykketStatus),
            )
            val handler = VarsleArbeidsgiverSoknadAvlyst(klient(plattform), database, clock)

            feilerSaNyttForsok(database, plattform, handler, avlyst())
            assertEquals(1, plattform.statuser.size)
        }

    @Test
    fun `nytt forsøk etter suksess varsler og kvitterer ikke på nytt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val plattform = Plattform(
            sakSvar = mutableListOf(vellykketSak, vellykketSak),
            beskjedSvar = MutableList(4) { vellykketBeskjed },
            statusSvar = mutableListOf(vellykketStatus, vellykketStatus),
        )
        val klient = klient(plattform)
        val handlere = listOf(
            suspend { VarsleArbeidsgiverSoknadMottattBehandlesIArena(klient, database, clock).handle(mottattIArena()) },
            suspend { VarsleArbeidsgiverSakOpprettet(klient, database, clock).handle(sakOpprettet()) },
            suspend { VarsleArbeidsgiverSoknadGodkjent(klient, database, clock).handle(godkjent()) },
            suspend { VarsleArbeidsgiverSoknadAvlyst(klient, database, clock).handle(avlyst()) },
        )
        handlere.forEach { handle -> assertIs<EventHandledResult.Success>(handle()) }
        val etterForste = koen(database)
        val kall = Triple(plattform.saker.size, plattform.beskjeder.size, plattform.statuser.size)

        handlere.forEach { handle -> assertIs<EventHandledResult.Success>(handle()) }

        assertEquals(4, etterForste.size)
        assertEquals(etterForste, koen(database))
        assertEquals(kall, Triple(plattform.saker.size, plattform.beskjeder.size, plattform.statuser.size))
    }

    @Test
    fun `varsel fra før sak fantes kan leses`() {
        val gammel = """
            {"merkelapp":"Ekspertbistand","mottaker":{"altinnRessursId":"nav_tiltak_ekspertbistand"},
             "virksomhetsnummer":"912345670","grupperingsid":"$soknadId",
             "beskjed":{"eksternId":"$soknadId-vedtak","tekst":"Søknaden om ekspertbistand er avslått.",
               "lenke":"https://example.org/vedtak",
               "eksterntVarsel":{"epostTittel":"t","epostHtmlBody":"b","smsTekst":"s","sendevindu":"NKS_AAPNINGSTID"}},
             "saksstatus":{"status":"FERDIG","statusTekst":"Søknad avslått","nyLenkeTilSak":"https://example.org/kvittering"}}
        """.trimIndent()

        val varsel = Json.decodeFromString<ArbeidsgiverVarselDomain>(gammel)

        assertNull(varsel.sak)
        assertEquals("Søknad avslått", varsel.saksstatus?.statusTekst)
        assertEquals("NKS_AAPNINGSTID", varsel.beskjed.eksterntVarsel?.sendevindu?.name)
    }

    private suspend fun <T : EventData> feilerSaNyttForsok(
        database: Database,
        plattform: Plattform,
        handler: EventHandler<T>,
        event: Event<T>,
    ) {
        assertIs<EventHandledResult.TransientError>(handler.handle(event))
        assertTrue(koen(database).isEmpty())

        assertIs<EventHandledResult.Success>(handler.handle(event))
        val kvittering = koen(database).single()
        assertSendtSom(kvittering.varsel(), plattform, sisteKall = true)
    }

    private fun EventData.varsel() = when (this) {
        is EventData.ArbeidsgiverVarsletSoknadMottatt -> varsel
        is EventData.ArbeidsgiverVarsletSoknadGodkjent -> varsel
        is EventData.ArbeidsgiverVarsletSoknadAvlyst -> varsel
        else -> error("Ikke en kvittering: ${this::class.simpleName}")
    }

    /** Feltene i [varsel] er de samme som variablene i kallene til plattformen. */
    private fun assertSendtSom(varsel: ArbeidsgiverVarselDomain, plattform: Plattform, sisteKall: Boolean = false) {
        fun List<JsonObject>.kall() = if (sisteKall) last() else single()

        val sak = varsel.sak
        if (sak == null) {
            assertTrue(plattform.saker.isEmpty())
        } else {
            val sendt = plattform.saker.kall()
            assertEquals(varsel.merkelapp, sendt.tekst("merkelapp"))
            assertEquals(varsel.grupperingsid, sendt.tekst("grupperingsid"))
            assertEquals(varsel.virksomhetsnummer, sendt.tekst("virksomhetsnummer"))
            assertEquals(varsel.mottaker.altinnRessursId, sendt.objekt("mottaker").objekt("altinnRessurs").tekst("ressursId"))
            assertEquals(sak.tittel, sendt.tekst("tittel"))
            assertEquals(sak.lenke, sendt.tekst("lenke"))
        }

        val beskjed = plattform.beskjeder.kall()
        assertEquals(varsel.merkelapp, beskjed.tekst("merkelapp"))
        assertEquals(varsel.grupperingsid, beskjed.tekst("grupperingsid"))
        assertEquals(varsel.virksomhetsnummer, beskjed.tekst("virksomhetsnummer"))
        assertEquals(varsel.mottaker.altinnRessursId, beskjed.objekt("mottaker").objekt("altinnRessurs").tekst("ressursId"))
        assertEquals(varsel.beskjed.eksternId, beskjed.tekst("eksternId"))
        assertEquals(varsel.beskjed.tekst, beskjed.tekst("tekst"))
        assertEquals(varsel.beskjed.lenke, beskjed.tekst("lenke"))
        val eksterneVarsler = beskjed.getValue("eksterneVarsler").jsonArray
        val eksterntVarsel = varsel.beskjed.eksterntVarsel
        if (eksterntVarsel == null) {
            assertTrue(eksterneVarsler.isEmpty())
        } else {
            val eksternt = eksterneVarsler.single().jsonObject.objekt("altinnressurs")
            assertEquals(eksterntVarsel.epostTittel, eksternt.tekst("epostTittel"))
            assertEquals(eksterntVarsel.epostHtmlBody, eksternt.tekst("epostHtmlBody"))
            assertEquals(eksterntVarsel.smsTekst, eksternt.tekst("smsTekst"))
            assertEquals(eksterntVarsel.sendevindu.name, eksternt.objekt("sendetidspunkt").tekst("sendevindu"))
        }

        val saksstatus = varsel.saksstatus
        if (saksstatus == null) {
            assertTrue(plattform.statuser.isEmpty())
        } else {
            val status = plattform.statuser.kall()
            assertEquals(varsel.merkelapp, status.tekst("merkelapp"))
            assertEquals(varsel.grupperingsid, status.tekst("grupperingsid"))
            assertEquals(saksstatus.status.name, status.tekst("nyStatus"))
            assertEquals(saksstatus.statusTekst, status.tekst("overstyrStatustekstMed"))
            assertEquals(saksstatus.nyLenkeTilSak, status["nyLenkeTilSak"]?.jsonPrimitive?.contentOrNull)
        }
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
                                polymorphic(NySakResultat::class) {
                                    subclass(DefaultNySakResultatImplementation::class)
                                    subclass(NySakVellykket::class)
                                }
                                polymorphic(NyStatusSakResultat::class) {
                                    subclass(DefaultNyStatusSakResultatImplementation::class)
                                    subclass(NyStatusSakVellykket::class)
                                }
                                polymorphic(NyBeskjedResultat::class) {
                                    subclass(DefaultNyBeskjedResultatImplementation::class)
                                    subclass(NyBeskjedVellykket::class)
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
                            "OpprettNySak" -> {
                                plattform.saker += variables
                                call.respond(KotlinxGraphQLResponse(OpprettNySak.Result(plattform.sakSvar.removeFirst()())))
                            }

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
