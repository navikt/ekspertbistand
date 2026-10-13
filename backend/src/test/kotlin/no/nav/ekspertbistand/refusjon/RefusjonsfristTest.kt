package no.nav.ekspertbistand.refusjon

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RefusjonsfristTest {

    @Test
    fun `fristen er seks måneder etter sluttdato`() {
        assertEquals(LocalDate(2026, 12, 30), refusjonsfrist(LocalDate(2026, 6, 30)))
        assertEquals(LocalDate(2027, 6, 30), refusjonsfrist(LocalDate(2026, 12, 30)))
    }

    @Test
    fun `dato som ikke finnes i fristmåneden gir siste dag i måneden`() {
        assertEquals(LocalDate(2027, 2, 28), refusjonsfrist(LocalDate(2026, 8, 31)))
        assertEquals(LocalDate(2028, 2, 29), refusjonsfrist(LocalDate(2027, 8, 31)))
    }
}
