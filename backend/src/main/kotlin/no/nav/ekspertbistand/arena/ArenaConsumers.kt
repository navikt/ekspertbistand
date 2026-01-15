package no.nav.ekspertbistand.arena

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext


fun Application.startKafkaConsumers(parentContext: CoroutineContext) {

    // Arena Tilsagnsbrev Processor
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        dependencies.resolve<ArenaTilsagnsbrevProcessor>().startProcessing()
    }

    // Arena TiltaksgjennomforingEndret Processor
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        dependencies.resolve<ArenaTiltaksgjennomforingEndretProcessor>().startProcessing()
    }

    // ..
}