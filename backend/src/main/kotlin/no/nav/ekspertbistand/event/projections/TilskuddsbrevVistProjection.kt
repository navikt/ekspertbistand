package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState.tilskuddsbrevFoersVistAt
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState.tilskuddsbrevOpprettetAt
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState.tilsagnNummer
import no.nav.ekspertbistand.tilsagndata.concat
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TilskuddsbrevVistProjection(
    database: Database,
) : EventLogProjectionBuilder(database) {
    override val name = "TilskuddsbrevVist"

    override fun handle(event: Event<out EventData>, eventTimestamp: Instant) = transaction<Unit>(database) {
        when (event.data) {
            is EventData.TilskuddsbrevMottatt -> {
                TilskuddsbrevVistState.insert {
                    it[tilsagnNummer] = event.data.tilsagnData.tilsagnNummer.concat()
                    it[this.tilskuddsbrevOpprettetAt] = eventTimestamp
                }
            }

            is EventData.TilskuddsbrevMottattKildeAltinn -> {
                TilskuddsbrevVistState.insert {
                    it[tilsagnNummer] = event.data.tilsagnData.tilsagnNummer.concat()
                    it[this.tilskuddsbrevOpprettetAt] = eventTimestamp
                }
            }

            is EventData.TilskuddsbrevVist -> {
                TilskuddsbrevVistState.update({
                    (tilsagnNummer eq event.data.tilsagnNummer) and (tilskuddsbrevFoersVistAt eq null)
                }) {
                    it[this.tilskuddsbrevFoersVistAt] = eventTimestamp
                }
            }

            else -> Unit
        }
    }
}

@OptIn(ExperimentalTime::class)
object TilskuddsbrevVistState : Table("tilskuddsbrev_vist_state") {
    val tilsagnNummer = text("tilsagn_nummer")
    val tilskuddsbrevOpprettetAt = timestamp("tilskuddsbrev_opprettet_at")
    val tilskuddsbrevFoersVistAt = timestamp("tilskuddsbrev_foerstvist_at").nullable()

    override val primaryKey = PrimaryKey(tilsagnNummer)
}

@OptIn(ExperimentalTime::class)
data class TilskuddsbrevVist(
    val tilsagnNummer: String,
    val tilskuddsbrevOpprettetAt: Instant,
    val tilskuddsbrevFoersVistAt: Instant?,
) {
    companion object {
        fun ResultRow.tilTilskuddsbrevVist() = TilskuddsbrevVist(
            tilsagnNummer = this[tilsagnNummer],
            tilskuddsbrevOpprettetAt = this[tilskuddsbrevOpprettetAt],
            tilskuddsbrevFoersVistAt = this[tilskuddsbrevFoersVistAt],
        )
    }
}


