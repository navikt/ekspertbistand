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
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.sak.KildeTilBehandling
import no.nav.ekspertbistand.sak.SakTable
import no.nav.ekspertbistand.sak.Saksstatus
import no.nav.ekspertbistand.soknad.DTO
import no.nav.ekspertbistand.soknad.SoknadStatus
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.soknad.getRequired
import no.nav.ekspertbistand.soknad.tilSoknadDTO
import no.nav.ekspertbistand.tilgangsmaskin.Regelsett
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.tilgangsmaskin.Tilgangsresultat
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.ExperimentalTime

private val log = LoggerFactory.getLogger("SaksbehandlingSoknadApi")

/**
 * GET /api/saksbehandling/v1/soknader => alle innsendte søknader (uten fnr)
 * GET /api/saksbehandling/v1/soknader/{soknadId} => én søknad med personopplysninger
 *
 * Krever rolle [Role.SAKSBEHANDLER] eller [Role.BESLUTTER]. Enkeltoppslag sjekkes i tillegg mot
 * Tilgangsmaskinen (fail-closed) og sporingslogges til ArcSight når søknaden vises.
 * Listen sporingslogges ikke, jf. krav til oppslagslogg på sikkerhet.nav.no.
 */
suspend fun Application.configureSaksbehandlingSoknadApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val auditClient = dependencies.resolve<ArcSightAuditClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/soknader") {
                get {
                    call.saksbehandlerMedRolle() ?: return@get

                    val soknader = transaction(database) { hentSoknaderForSaksbehandling() }
                    call.respond(SoknaderResponse(soknader = soknader))
                }

                get("/{soknadId}") {
                    val principal = call.saksbehandlerMedRolle() ?: return@get

                    val soknadId = call.pathParameters.getRequired(
                        name = "soknadId",
                        transform = UUID::fromString,
                    ) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig soknadId"))
                        return@get
                    }

                    val soknad = transaction(database) { hentSoknadForSaksbehandling(soknadId) }
                        ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke søknad"))

                    val tilgang = try {
                        tilgangsmaskinClient.evaluer(
                            userToken = principal.subjectToken,
                            brukerIdent = soknad.ansatt.fnr,
                            regelsett = Regelsett.KOMPLETT,
                        )
                    } catch (e: Exception) {
                        e.rethrowIfCancellation()
                        log.error("Tilgangskontroll feilet for soknadId={}, avviser oppslag", soknadId, e)
                        return@get call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("message" to "tilgangskontroll er ikke tilgjengelig"),
                        )
                    }

                    when (tilgang) {
                        Tilgangsresultat.Innvilget -> {
                            auditClient.loggOppslag(
                                navIdent = principal.navIdent,
                                fnr = soknad.ansatt.fnr,
                                tillatt = true,
                                melding = "Saksbehandler har sett søknad om ekspertbistand",
                            )
                            call.respond(soknad)
                        }

                        is Tilgangsresultat.Avvist -> {
                            log.info("Tilgang avvist av Tilgangsmaskinen for soknadId={}", soknadId)
                            call.respond(
                                HttpStatusCode.Forbidden,
                                TilgangAvvistResponse(kode = tilgang.kode, begrunnelse = tilgang.begrunnelse),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.saksbehandlerMedRolle(): AzureAdPrincipal? {
    val principal = principal<AzureAdPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    val roller = Role.fromGroups(principal.groups)
    if (Role.SAKSBEHANDLER !in roller && Role.BESLUTTER !in roller) {
        respond(HttpStatusCode.Forbidden, mapOf("message" to "krever rolle saksbehandler eller beslutter"))
        return null
    }
    return principal
}

@OptIn(ExperimentalTime::class)
fun hentSoknaderForSaksbehandling(): List<SoknadListeElement> =
    SoknadTable
        .join(SakTable, JoinType.LEFT, SoknadTable.id, SakTable.soknadId)
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
        .orderBy(SoknadTable.opprettetTidspunkt, SortOrder.DESC)
        .map { row ->
            SoknadListeElement(
                soknadId = row[SoknadTable.id].toString(),
                soknadStatus = SoknadStatus.valueOf(row[SoknadTable.status]),
                innsendtTidspunkt = row[SoknadTable.opprettetTidspunkt].toString(),
                virksomhet = SoknadListeElement.Virksomhet(
                    virksomhetsnummer = row[SoknadTable.virksomhetsnummer],
                    virksomhetsnavn = row[SoknadTable.virksomhetsnavn],
                ),
                ansattNavn = row[SoknadTable.ansattNavn],
                startdato = row[SoknadTable.behovForBistandStartdato],
                sak = row.tilSakInfo(),
            )
        }

fun hentSoknadForSaksbehandling(soknadId: UUID): SoknadDetaljer? =
    SoknadTable
        .join(SakTable, JoinType.LEFT, SoknadTable.id, SakTable.soknadId)
        .selectAll()
        .where { SoknadTable.id eq soknadId }
        .singleOrNull()
        ?.let { row ->
            val soknad = row.tilSoknadDTO()
            SoknadDetaljer(
                soknadId = soknadId.toString(),
                soknadStatus = soknad.status,
                innsendtTidspunkt = soknad.opprettetTidspunkt!!,
                virksomhet = soknad.virksomhet,
                ansatt = soknad.ansatt,
                ekspert = soknad.ekspert,
                behovForBistand = soknad.behovForBistand,
                nav = soknad.nav,
                sak = row.tilSakInfo(),
            )
        }

private fun ResultRow.tilSakInfo(): SakInfo? =
    getOrNull(SakTable.sakId)?.let { sakId ->
        SakInfo(
            sakId = sakId.toString(),
            status = Saksstatus.valueOf(this[SakTable.status]),
            kildeTilBehandling = KildeTilBehandling.valueOf(this[SakTable.kildeTilBehandling]),
            behandlendeEnhet = this[SakTable.behandlendeEnhet],
            saksbehandlerIdent = this[SakTable.saksbehandlerIdent],
            beslutterIdent = this[SakTable.beslutterIdent],
            arenaSakId = this[SakTable.arenaSakId],
        )
    }

@Serializable
data class SoknaderResponse(
    val soknader: List<SoknadListeElement>,
)

/** Listeelement uten fødselsnummer. */
@Serializable
data class SoknadListeElement(
    val soknadId: String,
    val soknadStatus: SoknadStatus,
    val innsendtTidspunkt: String,
    val virksomhet: Virksomhet,
    val ansattNavn: String,
    val startdato: LocalDate,
    val sak: SakInfo?,
) {
    @Serializable
    data class Virksomhet(
        val virksomhetsnummer: String,
        val virksomhetsnavn: String,
    )
}

/** Søknaden slik saksbehandler ser den. Innsenders ident (`opprettetAv`) er bevisst utelatt. */
@Serializable
data class SoknadDetaljer(
    val soknadId: String,
    val soknadStatus: SoknadStatus,
    val innsendtTidspunkt: String,
    val virksomhet: DTO.Virksomhet,
    val ansatt: DTO.Ansatt,
    val ekspert: DTO.Ekspert,
    val behovForBistand: DTO.BehovForBistand,
    val nav: DTO.Nav,
    val sak: SakInfo?,
)

@Serializable
data class SakInfo(
    val sakId: String,
    val status: Saksstatus,
    val kildeTilBehandling: KildeTilBehandling,
    val behandlendeEnhet: String?,
    val saksbehandlerIdent: String?,
    val beslutterIdent: String?,
    val arenaSakId: String?,
)

/** Svar ved avvist tilgang. Tilgangsmaskinens detaljer (inkl. brukerIdent) videresendes ikke. */
@Serializable
data class TilgangAvvistResponse(
    val kode: String?,
    val begrunnelse: String?,
)
