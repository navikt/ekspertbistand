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

    fun cleanMigrate(): TestDatabase {
        config.flywayAction {
            clean()
            migrate()
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
    val database = TestDatabase().cleanMigrate()
    database.use { testDatabase ->
        block(testDatabase)
    }
}