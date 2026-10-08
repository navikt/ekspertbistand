package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.soknad.SoknadTable
import no.nav.ekspertbistand.tilgangsmaskin.Regelsett
import no.nav.ekspertbistand.tilgangsmaskin.TilgangsmaskinClient
import no.nav.ekspertbistand.tilgangsmaskin.Tilgangsresultat
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger("SaksbehandlingTilgangsjekker")

const val IKKE_TILGANG_ENHET = "IKKE_TILGANG_ENHET"
const val IKKE_TILDELT_SAK = "IKKE_TILDELT_SAK"

/**
 * Henter innlogget bruker og sjekker at den har minst én av [roller]. Svarer 401 uten innlogget
 * bruker og 403 uten rolle, og returnerer da null.
 */
internal suspend fun ApplicationCall.principalMedRolle(vararg roller: Role): AzureAdPrincipal? {
    val principal = principal<AzureAdPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    if (roller.none { principal.harRolle(it) }) {
        val rolletekst = roller.joinToString(" eller ") { it.name.lowercase() }
        respond(HttpStatusCode.Forbidden, mapOf("message" to "krever rolle $rolletekst"))
        return null
    }
    return principal
}

/** Enhetsnumrene saksbehandler har tilgang til, eller null (og 503) hvis entra-proxy feiler. */
internal suspend fun ApplicationCall.hentEnheterForPrincipal(principal: AzureAdPrincipal): Set<String>? =
    try {
        principal.enheter().map { it.enhetnummer }.toSet()
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        log.error("Henting av enheter fra entra-proxy feilet ({}), avviser oppslag", e.javaClass.simpleName)
        respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "tilgangskontroll er ikke tilgjengelig"))
        null
    }

/** Det tilgangssjekkene trenger å vite om en sak. */
internal data class SakTilgangsgrunnlag(
    val sakId: UUID,
    val behandlendeEnhet: String?,
    val ansattFnr: String,
    val saksbehandlerIdent: String?,
    val beslutterIdent: String?,
) {
    companion object {
        fun SakDetaljer.tilTilgangsgrunnlag() = SakTilgangsgrunnlag(
            sakId = UUID.fromString(sakId),
            behandlendeEnhet = behandlendeEnhet,
            ansattFnr = soknad.ansatt.fnr,
            saksbehandlerIdent = saksbehandlerIdent,
            beslutterIdent = saksbehandlerIdent,
        )
    }
}

/** Må kalles i en transaksjon. Returnerer null hvis saken ikke finnes. */
internal fun JdbcTransaction.hentSakTilgangsgrunnlag(sakId: UUID): SakTilgangsgrunnlag? =
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(
            SakTable.behandlendeEnhet,
            SakTable.saksbehandlerIdent,
            SakTable.beslutterIdent,
            SoknadTable.ansattFnr,
        )
        .where { SakTable.sakId eq sakId }
        .singleOrNull()
        ?.let {
            SakTilgangsgrunnlag(
                sakId = sakId,
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
                ansattFnr = it[SoknadTable.ansattFnr],
                saksbehandlerIdent = it[SakTable.saksbehandlerIdent],
                beslutterIdent = it[SakTable.beslutterIdent],
            )
        }

/**
 * Sjekker med [AzureAdPrincipal.harTilgangTilEnhet] at saksbehandler har tilgang til sakens
 * behandlende enhet. Saker uten enhet er ikke tilgjengelige for noen. Svarer 403 og returnerer
 * false ved avslag. Fail-closed: feil mot entra-proxy gir 503.
 */
internal suspend fun ApplicationCall.sjekkTilgangTilEnhet(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
): Boolean {
    val harTilgangTilEnhet = try {
        sak.behandlendeEnhet != null && principal.harTilgangTilEnhet(sak.behandlendeEnhet)
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        log.error("Henting av enheter fra entra-proxy feilet ({}), avviser oppslag", e.javaClass.simpleName)
        respond(HttpStatusCode.ServiceUnavailable, mapOf("message" to "tilgangskontroll er ikke tilgjengelig"))
        return false
    }
    if (!harTilgangTilEnhet) {
        log.info("Tilgang avvist: saksbehandler mangler tilgang til enhet for sakId={}", sak.sakId)
        respond(
            HttpStatusCode.Forbidden,
            TilgangAvvistResponse(
                kode = IKKE_TILGANG_ENHET,
                begrunnelse = "Du har ikke tilgang til enheten som behandler saken",
            ),
        )
    }
    return harTilgangTilEnhet
}

/**
 * Sjekker at Tilgangsmaskinen (kjerneregler) godtar oppslag på den ansatte. Svarer 403 og
 * returnerer false ved avslag. Fail-closed: feil mot Tilgangsmaskinen gir 503.
 */
internal suspend fun ApplicationCall.sjekkTilgangsmaskin(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
    tilgangsmaskinClient: TilgangsmaskinClient,
): Boolean {
    val tilgang = try {
        tilgangsmaskinClient.evaluer(
            userToken = principal.subjectToken,
            brukerIdent = sak.ansattFnr,
            regelsett = Regelsett.KJERNE,
        )
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        log.error("Tilgangskontroll feilet for sakId={}, avviser oppslag", sak.sakId, e)
        respond(
            HttpStatusCode.ServiceUnavailable,
            mapOf("message" to "tilgangskontroll er ikke tilgjengelig"),
        )
        return false
    }

    return when (tilgang) {
        Tilgangsresultat.Innvilget -> true
        is Tilgangsresultat.Avvist -> {
            log.info("Tilgang avvist av Tilgangsmaskinen for sakId={}", sak.sakId)
            respond(
                HttpStatusCode.Forbidden,
                TilgangAvvistResponse(kode = tilgang.kode, begrunnelse = tilgang.begrunnelse),
            )
            false
        }
    }
}

/**
 * Sjekker at [principal] er tildelt saken som saksbehandler (`sak.saksbehandler_ident`).
 * Svarer 403 og returnerer false ved avslag.
 */
internal suspend fun ApplicationCall.sjekkErSaksbehandlerPåSak(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
): Boolean {
    val erSaksbehandler = sak.saksbehandlerIdent != null && sak.saksbehandlerIdent == principal.navIdent
    if (!erSaksbehandler) {
        log.info("Tilgang avvist: saksbehandler er ikke tildelt sakId={}", sak.sakId)
        respond(
            HttpStatusCode.Forbidden,
            TilgangAvvistResponse(kode = IKKE_TILDELT_SAK, begrunnelse = "Du er ikke tildelt saken"),
        )
    }
    return erSaksbehandler
}

internal suspend fun ApplicationCall.sjekkErBeslutterPåSak(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
): Boolean {
    val erBeslutter = sak.beslutterIdent != null && sak.beslutterIdent == principal.navIdent
    if (!erBeslutter) {
        log.info("Tilgang avvist: beslutter er ikke tildelt sakId={}", sak.sakId)
        respond(
            HttpStatusCode.Forbidden,
            TilgangAvvistResponse(kode = IKKE_TILDELT_SAK, begrunnelse = "Du er ikke tildelt saken"),
        )
    }
    return erBeslutter
}
