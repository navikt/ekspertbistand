package no.nav.ekspertbistand.refusjon

import no.nav.ekspertbistand.vedlegg.VedleggTable
import no.nav.ekspertbistand.vedlegg.VedleggType
import org.jetbrains.exposed.v1.core.dao.id.UUIDTable
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

@OptIn(kotlin.time.ExperimentalTime::class)
object RefusjonskravTable : UUIDTable("refusjonskrav") {
    val soknadId = uuid("soknad_id")
    val belopOre = long("belop_ore")
    val utgifter = text("utgifter")
    val status = text("status")
    val opprettet = timestamp("opprettet").defaultExpression(CurrentTimestamp)
}

data class RefusjonsfilInput(val filnavn: String, val innhold: ByteArray)

class RefusjonDb(private val database: Database) {

    fun lagreRefusjonskrav(
        soknadId: UUID,
        belopOre: Long,
        utgifter: String,
        filer: List<RefusjonsfilInput>,
    ): UUID = transaction(database) {
        val refusjonskravId = RefusjonskravTable.insertReturning {
            it[RefusjonskravTable.soknadId] = soknadId
            it[RefusjonskravTable.belopOre] = belopOre
            it[RefusjonskravTable.utgifter] = utgifter
            it[status] = "MOTTATT"
        }.single()[RefusjonskravTable.id].value

        filer.forEach { fil ->
            VedleggTable.insert {
                it[VedleggTable.soknadId] = soknadId
                it[type] = VedleggType.REFUSJONSDOKUMENTASJON.name
                it[filnavn] = fil.filnavn
                it[innhold] = fil.innhold
                it[storrelse] = fil.innhold.size
                it[VedleggTable.refusjonskravId] = refusjonskravId
            }
        }

        refusjonskravId
    }
}
