package no.nav.ekspertbistand.saksbehandling

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RolesTest {

    @Test
    fun `mapper groups til roller`() {
        val groups = listOf("0000-CA-Ekspertbistand_Saksbehandler", "0000-CA-Ekspertbistand_Beslutter")
        val roles = Role.fromGroups(groups)
        assertEquals(setOf(Role.SAKSBEHANDLER, Role.BESLUTTER), roles)
    }

    @Test
    fun `ukjente groups ignoreres`() {
        val roles = Role.fromGroups(listOf("unknown-group-id"))
        assertEquals(emptySet(), roles)
    }

    @Test
    fun `tom groups gir tomme roller`() {
        val roles = Role.fromGroups(emptyList())
        assertEquals(emptySet(), roles)
    }
}

