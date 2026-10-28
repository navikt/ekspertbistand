package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.pdl.GraderingOppslag
import no.nav.ekspertbistand.saksbehandling.*
import no.nav.ekspertbistand.skjerming.SkjermingOppslag
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.*

class OpprettSakTest {

    @Test
    fun `oppretter sak med behandlende enhet og vilkår, og publiserer SakOppdatert og SakOpprettet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)

        val result = opprettSak(database).handle(journalfoert(soknad, behandlendeEnhetId = "4242", kommunenummer = "4601"))

        assertIs<EventHandledResult.Success>(result)
        val sak = hentSak(database, soknad)
        assertEquals("4242", sak.behandlendeEnhet)
        assertEquals("4601", sak.kommunenummer)
        assertNull(sak.adressebeskyttelse)
        assertFalse(sak.skjermet)
        assertEquals(Saksstatus.OPPRETTET.name, sak.status)
        assertEquals(KildeTilBehandling.EKSPERTBISTAND.name, sak.kilde)
        assertEquals(Vilkar.entries.size, antallVilkar(database, sak.sakId))

        val events = hentEvents(database)
        assertEquals(2, events.size)

        val opprettet = events.filterIsInstance<EventData.SakOpprettet>().single()
        assertEquals(sak.sakId.toString(), opprettet.sakId)
        assertEquals(soknad, opprettet.soknad)
        assertNull(opprettet.adressebeskyttelse)
        assertFalse(opprettet.skjermet)

        val oppdatert = events.filterIsInstance<EventData.SakOppdatert>().single()
        assertEquals(sak.sakId.toString(), oppdatert.sakId)
        assertEquals(soknad.id, oppdatert.soknadId)
        assertEquals(AktorRolle.SYSTEM, oppdatert.utfortAvRolle)
        assertNull(oppdatert.utfortAvIdent)
        assertEquals("Sak opprettet", oppdatert.notat)
    }

    @Test
    fun `retry oppretter ikke ny sak eller nye events`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)
        val handler = opprettSak(database)
        val event = journalfoert(soknad, behandlendeEnhetId = "4242")

        repeat(2) { assertIs<EventHandledResult.Success>(handler.handle(event)) }

        transaction(database) { assertEquals(1, SakTable.selectAll().count()) }
        assertEquals(2, hentEvents(database).size)
    }

    @Test
    fun `søknad som ikke finnes gir unrecoverable error uten sak eller events`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database).copy(id = UUID.randomUUID().toString())

        val result = opprettSak(database).handle(journalfoert(soknad, behandlendeEnhetId = "4242"))

        assertIs<EventHandledResult.UnrecoverableError>(result)
        transaction(database) { assertEquals(0, SakTable.selectAll().count()) }
        assertTrue(hentEvents(database).isEmpty())
    }

    @Test
    fun `lagrer adressebeskyttelse og skjerming på saken og SakOpprettet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)
        val handler = opprettSak(
            database,
            graderingOppslag = { fnr -> Adressebeskyttelse.FORTROLIG.also { assertEquals(soknad.ansatt.fnr, fnr) } },
            skjermingOppslag = { fnr -> true.also { assertEquals(soknad.ansatt.fnr, fnr) } },
        )

        assertIs<EventHandledResult.Success>(handler.handle(journalfoert(soknad, behandlendeEnhetId = "4242")))

        val sak = hentSak(database, soknad)
        assertEquals(Adressebeskyttelse.FORTROLIG.name, sak.adressebeskyttelse)
        assertTrue(sak.skjermet)
        val opprettet = hentEvents(database).filterIsInstance<EventData.SakOpprettet>().single()
        assertEquals(Adressebeskyttelse.FORTROLIG, opprettet.adressebeskyttelse)
        assertTrue(opprettet.skjermet)
    }

    @Test
    fun `feil i oppslag gir transient error uten sak eller events`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)
        val event = journalfoert(soknad, behandlendeEnhetId = "4242")

        listOf(
            opprettSak(database, graderingOppslag = { throw RuntimeException("PDL er nede") }),
            opprettSak(database, skjermingOppslag = { throw RuntimeException("pip er nede") }),
        ).forEach { handler ->
            assertIs<EventHandledResult.TransientError>(handler.handle(event))
        }

        transaction(database) { assertEquals(0, SakTable.selectAll().count()) }
        assertTrue(hentEvents(database).isEmpty())
    }

    private fun opprettSak(
        database: Database,
        graderingOppslag: GraderingOppslag = GraderingOppslag { null },
        skjermingOppslag: SkjermingOppslag = SkjermingOppslag { false },
    ) = OpprettSak(database, graderingOppslag, skjermingOppslag)

    private data class LagretSak(
        val sakId: UUID,
        val status: String,
        val kilde: String,
        val behandlendeEnhet: String?,
        val kommunenummer: String?,
        val adressebeskyttelse: String?,
        val skjermet: Boolean,
    )

    private fun journalfoert(soknad: DTO.Soknad, behandlendeEnhetId: String, kommunenummer: String? = null) = Event(
        id = 1L,
        data = EventData.InnsendtSoknadJournalfoert(
            soknad = soknad,
            dokumentId = 9876,
            journaldpostId = 1234,
            behandlendeEnhetId = behandlendeEnhetId,
            virksomhetKommunenummer = kommunenummer,
        ),
    )

    private fun hentSak(database: Database, soknad: DTO.Soknad): LagretSak = transaction(database) {
        SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(soknad.id) }.single().let {
            LagretSak(
                sakId = it[SakTable.sakId],
                status = it[SakTable.status],
                kilde = it[SakTable.kildeTilBehandling],
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
                kommunenummer = it[SakTable.kommunenummer],
                adressebeskyttelse = it[SakTable.adressebeskyttelse],
                skjermet = it[SakTable.skjermet],
            )
        }
    }

    private fun antallVilkar(database: Database, sakId: UUID): Int = transaction(database) {
        SaksvilkarTable.selectAll().where { SaksvilkarTable.sakId eq sakId }
            .onEach { assertNull(it[SaksvilkarTable.godkjent]) }
            .count().toInt()
    }

    private fun hentEvents(database: Database): List<EventData> = transaction(database) {
        QueuedEvents.selectAll().map { it[QueuedEvents.eventData] }
    }

    private fun lagreSoknad(database: Database): DTO.Soknad = transaction(database) {
        SoknadTable.insertReturning {
            it[id] = UUID.randomUUID()
            it[virksomhetsnummer] = "987654321"
            it[virksomhetsnavn] = "Testbedrift AS"
            it[opprettetAv] = "42"
            it[behovForBistand] = "innsendt soknad"
            it[behovForBistandTilrettelegging] = ""
            it[behovForBistandBegrunnelse] = ""
            it[behovForBistandEstimertKostnad] = "42"
            it[behovForBistandTimer] = "9"
            it[behovForBistandStartdato] = CurrentDate
            it[kontaktpersonNavn] = ""
            it[kontaktpersonEpost] = ""
            it[kontaktpersonTelefon] = ""
            it[ansattFnr] = "12058512345"
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[navKontaktPerson] = ""
            it[beliggenhetsadresse] = ""
            it[status] = SoknadStatus.innsendt.toString()
        }.single().tilSoknadDTO()
    }
}
