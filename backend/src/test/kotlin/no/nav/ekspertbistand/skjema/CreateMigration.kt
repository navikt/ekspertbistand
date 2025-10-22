package no.nav.ekspertbistand.skjema

import no.nav.ekspertbistand.event.EventHandlerStates
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val testDatabase = TestDatabase()
    testDatabase.flyway.clean()
    transaction(testDatabase.config.jdbcDatabase) {
        MigrationUtils.generateMigrationScript(
            SkjemaTable,
            UtkastTable,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V1__init_skjema",
        )
        MigrationUtils.generateMigrationScript(
            QueuedEvents,
            EventLog,
            EventHandlerStates,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V2__init_eventqueue",
        )
    }
}
