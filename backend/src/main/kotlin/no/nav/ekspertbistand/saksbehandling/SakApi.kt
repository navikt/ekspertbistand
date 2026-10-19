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
 * POST /api/saksbehandling/v1/saker/{sakId}/tildeling/beslutter => innlogget tildeler seg saken som beslutter (202)
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
 * Tildeling som beslutter krever [Role.BESLUTTER], de samme tilgangssjekkene og at innlogget ikke
 * sendte det foreløpige vedtaket, se `specifications/listevisning.md`.
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

                    post("/beslutter") {
                        val principal = call.principalMedRolle(Role.BESLUTTER) ?: return@post
                        val sak = call.hentSakForTildeling(database, tilgangsmaskinClient, principal) ?: return@post

                        when (beslutterTildelingHindring(sak.status, sak.beslutterIdent, sak.innstiltAvIdent, principal)) {
                            null -> {
                                transaction(database) {
                                    publishEventQueue(
                                        EventData.SakTildeltBeslutter(
                                            sakId = sak.sakId.toString(),
                                            soknadId = sak.soknadId.toString(),
                                            beslutterIdent = principal.navIdent,
                                            beslutterNavn = principal.navn,
                                            tidspunkt = Clock.System.now(),
                                        )
                                    )
                                }
                                log.info("Tildeling av beslutter publisert for sakId={}", sak.sakId)
                                call.respond(HttpStatusCode.Accepted)
                            }

                            BeslutterTildelingHindring.HAR_SAKEN_ALLEREDE -> {
                                log.info("Tildeling av beslutter for sakId={}: innlogget har saken allerede", sak.sakId)
                                call.respond(HttpStatusCode.Accepted)
                            }

                            BeslutterTildelingHindring.UGYLDIG_STATUS -> {
                                log.info("Tildeling av beslutter for sakId={} avvist: ugyldig status", sak.sakId)
                                call.respond(
                                    HttpStatusCode.Conflict,
                                    mapOf(
                                        "kode" to SAK_UGYLDIG_STATUS,
                                        "message" to "Saken kan ikke tildeles beslutter med status ${sak.status}",
                                    ),
                                )
                            }

                            BeslutterTildelingHindring.INHABIL -> {
                                log.info("Tildeling av beslutter for sakId={} avvist: inhabil", sak.sakId)
                                call.respond(
                                    HttpStatusCode.Forbidden,
                                    TilgangAvvistResponse(
                                        kode = BESLUTTER_INHABIL,
                                        begrunnelse = "Du sendte det foreløpige vedtaket og kan ikke være beslutter på saken",
                                    ),
                                )
                            }

                            BeslutterTildelingHindring.MANGLER_ROLLE ->
                                call.respond(HttpStatusCode.Forbidden, mapOf("message" to "krever rolle beslutter"))
                        }
                    }
                }
            }
        }
    }
}

private val log = LoggerFactory.getLogger("SaksbehandlingSakApi")

const val SAK_UGYLDIG_STATUS = "SAK_UGYLDIG_STATUS"

enum class TildelingHindring {
    MANGLER_ROLLE,
    UGYLDIG_STATUS,
    HAR_SAKEN_ALLEREDE,
}

/**
 * Den ene regelen for om innlogget kan tildele seg en sak. Brukes av `POST …/tildeling` og til
 * `kanTildeleMeg` i listen og detaljoppslaget, slik at knappen og API-et svarer likt.
 * Enhetstilgang og Tilgangsmaskinen sjekkes utenfor, fordi listen og oppslaget gjør det ulikt.
 * Saken må vente på en oppgave for saksbehandler. Innvilgede saker kan derfor ikke tildeles før
 * refusjon får egen oppgavetype, se `specifications/tildeling_detaljvisning.md`.
 * Returnerer null når innlogget kan tildele seg saken, ellers første hindring.
 */
fun tildelingHindring(
    status: Saksstatus,
    saksbehandlerIdent: String?,
    principal: AzureAdPrincipal,
): TildelingHindring? = when {
    !principal.harRolle(Role.SAKSBEHANDLER) -> TildelingHindring.MANGLER_ROLLE
    oppgavetype(status, harRetur = false)?.rolle != Role.SAKSBEHANDLER -> TildelingHindring.UGYLDIG_STATUS
    saksbehandlerIdent == principal.navIdent -> TildelingHindring.HAR_SAKEN_ALLEREDE
    else -> null
}

enum class BeslutterTildelingHindring {
    MANGLER_ROLLE,
    UGYLDIG_STATUS,
    INHABIL,
    HAR_SAKEN_ALLEREDE,
}

/**
 * Den ene regelen for om innlogget kan tildele seg en sak som beslutter. Brukes av
 * `POST …/tildeling/beslutter` og til `kanTildeleMegSomBeslutter`, slik at knappen og API-et
 * svarer likt. [innstiltAvIdent] er fra siste foreløpige vedtak. Mangler den, avviser vi
 * (fail-closed). Returnerer null når innlogget kan tildele seg saken, ellers første hindring.
 */
fun beslutterTildelingHindring(
    status: Saksstatus,
    beslutterIdent: String?,
    innstiltAvIdent: String?,
    principal: AzureAdPrincipal,
): BeslutterTildelingHindring? = when {
    !principal.harRolle(Role.BESLUTTER) -> BeslutterTildelingHindring.MANGLER_ROLLE
    status != Saksstatus.TIL_BESLUTNING -> BeslutterTildelingHindring.UGYLDIG_STATUS
    innstiltAvIdent == null || innstiltAvIdent == principal.navIdent -> BeslutterTildelingHindring.INHABIL
    beslutterIdent == principal.navIdent -> BeslutterTildelingHindring.HAR_SAKEN_ALLEREDE
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
    val rader = SakTable
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
        .toList()
    val sakIder = rader.map { it[SakTable.sakId] }
    val vedtak = hentSisteForelopigVedtakSammendrag(sakIder)
    val sakerMedRetur = hentSakerMedRetur(sakIder)

    return rader.map { row ->
        val status = Saksstatus.valueOf(row[SakTable.status])
        val saksbehandlerIdent = row[SakTable.saksbehandlerIdent]
        val beslutterIdent = row[SakTable.beslutterIdent]
        val sisteVedtak = vedtak[row[SakTable.sakId]]
        val oppgavetype = oppgavetype(status, harRetur = row[SakTable.sakId] in sakerMedRetur)
        SakListeElement(
            sakId = row[SakTable.sakId].toString(),
            status = status,
            kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
            behandlendeEnhet = row[SakTable.behandlendeEnhet],
            saksbehandlerIdent = saksbehandlerIdent,
            saksbehandlerNavn = row[SakTable.saksbehandlerNavn],
            beslutterIdent = beslutterIdent,
            beslutterNavn = row[SakTable.beslutterNavn],
            arenaSakId = row[SakTable.arenaSakId],
            oppgavetype = oppgavetype,
            harOppgaveForMeg = harOppgaveFor(oppgavetype, principal),
            // Listen viser ikke «Tildel meg» på saker tildelt en annen. Overtakelse gjøres fra saksdetaljen.
            kanTildeleMeg = saksbehandlerIdent == null &&
                    tildelingHindring(status, saksbehandlerIdent, principal) == null,
            // Unntaket er en beslutter som selv har sendt siste foreløpige vedtak, og dermed er inhabil.
            kanTildeleMegSomBeslutter = (beslutterIdent == null || beslutterIdent == sisteVedtak?.innstiltAvIdent) &&
                    beslutterTildelingHindring(status, beslutterIdent, sisteVedtak?.innstiltAvIdent, principal) == null,
            tiltaksperiodeFra = sisteVedtak?.tiltaksperiodeFra,
            tiltaksperiodeTil = sisteVedtak?.tiltaksperiodeTil,
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
            val beslutterIdent = row[SakTable.beslutterIdent]
            val forelopigVedtak = hentSisteForelopigVedtak(sakId)
            val sisteRetur = hentSisteRetur(sakId)
            SakDetaljer(
                sakId = sakId.toString(),
                status = status,
                kildeTilBehandling = KildeTilBehandling.valueOf(row[SakTable.kildeTilBehandling]),
                behandlendeEnhet = row[SakTable.behandlendeEnhet],
                saksbehandlerIdent = saksbehandlerIdent,
                saksbehandlerNavn = row[SakTable.saksbehandlerNavn],
                beslutterIdent = beslutterIdent,
                beslutterNavn = row[SakTable.beslutterNavn],
                arenaSakId = row[SakTable.arenaSakId],
                oppgavetype = oppgavetype(status, harRetur = sisteRetur != null),
                kanTildeleMeg = tildelingHindring(status, saksbehandlerIdent, principal) == null,
                kanTildeleMegSomBeslutter = beslutterTildelingHindring(
                    status, beslutterIdent, forelopigVedtak?.innstiltAvIdent, principal,
                ) == null,
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
                forelopigVedtak = forelopigVedtak,
                sisteRetur = sisteRetur,
                vedtak = hentVedtak(sakId),
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
    val beslutterNavn: String?,
    val arenaSakId: String?,
    /** Null når saken ikke har noen oppgave. Frontend viser da saksstatusen. */
    val oppgavetype: Oppgavetype?,
    val harOppgaveForMeg: Boolean,
    val kanTildeleMeg: Boolean,
    val kanTildeleMegSomBeslutter: Boolean,
    /** Fra siste foreløpige vedtak. Null uten vedtak og ved avslag. */
    val tiltaksperiodeFra: LocalDate?,
    val tiltaksperiodeTil: LocalDate?,
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
    val beslutterNavn: String?,
    val arenaSakId: String?,
    val oppgavetype: Oppgavetype?,
    val kanTildeleMeg: Boolean,
    val kanTildeleMegSomBeslutter: Boolean,
    val soknad: Soknad,
    val forelopigVedtak: ForelopigVedtakDTO?,
    /** Nyeste retur fra beslutter. Eldre returer ligger i saksloggen. */
    val sisteRetur: ReturDTO?,
    /** Null til beslutter har fattet vedtaket. */
    val vedtak: VedtakDTO?,
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
