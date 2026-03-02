package no.nav.ekspertbistand

import no.nav.ekspertbistand.dokarkiv.FagsakIdTable
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val testDatabase = TestDatabase()
    transaction(testDatabase.config.jdbcDatabase) {
        MigrationUtils.generateMigrationScript(
            FagsakIdTable,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V3__fagsak_id",
        )
    }
}
