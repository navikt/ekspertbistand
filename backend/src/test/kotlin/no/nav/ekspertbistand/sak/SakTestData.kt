package no.nav.ekspertbistand.sak

import no.nav.ekspertbistand.soknad.SoknadTable
import org.jetbrains.exposed.v1.datetime.CurrentDate
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class LagretSak(val sakId: UUID, val soknadId: UUID)

const val TEST_ANSATT_FNR = "22420094160"

/** Lagrer en minimal søknad med tilhørende sak, for tester som trenger en rad i `sak`. */
fun lagreSoknadOgSak(database: Database, ansattFnr: String = TEST_ANSATT_FNR): LagretSak = transaction(database) {
    val soknadId = UUID.randomUUID()
    SoknadTable.insert {
        it[id] = soknadId
        it[virksomhetsnummer] = "987654321"
        it[virksomhetsnavn] = "Testbedrift AS"
        it[opprettetAv] = "42"
        it[behovForBistand] = ""
        it[behovForBistandTilrettelegging] = ""
        it[behovForBistandBegrunnelse] = ""
        it[behovForBistandEstimertKostnad] = "42"
        it[behovForBistandTimer] = "9"
        it[behovForBistandStartdato] = CurrentDate
        it[kontaktpersonNavn] = ""
        it[kontaktpersonEpost] = ""
        it[kontaktpersonTelefon] = ""
        it[SoknadTable.ansattFnr] = ansattFnr
        it[ansattNavn] = ""
        it[ekspertNavn] = ""
        it[ekspertVirksomhet] = ""
        it[ekspertKompetanse] = ""
        it[navKontaktPerson] = ""
        it[status] = "innsendt"
    }
    val sakId = SakTable.insertReturning(listOf(SakTable.sakId)) {
        it[SakTable.soknadId] = soknadId
        it[status] = Saksstatus.OPPRETTET.name
        it[kildeTilBehandling] = KildeTilBehandling.EKSPERTBISTAND.name
    }.single()[SakTable.sakId]
    LagretSak(sakId = sakId, soknadId = soknadId)
}

@OptIn(ExperimentalTime::class)
fun lagreSaksloggInnslag(
    database: Database,
    sakId: UUID,
    rolle: AktorRolle,
    ident: String?,
    notat: String,
    tidspunkt: Instant,
) = transaction(database) {
    SaksloggTable.insert {
        it[SaksloggTable.sakId] = sakId
        it[utfortAvRolle] = rolle.name
        it[utfortAvIdent] = ident
        it[SaksloggTable.notat] = notat
        it[utfortAt] = tidspunkt
    }
}
