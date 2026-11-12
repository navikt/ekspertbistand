package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.dokdist.Distribusjonstidspunkt
import no.nav.ekspertbistand.dokdist.Distribusjonstype
import no.nav.ekspertbistand.dokdist.DokDistClient
import no.nav.ekspertbistand.event.*
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.IdempotencyGuard.Companion.idempotencyGuard
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.reflect.KClass

private const val publiserDistribuertEventSubtask = "soknad_kopi_deltaker_distribuert_event"

/**
 * Distribuerer kopien av søknaden til deltakeren med dokdist, når den er journalført på personmappen
 * av [JournalfoerSoknadKopiDeltaker].
 *
 * Dokdist velger kanal ut fra KRR (digitalt eller papirbrev). Distribusjonen holdes tilbake til
 * kjernetid, slik at deltakeren ikke får varsel om natten. Svarer dokdist 409, er journalposten
 * allerede distribuert, og [DokDistClient] gir bestillingsId fra svaret. Et nytt forsøk gir derfor
 * ikke dobbel utsending.
 *
 * Etter distribusjonen publiseres [EventData.SoknadKopiDeltakerDistribuert] med bestillingsId.
 */
class DistribuerSoknadKopiDeltaker(
    private val dokDistClient: DokDistClient,
    private val database: Database,
) : EventHandler<EventData.SoknadKopiDeltakerJournalfoert> {
    override val id: String = "Distribuer Soknad Kopi Deltaker"
    override val eventType: KClass<EventData.SoknadKopiDeltakerJournalfoert> =
        EventData.SoknadKopiDeltakerJournalfoert::class

    private val idempotencyGuard = idempotencyGuard(database)

    override suspend fun handle(event: Event<EventData.SoknadKopiDeltakerJournalfoert>): EventHandledResult {
        if (idempotencyGuard.isGuarded(event.id, publiserDistribuertEventSubtask)) {
            return success()
        }

        val journalpostId = event.data.journalpostId

        val response = runCatching {
            dokDistClient.distribuerJournalpost(
                journalpostId = journalpostId.toString(),
                distribusjonstype = Distribusjonstype.ANNET,
                distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID,
            )
        }.getOrElse { e ->
            return transientError("Feil ved distribusjon av kopi av søknad til deltaker", e)
        }

        transaction(database) {
            publishEventQueue(
                EventData.SoknadKopiDeltakerDistribuert(
                    soknad = event.data.soknad,
                    journalpostId = journalpostId,
                    bestillingsId = response.bestillingsId,
                ),
            )
        }

        idempotencyGuard.guard(event, publiserDistribuertEventSubtask)

        return success()
    }
}
