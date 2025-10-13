package no.nav.ekspertbistand.infrastruktur

import org.flywaydb.core.Flyway

class TestDatabase(
    dbName: String = "ekspertbistand"
) {

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

    fun clean(): TestDatabase {
        config.flywayAction {
            clean()
            migrate()
        }
        return this
    }
}