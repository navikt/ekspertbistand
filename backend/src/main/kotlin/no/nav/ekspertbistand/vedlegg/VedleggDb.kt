package no.nav.ekspertbistand.vedlegg

import org.jetbrains.exposed.v1.core.SortOrder
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
}

class VedleggDb(private val database: Database) {

    fun lagreVedlegg(
        soknadId: UUID,
        type: VedleggType,
        filnavn: String,
        innhold: ByteArray,
    ): UUID = transaction(database) {
        VedleggTable.insertReturning {
            it[VedleggTable.soknadId] = soknadId
            it[VedleggTable.type] = type.name
            it[VedleggTable.filnavn] = filnavn
            it[VedleggTable.innhold] = innhold
            it[VedleggTable.storrelse] = innhold.size
        }.single()[VedleggTable.id].value
    }

    /**
     * Henter kun metadata for sluttrapporten (filnavn + tidspunkt) – aldri selve
     * filinnholdet, som kan være personsensitivt. `innhold`-kolonnen leses bevisst ikke.
     */
    @OptIn(kotlin.time.ExperimentalTime::class)
    fun finnSluttrapportMetadata(soknadId: UUID): SluttrapportMetadata? = transaction(database) {
        VedleggTable
            .select(VedleggTable.filnavn, VedleggTable.lastetOpp)
            .where {
                (VedleggTable.soknadId eq soknadId) and
                    (VedleggTable.type eq VedleggType.SLUTTRAPPORT.name)
            }
            .orderBy(VedleggTable.lastetOpp, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
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

