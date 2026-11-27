package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SAKSNOTAT_MAKS_LENGDE
import no.nav.ekspertbistand.saksbehandling.SaksnotatTable
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class OpprettSaksnotatTest {

    private val tidspunkt = Instant.parse("2026-04-02T09:00:00Z")

    @Test
    fun `lagrer notatet og publiserer Notat opprettet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()

        val resultat = OpprettSaksnotat(database).handle(
            Event(1L, saksnotatOpprettet(sak, notatId, "Z654321", AktorRolle.BESLUTTER, "Ringte arbeidsgiver", tidspunkt))
        )

        assertIs<EventHandledResult.Success>(resultat)
        val rad = assertNotNull(hentSaksnotat(database, notatId))
        assertEquals(sak.sakId, rad[SaksnotatTable.sakId])
        assertEquals("Ringte arbeidsgiver", rad[SaksnotatTable.tekst])
        assertEquals("Z654321", rad[SaksnotatTable.skrevetAvIdent])
        assertEquals("Navn Z654321", rad[SaksnotatTable.skrevetAvNavn])
        assertEquals(AktorRolle.BESLUTTER.name, rad[SaksnotatTable.skrevetAvRolle])
        assertEquals(tidspunkt, rad[SaksnotatTable.opprettetAt])
        assertNull(rad[SaksnotatTable.endretAt])

        val innslag = hentSakOppdatert(database).single()
        assertEquals(sak.sakId.toString(), innslag.sakId)
        assertEquals(AktorRolle.BESLUTTER, innslag.utfortAvRolle)
        assertEquals("Z654321", innslag.utfortAvIdent)
        assertEquals("Notat opprettet", innslag.notat)
        assertEquals(tidspunkt, innslag.tidspunkt)
    }

    @Test
    fun `samme notat to ganger gir én rad og ett innslag`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()
        val data = saksnotatOpprettet(sak, notatId, "Z123456", AktorRolle.SAKSBEHANDLER, "Første", tidspunkt)
        val handler = OpprettSaksnotat(database)

        assertIs<EventHandledResult.Success>(handler.handle(Event(1L, data)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(1L, data)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(2L, data.copy(tekst = "Andre"))))

        assertEquals("Første", hentSaksnotat(database, notatId)?.get(SaksnotatTable.tekst))
        assertEquals(listOf("Notat opprettet"), hentSakOppdatert(database).map { it.notat })
    }

    @Test
    fun `eventene avviser blank tekst, for lang tekst og SYSTEM`() {
        val notatId = UUID.randomUUID().toString()
        val sakId = UUID.randomUUID().toString()
        val soknadId = UUID.randomUUID().toString()
        fun opprettet(tekst: String, rolle: AktorRolle) = EventData.SaksnotatOpprettet(
            notatId, sakId, soknadId, tekst, rolle, "Z123456", "Navn", tidspunkt,
        )
        fun endret(tekst: String, rolle: AktorRolle) = EventData.SaksnotatEndret(
            notatId, sakId, soknadId, tekst, rolle, "Z123456", tidspunkt,
        )

        opprettet("x".repeat(SAKSNOTAT_MAKS_LENGDE), AktorRolle.SAKSBEHANDLER)
        assertThrows<IllegalArgumentException> { opprettet(" ", AktorRolle.SAKSBEHANDLER) }
        assertThrows<IllegalArgumentException> { opprettet("x".repeat(SAKSNOTAT_MAKS_LENGDE + 1), AktorRolle.SAKSBEHANDLER) }
        assertThrows<IllegalArgumentException> { opprettet("tekst", AktorRolle.SYSTEM) }
        assertThrows<IllegalArgumentException> { endret(" ", AktorRolle.BESLUTTER) }
        assertThrows<IllegalArgumentException> { endret("x".repeat(SAKSNOTAT_MAKS_LENGDE + 1), AktorRolle.BESLUTTER) }
        assertThrows<IllegalArgumentException> { endret("tekst", AktorRolle.SYSTEM) }
        assertThrows<IllegalArgumentException> {
            EventData.SaksnotatSlettet(notatId, sakId, soknadId, AktorRolle.SYSTEM, "Z123456", tidspunkt)
        }
    }
}
