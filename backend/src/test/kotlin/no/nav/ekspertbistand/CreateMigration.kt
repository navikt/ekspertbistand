package no.nav.ekspertbistand

import no.nav.ekspertbistand.arena.ArenaSakTable
import no.nav.ekspertbistand.event.EventHandlerStates
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.IdempotencyGuardRecords
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.projections.ProjectionBuilderState
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.skjema.SkjemaTable
import no.nav.ekspertbistand.skjema.UtkastTable
import no.nav.ekspertbistand.tilsagndata.TilsagndataTable
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
            QueuedEvents,
            EventLog,
            ProjectionBuilderState,
            EventHandlerStates,
            IdempotencyGuardRecords,
            ArenaSakTable,
            TilsagndataTable,
            TilskuddsbrevVistState,
            SoknadBehandletForsinkelseState,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V1__initial_setup",
        )
    }
}
