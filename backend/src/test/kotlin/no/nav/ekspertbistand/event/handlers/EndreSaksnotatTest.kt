package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SaksnotatTable
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class EndreSaksnotatTest {

    private val opprettet = Instant.parse("2026-04-02T09:00:00Z")
    private val endret = Instant.parse("2026-04-02T10:00:00Z")

    private fun endret(sak: LagretSak, notatId: UUID, ident: String) = EventData.SaksnotatEndret(
        notatId = notatId.toString(),
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        tekst = "Ny tekst",
        endretAvRolle = AktorRolle.SAKSBEHANDLER,
        endretAvIdent = ident,
        tidspunkt = endret,
    )

    @Test
    fun `endrer teksten og publiserer Notat endret`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()
        OpprettSaksnotat(database).handle(
            Event(1L, saksnotatOpprettet(sak, notatId, "Z123456", AktorRolle.SAKSBEHANDLER, "Gammel", opprettet))
        )
        val handler = EndreSaksnotat(database)

        assertIs<EventHandledResult.Success>(handler.handle(Event(2L, endret(sak, notatId, "Z123456"))))
        assertIs<EventHandledResult.Success>(handler.handle(Event(2L, endret(sak, notatId, "Z123456"))))

        val rad = assertNotNull(hentSaksnotat(database, notatId))
        assertEquals("Ny tekst", rad[SaksnotatTable.tekst])
        assertEquals(opprettet, rad[SaksnotatTable.opprettetAt])
        assertEquals(endret, rad[SaksnotatTable.endretAt])

        val innslag = hentSakOppdatert(database)
        assertEquals(listOf("Notat opprettet", "Notat endret"), innslag.map { it.notat })
        assertEquals(AktorRolle.SAKSBEHANDLER, innslag.last().utfortAvRolle)
        assertEquals("Z123456", innslag.last().utfortAvIdent)
        assertEquals(endret, innslag.last().tidspunkt)
    }

    @Test
    fun `endring av en annens notat endrer ingenting og publiserer ikke`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        val notatId = UUID.randomUUID()
        OpprettSaksnotat(database).handle(
            Event(1L, saksnotatOpprettet(sak, notatId, "Z123456", AktorRolle.SAKSBEHANDLER, "Gammel", opprettet))
        )

        assertIs<EventHandledResult.Success>(EndreSaksnotat(database).handle(Event(2L, endret(sak, notatId, "Z999999"))))

        assertEquals("Gammel", hentSaksnotat(database, notatId)?.get(SaksnotatTable.tekst))
        assertEquals(listOf("Notat opprettet"), hentSakOppdatert(database).map { it.notat })
    }

    @Test
    fun `endring av et slettet notat gir success uten innslag`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        val resultat = EndreSaksnotat(database).handle(Event(1L, endret(sak, UUID.randomUUID(), "Z123456")))

        assertIs<EventHandledResult.Success>(resultat)
        assertEquals(emptyList(), hentSakOppdatert(database))
    }
}
