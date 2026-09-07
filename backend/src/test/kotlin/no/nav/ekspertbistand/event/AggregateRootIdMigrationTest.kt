package no.nav.ekspertbistand.event

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2 i spesifikasjonen: `V9__aggregate_root_id.sql` legger til en nullbar `aggregate_root_id`-kolonne
 * på `event_queue` og `event_log`, samt `backfill_state`-tabellen.
 *
 * Testen migrerer først til V8 (før kolonnen fantes), legger inn en legacy-rad, og migrerer så videre
 * til V9. Det verifiserer både at ADD COLUMN kjører rent på en base med eksisterende rader, og at
 * kolonnen er nullbar — de eksisterende radene beholder null uten at migreringen feiler.
 */
class AggregateRootIdMigrationTest {
    private lateinit var testDb: TestDatabase
    private val config get() = testDb.config

    @BeforeTest
    fun setup() {
        testDb = TestDatabase()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `V9 legger til nullbar aggregate_root_id og bevarer eksisterende rader`() {
        // 1. Migrer til V8 — siste versjon før aggregate_root_id fantes.
        config.flywayAction { clean() }
        config.flywayConfig.target(MigrationVersion.fromVersion("8")).load().migrate()

        // 2. Legg inn en legacy-rad uten aggregate_root_id. Rå SQL fordi Exposed-modellen nå
        //    kjenner aggregate_root_id-kolonnen, som ikke finnes ennå på V7.
        val legacyId = transaction(config.jdbcDatabase) {
            exec(
                "INSERT INTO event_queue (event_json) VALUES ('{}'::json) RETURNING id",
                explicitStatementType = StatementType.SELECT,
            ) { rs ->
                rs.next(); rs.getLong(1)
            }!!
        }

        // 3. Migrer resten (V9).
        config.flywayConfig.target(MigrationVersion.LATEST).load().migrate()

        transaction(config.jdbcDatabase) {
            // Kolonnen finnes og er nullbar på begge tabellene.
            assertEquals("YES", nullbarhet("event_queue", "aggregate_root_id"))
            assertEquals("YES", nullbarhet("event_log", "aggregate_root_id"))

            // Backfill-state-tabellen finnes.
            assertTrue(tabellFinnes("backfill_state"), "backfill_state skal være opprettet")

            // Legacy-raden overlevde migreringen og har null aggregate_root_id.
            val legacyMedNull = exec(
                "SELECT count(*) FROM event_queue WHERE id = $legacyId AND aggregate_root_id IS NULL"
            ) { rs -> if (rs.next()) rs.getLong(1) else 0L }
            assertEquals(1L, legacyMedNull, "eksisterende rad skal beholdes med null aggregate_root_id")
        }
    }

    private fun JdbcTransaction.nullbarhet(tabell: String, kolonne: String): String? =
        exec(
            """
            SELECT is_nullable FROM information_schema.columns
            WHERE table_name = '$tabell' AND column_name = '$kolonne'
            """.trimIndent()
        ) { rs -> if (rs.next()) rs.getString(1) else null }

    private fun JdbcTransaction.tabellFinnes(tabell: String): Boolean =
        exec("SELECT to_regclass('public.$tabell') IS NOT NULL") { rs ->
            rs.next() && rs.getBoolean(1)
        } ?: false
}
