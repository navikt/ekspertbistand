package no.nav.ekspertbistand.saksbehandling

import no.nav.ekspertbistand.entraproxy.Enhet
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
internal fun principal(navIdent: String, vararg roller: Role) = AzureAdPrincipal(
    navIdent = navIdent,
    navn = "Navn $navIdent",
    epost = null,
    gjeldendeEnhet = Enhet("1234", "Nav Test"),
    groups = roller.map { it.groupId },
    enheter = listOf(Enhet("1234", "Nav Test")),
    berikelseUpdatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    subjectToken = "token",
)

class OppgavetypeTest {

    @Test
    fun `oppgavetype per status`() {
        assertEquals(
            mapOf(
                Saksstatus.OPPRETTET to Oppgavetype.SOKNAD,
                Saksstatus.UNDER_BEHANDLING to Oppgavetype.SOKNAD,
                Saksstatus.TIL_BESLUTNING to Oppgavetype.SOKNAD_BESLUTTER,
                Saksstatus.INNVILGET to null,
                Saksstatus.AVSLATT to null,
                Saksstatus.AVSLUTTET to null,
            ),
            Saksstatus.entries.associateWith { oppgavetype(it) },
        )
    }

    @Test
    fun `harOppgaveFor følger rollen`() {
        val saksbehandler = principal("A1", Role.SAKSBEHANDLER)
        val beslutter = principal("A2", Role.BESLUTTER)
        val begge = principal("A3", Role.SAKSBEHANDLER, Role.BESLUTTER)

        assertTrue(harOppgaveFor(Oppgavetype.SOKNAD, saksbehandler))
        assertFalse(harOppgaveFor(Oppgavetype.SOKNAD_BESLUTTER, saksbehandler))
        assertFalse(harOppgaveFor(Oppgavetype.SOKNAD, beslutter))
        assertTrue(harOppgaveFor(Oppgavetype.SOKNAD_BESLUTTER, beslutter))
        assertTrue(harOppgaveFor(Oppgavetype.SOKNAD, begge))
        assertTrue(harOppgaveFor(Oppgavetype.SOKNAD_BESLUTTER, begge))
        assertFalse(harOppgaveFor(null, begge))
    }
}
