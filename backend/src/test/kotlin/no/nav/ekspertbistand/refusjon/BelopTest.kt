package no.nav.ekspertbistand.refusjon

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BelopTest {

    @Test
    fun `heltall kroner parses`() {
        assertEquals(12500, belopKroner("12500"))
    }

    @Test
    fun `whitespace trimmes`() {
        assertEquals(12500, belopKroner("  12500 "))
    }

    @Test
    fun `null og negative belop er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKroner("0") }
        assertFailsWith<UgyldigBelopException> { belopKroner("-100") }
    }

    @Test
    fun `desimaler er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKroner("12500,50") }
        assertFailsWith<UgyldigBelopException> { belopKroner("12500.50") }
    }

    @Test
    fun `ugyldig format er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKroner("tolv tusen") }
        assertFailsWith<UgyldigBelopException> { belopKroner("") }
    }

    @Test
    fun `belop over maksgrense er ugyldig`() {
        assertFailsWith<UgyldigBelopException> { belopKroner("1000001") }
    }
}
