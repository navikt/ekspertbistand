@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import no.nav.ekspertbistand.oebs.integration.BestillingStatus
import no.nav.ekspertbistand.oebs.integration.BestillingStatusType
import no.nav.ekspertbistand.oebs.integration.FakturaStatus
import no.nav.ekspertbistand.oebs.integration.FakturaStatusType
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import no.nav.ekspertbistand.oebs.integration.OebsFeil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Kontraktstest mot statusmeldinger fra Team VALP (`FakturaStatus` sjekket mot VALP sin main
 * 2026-11-16), verifiserer at vår lokale speiling av status-DTO-ene deserialiseres nøyaktig fra
 * VALP sitt wire-format.
 *
 * Statusmeldingene konsumeres av [no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer], så
 * decode er den produksjonsrelevante retningen.
 */
class OebsStatusMeldingTest {

    private val json = OebsBestillingMelding.json

    @Test
    fun `bestilling-status deserialiseres fra VALP-kontrakten`() {
        val raw = """
            {
              "bestillingsnummer": "A-2024/12133-1",
              "status": "ANNULLERT",
              "statusSistOppdatert": "2026-11-16T12:00:00.591621Z"
            }
        """.trimIndent()

        val status = json.decodeFromString<BestillingStatus>(raw)

        assertEquals("A-2024/12133-1", status.bestillingsnummer)
        assertEquals(BestillingStatusType.ANNULLERT, status.status)
        assertEquals(Instant.parse("2026-11-16T12:00:00.591621Z"), status.statusSistOppdatert)
        assertNull(status.feil)
    }

    @Test
    fun `bestilling-status FEILET tar med feilkode og feilmelding`() {
        val raw = """
            {
              "bestillingsnummer": "E-2026/900006-1",
              "status": "FEILET",
              "statusSistOppdatert": "2026-11-17T10:30:28Z",
              "feil": {
                "kode": "PO_PDOI_CHARGE_FAILED",
                "melding": "Feil: Generering av Kostnadskonto mislyktes."
              }
            }
        """.trimIndent()

        val status = json.decodeFromString<BestillingStatus>(raw)

        assertEquals(BestillingStatusType.FEILET, status.status)
        assertEquals(
            OebsFeil(kode = "PO_PDOI_CHARGE_FAILED", melding = "Feil: Generering av Kostnadskonto mislyktes."),
            status.feil,
        )
    }

    @Test
    fun `faktura-status FEILET tar med feil der kode og melding kan mangle`() {
        val raw = """
            {
              "fakturanummer": "E-2026/900001-1-1",
              "status": "FEILET",
              "statusSistOppdatert": "2026-11-17T10:30:28Z",
              "feil": { "kode": null, "melding": "Fakturaen ble avvist" }
            }
        """.trimIndent()

        val status = json.decodeFromString<FakturaStatus>(raw)

        assertEquals(OebsFeil(kode = null, melding = "Fakturaen ble avvist"), status.feil)
    }

    @Test
    fun `faktura-status deserialiseres fra VALP-kontrakten`() {
        // VALP ga feltet nytt navn fra fakturaStatusSistOppdatert 2026-09-21 (mulighetsrommet 944a943).
        val raw = """
            {
              "fakturanummer": "E-2026/900001-1-1",
              "status": "IKKE_BETALT",
              "statusSistOppdatert": "2026-11-16T12:00:00.591621Z"
            }
        """.trimIndent()

        val status = json.decodeFromString<FakturaStatus>(raw)

        assertEquals("E-2026/900001-1-1", status.fakturanummer)
        assertEquals(FakturaStatusType.IKKE_BETALT, status.status)
        assertEquals(Instant.parse("2026-11-16T12:00:00.591621Z"), status.statusSistOppdatert)
    }
}
