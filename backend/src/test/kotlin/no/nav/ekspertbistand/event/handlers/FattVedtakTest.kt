package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.VedtakTable
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal const val SAKSBEHANDLER_IDENT = "Z123456"
internal const val BESLUTTER_IDENT = "B123456"

@OptIn(ExperimentalTime::class)
private val innstiltTidspunkt = Instant.parse("2026-11-23T08:00:00Z")

/** Lagrer søknad og sak og sender et foreløpig vedtak til beslutter, så saken står i `TIL_BESLUTNING`. */
@OptIn(ExperimentalTime::class)
internal suspend fun tilBeslutning(
    database: Database,
    utfall: ForelopigUtfall = ForelopigUtfall.INNVILGE,
    sak: LagretSak = lagreSoknadOgSak(database),
    eventId: Long = 100L,
): LagretSak {
    settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
    val resultat = LagreForelopigVedtak(database).handle(
        Event(
            eventId,
            EventData.ForelopigVedtakSendtTilBeslutter(
                sakId = sak.sakId.toString(),
                soknadId = sak.soknadId.toString(),
                utfall = utfall,
                innvilgetBelop = if (utfall == ForelopigUtfall.AVSLAA) null else 9999,
                tiltaksperiodeFra = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 11, 22),
                tiltaksperiodeTil = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 12, 31),
                begrunnelseDeltaker = "Til deltaker",
                begrunnelseArbeidsgiver = if (utfall == ForelopigUtfall.INNVILGE) null else "Til arbeidsgiver",
                innstiltAvIdent = SAKSBEHANDLER_IDENT,
                tidspunkt = innstiltTidspunkt,
            )
        )
    )
    assertIs<EventHandledResult.Success>(resultat)
    return sak
}

internal fun hentVedtakRader(database: Database, sak: LagretSak): List<ResultRow> = transaction(database) {
    VedtakTable.selectAll().where { VedtakTable.sakId eq sak.sakId }.toList()
}

@OptIn(ExperimentalTime::class)
internal fun vedtakFattet(
    sak: LagretSak,
    utfall: Saksstatus = Saksstatus.INNVILGET,
    versjon: Int = 1,
    fattetAv: String = BESLUTTER_IDENT,
) = EventData.VedtakFattet(
    sakId = sak.sakId.toString(),
    soknadId = sak.soknadId.toString(),
    forelopigVedtakVersjon = versjon,
    utfall = utfall,
    fattetAvIdent = fattetAv,
    fattetAvNavn = "Beslutter Navn",
    tidspunkt = Instant.parse("2026-11-24T09:00:00Z"),
)

@OptIn(ExperimentalTime::class)
class FattVedtakTest {

    @Test
    fun `lagrer vedtaket, setter INNVILGET og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val sakslogg = hentSakOppdatert(database).size

        val resultat = FattVedtak(database).handle(Event(1L, vedtakFattet(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        assertEquals(Saksstatus.INNVILGET.name, hentSak(database, sak)[SakTable.status])
        val vedtak = hentVedtakRader(database, sak).single()
        assertEquals(Saksstatus.INNVILGET.name, vedtak[VedtakTable.utfall])
        assertEquals(BESLUTTER_IDENT, vedtak[VedtakTable.fattetAvIdent])
        assertEquals("Beslutter Navn", vedtak[VedtakTable.fattetAvNavn])
        assertEquals(1L, vedtak[VedtakTable.eventId])
        assertEquals(Instant.parse("2026-11-24T09:00:00Z"), vedtak[VedtakTable.fattetTidspunkt])

        val logg = hentSakOppdatert(database).drop(sakslogg).single()
        assertEquals(AktorRolle.BESLUTTER, logg.utfortAvRolle)
        assertEquals(BESLUTTER_IDENT, logg.utfortAvIdent)
        assertEquals("Vedtak fattet: innvilget", logg.notat)
    }

    @Test
    fun `avslag setter AVSLATT`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database, ForelopigUtfall.AVSLAA)

        FattVedtak(database).handle(Event(1L, vedtakFattet(sak, Saksstatus.AVSLATT)))

        assertEquals(Saksstatus.AVSLATT.name, hentSak(database, sak)[SakTable.status])
        assertEquals("Vedtak fattet: avslått", hentSakOppdatert(database).last().notat)
    }

    @Test
    fun `samme event to ganger gir én rad og én SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val sakslogg = hentSakOppdatert(database).size
        val handler = FattVedtak(database)
        val event = Event(1L, vedtakFattet(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(1, hentVedtakRader(database, sak).size)
        assertEquals(sakslogg + 1, hentSakOppdatert(database).size)
    }

    @Test
    fun `utdatert versjon gir ingen endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val sakslogg = hentSakOppdatert(database).size

        val resultat = FattVedtak(database).handle(Event(1L, vedtakFattet(sak, versjon = 2)))

        assertIs<EventHandledResult.Success>(resultat)
        assertEquals(Saksstatus.TIL_BESLUTNING.name, hentSak(database, sak)[SakTable.status])
        assertTrue(hentVedtakRader(database, sak).isEmpty())
        assertEquals(sakslogg, hentSakOppdatert(database).size)
    }

    @Test
    fun `sak som ikke er TIL_BESLUTNING gir ingen endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)

        assertIs<EventHandledResult.Success>(FattVedtak(database).handle(Event(1L, vedtakFattet(sak))))

        assertEquals(Saksstatus.UNDER_BEHANDLING.name, hentSak(database, sak)[SakTable.status])
        assertTrue(hentVedtakRader(database, sak).isEmpty())
    }

    @Test
    fun `inhabil beslutter gir ingen endring`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val sakslogg = hentSakOppdatert(database).size

        val resultat = FattVedtak(database).handle(Event(1L, vedtakFattet(sak, fattetAv = SAKSBEHANDLER_IDENT)))

        assertIs<EventHandledResult.Success>(resultat)
        assertEquals(Saksstatus.TIL_BESLUTNING.name, hentSak(database, sak)[SakTable.status])
        assertTrue(hentVedtakRader(database, sak).isEmpty())
        assertEquals(sakslogg, hentSakOppdatert(database).size)
    }

    @Test
    fun `nytt vedtak på allerede besluttet sak gir ingen ny rad`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database)
        val handler = FattVedtak(database)

        handler.handle(Event(1L, vedtakFattet(sak)))
        assertIs<EventHandledResult.Success>(handler.handle(Event(2L, vedtakFattet(sak))))

        assertEquals(1L, hentVedtakRader(database, sak).single()[VedtakTable.eventId])
    }
}
