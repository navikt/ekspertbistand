package no.nav.ekspertbistand

import no.nav.ekspertbistand.arena.ArenaSakTable
import no.nav.ekspertbistand.arena.ArenaSakUnderBehandlingTable
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val testDatabase = TestDatabase()
    testDatabase.cleanMigrate()
    transaction(testDatabase.config.jdbcDatabase) {
        MigrationUtils.generateMigrationScript(
            ArenaSakTable,
            ArenaSakUnderBehandlingTable,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V4__arena_sak_under_behandling",
        )
    }
}
