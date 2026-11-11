package no.nav.ekspertbistand.vedtaksbrev

import kotlinx.serialization.json.Json
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.domain.VedtakDomain
import no.nav.ekspertbistand.event.domain.VedtaksbrevDomain
import no.nav.ekspertbistand.saksbehandling.SakTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Vedtaksbrevet til arbeidsgiver, én rad per sak. Slettes med søknaden via `sak`. */
@OptIn(ExperimentalTime::class)
object VedtaksbrevTable : Table("vedtaksbrev") {
    val sakId = javaUUID("sak_id")
    val utfall = text("utfall")
    val brevdata = json<VedtaksbrevDomain>("brevdata", Json)
    val journalfoertTidspunkt = timestamp("journalfoert_tidspunkt")
    val eventId = long("event_id")

    override val primaryKey = PrimaryKey(sakId)
}

@OptIn(ExperimentalTime::class)
data class Vedtaksbrev(
    val sakId: UUID,
    val utfall: VedtakDomain.Utfall,
    val brevdata: VedtaksbrevDomain,
    val journalfoertTidspunkt: Instant,
)

@OptIn(ExperimentalTime::class)
class VedtaksbrevDb(private val database: Database) {

    /** Idempotent: finnes saken fra før, blir den første raden stående. */
    fun lagre(eventId: Long, data: EventData.VedtaksbrevArbeidsgiverJournalfoert) {
        transaction(database) {
            VedtaksbrevTable.insertIgnore {
                it[sakId] = UUID.fromString(data.sakId)
                it[utfall] = data.utfall.name
                it[brevdata] = data.vedtaksbrev
                it[journalfoertTidspunkt] = data.tidspunkt
                it[VedtaksbrevTable.eventId] = eventId
            }
        }
    }

    fun hentForSoknad(soknadId: UUID): Vedtaksbrev? = transaction(database) {
        VedtaksbrevTable.join(SakTable, JoinType.INNER, VedtaksbrevTable.sakId, SakTable.sakId)
            .select(
                VedtaksbrevTable.sakId,
                VedtaksbrevTable.utfall,
                VedtaksbrevTable.brevdata,
                VedtaksbrevTable.journalfoertTidspunkt,
            )
            .where { SakTable.soknadId eq soknadId }
            .firstOrNull()
            ?.let {
                Vedtaksbrev(
                    sakId = it[VedtaksbrevTable.sakId],
                    utfall = VedtakDomain.Utfall.valueOf(it[VedtaksbrevTable.utfall]),
                    brevdata = it[VedtaksbrevTable.brevdata],
                    journalfoertTidspunkt = it[VedtaksbrevTable.journalfoertTidspunkt],
                )
            }
    }
}
