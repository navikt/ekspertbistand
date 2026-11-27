package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.Serializable
import no.nav.ekspertbistand.event.EventData
import no.nav.ekspertbistand.event.publishEventQueue
import no.nav.ekspertbistand.infrastruktur.AZURE_AD_PROVIDER
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.UgyldigInputException
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.valider
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val log = LoggerFactory.getLogger("SaksnotatApi")

const val IKKE_EGET_NOTAT = "IKKE_EGET_NOTAT"

@Serializable
data class SaksnotatRequest(val tekst: String)

@Serializable
data class SaksnotatOpprettetResponse(val notatId: String)

/**
 * Interne arbeidsnotater på en sak:
 *
 * - `GET /api/saksbehandling/v1/saker/{sakId}/notater`
 * - `POST /api/saksbehandling/v1/saker/{sakId}/notater`
 * - `PUT /api/saksbehandling/v1/saker/{sakId}/notater/{notatId}`
 * - `DELETE /api/saksbehandling/v1/saker/{sakId}/notater/{notatId}`
 *
 * Alle ruter krever rollen [Role.SAKSBEHANDLER] eller [Role.BESLUTTER], tilgang til sakens behandlende
 * enhet og tilgang i Tilgangsmaskinen. Skriving svarer 202 Accepted og lagres asynkront av
 * [no.nav.ekspertbistand.event.handlers.OpprettSaksnotat], [no.nav.ekspertbistand.event.handlers.EndreSaksnotat]
 * og [no.nav.ekspertbistand.event.handlers.SlettSaksnotat]. Bare forfatteren kan endre eller slette et notat.
 * Arena-saker har ingen notater. Notatteksten logges ikke. Se `specifications/notat_i_saken.md`.
 */
@OptIn(ExperimentalTime::class)
suspend fun Application.configureSaksnotatApiV1() {
    val database = dependencies.resolve<Database>()
    val tilgangsmaskinClient = dependencies.resolve<TilgangsmaskinClient>()

    suspend fun RoutingContext.sakMedTilgang(): Pair<AzureAdPrincipal, SakTilgangsgrunnlag>? {
        val principal = call.principalMedRolle(Role.SAKSBEHANDLER, Role.BESLUTTER) ?: return null
        val sakId = call.sakIdParameter() ?: return null
        val sak = transaction(database) { hentSakTilgangsgrunnlag(sakId) }
        if (sak == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke sak"))
            return null
        }
        if (!call.sjekkTilgangTilEnhet(principal, sak)) return null
        if (!call.sjekkTilgangsmaskin(principal, sak, tilgangsmaskinClient)) return null
        return principal to sak
    }

    suspend fun RoutingContext.avvisArenaSak(sak: SakTilgangsgrunnlag): Boolean {
        if (sak.kildeTilBehandling != KildeTilBehandling.ARENA) return false
        call.respond(
            HttpStatusCode.Conflict,
            mapOf("kode" to SAK_UGYLDIG_KILDE, "message" to "Saken behandles i Arena"),
        )
        return true
    }

    suspend fun RoutingContext.mottaTekst(): String? {
        val request = try {
            call.receive<SaksnotatRequest>()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig notat"))
            return null
        }
        valider(request)
        val tekst = request.tekst.trim()
        if (tekst.isEmpty()) throw UgyldigInputException("tekst", "påkrevd")
        if (tekst.length > SAKSNOTAT_MAKS_LENGDE) {
            throw UgyldigInputException("tekst", "for lang (over $SAKSNOTAT_MAKS_LENGDE tegn)")
        }
        return tekst
    }

    suspend fun RoutingContext.notatIdParameter(): UUID? {
        val notatId = call.parameters["notatId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (notatId == null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "ugyldig notatId"))
        }
        return notatId
    }

    routing {
        authenticate(AZURE_AD_PROVIDER) {
            get("/api/saksbehandling/v1/saker/{sakId}/notater") {
                val (_, sak) = sakMedTilgang() ?: return@get
                val notater = if (sak.kildeTilBehandling == KildeTilBehandling.ARENA) {
                    emptyList()
                } else {
                    transaction(database) { hentSaksnotater(sak.sakId) }
                }
                call.respond(SaksnotaterResponse(notater))
            }

            post("/api/saksbehandling/v1/saker/{sakId}/notater") {
                val (principal, sak) = sakMedTilgang() ?: return@post
                if (avvisArenaSak(sak)) return@post
                val tekst = mottaTekst() ?: return@post

                val notatId = UUID.randomUUID()
                transaction(database) {
                    publishEventQueue(
                        EventData.SaksnotatOpprettet(
                            notatId = notatId.toString(),
                            sakId = sak.sakId.toString(),
                            soknadId = sak.soknadId.toString(),
                            tekst = tekst,
                            skrevetAvRolle = rolleForNotat(principal, sak),
                            skrevetAvIdent = principal.navIdent,
                            skrevetAvNavn = principal.navn,
                            tidspunkt = Clock.System.now(),
                        )
                    )
                }
                log.info("Saksnotat {} publisert for sakId={}", notatId, sak.sakId)
                call.respond(HttpStatusCode.Accepted, SaksnotatOpprettetResponse(notatId.toString()))
            }

            put("/api/saksbehandling/v1/saker/{sakId}/notater/{notatId}") {
                val (principal, sak) = sakMedTilgang() ?: return@put
                if (avvisArenaSak(sak)) return@put
                val notatId = notatIdParameter() ?: return@put
                val tekst = mottaTekst() ?: return@put

                val resultat = transaction(database) {
                    val forfatter = hentSaksnotatForfatter(sak.sakId, notatId)
                        ?: return@transaction NotatSkriving.IkkeFunnet
                    if (forfatter != principal.navIdent) return@transaction NotatSkriving.IkkeEget
                    publishEventQueue(
                        EventData.SaksnotatEndret(
                            notatId = notatId.toString(),
                            sakId = sak.sakId.toString(),
                            soknadId = sak.soknadId.toString(),
                            tekst = tekst,
                            endretAvRolle = rolleForNotat(principal, sak),
                            endretAvIdent = principal.navIdent,
                            tidspunkt = Clock.System.now(),
                        )
                    )
                    NotatSkriving.Publisert
                }
                svarPaaNotatSkriving(resultat, "Endring av saksnotat", notatId, sak.sakId)
            }

            delete("/api/saksbehandling/v1/saker/{sakId}/notater/{notatId}") {
                val (principal, sak) = sakMedTilgang() ?: return@delete
                if (avvisArenaSak(sak)) return@delete
                val notatId = notatIdParameter() ?: return@delete

                val resultat = transaction(database) {
                    val forfatter = hentSaksnotatForfatter(sak.sakId, notatId)
                        ?: return@transaction NotatSkriving.IkkeFunnet
                    if (forfatter != principal.navIdent) return@transaction NotatSkriving.IkkeEget
                    publishEventQueue(
                        EventData.SaksnotatSlettet(
                            notatId = notatId.toString(),
                            sakId = sak.sakId.toString(),
                            soknadId = sak.soknadId.toString(),
                            slettetAvRolle = rolleForNotat(principal, sak),
                            slettetAvIdent = principal.navIdent,
                            tidspunkt = Clock.System.now(),
                        )
                    )
                    NotatSkriving.Publisert
                }
                svarPaaNotatSkriving(resultat, "Sletting av saksnotat", notatId, sak.sakId)
            }
        }
    }
}

private enum class NotatSkriving { Publisert, IkkeFunnet, IkkeEget }

private suspend fun RoutingContext.svarPaaNotatSkriving(
    resultat: NotatSkriving,
    handling: String,
    notatId: UUID,
    sakId: UUID,
) = when (resultat) {
    NotatSkriving.Publisert -> {
        log.info("{} {} publisert for sakId={}", handling, notatId, sakId)
        call.respond(HttpStatusCode.Accepted)
    }

    NotatSkriving.IkkeFunnet ->
        call.respond(HttpStatusCode.NotFound, mapOf("message" to "fant ikke notat"))

    NotatSkriving.IkkeEget ->
        call.respondTilgangAvvist(IKKE_EGET_NOTAT, "Du kan bare endre eller slette egne notater")
}

/**
 * Rollen innlogget skriver notatet i: beslutter når innlogget er beslutter på saken, ellers saksbehandler
 * hvis innlogget har den rollen.
 */
private fun rolleForNotat(principal: AzureAdPrincipal, sak: SakTilgangsgrunnlag): AktorRolle = when {
    principal.navIdent == sak.beslutterIdent -> AktorRolle.BESLUTTER
    principal.harRolle(Role.SAKSBEHANDLER) -> AktorRolle.SAKSBEHANDLER
    else -> AktorRolle.BESLUTTER
}
