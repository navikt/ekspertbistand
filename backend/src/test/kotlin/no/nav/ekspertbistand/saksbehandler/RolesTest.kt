package no.nav.ekspertbistand.saksbehandler

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RolesTest {

    @Test
    fun `mapper groups til roller`() {
        val groups = listOf("test-saksbehandler-group-id", "test-beslutter-group-id")
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

