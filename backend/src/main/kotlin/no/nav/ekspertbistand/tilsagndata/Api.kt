package no.nav.ekspertbistand.tilsagndata

import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.skjema.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class TilsagnDataApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient
) {
    suspend fun RoutingContext.hentTilsagnData(skjemaId: UUID) {
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val organisasjoner = tilganger.organisasjoner

        if (organisasjoner.isEmpty()) {
            call.respond(emptyList<TilsagnData>())
            return
        }

        val tilsagnData = transaction(database) {
            findTilsagnDataBySkjemaId(skjemaId)
        }
        call.respond(tilsagnData)
    }
}