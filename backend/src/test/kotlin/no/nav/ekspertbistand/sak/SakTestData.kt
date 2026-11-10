package no.nav.ekspertbistand.sak

import kotlinx.datetime.LocalDate
import no.nav.ekspertbistand.saksbehandling.AktorRolle
import no.nav.ekspertbistand.saksbehandling.ForelopigUtfall
import no.nav.ekspertbistand.saksbehandling.ForelopigVedtakTable
import no.nav.ekspertbistand.saksbehandling.KildeTilBehandling
import no.nav.ekspertbistand.saksbehandling.SakTable
import no.nav.ekspertbistand.saksbehandling.SaksloggTable
import no.nav.ekspertbistand.saksbehandling.Saksstatus
import no.nav.ekspertbistand.saksbehandling.VedtakTable
import no.nav.ekspertbistand.saksbehandling.nesteSaksnummer
import no.nav.ekspertbistand.saksbehandling.saksstatus
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
fun lagreSoknadOgSak(
    database: Database,
    ansattFnr: String = TEST_ANSATT_FNR,
    behandlendeEnhet: String? = null,
): LagretSak = transaction(database) {
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
        it[saksnummer] = nesteSaksnummer()
        it[SakTable.behandlendeEnhet] = behandlendeEnhet
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

/**
 * Lagrer et foreløpig vedtak og beslutters vedtak på saken. [innvilgetBelop] er null ved avslag.
 * Må kalles i en transaksjon.
 */
@OptIn(ExperimentalTime::class)
fun lagreFattetVedtak(
    sakId: UUID,
    utfall: ForelopigUtfall,
    innvilgetBelop: Int?,
) {
    val tidspunkt = Instant.parse("2026-11-01T10:00:00Z")
    val forelopigVedtakId = ForelopigVedtakTable.insert {
        it[ForelopigVedtakTable.sakId] = sakId
        it[versjon] = 1
        it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
        it[ForelopigVedtakTable.utfall] = utfall.name
        it[ForelopigVedtakTable.innvilgetBelop] = innvilgetBelop
        it[tiltaksperiodeFra] = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 11, 1)
        it[tiltaksperiodeTil] = if (utfall == ForelopigUtfall.AVSLAA) null else LocalDate(2026, 12, 31)
        it[begrunnelseDeltaker] = "Til deltaker"
        it[begrunnelseArbeidsgiver] = if (utfall == ForelopigUtfall.INNVILGE) null else "Til arbeidsgiver"
        it[innstiltAvIdent] = "Z111111"
        it[sendtTidspunkt] = tidspunkt
    }[ForelopigVedtakTable.id]
    VedtakTable.insert {
        it[VedtakTable.sakId] = sakId
        it[VedtakTable.forelopigVedtakId] = forelopigVedtakId
        it[VedtakTable.utfall] = utfall.saksstatus.name
        it[fattetAvIdent] = "Z222222"
        it[fattetAvNavn] = "Beslutter"
        it[fattetTidspunkt] = tidspunkt
        it[eventId] = Random().nextLong(1, Long.MAX_VALUE)
    }
}
