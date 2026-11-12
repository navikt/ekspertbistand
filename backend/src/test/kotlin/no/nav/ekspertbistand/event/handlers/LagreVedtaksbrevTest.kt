package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.nesteSaksnummer
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.vedtaksbrev.VedtaksbrevTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class LagreVedtaksbrevTest {

    @Test
    fun `lagrer vedtaksbrevet fra eventen`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        val data = TestEventData.vedtaksbrevArbeidsgiverJournalfoert.copy(sakId = settInnSak(database).toString())

        assertIs<EventHandledResult.Success>(LagreVedtaksbrev(database).handle(Event(id = 7, data = data)))

        transaction(database) {
            val rad = VedtaksbrevTable.selectAll().single()
            assertEquals(UUID.fromString(data.sakId), rad[VedtaksbrevTable.sakId])
            assertEquals(data.utfall.name, rad[VedtaksbrevTable.utfall])
            assertEquals(data.vedtaksbrev, rad[VedtaksbrevTable.brevdata])
            assertEquals(data.tidspunkt, rad[VedtaksbrevTable.journalfoertTidspunkt])
            assertEquals(7, rad[VedtaksbrevTable.eventId])
        }
    }

    @Test
    fun `samme event to ganger gir en rad`() = testApplicationWithDatabase { testDb ->
        val database = testDb.config.jdbcDatabase
        val data = TestEventData.vedtaksbrevArbeidsgiverJournalfoert.copy(sakId = settInnSak(database).toString())
        val handler = LagreVedtaksbrev(database)

        assertIs<EventHandledResult.Success>(handler.handle(Event(id = 7, data = data)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(id = 7, data = data)))

        transaction(database) {
            assertEquals(1, VedtaksbrevTable.selectAll().count())
        }
    }

    private fun settInnSak(database: Database): UUID = transaction(database) {
        val soknadId = UUID.randomUUID()
        SoknadTable.insert {
            it[id] = soknadId
            it[virksomhetsnavn] = "Testbedrift AS"
            it[virksomhetsnummer] = "987654321"
            it[opprettetAv] = "42"
            it[kontaktpersonNavn] = ""
            it[kontaktpersonEpost] = ""
            it[kontaktpersonTelefon] = ""
            it[ansattFnr] = ""
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[behovForBistand] = ""
            it[behovForBistandBegrunnelse] = ""
            it[behovForBistandTilrettelegging] = ""
            it[behovForBistandEstimertKostnad] = ""
            it[behovForBistandTimer] = ""
            it[behovForBistandStartdato] = CurrentDate
            it[navKontaktPerson] = ""
            it[beliggenhetsadresse] = ""
            it[status] = "godkjent"
        }
        SakTable.insertReturning {
            it[SakTable.soknadId] = soknadId
            it[status] = Saksstatus.INNVILGET.name
            it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
            it[saksnummer] = nesteSaksnummer()
        }.single()[SakTable.sakId]
    }
}
