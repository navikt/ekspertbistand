@file:OptIn(ExperimentalTime::class)

package no.nav.ekspertbistand.oebs

import no.nav.ekspertbistand.oebs.integration.BestillingStatus
import no.nav.ekspertbistand.oebs.integration.BestillingStatusType
import no.nav.ekspertbistand.oebs.integration.FakturaStatus
import no.nav.ekspertbistand.oebs.integration.FakturaStatusType
import no.nav.ekspertbistand.oebs.integration.OebsBestillingMelding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Kontraktstest mot faktiske statusmeldinger fra Team VALP (mottatt 2026-09-17), verifiserer at vår
 * lokale speiling av status-DTO-ene deserialiseres nøyaktig fra VALP sitt wire-format.
 *
 * Statusmeldingene konsumeres av [no.nav.ekspertbistand.oebs.integration.TiltaksokonomiConsumer], så
 * decode er den produksjonsrelevante retningen.
 */
class OebsStatusMeldingTest {

    private val json = OebsBestillingMelding.json

    @Test
    fun `bestilling-status deserialiseres fra VALP-kontrakten`() {
        val raw = """{ "bestillingsnummer": "A-2024/12133-1", "status": "ANNULLERT" }"""

        val status = json.decodeFromString<BestillingStatus>(raw)

        assertEquals("A-2024/12133-1", status.bestillingsnummer)
        assertEquals(BestillingStatusType.ANNULLERT, status.status)
    }

    @Test
    fun `faktura-status deserialiseres fra VALP-kontrakten`() {
        val raw = """
            {
              "fakturanummer": "A-2026/10468-1-3",
              "status": "IKKE_BETALT",
              "fakturaStatusSistOppdatert": "2026-06-03T13:00:00.591621Z"
            }
        """.trimIndent()

        val status = json.decodeFromString<FakturaStatus>(raw)

        assertEquals("A-2026/10468-1-3", status.fakturanummer)
        assertEquals(FakturaStatusType.IKKE_BETALT, status.status)
        assertEquals(Instant.parse("2026-06-03T13:00:00.591621Z"), status.fakturaStatusSistOppdatert)
    }
}
