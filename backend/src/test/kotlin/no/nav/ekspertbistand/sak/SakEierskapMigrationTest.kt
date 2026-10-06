package no.nav.ekspertbistand.sak

import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.refusjon.RefusjonskravTable
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.flywaydb.core.api.MigrationVersion
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SakEierskapMigrationTest {
    private lateinit var testDb: TestDatabase
    private val config get() = testDb.config

    @BeforeTest
    fun setup() {
        testDb = TestDatabase()
    }

    @AfterTest
    fun teardown() {
        testDb.close()
    }

    @Test
    fun `V17 legger til nullbare sakskoblinger og bevarer eksisterende rader`() {
        config.flywayAction { clean() }
        config.flywayConfig.target(MigrationVersion.fromVersion("16")).load().migrate()

        val legacyData = transaction(config.jdbcDatabase) {
            val soknadId = lagreSoknad()
            val sakId = lagreSak(soknadId)
            val refusjonskravId = LegacyRefusjonskravTable.insertReturning {
                it[LegacyRefusjonskravTable.soknadId] = soknadId
                it[belopOre] = 42_00
                it[utgifter] = "Legacy-utgift"
                it[status] = "MOTTATT"
            }.single()[LegacyRefusjonskravTable.id]
            val sluttrapportId = LegacySluttrapportTable.insertReturning {
                it[status] = "MOTTATT"
            }.single()[LegacySluttrapportTable.id]

            LegacyData(sakId, refusjonskravId, sluttrapportId)
        }

        config.flywayConfig.target(MigrationVersion.fromVersion("17")).load().migrate()

        transaction(config.jdbcDatabase) {
            assertEquals("YES", nullbarhet("refusjonskrav", "sak_id"))
            assertEquals("YES", nullbarhet("sluttrapport", "sak_id"))
            assertTrue(indeksFinnes("idx_refusjonskrav_sak_id"))
            assertTrue(indeksFinnes("idx_sluttrapport_sak_id"))

            assertNull(
                RefusjonskravSakTable
                    .selectAll()
                    .where { RefusjonskravSakTable.id eq legacyData.refusjonskravId }
                    .single()[RefusjonskravSakTable.sakId]
            )
            assertNull(
                SluttrapportSakTable
                    .selectAll()
                    .where { SluttrapportSakTable.id eq legacyData.sluttrapportId }
                    .single()[SluttrapportSakTable.sakId]
            )
        }
    }

    @Test
    fun `V17 sletter refusjonskrav og sluttrapport sammen med saken`() {
        config.flywayAction { clean() }
        config.flywayConfig.target(MigrationVersion.fromVersion("17")).load().migrate()

        val data = transaction(config.jdbcDatabase) {
            val soknadId = lagreSoknad()
            val sakId = lagreSak(soknadId)
            val refusjonskravId = RefusjonskravTable.insertReturning {
                it[RefusjonskravTable.soknadId] = soknadId
                it[belopOre] = 42_00
                it[utgifter] = "Utgift"
                it[status] = "MOTTATT"
            }.single()[RefusjonskravTable.id].value
            val sluttrapportId = LegacySluttrapportTable.insertReturning {
                it[status] = "MOTTATT"
            }.single()[LegacySluttrapportTable.id]

            RefusjonskravSakTable.update({ RefusjonskravSakTable.id eq refusjonskravId }) {
                it[RefusjonskravSakTable.sakId] = sakId
            }
            SluttrapportSakTable.update({ SluttrapportSakTable.id eq sluttrapportId }) {
                it[SluttrapportSakTable.sakId] = sakId
            }

            LegacyData(sakId, refusjonskravId, sluttrapportId)
        }

        transaction(config.jdbcDatabase) {
            SakTable.deleteWhere { SakTable.sakId eq data.sakId }

            assertTrue(
                RefusjonskravSakTable
                    .selectAll()
                    .where { RefusjonskravSakTable.id eq data.refusjonskravId }
                    .empty()
            )
            assertTrue(
                SluttrapportSakTable
                    .selectAll()
                    .where { SluttrapportSakTable.id eq data.sluttrapportId }
                    .empty()
            )
        }
    }

    private fun JdbcTransaction.lagreSoknad(): UUID {
        val soknadId = UUID.randomUUID()
        SoknadTable.insert {
            it[id] = soknadId
            it[virksomhetsnummer] = "999999999"
            it[virksomhetsnavn] = "Testbedrift"
            it[kontaktpersonNavn] = "Test"
            it[kontaktpersonEpost] = "test@example.com"
            it[kontaktpersonTelefon] = "00000000"
            it[ansattFnr] = "00000000000"
            it[ansattNavn] = "Test"
            it[ekspertNavn] = "Test"
            it[ekspertVirksomhet] = "Test"
            it[ekspertKompetanse] = "Test"
            it[behovForBistandBegrunnelse] = "Test"
            it[behovForBistand] = "Test"
            it[behovForBistandEstimertKostnad] = "42"
            it[behovForBistandTimer] = "1"
            it[behovForBistandTilrettelegging] = "Test"
            it[behovForBistandStartdato] = CurrentDate
            it[navKontaktPerson] = "Test"
            it[opprettetAv] = "Test"
            it[status] = "godkjent"
        }
        return soknadId
    }

    private fun JdbcTransaction.lagreSak(soknadId: UUID): UUID =
        SakTable.insertReturning {
            it[SakTable.soknadId] = soknadId
            it[status] = Saksstatus.OPPRETTET.name
            it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
        }.single()[SakTable.sakId]

    private fun JdbcTransaction.nullbarhet(tabell: String, kolonne: String): String? =
        exec(
            """
            SELECT is_nullable
            FROM information_schema.columns
            WHERE table_name = '$tabell' AND column_name = '$kolonne'
            """.trimIndent()
        ) { rs -> if (rs.next()) rs.getString(1) else null }

    private fun JdbcTransaction.indeksFinnes(indeks: String): Boolean =
        exec("SELECT to_regclass('public.$indeks') IS NOT NULL") { rs ->
            rs.next() && rs.getBoolean(1)
        } ?: false

    private data class LegacyData(
        val sakId: UUID,
        val refusjonskravId: UUID,
        val sluttrapportId: UUID,
    )
}

private object LegacySluttrapportTable : Table("sluttrapport") {
    val id = uuid("sluttrapport_id").databaseGenerated()
    val status = text("status")

    override val primaryKey = PrimaryKey(id)
}

private object LegacyRefusjonskravTable : Table("refusjonskrav") {
    val id = uuid("id").databaseGenerated()
    val soknadId = uuid("soknad_id")
    val belopOre = long("belop_ore")
    val utgifter = text("utgifter")
    val status = text("status")

    override val primaryKey = PrimaryKey(id)
}

private object RefusjonskravSakTable : Table("refusjonskrav") {
    val id = uuid("id")
    val sakId = uuid("sak_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object SluttrapportSakTable : Table("sluttrapport") {
    val id = uuid("sluttrapport_id")
    val sakId = uuid("sak_id").nullable()

    override val primaryKey = PrimaryKey(id)
}
