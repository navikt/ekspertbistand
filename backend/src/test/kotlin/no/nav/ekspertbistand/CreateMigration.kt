package no.nav.ekspertbistand

import no.nav.ekspertbistand.arena.ArenaMeldingIdempotencyTable
import no.nav.ekspertbistand.arena.ArenaSakTable
import no.nav.ekspertbistand.event.EventHandlerStates
import no.nav.ekspertbistand.event.EventLog
import no.nav.ekspertbistand.event.IdempotencyGuardRecords
import no.nav.ekspertbistand.event.QueuedEvents
import no.nav.ekspertbistand.event.projections.ProjectionBuilderState
import no.nav.ekspertbistand.event.projections.SoknadBehandletForsinkelseState
import no.nav.ekspertbistand.event.projections.TilskuddsbrevVistState
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.UtkastTable
import no.nav.ekspertbistand.tilsagndata.TilsagndataTable
import org.jetbrains.exposed.v1.core.ExperimentalDatabaseMigrationApi
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

@OptIn(ExperimentalDatabaseMigrationApi::class)
fun main() {
    val testDatabase = TestDatabase()
    transaction(testDatabase.config.jdbcDatabase) {
        MigrationUtils.generateMigrationScript(
            ArenaMeldingIdempotencyTable,
            scriptDirectory = "backend/src/main/resources/db/migration",
            scriptName = "V2__arena_melding_idempotency",
        )
    }
}
