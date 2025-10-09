package no.nav.ekspertbistand.infrastruktur

object TestDatabase {
    val config by lazy {
        DbConfig(
            url = "jdbc:postgresql://localhost:5532/ekspertbistand?user=postgres&password=postgres",
            connectRetries = 1
        )
    }

    fun initialize() = config.apply {
        // TODO: make test specific database per test
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)

        flyway.clean()
        flyway.migrate()
    }
}