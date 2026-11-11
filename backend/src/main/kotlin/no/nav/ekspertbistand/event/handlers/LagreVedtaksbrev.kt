package no.nav.ekspertbistand.event.handlers

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventHandledResult
import no.nav.ekspertbistand.event.EventHandledResult.Companion.success
import no.nav.ekspertbistand.event.EventHandledResult.Companion.transientError
import no.nav.ekspertbistand.event.EventHandler
import no.nav.ekspertbistand.infrastruktur.logger
import no.nav.ekspertbistand.vedtaksbrev.VedtaksbrevDb
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Lagrer vedtaksbrevet når det er journalført i arbeidsgivermappa, så arbeidsgiver kan se vedtaket
 * uten at API-et slår opp i `event_log`. Se `specifications/vis_vedtak_arbeidsgiver.md`.
 *
 * Brevdataene har deltakers navn og begrunnelsen, så bare sakId logges.
 */
class LagreVedtaksbrev(
    database: Database,
) : EventHandler<EventData.VedtaksbrevArbeidsgiverJournalfoert> {
    override val id = "LagreVedtaksbrev"
    override val eventType = EventData.VedtaksbrevArbeidsgiverJournalfoert::class

    private val vedtaksbrevDb = VedtaksbrevDb(database)
    private val log = logger()

    override suspend fun handle(event: Event<EventData.VedtaksbrevArbeidsgiverJournalfoert>): EventHandledResult {
        try {
            vedtaksbrevDb.lagre(event.id, event.data)
        } catch (e: Exception) {
            return transientError("Klarte ikke lagre vedtaksbrev for sak ${event.data.sakId}", e)
        }
        log.info("Lagret vedtaksbrev for sak {}", event.data.sakId)
        return success()
    }
}
