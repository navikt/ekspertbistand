package no.nav.ekspertbistand.norg

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Kommunenavn fra Norg, cachet i [ttl].
 *
 * Feiler oppslaget, brukes forrige svar, og neste forsøk venter [ventEtterFeil]. Uten forrige svar
 * er resultatet tomt, og kallerne viser kommunenummeret.
 */
@OptIn(ExperimentalTime::class)
class KommunenavnCache(
    private val norgKlient: NorgKlient,
    private val ttl: Duration = 1.days,
    private val ventEtterFeil: Duration = 5.minutes,
    private val clock: Clock = Clock.System,
) {
    private val log = logger()
    private val mutex = Mutex()
    private var kommunenavn: Map<String, String> = emptyMap()
    private var nesteOppslag: Instant = Instant.DISTANT_PAST

    suspend fun hent(): Map<String, String> = mutex.withLock {
        val naa = clock.now()
        if (naa >= nesteOppslag) {
            try {
                kommunenavn = norgKlient.hentKommunenavn()
                nesteOppslag = naa + ttl
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                log.warn("Kunne ikke hente kommunenavn fra Norg", e)
                nesteOppslag = naa + ventEtterFeil
            }
        }
        kommunenavn
    }
}
