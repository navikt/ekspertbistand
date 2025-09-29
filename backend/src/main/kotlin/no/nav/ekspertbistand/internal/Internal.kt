package no.nav.ekspertbistand.internal

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.ekspertbistand.infrastruktur.Health
import no.nav.ekspertbistand.infrastruktur.Metrics

object Internal {
    fun Application.internal() {
        routing {
            route("/internal") {
                get("prometheus") {
                    call.respond<String>(Metrics.meterRegistry.scrape())
                }
                get("isalive") {
                    call.response.status(
                        if (Health.alive)
                            HttpStatusCode.OK
                        else
                            HttpStatusCode.ServiceUnavailable
                    )
                }
                get("isready") {
                    call.response.status(
                        if (Health.ready)
                            HttpStatusCode.OK
                        else
                            HttpStatusCode.ServiceUnavailable
                    )
                }
            }
        }
    }
}