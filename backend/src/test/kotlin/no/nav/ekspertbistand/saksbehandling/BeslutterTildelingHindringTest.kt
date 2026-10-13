package no.nav.ekspertbistand.saksbehandling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BeslutterTildelingHindringTest {

    private val meg = "A123456"
    private val kollega = "Z999999"
    private val beslutter = principal(meg, Role.BESLUTTER)

    @Test
    fun `ledig sak sendt av en kollega kan tildeles`() {
        assertNull(beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, null, kollega, beslutter))
    }

    @Test
    fun `sak med annen beslutter kan tas over`() {
        assertNull(beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, "Z888888", kollega, beslutter))
    }

    @Test
    fun `uten beslutterrolle gir MANGLER_ROLLE`() {
        assertEquals(
            BeslutterTildelingHindring.MANGLER_ROLLE,
            beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, null, kollega, principal(meg, Role.SAKSBEHANDLER)),
        )
    }

    @Test
    fun `annen status enn TIL_BESLUTNING gir UGYLDIG_STATUS`() {
        (Saksstatus.entries - Saksstatus.TIL_BESLUTNING).forEach {
            assertEquals(
                BeslutterTildelingHindring.UGYLDIG_STATUS,
                beslutterTildelingHindring(it, null, kollega, beslutter),
                "status=$it",
            )
        }
    }

    @Test
    fun `den som sendte vedtaket er INHABIL, også med begge rollene`() {
        listOf(beslutter, principal(meg, Role.SAKSBEHANDLER, Role.BESLUTTER)).forEach {
            assertEquals(
                BeslutterTildelingHindring.INHABIL,
                beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, null, meg, it),
            )
        }
    }

    @Test
    fun `uten foreløpig vedtak gir INHABIL (fail-closed)`() {
        assertEquals(
            BeslutterTildelingHindring.INHABIL,
            beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, null, null, beslutter),
        )
    }

    @Test
    fun `innlogget har saken allerede gir HAR_SAKEN_ALLEREDE`() {
        assertEquals(
            BeslutterTildelingHindring.HAR_SAKEN_ALLEREDE,
            beslutterTildelingHindring(Saksstatus.TIL_BESLUTNING, meg, kollega, beslutter),
        )
    }
}
