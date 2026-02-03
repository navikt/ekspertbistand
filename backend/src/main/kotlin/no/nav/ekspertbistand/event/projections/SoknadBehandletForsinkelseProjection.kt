package no.nav.ekspertbistand.event.projections

import no.nav.ekspertbistand.event.Event
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.avlystTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.godkjentTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.innsendtTidspunkt
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState.skjemaId
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
            is EventData.SkjemaInnsendt -> {
                SoknadBehandletForsinkelseState.insert {
                    it[skjemaId] = event.data.skjema.id!!
                    it[innsendtTidspunkt] = eventTimestamp
                }
            }

            is EventData.TilskuddsbrevMottatt -> {
                SoknadBehandletForsinkelseState.update(
                    {
                        skjemaId eq event.data.skjema.id!!
                    }
                ) {
                    it[godkjentTidspunkt] = eventTimestamp
                }
            }

            is EventData.SoknadAvlystIArena -> {
                SoknadBehandletForsinkelseState.update(
                    {
                        skjemaId eq event.data.skjema.id!!
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
    val skjemaId = text("skjema_id")
    val innsendtTidspunkt = timestamp("innsendt_at")
    val godkjentTidspunkt = timestamp("godkjent_at").nullable()
    val avlystTidspunkt = timestamp("avlystt_at").nullable()

    override val primaryKey = PrimaryKey(skjemaId)
}

@OptIn(ExperimentalTime::class)
data class SoknadBehandletForsinkelse(
    val skjemaId: String,
    val innsendtTidspunkt: Instant,
    val godkjentTidspunkt: Instant?,
    val avlystTidspunkt: Instant?,
) {
    companion object {
        fun ResultRow.tilSoknadBehandletForsinkelse() = SoknadBehandletForsinkelse(
            skjemaId = this[skjemaId],
            innsendtTidspunkt = this[innsendtTidspunkt],
            godkjentTidspunkt = this[godkjentTidspunkt],
            avlystTidspunkt = this[avlystTidspunkt],
        )
    }
}


