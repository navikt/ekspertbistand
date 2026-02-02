package no.nav.ekspertbistand.tilsagndata

import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.altinn.AltinnTilgangerClient
import no.nav.ekspertbistand.dokgen.DokgenClient
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.EventQueue
import no.nav.ekspertbistand.skjema.findSkjemaById
import no.nav.ekspertbistand.skjema.subjectToken
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

class TilsagnDataApi(
    private val database: Database,
    private val altinnTilgangerClient: AltinnTilgangerClient,
    private val dokgenClient: DokgenClient,
) {

    suspend fun RoutingContext.hentTilskuddsbrevHtmlForSkjema(skjemaId: UUID) {
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)

        val (skjema, tilsagnData) = transaction(database) {
            findSkjemaById(skjemaId) to findTilsagnDataBySkjemaId(skjemaId)
        }

        if (skjema == null || skjema.virksomhet.virksomhetsnummer !in tilganger.organisasjoner) {
            call.respond(emptyList<TilskuddsbrevHtml>())
            return
        }

        val html = tilsagnData.map { tilsagn ->
            EventQueue.publish(
                EventData.TilskuddsbrevVist(
                    tilsagnNummer = tilsagn.tilsagnNummer.concat(),
                    skjema = skjema,
                )
            )

            TilskuddsbrevHtml(
                tilsagnNummer = tilsagn.tilsagnNummer.concat(),
                html = dokgenClient.genererTilskuddsbrevHtml(tilsagn),
            )
        }



        call.respond(html)
    }

    suspend fun RoutingContext.hentTilskuddsbrevHtmlForTilsagnnummer(tilsagnNummer: String) {
        val tilganger = altinnTilgangerClient.hentTilganger(subjectToken)

        val tilsagnData = transaction(database) {
            findTilsagnDataByTilsagnNummer(tilsagnNummer)
        }

        if (tilsagnData == null || "${tilsagnData.tiltakArrangor.orgNummer}" !in tilganger.organisasjoner) {
            call.respond(emptyList<TilskuddsbrevHtml>())
            return
        }

        val html = TilskuddsbrevHtml(
            tilsagnNummer = tilsagnData.tilsagnNummer.concat(),
            html = dokgenClient.genererTilskuddsbrevHtml(tilsagnData),
        )

        EventQueue.publish(
            EventData.TilskuddsbrevVist(
                tilsagnNummer = tilsagnNummer,
                skjema = null,
            )
        )

        call.respond(html)
    }
}

@Serializable
data class TilskuddsbrevHtml(
    val tilsagnNummer: String,
    val html: String,
)

