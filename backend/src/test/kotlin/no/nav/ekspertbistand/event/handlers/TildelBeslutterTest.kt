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
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
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
class TildelBeslutterTest {

    private val tidspunkt = Instant.parse("2026-04-02T08:00:00Z")
    private val saksbehandler = "Z999999"

    private fun tildelt(sak: LagretSak, ident: String = "Z123456", navn: String = "Berit Beslutter") =
        EventData.SakTildeltBeslutter(
            sakId = sak.sakId.toString(),
            soknadId = sak.soknadId.toString(),
            beslutterIdent = ident,
            beslutterNavn = navn,
            tidspunkt = tidspunkt,
        )

    private fun lagreSakTilBeslutning(database: Database, innstiltAvIdent: String = saksbehandler): LagretSak {
        val sak = lagreSoknadOgSak(database)
        settStatus(database, sak, Saksstatus.TIL_BESLUTNING)
        transaction(database) {
            ForelopigVedtakTable.insert {
                it[sakId] = sak.sakId
                it[versjon] = 1
                it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
                it[utfall] = ForelopigUtfall.INNVILGE.name
                it[innvilgetBelop] = 9999
                it[tiltaksperiodeFra] = LocalDate(2026, 11, 22)
                it[tiltaksperiodeTil] = LocalDate(2026, 12, 31)
                it[begrunnelseDeltaker] = "Til deltaker"
                it[this.innstiltAvIdent] = innstiltAvIdent
                it[sendtTidspunkt] = tidspunkt
            }
        }
        return sak
    }

    @Test
    fun `tildeling setter ident og navn og publiserer SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakTilBeslutning(database)

        val resultat = TildelBeslutter(database).handle(Event(10L, tildelt(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSak(database, sak)
        assertEquals("Z123456", rad[SakTable.beslutterIdent])
        assertEquals("Berit Beslutter", rad[SakTable.beslutterNavn])
        assertEquals(Saksstatus.TIL_BESLUTNING.name, rad[SakTable.status])
        assertEquals(10L, rad[SakTable.beslutterTildelingEventId])
        assertNull(rad[SakTable.tildelingEventId], "tildeling av saksbehandler skal ikke påvirkes")

        val sakOppdatert = hentSakOppdatert(database).single()
        assertEquals(sak.sakId.toString(), sakOppdatert.sakId)
        assertEquals(AktorRolle.BESLUTTER, sakOppdatert.utfortAvRolle)
        assertEquals("Z123456", sakOppdatert.utfortAvIdent)
        assertEquals("Beslutter tildelt", sakOppdatert.notat)
        assertEquals(tidspunkt, sakOppdatert.tidspunkt)
    }

    @Test
    fun `samme event to ganger gir en SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakTilBeslutning(database)
        val handler = TildelBeslutter(database)
        val event = Event(1L, tildelt(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(1, hentSakOppdatert(database).size)
    }

    @Test
    fun `to tildelinger etter hverandre - den siste vinner`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakTilBeslutning(database)
        val handler = TildelBeslutter(database)

        handler.handle(Event(1L, tildelt(sak, "Z111111", "Første Beslutter")))
        handler.handle(Event(2L, tildelt(sak, "Z222222", "Andre Beslutter")))

        val rad = hentSak(database, sak)
        assertEquals("Z222222", rad[SakTable.beslutterIdent])
        assertEquals("Andre Beslutter", rad[SakTable.beslutterNavn])
        assertEquals(2, hentSakOppdatert(database).size)
    }

    @Test
    fun `eldre event etter en nyere endrer ikke sak og publiserer ikke SakOppdatert`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakTilBeslutning(database)
        val handler = TildelBeslutter(database)

        handler.handle(Event(2L, tildelt(sak, "Z222222", "Andre Beslutter")))
        assertIs<EventHandledResult.Success>(handler.handle(Event(1L, tildelt(sak, "Z111111", "Første Beslutter"))))

        val rad = hentSak(database, sak)
        assertEquals("Z222222", rad[SakTable.beslutterIdent])
        assertEquals(2L, rad[SakTable.beslutterTildelingEventId])
        assertEquals(listOf("Z222222"), hentSakOppdatert(database).map { it.utfortAvIdent })
    }

    @Test
    fun `ingen endring når saken ikke er TIL_BESLUTNING`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        (Saksstatus.entries - Saksstatus.TIL_BESLUTNING).forEachIndexed { i, status ->
            val sak = lagreSakTilBeslutning(database)
            settStatus(database, sak, status)

            assertIs<EventHandledResult.Success>(TildelBeslutter(database).handle(Event(i + 1L, tildelt(sak))))

            assertNull(hentSak(database, sak)[SakTable.beslutterIdent], "status=$status")
        }
        assertEquals(0, hentSakOppdatert(database).size)
    }

    @Test
    fun `ingen endring når beslutteren sendte det foreløpige vedtaket`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakTilBeslutning(database, innstiltAvIdent = "Z123456")

        assertIs<EventHandledResult.Success>(TildelBeslutter(database).handle(Event(1L, tildelt(sak, "Z123456"))))

        assertNull(hentSak(database, sak)[SakTable.beslutterIdent])
        assertEquals(0, hentSakOppdatert(database).size)
    }

    @Test
    fun `ingen endring uten foreløpig vedtak`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)
        settStatus(database, sak, Saksstatus.TIL_BESLUTNING)

        assertIs<EventHandledResult.Success>(TildelBeslutter(database).handle(Event(1L, tildelt(sak))))

        assertNull(hentSak(database, sak)[SakTable.beslutterIdent])
        assertEquals(0, hentSakOppdatert(database).size)
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())

        val resultat = TildelBeslutter(database).handle(Event(1L, tildelt(sak)))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
        assertEquals(0, hentSakOppdatert(database).size)
    }

    @Test
    fun `CHECK avviser beslutterident uten navn og navn uten ident`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSoknadOgSak(database)

        assertThrows<Exception> {
            transaction(database) {
                SakTable.update({ SakTable.sakId eq sak.sakId }) { it[beslutterIdent] = "Z123456" }
            }
        }
        assertThrows<Exception> {
            transaction(database) {
                SakTable.update({ SakTable.sakId eq sak.sakId }) { it[beslutterNavn] = "Berit Beslutter" }
            }
        }
        assertNull(hentSak(database, sak)[SakTable.beslutterIdent])
    }

    @Test
    fun `SakTildeltBeslutter avviser blankt navn`() {
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())
        assertThrows<IllegalArgumentException> { tildelt(sak, navn = " ") }
    }
}
