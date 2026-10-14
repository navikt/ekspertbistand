package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ReturArsak
import no.nav.ekspertbistand.saksbehandling.SakReturTable
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.hentSisteRetur
import no.nav.ekspertbistand.saksbehandling.hentVedtak
import no.nav.ekspertbistand.sak.LagretSak
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ReturnerForelopigVedtakTest {

    private val tidspunkt = Instant.parse("2026-11-24T09:00:00Z")

    private fun retur(
        sak: LagretSak,
        versjon: Int = 1,
        aarsaker: Set<ReturArsak> = setOf(ReturArsak.FEIL_FAKTA, ReturArsak.ANNET),
        tidspunkt: Instant = this.tidspunkt,
    ) = EventData.ForelopigVedtakReturnert(
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        forelopigVedtakVersjon = versjon,
        aarsaker = aarsaker,
        forklaring = "Sjekk perioden",
        returnertAvIdent = BESLUTTER_IDENT,
        returnertAvNavn = "Beslutter Navn",
        tidspunkt = tidspunkt,
    )

    private fun hentReturer(database: Database, sak: LagretSak): List<ResultRow> = transaction(database) {
        SakReturTable.selectAll().where { SakReturTable.sakId eq sak.sakId }.toList()
    }

    private suspend fun tilBeslutningMedSaksbehandler(database: Database, eventId: Long = 100L): LagretSak =
        tilBeslutning(database, eventId = eventId).also { sak ->
            transaction(database) {
                SakTable.update({ SakTable.sakId eq sak.sakId }) {
                    it[saksbehandlerIdent] = SAKSBEHANDLER_IDENT
                    it[saksbehandlerNavn] = "Saksbehandler Navn"
                    it[beslutterIdent] = BESLUTTER_IDENT
                    it[beslutterNavn] = "Beslutter Navn"
                }
            }
        }

    @Test
    fun `lagrer returen, setter UNDER_BEHANDLING og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database)
        val sakslogg = hentSakOppdatert(database).size

        val resultat = ReturnerForelopigVedtak(database).handle(Event(1L, retur(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val sakRad = hentSak(database, sak)
        assertEquals(Saksstatus.UNDER_BEHANDLING.name, sakRad[SakTable.status])
        assertNull(sakRad[SakTable.foreslattUtfall])
        assertEquals(SAKSBEHANDLER_IDENT, sakRad[SakTable.saksbehandlerIdent])
        assertEquals(BESLUTTER_IDENT, sakRad[SakTable.beslutterIdent])

        val rad = hentReturer(database, sak).single()
        assertEquals(listOf("FEIL_FAKTA", "ANNET"), rad[SakReturTable.aarsaker])
        assertEquals("Sjekk perioden", rad[SakReturTable.forklaring])
        assertEquals(BESLUTTER_IDENT, rad[SakReturTable.returnertAvIdent])
        assertEquals(SAKSBEHANDLER_IDENT, rad[SakReturTable.tilSaksbehandlerIdent])
        assertEquals(1L, rad[SakReturTable.eventId])
        assertEquals(tidspunkt, rad[SakReturTable.opprettet])

        val logg = hentSakOppdatert(database).drop(sakslogg).single()
        assertEquals(AktorRolle.BESLUTTER, logg.utfortAvRolle)
        assertEquals(BESLUTTER_IDENT, logg.utfortAvIdent)
        assertEquals("Sendt i retur til saksbehandler", logg.notat)
    }

    @Test
    fun `samme event to ganger gir én retur og én SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database)
        val sakslogg = hentSakOppdatert(database).size
        val handler = ReturnerForelopigVedtak(database)
        val event = Event(1L, retur(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(1, hentReturer(database, sak).size)
        assertEquals(sakslogg + 1, hentSakOppdatert(database).size)
    }

    @Test
    fun `utdatert versjon gir ingen endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database)

        assertIs<EventHandledResult.Success>(ReturnerForelopigVedtak(database).handle(Event(1L, retur(sak, versjon = 2))))

        assertEquals(Saksstatus.TIL_BESLUTNING.name, hentSak(database, sak)[SakTable.status])
        assertTrue(hentReturer(database, sak).isEmpty())
    }

    @Test
    fun `beslutter som også innstilte kan returnere`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database)

        val event = retur(sak).copy(returnertAvIdent = SAKSBEHANDLER_IDENT)
        assertIs<EventHandledResult.Success>(ReturnerForelopigVedtak(database).handle(Event(1L, event)))

        assertEquals(1, hentReturer(database, sak).size)
    }

    @Test
    fun `ny innsending og ny retur gir siste retur på versjon 2`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database, eventId = 100L)
        val handler = ReturnerForelopigVedtak(database)

        handler.handle(Event(1L, retur(sak)))
        tilBeslutning(database, ForelopigUtfall.AVSLAA, sak, eventId = 101L)
        handler.handle(
            Event(2L, retur(sak, versjon = 2, aarsaker = setOf(ReturArsak.FEIL_REGELFORSTAELSE), tidspunkt = tidspunkt.plus(1.hours)))
        )

        assertEquals(2, hentReturer(database, sak).size)
        val siste = transaction(database) { hentSisteRetur(sak.sakId) }!!
        assertEquals(2, siste.forelopigVedtakVersjon)
        assertEquals(listOf(ReturArsak.FEIL_REGELFORSTAELSE), siste.aarsaker)
    }

    @Test
    fun `retur, ny innsending og vedtak gir vedtak på versjon 2`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutningMedSaksbehandler(database, eventId = 100L)

        ReturnerForelopigVedtak(database).handle(Event(1L, retur(sak)))
        tilBeslutning(database, ForelopigUtfall.AVSLAA, sak, eventId = 101L)
        FattVedtak(database).handle(Event(2L, vedtakFattet(sak, Saksstatus.AVSLATT, versjon = 2)))

        assertEquals(Saksstatus.AVSLATT.name, hentSak(database, sak)[SakTable.status])
        val vedtak = transaction(database) { hentVedtak(sak.sakId) }!!
        assertEquals(2, vedtak.forelopigVedtakVersjon)
        assertEquals(Saksstatus.AVSLATT, vedtak.utfall)
    }
}
