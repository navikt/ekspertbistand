package no.nav.ekspertbistand.infrastruktur

import io.ktor.server.testing.*
import org.flywaydb.core.Flyway

class TestDatabase(
    dbName: String = "ekspertbistand"
) : AutoCloseable {

    val config: DbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres",
    ).apply {
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)
        flywayConfig.lockRetryCount(10)
        flywayConfig.configuration(
            mapOf(
                "lockTimeout" to "30s",
                "statementTimeout" to "30s"
            )
        )
    }

    val flyway: Flyway
        get() = config.flyway

    /**
     * Lukker poolen hvis migreringen feiler. Uten dette lekker en feilende migrering (f.eks. to
     * migreringsfiler med samme versjon) én åpen pool per test, og etterfølgende tester feiler med
     * "sorry, too many clients already" i stedet for den faktiske årsaken.
     */
    fun cleanMigrate(): TestDatabase {
        try {
            config.flywayAction {
                clean()
                migrate()
            }
        } catch (e: Throwable) {
            runCatching { config.close() }
            throw e
        }
        return this
    }

    override fun close() {
        config.close()
    }
}

fun testApplicationWithDatabase(
    block: suspend ApplicationTestBuilder.(testDatabase: TestDatabase) -> Unit
) = testApplication {
    TestDatabase().use { testDatabase ->
        testDatabase.cleanMigrate()
        block(testDatabase)
    }
}
