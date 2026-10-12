package no.nav.ekspertbistand.infrastruktur

import io.ktor.server.testing.*
import org.flywaydb.core.Flyway
import java.sql.DriverManager
import java.sql.Statement
import java.util.*
import kotlin.time.Duration.Companion.hours

class TestDatabase private constructor(
    val dbName: String,
    private val slettVedClose: Boolean,
) : AutoCloseable {

    constructor(dbName: String = "ekspertbistand") : this(dbName, slettVedClose = false)

    val config: DbConfig = DbConfig(
        url = jdbcUrl(dbName),
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

    /**
     * Lukker poolen hvis migreringen feiler. Uten dette lekker en feilende migrering (f.eks. to
     * migreringsfiler med samme versjon) én åpen pool per test, og etterfølgende tester feiler med
     * "sorry, too many clients already" i stedet for den faktiske årsaken.
     */
    fun cleanMigrate(): TestDatabase {
        try {
            config.flywayAction {
                clean()
                migrate()
            }
        } catch (e: Throwable) {
            runCatching { config.close() }
            throw e
        }
        return this
    }

    override fun close() {
        config.close()
        if (slettVedClose) slett(dbName)
    }

    companion object {
        private const val PREFIKS = "eb_test_"

        fun jdbcUrl(dbName: String) = "jdbc:postgresql://localhost:5532/$dbName?user=postgres&password=postgres"

        /**
         * Gir testen sin egen database, kopiert fra en mal som migreres én gang per JVM.
         * `CREATE DATABASE ... TEMPLATE` tar ~10 ms, mot ~200 ms for clean() + migrate() per test.
         * Unike navn gjør også at samtidige kjøringer (f.eks. IDE og mvn) ikke sletter hverandres skjema.
         */
        fun isolert(): TestDatabase {
            val navn = uniktNavn()
            admin { execute("""CREATE DATABASE "$navn" TEMPLATE "$mal"""") }
            // Exposed bruker sist tilkoblede database når transaction {} kalles uten database.
            // Kobles det ikke til her, treffer slike kall forrige tests lukkede pool.
            return TestDatabase(navn, slettVedClose = true).also { it.config.jdbcDatabase }
        }

        private val mal: String by lazy {
            slettEtterlatte()
            val navn = uniktNavn()
            admin { execute("""CREATE DATABASE "$navn"""") }
            TestDatabase(navn, slettVedClose = false).use { it.config.flywayAction { migrate() } }
            Runtime.getRuntime().addShutdownHook(Thread { slett(navn) })
            navn
        }

        private fun uniktNavn() =
            "$PREFIKS${System.currentTimeMillis() / 1000}_${UUID.randomUUID().toString().take(8)}"

        /** Databaser fra kjøringer som ble drept før shutdown-hooken rakk å rydde. */
        private fun slettEtterlatte() {
            val grense = System.currentTimeMillis() / 1000 - 24.hours.inWholeSeconds
            val gamle = mutableListOf<String>()
            admin {
                executeQuery("SELECT datname FROM pg_database WHERE datname LIKE '${PREFIKS}%'").use { rs ->
                    while (rs.next()) gamle += rs.getString(1)
                }
            }
            gamle.filter { (it.removePrefix(PREFIKS).substringBefore('_').toLongOrNull() ?: 0) < grense }
                .forEach(::slett)
        }

        private fun slett(navn: String) =
            admin { execute("""DROP DATABASE IF EXISTS "$navn" WITH (FORCE)""") }

        private fun admin(block: Statement.() -> Unit) {
            val url = DbUrl(jdbcUrl("postgres"))
            DriverManager.getConnection(url.jdbcUrl, url.username, url.password).use { conn ->
                conn.createStatement().use(block)
            }
        }
    }
}

/**
 * Databasen lukkes først etter at testApplication er stoppet. Ellers kan bakgrunnsjobber i appen
 * (f.eks. `slettGamleUtkast` i `configureSoknadApiV1`) kjøre spørringer mot en pool som er lukket.
 */
fun testApplicationWithDatabase(
    block: suspend ApplicationTestBuilder.(testDatabase: TestDatabase) -> Unit
) = TestDatabase.isolert().use { testDatabase ->
    testApplication {
        block(testDatabase)
    }
}
