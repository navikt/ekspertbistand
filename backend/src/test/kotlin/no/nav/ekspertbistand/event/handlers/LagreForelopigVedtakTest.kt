package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ForelopigVedtakTable
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class LagreForelopigVedtakTest {

    private val tidspunkt = Instant.parse("2026-11-23T08:00:00Z")

    private fun innstilling(sak: LagretSak, utfall: ForelopigUtfall = ForelopigUtfall.INNVILGE) =
        EventData.ForelopigVedtakSendtTilBeslutter(
            sakId = sak.sakId.toString(),
            soknadId = sak.soknadId.toString(),
            utfall = utfall,
            innvilgetBelop = if (utfall == ForelopigUtfall.AVSLAA) null else 9999,
            tiltaksperiodeFra = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 11, 22),
            tiltaksperiodeTil = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 12, 31),
            begrunnelseDeltaker = "Til deltaker",
            begrunnelseArbeidsgiver = if (utfall == ForelopigUtfall.INNVILGE) null else "Til arbeidsgiver",
            innstiltAvIdent = "Z123456",
            tidspunkt = tidspunkt,
        )

    private fun underBehandling(database: Database): LagretSak =
        lagreSoknadOgSak(database).also { settStatus(database, it, Saksstatus.UNDER_BEHANDLING) }

    @Test
    fun `lagrer vedtaket, setter TIL_BESLUTNING og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = underBehandling(database)

        val resultat = LagreForelopigVedtak(database).handle(Event(10L, innstilling(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSak(database, sak)
        assertEquals(Saksstatus.TIL_BESLUTNING.name, rad[SakTable.status])
        assertEquals(Saksstatus.INNVILGET.name, rad[SakTable.foreslattUtfall])

        val vedtak = hentVedtak(database, sak).single()
        assertEquals(1, vedtak[ForelopigVedtakTable.versjon])
        assertEquals(10L, vedtak[ForelopigVedtakTable.eventId])
        assertEquals(ForelopigUtfall.INNVILGE.name, vedtak[ForelopigVedtakTable.utfall])
        assertEquals(9999, vedtak[ForelopigVedtakTable.innvilgetBelop])
        assertEquals("Til deltaker", vedtak[ForelopigVedtakTable.begrunnelseDeltaker])
        assertNull(vedtak[ForelopigVedtakTable.begrunnelseArbeidsgiver])
        assertEquals("Z123456", vedtak[ForelopigVedtakTable.innstiltAvIdent])
        assertEquals(tidspunkt, vedtak[ForelopigVedtakTable.sendtTidspunkt])

        val sakOppdatert = hentSakOppdatert(database).single()
        assertEquals(sak.sakId.toString(), sakOppdatert.sakId)
        assertEquals(AktorRolle.SAKSBEHANDLER, sakOppdatert.utfortAvRolle)
        assertEquals("Z123456", sakOppdatert.utfortAvIdent)
        assertTrue(sakOppdatert.notat.startsWith("Foreløpig vedtak sendt til beslutter"), sakOppdatert.notat)
        assertEquals(tidspunkt, sakOppdatert.tidspunkt)
    }

    @Test
    fun `avslag gir foreslatt_utfall AVSLATT`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = underBehandling(database)

        LagreForelopigVedtak(database).handle(Event(1L, innstilling(sak, ForelopigUtfall.AVSLAA)))

        assertEquals(Saksstatus.AVSLATT.name, hentSak(database, sak)[SakTable.foreslattUtfall])
        assertNull(hentVedtak(database, sak).single()[ForelopigVedtakTable.innvilgetBelop])
    }

    @Test
    fun `ny innsending etter retur gir versjon 2`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = underBehandling(database)
        val handler = LagreForelopigVedtak(database)

        handler.handle(Event(1L, innstilling(sak, ForelopigUtfall.AVSLAA)))
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
        handler.handle(Event(2L, innstilling(sak, ForelopigUtfall.INNVILGE_REDUSERT)))

        assertEquals(listOf(1, 2), hentVedtak(database, sak).map { it[ForelopigVedtakTable.versjon] })
        assertEquals(Saksstatus.INNVILGET.name, hentSak(database, sak)[SakTable.foreslattUtfall])
    }

    @Test
    fun `samme event to ganger gir én rad og én SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = underBehandling(database)
        val handler = LagreForelopigVedtak(database)
        val event = Event(1L, innstilling(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(1, hentVedtak(database, sak).size)
        assertEquals(1, hentSakOppdatert(database).size)
    }

    @Test
    fun `sak som ikke er under behandling gir Success uten endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        listOf(Saksstatus.OPPRETTET, Saksstatus.TIL_BESLUTNING, Saksstatus.INNVILGET).forEachIndexed { i, status ->
            val sak = lagreSoknadOgSak(database)
            settStatus(database, sak, status)

            val resultat = LagreForelopigVedtak(database).handle(Event(i + 1L, innstilling(sak)))

            assertIs<EventHandledResult.Success>(resultat)
            assertEquals(status.name, hentSak(database, sak)[SakTable.status])
            assertNull(hentSak(database, sak)[SakTable.foreslattUtfall])
            assertTrue(hentVedtak(database, sak).isEmpty())
        }
        assertTrue(hentSakOppdatert(database).isEmpty())
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())

        val resultat = LagreForelopigVedtak(database).handle(Event(1L, innstilling(sak)))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
        assertTrue(hentSakOppdatert(database).isEmpty())
    }

    private fun hentVedtak(database: Database, sak: LagretSak): List<ResultRow> = transaction(database) {
        ForelopigVedtakTable.selectAll()
            .where { ForelopigVedtakTable.sakId eq sak.sakId }
            .orderBy(ForelopigVedtakTable.versjon, SortOrder.ASC)
            .toList()
    }
}
