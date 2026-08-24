package no.nav.ekspertbistand.vedlegg

import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertReturning
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
}

enum class VedleggType { SLUTTRAPPORT, REFUSJONSDOKUMENTASJON }

