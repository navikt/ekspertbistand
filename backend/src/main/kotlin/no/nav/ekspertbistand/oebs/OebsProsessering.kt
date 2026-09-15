package no.nav.ekspertbistand.oebs

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import kotlin.coroutines.CoroutineContext

/**
 * Starter bakgrunnsprosessene for OeBS-integrasjonen: outbox-polleren og status-konsumenten.
 *
 * ⚠️ IKKE wiret inn i [no.nav.ekspertbistand.Application] sin `main()` ennå. Både
 * [OebsOutboxPoller.startProcessing] og [BestillingStatusConsumer.tolkStatus] er 🔴 rød sone og
 * kaster `TODO(...)` inntil de er implementert. Koble denne på i `main()` (ved siden av
 * `startKafkaConsumers`) først når rød-sone-logikken er skrevet, ellers krasjer prosessene ved
 * oppstart.
 *
 * Mønsteret speiler `no.nav.ekspertbistand.arena.startKafkaConsumers`: én dedikert
 * single-thread-dispatcher per prosess.
 */
fun Application.startOebsProsessering(parentContext: CoroutineContext) {

    // Outbox-poller (🔴 rød sone – ikke implementert).
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        OebsOutboxPoller(dependencies.resolve<Database>(), TiltaksokonomiProducer()).startProcessing()
    }

    // Status-konsument (skjelett grønt, tolkning 🔴 rød sone).
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        BestillingStatusConsumer(dependencies.resolve<Database>()).startProcessing()
    }
}
