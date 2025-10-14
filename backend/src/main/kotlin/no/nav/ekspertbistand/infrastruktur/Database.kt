package no.nav.ekspertbistand.infrastruktur

import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.net.URI
import java.sql.Connection

class DbConfig(
    val url: String
) {
    companion object {
        fun nais() = DbConfig(
            url = System.getenv("DB_JDBC_URL")!!
        )
    }

    /**
     * Nais env var `DB_JDBC_URL` format does not match expected r2dbc format, so we need to parse and reformat.
     * expected format: r2dbc:driver[:protocol]://[user:password@]host[:port][/path][?option=value]
     * example: r2dbc:postgresql://host:5432/database
     * NAIS format: jdbc:postgresql://100.10.1.0:5432/mydb?password=...&user=...&sslcert=...
     */
    val dbUrl = DbUrl(url)

    /**
     * mixing r2dbc with jdbc causes transactions issues due to the internal implementation og exposed sticking
     * the last used transaction manager. And since we are using flyway which needs jdbc, we cannot use r2dbc here.
     * If we want to use r2dbc, we need to find another way to run database migrations.
     */
    //val r2dbcDatabase by lazy {
    //    R2dbcDatabase.connect(
    //        url = dbUrl.r2dbcUrl,
    //        databaseConfig = R2dbcDatabaseConfig {
    //            defaultMaxAttempts = 1
    //            defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
    //
    //            connectionFactoryOptions {
    //                option(ConnectionFactoryOptions.USER, dbUrl.username)
    //                option(ConnectionFactoryOptions.PASSWORD, dbUrl.password)
    //            }
    //        }
    //    )
    //}

    val jdbcDatabase by lazy {
        Database.connect(
            url = url,
            databaseConfig = DatabaseConfig {
                defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED
            },
        )
    }

    val flywayConfig: FluentConfiguration by lazy {
        Flyway.configure()
            .initSql("select 1")
            .dataSource(url, dbUrl.username, dbUrl.password)
            .locations("db/migration") // default = db/migration, just being explicit
            .connectRetries(7) // time waited after retries ca: 1=1s, 5=31s, 6=63s, 7=127s
    }

    val flyway: Flyway by lazy {
        flywayConfig.load()
    }

    fun flywayAction(
        action: Flyway.() -> Unit
    ) {
        transaction(jdbcDatabase) {
            flyway.action()
        }
    }
}


/**
 * Utility class to aid in constructing and manipulating a jdbc url.
 */
class DbUrl(
    url: String
) {

    /**
     * we need to strip the jdbc: part by using schemeSpecificPart
     * so that URI is able to parse correctly.
     * the jdbc: prefix is added back in toString()
     */
    private val uri = URLBuilder(
        URI(url).also {
            require(it.scheme == "jdbc") { "not a jdbc url: $url" }
        }.schemeSpecificPart
    ).build()

    private val urlParameters = uri.encodedQuery.split('&').associate {
        val parts = it.split('=')
        val name = parts.firstOrNull() ?: ""
        val value = parts.drop(1).firstOrNull() ?: ""
        Pair(name, value)
    }

    val username = urlParameters["user"]!!
    val password = urlParameters["password"]!!
    val database = uri.encodedPath.split('/').last()
    val host = uri.host
    val port = uri.port
    val jdbcUrl = "jdbc:postgresql://$host:$port/$database"
    val r2dbcUrl = "r2dbc:postgresql://$host:$port/$database"

    override fun toString() = "jdbc:$uri"
}

suspend fun Application.configureDatabase() {
    val dbConfig = dependencies.resolve<DbConfig>()

    dbConfig.flywayAction {
        migrate()
    }

    dependencies {
        // mixing r2dbc and jdbc does not work well together, so we use only jdbc for now
        //provide<R2dbcDatabase> {
        //    dbConfig.r2dbcDatabase
        //}
        provide<Database> {
            dbConfig.jdbcDatabase
        }
    }
}