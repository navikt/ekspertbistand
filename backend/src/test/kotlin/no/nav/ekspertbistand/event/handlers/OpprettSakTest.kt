package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.SaksvilkarTable
import no.nav.ekspertbistand.saksbehandling.Vilkar
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpprettSakTest {

    @Test
    fun `oppretter sak med behandlende enhet og vilkår, og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)

        val result = OpprettSak(database).handle(journalfoert(soknad, behandlendeEnhetId = "4242"))

        assertIs<EventHandledResult.Success>(result)
        val sak = hentSak(database, soknad)
        assertEquals("4242", sak.behandlendeEnhet)
        assertEquals(Saksstatus.OPPRETTET.name, sak.status)
        assertEquals(KildeTilBehandling.ARENA.name, sak.kilde)
        assertEquals(Vilkar.entries.size, antallVilkar(database, sak.sakId))

        val oppdatert = hentEvents(database).single()
        assertIs<EventData.SakOppdatert>(oppdatert)
        assertEquals(sak.sakId.toString(), oppdatert.sakId)
        assertEquals(soknad.id, oppdatert.soknadId)
        assertEquals(AktorRolle.SYSTEM, oppdatert.utfortAvRolle)
        assertNull(oppdatert.utfortAvIdent)
        assertEquals("Sak opprettet", oppdatert.notat)
    }

    @Test
    fun `eksisterende sak uten enhet får behandlende enhet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)
        val eksisterendeSakId = transaction(database) {
            SakTable.insert {
                it[soknadId] = UUID.fromString(soknad.id)
                it[status] = Saksstatus.OPPRETTET.name
                it[kildeTilBehandling] = KildeTilBehandling.ARENA.name
            }[SakTable.sakId]
        }

        val result = OpprettSak(database).handle(journalfoert(soknad, behandlendeEnhetId = "4242"))

        assertIs<EventHandledResult.Success>(result)
        val sak = hentSak(database, soknad)
        assertEquals(eksisterendeSakId, sak.sakId)
        assertEquals("4242", sak.behandlendeEnhet)
        assertEquals(Vilkar.entries.size, antallVilkar(database, sak.sakId))
    }

    @Test
    fun `retry oppretter ikke ny sak eller nye events`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database)
        val handler = OpprettSak(database)
        val event = journalfoert(soknad, behandlendeEnhetId = "4242")

        repeat(2) { assertIs<EventHandledResult.Success>(handler.handle(event)) }

        transaction(database) { assertEquals(1, SakTable.selectAll().count()) }
        assertEquals(1, hentEvents(database).size)
    }

    @Test
    fun `søknad som ikke finnes gir unrecoverable error uten sak eller events`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val soknad = lagreSoknad(database).copy(id = UUID.randomUUID().toString())

        val result = OpprettSak(database).handle(journalfoert(soknad, behandlendeEnhetId = "4242"))

        assertIs<EventHandledResult.UnrecoverableError>(result)
        transaction(database) { assertEquals(0, SakTable.selectAll().count()) }
        assertTrue(hentEvents(database).isEmpty())
    }

    private data class LagretSak(val sakId: UUID, val status: String, val kilde: String, val behandlendeEnhet: String?)

    private fun journalfoert(soknad: DTO.Soknad, behandlendeEnhetId: String) = Event(
        id = 1L,
        data = EventData.InnsendtSoknadJournalfoert(
            soknad = soknad,
            dokumentId = 9876,
            journaldpostId = 1234,
            behandlendeEnhetId = behandlendeEnhetId,
        ),
    )

    private fun hentSak(database: Database, soknad: DTO.Soknad): LagretSak = transaction(database) {
        SakTable.selectAll().where { SakTable.soknadId eq UUID.fromString(soknad.id) }.single().let {
            LagretSak(
                sakId = it[SakTable.sakId],
                status = it[SakTable.status],
                kilde = it[SakTable.kildeTilBehandling],
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
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
