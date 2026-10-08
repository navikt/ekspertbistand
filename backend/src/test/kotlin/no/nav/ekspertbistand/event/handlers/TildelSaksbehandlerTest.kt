package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.QueuedEvent.Companion.tilQueuedEvent
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TildelSaksbehandlerTest {

    private val tidspunkt = Instant.parse("2026-04-02T08:00:00Z")

    private fun tildelt(sak: LagretSak, ident: String = "Z123456", navn: String = "Tore Tang") =
        EventData.SakTildeltSaksbehandler(
            sakId = sak.sakId.toString(),
            soknadId = sak.soknadId.toString(),
            saksbehandlerIdent = ident,
            saksbehandlerNavn = navn,
            tidspunkt = tidspunkt,
        )

    @Test
    fun `tildeling setter ident, navn og UNDER_BEHANDLING og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        val resultat = TildelSaksbehandler(database).handle(Event(10L, tildelt(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSak(database, sak)
        assertEquals("Z123456", rad[SakTable.saksbehandlerIdent])
        assertEquals("Tore Tang", rad[SakTable.saksbehandlerNavn])
        assertEquals(Saksstatus.UNDER_BEHANDLING.name, rad[SakTable.status])
        assertEquals(10L, rad[SakTable.tildelingEventId])

        val sakOppdatert = hentSakOppdatert(database).single()
        assertEquals(sak.sakId.toString(), sakOppdatert.sakId)
        assertEquals(sak.soknadId.toString(), sakOppdatert.soknadId)
        assertEquals(AktorRolle.SAKSBEHANDLER, sakOppdatert.utfortAvRolle)
        assertEquals("Z123456", sakOppdatert.utfortAvIdent)
        assertEquals("Sak tildelt", sakOppdatert.notat)
        assertEquals(tidspunkt, sakOppdatert.tidspunkt)
    }

    @Test
    fun `tildeling endrer ikke status TIL_BESLUTNING eller INNVILGET`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        listOf(Saksstatus.TIL_BESLUTNING, Saksstatus.INNVILGET).forEachIndexed { i, status ->
            val sak = lagreSoknadOgSak(database)
            settStatus(database, sak, status)

            assertIs<EventHandledResult.Success>(TildelSaksbehandler(database).handle(Event(i + 1L, tildelt(sak))))

            assertEquals(status.name, hentSak(database, sak)[SakTable.status])
            assertEquals("Z123456", hentSak(database, sak)[SakTable.saksbehandlerIdent])
        }
    }

    @Test
    fun `to tildelinger etter hverandre - den siste vinner, også for navnet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val handler = TildelSaksbehandler(database)

        handler.handle(Event(1L, tildelt(sak, "Z111111", "Første Saksbehandler")))
        handler.handle(Event(2L, tildelt(sak, "Z222222", "Andre Saksbehandler")))

        val rad = hentSak(database, sak)
        assertEquals("Z222222", rad[SakTable.saksbehandlerIdent])
        assertEquals("Andre Saksbehandler", rad[SakTable.saksbehandlerNavn])
        assertEquals(2, hentSakOppdatert(database).size)
    }

    @Test
    fun `samme event to ganger gir en SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val handler = TildelSaksbehandler(database)
        val event = Event(1L, tildelt(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(1, hentSakOppdatert(database).size)
    }

    @Test
    fun `eldre event etter en nyere endrer ikke sak, men publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val handler = TildelSaksbehandler(database)

        handler.handle(Event(2L, tildelt(sak, "Z222222", "Andre Saksbehandler")))
        assertIs<EventHandledResult.Success>(handler.handle(Event(1L, tildelt(sak, "Z111111", "Første Saksbehandler"))))

        val rad = hentSak(database, sak)
        assertEquals("Z222222", rad[SakTable.saksbehandlerIdent])
        assertEquals("Andre Saksbehandler", rad[SakTable.saksbehandlerNavn])
        assertEquals(2L, rad[SakTable.tildelingEventId])
        assertEquals(listOf("Z222222", "Z111111"), hentSakOppdatert(database).map { it.utfortAvIdent })
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())

        val resultat = TildelSaksbehandler(database).handle(Event(1L, tildelt(sak)))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
        assertEquals(0, hentSakOppdatert(database).size)
    }

    @Test
    fun `tildeling til beslutter på saken setter ident og navn`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        transaction(database) {
            SakTable.update({ SakTable.sakId eq sak.sakId }) { it[beslutterIdent] = "Z123456" }
        }

        assertIs<EventHandledResult.Success>(TildelSaksbehandler(database).handle(Event(1L, tildelt(sak))))

        val rad = hentSak(database, sak)
        assertEquals("Z123456", rad[SakTable.saksbehandlerIdent])
        assertEquals("Z123456", rad[SakTable.beslutterIdent])
        assertEquals("Tore Tang", rad[SakTable.saksbehandlerNavn])
    }

    @Test
    fun `CHECK avviser ident uten navn og navn uten ident`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        assertThrows<Exception> {
            transaction(database) {
                SakTable.update({ SakTable.sakId eq sak.sakId }) { it[saksbehandlerIdent] = "Z123456" }
            }
        }
        assertThrows<Exception> {
            transaction(database) {
                SakTable.update({ SakTable.sakId eq sak.sakId }) { it[saksbehandlerNavn] = "Tore Tang" }
            }
        }
        assertNull(hentSak(database, sak)[SakTable.saksbehandlerIdent])
    }

    @Test
    fun `SakTildeltSaksbehandler avviser blankt navn`() {
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())
        assertThrows<IllegalArgumentException> { tildelt(sak, navn = " ") }
    }
}

internal fun hentSak(database: Database, sak: LagretSak): ResultRow = transaction(database) {
    SakTable.selectAll().where { SakTable.sakId eq sak.sakId }.single()
}

internal fun settStatus(database: Database, sak: LagretSak, status: Saksstatus) = transaction(database) {
    SakTable.update({ SakTable.sakId eq sak.sakId }) { it[SakTable.status] = status.name }
}

internal fun hentSakOppdatert(database: Database): List<EventData.SakOppdatert> = transaction(database) {
    QueuedEvents.selectAll()
        .orderBy(QueuedEvents.id)
        .map { it.tilQueuedEvent().eventData }
        .filterIsInstance<EventData.SakOppdatert>()
}
