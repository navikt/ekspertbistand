package no.nav.ekspertbistand.event.handlers

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.ekspertbistand.arena.ArenaSakUnderBehandlingTable
import no.nav.ekspertbistand.arena.TiltakssakEndret
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarkerSakUnderBehandlingIArenaTest {

    private fun tiltakssakEndret(sakId: Int = 13769058) = TiltakssakEndret(
        sakId = sakId,
        sakskode = "TILT",
        aar = 2026,
        lopenrsak = 202,
        sakstatuskode = TiltakssakEndret.Sakstatuskode.AKTIV,
        brukeridAnsvarlig = "K123456",
        aetatenhetAnsvarlig = "1899",
    )

    private fun insertSoknad(): DTO.Soknad = transaction {
        SoknadTable.insertReturning {
            it[id] = UUID.randomUUID()
            it[virksomhetsnummer] = "1337"
            it[virksomhetsnavn] = "foo bar AS"
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
            it[ansattFnr] = ""
            it[ansattNavn] = ""
            it[ekspertNavn] = ""
            it[ekspertVirksomhet] = ""
            it[ekspertKompetanse] = ""
            it[navKontaktPerson] = ""
            it[beliggenhetsadresse] = ""
            it[status] = SoknadStatus.innsendt.toString()
        }.single().tilSoknadDTO()
    }

    @Test
    fun `16 - happy path skriver rad i arena_sak_under_behandling`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val handler = MarkerSakUnderBehandlingIArena(database, SimpleMeterRegistry())
        val soknad = transaction(database) { insertSoknad() }

        val result = handler.handle(
            Event(
                id = 1L,
                data = EventData.SaksbehandlingStartetIArena(soknad, tiltakssakEndret())
            )
        )

        assertIs<EventHandledResult.Success>(result)
        transaction(database) {
            ArenaSakUnderBehandlingTable.selectAll().single().let { rad ->
                assertEquals(13769058, rad[ArenaSakUnderBehandlingTable.sakId])
                assertEquals("2026202", rad[ArenaSakUnderBehandlingTable.saksnummer])
                assertEquals(UUID.fromString(soknad.id), rad[ArenaSakUnderBehandlingTable.soknadId])
                assertEquals("K123456", rad[ArenaSakUnderBehandlingTable.brukeridAnsvarlig])
                assertEquals("1899", rad[ArenaSakUnderBehandlingTable.aetatenhetAnsvarlig])
                assertEquals("AKTIV", rad[ArenaSakUnderBehandlingTable.sakstatuskode])
            }
        }
    }

    @Test
    fun `17 - to kall for samme sakId gir success og én rad`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val handler = MarkerSakUnderBehandlingIArena(database, SimpleMeterRegistry())
        val soknad = transaction(database) { insertSoknad() }
        val event = Event(id = 1L, data = EventData.SaksbehandlingStartetIArena(soknad, tiltakssakEndret()))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            assertEquals(1, ArenaSakUnderBehandlingTable.selectAll().count())
        }
    }

    @Test
    fun `18 - soknad uten id gir unrecoverableError`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val handler = MarkerSakUnderBehandlingIArena(database, SimpleMeterRegistry())
        val soknad = transaction(database) { insertSoknad() }.copy(id = null)

        val result = handler.handle(
            Event(id = 1L, data = EventData.SaksbehandlingStartetIArena(soknad, tiltakssakEndret()))
        )

        assertIs<EventHandledResult.UnrecoverableError>(result)
        transaction(database) {
            assertEquals(0, ArenaSakUnderBehandlingTable.selectAll().count())
        }
    }

    @Test
    fun `19 - soknadens status er uendret etter håndtering`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val handler = MarkerSakUnderBehandlingIArena(database, SimpleMeterRegistry())
        val soknad = transaction(database) { insertSoknad() }

        assertIs<EventHandledResult.Success>(
            handler.handle(Event(id = 1L, data = EventData.SaksbehandlingStartetIArena(soknad, tiltakssakEndret())))
        )

        transaction(database) {
            assertEquals(
                SoknadStatus.innsendt,
                SoknadStatus.valueOf(SoknadTable.selectAll().single()[SoknadTable.status])
            )
        }
    }
}
