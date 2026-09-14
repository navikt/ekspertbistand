package no.nav.ekspertbistand.oebs

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * 🔴 Rød sone — testskjelett. Fyll ut sammen med implementasjonen av [OebsOutboxPoller].
 */
class OutboxTest {

    @Ignore // TODO(rød sone): implementer sammen med OebsOutboxPoller
    @Test
    fun `poller publiserer PENDING-rad og markerer PUBLISHED i samme transaksjon`() {
        TODO("Verifiser at en rad lagt via leggIOutbox publiseres og markeres PUBLISHED atomisk.")
    }

    @Ignore // TODO(rød sone): implementer sammen med OebsOutboxPoller
    @Test
    fun `publiseringsfeil lar raden bli liggende for retry`() {
        TODO("Verifiser at feil i produsenten ikke markerer raden PUBLISHED, og at attempts økes.")
    }
}
