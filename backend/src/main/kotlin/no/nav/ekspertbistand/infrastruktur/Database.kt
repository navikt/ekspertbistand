package no.nav.ekspertbistand.infrastruktur

import io.ktor.http.*
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.FluentConfiguration
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import java.net.URI

class DbConfig(
    val url: String,
    val connectRetries: Int,
) {
    companion object {
        fun nais() = DbConfig(
            url = System.getenv("DB_JDBC_URL")!!,
            connectRetries = 7
        )
    }

    /**
     * Nais env var `DB_JDBC_URL` format does not match expected r2dbc format, so we need to parse and reformat.
     * expected format: r2dbc:driver[:protocol]://[user:password@]host[:port][/path][?option=value]
     * example: r2dbc:postgresql://host:5432/database
     * NAIS format: jdbc:postgresql://100.10.1.0:5432/mydb?password=...&user=...&sslcert=...
     */
    val dbUrl = DbUrl(url)

    val database by lazy {
        R2dbcDatabase.connect(
            driver = "postgresql",
            url = dbUrl.r2dbcUrl,
            user = dbUrl.username,
            password = dbUrl.password,
        )
    }

    val flywayConfig: FluentConfiguration by lazy {
        Flyway.configure()
            .initSql("select 1")
            .dataSource(dbUrl.jdbcUrl, dbUrl.username, dbUrl.password)
            .locations("db/migration") // default = db/migration, just being explicit
            .connectRetries(connectRetries) // time waited after retries ca: 1=1s, 5=31s, 6=63s, 7=127s
    }

    val flyway: Flyway by lazy {
        flywayConfig.load()
    }
}

suspend fun Application.configureDatabase() = with(dependencies.resolve<DbConfig>()) {
    withContext(Dispatchers.IO) {
        flyway.migrate()
    }
    database
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