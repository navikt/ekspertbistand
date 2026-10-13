package no.nav.ekspertbistand.saksbehandling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TildelingHindringTest {

    private val meg = "A123456"
    private val kollega = "Z999999"
    private val saksbehandler = principal(meg, Role.SAKSBEHANDLER)

    @Test
    fun `sak som venter på saksbehandler kan tildeles, ledig eller hos en kollega`() {
        listOf(Saksstatus.OPPRETTET, Saksstatus.UNDER_BEHANDLING).forEach { status ->
            listOf(null, kollega).forEach { ident ->
                assertNull(tildelingHindring(status, ident, saksbehandler), "status=$status ident=$ident")
            }
        }
    }

    @Test
    fun `sak som ikke venter på saksbehandler gir UGYLDIG_STATUS`() {
        listOf(
            Saksstatus.TIL_BESLUTNING,
            Saksstatus.INNVILGET,
            Saksstatus.AVSLATT,
            Saksstatus.AVSLUTTET,
        ).forEach {
            assertEquals(TildelingHindring.UGYLDIG_STATUS, tildelingHindring(it, kollega, saksbehandler), "status=$it")
        }
    }

    @Test
    fun `uten saksbehandlerrolle gir MANGLER_ROLLE`() {
        assertEquals(
            TildelingHindring.MANGLER_ROLLE,
            tildelingHindring(Saksstatus.OPPRETTET, null, principal(meg, Role.BESLUTTER)),
        )
    }

    @Test
    fun `innlogget har saken allerede gir HAR_SAKEN_ALLEREDE`() {
        assertEquals(
            TildelingHindring.HAR_SAKEN_ALLEREDE,
            tildelingHindring(Saksstatus.UNDER_BEHANDLING, meg, saksbehandler),
        )
    }
}
