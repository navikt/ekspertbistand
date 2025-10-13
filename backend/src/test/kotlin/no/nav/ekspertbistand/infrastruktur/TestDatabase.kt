package no.nav.ekspertbistand.infrastruktur

import java.io.Closeable

class TestDatabase(
    dbName: String = "ekspertbistand",
    connectRetries: Int = 1,
    private val cleanOnClose: Boolean = true,
) : Closeable {
    val config: DbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres",
        connectRetries = connectRetries
    ).apply {
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)
    }

    init {
        // Clean and migrate for a fresh state per test
        config.flyway.clean()
        config.flyway.migrate()
    }

    override fun close() {
        if (cleanOnClose) {
            config.flyway.clean()
        }
    }
}