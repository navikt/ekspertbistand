package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.slettSøknadOm
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SettSoknadStatusVedtakFattetTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@SettSoknadStatusVedtakFattetTest.now
    }

    private fun hentSoknad(database: Database, sak: LagretSak): ResultRow = transaction(database) {
        SoknadTable.selectAll().where { SoknadTable.id eq sak.soknadId }.single()
    }

    private fun soknadStatus(database: Database, sak: LagretSak) =
        SoknadStatus.valueOf(hentSoknad(database, sak)[SoknadTable.status])

    @Test
    fun `innvilget vedtak setter søknaden til godkjent`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        val soknad = hentSoknad(database, sak)
        assertEquals(SoknadStatus.godkjent.name, soknad[SoknadTable.status])
        assertEquals(now.plus(slettSøknadOm), soknad[SoknadTable.sletteTidspunkt])
    }

    @Test
    fun `avslått vedtak setter søknaden til avslatt`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database, ForelopigUtfall.AVSLAA)
        val event = Event(1L, vedtakFattet(sak, Saksstatus.AVSLATT))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        assertEquals(SoknadStatus.avslatt, soknadStatus(database, sak))
    }

    @Test
    fun `vedtaket er ikke lagret ennå gir transientError, så godkjent etter FattVedtak`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        val handler = SettSoknadStatusVedtakFattet(database, clock)

        assertIs<EventHandledResult.TransientError>(handler.handle(event))
        assertEquals(SoknadStatus.innsendt, soknadStatus(database, sak))

        FattVedtak(database).handle(event)
        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertEquals(SoknadStatus.godkjent, soknadStatus(database, sak))
    }

    @Test
    fun `inhabil beslutter gir success uten endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak, fattetAv = SAKSBEHANDLER_IDENT))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        assertEquals(SoknadStatus.innsendt, soknadStatus(database, sak))
    }

    @Test
    fun `vedtak etter retur gir success uten endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        assertEquals(SoknadStatus.innsendt, soknadStatus(database, sak))
    }

    @Test
    fun `utdatert versjon gir success uten endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak, versjon = 2))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        assertEquals(SoknadStatus.innsendt, soknadStatus(database, sak))
    }

    @Test
    fun `søknad som ikke er innsendt endres ikke`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)
        transaction(database) {
            SoknadTable.update({ SoknadTable.id eq sak.soknadId }) { it[status] = SoknadStatus.avlyst.name }
        }

        assertIs<EventHandledResult.Success>(SettSoknadStatusVedtakFattet(database, clock).handle(event))

        assertEquals(SoknadStatus.avlyst, soknadStatus(database, sak))
    }
}
