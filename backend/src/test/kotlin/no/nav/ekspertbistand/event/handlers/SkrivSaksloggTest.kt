package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SaksloggTable
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SkrivSaksloggTest {

    private val tidspunkt = Instant.parse("2026-04-02T08:00:00Z")

    private fun sakOppdatert(
        sakId: UUID,
        soknadId: UUID,
        rolle: AktorRolle = AktorRolle.SAKSBEHANDLER,
        ident: String? = "Z123456",
    ) = EventData.SakOppdatert(
        sakId = sakId.toString(),
        soknadId = soknadId.toString(),
        utfortAvRolle = rolle,
        utfortAvIdent = ident,
        notat = "Sak tildelt",
        tidspunkt = tidspunkt,
    )

    @Test
    fun `saksbehandler-event gir rad med rolle, ident, notat og tidspunkt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        val resultat = SkrivSakslogg(database).handle(Event(1L, sakOppdatert(sak.sakId, sak.soknadId)))

        assertIs<EventHandledResult.Success>(resultat)
        transaction(database) {
            val rad = SaksloggTable.selectAll().single()
            assertEquals(sak.sakId, rad[SaksloggTable.sakId])
            assertEquals(AktorRolle.SAKSBEHANDLER.name, rad[SaksloggTable.utfortAvRolle])
            assertEquals("Z123456", rad[SaksloggTable.utfortAvIdent])
            assertEquals("Sak tildelt", rad[SaksloggTable.notat])
            assertEquals(tidspunkt, rad[SaksloggTable.utfortAt])
        }
    }

    @Test
    fun `system-event gir rad med rolle SYSTEM og uten ident`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        val event = sakOppdatert(sak.sakId, sak.soknadId, rolle = AktorRolle.SYSTEM, ident = null)
        assertIs<EventHandledResult.Success>(SkrivSakslogg(database).handle(Event(1L, event)))

        transaction(database) {
            val rad = SaksloggTable.selectAll().single()
            assertEquals(AktorRolle.SYSTEM.name, rad[SaksloggTable.utfortAvRolle])
            assertNull(rad[SaksloggTable.utfortAvIdent])
        }
    }

    @Test
    fun `samme event to ganger gir en rad`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val handler = SkrivSakslogg(database)
        val event = Event(1L, sakOppdatert(sak.sakId, sak.soknadId))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        transaction(database) {
            assertEquals(1, SaksloggTable.selectAll().count())
        }
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase

        val event = sakOppdatert(UUID.randomUUID(), UUID.randomUUID())
        val resultat = SkrivSakslogg(database).handle(Event(1L, event))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
        transaction(database) {
            assertEquals(0, SaksloggTable.selectAll().count())
        }
    }

    @Test
    fun `SakOppdatert avviser ugyldig kombinasjon av rolle og ident`() {
        val sakId = UUID.randomUUID()
        val soknadId = UUID.randomUUID()
        assertThrows<IllegalArgumentException> { sakOppdatert(sakId, soknadId, AktorRolle.SAKSBEHANDLER, null) }
        assertThrows<IllegalArgumentException> { sakOppdatert(sakId, soknadId, AktorRolle.BESLUTTER, " ") }
        assertThrows<IllegalArgumentException> { sakOppdatert(sakId, soknadId, AktorRolle.SYSTEM, "Z123456") }
    }
}
