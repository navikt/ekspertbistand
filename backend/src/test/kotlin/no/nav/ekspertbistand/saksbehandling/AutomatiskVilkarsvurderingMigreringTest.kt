package no.nav.ekspertbistand.saksbehandling

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.infrastruktur.TestDatabase
import no.nav.ekspertbistand.infrastruktur.testApplicationWithDatabase
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testdatabasen er allerede migrert, så testen legger inn rader slik de så ut før V37 og kjører
 * SQL-en på nytt. UPDATE-en er idempotent.
 */
class AutomatiskVilkarsvurderingMigreringTest {

    @Test
    fun `fyller ut urørte vilkår på saker i ny saksbehandling og lar resten være`() = testApplicationWithDatabase { db ->
        val ny = lagreSak(db, KildeTilBehandling.EKSPERTBISTAND)
        val nullstilt = lagreSak(db, KildeTilBehandling.EKSPERTBISTAND)
        val medNotat = lagreSak(db, KildeTilBehandling.EKSPERTBISTAND)
        val arena = lagreSak(db, KildeTilBehandling.ARENA)
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.batchInsert(listOf(ny, nullstilt, medNotat, arena).flatMap { sak -> Vilkar.entries.map { sak to it } }) { (sak, vilkar) ->
                this[SaksvilkarTable.sakId] = sak
                this[SaksvilkarTable.vilkarId] = vilkar.name
                this[SaksvilkarTable.vurdertAvIdent] = if (sak == nullstilt) "Z123456" else null
                this[SaksvilkarTable.notat] = if (sak == medNotat) "Sjekket" else null
            }
        }

        kjorMigrering(db)

        assertEquals(
            Vilkar.entries.associateWith { if (it in automatiskOppfylteVilkar) true else null },
            godkjent(db, ny),
        )
        assertEquals(Vilkar.entries.associateWith { null }, godkjent(db, nullstilt))
        assertEquals(Vilkar.entries.associateWith { null }, godkjent(db, medNotat))
        assertEquals(Vilkar.entries.associateWith { null }, godkjent(db, arena))
    }

    @Test
    fun `endrer ikke vurderinger som allerede er gjort`() = testApplicationWithDatabase { db ->
        val sak = lagreSak(db, KildeTilBehandling.EKSPERTBISTAND)
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.batchInsert(Vilkar.entries) { vilkar ->
                this[SaksvilkarTable.sakId] = sak
                this[SaksvilkarTable.vilkarId] = vilkar.name
                this[SaksvilkarTable.godkjent] = false
            }
        }

        kjorMigrering(db)

        assertEquals(Vilkar.entries.associateWith { false }, godkjent(db, sak))
    }

    private fun kjorMigrering(db: TestDatabase) {
        val sql = checkNotNull(javaClass.getResource("/db/migration/V37__automatisk_vilkarsvurdering.sql")).readText()
        transaction(db.config.jdbcDatabase) { exec(sql) }
    }

    private fun godkjent(db: TestDatabase, sakId: UUID): Map<Vilkar, Boolean?> =
        transaction(db.config.jdbcDatabase) {
            SaksvilkarTable.selectAll().where { SaksvilkarTable.sakId eq sakId }
                .associate { Vilkar.valueOf(it[SaksvilkarTable.vilkarId]) to it[SaksvilkarTable.godkjent] }
        }

    private fun lagreSak(db: TestDatabase, kilde: KildeTilBehandling): UUID =
        transaction(db.config.jdbcDatabase) {
            val soknadId = UUID.randomUUID()
            SoknadTable.insert {
                it[id] = soknadId
                it[virksomhetsnummer] = "123456780"
                it[virksomhetsnavn] = "Eksempel Bedrift AS"
                it[kontaktpersonNavn] = ""
                it[kontaktpersonEpost] = ""
                it[kontaktpersonTelefon] = ""
                it[ansattFnr] = "12058512345"
                it[ansattNavn] = ""
                it[ekspertNavn] = ""
                it[ekspertVirksomhet] = ""
                it[ekspertKompetanse] = ""
                it[behovForBistand] = ""
                it[behovForBistandBegrunnelse] = ""
                it[behovForBistandEstimertKostnad] = "9999"
                it[behovForBistandTimer] = "16"
                it[behovForBistandTilrettelegging] = ""
                it[behovForBistandStartdato] = LocalDate(2026, 11, 22)
                it[navKontaktPerson] = ""
                it[opprettetAv] = "98765432109"
                it[status] = SoknadStatus.innsendt.name
            }
            SakTable.insert {
                it[this.soknadId] = soknadId
                it[status] = Saksstatus.UNDER_BEHANDLING.name
                it[kildeTilBehandling] = kilde.name
                if (kilde == KildeTilBehandling.EKSPERTBISTAND) {
                    it[saksnummer] = nesteSaksnummer()
                }
            }[SakTable.sakId]
        }
}
