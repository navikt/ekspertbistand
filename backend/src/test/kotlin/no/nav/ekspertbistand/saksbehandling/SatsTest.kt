package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SatsTest {

    private val satser = listOf(
        MaksSats(gyldigFra = LocalDate(2026, 1, 1), belop = 24_900),
        MaksSats(gyldigFra = LocalDate(2027, 1, 1), belop = 26_000),
    )

    @Test
    fun `gir satsen på og etter gyldigFra`() {
        assertEquals(24_900, gjeldendeMaksSats(LocalDate(2026, 1, 1), satser)?.belop)
        assertEquals(24_900, gjeldendeMaksSats(LocalDate(2026, 12, 31), satser)?.belop)
        assertEquals(26_000, gjeldendeMaksSats(LocalDate(2027, 1, 1), satser)?.belop)
        assertEquals(26_000, gjeldendeMaksSats(LocalDate(2030, 6, 1), satser)?.belop)
    }

    @Test
    fun `gir null før første sats`() {
        assertNull(gjeldendeMaksSats(LocalDate(2025, 12, 31), satser))
    }

    @Test
    fun `rekkefølgen i listen spiller ingen rolle`() {
        assertEquals(26_000, gjeldendeMaksSats(LocalDate(2027, 3, 1), satser.reversed())?.belop)
    }

    @Test
    fun `MAKS_SATSER har unike gyldigFra og positive beløp`() {
        assertEquals(MAKS_SATSER.size, MAKS_SATSER.map { it.gyldigFra }.toSet().size)
        assertTrue(MAKS_SATSER.all { it.belop > 0 })
    }

    @Test
    fun `MAKS_SATSER har effektiv sats og viderefører den uten ny sats`() {
        val satsFra2026 = MaksSats(gyldigFra = LocalDate(2026, 1, 1), belop = 24_900)

        assertEquals(satsFra2026, gjeldendeMaksSats(LocalDate(2026, 1, 1)))
        assertTrue(MAKS_SATSER.none { it.gyldigFra == LocalDate(2027, 1, 1) })
        assertEquals(satsFra2026, gjeldendeMaksSats(LocalDate(2027, 1, 1)))
        assertNull(gjeldendeMaksSats(LocalDate(2025, 12, 31)))
    }
}
