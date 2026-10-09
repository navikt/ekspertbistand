package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FrigjoerSakTest {

    private val tidspunkt = Instant.parse("2026-04-02T09:00:00Z")

    private fun tildelt(sak: LagretSak, ident: String) = EventData.SakTildeltSaksbehandler(
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        saksbehandlerIdent = ident,
        saksbehandlerNavn = "Navn $ident",
        tidspunkt = Instant.parse("2026-04-02T08:00:00Z"),
    )

    private fun frigjort(sak: LagretSak, ident: String = "Z123456") = EventData.SakFrigjort(
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        saksbehandlerIdent = ident,
        tidspunkt = tidspunkt,
    )

    @Test
    fun `frigjøring nuller ident og navn, endrer ikke status og publiserer Sak frigjort`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        TildelSaksbehandler(database).handle(Event(1L, tildelt(sak, "Z123456")))

        val resultat = FrigjoerSak(database).handle(Event(2L, frigjort(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSak(database, sak)
        assertNull(rad[SakTable.saksbehandlerIdent])
        assertNull(rad[SakTable.saksbehandlerNavn])
        assertEquals(Saksstatus.UNDER_BEHANDLING.name, rad[SakTable.status])
        assertEquals(2L, rad[SakTable.tildelingEventId])

        val sakFrigjort = hentSakOppdatert(database).last()
        assertEquals(sak.sakId.toString(), sakFrigjort.sakId)
        assertEquals(AktorRolle.SAKSBEHANDLER, sakFrigjort.utfortAvRolle)
        assertEquals("Z123456", sakFrigjort.utfortAvIdent)
        assertEquals("Sak frigjort", sakFrigjort.notat)
        assertEquals(tidspunkt, sakFrigjort.tidspunkt)
    }

    @Test
    fun `frigjøring når en annen har saken endrer ingenting og publiserer ikke`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        TildelSaksbehandler(database).handle(Event(1L, tildelt(sak, "Z999999")))

        assertIs<EventHandledResult.Success>(FrigjoerSak(database).handle(Event(2L, frigjort(sak, "Z123456"))))

        val rad = hentSak(database, sak)
        assertEquals("Z999999", rad[SakTable.saksbehandlerIdent])
        assertEquals("Navn Z999999", rad[SakTable.saksbehandlerNavn])
        assertEquals(1L, rad[SakTable.tildelingEventId])
        assertEquals(listOf("Sak tildelt"), hentSakOppdatert(database).map { it.notat })
    }

    @Test
    fun `eldre frigjøring etter en nyere tildeling endrer ingenting`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        TildelSaksbehandler(database).handle(Event(5L, tildelt(sak, "Z123456")))

        assertIs<EventHandledResult.Success>(FrigjoerSak(database).handle(Event(3L, frigjort(sak))))

        assertEquals("Z123456", hentSak(database, sak)[SakTable.saksbehandlerIdent])
        assertEquals(listOf("Sak tildelt"), hentSakOppdatert(database).map { it.notat })
    }

    @Test
    fun `samme event to ganger gir en SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        TildelSaksbehandler(database).handle(Event(1L, tildelt(sak, "Z123456")))
        val handler = FrigjoerSak(database)
        val event = Event(2L, frigjort(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(listOf("Sak tildelt", "Sak frigjort"), hentSakOppdatert(database).map { it.notat })
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())

        assertIs<EventHandledResult.UnrecoverableError>(FrigjoerSak(database).handle(Event(1L, frigjort(sak))))
    }
}
