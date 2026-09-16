package no.nav.ekspertbistand.oebs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.oebs.model.nesteBestillingsnummer
import no.nav.ekspertbistand.oebs.model.nesteFakturanummer
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 🔴 Rød sone — tester for den økonomikritiske nummerserie-genereringen.
 *
 * Krever den lokale test-Postgres-en (`backend/docker-compose.yml`, port 5532); kjøres i CI.
 */
class NummerserieTest {

    @Test
    fun `deler ut sekvensielle nummer per sak`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val sakA = "2026/10000"
        val sakB = "2026/20000"

        val nrA1 = transaction(database) { nesteBestillingsnummer(sakA) }
        val nrA2 = transaction(database) { nesteBestillingsnummer(sakA) }
        val nrB1 = transaction(database) { nesteBestillingsnummer(sakB) }

        assertEquals("E-2026/10000-1", nrA1)
        assertEquals("E-2026/10000-2", nrA2)
        // Egen serie per sak: sak B starter på nytt på 1.
        assertEquals("E-2026/20000-1", nrB1)
    }

    @Test
    fun `to samtidige kall deler aldri ut samme nummer`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val sak = "2026/33333"
        val antall = 20

        // Kjør mange nummer-uttak samtidig, hvert i sin egen transaksjon. Radlåsen (FOR UPDATE)
        // skal serialisere dem slik at vi får nøyaktig 1..antall uten dubletter eller hull.
        val numre = runBlocking(Dispatchers.IO) {
            (1..antall).map {
                async { transaction(database) { nesteBestillingsnummer(sak) } }
            }.awaitAll()
        }

        assertEquals(antall, numre.toSet().size, "alle nummer skal være unike")
        assertEquals((1..antall).map { "E-$sak-$it" }.toSet(), numre.toSet())
    }

    @Test
    fun `for langt bestillingsnummer avvises og ruller tilbake`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        // "E-" + sak + "-1" > 20 tegn: saksnummer på 21 tegn gir 25 tegn totalt.
        val forLangSak = "2026/1234567890123456"

        assertThrows<IllegalArgumentException> {
            transaction(database) { nesteBestillingsnummer(forLangSak) }
        }

        // Lengdesjekken kaster inne i transaksjonen, så inkrementeringen rulles tilbake:
        // et gyldig senere kall for en annen sak er upåvirket og starter på 1.
        val gyldig = transaction(database) { nesteBestillingsnummer("2026/40000") }
        assertEquals("E-2026/40000-1", gyldig)
    }

    @Test
    fun `deler ut sekvensielle fakturanummer per bestilling`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val bestillingA = "E-2026/10000-1"
        val bestillingB = "E-2026/10000-2"

        val fA1 = transaction(database) { nesteFakturanummer(bestillingA) }
        val fA2 = transaction(database) { nesteFakturanummer(bestillingA) }
        val fB1 = transaction(database) { nesteFakturanummer(bestillingB) }

        assertEquals("E-2026/10000-1-1", fA1)
        assertEquals("E-2026/10000-1-2", fA2)
        // Egen serie per bestilling: bestilling B starter på nytt på 1.
        assertEquals("E-2026/10000-2-1", fB1)
    }

    @Test
    fun `to samtidige fakturakall deler aldri ut samme nummer`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        val bestilling = "E-2026/55555-1"
        val antall = 20

        val numre = runBlocking(Dispatchers.IO) {
            (1..antall).map {
                async { transaction(database) { nesteFakturanummer(bestilling) } }
            }.awaitAll()
        }

        assertEquals(antall, numre.toSet().size, "alle fakturanummer skal være unike")
        assertEquals((1..antall).map { "$bestilling-$it" }.toSet(), numre.toSet())
    }

    @Test
    fun `for langt fakturanummer avvises og ruller tilbake`() = testApplicationWithDatabase { db ->
        val database = db.config.jdbcDatabase
        // Bestillingsnummer så langt at "<bestilling>-1" overstiger 50 tegn.
        val forLangBestilling = "E-2026/" + "0".repeat(60)

        assertThrows<IllegalArgumentException> {
            transaction(database) { nesteFakturanummer(forLangBestilling) }
        }

        val gyldig = transaction(database) { nesteFakturanummer("E-2026/60000-1") }
        assertEquals("E-2026/60000-1-1", gyldig)
    }
}

