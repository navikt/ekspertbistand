package no.nav.ekspertbistand.refusjon

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BelopTest {

    @Test
    fun `heltall kroner konverteres til ore`() {
        assertEquals(1_250_000, belopKronerTilOre("12500"))
    }

    @Test
    fun `whitespace trimmes`() {
        assertEquals(1_250_000, belopKronerTilOre("  12500 "))
    }

    @Test
    fun `null og negative belop er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("0") }
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("-100") }
    }

    @Test
    fun `desimaler er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("12500,50") }
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("12500.50") }
    }

    @Test
    fun `ugyldig format er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("tolv tusen") }
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("") }
    }

    @Test
    fun `belop over maksgrense er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKronerTilOre("1000001") }
    }
}
