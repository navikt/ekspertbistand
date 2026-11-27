package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SlettSaksnotatTest {

    private val opprettet = Instant.parse("2026-04-02T09:00:00Z")
    private val slettet = Instant.parse("2026-04-02T10:00:00Z")

    private fun slettet(sak: LagretSak, notatId: UUID, ident: String) = EventData.SaksnotatSlettet(
        notatId = notatId.toString(),
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        slettetAvRolle = AktorRolle.BESLUTTER,
        slettetAvIdent = ident,
        tidspunkt = slettet,
    )

    @Test
    fun `sletter raden og publiserer Notat slettet én gang`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()
        OpprettSaksnotat(database).handle(
            Event(1L, saksnotatOpprettet(sak, notatId, "Z654321", AktorRolle.BESLUTTER, "Notat", opprettet))
        )
        val handler = SlettSaksnotat(database)

        assertIs<EventHandledResult.Success>(handler.handle(Event(2L, slettet(sak, notatId, "Z654321"))))
        assertIs<EventHandledResult.Success>(handler.handle(Event(3L, slettet(sak, notatId, "Z654321"))))

        assertNull(hentSaksnotat(database, notatId))
        val innslag = hentSakOppdatert(database)
        assertEquals(listOf("Notat opprettet", "Notat slettet"), innslag.map { it.notat })
        assertEquals(AktorRolle.BESLUTTER, innslag.last().utfortAvRolle)
        assertEquals("Z654321", innslag.last().utfortAvIdent)
        assertEquals(slettet, innslag.last().tidspunkt)
    }

    @Test
    fun `sletting av en annens notat beholder raden og publiserer ikke`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()
        OpprettSaksnotat(database).handle(
            Event(1L, saksnotatOpprettet(sak, notatId, "Z123456", AktorRolle.SAKSBEHANDLER, "Notat", opprettet))
        )

        assertIs<EventHandledResult.Success>(SlettSaksnotat(database).handle(Event(2L, slettet(sak, notatId, "Z999999"))))

        assertNotNull(hentSaksnotat(database, notatId))
        assertEquals(listOf("Notat opprettet"), hentSakOppdatert(database).map { it.notat })
    }
}
