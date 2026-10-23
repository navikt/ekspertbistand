package no.nav.ekspertbistand.statistikk

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdressebeskyttelseskontrollTest {

    private val registrerte = mutableListOf<String>()
    private val register = object : AdressebeskyttedeBehandlinger {
        override fun registrer(behandlingIder: Collection<String>) {
            registrerte += behandlingIder
        }

        override fun utenAdressebeskyttelse() = mapOf(
            "b1" to "11111111111",
            "b2" to "22222222222",
            "b3" to "22222222222",
            "b4" to "33333333333",
        )
    }

    @Test
    fun `registrerer alle behandlingene til personer som har faatt adressebeskyttelse`() = runTest {
        Adressebeskyttelseskontroll(register) { fnr -> fnr == "22222222222" }.kjor()

        assertEquals(listOf("b2", "b3"), registrerte)
    }

    @Test
    fun `feil i ett oppslag hindrer ikke registrering av de andre, men kontrollen feiler`() = runTest {
        val kontroll = Adressebeskyttelseskontroll(register) { fnr ->
            if (fnr == "11111111111") error("PDL er nede")
            fnr == "33333333333"
        }

        assertFailsWith<IllegalStateException> { kontroll.kjor() }
        assertEquals(listOf("b4"), registrerte)
    }
}
