package no.nav.ekspertbistand.infrastruktur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable

class TestDatabase private constructor(
    dbName: String = "ekspertbistand",
    connectRetries: Int = 1,
    private val cleanOnClose: Boolean = true,
) : Closeable {
    private val logger = logger()

    val config: DbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres",
        connectRetries = connectRetries
    ).apply {
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)
        flywayConfig.lockRetryCount(10)
        flywayConfig.configuration(mapOf(
            "lockTimeout" to "30s",
            "statementTimeout" to "30s"
        ))
    }

    companion object {
        suspend fun create(
            dbName: String = "ekspertbistand",
            connectRetries: Int = 1,
            cleanOnClose: Boolean = true,
        ): TestDatabase {
            val instance = TestDatabase(dbName, connectRetries, cleanOnClose)
            try {
                withContext(Dispatchers.IO) {
                    instance.logger.info("Flyway clean start")
                    instance.config.flyway.clean()
                    instance.logger.info("Flyway migrate start")
                    instance.config.flyway.migrate()
                    instance.logger.info("Flyway migrate done")
                }
            } catch (e: Exception) {
                instance.logger.error("Flyway clean/migrate failed", e)
                throw e
            }
            return instance
        }
    }

    override fun close() {
        if (cleanOnClose) {
            runBlocking {
                try {
                    withContext(Dispatchers.IO) {
                        logger.info("Flyway clean on close start")
                        config.flyway.clean()
                        logger.info("Flyway clean on close done")
                    }
                } catch (e: Exception) {
                    logger.error("Flyway clean on close failed", e)
                }
            }
        }
    }
}