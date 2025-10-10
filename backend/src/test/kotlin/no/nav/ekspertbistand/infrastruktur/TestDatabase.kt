package no.nav.ekspertbistand.infrastruktur

object TestDatabase {
    val config by lazy {
        DbConfig(
            url = "jdbc:postgresql://localhost:5532/ekspertbistand?user=postgres&password=postgres",
            connectRetries = 1
        ).apply {
            flywayConfig.cleanDisabled(false)
            flywayConfig.validateOnMigrate(false)
        }
    }

    fun initialize() = config.apply {
        // TODO: make test specific database per test
        flyway.clean()
        flyway.migrate()
    }
}