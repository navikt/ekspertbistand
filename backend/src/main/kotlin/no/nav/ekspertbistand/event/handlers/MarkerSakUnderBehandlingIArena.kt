package no.nav.ekspertbistand.event.handlers

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.nav.ekspertbistand.arena.markerArenaSakUnderBehandling
import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.Metrics
import no.nav.ekspertbistand.infrastruktur.logger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*

/**
 * Markerer at saken er tatt til behandling i Arena, slik at vår egen saksbehandlingsløsning kan
 * advare om mulig dobbeltbehandling.
 *
 * Dette er en advarsel som lever ved siden av søknadens status, ikke en tilstandsovergang i vår
 * saksflyt. Søknadens status endres derfor ikke.
 */
class MarkerSakUnderBehandlingIArena(
    private val database: Database,
    meterRegistry: MeterRegistry = Metrics.meterRegistry,
) : EventHandler<EventData.SaksbehandlingStartetIArena> {

    override val id = "Marker sak under behandling i Arena"
    override val eventType = EventData.SaksbehandlingStartetIArena::class

    private val logger = logger()

    /** Rendres som `ekspertbistand_arena_sak_under_behandling_total` i Prometheus. */
    private val underBehandlingCounter: Counter = Counter
        .builder("ekspertbistand.arena.sak.under_behandling")
        .description("Antall saker observert tatt til behandling i Arena")
        .register(meterRegistry)

    override suspend fun handle(event: Event<EventData.SaksbehandlingStartetIArena>): EventHandledResult {
        val soknadId = event.data.soknad.id
            ?: return unrecoverableError("soknad.id er null, kan ikke markere sak som under behandling i Arena")

        val tiltakssakEndret = event.data.tiltakssakEndret
        val brukeridAnsvarlig = tiltakssakEndret.brukeridAnsvarlig
            ?: return unrecoverableError("BRUKERID_ANSVARLIG er null for sakId=${tiltakssakEndret.sakId}")

        return transaction(database) {
            try {
                val markert = markerArenaSakUnderBehandling(
                    sakId = tiltakssakEndret.sakId,
                    saksnummer = tiltakssakEndret.saksnummer,
                    soknadId = UUID.fromString(soknadId),
                    brukeridAnsvarlig = brukeridAnsvarlig,
                    aetatenhetAnsvarlig = tiltakssakEndret.aetatenhetAnsvarlig,
                    sakstatuskode = tiltakssakEndret.sakstatuskode?.name,
                )
                if (markert) {
                    underBehandlingCounter.increment()
                    logger.info("Sak ${tiltakssakEndret.sakId} markert som under behandling i Arena.")
                } else {
                    logger.info("Sak ${tiltakssakEndret.sakId} var allerede markert som under behandling i Arena.")
                }
                success()
            } catch (e: Exception) {
                rollback()
                transientError("Feil ved markering av sak under behandling i Arena", e)
            }
        }
    }
}
