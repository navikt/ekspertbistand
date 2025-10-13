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

    val config: DbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres",
        connectRetries = connectRetries
    ).apply {
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)
    }

    companion object {
        suspend fun create(
            dbName: String = "ekspertbistand",
            connectRetries: Int = 1,
            cleanOnClose: Boolean = true
        ): TestDatabase {
            val instance = TestDatabase(dbName, connectRetries, cleanOnClose)
            withContext(Dispatchers.IO) {
                instance.config.flyway.clean()
                instance.config.flyway.migrate()
            }
            return instance
        }
    }

    override fun close() {
        if (cleanOnClose) {
            runBlocking {
                withContext(Dispatchers.IO) {
                    config.flyway.clean()
                }
            }
        }
    }
}