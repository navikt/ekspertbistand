package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal

/**
 * Oppgaven saken venter på. Utledes av status, se `specifications/listevisning.md`.
 * Refusjon, sluttrapport og retur kommer med egne kort.
 */
@Serializable
enum class Oppgavetype {
    SOKNAD,
    SOKNAD_BESLUTTER,
}

/**
 * Rollen som utfører oppgaven. Styrer hvem som kan tildeles saken, og hvem saksdetaljen viser.
 * Speiles av `rolleForOppgave` i frontend.
 */
val Oppgavetype.rolle: Role
    get() = when (this) {
        Oppgavetype.SOKNAD -> Role.SAKSBEHANDLER
        Oppgavetype.SOKNAD_BESLUTTER -> Role.BESLUTTER
    }

/** Null betyr at saken ikke har noen oppgave. */
fun oppgavetype(status: Saksstatus): Oppgavetype? = when (status) {
    Saksstatus.OPPRETTET, Saksstatus.UNDER_BEHANDLING -> Oppgavetype.SOKNAD
    Saksstatus.TIL_BESLUTNING -> Oppgavetype.SOKNAD_BESLUTTER
    Saksstatus.INNVILGET, Saksstatus.AVSLATT, Saksstatus.AVSLUTTET -> null
}

/**
 * Om saken har en oppgave for en rolle [principal] har, uansett hvem som er tildelt saken.
 * Kolleger skal kunne se og overta hverandres saker.
 */
fun harOppgaveFor(oppgavetype: Oppgavetype?, principal: AzureAdPrincipal): Boolean =
    oppgavetype != null && principal.harRolle(oppgavetype.rolle)
