package no.nav.ekspertbistand.oebs

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * 🔴 Rød sone — testskjelett. Fyll ut sammen med implementasjonen av [nesteBestillingsnummer].
 *
 * Bruk `testApplicationWithDatabase { testDb -> ... }` (se
 * no.nav.ekspertbistand.infrastruktur.TestDatabase) og kjør i en `transaction { }`.
 */
class NummerserieTest {

    @Ignore // TODO(rød sone): implementer sammen med nesteBestillingsnummer
    @Test
    fun `deler ut sekvensielle nummer per sak`() {
        TODO("Verifiser at nesteBestillingsnummer gir E<sak>1, E<sak>2, ... og starter på nytt per sak.")
    }

    @Ignore // TODO(rød sone): implementer sammen med nesteBestillingsnummer
    @Test
    fun `to samtidige kall deler aldri ut samme nummer`() {
        TODO("Verifiser transaksjonssikkerhet under samtidighet (radlås på OebsLopenummer).")
    }
}
