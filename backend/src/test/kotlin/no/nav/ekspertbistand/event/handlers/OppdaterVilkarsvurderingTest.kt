package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.handlers.OpprettSak.Companion.opprettVilkarForSak
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.SaksvilkarTable
import no.nav.ekspertbistand.saksbehandling.Vilkar
import no.nav.ekspertbistand.saksbehandling.VilkarsvurderingRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class OppdaterVilkarsvurderingTest {

    private val tidspunkt = Instant.parse("2026-10-09T10:00:00Z")
    private val vilkar = Vilkar.DELTAKER_HAR_ARBEIDSFORHOLD

    private fun vurdert(
        sak: LagretSak,
        godkjent: Boolean? = true,
        notat: String? = "Bekreftet",
        ident: String = "Z123456",
        tidspunkt: Instant = this.tidspunkt,
    ) = EventData.VilkarsvurderingOppdatert(
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        vurdering = VilkarsvurderingRequest(vilkar, godkjent, notat),
        vurdertAvIdent = ident,
        tidspunkt = tidspunkt,
    )

    private fun lagreSakUnderBehandling(database: Database): LagretSak {
        val sak = lagreSoknadOgSak(database)
        transaction(database) { opprettVilkarForSak(sak.sakId) }
        settStatus(database, sak, Saksstatus.UNDER_BEHANDLING)
        return sak
    }

    @Test
    fun `lagrer vurderingen med tidspunktet fra eventen`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)

        val resultat = OppdaterVilkarsvurdering(database).handle(Event(1L, vurdert(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentVilkar(database, sak, vilkar)
        assertEquals(true, rad[SaksvilkarTable.godkjent])
        assertEquals("Bekreftet", rad[SaksvilkarTable.notat])
        assertEquals("Z123456", rad[SaksvilkarTable.vurdertAvIdent])
        assertEquals(tidspunkt, rad[SaksvilkarTable.vurdertTidspunkt])
        assertEquals(tidspunkt, hentSak(database, sak)[SakTable.sistEndret])
        assertTrue(
            Vilkar.entries.filter { v -> v != vilkar }
                .all { v -> hentVilkar(database, sak, v)[SaksvilkarTable.vurdertAvIdent] == null },
            "andre vilkår skal ikke endres",
        )
    }

    @Test
    fun `godkjent null nullstiller vurderingen`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)
        val handler = OppdaterVilkarsvurdering(database)

        handler.handle(Event(1L, vurdert(sak)))
        handler.handle(Event(2L, vurdert(sak, godkjent = null, notat = null, tidspunkt = tidspunkt + 1.minutes)))

        val rad = hentVilkar(database, sak, vilkar)
        assertNull(rad[SaksvilkarTable.godkjent])
        assertNull(rad[SaksvilkarTable.notat])
        assertEquals(tidspunkt + 1.minutes, rad[SaksvilkarTable.vurdertTidspunkt])
    }

    @Test
    fun `eldre vurdering skriver ikke over en nyere`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)
        val handler = OppdaterVilkarsvurdering(database)

        handler.handle(Event(1L, vurdert(sak, godkjent = false, notat = "Ny", tidspunkt = tidspunkt + 1.minutes)))
        val resultat = handler.handle(Event(2L, vurdert(sak, godkjent = true, notat = "Gammel")))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentVilkar(database, sak, vilkar)
        assertEquals(false, rad[SaksvilkarTable.godkjent])
        assertEquals("Ny", rad[SaksvilkarTable.notat])
        assertEquals(tidspunkt + 1.minutes, rad[SaksvilkarTable.vurdertTidspunkt])
    }

    @Test
    fun `samme event to ganger gir samme resultat`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)
        val handler = OppdaterVilkarsvurdering(database)
        val event = Event(1L, vurdert(sak))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals(tidspunkt, hentVilkar(database, sak, vilkar)[SaksvilkarTable.vurdertTidspunkt])
    }

    @Test
    fun `lagrer ikke når saken ikke lenger er under behandling`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)
        settStatus(database, sak, Saksstatus.TIL_BESLUTNING)

        val resultat = OppdaterVilkarsvurdering(database).handle(Event(1L, vurdert(sak)))

        assertIs<EventHandledResult.Success>(resultat)
        assertNull(hentVilkar(database, sak, vilkar)[SaksvilkarTable.vurdertAvIdent])
    }

    @Test
    fun `manglende vilkårsrad gir unrecoverable og legges ikke til`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = lagreSakUnderBehandling(database)
        transaction(database) {
            SaksvilkarTable.deleteWhere { (SaksvilkarTable.sakId eq sak.sakId) and (vilkarId eq vilkar.name) }
        }

        val resultat = OppdaterVilkarsvurdering(database).handle(Event(1L, vurdert(sak)))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
        assertEquals(
            0L,
            transaction(database) {
                SaksvilkarTable.selectAll()
                    .where { (SaksvilkarTable.sakId eq sak.sakId) and (SaksvilkarTable.vilkarId eq vilkar.name) }
                    .count()
            },
        )
    }

    @Test
    fun `ukjent sak gir unrecoverable`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val sak = LagretSak(sakId = UUID.randomUUID(), soknadId = UUID.randomUUID())

        val resultat = OppdaterVilkarsvurdering(database).handle(Event(1L, vurdert(sak)))

        assertIs<EventHandledResult.UnrecoverableError>(resultat)
    }

    private fun hentVilkar(database: Database, sak: LagretSak, vilkar: Vilkar): ResultRow = transaction(database) {
        SaksvilkarTable.selectAll()
            .where { (SaksvilkarTable.sakId eq sak.sakId) and (SaksvilkarTable.vilkarId eq vilkar.name) }
            .single()
    }
}
