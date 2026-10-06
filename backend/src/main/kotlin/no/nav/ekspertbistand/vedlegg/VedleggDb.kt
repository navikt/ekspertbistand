package no.nav.ekspertbistand.vedlegg

import no.nav.ekspertbistand.saksbehandling.hentGodkjentSakIdForUpdate
import no.nav.ekspertbistand.saksbehandling.SakTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(kotlin.time.ExperimentalTime::class)
object VedleggTable : UUIDTable("vedlegg") {
    val soknadId = uuid("soknad_id")
    val type = text("type")
    val filnavn = text("filnavn")
    val innhold = binary("innhold")
    val storrelse = integer("storrelse")
    val lastetOpp = timestamp("lastet_opp").defaultExpression(CurrentTimestamp)
    val refusjonskravId = uuid("refusjonskrav_id").nullable()
    val sluttrapportId = uuid("sluttrapport_id").nullable()
}

@OptIn(kotlin.time.ExperimentalTime::class)
object SluttrapportTable : Table("sluttrapport") {
    val id = uuid("sluttrapport_id").databaseGenerated()
    val sakId = uuid("sak_id").nullable()
    val status = text("status")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

class VedleggDb(private val database: Database) {

    fun lagreSluttrapport(
        soknadId: UUID,
        filnavn: String,
        innhold: ByteArray,
    ): UUID = transaction(database) {
        val sakId = hentGodkjentSakIdForUpdate(soknadId)
        val sluttrapportId = SluttrapportTable.insertReturning {
            it[SluttrapportTable.sakId] = sakId
            it[status] = "MOTTATT"
        }.single()[SluttrapportTable.id]

        VedleggTable.insertReturning {
            it[VedleggTable.soknadId] = soknadId
            it[VedleggTable.type] = VedleggType.SLUTTRAPPORT.name
            it[VedleggTable.filnavn] = filnavn
            it[VedleggTable.innhold] = innhold
            it[VedleggTable.storrelse] = innhold.size
            it[VedleggTable.sluttrapportId] = sluttrapportId
        }.single()[VedleggTable.id].value
    }

    /**
     * Henter kun metadata for sluttrapporten (filnavn + tidspunkt) – aldri selve
     * filinnholdet, som kan være personsensitivt. `innhold`-kolonnen leses bevisst ikke.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun finnSluttrapportMetadata(soknadId: UUID): SluttrapportMetadata? = transaction(database) {
        val sakId = SakTable
            .select(SakTable.sakId)
            .where { SakTable.soknadId eq soknadId }
            .singleOrNull()
            ?.get(SakTable.sakId)

        val sluttrapportId = sakId?.let {
            SluttrapportTable
                .select(SluttrapportTable.id)
                .where { SluttrapportTable.sakId eq it }
                .orderBy(SluttrapportTable.opprettet, SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.get(SluttrapportTable.id)
        }

        val metadata = sluttrapportId?.let {
            VedleggTable
                .select(VedleggTable.filnavn, VedleggTable.lastetOpp)
                .where { VedleggTable.sluttrapportId eq it }
                .singleOrNull()
        }

        (metadata ?: VedleggTable
            .select(VedleggTable.filnavn, VedleggTable.lastetOpp)
            .where {
                (VedleggTable.soknadId eq soknadId) and
                    (VedleggTable.type eq VedleggType.SLUTTRAPPORT.name)
            }
            .orderBy(VedleggTable.lastetOpp, SortOrder.DESC)
            .limit(1)
            .firstOrNull())
            ?.let {
                SluttrapportMetadata(
                    filnavn = it[VedleggTable.filnavn],
                    lastetOpp = it[VedleggTable.lastetOpp].toString(),
                )
            }
    }
}

data class SluttrapportMetadata(
    val filnavn: String,
    val lastetOpp: String,
)

enum class VedleggType { SLUTTRAPPORT, REFUSJONSDOKUMENTASJON }
