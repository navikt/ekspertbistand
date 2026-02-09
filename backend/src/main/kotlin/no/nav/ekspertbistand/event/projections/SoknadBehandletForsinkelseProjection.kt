package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.avlystTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.godkjentTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.innsendtTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.soknadId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class SoknadBehandletForsinkelseProjection(
    database: Database,
) : EventLogProjectionBuilder(database) {
    override val name = "SoknadBehandlet"

    override fun handle(event: Event<out EventData>, eventTimestamp: Instant) = transaction<Unit>(database) {
        when (event.data) {
            is EventData.SoknadInnsendt -> {
                SoknadBehandletForsinkelseState.insert {
                    it[soknadId] = event.data.soknad.id!!
                    it[innsendtTidspunkt] = eventTimestamp
                }
            }

            is EventData.TilskuddsbrevMottatt -> {
                SoknadBehandletForsinkelseState.update(
                    {
                        soknadId eq event.data.soknad.id!!
                    }
                ) {
                    it[godkjentTidspunkt] = eventTimestamp
                }
            }

            is EventData.SoknadAvlystIArena -> {
                SoknadBehandletForsinkelseState.update(
                    {
                        soknadId eq event.data.soknad.id!!
                    }
                ) {
                    it[avlystTidspunkt] = eventTimestamp
                }
            }

            else -> Unit
        }
    }
}

@OptIn(ExperimentalTime::class)
object SoknadBehandletForsinkelseState : Table("soknad_behandlet_forsinkelse_state") {
    val soknadId = text("soknad_id")
    val innsendtTidspunkt = timestamp("innsendt_at")
    val godkjentTidspunkt = timestamp("godkjent_at").nullable()
    val avlystTidspunkt = timestamp("avlystt_at").nullable()

    override val primaryKey = PrimaryKey(soknadId)
}

@OptIn(ExperimentalTime::class)
data class SoknadBehandletForsinkelse(
    val soknadId: String,
    val innsendtTidspunkt: Instant,
    val godkjentTidspunkt: Instant?,
    val avlystTidspunkt: Instant?,
) {
    companion object {
        fun ResultRow.tilSoknadBehandletForsinkelse() = SoknadBehandletForsinkelse(
            soknadId = this[soknadId],
            innsendtTidspunkt = this[innsendtTidspunkt],
            godkjentTidspunkt = this[godkjentTidspunkt],
            avlystTidspunkt = this[avlystTidspunkt],
        )
    }
}


