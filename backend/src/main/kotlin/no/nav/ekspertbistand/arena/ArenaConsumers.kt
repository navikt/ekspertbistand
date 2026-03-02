package no.nav.ekspertbistand.arena

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import no.nav.ekspertbistand.infrastruktur.basedOnEnv
import java.time.Instant
import kotlin.coroutines.CoroutineContext

val startKafkaProsesseringAt: Instant = basedOnEnv(
    other = { Instant.EPOCH },
    prod = { Instant.parse("2026-02-23T10:00:00.00Z") }
)

fun Application.startKafkaConsumers(parentContext: CoroutineContext) {

    // Arena Tilsagnsbrev Processor
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        ArenaTilsagnsbrevProcessor(
            dependencies.resolve(),
            startKafkaProsesseringAt
        ).startProcessing()
    }

    // Arena TiltaksgjennomforingEndret Processor
    CoroutineScope(parentContext + Dispatchers.IO.limitedParallelism(1)).launch {
        ArenaTiltaksgjennomforingEndretProcessor(
            dependencies.resolve(),
            startKafkaProsesseringAt
        ).startProcessing()
    }

    // ..
}