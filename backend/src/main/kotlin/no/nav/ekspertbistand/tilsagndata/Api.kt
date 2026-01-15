package no.nav.ekspertbistand.tilsagndata

import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.arena.TilsagnData
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.skjema.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class TilsagnDataApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient,
    private val dokgenClient: DokgenClient,
) {
    suspend fun RoutingContext.hentTilskuddsbrevHtml(skjemaId: UUID) {
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)
        val organisasjoner = tilganger.organisasjoner

        if (organisasjoner.isEmpty()) {
            call.respond(emptyList<TilskuddsbrevHtml>())
            return
        }

        val tilsagnData = transaction(database) {
            findTilsagnDataBySkjemaId(skjemaId)
        }

        val html = tilsagnData.map { tilsagn ->
            TilskuddsbrevHtml(
                tilsagnNummer = tilsagn.tilsagnNummerKey(),
                html = dokgenClient.genererTilskuddsbrevHtml(tilsagn),
            )
        }

        call.respond(html)
    }
}

@Serializable
data class TilskuddsbrevHtml(
    val tilsagnNummer: String,
    val html: String,
)

private fun TilsagnData.tilsagnNummerKey(): String {
    return "${tilsagnNummer.aar}-${tilsagnNummer.loepenrSak}-${tilsagnNummer.loepenrTilsagn}"
}
