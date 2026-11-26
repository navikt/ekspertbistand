package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.sak.LagretSak
import no.nav.ekspertbistand.sak.lagreSoknadOgSak
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.hentMottatteInnsendinger
import no.nav.ekspertbistand.vedlegg.SluttrapportKanIkkeSendesInnException
import no.nav.ekspertbistand.vedlegg.SluttrapportStatus
import no.nav.ekspertbistand.vedlegg.SluttrapportTable
import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.registrerSluttrapport
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class LagreSluttrapportVurderingTest {

    private val tidspunkt = Instant.parse("2026-11-24T10:00:00Z")
    private val ident = "Z123456"

    private fun lagreSakMedSluttrapport(database: Database): Pair<LagretSak, UUID> {
        val sak = lagreSoknadOgSak(database)
        settStatus(database, sak, Saksstatus.INNVILGET)
        return sak to lagreSluttrapport(database, sak)
    }

    private fun lagreSluttrapport(database: Database, sak: LagretSak, filnavn: String = "sluttrapport.pdf"): UUID =
        transaction(database) {
            registrerSluttrapport(sak.sakId, sak.soknadId, "987654321", "42", filnavn, byteArrayOf(1, 2, 3))
            SluttrapportTable.selectAll()
                .where { SluttrapportTable.sakId eq sak.sakId }
                .orderBy(SluttrapportTable.opprettet, org.jetbrains.exposed.v1.core.SortOrder.DESC)
                .first()[SluttrapportTable.id]
        }

    private fun bekreftet(sak: LagretSak, sluttrapportId: UUID) = EventData.SluttrapportBekreftet(
        sluttrapportId = sluttrapportId.toString(),
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        vurdertAvIdent = ident,
        vurdertAvNavn = "Sara Saksbehandler",
        tidspunkt = tidspunkt,
    )

    private fun nyInnsendingKrevd(sak: LagretSak, sluttrapportId: UUID) = EventData.SluttrapportNyInnsendingKrevd(
        sluttrapportId = sluttrapportId.toString(),
        sakId = sak.sakId.toString(),
        soknadId = sak.soknadId.toString(),
        vurdertAvIdent = ident,
        vurdertAvNavn = "Sara Saksbehandler",
        begrunnelse = "Vedlegget mangler signatur",
        tidspunkt = tidspunkt,
    )

    private fun hentSluttrapport(database: Database, id: UUID): ResultRow = transaction(database) {
        SluttrapportTable.selectAll().where { SluttrapportTable.id eq id }.single()
    }

    private fun saksloggNotater(database: Database, sak: LagretSak): List<EventData.SakOppdatert> =
        hentSakOppdatert(database).filter { it.sakId == sak.sakId.toString() && it.utfortAvRolle == AktorRolle.SAKSBEHANDLER }

    @Test
    fun `bekreftet lagrer status, ident og tidspunkt og skriver sakslogg`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, sluttrapportId) = lagreSakMedSluttrapport(database)

        val resultat = LagreSluttrapportBekreftet(database).handle(Event(1L, bekreftet(sak, sluttrapportId)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSluttrapport(database, sluttrapportId)
        assertEquals(SluttrapportStatus.BEKREFTET.name, rad[SluttrapportTable.status])
        assertEquals(ident, rad[SluttrapportTable.vurdertAvIdent])
        assertEquals("Sara Saksbehandler", rad[SluttrapportTable.vurdertAvNavn])
        assertEquals(tidspunkt, rad[SluttrapportTable.vurdertTidspunkt])
        assertNull(rad[SluttrapportTable.begrunnelse])
        assertEquals(tidspunkt, transaction(database) {
            SakTable.selectAll().where { SakTable.sakId eq sak.sakId }.single()[SakTable.sistEndret]
        })
        val sakslogg = saksloggNotater(database, sak).single()
        assertEquals("Sluttrapport bekreftet", sakslogg.notat)
        assertEquals(ident, sakslogg.utfortAvIdent)
    }

    @Test
    fun `ny innsending krevd lagrer begrunnelsen, men ikke i saksloggen`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, sluttrapportId) = lagreSakMedSluttrapport(database)

        val resultat = LagreSluttrapportNyInnsendingKrevd(database)
            .handle(Event(1L, nyInnsendingKrevd(sak, sluttrapportId)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSluttrapport(database, sluttrapportId)
        assertEquals(SluttrapportStatus.NY_INNSENDING_KREVES.name, rad[SluttrapportTable.status])
        assertEquals("Vedlegget mangler signatur", rad[SluttrapportTable.begrunnelse])
        val sakslogg = saksloggNotater(database, sak).single()
        assertEquals("Ny innsending av sluttrapport kreves", sakslogg.notat)
        assertTrue("signatur" !in sakslogg.notat)
    }

    @Test
    fun `samme event to ganger gir én sakslogg`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, sluttrapportId) = lagreSakMedSluttrapport(database)
        val handler = LagreSluttrapportBekreftet(database)

        handler.handle(Event(1L, bekreftet(sak, sluttrapportId)))
        val resultat = handler.handle(Event(1L, bekreftet(sak, sluttrapportId)))

        assertIs<EventHandledResult.Success>(resultat)
        assertEquals(1, saksloggNotater(database, sak).size)
    }

    @Test
    fun `allerede vurdert sluttrapport endres ikke og får ingen sakslogg`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, sluttrapportId) = lagreSakMedSluttrapport(database)

        LagreSluttrapportBekreftet(database).handle(Event(1L, bekreftet(sak, sluttrapportId)))
        val resultat = LagreSluttrapportNyInnsendingKrevd(database)
            .handle(Event(2L, nyInnsendingKrevd(sak, sluttrapportId)))

        assertIs<EventHandledResult.Success>(resultat)
        val rad = hentSluttrapport(database, sluttrapportId)
        assertEquals(SluttrapportStatus.BEKREFTET.name, rad[SluttrapportTable.status])
        assertNull(rad[SluttrapportTable.begrunnelse])
        assertEquals(1, saksloggNotater(database, sak).size)
    }

    @Test
    fun `sluttrapport på en annen sak endres ikke`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (_, sluttrapportId) = lagreSakMedSluttrapport(database)
        val annenSak = lagreSoknadOgSak(database)

        LagreSluttrapportBekreftet(database).handle(Event(1L, bekreftet(annenSak, sluttrapportId)))

        assertEquals(SluttrapportStatus.MOTTATT.name, hentSluttrapport(database, sluttrapportId)[SluttrapportTable.status])
    }

    @Test
    fun `ny opplasting sletter filen til forrige sluttrapport, men beholder raden`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, forste) = lagreSakMedSluttrapport(database)
        LagreSluttrapportNyInnsendingKrevd(database).handle(Event(1L, nyInnsendingKrevd(sak, forste)))

        val andre = lagreSluttrapport(database, sak, filnavn = "ny.pdf")

        transaction(database) {
            assertTrue(VedleggTable.selectAll().where { VedleggTable.sluttrapportId eq forste }.empty())
            assertEquals(1, VedleggTable.selectAll().where { VedleggTable.sluttrapportId eq andre }.count())
        }
        val gammel = hentSluttrapport(database, forste)
        assertEquals(SluttrapportStatus.NY_INNSENDING_KREVES.name, gammel[SluttrapportTable.status])
        assertEquals("Vedlegget mangler signatur", gammel[SluttrapportTable.begrunnelse])
        assertEquals(SluttrapportStatus.MOTTATT.name, hentSluttrapport(database, andre)[SluttrapportTable.status])
    }

    @Test
    fun `ny opplasting avvises når nyeste sluttrapport er mottatt eller bekreftet`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val (sak, forste) = lagreSakMedSluttrapport(database)

        assertFailsWith<SluttrapportKanIkkeSendesInnException> { lagreSluttrapport(database, sak, filnavn = "andre.pdf") }
        LagreSluttrapportBekreftet(database).handle(Event(1L, bekreftet(sak, forste)))
        assertFailsWith<SluttrapportKanIkkeSendesInnException> { lagreSluttrapport(database, sak, filnavn = "tredje.pdf") }

        transaction(database) {
            assertEquals(1, SluttrapportTable.selectAll().where { SluttrapportTable.sakId eq sak.sakId }.count())
            assertEquals(1, VedleggTable.selectAll().where { VedleggTable.sluttrapportId eq forste }.count())
        }
    }

    @Test
    fun `oppgaven forsvinner når nyeste sluttrapport er vurdert, også om en eldre står som mottatt`() =
        testApplicationWithDatabase {
            val database = it.config.jdbcDatabase
            val sak = lagreSoknadOgSak(database)
            settStatus(database, sak, Saksstatus.INNVILGET)
            val nyeste = lagreSluttrapport(database, sak, filnavn = "nyeste.pdf")
            // Før vakten på opplasting kunne en sak få flere sluttrapporter med status MOTTATT.
            transaction(database) {
                val nyesteOpprettet = hentSluttrapport(database, nyeste)[SluttrapportTable.opprettet]
                SluttrapportTable.insert {
                    it[sakId] = sak.sakId
                    it[status] = SluttrapportStatus.MOTTATT.name
                    it[opprettet] = nyesteOpprettet - 1.hours
                }
            }
            assertTrue(transaction(database) { hentMottatteInnsendinger(listOf(sak.sakId)) }[sak.sakId]?.sluttrapport != null)

            LagreSluttrapportBekreftet(database).handle(Event(1L, bekreftet(sak, nyeste)))

            assertNull(transaction(database) { hentMottatteInnsendinger(listOf(sak.sakId)) }[sak.sakId])
        }
}
