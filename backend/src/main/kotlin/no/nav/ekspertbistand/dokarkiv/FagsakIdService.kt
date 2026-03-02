package no.nav.ekspertbistand.dokarkiv

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class FagsakIdService(
    val database: Database
) {

    fun opprettEllerHentFagsakId(soknadId: String): String = transaction(database) {
        FagsakIdTable.insertIgnore {
            it[FagsakIdTable.soknadId] = soknadId
        }

        transaction {
            FagsakIdTable.selectAll()
                .where { FagsakIdTable.soknadId eq soknadId }
                .map { it[FagsakIdTable.fagsakId] }
                .first().toString()
        }
    }
}


object FagsakIdTable : Table("fagsak_id_mapping") {
    val soknadId = text("soknad_id")
    val fagsakId = long("fagsak_id").autoIncrement()

    override val primaryKey = PrimaryKey(soknadId)
}