package no.nav.ekspertbistand.event.handlers

import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.ekspertbistand.dokarkiv.DokArkivClient
import no.nav.ekspertbistand.dokarkiv.FagsakIdService
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostDokument
import no.nav.ekspertbistand.dokarkiv.OpprettJournalpostResponse
import no.nav.ekspertbistand.dokument.DokumentService
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.domain.SakDomain
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.infrastruktur.successAzureAdTokenProvider
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.mocks.OpprettJournalpostRequest
import no.nav.ekspertbistand.mocks.StubPdfKonverterer
import no.nav.ekspertbistand.mocks.mockDokArkiv
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.SLADDET_NAVN
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class JournalfoerVedtaksbrevArbeidsgiverTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@JournalfoerVedtaksbrevArbeidsgiverTest.now
    }

    private class Dokarkiv {
        val requests = mutableListOf<OpprettJournalpostRequest>()
    }

    private class Gotenberg {
        val html = mutableListOf<String>()
        var feil: Exception? = null
    }

    private fun ApplicationTestBuilder.handler(
        database: Database,
        dokarkiv: Dokarkiv = Dokarkiv(),
        gotenberg: Gotenberg = Gotenberg(),
        dokarkivStatus: HttpStatusCode = HttpStatusCode.Created,
        ferdigstilt: Boolean = true,
    ): JournalfoerVedtaksbrevArbeidsgiver {
        mockDokArkiv(status = dokarkivStatus) { request ->
            dokarkiv.requests += request
            OpprettJournalpostResponse(
                dokumenter = listOf(OpprettJournalpostDokument("9876")),
                journalpostId = "1234",
                journalpostferdigstilt = ferdigstilt,
            )
        }
        val pdf = StubPdfKonverterer(onConvert = { html ->
            gotenberg.feil?.let { throw it }
            gotenberg.html += html
        })
        return JournalfoerVedtaksbrevArbeidsgiver(
            dokumentService = DokumentService(pdf),
            dokArkivClient = DokArkivClient(successAzureAdTokenProvider, createClient {}),
            fagsakIdService = FagsakIdService(database),
            database = database,
            clock = clock,
        )
    }

    /** Eventens data skiller seg fra projeksjonene, så testene ser at brevet bygges fra eventen. */
    private fun event(
        sak: LagretSak,
        utfall: VedtakDomain.Utfall = VedtakDomain.Utfall.INNVILGE,
        adressebeskyttelse: SakDomain.Adressebeskyttelse? = null,
        skjermet: Boolean = false,
    ) = Event(
        1L,
        TestEventData.vedtakFattet(
            sakId = sak.sakId.toString(),
            soknadId = sak.soknadId.toString(),
            utfall = utfall,
            fattetAvIdent = BESLUTTER_IDENT,
            tidspunkt = Instant.parse("2026-11-24T23:30:00Z"),
            soknad = TestEventData.sampleSoknadDomain.copy(
                id = sak.soknadId.toString(),
                ansatt = TestEventData.sampleSoknadDomain.ansatt.copy(navn = "Deltaker Fra Eventen"),
                virksomhet = TestEventData.sampleSoknadDomain.virksomhet.copy(
                    virksomhetsnummer = "912345670",
                    virksomhetsnavn = "Fra Eventen AS",
                ),
            ),
            sak = TestEventData.sampleSakDomain.copy(
                sakId = sak.sakId.toString(),
                behandlendeEnhet = "0315",
                behandlendeEnhetNavn = "Nav arbeidslivssenter Eventen",
                adressebeskyttelse = adressebeskyttelse,
                skjermet = skjermet,
            ),
        ),
    )

    private suspend fun fattet(database: Database, event: Event<EventData.VedtakFattet>) {
        assertIs<EventHandledResult.Success>(FattVedtak(database).handle(event))
    }

    private fun koen(database: Database): List<EventData> = transaction(database) {
        QueuedEvents.selectAll().orderBy(QueuedEvents.id).map { it.tilQueuedEvent().eventData }
    }

    private fun journalfoert(database: Database) =
        koen(database).filterIsInstance<EventData.VedtaksbrevArbeidsgiverJournalfoert>()

    private fun sakslogg(database: Database) =
        koen(database).filterIsInstance<EventData.SakOppdatert>()
            .filter { it.notat == "Vedtaksbrev til arbeidsgiver journalført" }

    private fun journalfoererUtfall(utfall: VedtakDomain.Utfall, forelopig: ForelopigUtfall, tittel: String, mal: String) =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val sak = tilBeslutning(database, forelopig)
            val event = event(sak, utfall)
            fattet(database, event)
            val dokarkiv = Dokarkiv()
            val gotenberg = Gotenberg()

            assertIs<EventHandledResult.Success>(handler(database, dokarkiv, gotenberg).handle(event))

            val request = dokarkiv.requests.single()
            assertEquals(tittel, request.tittel)
            assertEquals("UTGAAENDE", request.journalposttype)
            assertEquals(OpprettJournalpostRequest.Ident("912345670", "ORGNR"), request.avsenderMottaker)
            assertEquals("912345670", request.bruker.id)
            assertEquals("9999", request.journalfoerendeEnhet)
            assertEquals("${sak.soknadId}-vedtaksbrev-arbeidsgiver", request.eksternReferanseId)

            val html = gotenberg.html.single()
            assertContains(html, mal)
            assertContains(html, "Fra Eventen AS")
            assertContains(html, "Deltaker Fra Eventen")
            assertContains(html, "Nav arbeidslivssenter Eventen")
            assertContains(html, "25. november 2026", message = "brevdato er VedtakFattet.tidspunkt i Oslo-tid")

            val kvittering = journalfoert(database).single()
            assertEquals(
                EventData.VedtaksbrevArbeidsgiverJournalfoert(
                    sakId = sak.sakId.toString(),
                    soknadId = sak.soknadId.toString(),
                    utfall = utfall,
                    virksomhetsnummer = "912345670",
                    virksomhetsnavn = "Fra Eventen AS",
                    journalpostId = "1234",
                    dokumentInfoId = "9876",
                    tidspunkt = now,
                ),
                kvittering,
            )
            val logg = sakslogg(database).single()
            assertEquals(AktorRolle.SYSTEM, logg.utfortAvRolle)
            assertNull(logg.utfortAvIdent)
        }

    @Test
    fun `innvilget vedtak journalføres med innvilgelsesbrevet`() = journalfoererUtfall(
        VedtakDomain.Utfall.INNVILGE, ForelopigUtfall.INNVILGE, "Vedtak om tilskudd til ekspertbistand", "Dere har fått innvilget tilskudd",
    )

    @Test
    fun `redusert innvilgelse journalføres med brevet for redusert beløp`() = journalfoererUtfall(
        VedtakDomain.Utfall.INNVILGE_REDUSERT,
        ForelopigUtfall.INNVILGE_REDUSERT,
        "Vedtak om tilskudd til ekspertbistand",
        "innvilget lavere tilskudd",
    )

    @Test
    fun `avslag journalføres med avslagsbrevet`() = journalfoererUtfall(
        VedtakDomain.Utfall.AVSLAA, ForelopigUtfall.AVSLAA, "Avslag på søknad om ekspertbistand", "Dere får ikke tilskudd",
    )

    private fun sladding(adressebeskyttelse: SakDomain.Adressebeskyttelse?, skjermet: Boolean, sladdet: Boolean) =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val sak = tilBeslutning(database)
            val event = event(sak, adressebeskyttelse = adressebeskyttelse, skjermet = skjermet)
            fattet(database, event)
            val gotenberg = Gotenberg()

            assertIs<EventHandledResult.Success>(handler(database, gotenberg = gotenberg).handle(event))

            val html = gotenberg.html.single()
            assertEquals(sladdet, SLADDET_NAVN in html)
            assertEquals(!sladdet, "Deltaker Fra Eventen" in html)
        }

    @Test
    fun `deltakers navn sladdes ved strengt fortrolig adresse`() =
        sladding(SakDomain.Adressebeskyttelse.STRENGT_FORTROLIG, skjermet = false, sladdet = true)

    @Test
    fun `deltakers navn sladdes ved fortrolig adresse`() =
        sladding(SakDomain.Adressebeskyttelse.FORTROLIG, skjermet = false, sladdet = true)

    @Test
    fun `skjermet deltaker uten adressebeskyttelse sladdes ikke`() =
        sladding(null, skjermet = true, sladdet = false)

    @Test
    fun `vedtak som ikke er lagret ennå gir transientError, så journalføring etter FattVedtak`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val sak = tilBeslutning(database)
            val event = event(sak)
            val dokarkiv = Dokarkiv()
            val handler = handler(database, dokarkiv)

            assertIs<EventHandledResult.TransientError>(handler.handle(event))
            assertTrue(dokarkiv.requests.isEmpty())

            fattet(database, event)
            assertIs<EventHandledResult.Success>(handler.handle(event))
            assertEquals(1, dokarkiv.requests.size)
        }

    @Test
    fun `avvist vedtak feiler uten journalføring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val utdatert = event(sak).let { e ->
            Event(e.id, TestEventData.vedtakFattet(
                sakId = e.data.sakId,
                soknadId = e.data.soknadId,
                versjon = 2,
                fattetAvIdent = BESLUTTER_IDENT,
                soknad = e.data.soknad,
                sak = e.data.sak,
            ))
        }
        FattVedtak(database).handle(utdatert)
        val dokarkiv = Dokarkiv()

        assertIs<EventHandledResult.UnrecoverableError>(handler(database, dokarkiv).handle(utdatert))

        assertTrue(dokarkiv.requests.isEmpty())
        assertTrue(journalfoert(database).isEmpty())
    }

    @Test
    fun `feil fra Gotenberg gir transientError og ingen eventer`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = event(sak)
        fattet(database, event)
        val dokarkiv = Dokarkiv()
        val gotenberg = Gotenberg().apply { feil = IllegalStateException("Gotenberg nede") }

        assertIs<EventHandledResult.TransientError>(handler(database, dokarkiv, gotenberg).handle(event))

        assertTrue(dokarkiv.requests.isEmpty())
        assertTrue(journalfoert(database).isEmpty())
        assertTrue(sakslogg(database).isEmpty())
    }

    @Test
    fun `feil fra Dokarkiv gir transientError og ingen eventer`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = event(sak)
        fattet(database, event)

        val result = handler(database, dokarkivStatus = HttpStatusCode.InternalServerError).handle(event)

        assertIs<EventHandledResult.TransientError>(result)
        assertTrue(journalfoert(database).isEmpty())
        assertTrue(sakslogg(database).isEmpty())
    }

    @Test
    fun `journalpost som ikke er ferdigstilt gir transientError og ingen eventer`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = event(sak)
        fattet(database, event)

        assertIs<EventHandledResult.TransientError>(handler(database, ferdigstilt = false).handle(event))
        assertTrue(journalfoert(database).isEmpty())
    }

    @Test
    fun `ny kjøring etter suksess gjør ingen nye kall og publiserer ingen nye eventer`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val sak = tilBeslutning(database)
            val event = event(sak)
            fattet(database, event)
            val dokarkiv = Dokarkiv()
            val gotenberg = Gotenberg()
            val handler = handler(database, dokarkiv, gotenberg)

            assertIs<EventHandledResult.Success>(handler.handle(event))
            assertIs<EventHandledResult.Success>(handler.handle(event))

            assertEquals(1, dokarkiv.requests.size)
            assertEquals(1, gotenberg.html.size)
            assertEquals(1, journalfoert(database).size)
            assertEquals(1, sakslogg(database).size)
        }
}
