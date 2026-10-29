package no.nav.ekspertbistand.event.handlers

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.oebs.integration.NavEnhetNummer
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.OkonomiPart
import no.nav.ekspertbistand.oebs.integration.OpprettBestilling
import no.nav.ekspertbistand.oebs.integration.Organisasjonsnummer
import no.nav.ekspertbistand.oebs.integration.Periode
import no.nav.ekspertbistand.oebs.integration.Tilskuddstype
import no.nav.ekspertbistand.oebs.integration.Tiltakskode
import no.nav.ekspertbistand.oebs.integration.Valuta
import no.nav.ekspertbistand.oebs.model.OebsLopenummer
import no.nav.ekspertbistand.oebs.model.OebsOutbox
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.SakBestillingTable
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class BestillTilsagnIOebsTest {

    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = this@BestillTilsagnIOebsTest.now
    }

    private fun handler(database: Database) = BestillTilsagnIOebs(database, clock = clock)

    private suspend fun innvilgetTilBeslutning(database: Database): LagretSak =
        tilBeslutning(database, sak = lagreSoknadOgSak(database, behandlendeEnhet = "4242"))

    private fun saksnummer(database: Database, sak: LagretSak): String = transaction(database) {
        SakTable.select(SakTable.saksnummer).where { SakTable.sakId eq sak.sakId }.single()[SakTable.saksnummer]!!
    }

    private fun outbox(database: Database): List<ResultRow> = transaction(database) {
        OebsOutbox.selectAll().orderBy(OebsOutbox.id).toList()
    }

    private fun bestillinger(database: Database): List<ResultRow> = transaction(database) {
        SakBestillingTable.selectAll().toList()
    }

    private fun bestillingSakOppdatert(database: Database) =
        hentSakOppdatert(database).filter { it.notat.startsWith("Bestilling til OeBS opprettet") }

    @Test
    fun `innvilget vedtak bestiller tilsagnet i OeBS`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = innvilgetTilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(handler(database).handle(event))

        val saksnummer = saksnummer(database, sak)
        val bestillingsnummer = "E-$saksnummer-1"
        assertTrue(bestillingsnummer.length <= 20, "$bestillingsnummer er lengre enn 20 tegn")

        val rad = outbox(database).single()
        assertEquals("BESTILLING", rad[OebsOutbox.meldingstype])
        assertEquals(bestillingsnummer, rad[OebsOutbox.bestillingsnummer])
        assertEquals(
            OebsBestillingMelding.Bestilling(
                OpprettBestilling(
                    bestillingsnummer = bestillingsnummer,
                    tilskuddstype = Tilskuddstype.TILTAK_DRIFTSTILSKUDD,
                    tiltakskode = Tiltakskode.EKSPERTBISTAND,
                    arrangor = OpprettBestilling.Arrangor.Norsk(Organisasjonsnummer("987654321")),
                    kostnadssted = NavEnhetNummer("4242"),
                    avtalenummer = null,
                    belop = 9999,
                    periode = Periode(start = LocalDate(2026, 11, 22), slutt = LocalDate(2027, 1, 1)),
                    behandletAv = OkonomiPart.NavAnsatt(SAKSBEHANDLER_IDENT),
                    behandletTidspunkt = Instant.parse("2026-11-23T08:00:00Z"),
                    besluttetAv = OkonomiPart.NavAnsatt(BESLUTTER_IDENT),
                    besluttetTidspunkt = Instant.parse("2026-11-24T09:00:00Z"),
                    valuta = Valuta.NOK,
                )
            ),
            rad[OebsOutbox.meldingJson],
        )

        val bestilling = bestillinger(database).single()
        assertEquals(sak.sakId, bestilling[SakBestillingTable.sakId])
        assertEquals(bestillingsnummer, bestilling[SakBestillingTable.bestillingsnummer])
        assertEquals(9999, bestilling[SakBestillingTable.belop])
        assertEquals(LocalDate(2026, 11, 22), bestilling[SakBestillingTable.periodeFra])
        assertEquals(LocalDate(2026, 12, 31), bestilling[SakBestillingTable.periodeTil])

        val logg = bestillingSakOppdatert(database).single()
        assertEquals(AktorRolle.SYSTEM, logg.utfortAvRolle)
        assertEquals("Bestilling til OeBS opprettet: $bestillingsnummer", logg.notat)
    }

    @Test
    fun `avslått vedtak gir ingen bestilling`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = tilBeslutning(database, ForelopigUtfall.AVSLAA, lagreSoknadOgSak(database, behandlendeEnhet = "4242"))
        val event = Event(1L, vedtakFattet(sak, Saksstatus.AVSLATT))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(handler(database).handle(event))

        assertTrue(outbox(database).isEmpty())
        assertTrue(bestillinger(database).isEmpty())
        assertTrue(bestillingSakOppdatert(database).isEmpty())
    }

    @Test
    fun `vedtak avvist av FattVedtak etter retur gir ingen bestilling`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = innvilgetTilBeslutning(database)
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(handler(database).handle(event))

        assertTrue(outbox(database).isEmpty())
        assertTrue(bestillinger(database).isEmpty())
    }

    @Test
    fun `vedtak fra inhabil beslutter gir ingen bestilling`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = innvilgetTilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak, fattetAv = SAKSBEHANDLER_IDENT))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(handler(database).handle(event))

        assertTrue(outbox(database).isEmpty())
        assertTrue(bestillinger(database).isEmpty())
    }

    @Test
    fun `vedtaket er ikke lagret ennå gir transientError, så bestilling etter FattVedtak`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = innvilgetTilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        val handler = handler(database)

        assertIs<EventHandledResult.TransientError>(handler.handle(event))
        assertTrue(outbox(database).isEmpty())

        FattVedtak(database).handle(event)
        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertEquals(1, outbox(database).size)
        assertEquals(1, bestillinger(database).size)
    }

    @Test
    fun `samme event to ganger gir én bestilling`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = innvilgetTilBeslutning(database)
        val event = Event(1L, vedtakFattet(sak))
        FattVedtak(database).handle(event)

        assertIs<EventHandledResult.Success>(handler(database).handle(event))
        assertIs<EventHandledResult.Success>(handler(database).handle(event))

        assertEquals(1, outbox(database).size)
        assertEquals(1, bestillinger(database).size)
        assertEquals(1, bestillingSakOppdatert(database).size)
        val saksnummer = saksnummer(database, sak)
        val nesteLopenr = transaction(database) {
            OebsLopenummer.selectAll()
                .where { OebsLopenummer.saksnummer eq saksnummer }
                .single()[OebsLopenummer.nesteLopenr]
        }
        assertEquals(2, nesteLopenr)
    }
}
