package no.nav.ekspertbistand.infrastruktur

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.io.Closeable

class TestDatabase private constructor(
    dbName: String = "ekspertbistand",
    connectRetries: Int = 1,
    private val cleanOnClose: Boolean = true,
) : Closeable {

    private val config: DbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres",
        connectRetries = connectRetries
    ).apply {
        flywayConfig.cleanDisabled(false)
        flywayConfig.validateOnMigrate(false)
    }

    val database: R2dbcDatabase
        get() = config.database

    val flyway: Flyway
        get() = config.flyway

    suspend fun clean() = withContext(Dispatchers.IO) {
        config.flyway.clean()
        config.flyway.migrate()
    }


    companion object {
        fun create(
            dbName: String = "ekspertbistand",
            connectRetries: Int = 1,
            cleanOnClose: Boolean = true,
        ) = TestDatabase(dbName, connectRetries, cleanOnClose)
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