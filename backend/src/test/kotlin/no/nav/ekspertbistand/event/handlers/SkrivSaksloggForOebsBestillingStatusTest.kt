package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.TestEventData
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain
import no.nav.ekspertbistand.event.domain.OebsBestillingStatusDomain.Status
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SkrivSaksloggForOebsBestillingStatusTest {

    private val nr = "E-2026/1000001-1"
    private val statusTidspunkt = Instant.parse("2026-11-17T10:30:28Z")

    private fun bestilling(status: Status, feilKode: String? = null, feilMelding: String? = null) =
        OebsBestillingStatusDomain(
            bestillingsnummer = nr,
            status = status,
            statusSistOppdatert = statusTidspunkt,
            feilKode = feilKode,
            feilMelding = feilMelding,
            kvittering = TestEventData.oebsKvittering,
        )

    private fun event(bestilling: OebsBestillingStatusDomain) =
        Event(1L, TestEventData.oebsBestillingStatusMottatt.copy(bestilling = bestilling))

    @Test
    fun `AKTIV gir sakslogg om opprettet bestilling med tidspunktet fra VALP`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val event = event(bestilling(Status.AKTIV))

        assertIs<EventHandledResult.Success>(SkrivSaksloggForOebsBestillingStatus(database).handle(event))

        val logg = hentSakOppdatert(database).single()
        assertEquals(event.data.sakId, logg.sakId)
        assertEquals(event.data.soknadId, logg.soknadId)
        assertEquals(AktorRolle.SYSTEM, logg.utfortAvRolle)
        assertNull(logg.utfortAvIdent)
        assertEquals("Bestilling til OeBS opprettet: $nr", logg.notat)
        assertEquals(statusTidspunkt, logg.tidspunkt)
    }

    @Test
    fun `samme event to ganger gir en sakslogg`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = SkrivSaksloggForOebsBestillingStatus(database)
        val event = event(bestilling(Status.SENDT))

        assertIs<EventHandledResult.Success>(handler.handle(event))
        assertIs<EventHandledResult.Success>(handler.handle(event))

        assertEquals("Bestilling til OeBS sendt: $nr", hentSakOppdatert(database).single().notat)
    }

    @Test
    fun `status uten implementert sakslogg gir transientError og ingen sakslogg`() = testApplicationWithDatabase {
        val database = it.config.jdbcDatabase
        val handler = SkrivSaksloggForOebsBestillingStatus(database)

        for (status in listOf(Status.ANNULLERING_SENDT, Status.ANNULLERT, Status.OPPGJORT)) {
            assertIs<EventHandledResult.TransientError>(handler.handle(event(bestilling(status))))
        }

        assertTrue(hentSakOppdatert(database).isEmpty())
    }

    @Test
    fun `FEILET tar med det VALP sendte av feilkode og feilmelding`() {
        assertEquals(
            "Bestilling til OeBS feilet: $nr. Kontering feilet (PO_PDOI_CHARGE_FAILED)",
            saksloggNotat(bestilling(Status.FEILET, "PO_PDOI_CHARGE_FAILED", "Kontering feilet")),
        )
        assertEquals(
            "Bestilling til OeBS feilet: $nr. Kontering feilet",
            saksloggNotat(bestilling(Status.FEILET, feilMelding = "Kontering feilet")),
        )
        assertEquals(
            "Bestilling til OeBS feilet: $nr (PO_PDOI_CHARGE_FAILED)",
            saksloggNotat(bestilling(Status.FEILET, feilKode = "PO_PDOI_CHARGE_FAILED")),
        )
        assertEquals("Bestilling til OeBS feilet: $nr", saksloggNotat(bestilling(Status.FEILET)))
    }
}
