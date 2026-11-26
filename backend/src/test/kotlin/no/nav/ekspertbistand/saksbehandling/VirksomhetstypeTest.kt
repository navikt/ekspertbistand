package no.nav.ekspertbistand.saksbehandling

import no.nav.ekspertbistand.saksbehandling.Virksomhetstype.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VirksomhetstypeTest {

    @Test
    fun `sektorkodene mappes til virksomhetstype`() {
        val forventet = mapOf(
            "1110" to STATLIG, "1120" to PRIVAT, "1510" to KOMMUNAL, "1520" to KOMMUNAL,
            "2100" to PRIVAT, "2300" to PRIVAT, "2500" to PRIVAT, "3100" to STATLIG,
            "3200" to PRIVAT, "3500" to PRIVAT, "3600" to PRIVAT, "3900" to STATLIG,
            "4100" to PRIVAT, "4300" to PRIVAT, "4500" to PRIVAT, "4900" to PRIVAT,
            "5500" to PRIVAT, "5700" to PRIVAT, "6100" to STATLIG, "6500" to KOMMUNAL,
            "7000" to PRIVAT, "8200" to PRIVAT, "8300" to PRIVAT, "8500" to UKJENT,
            "9000" to PRIVAT,
        )

        assertEquals(forventet, forventet.mapValues { (kode, _) -> Virksomhetstype.fraSektorkode(kode) })
    }

    @Test
    fun `manglende eller ukjent sektorkode gir UKJENT`() {
        listOf(null, "", "1234").forEach {
            assertEquals(UKJENT, Virksomhetstype.fraSektorkode(it), "sektorkode=$it")
        }
    }
}
