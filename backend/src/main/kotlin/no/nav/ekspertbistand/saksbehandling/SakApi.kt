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
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
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
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * GET /api/saksbehandling/v1/saker => saker på saksbehandlers enheter (uten fnr)
 * GET /api/saksbehandling/v1/saker/{sakId} => én sak med søknaden og personopplysninger
 * POST /api/saksbehandling/v1/saker/{sakId}/tildeling => innlogget tildeler seg saken (202)
 * DELETE /api/saksbehandling/v1/saker/{sakId}/tildeling => innlogget frigjør saken (202)
 *
 * Krever rolle [Role.SAKSBEHANDLER] eller [Role.BESLUTTER], og at saken har en behandlende enhet
 * som saksbehandler har tilgang til (fra entra-proxy). Saker uten enhet er ikke synlige for noen.
 * Enkeltoppslag sjekkes i tillegg mot Tilgangsmaskinen og sporingslogges til ArcSight når saken
 * vises. Tilgangsmaskinen sjekker kun kjerneregler: geografisk tilgang styres av sakens
 * behandlende enhet, ikke av den ansattes geografiske tilknytning.
 * Alle eksterne tilgangssjekker er fail-closed (503).
 * Listen sporingslogges ikke, jf. krav til oppslagslogg på sikkerhet.nav.no.
 *
 * Tildeling krever [Role.SAKSBEHANDLER] og de samme tilgangssjekkene som enkeltoppslaget. Rutene
 * publiserer bare en event. Handlerne gjør endringen, se `specifications/tildel_meg_sak.md`.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureSaksbehandlingSakApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()
    val auditClient = dependencies.resolve<ArcSightAuditClient>()

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            route("/api/saksbehandling/v1/saker") {
                get {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val enheter = principal.enheter.map { it.enhetnummer }.toSet()

                    val saker = transaction(database) { hentSakerForSaksbehandling(enheter, principal) }
                    call.respond(SakerResponse(saker = saker))
                }

                get("/{sakId}") {
                    val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return@get
                    val sakId = call.sakIdParameter() ?: return@get

                    val sak = transaction(database) { hentSakForSaksbehandling(sakId, principal) }
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

                route("/{sakId}/tildeling") {
                    post {
                        val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@post
                        val sak = call.hentSakForTildeling(database, tilgangsmaskinClient, principal) ?: return@post

                        when (tildelingHindring(sak.status, sak.saksbehandlerIdent, principal)) {
                            null -> {
                                transaction(database) {
                                    publishEventQueue(
                                        EventData.SakTildeltSaksbehandler(
                                            sakId = sak.sakId.toString(),
                                            soknadId = sak.soknadId.toString(),
                                            saksbehandlerIdent = principal.navIdent,
                                            saksbehandlerNavn = principal.navn,
                                            tidspunkt = Clock.System.now(),
                                        )
                                    )
                                }
                                log.info("Tildeling publisert for sakId={}", sak.sakId)
                                call.respond(HttpStatusCode.Accepted)
                            }

                            TildelingHindring.HAR_SAKEN_ALLEREDE -> {
                                log.info("Tildeling for sakId={}: innlogget har saken allerede", sak.sakId)
                                call.respond(HttpStatusCode.Accepted)
                            }

                            TildelingHindring.UGYLDIG_STATUS -> {
                                log.info("Tildeling for sakId={} avvist: ugyldig status", sak.sakId)
                                call.respond(
                                    HttpStatusCode.Conflict,
                                    mapOf(
                                        "kode" to SAK_UGYLDIG_STATUS,
                                        "message" to "Saken kan ikke tildeles med status ${sak.status}",
                                    ),
                                )
                            }

                            TildelingHindring.MANGLER_ROLLE ->
                                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "krever rolle saksbehandler"))
                        }
                    }

                    delete {
                        val principal = call.principalMedRolle(Role.SAKSBEHANDLER) ?: return@delete
                        val sak = call.hentSakForTildeling(database, tilgangsmaskinClient, principal) ?: return@delete
                        if (!call.sjekkErSaksbehandlerPåSak(principal, sak)) return@delete

                        transaction(database) {
                            publishEventQueue(
                                EventData.SakFrigjort(
                                    sakId = sak.sakId.toString(),
                                    soknadId = sak.soknadId.toString(),
                                    saksbehandlerIdent = principal.navIdent,
                                    tidspunkt = Clock.System.now(),
                                )
                            )
                        }
                        log.info("Frigjøring publisert for sakId={}", sak.sakId)
                        call.respond(HttpStatusCode.Accepted)
                    }
                }
            }
        }
    }
}

private val log = LoggerFactory.getLogger("SaksbehandlingSakApi")

const val SAK_UGYLDIG_STATUS = "SAK_UGYLDIG_STATUS"

private val statuserSomIkkeKanTildeles = setOf(Saksstatus.AVSLATT, Saksstatus.AVSLUTTET)

enum class TildelingHindring {
    MANGLER_ROLLE,
    UGYLDIG_STATUS,
    HAR_SAKEN_ALLEREDE,
}

/**
 * Den ene regelen for om innlogget kan tildele seg en sak. Brukes av `POST …/tildeling` og til
 * `kanTildeleMeg` i listen og detaljoppslaget, slik at knappen og API-et svarer likt.
 * Enhetstilgang og Tilgangsmaskinen sjekkes utenfor, fordi listen og oppslaget gjør det ulikt.
 * Returnerer null når innlogget kan tildele seg saken, ellers første hindring.
 */
fun tildelingHindring(
    status: Saksstatus,
    saksbehandlerIdent: String?,
    principal: AzureAdPrincipal,
): TildelingHindring? = when {
    !principal.harRolle(Role.SAKSBEHANDLER) -> TildelingHindring.MANGLER_ROLLE
    status in statuserSomIkkeKanTildeles -> TildelingHindring.UGYLDIG_STATUS
    saksbehandlerIdent == principal.navIdent -> TildelingHindring.HAR_SAKEN_ALLEREDE
    else -> null
}

/** Samme tilgangssjekker som enkeltoppslaget. Svarer selv og returnerer null ved avslag. */
private suspend fun ApplicationCall.hentSakForTildeling(
    database: Database,
    tilgangsmaskinClient: TilgangsmaskinClient,
    principal: AzureAdPrincipal,
): SakTilgangsgrunnlag? {
    val sakId = sakIdParameter() ?: return null
    val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
    if (sak == null) {
        respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
        return null
    }
    if (!sjekkTilgangTilEnhet(principal, sak)) return null
    if (!sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return null
    return sak
}

@OptIn(ExperimentalTime::class)
fun hentSakerForSaksbehandling(enheter: Set<String>, principal: AzureAdPrincipal): List<SakListeElement> {
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
            val status = Saksstatus.valueOf(row[SakTable.status])
            val saksbehandlerIdent = row[SakTable.saksbehandlerIdent]
            SakListeElement(
                sakId = row[SakTable.sakId].toString(),
                status = status,
                kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
                behandlendeEnhet = row[SakTable.behandlendeEnhet],
                saksbehandlerIdent = saksbehandlerIdent,
                saksbehandlerNavn = row[SakTable.saksbehandlerNavn],
                beslutterIdent = row[SakTable.beslutterIdent],
                arenaSakId = row[SakTable.arenaSakId],
                // Listen viser ikke «Tildel meg» på saker tildelt en annen. Overtakelse gjøres fra saksdetaljen.
                kanTildeleMeg = saksbehandlerIdent == null &&
                        tildelingHindring(status, saksbehandlerIdent, principal) == null,
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

fun hentSakForSaksbehandling(sakId: UUID, principal: AzureAdPrincipal): SakDetaljer? =
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .selectAll()
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.let { row ->
            val soknad = row.tilSoknadDTO()
            val status = Saksstatus.valueOf(row[SakTable.status])
            val saksbehandlerIdent = row[SakTable.saksbehandlerIdent]
            SakDetaljer(
                sakId = sakId.toString(),
                status = status,
                kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
                behandlendeEnhet = row[SakTable.behandlendeEnhet],
                saksbehandlerIdent = saksbehandlerIdent,
                saksbehandlerNavn = row[SakTable.saksbehandlerNavn],
                beslutterIdent = row[SakTable.beslutterIdent],
                arenaSakId = row[SakTable.arenaSakId],
                kanTildeleMeg = tildelingHindring(status, saksbehandlerIdent, principal) == null,
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
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val arenaSakId: String?,
    val kanTildeleMeg: Boolean,
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
    val saksbehandlerNavn: String?,
    val beslutterIdent: String?,
    val arenaSakId: String?,
    val kanTildeleMeg: Boolean,
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
