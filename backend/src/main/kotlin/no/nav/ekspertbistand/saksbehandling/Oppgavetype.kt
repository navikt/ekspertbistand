package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal

/**
 * Oppgaven saken venter på. Utledes av status, se `specifications/listevisning.md`.
 * Refusjon og sluttrapport kommer med egne kort.
 */
@Serializable
enum class Oppgavetype {
    SOKNAD,
    SOKNAD_RETUR,
    SOKNAD_BESLUTTER,
}

/**
 * Rollen som utfører oppgaven. Styrer hvem som kan tildeles saken, og hvem saksdetaljen viser.
 * Speiles av `rolleForOppgave` i frontend.
 */
val Oppgavetype.rolle: Role
    get() = when (this) {
        Oppgavetype.SOKNAD, Oppgavetype.SOKNAD_RETUR -> Role.SAKSBEHANDLER
        Oppgavetype.SOKNAD_BESLUTTER -> Role.BESLUTTER
    }

/**
 * Null betyr at saken ikke har noen oppgave. [harRetur] er om saken har en rad i `sak_retur`.
 * En sak er bare `UNDER_BEHANDLING` med retur etter at beslutter har sendt den tilbake.
 */
fun oppgavetype(status: Saksstatus, harRetur: Boolean): Oppgavetype? = when (status) {
    Saksstatus.OPPRETTET -> Oppgavetype.SOKNAD
    Saksstatus.UNDER_BEHANDLING -> if (harRetur) Oppgavetype.SOKNAD_RETUR else Oppgavetype.SOKNAD
    Saksstatus.TIL_BESLUTNING -> Oppgavetype.SOKNAD_BESLUTTER
    Saksstatus.INNVILGET, Saksstatus.AVSLATT, Saksstatus.AVSLUTTET -> null
}

/**
 * Om saken har en oppgave for en rolle [principal] har, uansett hvem som er tildelt saken.
 * Kolleger skal kunne se og overta hverandres saker.
 */
fun harOppgaveFor(oppgavetype: Oppgavetype?, principal: AzureAdPrincipal): Boolean =
    oppgavetype != null && principal.harRolle(oppgavetype.rolle)
