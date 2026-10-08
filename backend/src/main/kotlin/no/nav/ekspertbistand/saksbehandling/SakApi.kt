package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.audit.ArcSightAuditClient
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.saksbehandling.SakTilgangsgrunnlag.Companion.tilTilgangsgrunnlag
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.time.ExperimentalTime

/**
 * GET /api/saksbehandling/v1/saker => saker på saksbehandlers enheter (uten fnr)
 * GET /api/saksbehandling/v1/saker/{sakId} => én sak med søknaden og personopplysninger
 *
 * Krever rolle [Role.SAKSBEHANDLER] eller [Role.BESLUTTER], og at saken har en behandlende enhet
 * som saksbehandler har tilgang til (fra entra-proxy). Saker uten enhet er ikke synlige for noen.
 * Enkeltoppslag sjekkes i tillegg mot Tilgangsmaskinen og sporingslogges til ArcSight når saken
 * vises. Tilgangsmaskinen sjekker kun kjerneregler: geografisk tilgang styres av sakens
 * behandlende enhet, ikke av den ansattes geografiske tilknytning.
 * Alle eksterne tilgangssjekker er fail-closed (503).
 * Listen sporingslogges ikke, jf. krav til oppslagslogg på sikkerhet.nav.no.
 */
suspend fun Application.configureSaksbehandlingSakApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val auditClient = dependencies.resolve<ArcSightAuditClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/saker") {
                get {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val enheter = call.hentEnheterForPrincipal(principal) ?: return@get

                    val saker = transaction(database) { hentSakerForSaksbehandling(enheter) }
                    call.respond(SakerResponse(saker = saker))
                }

                get("/{sakId}") {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val sakId = call.sakIdParameter() ?: return@get

                    val sak = transaction(database) { hentSakForSaksbehandling(sakId) }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))

                    val tilgangsgrunnlag = sak.tilTilgangsgrunnlag()
                    if (!call.sjekkTilgangTilEnhet(principal, tilgangsgrunnlag)) return@get
                    if (!call.sjekkTilgangsmaskin(principal, tilgangsgrunnlag, tilgangsmaskinClient)) return@get

                    auditClient.loggOppslag(
                        navIdent = principal.navIdent,
                        fnr = sak.soknad.ansatt.fnr,
                        tillatt = true,
                        melding = "Saksbehandler har sett sak om ekspertbistand",
                    )
                    call.respond(sak)
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
fun hentSakerForSaksbehandling(enheter: Set<String>): List<SakListeElement> {
    if (enheter.isEmpty()) return emptyList()
    return SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(
            SoknadTable.id,
            SoknadTable.status,
            SoknadTable.opprettetTidspunkt,
            SoknadTable.virksomhetsnummer,
            SoknadTable.virksomhetsnavn,
            SoknadTable.ansattNavn,
            SoknadTable.behovForBistandStartdato,
            *SakTable.columns.toTypedArray(),
        )
        .where { SakTable.behandlendeEnhet inList enheter }
        .orderBy(SoknadTable.opprettetTidspunkt, SortOrder.DESC)
        .map { row ->
            SakListeElement(
                sakId = row[SakTable.sakId].toString(),
                status = Saksstatus.valueOf(row[SakTable.status]),
                kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
                behandlendeEnhet = row[SakTable.behandlendeEnhet],
                saksbehandlerIdent = row[SakTable.saksbehandlerIdent],
                beslutterIdent = row[SakTable.beslutterIdent],
                arenaSakId = row[SakTable.arenaSakId],
                soknad = SakListeElement.Soknad(
                    soknadId = row[SoknadTable.id].toString(),
                    status = SoknadStatus.valueOf(row[SoknadTable.status]),
                    innsendtTidspunkt = row[SoknadTable.opprettetTidspunkt].toString(),
                    virksomhet = SakListeElement.Virksomhet(
                        virksomhetsnummer = row[SoknadTable.virksomhetsnummer],
                        virksomhetsnavn = row[SoknadTable.virksomhetsnavn],
                    ),
                    ansattNavn = row[SoknadTable.ansattNavn],
                    startdato = row[SoknadTable.behovForBistandStartdato],
                ),
            )
        }
}

fun hentSakForSaksbehandling(sakId: UUID): SakDetaljer? =
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .selectAll()
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.let { row ->
            val soknad = row.tilSoknadDTO()
            SakDetaljer(
                sakId = sakId.toString(),
                status = Saksstatus.valueOf(row[SakTable.status]),
                kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
                behandlendeEnhet = row[SakTable.behandlendeEnhet],
                saksbehandlerIdent = row[SakTable.saksbehandlerIdent],
                beslutterIdent = row[SakTable.beslutterIdent],
                arenaSakId = row[SakTable.arenaSakId],
                soknad = SakDetaljer.Soknad(
                    soknadId = row[SoknadTable.id].toString(),
                    status = soknad.status,
                    innsendtTidspunkt = soknad.opprettetTidspunkt!!,
                    virksomhet = soknad.virksomhet,
                    ansatt = soknad.ansatt,
                    ekspert = soknad.ekspert,
                    behovForBistand = soknad.behovForBistand,
                    nav = soknad.nav,
                ),
            )
        }

@Serializable
data class SakerResponse(
    val saker: List<SakListeElement>,
)

/** Listeelement uten fødselsnummer. */
@Serializable
data class SakListeElement(
    val sakId: String,
    val status: Saksstatus,
    val kildeTilBehandling: KildeTilBehandling,
    val behandlendeEnhet: String?,
    val saksbehandlerIdent: String?,
    val beslutterIdent: String?,
    val arenaSakId: String?,
    val soknad: Soknad,
) {
    @Serializable
    data class Soknad(
        val soknadId: String,
        val status: SoknadStatus,
        val innsendtTidspunkt: String,
        val virksomhet: Virksomhet,
        val ansattNavn: String,
        val startdato: LocalDate,
    )

    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
    )
}

/** Saken slik saksbehandler ser den. Innsenders ident (`opprettetAv`) er bevisst utelatt. */
@Serializable
data class SakDetaljer(
    val sakId: String,
    val status: Saksstatus,
    val kildeTilBehandling: KildeTilBehandling,
    val behandlendeEnhet: String?,
    val saksbehandlerIdent: String?,
    val beslutterIdent: String?,
    val arenaSakId: String?,
    val soknad: Soknad,
) {
    @Serializable
    data class Soknad(
        val soknadId: String,
        val status: SoknadStatus,
        val innsendtTidspunkt: String,
        val virksomhet: DTO.Virksomhet,
        val ansatt: DTO.Ansatt,
        val ekspert: DTO.Ekspert,
        val behovForBistand: DTO.BehovForBistand,
        val nav: DTO.Nav,
    )
}

/** Svar ved avvist tilgang. Tilgangsmaskinens detaljer (inkl. brukerIdent) videresendes ikke. */
@Serializable
data class TilgangAvvistResponse(
    val kode: String?,
    val begrunnelse: String?,
)
