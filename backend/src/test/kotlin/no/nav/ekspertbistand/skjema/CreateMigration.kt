package no.nav.ekspertbistand.skjema

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.migration.r2dbc.MigrationUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

@OptIn(ExperimentalDatabaseMigrationApi::class)
suspend fun main() {
    suspendTransaction(TestDatabase.config.database) {
        MigrationUtils.generateMigrationScript(
            SkjemaTable,
            UtkastTable,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V1__init_skjema",
        )
    }
}
