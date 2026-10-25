package no.nav.ekspertbistand.saksbehandling

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import no.nav.ekspertbistand.infrastruktur.AzureAdPrincipal
import no.nav.ekspertbistand.infrastruktur.rethrowIfCancellation
import no.nav.ekspertbistand.infrastruktur.teamLogger
import no.nav.ekspertbistand.norg.BehandlendeEnhetService
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

private object SaksbehandlingTilgangsjekker

private val log = LoggerFactory.getLogger("SaksbehandlingTilgangsjekker")
private val teamLog = SaksbehandlingTilgangsjekker.teamLogger()

const val IKKE_TILGANG = "IKKE_TILGANG"
const val IKKE_TILGANG_ENHET = "IKKE_TILGANG_ENHET"
const val IKKE_TILDELT_SAK = "IKKE_TILDELT_SAK"
const val BESLUTTER_INHABIL = "BESLUTTER_INHABIL"

const val AVVIST_STRENGT_FORTROLIG_ADRESSE = "AVVIST_STRENGT_FORTROLIG_ADRESSE"
const val AVVIST_FORTROLIG_ADRESSE = "AVVIST_FORTROLIG_ADRESSE"
private val avvisningskoderForKode6 = setOf(AVVIST_STRENGT_FORTROLIG_ADRESSE, "AVVIST_STRENGT_FORTROLIG_UTLAND")

/** Svarer 403 med correlation-ID-en til kallet som `feilId`, slik at brukerstøtte kan finne kallet i loggen. */
internal suspend fun ApplicationCall.respondTilgangAvvist(kode: String?, begrunnelse: String?) =
    respond(HttpStatusCode.Forbidden, TilgangAvvistResponse(kode = kode, begrunnelse = begrunnelse, feilId = callId))

/**
 * Avvisning av en sak der deltakeren har strengt fortrolig adresse (kode 6). Svaret sier at det er grunnen
 * (etterlevelseskrav K255.1), men ingenting om personen. Team logs får nok til å følge opp ut fra feil-ID-en.
 */
private suspend fun ApplicationCall.avvisKode6(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
    avvisningskode: String?,
) {
    teamLog.warn(
        "Tilgang avvist til sak med strengt fortrolig adresse: navIdent={}, sakId={}, rute={} {}, avvisningskode={}, feilId={}",
        principal.navIdent, sak.sakId, request.httpMethod.value, request.path(), avvisningskode, callId,
    )
    respondTilgangAvvist(
        kode = AVVIST_STRENGT_FORTROLIG_ADRESSE,
        begrunnelse = "Du har ikke tilgang til brukere med strengt fortrolig adresse",
    )
}

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

/** Det tilgangssjekkene trenger å vite om en sak. */
internal data class SakTilgangsgrunnlag(
    val sakId: UUID,
    val soknadId: UUID,
    val status: Saksstatus,
    val kildeTilBehandling: KildeTilBehandling,
    val behandlendeEnhet: String?,
    val ansattFnr: String,
    val saksbehandlerIdent: String?,
    val beslutterIdent: String?,
    /** Saksbehandleren som sendte siste foreløpige vedtak, eller null uten vedtak. */
    val innstiltAvIdent: String?,
) {
    companion object {
        fun SakDetaljer.tilTilgangsgrunnlag() = SakTilgangsgrunnlag(
            sakId = UUID.fromString(sakId),
            soknadId = UUID.fromString(soknad.soknadId),
            status = status,
            kildeTilBehandling = kildeTilBehandling,
            behandlendeEnhet = behandlendeEnhet,
            ansattFnr = soknad.ansatt.fnr,
            saksbehandlerIdent = saksbehandlerIdent,
            beslutterIdent = beslutterIdent,
            innstiltAvIdent = forelopigVedtak?.innstiltAvIdent,
        )
    }
}

/** Må kalles i en transaksjon. Returnerer null hvis saken ikke finnes. */
internal fun JdbcTransaction.hentSakTilgangsgrunnlag(sakId: UUID): SakTilgangsgrunnlag? =
    SakTable
        .join(SoknadTable, JoinType.INNER, SakTable.soknadId, SoknadTable.id)
        .select(
            SakTable.soknadId,
            SakTable.status,
            SakTable.kildeTilBehandling,
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
                soknadId = it[SakTable.soknadId],
                status = Saksstatus.valueOf(it[SakTable.status]),
                kildeTilBehandling = KildeTilBehandling.valueOf(it[SakTable.kildeTilBehandling]),
                behandlendeEnhet = it[SakTable.behandlendeEnhet],
                ansattFnr = it[SoknadTable.ansattFnr],
                saksbehandlerIdent = it[SakTable.saksbehandlerIdent],
                beslutterIdent = it[SakTable.beslutterIdent],
                innstiltAvIdent = hentSisteForelopigVedtakSammendrag(listOf(sakId))[sakId]?.innstiltAvIdent,
            )
        }

/**
 * Sjekker med [AzureAdPrincipal.harTilgangTilEnhet] at saksbehandler har tilgang til sakens
 * behandlende enhet. Saker uten enhet er ikke tilgjengelige for noen. Svarer 403 og returnerer
 * false ved avslag. Enhetene er hentet fra entra-proxy i `AZURE_AD_PROVIDER`, som svarer 503 ved feil.
 */
internal suspend fun ApplicationCall.sjekkTilgangTilEnhet(
    principal: AzureAdPrincipal,
    sak: SakTilgangsgrunnlag,
): Boolean {
    val harTilgangTilEnhet = sak.behandlendeEnhet != null && principal.harTilgangTilEnhet(sak.behandlendeEnhet)
    if (!harTilgangTilEnhet) {
        log.info("Tilgang avvist: saksbehandler mangler tilgang til enhet for sakId={}", sak.sakId)
        if (sak.behandlendeEnhet == BehandlendeEnhetService.NAV_VIKAFOSSEN) {
            avvisKode6(principal, sak, avvisningskode = IKKE_TILGANG_ENHET)
        } else {
            respondTilgangAvvist(
                kode = IKKE_TILGANG_ENHET,
                begrunnelse = "Du har ikke tilgang til enheten som behandler saken",
            )
        }
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
            if (tilgang.kode in avvisningskoderForKode6) {
                avvisKode6(principal, sak, avvisningskode = tilgang.kode)
            } else {
                respondTilgangAvvist(kode = tilgang.kode, begrunnelse = tilgang.begrunnelse)
            }
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
        respondTilgangAvvist(kode = IKKE_TILDELT_SAK, begrunnelse = "Du er ikke tildelt saken")
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
        respondTilgangAvvist(kode = IKKE_TILDELT_SAK, begrunnelse = "Du er ikke tildelt saken")
    }
    return erBeslutter
}

private const val MAKS_IDENTER_PER_BULK = 1000
const val AVVIST_SKJERMING = "AVVIST_SKJERMING"

/**
 * Bulk-sjekk mot Tilgangsmaskinen (kjerneregler) for listen. Gir avvisningen per ident, eller null når innvilget.
 * Identer som mangler i svaret eller har annen status enn 204 (også 404), regnes som avvist. Skjerming og
 * adressebeskyttelse sendes videre som kode, slik at saksbehandleren ser grunnen (etterlevelseskrav K255.1).
 * Kode 6 i utlandet blir [AVVIST_STRENGT_FORTROLIG_ADRESSE]. Andre avvisninger blir [IKKE_TILGANG].
 * Kaster ved feil mot Tilgangsmaskinen.
 */
internal suspend fun TilgangsmaskinClient.deltakerTilganger(
    principal: AzureAdPrincipal,
    identer: Collection<String>,
): Map<String, DeltakerTilgang?> {
    val distinkte = identer.distinct()
    val resultater = distinkte
        .chunked(MAKS_IDENTER_PER_BULK)
        .flatMap { bolk -> evaluerBulk(principal.subjectToken, bolk, Regelsett.KJERNE).resultater }
        .associateBy { it.brukerId }
    return distinkte.associateWith { ident ->
        val resultat = resultater[ident]
        if (resultat?.innvilget == true) {
            null
        } else {
            DeltakerTilgang(
                avvist = true,
                kode = when (val kode = resultat?.detaljer?.title) {
                    AVVIST_SKJERMING, AVVIST_FORTROLIG_ADRESSE -> kode
                    in avvisningskoderForKode6 -> AVVIST_STRENGT_FORTROLIG_ADRESSE
                    else -> IKKE_TILGANG
                },
            )
        }
    }
}
