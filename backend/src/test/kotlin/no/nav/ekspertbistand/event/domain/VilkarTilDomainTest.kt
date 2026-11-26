package no.nav.ekspertbistand.event.domain

import no.nav.ekspertbistand.saksbehandling.Vilkar
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VilkarTilDomainTest {

    @Test
    fun `alle vilkår oversettes til domain-vilkåret med samme navn`() {
        assertEquals(
            Vilkar.entries.map { it.name },
            Vilkar.entries.map { it.tilDomain().name },
        )
        assertEquals(Vilkar.entries.size, SakDomain.Vilkarsvurdering.Vilkar.entries.size)
    }
}
