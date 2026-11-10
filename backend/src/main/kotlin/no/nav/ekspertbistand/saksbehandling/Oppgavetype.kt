package no.nav.ekspertbistand.saksbehandling

import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Oppgaven saken venter på. Utledes av status og innsendinger, se `specifications/listevisning.md`
 * og `specifications/oversikt_refusjon_sluttrapport.md`.
 */
@Serializable
enum class Oppgavetype {
    SOKNAD,
    SOKNAD_RETUR,
    SOKNAD_BESLUTTER,
    REFUSJON,
    SLUTTRAPPORT,
}

/**
 * Rollen som utfører oppgaven. Styrer hvem som kan tildeles saken, og hvem saksdetaljen viser.
 * Speiles av `rolleForOppgave` i frontend.
 */
val Oppgavetype.rolle: Role
    get() = when (this) {
        Oppgavetype.SOKNAD, Oppgavetype.SOKNAD_RETUR, Oppgavetype.REFUSJON, Oppgavetype.SLUTTRAPPORT -> Role.SAKSBEHANDLER
        Oppgavetype.SOKNAD_BESLUTTER -> Role.BESLUTTER
    }

/**
 * Om oppgaven gjelder søknaden. Bare søknaden har beslutter så langt, så listen viser beslutteren
 * bare på disse radene. Refusjon og sluttrapport får beslutter med «Refusjon - beslutter».
 */
val Oppgavetype.gjelderSoknad: Boolean
    get() = when (this) {
        Oppgavetype.SOKNAD, Oppgavetype.SOKNAD_RETUR, Oppgavetype.SOKNAD_BESLUTTER -> true
        Oppgavetype.REFUSJON, Oppgavetype.SLUTTRAPPORT -> false
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

/** Når arbeidsgiver sendte inn det som venter. Null betyr ingen innsending med status `MOTTATT`. */
@OptIn(ExperimentalTime::class)
data class MottatteInnsendinger(val refusjonskrav: Instant?, val sluttrapport: Instant?) {
    companion object {
        val INGEN = MottatteInnsendinger(refusjonskrav = null, sluttrapport = null)
    }
}

/** [mottatt] er når innsendingen kom. Null for søknadsoppgaver, der søknaden gir datoen. */
@OptIn(ExperimentalTime::class)
data class Oppgave(val type: Oppgavetype, val mottatt: Instant?)

/**
 * Oppgavene saken venter på, med refusjon først. En innvilget sak fra ny løsning får én oppgave per
 * mottatt innsending. Arena-saker behandles i Arena og får ikke disse oppgavene. Ellers er det
 * søknadsoppgaven fra [oppgavetype], eller ingen.
 */
@OptIn(ExperimentalTime::class)
fun oppgaver(
    status: Saksstatus,
    kilde: KildeTilBehandling,
    harRetur: Boolean,
    innsendinger: MottatteInnsendinger,
): List<Oppgave> {
    val fraInnsendinger = when (kilde) {
        KildeTilBehandling.ARENA -> emptyList()
        KildeTilBehandling.EKSPERTBISTAND ->
            if (status != Saksstatus.INNVILGET) emptyList()
            else listOfNotNull(
                innsendinger.refusjonskrav?.let { Oppgave(Oppgavetype.REFUSJON, it) },
                innsendinger.sluttrapport?.let { Oppgave(Oppgavetype.SLUTTRAPPORT, it) },
            )
    }
    if (fraInnsendinger.isNotEmpty()) return fraInnsendinger
    return listOfNotNull(oppgavetype(status, harRetur)?.let { Oppgave(it, mottatt = null) })
}

/**
 * Om saken har en oppgave for en rolle [principal] har, uansett hvem som er tildelt saken.
 * Kolleger skal kunne se og overta hverandres saker.
 */
fun harOppgaveFor(oppgavetype: Oppgavetype?, principal: AzureAdPrincipal): Boolean =
    oppgavetype != null && principal.harRolle(oppgavetype.rolle)
