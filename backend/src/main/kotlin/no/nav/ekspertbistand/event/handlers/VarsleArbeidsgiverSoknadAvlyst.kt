package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandledResult.Companion.unrecoverableError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.notifikasjon.ProdusentApiKlient
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * Når tiltaksgjennomføringen er satt til AVLYST i Arena, publiseres [EventData.SoknadAvlystIArena].
 *
 * Denne handleren sender da en beskjed med eksternt varsel og setter saken til ferdig i notifikasjonsplatformen.
 * Når begge kallene har lyktes, publiseres [EventData.ArbeidsgiverVarsletSoknadAvlyst] med det som ble sendt.
 */
class VarsleArbeidsgiverSoknadAvlyst @OptIn(ExperimentalTime::class) constructor(
    private val produsentApiKlient: ProdusentApiKlient,
    private val database: Database,
    private val clock: Clock = Clock.System,
) : EventHandler<EventData.SoknadAvlystIArena> {
    override val id: String = "Varsle Arbeidsgiver Soknad Avlyst"
    override val eventType = EventData.SoknadAvlystIArena::class

    private val nyBeskjedSubTask = "notifikasjonsplatform_ny_beskjed"
    private val nystatusSakSubTast = "notifikasjonsplatform_ny_status_sak"
    private val kvittertSubTask = "arbeidsgiver_varslet_kvittert"

    private val idempotencyGuard = idempotencyGuard(database)

    @OptIn(ExperimentalTime::class)
    override suspend fun handle(event: Event<EventData.SoknadAvlystIArena>): EventHandledResult {
        val soknadId = event.data.soknad.id ?: return unrecoverableError("soknad.id kan ikke være null")
        val varsel = arbeidsgiverVarselSoknadAvlyst(event.data.soknad)

        if (!idempotencyGuard.isGuarded(event.id, nyBeskjedSubTask)) {
            try {
                produsentApiKlient.bestillBeskjed(varsel)
            } catch (e: Exception) {
                return transientError("Klarte ikke opprette ny beskjed i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nyBeskjedSubTask)
        }

        if (!idempotencyGuard.isGuarded(event.id, nystatusSakSubTast)) {
            try {
                produsentApiKlient.bestillSaksstatus(varsel)
            } catch (e: Exception) {
                return transientError("Klarte ikke oppdatere saksstatus i notifikasjonsplatform", e)
            }
            idempotencyGuard.guard(event, nystatusSakSubTast)
        }

        return publiserKvittering(idempotencyGuard, database, event, kvittertSubTask) {
            publishEventQueue(
                EventData.ArbeidsgiverVarsletSoknadAvlyst(
                    soknadId = soknadId,
                    varsel = varsel,
                    tidspunkt = clock.now(),
                ),
            )
        }
    }
}
